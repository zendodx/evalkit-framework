---
layout: default
title: 评测门面（EvalFacade）
parent: 用户指南
nav_order: 14
has_toc: true
---

# 评测门面（EvalFacade）

## 概述

**评测门面（EvalFacade）** 是 EvalKit 框架的**顶层控制器**，负责协调整个评测任务的生命周期：数据加载 → 评测执行 → 结果上报。

你只需要配置好评测门面，调用 `run()` 方法，框架就会自动完成所有步骤。


## 三种评测模式

| 评测模式 | 类名 | 适用场景 |
|---|---|---|
| **全量评测** | `FullEvalFacade` | 数据量较小、一次性评测，中断后重新开始 |
| **增量评测** | `DeltaEvalFacade` | 数据量大、需要断点续评、支持周期上报进度 |
| **有序增量评测** | `OrderedDeltaEvalFacade` | 在增量评测基础上，保证同组数据（如同一会话）按顺序处理 |


## 通用生命周期

所有评测门面都遵循相同的生命周期：

```
run()
  ├── init()                    初始化
  └── executeWrapper()
        ├── beforeExecute()     执行前钩子
        ├── execute()
        │     ├── loadDataWrapper()
        │     │     ├── beforeLoadData()  加载数据前钩子
        │     │     ├── loadData()        加载数据
        │     │     └── afterLoadData()   加载数据后钩子
        │     ├── evalWrapper()
        │     │     ├── beforeEval()      评测前钩子
        │     │     ├── eval()            执行评测
        │     │     └── afterEval()       评测后钩子
        │     └── reportWrapper()
        │           ├── beforeReport()    上报前钩子
        │           ├── report()          执行上报
        │           └── afterReport()     上报后钩子
        └── afterExecute()      执行后钩子
```


## 公共基础配置（`EvalConfig`）

所有评测门面配置都继承自 `EvalConfig`：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `taskName` | String | `EvalTest_yyyyMMddHHmmss` | 任务名称（增量评测时同一任务需保持一致） |
| `offset` | int | 0 | 数据加载偏移量（从第几条开始） |
| `limit` | int | -1 | 加载数量（-1 表示加载全部） |
| `threadNum` | int | 1 | 并发线程数 |
| `passScore` | double | 0.0 | 评测通过分数线 |
| `extra` | `Map<String, Object>` | null | 额外配置参数 |
| `openInjectData` | boolean | false | 是否将输入数据注入到上下文 |
| `injectDataIndex` | boolean | true | 注入数据索引 |
| `injectInputData` | boolean | true | 注入输入数据 |
| `injectApiCompletionResult` | boolean | true | 注入接口调用结果 |
| `injectEvalResult` | boolean | true | 注入评测结果 |
| `injectExtra` | boolean | true | 注入额外数据 |

> 💡 **环境变量覆盖**：所有配置参数都可以通过 JVM 系统属性覆盖，例如 `-DthreadNum=8 -DpassScore=0.7`，方便在不修改代码的情况下动态调整配置。


## 模式一：全量评测（FullEvalFacade）

### 适用场景

- 数据量较小（千条以内）
- 一次性评测，不需要断点续评
- 配置简单、上手快

### 特点

- 数据全部加载到内存
- 同步执行：加载 → 评测 → 上报
- 如果中途中断，下次需要从头开始

### 配置说明（`FullEvalConfig`）

在 `EvalConfig` 基础上增加：

| 参数 | 类型 | 说明 |
|---|---|---|
| `dataLoader` | `DataLoader` | 数据加载器（**必填**） |
| `evalWorkflow` | `Workflow` | 评测工作流（**必填**） |
| `reportWorkflow` | `Workflow` | 结果上报工作流（**必填**） |

### 完整示例

```java
// 1. 数据加载器：从Excel文件加载测试数据
ExcelDataLoader dataLoader = new ExcelDataLoader(
    "src/test/resources/eval_data.xlsx",
    ExcelDataLoaderConfig.builder().build()
);

// 2. 评测工作流：调用接口 + 打分
Workflow evalWorkflow = Workflow.builder()
    .addNode(new MyHotelSearchApiCompletion())  // 调用被测接口
    .addNode(new VectorSimilarityScorer(        // 向量相似度打分
        VectorSimilarityScorerConfig.builder()
            .threshold(0.7)
            .build()
    ))
    .build();

// 3. 上报工作流：统计 + 导出Excel + 生成HTML报告
Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())                          // 基础统计
    .addNode(new ExcelReporter("hotel_eval_result"))      // 导出Excel
    .addNode(new HtmlReporter("hotel_eval_report"))       // 生成HTML报告
    .build();

// 4. 组装全量评测配置
FullEvalConfig config = FullEvalConfig.builder()
    .taskName("酒店搜索质量评测-v1.0")
    .dataLoader(dataLoader)
    .evalWorkflow(evalWorkflow)
    .reportWorkflow(reportWorkflow)
    .threadNum(5)         // 5线程并发
    .passScore(0.6)       // 通过分数线：0.6
    .limit(500)           // 只评测前500条
    .build();

// 5. 运行评测
new FullEvalFacade(config).run();
```


## 模式二：增量评测（DeltaEvalFacade）

### 适用场景

- 数据量大（数千条甚至更多）
- 需要支持**断点续评**（中途中断后可以从断点恢复）
- 需要**实时查看进度**（周期性上报阶段结果）
- 对评测速度有要求（高并发处理）

### 特点

- 使用**嵌入式 ActiveMQ** 作为消息队列缓存待处理数据
- 使用**嵌入式 SQLite** 持久化已处理的结果
- 支持**断点续评**：中断后再次运行会自动识别已处理数据，跳过不重复执行
- 支持**周期上报**：每隔固定时间自动上报当前已完成的评测结果

### 底层架构

```
加载数据
  ↓
全量写入 ActiveMQ 队列
  ↓
多线程消费 MQ（并发评测）
  ↓
评测结果落库 SQLite（事务保障，中断不丢数据）
  ↓
定时上报（每30秒一次）→ 上报到 reportWorkflow
  ↓
全部消费完成 → 最终上报
```

### 配置说明（`DeltaEvalConfig`）

在 `FullEvalConfig` 基础上增加：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `batchSize` | int | 10 | 每次从 MQ 批量拉取的消息数 |
| `reportInterval` | int | 30 | 结果上报间隔（秒） |
| `mqReceiveTimeout` | int | 10000 | MQ 消息接收超时时间（毫秒） |
| `enableResume` | boolean | true | 是否开启断点续评 |
| `messageProcessMaxTime` | long | 60 | 单条消息处理最大时间（秒） |

> ⚠️ **断点续评说明**：
> - 开启 `enableResume=true`：再次运行时会读取上次的 MQ 和 SQLite 数据，从断点继续
> - 关闭 `enableResume=false`：每次运行都会清空缓存数据，从头开始
> - **同一任务的 `taskName` 必须保持一致**，框架用它来识别是否是同一个评测任务

### 完整示例

```java
// 评测工作流（与FullEvalFacade相同）
Workflow evalWorkflow = Workflow.builder()
    .addNode(new MyApiCompletion())
    .addNode(new PromptBasedScorer(scorerConfig))
    .build();

// 上报工作流（注意：增量评测会周期性调用，确保上报器支持覆盖写入）
Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())
    .addNode(new JsonReporter("live_result"))   // JSON格式，方便增量覆写
    .build();

// 增量评测配置
DeltaEvalConfig config = DeltaEvalConfig.builder()
    .taskName("大规模评测-2024Q4")          // ⚠️ 断点续评时任务名必须一致
    .dataLoader(new ExcelDataLoader("big_dataset.xlsx", ExcelDataLoaderConfig.builder().build()))
    .evalWorkflow(evalWorkflow)
    .reportWorkflow(reportWorkflow)
    .threadNum(10)                          // 10线程并发
    .passScore(0.7)
    .batchSize(20)                          // 每次拉取20条消息
    .reportInterval(60)                     // 每60秒上报一次进度
    .enableResume(true)                     // 开启断点续评
    .messageProcessMaxTime(120)             // 单条消息最长处理120秒
    .build();

// 运行评测
DeltaEvalFacade facade = new DeltaEvalFacade(config);
facade.run();

// 可以随时查询进度
System.out.println("已处理：" + facade.getProcessedDataCount());
System.out.println("待处理：" + facade.getRemainDataCount());
System.out.println("总量：" + facade.getTotalCount());
```

### 断点续评场景演示

```
第一次运行：
- 共1000条数据 → 全部写入MQ
- 处理了300条 → 程序崩溃中断

第二次运行（taskName相同，enableResume=true）：
- 检测到SQLite有300条记录，MQ有700条待处理消息
- 从第301条继续处理，已处理的300条跳过
- 最终完成1000条评测
```

### 缓存文件位置

增量评测的中间数据保存在 `eval_cache_data/` 目录下，以 `taskName` 的 UUID 命名：

```
eval_cache_data/
├── {taskNameUuid}/     # ActiveMQ数据目录
└── {taskNameUuid}.db   # SQLite数据库文件
```


## 模式三：有序增量评测（OrderedDeltaEvalFacade）

### 适用场景

多轮对话评测场景中，**同一会话的多轮 Query 必须按顺序依次调用接口**（因为后一轮依赖前一轮的上下文）。

`OrderedDeltaEvalFacade` 在 `DeltaEvalFacade` 的基础上，增加了**分组有序执行**的能力：
- 相同 `orderKey` 的数据会被分配到同一个线程
- 同一线程内按照自定义比较器的顺序串行执行
- 不同 `orderKey` 的数据仍然可以并发处理

### 需要实现的方法

继承 `OrderedDeltaEvalFacade` 并实现以下两个方法：

| 方法 | 说明 |
|---|---|
| `prepareOrderKey(InputData)` | 返回数据的分组 key（如 `sessionId`，同一会话的数据会分配到同一线程） |
| `prepareComparator()` | 返回同组内数据的排序比较器（如按 `round` 字段升序） |

### 完整示例

```java
public class OrderedHotelEvalFacade extends OrderedDeltaEvalFacade {
    public OrderedHotelEvalFacade(DeltaEvalConfig config) {
        super(config);
    }

    @Override
    public String prepareOrderKey(InputData inputData) {
        // 同一sessionId的数据放在同一线程处理
        return (String) inputData.getInputItem().get("sessionId");
    }

    @Override
    public Comparator<InputData> prepareComparator() {
        // 按round字段升序（第1轮 → 第2轮 → 第3轮）
        return Comparator.comparingInt(inputData ->
            Integer.parseInt(inputData.getInputItem().get("round").toString())
        );
    }
}

// 使用
DeltaEvalConfig config = DeltaEvalConfig.builder()
    .taskName("多轮对话有序评测")
    .dataLoader(new ExcelDataLoader("multi_turn_data.xlsx", ExcelDataLoaderConfig.builder().build()))
    .evalWorkflow(Workflow.builder()
        .addNode(new MyMultiTurnApiCompletion())  // 有状态的多轮对话API调用
        .addNode(new VectorSimilarityScorer(scorerConfig))
        .build())
    .reportWorkflow(Workflow.builder()
        .addNode(new BasicCounter())
        .addNode(new ExcelReporter("multi_turn_result"))
        .build())
    .threadNum(5)      // 5个并发"槽"，每个槽处理一组会话
    .batchSize(50)
    .enableResume(true)
    .build();

new OrderedHotelEvalFacade(config).run();
```


## 三种模式选择指南

```
数据量 < 1000 条？
    ↓ 是
  → 使用 FullEvalFacade（简单、快速）

    ↓ 否（数据量大）
  需要保证同组数据顺序执行？（如多轮对话）
      ↓ 是
    → 使用 OrderedDeltaEvalFacade

      ↓ 否
    → 使用 DeltaEvalFacade
```


## 进度监控

所有评测门面都提供了进度查询方法：

```java
// 全量评测
FullEvalFacade facade = new FullEvalFacade(config);
facade.run();
System.out.println("已完成：" + facade.getProcessedDataCount());

// 增量评测（可在评测过程中调用）
DeltaEvalFacade deltaFacade = new DeltaEvalFacade(config);
// 在另一个线程中监控进度
new Thread(() -> {
    while (deltaFacade.getRemainDataCount() > 0) {
        System.out.printf("进度：%d/%d%n",
            deltaFacade.getProcessedDataCount(),
            deltaFacade.getTotalCount());
        Thread.sleep(10000);
    }
}).start();
deltaFacade.run();
```


## 注意事项

1. **工作流需要支持并行**：多线程场景下，同一个 `Workflow` 对象可能被多个线程同时使用（`DeltaEvalFacade` 会 `clone()` 工作流），请确保 `Scorer`、`ApiCompletion` 等节点的实现是线程安全的，或者通过 `clone()` 机制保证每个线程有独立的节点实例。

2. **增量评测的 `taskName` 约束**：使用 `DeltaEvalFacade` 时，`taskName` 用来定位 MQ 和数据库文件。同一批次的评测（包括断点续评）必须使用相同的 `taskName`；不同批次的评测必须使用不同的 `taskName`，否则可能造成数据混乱。

3. **上报工作流的幂等性**：增量评测的上报是周期性的（可能多次执行），上报器应当支持覆盖写入（如 `JsonReporter`、`ExcelReporter`），避免生成大量重复文件。

4. **缓存清理**：当一次评测任务完全结束后，`eval_cache_data/` 目录下的缓存文件不会自动清理。如果你的磁盘空间有限，可以在评测结束后手动删除对应的缓存目录。

