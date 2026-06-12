---
layout: default
title: Skill 评测技术方案
parent: 开发指南
nav_order: 5
has_toc: true
---

# Skill 评测技术方案

> 本文档面向需要对 AI Skill（业务接口/智能体能力）进行自动化评测的开发者，系统梳理主流评测维度、评估器选型、评测模式选择及最佳实践。

---

## 一、整体思路

Skill 评测的核心流程可以概括为：

```
测试数据（问题 + 标准答案）
    ↓ DataLoader 加载
    ↓ ApiCompletion 调用 Skill 接口
    ↓ Scorer × N 并行评估输出质量
    ↓ Counter 汇总统计指标
    ↓ Reporter 输出报告
```

核心差异在于**评估器（Scorer）的选型**，代表不同技术路线，应根据 Skill 的特性和评测目标灵活组合。

---

## 二、评估器技术路线

### 2.1 规则 / 启发式评测（无需 LLM）

**适用场景**：有明确正确答案或格式规范要求的场景，追求高速度、低成本、确定性结果。

| 方案 | 说明 | EvalKit 对应类 |
|------|------|---------------|
| **关键词覆盖检查** | 输出中是否包含必要关键词 | 继承 `Scorer` 实现 `eval()` |
| **TF-IDF 向量相似度** | 与标准答案做余弦相似度比较 | `VectorSimilarityScorer` |
| **格式/正则校验** | 验证输出格式（JSON、手机号、日期等） | 继承 `Checker` + `MultiCheckerBasedScorer` |
| **长度/字数检查** | 回复是否过短或超长 | 继承 `Scorer` 自定义 `eval()` |

```java
// 示例：关键词必须全部覆盖（必过指标）
Scorer keywordScorer = new Scorer(
    ScorerConfig.builder()
        .metricName("关键词覆盖")
        .threshold(1.0)
        .star(true)   // 一票否决：不通过则整体不通过
        .build()
) {
    @Override
    public ScorerResult eval(DataItem dataItem) {
        String response = dataItem.getApiCompletionResult().get("response");
        String[] required = {"价格", "座位", "出发时间"};
        List<String> missing = Arrays.stream(required)
                .filter(kw -> !response.contains(kw))
                .collect(Collectors.toList());
        ScorerResult result = new ScorerResult();
        result.setMetric("关键词覆盖");
        result.setScore(missing.isEmpty() ? 1.0 : 0.0);
        result.setReason(missing.isEmpty() ? "全部关键词已覆盖" : "缺少关键词：" + String.join("、", missing));
        return result;
    }
};
```

**优点**：速度快、成本零、结果可复现
**缺点**：无法处理语义等价但措辞不同的情况，对自然语言理解能力有限

---

### 2.2 LLM-as-Judge 评测（主流推荐）

用大模型作为"裁判"理解语义、判断质量，是当前业界最主流的 Skill 评测技术路线。

#### 2.2.1 自定义 Prompt 打分（最灵活）

适合有特定业务语义的评测场景，完全自定义系统提示词和用户提示词：

```java
PromptBasedScorer scorer = new PromptBasedScorer(
    PromptBasedScorerConfig.builder()
        .metricName("回答准确性")
        .llmService(myLLMService)
        .threshold(0.6)
        .build()
) {
    @Override
    public String prepareSysPrompt() {
        return "你是评测专家。请评估候选答案是否准确回答了用户的问题。" +
               "输出 JSON 格式：{\"score\": 0到1之间的小数, \"reason\": \"评判理由\"}";
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult result) {
        return String.format("用户问题：%s\n标准答案：%s\n候选答案：%s",
                inputData.get("query"),
                inputData.get("groundTruth"),
                result.get("response"));
    }

    @Override
    public LLMResult parseLLMReply(String reply) {
        String json = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(json, LLMResult.class);
    }
};
```

#### 2.2.2 内置专项评估器

框架内置了常用的专项评估器，可直接使用：

| 评估器 | 评测维度 | 输出范围 | 适用场景 |
|--------|----------|----------|---------|
| `AnswerRelevancyScorer` | 答案是否切题，回答了核心诉求 | 0~1 | 通用问答类 Skill |
| `SemanticConsistencyScorer` | 与标准答案语义是否完全一致 | 0 或 1 | 需精确语义等价判断 |
| `SecurityScorer` | 内容安全（政治/暴力/色情/诈骗等） | 0 或 1 | 所有对外 Skill |
| `GSBScorer` | 准确性+相关性+完整性+流畅性综合打分 | 0~1 | A/B 对比实验 |

```java
// 示例：答案相关性评估
AnswerRelevancyScorer relevancyScorer = new AnswerRelevancyScorer(
    PromptBasedScorerConfig.builder()
        .metricName("答案相关性")
        .llmService(myLLMService)
        .threshold(0.7)
        .build()
) {
    @Override
    public String prepareQuery(InputData in, ApiCompletionResult out) {
        return in.get("query");
    }
    @Override
    public String prepareAnswer(InputData in, ApiCompletionResult out) {
        return out.get("response");
    }
};
```

#### 2.2.3 量规评估器 `RubricBasedScorer`（最系统化）

预定义多个评估维度（Criteria），每个维度独立发起 LLM 调用，采用 Chain-of-Thought 强制推理，防止 LLM 先锚定分数再补理由：

```java
RubricBasedScorer scorer = new RubricBasedScorer(
    RubricBasedScorerConfig.builder()
        .metricName("综合质量评估")
        .llmService(myLLMService)
        .criteria(Arrays.asList(
            // 安全性：二元分，一票否决
            RubricCriteria.builder()
                .name("Safety")
                .definition("回复是否包含有害、违规、歧视性内容")
                .scoreType(RubricScoreType.BINARY)
                .maxScore(1).minScore(0).passScore(1)
                .scoringGuide("1=无任何有害内容; 0=包含有害内容")
                .star(true)
                .build(),
            // 准确性：5 级阶梯，权重 2 倍
            RubricCriteria.builder()
                .name("Accuracy")
                .definition("回复的事实准确程度，有无错误或捏造")
                .scoreType(RubricScoreType.STEPPED)
                .maxScore(5).minScore(0).passScore(3)
                .scoringGuide("5=完全准确; 4=基本准确有小偏差; 3=整体可信有遗漏; 2=有明显错误; 1=大量虚构")
                .weight(2.0)
                .build(),
            // 流畅性：5 级阶梯，权重 1 倍
            RubricCriteria.builder()
                .name("Fluency")
                .definition("回复的语言流畅度和可读性")
                .scoreType(RubricScoreType.STEPPED)
                .maxScore(5).minScore(0).passScore(3)
                .scoringGuide("5=表达精准流畅; 4=基本流畅; 3=可读但有语法问题; 2=较难理解; 1=不可读")
                .weight(1.0)
                .build()
        ))
        .mergeStrategy(RubricMergeStrategy.STAR_GATE)  // Safety 不过则整体为 0
        .threshold(0.6)
        .criteriaThreadNum(3)   // 3个维度并发调用 LLM
        .build()
) {
    @Override
    public String prepareUserPrompt(InputData in, ApiCompletionResult out) {
        return "用户问题：" + in.get("query") + "\n模型回复：" + out.get("response");
    }
};
```

**RubricMergeStrategy 五种合并策略：**

| 策略 | 公式 | 适用场景 |
|------|------|---------|
| `WEIGHTED_AVERAGE`（默认） | `Σ(score_i × weight_i) / Σ(weight_i)` | 各维度重要程度不同 |
| `SIMPLE_AVERAGE` | `Σ(score_i) / N` | 各维度等权重 |
| `LOGICAL_AND` | 任意维度未达标则取最差分 | 要求所有维度均达标 |
| `STAR_GATE` | `star` 维度未达标则整体为 0 | 存在一票否决项（如安全合规） |
| `COMPLETION_RATE` | 达标维度数 / 总维度数 | 关注"多少维度达标"而非具体分值 |

---

### 2.3 外部工作流评测

通过 `DifyWorkflowScorer` 调用在 Dify 平台上已搭建好的评测工作流，适合团队已有现成评测流程的场景：

```java
DifyWorkflowScorer difyScorer = new DifyWorkflowScorer(
    DifyWorkflowScorerConfig.builder()
        .metricName("Dify评分")
        .apiKey("app-xxxxxxxxxxxx")
        .baseUrl("https://api.dify.ai")
        .userName("evalkit-user")
        .threshold(0.7)
        .build()
) {
    @Override
    public Map<String, Object> prepareInputParams(InputData in, ApiCompletionResult out) {
        return MapUtils.of("query", in.get("query"), "response", out.get("response"));
    }

    @Override
    public ScorerResult prepareScorerResult(InputData in, ApiCompletionResult out, Map<String, Object> outputs) {
        ScorerResult result = new ScorerResult();
        result.setMetric("Dify评分");
        result.setScore(Double.parseDouble(outputs.get("score").toString()));
        result.setReason(outputs.get("reason").toString());
        return result;
    }
};
```

---

## 三、评测模式选择

根据数据量、是否需要断点续评、是否是多轮对话，选择对应的评测门面：

```
数据量 < 1000 条？
    ↓ 是
  → FullEvalFacade（简单、快速、一次性）

    ↓ 否（数据量大）
  需要保证同组数据顺序执行？（如多轮对话 session）
      ↓ 是
    → OrderedDeltaEvalFacade（同 sessionId 按轮次串行）

      ↓ 否
    → DeltaEvalFacade（断点续评，ActiveMQ + SQLite）
```

| 评测模式 | 类 | 适用场景 | 断点续评 |
|---------|-----|---------|---------|
| **全量评测** | `FullEvalFacade` | 小数据集（< 1000条），一次性评测 | ✗ |
| **增量评测** | `DeltaEvalFacade` | 大数据集，需断点续评 | ✓ |
| **有序增量评测** | `OrderedDeltaEvalFacade` | 多轮对话，同 session 需有序执行 | ✓ |

---

## 四、多维度组合评测（最佳实践）

实际 Skill 评测通常是**多维度并行组合**，建议按以下层次搭建：

```
Skill 评测维度组合（推荐）：
├── 内容安全        (star=true，必过)  → SecurityScorer / RubricCriteria(Safety, star=true)
├── 答案相关性      (核心指标)         → AnswerRelevancyScorer
├── 语义准确性      (与标准答案对比)   → SemanticConsistencyScorer / VectorSimilarityScorer
├── 格式规范        (结构化输出验证)   → MultiCheckerBasedScorer
└── 综合质量        (A/B 对比或上线门控) → GSBScorer / RubricBasedScorer
```

### 完整示例：酒店搜索 Skill 评测

```java
// 1. LLM 服务（用于 LLM-as-Judge 评估器）
LLMService llmService = LLMServiceFactory.createDeepSeekService(
    LLMServiceConfig.builder().model("deepseek-chat").temperature(0.1).build(),
    System.getenv("DEEPSEEK_API_KEY")
);

// 2. 评测工作流：调用 Skill + 并行打分
Workflow evalWorkflow = Workflow.builder()
    .addNode(new MyHotelSearchApiCompletion())          // 调用酒店搜索接口
    .addNode(new SecurityScorer(PromptBasedScorerConfig.builder()
        .metricName("内容安全").llmService(llmService)
        .star(true).threshold(1.0).build()) { ... })    // 安全必过
    .addNode(new AnswerRelevancyScorer(PromptBasedScorerConfig.builder()
        .metricName("答案相关性").llmService(llmService)
        .threshold(0.7).build()) { ... })               // 相关性
    .addNode(new VectorSimilarityScorer(VectorSimilarityScorerConfig.builder()
        .metricName("语义相似度").similarityThreshold(0.5).build()) { ... }) // 快速相似度
    .build();

// 3. 上报工作流：统计 + 输出
Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())
    .addNode(new ExcelReporter("hotel_eval_result"))
    .addNode(new HtmlReporter("hotel_eval_report"))
    .build();

// 4. 运行评测（数据量大时改用 DeltaEvalFacade）
FullEvalConfig config = FullEvalConfig.builder()
    .taskName("酒店搜索质量评测-v2.0")
    .dataLoader(new ExcelDataLoader(ExcelDataLoaderConfig.builder()
        .filePath("hotel_testcases.xlsx").build()))
    .evalWorkflow(evalWorkflow)
    .reportWorkflow(reportWorkflow)
    .threadNum(8)
    .passScore(0.6)
    .build();

new FullEvalFacade(config).run();
```

---

## 五、多轮对话 Skill 评测

多轮对话 Skill 需要保证同一 session 内的请求**按轮次顺序**发送，使用 `OrderedApiCompletion` + `OrderedDeltaEvalFacade`：

```java
// 接口调用器：同 sessionId 的请求按 round 顺序串行执行
OrderedApiCompletion apiCompletion = new OrderedApiCompletion(
    ApiCompletionConfig.builder().threadNum(8).build()
) {
    @Override
    public String prepareOrderKey(DataItem dataItem) {
        return dataItem.getInputData().get("sessionId");
    }
    @Override
    public Comparator<DataItem> prepareComparator() {
        return Comparator.comparingInt(d -> Integer.parseInt(d.getInputData().get("round")));
    }
    @Override
    protected ApiCompletionResult invoke(DataItem dataItem) {
        // 利用内置工具方法获取历史对话上下文
        List<DataItem> prevItems = getPrevDataItems(dataItem);
        // 构建历史消息并调用接口...
    }
};

// 评测门面：有序增量评测
public class MyOrderedEvalFacade extends OrderedDeltaEvalFacade {
    public MyOrderedEvalFacade(DeltaEvalConfig config) { super(config); }

    @Override
    public String prepareOrderKey(InputData inputData) {
        return (String) inputData.getInputItem().get("sessionId");
    }
    @Override
    public Comparator<InputData> prepareComparator() {
        return Comparator.comparingInt(d -> Integer.parseInt(d.getInputItem().get("round").toString()));
    }
}
```

在评估器中，可以通过 `DataItem.extra` 读取 `ApiCompletion` 预先写入的历史对话信息（详见[接口调用器文档](../user-guide/api-completion.md)）。

---

## 六、评测方案选型指南

| 场景 | 推荐方案 | 说明 |
|------|---------|------|
| 有标准答案、追求速度低成本 | `VectorSimilarityScorer` + 规则 `Scorer` | 纯本地计算，无 LLM 调用费用 |
| 需要语义理解、主观质量判断 | `PromptBasedScorer` / `AnswerRelevancyScorer` | LLM-as-Judge，理解语义等价 |
| 多维度系统化评测，需要可解释性 | `RubricBasedScorer` | CoT 推理 + Few-shot 锚点，打分透明可审计 |
| A/B 实验对比新旧版本 Skill | `GSBScorer` | Good/Same/Bad 三档，直观反映迭代效果 |
| 内容安全强制门控 | 任意 `Scorer` 配置 `star=true` | 安全不过则整体不通过，无论其他指标分数多高 |
| 多轮对话 Skill | `OrderedApiCompletion` + `OrderedDeltaEvalFacade` | 保证同 session 按轮次顺序执行 |
| 大规模评测（断点续评） | `DeltaEvalFacade`（ActiveMQ + SQLite） | 支持中断恢复，不丢数据 |
| 已有 Dify 评测流程 | `DifyWorkflowScorer` | 复用已搭建的工作流，降低迁移成本 |

---

## 七、LLM 评测器参数建议

使用 LLM-as-Judge 时，以下参数配置会显著影响评测稳定性：

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `temperature` | `0.1~0.3` | 打分/判断类任务追求确定性，不宜过高 |
| `retryTimes` | `3` | 解析失败时重试，防止偶发格式错误导致数据缺失 |
| `RubricBasedScorer.criteriaThreadNum` | 与维度数相同 | 各维度 LLM 调用全并发，显著缩短评测耗时 |
| `RubricBasedScorer.sampleTimes` | `0`（单次）或 `3`（多次采样） | 对稳定性要求高的场景可多次采样取均值 |
| `RubricCriteria.anchors` | 强烈建议配置 | Few-shot 锚点显著校准中间分值漂移，提升打分一致性 |

