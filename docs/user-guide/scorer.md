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

