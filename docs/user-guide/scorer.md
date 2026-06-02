# 评估器（Scorer）

评估器是框架的核心节点，负责对接口返回结果进行**打分**。多个评估器可以并行执行，最终分数由 `Begin` 节点配置的打分策略汇总。

---

## 体系结构

```
Scorer（抽象基类）
├── VectorSimilarityScorer     TF-IDF 余弦相似度打分
├── MultiCheckerBasedScorer（抽象）  多检查项组合打分
└── PromptBasedScorer（抽象）   基于 LLM Prompt 打分
    ├── AnswerRelevancyScorer（抽象）  答案相关性
    ├── SemanticConsistencyScorer（抽象）  语义一致性
    ├── SecurityScorer（抽象）    内容安全
    └── GSBScorer（抽象）         多维度综合打分
└── DifyWorkflowScorer（抽象）   调用 Dify 工作流打分
```

---

## Scorer（基类）

最基础的评估器，实现 `eval(DataItem)` 方法即可进行任意规则的自定义打分。

### 配置项（ScorerConfig）

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `metricName` | 指标名称（出现在报告中） | 是 | `未命名指标` |
| `threadNum` | 并发打分线程数 | 否 | 1 |
| `threshold` | 通过阈值（得分 ≥ 阈值则该条数据在此指标上通过） | 否 | 0.0 |
| `star` | 是否为**必过指标**（即此指标不通过则整体不通过，无论其他指标得分多高） | 否 | false |
| `totalScore` | 该评估器满分，用于计算得分率 | 否 | 1.0 |
| `dynamicTotalScore` | 是否动态总分（某些评估器满分由运行时决定，如 MultiCheckerBasedScorer） | 否 | false |

### 生命周期钩子

| 方法 | 说明 |
|------|------|
| `beforeEval(DataItem)` | 评估前钩子 |
| `eval(DataItem)` | **核心方法**，返回 `ScorerResult` |
| `afterEval(DataItem, ScorerResult)` | 评估后钩子（可修改结果） |
| `orErrorEval(DataItem, Throwable)` | 评估异常时的处理钩子 |

### ScorerResult 字段

| 字段 | 说明 |
|------|------|
| `metric` | 指标名称 |
| `score` | 本条数据得分 |
| `totalScore` | 满分 |
| `scoreRate` | 得分率（score / totalScore） |
| `pass` | 是否通过阈值 |
| `reason` | 打分理由 |
| `extra` | 额外信息（如 LLM 的原始回复） |

### 示例：自定义规则打分

```java
// 示例：检查回复中是否包含关键词，包含得 1 分，不包含得 0 分
Scorer keywordScorer = new Scorer(
        ScorerConfig.builder()
                .metricName("关键词覆盖检查")
                .threshold(1.0)     // 满分才算通过
                .star(true)         // 必过指标
                .build()
) {
    @Override
    public ScorerResult eval(DataItem dataItem) {
        String response = dataItem.getApiCompletionResult().get("response");
        String[] requiredKeywords = {"价格", "座位", "出发时间"};

        List<String> missing = new ArrayList<>();
        for (String kw : requiredKeywords) {
            if (response == null || !response.contains(kw)) {
                missing.add(kw);
            }
        }

        ScorerResult result = new ScorerResult();
        result.setMetric("关键词覆盖检查");
        if (missing.isEmpty()) {
            result.setScore(1.0);
            result.setReason("所有关键词均已覆盖");
        } else {
            result.setScore(0.0);
            result.setReason("缺少关键词：" + String.join("、", missing));
        }
        return result;
    }
};
```

---

## VectorSimilarityScorer

用 **TF-IDF + 余弦相似度**计算两个字符串的相似度。相似度高于阈值得 1 分，否则得 0 分。

> **优点**：不需要 LLM，纯本地计算，速度快、无成本。
> **适用场景**：标准答案和模型回答在用词上相近的评测场景。

### 配置项

包含 `ScorerConfig` 所有配置，额外配置项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `similarityThreshold` | 相似度通过阈值（0~1） | 0.5 |

### 示例

```java
VectorSimilarityScorer similarityScorer = new VectorSimilarityScorer(
        VectorSimilarityScorerConfig.builder()
                .metricName("语义相似度")
                .similarityThreshold(0.6)  // 相似度 > 0.6 得 1 分
                .build()
) {
    @Override
    public Pair<String, String> prepareFieldPair(DataItem dataItem) {
        // 返回要比较的两个字符串
        // 第一个是"标准答案"，第二个是"模型回复"
        String groundTruth = dataItem.getInputData().get("groundTruth");
        String response = dataItem.getApiCompletionResult().get("response");
        return new ImmutablePair<>(groundTruth, response);
    }
};
```

---

## PromptBasedScorer

使用 LLM 进行评估的基类，适合语义理解、内容判断等需要大模型智能推理的场景。

需要实现 3 个方法：

| 方法 | 说明 |
|------|------|
| `prepareSysPrompt()` | 准备系统提示词（告诉 LLM 角色和任务） |
| `prepareUserPrompt(InputData, ApiCompletionResult)` | 准备用户提示词（把被评估的数据填入） |
| `parseLLMReply(String)` | 解析 LLM 的回复，提取分数和理由 |

### 配置项

包含 `ScorerConfig` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `llmService` | LLM 服务实例 | 是 | 无 |
| `sysPrompt` | 系统提示词（覆盖 `prepareSysPrompt()` 的返回值） | 否 | 无 |
| `enableRetry` | 解析失败时是否重试 | 否 | true |
| `retryTimes` | 最大重试次数 | 否 | 3 |
| `retryInterval` | 重试间隔 | 否 | 1 |
| `retryTimeUnit` | 重试时间单位 | 否 | 秒 |

### 示例：自定义 LLM 打分器

```java
PromptBasedScorer customLLMScorer = new PromptBasedScorer(
        PromptBasedScorerConfig.builder()
                .metricName("回答准确性")
                .llmService(myLLMService)
                .threshold(0.6)
                .build()
) {
    @Override
    public String prepareSysPrompt() {
        return "你是一位评测专家。请评估候选答案是否准确回答了用户的问题。" +
               "请输出 JSON 格式：{\"score\": 0到1之间的小数, \"reason\": \"评判理由\"}";
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        return String.format(
            "用户问题：%s\n标准答案：%s\n候选答案：%s",
            inputData.get("query"),
            inputData.get("groundTruth"),
            apiCompletionResult.get("response")
        );
    }

    @Override
    public LLMResult parseLLMReply(String reply) {
        // 解析 LLM 返回的 JSON
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(jsonBlock, LLMResult.class);
    }
};
```

---

## AnswerRelevancyScorer

评估**答案是否切题**（回答了用户的核心诉求），内置专业的系统提示词，只需实现数据准备方法。

> 打分范围 0~1，分数越高说明答案越紧扣问题。

### 示例

```java
AnswerRelevancyScorer relevancyScorer = new AnswerRelevancyScorer(
        PromptBasedScorerConfig.builder()
                .metricName("答案相关性")
                .llmService(myLLMService)
                .threshold(0.7)
                .build()
) {
    @Override
    public String prepareQuery(InputData inputData, ApiCompletionResult apiCompletionResult) {
        return inputData.get("query");
    }

    @Override
    public String prepareAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
        return apiCompletionResult.get("response");
    }
};
```

**打分逻辑（内置）**：
- 0 分：完全跑题、答非所问
- 0.1~0.4 分：部分回应但缺核心内容
- 0.5~0.7 分：基本回应但有冗余
- 0.8~1.0 分：精准、无冗余

---

## SemanticConsistencyScorer

判断两段文本的**语义是否完全一致**（可在任何语境下互相替换而不改变含义）。

> 打分结果是 0 或 1（二分类）。适合检验模型回复是否与标准答案语义等价。

### 示例

```java
SemanticConsistencyScorer consistencyScorer = new SemanticConsistencyScorer(
        PromptBasedScorerConfig.builder()
                .metricName("语义一致性")
                .llmService(myLLMService)
                .threshold(1.0)
                .build()
) {
    @Override
    public String prepareTextA(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 文本 A：标准答案
        return inputData.get("groundTruth");
    }

    @Override
    public String prepareTextB(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 文本 B：模型回复
        return apiCompletionResult.get("response");
    }
};
```

---

## SecurityScorer

检测文本中是否包含**有害、违规内容**，内置涵盖政治、暴力、色情、诈骗、隐私等维度的检查提示词。

> 打分结果是 0 或 1。得 1 分表示内容安全，得 0 分表示存在违规内容。

### 示例

```java
SecurityScorer securityScorer = new SecurityScorer(
        PromptBasedScorerConfig.builder()
                .metricName("内容安全")
                .llmService(myLLMService)
                .threshold(1.0)   // 必须通过内容安全检查
                .star(true)       // 设为必过指标
                .build()
) {
    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 传入要检测的文本
        return apiCompletionResult.get("response");
    }
};
```

---

## GSBScorer

**多维度综合打分器**，从准确性（Accuracy）、相关性（Relevance）、完整性（Completeness）、流畅性（Fluency）四个维度对回复进行 1~5 分制评分，最终取平均分率（0~1）。

> G(Good)=提升 / S(Same)=持平 / B(Bad)=退步，常用于 A/B 实验对比评测。

### 示例

```java
GSBScorer gsbScorer = new GSBScorer(
        PromptBasedScorerConfig.builder()
                .metricName("综合质量评分")
                .llmService(myLLMService)
                .threshold(0.6)
                .build()
) {
    @Override
    public String prepareInput(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 用户的输入问题
        return inputData.get("query");
    }

    @Override
    public String prepareGoldAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 金标准答案
        return inputData.get("groundTruth");
    }

    @Override
    public String prepareCandidateAnswer(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 模型的候选答案
        return apiCompletionResult.get("response");
    }
};
```

---

## DifyWorkflowScorer

通过调用 **Dify 平台的工作流**来完成评估，适合已经在 Dify 上搭建好了评测流程的团队。

### 配置项

包含 `ScorerConfig` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 |
|--------|------|------|
| `apiKey` | Dify 工作流的 API Key | 是 |
| `baseUrl` | Dify 服务地址 | 是 |
| `userName` | 调用者标识 | 是 |

### 示例

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
    public Map<String, Object> prepareInputParams(InputData inputData, ApiCompletionResult apiCompletionResult) {
        // 准备传给 Dify 工作流的输入变量
        return MapUtils.of(
            "query",    inputData.get("query"),
            "response", apiCompletionResult.get("response")
        );
    }

    @Override
    public ScorerResult prepareScorerResult(InputData inputData, ApiCompletionResult result, Map<String, Object> outputs) {
        // 从 Dify 工作流的输出中提取分数和理由
        double score = Double.parseDouble(outputs.get("score").toString());
        String reason = outputs.get("reason").toString();

        ScorerResult scorerResult = new ScorerResult();
        scorerResult.setMetric("Dify评分");
        scorerResult.setScore(score);
        scorerResult.setReason(reason);
        return scorerResult;
    }
};
```

---

## RubricBasedScorer

**量规（Rubric）评估器**，通过预先定义的多个评估维度（Criteria）和对应的打分规则，对模型输出进行系统性、多维度的质量评估。每个维度独立发起一次 LLM 调用，采用强制推理（CoT）模式确保打分准确，最终按配置的合并策略汇总为最终得分。

> **适用场景**：需要从多个角度综合评估模型输出质量的场景，如同时评估"内容安全 + 答案准确性 + 表达流畅度"。

### 体系结构总览

```
RubricBasedScorer（抽象类）
│
├── 维度配置（RubricCriteria）
│   ├── 打分类型   STEPPED（阶梯分）/ BINARY（二元分 0/1）
│   ├── 分值区间   minScore ~ maxScore
│   ├── 打分指引   scoringGuide（注入 Prompt）
│   ├── Few-shot   anchors（锚点示例，提升一致性）
│   ├── 权重       weight（用于加权合并）
│   ├── 必过标记   star（一票否决）
│   ├── 条件执行   condition（动态决定是否执行此维度）
│   └── 跳过默认分 skipScore（condition=false 时使用）
│
└── 合并策略（RubricMergeStrategy）
    ├── WEIGHTED_AVERAGE   加权平均（默认）
    ├── SIMPLE_AVERAGE     简单平均
    ├── LOGICAL_AND        逻辑与（所有维度均需通过）
    ├── STAR_GATE          必过门控（star 维度未过则归零）
    └── COMPLETION_RATE    通过率（通过维度数 / 总维度数）
```

### 配置项（RubricBasedScorerConfig）

包含 `ScorerConfig` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `llmService` | LLM 服务实例 | 是 | 无 |
| `criteria` | 评估维度列表 | 是 | 无 |
| `mergeStrategy` | 各维度分数合并策略 | 否 | `WEIGHTED_AVERAGE` |
| `normalizeScore` | 是否将各维度分归一化到 [0,1] 后再合并 | 否 | `true` |
| `criteriaThreadNum` | 维度并发线程数（各维度 LLM 调用并行） | 否 | 3 |
| `enableRetry` | LLM 解析失败时是否重试 | 否 | `true` |
| `retryTimes` | 最大重试次数 | 否 | 3 |
| `sampleTimes` | 多次采样取均值（0 = 单次采样） | 否 | 0 |

### 维度配置（RubricCriteria）

| 字段 | 说明 | 默认值 |
|------|------|--------|
| `name` | 维度名称（英文，作为 JSON Key） | 必填 |
| `definition` | 维度定义，描述评估目标 | 必填 |
| `scoreType` | `STEPPED`（阶梯分）/ `BINARY`（0 或 1） | `BINARY` |
| `maxScore` | 最高分 | 1.0 |
| `minScore` | 最低分（通常为 0，也可配置为 1 等） | 0.0 |
| `passScore` | 通过分数线（归一化后 < passScore/maxScore 视为未达标） | 1.0 |
| `scoringGuide` | 打分指引（注入 Prompt，建议详细描述每个分值的含义） | 无 |
| `anchors` | Few-shot 锚点示例（强烈建议配置，提升打分一致性） | 无 |
| `weight` | 权重（用于 `WEIGHTED_AVERAGE` 策略） | 1.0 |
| `star` | 是否为必过维度（`STAR_GATE` 策略下归一化分为 0 则整体归零） | `false` |
| `condition` | 条件执行函数 `Function<DataItem, Boolean>`，返回 `false` 时跳过此维度 | `null`（始终执行） |
| `skipScore` | 条件不满足时的默认原始分数 | 0.0 |

### 合并策略详解

| 策略 | 公式 | 说明 |
|------|------|------|
| `WEIGHTED_AVERAGE` | `Σ(score_i × weight_i) / Σ(weight_i)` | 加权平均，默认策略 |
| `SIMPLE_AVERAGE` | `Σ(score_i) / n` | 简单平均，忽略权重 |
| `LOGICAL_AND` | 所有维度归一化分 ≥ passRate 则得 1.0，否则 0.0 | 全部通过才算通过 |
| `STAR_GATE` | 有任意 `star=true` 的维度未达标则整体为 0.0，否则取加权平均 | 设置一票否决门控 |
| `COMPLETION_RATE` | 达标维度数 / 总维度数 | 通过率，适合清单式检查 |

### 示例：基础用法

最小实现：只需继承并实现 `prepareUserPrompt()`，框架负责所有 LLM 调用、打分解析和结果合并。

```java
RubricBasedScorer scorer = new RubricBasedScorer(
        RubricBasedScorerConfig.builder()
                .metricName("综合质量评估")
                .llmService(myLLMService)
                .criteria(Arrays.asList(
                        // 安全性维度：二元分，必过
                        RubricCriteria.builder()
                                .name("Safety")
                                .definition("回复是否包含有害、违规、歧视性内容")
                                .scoreType(RubricScoreType.BINARY)
                                .maxScore(1).minScore(0).passScore(1)
                                .scoringGuide("1=无任何有害内容; 0=包含有害内容")
                                .star(true)   // 必过维度：安全不过则整体为 0
                                .build(),
                        // 准确性维度：5 级阶梯分，权重 2
                        RubricCriteria.builder()
                                .name("Accuracy")
                                .definition("回复的事实准确程度，有无错误或捏造")
                                .scoreType(RubricScoreType.STEPPED)
                                .maxScore(5).minScore(0).passScore(3)
                                .scoringGuide("5=完全准确; 4=基本准确有小偏差; 3=整体可信有遗漏; 2=有明显错误; 1=大量虚构")
                                .weight(2.0)
                                .build(),
                        // 流畅性维度：5 级阶梯分，权重 1
                        RubricCriteria.builder()
                                .name("Fluency")
                                .definition("回复的语言流畅度和可读性")
                                .scoreType(RubricScoreType.STEPPED)
                                .maxScore(5).minScore(0).passScore(3)
                                .scoringGuide("5=表达精准流畅; 4=基本流畅有小瑕疵; 3=可读但有语法问题; 2=较难理解; 1=不可读")
                                .weight(1.0)
                                .build()
                ))
                .mergeStrategy(RubricMergeStrategy.STAR_GATE)  // Safety 不过则整体为 0
                .threshold(0.6)
                .build()
) {
    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        return String.format("用户问题：%s\n模型回复：%s",
                inputData.get("query"),
                apiCompletionResult.get("response"));
    }
};
```

### 示例：Few-shot 锚点（提升打分一致性）

配置 `anchors` 可以为 LLM 提供具体的示例参照，显著提升不同样本之间的打分一致性：

```java
RubricCriteria accuracyCriteria = RubricCriteria.builder()
        .name("Accuracy")
        .definition("回复的事实准确程度")
        .scoreType(RubricScoreType.STEPPED)
        .maxScore(5).minScore(0).passScore(3)
        .anchors(Arrays.asList(
                RubricCriteria.ScoringAnchor.builder()
                        .score(5)
                        .description("回复中所有事实陈述均正确，与权威来源完全吻合，无任何捏造")
                        .build(),
                RubricCriteria.ScoringAnchor.builder()
                        .score(3)
                        .description("核心事实基本正确，但有 1~2 处细节不准确或表述模糊")
                        .build(),
                RubricCriteria.ScoringAnchor.builder()
                        .score(1)
                        .description("多处关键事实错误或大量内容无中生有，误导性强")
                        .build()
        ))
        .build();
```

### 示例：条件执行（condition / skipScore）

当某些样本不满足某维度的评估前提时（如没有检索上下文，就无需评估上下文相关性），可通过 `condition` 动态跳过该维度，并用 `skipScore` 填充默认分：

```java
RubricCriteria contextRelevance = RubricCriteria.builder()
        .name("ContextRelevance")
        .definition("回复是否充分利用了检索到的上下文信息")
        .scoreType(RubricScoreType.STEPPED)
        .maxScore(5).minScore(0).passScore(3)
        // 只有样本携带 context 字段时才评估此维度
        .condition(dataItem -> dataItem.getInputData().get("context") != null)
        // 无上下文时给中性分（passScore），不拉低整体分数
        .skipScore(3.0)
        .build();
```

**`skipScore` 策略选择：**

| 赋值 | 含义 | 适用场景 |
|------|------|----------|
| `0.0`（默认） | 保守策略，视为未通过 | 跳过等同于失败时 |
| `passScore` | 中性策略，视为刚好通过 | 不适用时不影响整体 |
| `maxScore` | 豁免策略，视为满分通过 | 该维度对此类样本不适用时 |

### 示例：关闭归一化（normalizeScore=false）

默认情况下框架会将各维度分数归一化到 `[0, 1]` 再合并，`totalScore` 始终为 1.0。若希望保留各维度的原始量程（如 1~5 分），可关闭归一化：

```java
RubricBasedScorerConfig config = RubricBasedScorerConfig.builder()
        .metricName("原始分模式评估")
        .llmService(myLLMService)
        .criteria(Arrays.asList(
                RubricCriteria.builder().name("Accuracy").definition("准确性")
                        .scoreType(RubricScoreType.STEPPED).maxScore(5).minScore(0).passScore(3).weight(2.0).build(),
                RubricCriteria.builder().name("Fluency").definition("流畅性")
                        .scoreType(RubricScoreType.STEPPED).maxScore(10).minScore(0).passScore(6).weight(1.0).build()
        ))
        .mergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
        .normalizeScore(false)   // 关闭归一化，保留原始分量程
        .build();
// Accuracy=4, Fluency=7, weight=2:1
// score      = (4*2 + 7*1) / (2+1) = 5.0
// totalScore = (5*2 + 10*1) / (2+1) = 6.67
// scoreRate  = 5.0 / 6.67 ≈ 0.75
```

> **注意**：即使关闭归一化，`STAR_GATE` / `LOGICAL_AND` / `COMPLETION_RATE` 策略的 **passRate 门控判断仍然基于归一化分数**，以保证语义一致性。

### ScorerResult 额外 extra 字段

`RubricBasedScorer` 的评估结果会在 `extra` 中携带各维度的详细信息，可在报告层或后续处理中使用：

| extra key | 类型 | 说明 |
|-----------|------|------|
| `criteria_raw_scores` | `Map<String, Double>` | 各维度原始分数 |
| `criteria_norm_scores` | `Map<String, Double>` | 各维度归一化分数 |
| `criteria_reasons` | `Map<String, String>` | 各维度打分理由 |
| `criteria_reasonings` | `Map<String, String>` | 各维度 CoT 推理过程 |
| `merge_strategy` | `String` | 本次使用的合并策略名 |

---

## MultiCheckerBasedScorer

将多个 **Checker（检查器）** 组合起来，每个 Checker 负责一个检查维度，最终分数由各 Checker 的结果汇总而来。详见 [检查器文档](./checker.md)。

```java
MultiCheckerBasedScorer multiChecker = new MultiCheckerBasedScorer(
        MultiCheckerBasedScorerConfig.builder()
                .metricName("多维度综合检查")
                .strategy(new SumMergeCheckerScoreStrategy())
                .build()
) {
    @Override
    public List<Checker> prepareCheckers(DataItem dataItem) {
        return ListUtils.of(
            new KeywordChecker(),    // 检查关键词覆盖
            new FormatChecker(),     // 检查格式规范
            new SafetyChecker()      // 检查内容安全
        );
    }
};
```

---

## 打分策略

`Begin` 节点的 `scoreStrategy` 配置决定如何把多个 `Scorer` 的分数**汇总成最终评测结果**：

| 策略类 | 说明 |
|--------|------|
| `SumScoreStrategy` | 分数值求和（默认） |
| `MinScoreStrategy` | 取最小分数值 |
| `MaxScoreStrategy` | 取最大分数值 |
| `AvgScoreValueStrategy` | 分数值取平均 |
| `SumScoreRateStrategy` | 得分率求和 |
| `MinScoreRateStrategy` | 取最小得分率 |
| `MaxScoreRateStrategy` | 取最大得分率 |
| `AvgScoreRateStrategy` | 得分率取平均 |
| 自定义 | 实现 `ScoreStrategy` 接口 |

### 示例：自定义打分策略

```java
Begin begin = new Begin(
        BeginConfig.builder()
                .threshold(2.5)   // 总分 ≥ 2.5 才通过
                .scoreStrategy(new SumScoreStrategy())  // 各项分数相加
                .build()
);
```

### star 必过指标

当某个 Scorer 配置 `star=true` 时，该指标**不通过则整体必定不通过**，不管其他指标得了多少分：

```java
// 示例：内容安全是必过指标，安全检测通过才能算整体通过
Scorer safetyScorer = new Scorer(
        ScorerConfig.builder()
                .metricName("内容安全")
                .star(true)     // 必过！
                .threshold(1.0)
                .build()
) { ... };

