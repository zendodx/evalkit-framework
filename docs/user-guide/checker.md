# 检查器（Checker）

检查器是 `MultiCheckerBasedScorer` 的内部组件，负责对评测数据进行**单个维度的细粒度检查**，每个检查器包含若干个**检查项（CheckItem）**，最终分数由所有检查项的结果汇总而来。

---

## 核心概念

```
MultiCheckerBasedScorer
  ├── Checker A（如：格式检查）
  │     ├── CheckItem: "是否包含必要字段"  （规则检查）
  │     └── CheckItem: "JSON是否合法"      （规则检查）
  └── Checker B（如：质量评估）
        ├── CheckItem: "内容完整性"         （LLM检查）
        └── CheckItem: "语言流畅性"         （LLM检查）
```

**层次关系**：
- `Scorer`（评估器）包含多个 `Checker`（检查器）
- `Checker`（检查器）包含多个 `CheckItem`（检查项）
- `CheckItem` 是最小的打分单元

---

## CheckItem（检查项）

每个检查项代表一个具体的评分标准。

### 字段说明

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `name` | String | `未命名检查项` | 检查项名称（在报告中显示） |
| `score` | double | 0 | 检查项得分 |
| `totalScore` | double | 1.0 | 检查项满分 |
| `reason` | String | `""` | 打分理由 |
| `weight` | double | 1.0 | 权重（影响检查器总分计算） |
| `star` | boolean | false | 是否为**必过检查项**（不通过则整个检查器失败） |
| `support` | boolean | true | 是否执行此检查（false 时直接使用 `defaultScore`） |
| `executed` | boolean | false | 是否已执行（框架自动设置） |
| `defaultScore` | double | 0.0 | 当 `support=false` 或检查失败时的默认得分 |
| `checkDescription` | String | `""` | 检查描述（LLM 检查时会发给模型作为参考） |
| `checkMethod` | CheckMethod | NONE | 检查方式：`RULE`（规则检查）/ `LLM`（大模型检查）/ `NONE`（未执行） |

### 定义检查项

```java
// 基础检查项
CheckItem basicItem = CheckItem.builder()
        .name("关键词覆盖")
        .totalScore(1.0)
        .build();

// 必过检查项（不通过则整个检查器必定不通过）
CheckItem starItem = CheckItem.builder()
        .name("内容安全")
        .star(true)
        .totalScore(1.0)
        .build();

// 带权重的检查项（优先级更高的检查项可设置更大权重）
CheckItem weightedItem = CheckItem.builder()
        .name("准确性")
        .weight(2.0)     // 权重为2，最终分数贡献是其他检查项的2倍
        .totalScore(1.0)
        .build();

// 可能不支持检查的检查项（当数据不满足条件时跳过）
CheckItem conditionalItem = CheckItem.builder()
        .name("图片描述检查")
        .support(false)      // 当没有图片时，跳过此检查
        .defaultScore(1.0)   // 跳过时默认给满分
        .build();

// 带 LLM 描述的检查项（发给大模型作为打分标准）
CheckItem llmItem = CheckItem.builder()
        .name("回答完整性")
        .checkDescription("检查答案是否完整回答了用户的所有问题。" +
                         "满分1分：完整回答所有问题；" +
                         "0.5分：回答了部分问题；" +
                         "0分：完全没有回答")
        .totalScore(1.0)
        .build();
```

---

## Checker 接口

```java
public interface Checker {
    boolean support(DataItem dataItem);   // 是否对该数据执行检查
    void checkWrapper(DataItem dataItem); // 执行检查（包含钩子）
    double getScore();                    // 获取最终得分
    double getTotalScore();               // 获取满分
    String getReason();                   // 获取不通过原因
    String getCheckName();                // 获取检查器名称
    List<CheckItem> getCheckItems();      // 获取检查项列表
    boolean isStar();                     // 是否为必过检查器
}
```

---

## AbstractChecker（抽象基类）

封装了检查器的通用逻辑（生命周期管理、分数汇总等），建议直接继承此类。

### CheckerConfig 配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `name` | 检查器名称 | `未命名检查` |
| `strategy` | 检查项分数合并策略 | `SumCheckItemScoreMergeStrategy`（求和） |
| `threshold` | 检查通过阈值 | 0.0 |
| `star` | 是否为必过检查器 | false |
| `totalScore` | 检查器总分 | 0.0（建议手动设置或通过 `getTotalScore()` 动态计算） |

### 检查项分数合并策略

| 策略类 | 说明 |
|--------|------|
| `SumCheckItemScoreMergeStrategy` | 各检查项得分求和（默认） |
| `AvgCheckItemScoreMergeStrategy` | 各检查项得分取平均 |
| `MinCheckItemScoreMergeStrategy` | 取最小检查项得分 |
| 自定义 | 实现 `CheckItemScoreMergeStrategy` 接口 |

---

## 示例：规则检查器

纯规则检查，不调用 LLM，速度快，适合格式、字段是否存在等确定性检查。

```java
/**
 * 检查回复是否包含必要字段（规则检查）
 */
public class RequiredFieldsChecker extends AbstractChecker {

    // 声明检查项（类成员变量，复用同一对象）
    private final CheckItem hasResponse = CheckItem.builder()
            .name("包含response字段")
            .checkDescription("回复中必须包含 response 字段")
            .star(true)          // 必过，没有回复内容则直接不通过
            .build();

    private final CheckItem hasTraceId = CheckItem.builder()
            .name("包含traceId字段")
            .checkDescription("回复中应包含 traceId 字段，用于问题追踪")
            .build();

    public RequiredFieldsChecker() {
        super(CheckerConfig.builder()
                .name("必填字段检查")
                .totalScore(2.0)   // 两个检查项各 1 分，满分 2 分
                .build());
    }

    @Override
    public boolean support(DataItem dataItem) {
        // 始终执行检查
        return true;
    }

    @Override
    protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
        return ListUtils.of(hasResponse, hasTraceId);
    }

    @Override
    protected void check(DataItem dataItem) {
        ApiCompletionResult result = dataItem.getApiCompletionResult();

        // 检查 response 字段
        String response = result.get("response");
        if (StringUtils.isNotEmpty(response)) {
            hasResponse.setScore(1.0);
            hasResponse.setReason("response 字段存在且不为空");
        } else {
            hasResponse.setScore(0.0);
            hasResponse.setReason("response 字段为空或不存在");
        }
        hasResponse.setExecuted(true);
        hasResponse.setCheckMethod(CheckMethod.RULE);

        // 检查 traceId 字段
        String traceId = result.get("traceId");
        if (StringUtils.isNotEmpty(traceId)) {
            hasTraceId.setScore(1.0);
            hasTraceId.setReason("traceId 字段存在");
        } else {
            hasTraceId.setScore(0.0);
            hasTraceId.setReason("traceId 字段缺失");
        }
        hasTraceId.setExecuted(true);
        hasTraceId.setCheckMethod(CheckMethod.RULE);
    }

    @Override
    public double getTotalScore() {
        return 2.0;  // 满分 = 2
    }
}
```

---

## 示例：LLM 检查器

借助大模型的理解能力进行评判，适合语义类、质量类的复杂检查。

> 继承 `LLMBasedChecker` 可自动处理 LLM 调用、结果解析、检查项更新等逻辑。

```java
/**
 * 使用 LLM 检查回复的质量（LLM检查）
 */
public class QualityChecker extends LLMBasedChecker {

    private final CheckItem completeness = CheckItem.builder()
            .name("完整性")
            .checkDescription(
                "检查答案是否完整回答了所有问题。" +
                "1分：完整；0.5分：部分回答；0分：完全未回答")
            .build();

    private final CheckItem fluency = CheckItem.builder()
            .name("流畅性")
            .checkDescription(
                "检查语言是否自然流畅，无语法错误。" +
                "1分：自然流畅；0.5分：有小瑕疵；0分：语言生硬或有明显错误")
            .build();

    public QualityChecker(LLMService llmService) {
        super(LLMBasedCheckerConfig.builder()
                .name("质量检查")
                .llmService(llmService)
                .totalScore(2.0)
                .build());
    }

    @Override
    protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
        return ListUtils.of(completeness, fluency);
    }

    @Override
    protected String prepareUserPrompt(DataItem dataItem, int round) {
        // 准备传给 LLM 的用户输入（round 用于多轮对话场景）
        String query    = dataItem.getInputData().get("query");
        String response = dataItem.getApiCompletionResult().get("response");
        return String.format("用户问题：%s\n模型回复：%s", query, response);
    }

    @Override
    protected boolean needCheck(DataItem dataItem, int round) {
        // round 为 1 时执行检查（单轮检查固定返回 true）
        return round == 1;
    }

    @Override
    public boolean support(DataItem dataItem) {
        // 只有接口调用成功的数据才执行 LLM 检查
        return dataItem.getApiCompletionResult().isSuccess();
    }

    @Override
    public double getTotalScore() {
        return 2.0;
    }
}
```

---

## 多轮对话检查（LLMBasedChecker 高级用法）

`LLMBasedChecker` 支持对**多轮对话**的每一轮分别检查，通过 `beginRound` / `endRound` 配置和 `needCheck(dataItem, round)` 控制。

```java
// 检查多轮对话，只检查第 2、3 轮
public class MultiTurnChecker extends LLMBasedChecker {

    public MultiTurnChecker(LLMService llmService) {
        super(LLMBasedCheckerConfig.builder()
                .name("多轮对话检查")
                .llmService(llmService)
                .beginRound(1)   // 从第 1 轮开始
                .endRound(3)     // 到第 3 轮结束
                .build());
    }

    @Override
    protected boolean needCheck(DataItem dataItem, int round) {
        // 只检查第 2 轮和第 3 轮（第 1 轮跳过）
        return round >= 2;
    }

    @Override
    protected String prepareUserPrompt(DataItem dataItem, int round) {
        // 根据轮次从 inputData 中取对应的数据
        String query    = dataItem.getInputData().get("query_" + round);
        String response = dataItem.getApiCompletionResult().get("response_" + round);
        return String.format("第%d轮 - 用户：%s\n第%d轮 - AI回复：%s", round, query, round, response);
    }

    // ... 其他实现略
}
```

---

## 完整示例：组合多个检查器

```java
MultiCheckerBasedScorer complexScorer = new MultiCheckerBasedScorer(
        MultiCheckerBasedScorerConfig.builder()
                .metricName("综合质量评测")
                .strategy(new SumMergeCheckerScoreStrategy())  // 各检查器分数相加
                .dynamicTotalScore(true)   // 总分由运行时各检查器totalScore之和决定
                .build()
) {
    @Override
    public List<Checker> prepareCheckers(DataItem dataItem) {
        return ListUtils.of(
            new RequiredFieldsChecker(),          // 规则检查：必填字段（满分2）
            new QualityChecker(myLLMService)       // LLM检查：质量评估（满分2）
        );
    }
};

// 总满分 = 2 + 2 = 4，阈值设为 2.0 则超过 50% 即通过

