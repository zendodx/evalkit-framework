---
layout: default
title: 调试器（Debugger）
parent: 用户指南
nav_order: 15
has_toc: true
---

# 调试器（Debugger）

## 概述

**调试器（Debugger）** 是 EvalKit 框架中专门用于**调试和开发阶段**的工具组件。

在开发评测流程时，你通常需要这样一个能力：**跳过数据加载和接口调用，直接注入已有的评测结果数据，然后只测试评分逻辑或上报逻辑**。这正是调试器的设计目的。

举一个具体例子：
- 你已经完成了一次完整评测，结果保存在 JSON 文件中
- 你想修改统计逻辑后重新统计，但不想再次调用耗时的接口
- 此时用 `JsonFileDebugger` 加载上次的结果，直接跳到统计和上报步骤即可


## 类继承关系

```
WorkflowNode（工作流节点）
└── Debugger（抽象调试器基类）
    ├── JsonFileDebugger    从 JSON 文件加载数据并注入上下文
    └── JsonStringDebugger  从 JSON 字符串加载数据并注入上下文
```


## 核心机制

调试器通过**向工作流上下文中直接注入数据**来实现"跳过前置步骤"的效果：

1. 调试器作为工作流的**第一个节点**执行
2. 它从 JSON 文件或 JSON 字符串中读取上一次的评测结果数据
3. 将数据注入到工作流上下文（`WorkflowContext`）中
4. 后续节点（如统计器、上报器）直接使用注入的数据，无需再走数据加载和接口调用


## 调试器参数说明

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `offset` | int | 0 | 从第几条数据开始（用于分页调试） |
| `limit` | int | -1 | 加载条数（-1 表示加载全部） |
| `containsEvalResult` | boolean | true | 注入的数据是否已包含评测结果 |

### `containsEvalResult` 参数详解

这个参数控制注入数据时如何处理评测结果字段：

- **`true`（默认）**：保留 JSON 数据中已有的评测结果，适合"只重新统计、不重新评测"的场景
- **`false`**：清空 JSON 数据中的评测结果，重新初始化一个空的 `EvalResult`，适合"数据已有接口调用结果，需要重新评测打分"的场景


## 内置实现

### 1. JsonFileDebugger — 从 JSON 文件加载

从本地 JSON 文件（通常是 `JsonReporter` 上次保存的结果）中读取评测数据，注入到工作流上下文。

**构造方法：**

```java
// 最简单用法：加载全部数据，保留评测结果
new JsonFileDebugger(new File("attachments/result.json"));

// 指定分页：跳过前100条，只取接下来200条
new JsonFileDebugger(100, 200, new File("attachments/result.json"));

// 不保留评测结果（重新评测）
new JsonFileDebugger(false, new File("attachments/result.json"));

// 完整参数
new JsonFileDebugger(offset, limit, containsEvalResult, new File("attachments/result.json"));
```

**使用场景：**

```java
// 场景：上次评测结果保存在 result.json，现在想改统计逻辑后重新统计
Workflow workflow = Workflow.builder()
    .addNode(new JsonFileDebugger(new File("attachments/result.json")))  // ① 注入上次结果
    .addNode(new BasicCounter())                                         // ② 重新统计
    .addNode(new MetricCounter())                                        // ② 重新统计
    .addNode(new ExcelReporter("new_stats"))                             // ③ 导出新结果
    .build();

FullEvalConfig config = FullEvalConfig.builder()
    .taskName("重新统计")
    .dataLoader(new JsonDataLoader("attachments/result.json", ...))  // 数据加载器仍需配置（但会被调试器覆盖）
    .evalWorkflow(workflow)
    .reportWorkflow(Workflow.builder().addNode(new StdReporter()).build())
    .build();

new FullEvalFacade(config).run();
```

> 💡 **最佳实践**：通常将调试器直接放在 `evalWorkflow` 的第一个节点，它会覆盖 `loadData()` 阶段加载的数据，或者更简单地，专门构建一个只含调试器和后续处理节点的工作流。


### 2. JsonStringDebugger — 从 JSON 字符串加载

与 `JsonFileDebugger` 类似，但数据来源是 JSON 字符串，适合在**单元测试**或**动态构造数据**的场景。

**构造方法：**

```java
// 从JSON字符串加载
new JsonStringDebugger(jsonString);

// 不保留评测结果
new JsonStringDebugger(false, jsonString);
```

**单元测试中使用示例：**

```java
@Test
public void testBasicCounter() {
    // 准备测试数据（JSON格式，包含已有评测结果）
    String testData = """
        {
          "dataItems": [
            {
              "dataIndex": 0,
              "inputData": {"query": "北京酒店"},
              "apiCompletionResult": {"response": "为您找到以下酒店..."},
              "evalResult": {
                "score": 0.8,
                "passed": true,
                "scorerResults": []
              }
            },
            {
              "dataIndex": 1,
              "inputData": {"query": "上海餐厅"},
              "apiCompletionResult": {"response": "为您找到以下餐厅..."},
              "evalResult": {
                "score": 0.5,
                "passed": false,
                "scorerResults": []
              }
            }
          ],
          "countResult": {}
        }
        """;

    // 构建只含调试器的工作流
    Workflow workflow = Workflow.builder()
        .addNode(new JsonStringDebugger(testData))
        .addNode(new BasicCounter())
        .addNode(new StdReporter())
        .build();

    // 执行并验证
    WorkflowContext ctx = new WorkflowContext();
    workflow.setWorkflowContext(ctx);
    workflow.execute();

    Map<String, String> countResults = WorkflowContextOps.getCountResults(ctx);
    // 验证统计结果
    assertNotNull(countResults.get("BasicCounter"));
}
```


## 调试数据的 JSON 格式

调试器读取的 JSON 文件格式与 `JsonReporter` 输出的格式完全一致，主要结构如下：

```json
{
  "dataItems": [
    {
      "dataIndex": 0,
      "inputData": {
        "dataIndex": 0,
        "inputItem": {
          "query": "帮我找北京的酒店",
          "sessionId": "xxx",
          "round": "1"
        }
      },
      "apiCompletionResult": {
        "dataIndex": 0,
        "response": "为您推荐以下酒店...",
        "extra": {}
      },
      "evalResult": {
        "dataIndex": 0,
        "score": 0.75,
        "passed": true,
        "threshold": 0.6,
        "scoreStrategyName": "AVG",
        "scorerResults": [
          {
            "scorerName": "VectorSimilarityScorer",
            "score": 0.75,
            "reason": "相似度计算：0.75"
          }
        ]
      },
      "extra": {}
    }
  ],
  "countResult": {
    "BasicCounter": "{\"passCount\":8,\"failCount\":2,\"passRate\":0.8,\"totalCount\":10}"
  }
}
```


## 典型使用场景

### 场景1：修改统计逻辑后重新统计（不重新评测）

```java
// 上次评测结果保存在 result.json
// 现在要换一个统计维度重新看数据

Workflow newStatsWorkflow = Workflow.builder()
    .addNode(new JsonFileDebugger(new File("attachments/result.json")))
    .addNode(new BasicCounter())
    .addNode(new AttributeCounterV2())  // 新增归因分析
    .addNode(new HtmlReporter("enhanced_report"))
    .build();

WorkflowContext ctx = new WorkflowContext();
newStatsWorkflow.setWorkflowContext(ctx);
newStatsWorkflow.execute();
```

### 场景2：对已有接口调用结果重新打分（不重新调用接口）

```java
// 已有接口调用结果，但打分逻辑改了，需要重新打分

Workflow reScoringWorkflow = Workflow.builder()
    // containsEvalResult=false：清空旧评分，重新打分
    .addNode(new JsonFileDebugger(false, new File("attachments/old_result.json")))
    .addNode(new PromptBasedScorer(newScorerConfig))  // 新的评分器
    .addNode(new BasicCounter())
    .addNode(new ExcelReporter("re_scored_result"))
    .build();
```

### 场景3：调试时只处理部分数据

```java
// 数据集有1000条，调试时只想测试前10条

Workflow debugWorkflow = Workflow.builder()
    .addNode(new JsonFileDebugger(0, 10, new File("attachments/result.json")))  // 只取前10条
    .addNode(new VectorSimilarityScorer(scorerConfig))
    .addNode(new StdReporter())
    .build();
```

### 场景4：单元测试评分器逻辑

```java
@Test
public void testPromptBasedScorer() {
    // 构造包含接口调用结果的测试数据（不含评测结果）
    String testData = buildTestDataWithApiResults();

    Workflow testWorkflow = Workflow.builder()
        .addNode(new JsonStringDebugger(false, testData))  // false: 重新评测
        .addNode(new PromptBasedScorer(scorerConfig))
        .addNode(new StdReporter())
        .build();

    WorkflowContext ctx = new WorkflowContext();
    testWorkflow.setWorkflowContext(ctx);
    testWorkflow.execute();

    // 验证评分结果
    List<DataItem> items = WorkflowContextOps.getDataItems(ctx);
    for (DataItem item : items) {
        assertTrue(item.getEvalResult().getScore() >= 0);
        assertTrue(item.getEvalResult().getScore() <= 1);
    }
}
```


## 调试器 vs 数据加载器

| 对比 | Debugger | DataLoader |
|---|---|---|
| **使用目的** | 开发调试，跳过耗时步骤 | 生产使用，加载真实数据 |
| **数据来源** | 上次评测结果的 JSON | 各种外部数据源 |
| **是否调用接口** | 否（直接注入结果） | 否（只加载输入数据） |
| **是否包含评测结果** | 可以包含（可配置） | 通常不包含 |
| **推荐使用场景** | 调试、单元测试、重新统计 | 正式评测 |


## 注意事项

1. **数据格式必须匹配**：调试器读取的 JSON 格式必须是 `JsonReporter` 输出的标准格式，如果手动构造 JSON，需要严格遵守格式要求。

2. **`containsEvalResult` 要根据场景设置**：
   - 如果你只是想重新统计，设为 `true`（保留评分，不重新评测）
   - 如果你想重新评测，设为 `false`（清空旧评分）

3. **调试器不影响生产评测**：调试器只应该在开发和测试阶段使用。生产评测中，工作流不应包含调试器节点。

4. **内存注意**：如果 JSON 文件非常大（几十万条数据），`JsonFileDebugger` 会一次性把所有数据加载到内存，需要注意内存消耗。可以通过 `offset` 和 `limit` 参数分批处理。

