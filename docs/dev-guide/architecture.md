---
layout: default
title: 框架总体架构
parent: 开发指南
nav_order: 0
has_toc: true
---

# 框架总体架构

本文档从开发者视角出发，系统性地描述 EvalKit Framework 的总体结构，包括 Maven 模块划分、核心包结构、各层职责，以及关键设计模式与扩展点。

## 一、项目模块概览

EvalKit Framework 采用多 Maven 模块组织，BOM 统一管理版本：

```
evalkit-bom (根 pom，版本管理)
├── evalkit-parent      ← 公共父 POM，统一依赖版本配置
├── evalkit-common      ← 通用工具层（工具类、HTTP 客户端、线程池等）
├── evalkit-workflow    ← DAG 工作流引擎层
├── evalkit-infra       ← 基础设施层（LLM 服务、嵌入式 MQ/DB）
├── evalkit-eval        ← 核心评测层（节点、Facade、模型）
└── evalkit-test        ← 测试辅助层
```

模块依赖方向（单向，无循环）：

```
evalkit-eval
  └── evalkit-infra
       └── evalkit-workflow
            └── evalkit-common
```

用户只需引入 `evalkit-eval` 即可获得完整功能；所有模块均不依赖 Spring，可嵌入任意 Java 项目。

---

## 二、各模块职责

### 2.1 evalkit-common（通用工具层）

提供与评测业务无关的通用能力，供其他所有模块使用。

| 包路径 | 职责 |
|--------|------|
| `utils.*` | 工具类集合：JSON、HTTP、文件、正则、NLP、时间、地址、数学等 |
| `client.http` | 基于 OkHttp 的 HTTP API 客户端，支持 SSE 流式响应 |
| `client.deepseek` | DeepSeek API 请求/响应模型，支持流式与非流式调用 |
| `thread.BatchRunner` | 批量并发任务执行器，基于 `CompletableFuture` 实现 |
| `thread.ThreadPoolManager` | 全局线程池管理，按 `PoolName` 命名隔离各场景线程池 |
| `thread.OrderedDispatcher` | 有序任务调度器，保证同 key 的任务串行执行 |

**线程池隔离设计：** 系统预定义了 6 个命名线程池，确保各场景互不阻塞：

| PoolName | 使用场景 |
|----------|---------|
| `DATA_WRAPPER` | 数据加载装饰器并发处理 |
| `API_COMPLETION` | 被测接口并发调用 |
| `SCORER` | 评估器并发打分 |
| `SCORER_CRITERIA` | Rubric 评估器内部维度并发 LLM 调用（与 SCORER 隔离，防死锁） |
| `MQ_CONSUME` | 增量评测 MQ 消息消费 |
| `DATA_GENERATOR` | 数据生成器并发处理 |

### 2.2 evalkit-workflow（DAG 工作流引擎层）

实现通用的 DAG（有向无环图）工作流引擎，是整个框架的执行骨架。

| 类/包 | 职责 |
|-------|------|
| `WorkflowNode` | 所有节点的抽象基类，实现 `Callable`，子类实现 `doExecute()` |
| `DAG` | DAG 图模型：节点（tasks）+ 入边（inEdges）+ 出边（outEdges） |
| `WorkflowContext` | 工作流上下文，以 Map 形式在节点间共享数据 |
| `WorkflowBuilder` | 流式构建器，提供 `link()` 方法声明节点依赖关系 |
| `Workflow` | 工作流执行器，封装 `DAG + WorkflowContext + TaskExecutor` |
| `TaskExecutor` | 拓扑排序 + 并发执行引擎，零入度节点可并行运行 |
| `WorkflowContextHolder` | 基于 `ThreadLocal` 的上下文持有者，执行完毕后自动清理 |

**DAG 执行原理：**

```
TaskExecutor 执行流程：
1. 找出所有零入度节点（无前驱节点），提交线程池并发执行
2. 节点执行完成后，检查其所有后继节点
3. 后继节点所有前驱均完成 → 添加到待执行队列
4. 重复步骤 1-3 直至所有节点完成
```

`WorkflowBuilder.link()` 支持灵活的连接模式：

```java
// 单对单
builder.link(nodeA, nodeB);
// 单对多（广播，nodeA 完成后 B/C 并行）
builder.link(nodeA, nodeB, nodeC);
// 多对一（汇聚，所有 scorers 完成后执行 counter）
builder.link(scorers, counter);
// 混合串联（单节点与集合任意组合）
builder.link(begin, dataLoader, apiCompletion, scorers, counter, reporter, end);
```

### 2.3 evalkit-infra（基础设施层）

封装外部中间件和服务，屏蔽底层细节。

| 类/包 | 职责 |
|-------|------|
| `service.llm.LLMService` | 大模型服务统一接口（`chat(prompt)`） |
| `service.llm.LLMServiceFactory` | 工厂模式，支持注册自定义大模型服务；内置 DeepSeek |
| `service.llm.LoadBalanceLLMService` | 负载均衡 LLM 服务，支持随机/轮询策略 |
| `service.chat.DynamicChatService` | 动态 Chat 服务，支持多轮对话上下文管理 |
| `server.mq.ActiveMQEmbeddedServer` | 嵌入式 ActiveMQ，用于增量评测的数据队列 |
| `server.sql.SQLiteEmbeddedServer` | 嵌入式 SQLite，用于增量评测的断点续评存储 |

**LLM 服务扩展方式：**

```java
// 注册自定义 LLM 服务
LLMServiceFactory.registerLLMService("custom-model", MyLLMService::new);

// 使用工厂创建服务
LLMService myService = LLMServiceFactory.createLLMService("custom-model", myConfig);
```

### 2.4 evalkit-eval（核心评测层）

框架的核心业务层，包含所有评测节点、数据模型和门面。

---

## 三、evalkit-eval 内部结构详解

### 3.1 包结构总览

```
com.evalkit.framework.eval
├── facade/           ← 评测门面（用户入口）
├── node/             ← 评测节点（工作流积木）
│   ├── begin/        ← 开始节点
│   ├── dataloader/   ← 数据加载器
│   ├── dataloader_wrapper/  ← 数据加载装饰器（可选）
│   ├── api/          ← 接口调用器
│   ├── api_wrapper/  ← 接口结果装饰器（可选）
│   ├── scorer/       ← 评估器（含 checker 子模块）
│   ├── counter/      ← 统计器
│   ├── reporter/     ← 结果上报器
│   ├── end/          ← 结束节点
│   ├── querygen/     ← Query 生成器
│   └── data_generator/ ← 数据生成器
├── model/            ← 数据模型
├── context/          ← 上下文操作工具
├── mock/             ← Mock 数据引擎
├── mapper/           ← SQLite 数据访问（增量评测）
├── constants/        ← 枚举与常量
└── exception/        ← 评测异常
```

### 3.2 评测节点继承体系

所有评测节点均继承自 `WorkflowNode`，形成如下继承树：

```
WorkflowNode（workflow 层基类）
├── Begin                    ← 初始化工作流上下文
├── DataLoader               ← 抽象：准备 InputData 列表
│   ├── CsvDataLoader
│   ├── JsonDataLoader / JsonFileDataLoader / JsonTextDataLoader
│   ├── ExcelDataLoader
│   ├── JdbcDataLoader
│   ├── ApiDataLoader
│   └── MultiDataLoader      ← 聚合多个 DataLoader
├── DataLoaderWrapper        ← 可选：对 InputData 进行 Mock/润色
│   ├── MockDataLoaderWrapper
│   ├── PolishDataLoaderWrapper
│   └── PromptDataLoaderWrapper
├── ApiCompletion            ← 抽象：调用被测接口
│   ├── HttpApiCompletion    ← 内置 HTTP 接口调用
│   └── OrderedApiCompletion ← 支持多轮对话上下文访问
├── ApiCompletionWrapper     ← 可选：转化/清洗接口返回结果
├── Scorer                   ← 抽象评估器（核心扩展点）
│   ├── VectorSimilarityScorer    ← TF-IDF 余弦相似度
│   ├── SemanticConsistencyScorer ← 语义一致性
│   ├── AnswerRelevancyScorer     ← 答案相关性
│   ├── PromptBasedScorer         ← 基于 Prompt 的 LLM 评估（抽象）
│   ├── RubricBasedScorer         ← 量规评估（多维度结构化打分，抽象）
│   ├── GSBScorer                 ← Good/Same/Bad 三档比较评估
│   ├── MultiCheckerBasedScorer   ← 基于多 Checker 的组合评估
│   ├── RouterScorer              ← 评估器路由分发
│   └── SecurityScorer            ← 安全性评估
├── Counter                  ← 抽象统计器
│   ├── BasicCounter         ← 通过率、平均分等基础指标
│   ├── MetricCounter        ← 按 Metric 维度统计
│   ├── RubricCounter        ← Rubric 评估专用统计
│   └── AttributeCounter / AttributeCounterV2  ← LLM 归因分析
├── Reporter                 ← 抽象上报器
│   ├── StdReporter          ← 控制台输出
│   ├── JsonReporter / FileReporter / JsonFileDebugger
│   ├── CsvReporter
│   ├── ExcelReporter
│   ├── JdbcReport           ← 写入数据库
│   └── ApiReporter          ← 调用 API 上报
└── End                      ← 评测收尾（自定义逻辑）
```

### 3.3 数据流模型

一次评测的数据流向：

```
InputData（原始输入）
    ↓ [DataLoader 加载]
DataItem（数据项，贯穿整个工作流）
    ├── dataIndex         ← 数据索引
    ├── inputData         ← 原始输入 Map
    ├── apiCompletionResult  ← 接口调用结果 Map
    └── evalResult        ← 评测结果（ScorerResult 列表聚合）
                              ├── score         ← 最终分数
                              ├── pass          ← 是否通过
                              ├── reason        ← 评测原因
                              └── scorerResults ← 各 Scorer 明细
```

**关键数据模型说明：**

| 模型类 | 职责 |
|--------|------|
| `InputData` | 评测输入数据，本质是 `Map<String, Object>` |
| `DataItem` | 工作流数据项，聚合单条 case 的全部中间结果 |
| `ApiCompletionResult` | 接口调用结果，含 `resultItem` Map 和时延信息 |
| `ScorerResult` | 单个评估器的打分结果（metric、score、totalScore、reason、pass） |
| `EvalResult` | 所有 ScorerResult 的聚合，计算最终 score/pass |
| `CountResult` | Counter 的统计汇总，写入 WorkflowContext |
| `ReportData` | Reporter 接收的上报数据（DataItem 列表 + CountResult） |

### 3.4 WorkflowContext 数据结构

上下文以 `Map<String, Object>` 存储，通过 `WorkflowContextOps` 进行类型安全的读写：

| Key | 类型 | 说明 |
|-----|------|------|
| `dataItems` | `List<DataItem>` | 所有评测数据项 |
| `scoreStrategy` | `ScoreStrategy` | 评分聚合策略（Begin 写入） |
| `evalReasonStrategy` | `EvalReasonStrategy` | 评测原因生成策略 |
| `threshold` | `Double` | 通过阈值 |
| `countResults` | `Map<String, String>` | 各 Counter 统计结果（JSON 序列化存储） |
| `extra` | `Map<String, Object>` | 自定义扩展数据 |

### 3.5 评分策略体系

**ScoreStrategy（多 Scorer 分数聚合策略）：**

| 策略 | 说明 |
|------|------|
| `SumScoreStrategy` | 求和（默认） |
| `AvgScoreStrategy` | 平均值 |
| `MinScoreStrategy` | 取最小值 |

**判断是否 pass 的两种方式：**

| 策略接口 | 比较对象 | 使用场景 |
|----------|---------|---------|
| `ScoreValueStrategy` | `score >= threshold`（绝对分值） | 分值有明确含义时 |
| `ScoreRateStrategy` | `scoreRate >= threshold`（得分率） | 需要跨维度可比性时 |

**EvalReasonStrategy（评测原因生成策略）：**

| 策略 | 说明 |
|------|------|
| `NormalEvalReasonStrategy` | 直接拼接各 Scorer reason（默认） |
| `JsonEvalReasonStrategy` | JSON 格式序列化 |
| `LLMSummaryEvalReasonStrategy` | 调用 LLM 生成综合摘要（需配置 LLMService） |

---

## 四、评测门面（Facade 层）

门面层对用户屏蔽了工作流的初始化细节，提供三种评测模式：

### 4.1 评测模式对比

| 模式 | 类 | 适用场景 | 断点续评 | 中间件 |
|------|----|---------|---------|--------|
| **直接 DAG 评测** | `WorkflowBuilder + Workflow` | 简单场景、快速验证 | ✗ | 无 |
| **全量评测** | `FullEvalFacade` | 中小数据集，一次性评测 | ✗ | 无 |
| **增量评测** | `DeltaEvalFacade` | 大数据集、需要断点续评 | ✓ | ActiveMQ + SQLite |
| **有序增量评测** | `OrderedDeltaEvalFacade` | 多轮对话评测（有状态） | ✓ | ActiveMQ + SQLite |

### 4.2 EvalFacade 生命周期

```
EvalFacade.run()
├── init()                     ← 初始化（环境/中间件准备）
├── beforeExecute()            ← 执行前钩子
├── execute()
│   ├── loadDataWrapper()
│   │   ├── beforeLoadData()
│   │   ├── loadData()         ← 加载评测数据
│   │   └── afterLoadData()
│   ├── evalWrapper()
│   │   ├── beforeEval()
│   │   ├── eval()             ← 执行评测工作流
│   │   └── afterEval()
│   └── reportWrapper()
│       ├── beforeReport()
│       ├── report()           ← 执行上报工作流
│       └── afterReport()
└── afterExecute()             ← 执行后钩子
```

### 4.3 增量评测（DeltaEvalFacade）核心机制

增量评测通过嵌入式中间件实现大规模数据的断点续评：

```
loadData()
  ↓ 分页加载 InputData，序列化为 JSON 消息
  ↓ 批量写入 ActiveMQ 队列
eval()                                        ← CompletableFuture 异步
  ↓ 多线程从 MQ 消费消息（事务性 batchReceiveInTx）
  ↓ 幂等检查（去重表 mqMessageProcessed）
  ↓ 克隆 evalWorkflow（每条 DataItem 独立工作流实例）
  ↓ 执行评测，结果写入 SQLite（dataItemMapper.insert）
report()
  ↓ ScheduledExecutorService 周期性上报（默认启用）
  ↓ doReport() 从 SQLite 读取所有已完成 DataItem，执行上报工作流
```

**断点续评原理：** 数据存储在本地文件（`eval_cache_data/<taskNameUuid>/`），重启后 MQ 和 SQLite 数据仍在，通过幂等 check 跳过已处理消息，从中断点继续消费。

---

## 五、节点扩展点详解

### 5.1 Scorer 扩展点

```java
public abstract class Scorer extends WorkflowNode {
    public abstract ScorerResult eval(DataItem dataItem);  // 必须实现

    // 可选覆盖的钩子
    public void beforeEval(DataItem dataItem) {}
    public ScorerResult afterEval(DataItem dataItem, ScorerResult result) {}
    public void orErrorEval(DataItem dataItem, Throwable ex) {}

    // 条件过滤：返回 false 则跳过该 DataItem
    protected boolean shouldEval(DataItem dataItem) {
        return config.getCondition() == null || config.getCondition().apply(dataItem);
    }
}
```

**ScorerConfig 关键配置：**

| 配置项 | 说明 |
|--------|------|
| `metricName` | 评测指标名称（必填） |
| `threshold` | 通过阈值（与 ScoreStrategy 配合） |
| `totalScore` | 满分值（用于计算 scoreRate） |
| `star` | 是否为关键指标（影响某些 Counter 汇总逻辑） |
| `threadNum` | 并发评测线程数 |
| `condition` | 条件函数，指定何时评估该 Scorer |

### 5.2 Checker 子系统

`checker` 是 Scorer 内部的规则检查模块，用于基于规则的细粒度判断：

```
Checker（接口）
└── AbstractChecker
    └── LLMBasedChecker    ← 基于 LLM 的规则 Checker

MultiCheckerBasedScorer    ← 将多个 Checker 结果聚合为一个 ScorerResult
```

Checker 内部打分也有聚合策略：

- `CheckItemScoreMergeStrategy`：单个 Checker 内多个检查项的分数合并
- `MergeCheckerScoreStrategy`：多个 Checker 分数间的合并（Sum/Avg/Min）

### 5.3 RubricBasedScorer（量规评估）扩展点

```java
public abstract class RubricBasedScorer extends Scorer {
    // 唯一必须实现的方法：提供待评估的用户内容
    public abstract String prepareUserPrompt(InputData inputData, ApiCompletionResult result);
}
```

量规评估支持丰富的维度配置（`RubricCriteria`）：

| 配置项 | 说明 |
|--------|------|
| `name` | 维度名称（唯一） |
| `definition` | 维度定义说明 |
| `scoreType` | 评分类型：`BINARY`（0/1）或 `STEPPED`（阶梯分） |
| `maxScore / minScore` | 分值范围 |
| `passScore` | 通过阈值 |
| `weight` | 聚合权重（用于加权平均） |
| `star` | 是否为关键维度（`STAR_GATE` 策略下失败则整体为 0） |
| `anchors` | Few-shot 分值锚点示例 |

**维度合并策略（RubricMergeStrategy）：**

| 策略 | 说明 |
|------|------|
| `WEIGHTED_AVERAGE` | 加权平均（默认） |
| `SIMPLE_AVERAGE` | 简单平均 |
| `LOGICAL_AND` | 任意维度未达标则取最差分 |
| `STAR_GATE` | 关键维度（star=true）未达标则整体为 0 |
| `COMPLETION_RATE` | 达标维度占比 |

### 5.4 DataLoader 扩展点

```java
public abstract class DataLoader extends WorkflowNode {
    // 必须实现：返回原始输入数据列表
    public abstract List<InputData> prepareDataList() throws Exception;

    // 可选覆盖的钩子
    protected void beforeLoad() {}
    protected List<InputData> afterLoad(List<InputData> inputDataList) {}
    protected void onLoadError(List<InputData> inputDataList, Throwable e) {}
}
```

**DataLoaderConfig 关键配置：**

| 配置项 | 说明 |
|--------|------|
| `offset / limit` | 数据分页（-1 表示不限制） |
| `shuffle` | 是否随机打乱顺序 |
| `filters` | 过滤器列表（`Predicate<InputData>`） |
| `openInjectData` | 是否开启数据注入（将历史 DataItem 数据注入到工作流） |

---

## 六、数据生成子系统

`data_generator` 包提供了独立于评测流程的测试数据生成能力：

```
DataGenerator           ← 抽象：生成单条 TestCase
MultiDataGenerator      ← 批量生成，支持并发
EvalCaseDataGenerator   ← 生成评测专用 Case（含 query + groundTruth）
KGBasedQueryGenerator   ← 基于知识图谱生成 Query
LoaderBasedDataGenerator ← 基于已有 DataLoader 扩展/改写数据
```

生成的数据可通过 `GenDataExporter` 导出：

- `ExcelGenDataExporter`：导出为 Excel
- `JsonFileGenDataExporter`：导出为 JSON 文件

---

## 七、Mock 数据引擎

`mock` 包提供了基于 SpEL 表达式的规则驱动 Mock 能力，用于 `DataLoaderWrapper` 中对评测数据进行模拟/补充：

```
MockRuleEngine（接口）
└── AbstractMockRuleEngine
    └── SpelMockRuleEngine    ← 基于 Spring SpEL 的规则引擎

Mocker（接口）
├── DateMocker               ← 模拟日期
├── NumberMocker             ← 模拟数字
├── ChinaPoiMocker           ← 模拟中国 POI 地点
├── ChinaAddressMocker       ← 模拟中国地址
├── ChinaHolidayMocker       ← 模拟中国节假日
└── ChinaFuzzyDateMocker     ← 模拟模糊日期表达
```

---

## 八、关键设计模式汇总

| 设计模式 | 应用位置 | 说明 |
|---------|---------|------|
| **模板方法** | `EvalFacade`、`Scorer`、`Counter`、`Reporter`、`DataLoader` | 定义算法骨架，子类实现关键步骤，钩子方法提供扩展点 |
| **策略模式** | `ScoreStrategy`、`EvalReasonStrategy`、`RubricMergeStrategy`、`LoadBalanceStrategy` | 算法族封装，运行时可替换 |
| **工厂模式** | `LLMServiceFactory` | 基于注册表的工厂，支持用户自定义注册 |
| **建造者模式** | `WorkflowBuilder`、`ScorerConfig.builder()`、`BeginConfig.builder()` | 流式构建复杂对象 |
| **装饰器模式** | `DataLoaderWrapper`、`ApiCompletionWrapper` | 在原有节点功能上透明添加处理逻辑 |
| **观察者/钩子** | 所有节点的 `beforeXxx / afterXxx / onError` | 生命周期回调，不破坏主流程 |
| **外观模式** | `EvalFacade` 及其子类 | 隐藏内部复杂性，提供简洁入口 |
| **享元/对象池** | `ThreadPoolManager` | 全局线程池复用，按名称隔离 |
| **原型模式** | `Workflow.clone()`、`DAG.clone()`、`WorkflowNode.clone()` | 增量评测中每条 DataItem 需要独立的工作流实例 |

---

## 九、扩展开发快速参考

### 自定义 Scorer（最常见的扩展）

```java
public class MyScorer extends Scorer {
    public MyScorer() {
        super(ScorerConfig.builder()
            .metricName("自定义指标")
            .threshold(0.6)
            .build());
    }

    @Override
    public ScorerResult eval(DataItem dataItem) {
        String response = dataItem.getApiCompletionResult().get("response");
        // 自定义评估逻辑
        double score = myEvalLogic(response);
        ScorerResult result = new ScorerResult();
        result.setMetric("自定义指标");
        result.setScore(score);
        result.setReason("评估原因...");
        return result;
    }
}
```

### 自定义 DataLoader

```java
public class MyDataLoader extends DataLoader {
    @Override
    public List<InputData> prepareDataList() {
        // 从任意数据源加载
        return myDataSource.stream()
            .map(item -> new InputData(MapUtils.of("query", item.getQuery())))
            .collect(Collectors.toList());
    }
}
```

### 自定义 LLM 服务

```java
public class MyLLMService implements LLMService {
    @Override
    public String chat(String prompt) {
        return myApiClient.call(prompt);
    }
    @Override
    public String getModel() { return "my-model-v1"; }
}

// 注册到工厂
LLMServiceFactory.registerLLMService("my-model", config -> new MyLLMService(config));
```

---

## 十、模块依赖与包引用总结

```
┌──────────────────────────────────────────────────────────┐
│                    evalkit-eval                           │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │   facade/   │  │    node/     │  │    model/      │  │
│  │ EvalFacade  │  │ Begin        │  │ DataItem       │  │
│  │ FullEval    │  │ DataLoader   │  │ InputData      │  │
│  │ DeltaEval   │  │ ApiCompletion│  │ EvalResult     │  │
│  │ OrderedDelta│  │ Scorer       │  │ ScorerResult   │  │
│  └─────────────┘  │ Counter      │  │ CountResult    │  │
│                   │ Reporter     │  │ ReportData     │  │
│                   │ End          │  └────────────────┘  │
│                   └──────────────┘                       │
├──────────────────────────────────────────────────────────┤
│                    evalkit-infra                          │
│   LLMService / LLMServiceFactory / ActiveMQ / SQLite      │
├──────────────────────────────────────────────────────────┤
│                   evalkit-workflow                        │
│   WorkflowNode / DAG / Workflow / WorkflowBuilder         │
├──────────────────────────────────────────────────────────┤
│                   evalkit-common                          │
│   BatchRunner / ThreadPoolManager / Utils / HttpClient    │
└──────────────────────────────────────────────────────────┘

