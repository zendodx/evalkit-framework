---
layout: default
title: 工作流节点关系详解
parent: 开发指南
nav_order: 2
has_toc: true
---

# 工作流节点关系详解

本文档从节点协作的角度，详细描述 EvalKit 各工作流节点之间的**数据流动关系**、**上下文依赖关系**、**拓扑组合规则**与**特殊协作模式**，帮助开发者理解节点如何"配合"工作，以及在什么场景下应该选择哪种节点组合。

---

## 一、节点全景与默认执行顺序

一次标准评测工作流由以下节点按顺序组成：

```
Begin
  └─► DataLoader / DataGenerator
            └─► [DataLoaderWrapper]  (可选)
                      └─► ApiCompletion
                                └─► [ApiCompletionWrapper]  (可选)
                                          └─► Scorer × N  (并行)
                                                    └─► Counter × N  (并行)
                                                              └─► Reporter × N  (并行)
                                                                        └─► End
```

**连线规则概要：**

| 连接方式 | 语义 | 示例 |
|---------|------|------|
| 单→单 | 顺序执行 | `Begin → DataLoader` |
| 单→多（广播） | A 执行完后，B/C/D 并行 | `ApiCompletion → [Scorer1, Scorer2, Scorer3]` |
| 多→单（汇聚） | 所有前驱完成后才启动后继 | `[Scorer1, Scorer2] → Counter` |
| 多→多 | 集合广播到集合 | `[Scorer] → [Counter]` |

---

## 二、每个节点的输入与输出

各节点通过共享的 `WorkflowContext` 传递数据，核心载体是 `List<DataItem>`。

### 2.1 Begin

| 项目 | 说明 |
|------|------|
| **前驱** | 无（入口节点） |
| **后继** | DataLoader 或 DataGenerator |
| **读取 Context** | 无 |
| **写入 Context** | `dataItems`（初始化为空列表）、`scoreStrategy`、`evalReasonStrategy`、`threshold`、`countResults`（空 Map）、`extra`（空 Map） |
| **职责** | 初始化整个工作流上下文，将 `BeginConfig` 中的全局策略写入 Context，供后续所有节点使用 |

> **注意：** Begin 必须是 DAG 的第一个节点。它写入的 `scoreStrategy` 和 `threshold` 会被 DataLoader 在构建 `DataItem.evalResult` 时用到，也会被每个 Scorer 在计算 `pass` 时读取。

### 2.2 DataLoader / DataGenerator

| 项目 | 说明 |
|------|------|
| **前驱** | Begin |
| **后继** | DataLoaderWrapper（可选）或 ApiCompletion |
| **读取 Context** | `scoreStrategy`、`evalReasonStrategy`、`threshold`（用于初始化每个 DataItem 的 EvalResult） |
| **写入 Context** | 向 `dataItems` 中追加新构建的 `DataItem` 列表 |
| **职责** | 调用 `prepareDataList()` 获取原始 `InputData`，完成分页/过滤/打乱，构建带索引的 `DataItem`，初始化 `EvalResult` 占位对象 |

**DataGenerator 与 DataLoader 的关系：**

`DataGenerator` 继承自 `DataLoader`，重写了 `doExecute()` 与 `prepareDataList()` 的调用链，在加载完成后额外支持将数据导出到文件（Excel/JSON）。在工作流中，两者可互换位置——Begin 之后可以接 DataGenerator 替代 DataLoader，生成的测试数据直接进入评测流程。

```
Begin
  └─► DataGenerator    ← 等价于 DataLoader，额外具备数据生成与导出能力
            └─► ApiCompletion
```

### 2.3 DataLoaderWrapper（可选）

| 项目 | 说明 |
|------|------|
| **前驱** | DataLoader / DataGenerator |
| **后继** | ApiCompletion |
| **读取 Context** | `dataItems` |
| **写入 Context** | 直接修改 `dataItems` 中各 `DataItem.inputData` 的字段值（原地修改，不替换列表引用） |
| **职责** | 对已加载的输入数据进行增强：Mock 字段、改写、裂变等 |

**内置实现说明：**

| 实现类 | 说明 |
|--------|------|
| `MockDataLoaderWrapper` | 基于 SpEL 规则引擎（`SpelMockRuleEngine`）对指定字段进行 Mock 替换，例如将 `{{holiday}}` 替换为随机节假日 |
| `PolishDataLoaderWrapper` | 调用 LLM 对 InputData 中的文本字段进行润色改写 |
| `PromptDataLoaderWrapper` | 根据 Prompt 模板生成新的字段值，注入到 InputData |

**可串联多个 DataLoaderWrapper：**

```java
// 先 Mock，再润色
builder.link(dataLoader, mockWrapper, polishWrapper, apiCompletion);
```

### 2.4 ApiCompletion

| 项目 | 说明 |
|------|------|
| **前驱** | DataLoader / DataLoaderWrapper |
| **后继** | ApiCompletionWrapper（可选）或 Scorer |
| **读取 Context** | `dataItems`（读取每个 `DataItem.inputData`） |
| **写入 Context** | 为每个 `DataItem` 设置 `apiCompletionResult` |
| **职责** | 并发调用被测接口（`BatchRunner` + `API_COMPLETION` 线程池），将调用结果（含时延信息）写回对应 DataItem |

**OrderedApiCompletion 特殊关系：**

`OrderedApiCompletion` 适用于**多轮对话**场景，同一 `orderKey`（例如同一个 `sessionId`）的数据会被保证在**同一线程串行执行**。在 `invoke()` 内部，可通过以下方法访问同组历史轮次：

```java
DataItem prev = getPrevDataItem(current);           // 上一轮
List<DataItem> history = getPrevDataItems(current); // 所有历史轮
DataItem first = getGroupDataItemAt(current, 1);    // 第 1 轮
```

这些方法依赖**预建的分组索引**（`groupIndexCache`），在 `batchInvoke()` 开始时一次性构建，后续查询为 O(1)。

### 2.5 ApiCompletionWrapper（可选）

| 项目 | 说明 |
|------|------|
| **前驱** | ApiCompletion |
| **后继** | Scorer |
| **读取 Context** | `dataItems` |
| **写入 Context** | 直接修改各 `DataItem.apiCompletionResult` 的内容（原地修改） |
| **职责** | 对接口返回结果进行清洗/转化，例如提取嵌套字段、格式化文本、删除冗余信息等 |

**与 DataLoaderWrapper 的对称性：**

```
DataLoader ──► [DataLoaderWrapper] ──► ApiCompletion ──► [ApiCompletionWrapper] ──► Scorer
  （加载原始数据）  （加工输入数据）      （调用接口）         （加工输出数据）             （评估）
```

两者都是"装饰器"节点，前者处理输入侧，后者处理输出侧，互相镜像。

### 2.6 Scorer（可多个并行）

| 项目 | 说明 |
|------|------|
| **前驱** | ApiCompletion / ApiCompletionWrapper |
| **后继** | Counter |
| **读取 Context** | `dataItems`（读取 `inputData` 和 `apiCompletionResult`）、`scoreStrategy`、`evalReasonStrategy`、`threshold` |
| **写入 Context** | 为每个 `DataItem.evalResult` 追加自己的 `ScorerResult`；`EvalResult` 会同步更新 `score`、`pass`、`reason` |
| **职责** | 按指标对每条 DataItem 打分，每个 Scorer 产出一个 `ScorerResult` |

**多 Scorer 并行关系：**

```
ApiCompletion
    ├─► Scorer1（回复长度）    ← 并行执行，彼此独立
    ├─► Scorer2（语义相似度）
    └─► Scorer3（安全性检测）
              ↓（全部完成后）
           Counter
```

多个 Scorer 并发写同一个 `DataItem.evalResult`，`EvalResult.addScorerResult()` 使用**双重检查锁**保证线程安全。每次添加后立即重新计算 `score`/`pass`/`reason`。

**EvalResult.pass 的判定逻辑：**

```
1. 检查所有 star=true 的 ScorerResult：
   - 若任一 star=true 且 pass=false → EvalResult.pass = false（直接返回）
2. 若所有 star 指标均通过：
   - EvalResult.pass = (最终聚合 score >= threshold)
```

### 2.7 Counter（可多个并行）

| 项目 | 说明 |
|------|------|
| **前驱** | Scorer（所有 Scorer 完成后才触发） |
| **后继** | Reporter |
| **读取 Context** | `dataItems`（读取所有已完成的 `EvalResult`） |
| **写入 Context** | 将自己的统计结果以 `{counterName → JSON}` 形式写入 `countResults` Map |
| **职责** | 对全量 DataItem 的评测结果进行聚合统计 |

**各 Counter 与其依赖的 Scorer 类型：**

| Counter | 依赖的前置条件 | 说明 |
|---------|-------------|------|
| `BasicCounter` | 任意 Scorer 的 `EvalResult` | 统计通过率、平均分、接口耗时分布（TP50~TP99）、评测耗时、LLM Token 消耗 |
| `MetricCounter` | 需用户实现 `buildMetricItems()` 提取指标 | 按指标名称分组，统计每个指标的均值/最值/通过率 |
| `RubricCounter` | 需与 `RubricBasedScorer` 配套使用 | 按评估器 + 维度两级聚合，计算每个 criteria 的通过率和均分 |
| `AttributeCounter` / `AttributeCounterV2` | `EvalResult.reason` 不为空 | 调用 LLM 对失败原因进行归因分类，输出问题类型分布 |

**多 Counter 并行关系：**

多个 Counter 共享同一个 `countResults` Map，每个 Counter 以自己的 `counterType` 为 key 写入，不会互相覆盖。

```java
// CountResult.writeToCtx() 的行为
map.put(result.counterName(), JsonUtils.toJson(result));
```

### 2.8 Reporter（可多个并行）

| 项目 | 说明 |
|------|------|
| **前驱** | Counter（所有 Counter 完成后才触发） |
| **后继** | End |
| **读取 Context** | `dataItems`、`countResults` |
| **写入 Context** | 无（只输出，不修改 Context） |
| **职责** | 将评测结果输出到各种目标（控制台/文件/数据库/API） |

**Reporter 接收的数据结构：**

```java
ReportData
  ├── dataItems       ← 所有 DataItem（含 inputData、apiCompletionResult、evalResult）
  └── countResultMap  ← 所有 Counter 的统计结果（key = counterType, value = JSON 字符串）
```

**多 Reporter 并行：** 同一次评测可以同时输出到多种目标，例如：

```java
List<Reporter> reporters = ListUtils.of(
    new StdReporter(),           // 控制台打印
    new HtmlReporter(fileName),  // HTML 报告文件
    new ExcelReporter(fileName), // Excel 报告文件
    new CsvReporter(fileName),   // CSV 格式
    new ApiReporter(endpoint)    // 调用远端 API 上报
);
builder.link(counters, reporters);
```

### 2.9 End

| 项目 | 说明 |
|------|------|
| **前驱** | Reporter（所有 Reporter 完成后触发） |
| **后继** | 无（出口节点） |
| **读取 Context** | 全量 Context（用户可自由读取任何数据） |
| **写入 Context** | 由用户决定 |
| **职责** | 评测收尾操作，例如：发送通知、上传附件、清理临时文件、打印资源消耗摘要等 |

---

## 三、节点间的数据流时序图

```
                          WorkflowContext（共享状态）
                         ┌─────────────────────────┐
Begin                    │ dataItems = []           │
  │ write                │ scoreStrategy            │
  ▼                      │ evalReasonStrategy       │
DataLoader               │ threshold                │
  │ append dataItems     │ countResults = {}        │
  ▼                      │ extra = {}               │
DataLoaderWrapper?       │                         │
  │ modify inputData     │  DataItem[0]             │
  ▼                      │  ├── inputData ──────────┼── Begin 写策略 → 初始化 EvalResult
ApiCompletion            │  ├── apiCompletionResult │
  │ set apiResult        │  └── evalResult          │
  ▼                      │      ├── scorerResults[] │
ApiCompletionWrapper?    │      ├── score           │
  │ modify apiResult     │      └── pass            │
  ▼                      └─────────────────────────┘
Scorer[0] ──── Scorer[1] ──── Scorer[2]   ← 并行
  │ addScorerResult       每个 Scorer 写完后 EvalResult 实时更新
  └──────────────────────┬──────────────────────────
                         ▼
Counter[0] ── Counter[1] ← 并行（读全量 dataItems）
  │ write countResults
  └──────────────────────┬──────────────────────────
                         ▼
Reporter[0] ─ Reporter[1] ─ Reporter[2] ← 并行
  │ read dataItems + countResults（只读，不修改）
  └──────────────────────┬──────────────────────────
                         ▼
                        End
                  │ read full Context
```

---

## 四、可选节点的插拔规则

以下节点是**可选的**，可根据需要灵活插入或省略：

| 可选节点 | 插入位置 | 省略时的行为 |
|---------|---------|------------|
| `DataLoaderWrapper` | DataLoader 之后，ApiCompletion 之前 | 省略：`inputData` 直接原样传入 ApiCompletion |
| `ApiCompletionWrapper` | ApiCompletion 之后，Scorer 之前 | 省略：接口返回结果直接原样传入 Scorer |
| `Counter` | Scorer 之后，Reporter 之前 | 省略：`countResultMap` 为空，Reporter 的 `ReportData` 中无统计数据 |
| `End` | Reporter 之后 | 省略：工作流正常结束，但无收尾操作 |

**最简工作流（无 Counter、无 End）：**

```java
new WorkflowBuilder()
    .link(begin, dataLoader, apiCompletion, scorer, reporter)
    .build()
    .execute();
```

---

## 五、Scorer 内部节点关系

Scorer 不是原子的，其内部也有多种**子节点协作模式**：

### 5.1 MultiCheckerBasedScorer：Scorer + Checker 组合

```
MultiCheckerBasedScorer.eval(dataItem)
    ├─ Checker1.checkWrapper(dataItem)   ← 顺序执行（非并发）
    ├─ Checker2.checkWrapper(dataItem)
    └─ Checker3.checkWrapper(dataItem)
         ↓
  MergeCheckerScoreStrategy.mergeScore(checkers)
         ↓
  ScorerResult（score = 合并后分数，extra 包含每个 Checker 的明细）
```

Checker 与 Scorer 的关系：

- **Checker 不是 WorkflowNode**，不接入 DAG，不共享 WorkflowContext
- Checker 只在 `MultiCheckerBasedScorer.eval()` 内部被**顺序调用**
- Checker 的结果通过 `Scorer` 聚合后写入 `DataItem.evalResult`

**Checker 聚合策略（MergeCheckerScoreStrategy）：**

| 策略 | 说明 |
|------|------|
| `SumMergeCheckerScoreStrategy` | 各 Checker 分数求和 |
| `AvgMergeCheckerScoreStrategy` | 各 Checker 分数取平均 |
| `MinMergeCheckerScoreStrategy` | 取最低 Checker 分数 |

**star=true 的 Checker：** 若某 Checker 标记为 `star=true`，该 Checker 不通过时整体 Scorer 结果为 0 分。

### 5.2 RubricBasedScorer：Scorer + 多维度 LLM 调用

```
RubricBasedScorer.eval(dataItem)
    │
    ├─ 条件过滤：将 criteria 分为"需要执行"和"跳过"两组
    │
    ├─ 按 criteriaBatchSize 分组（每组作为一次 LLM 调用任务）
    │
    └─ BatchRunner（SCORER_CRITERIA 线程池）并发调用各批次
            ├─ 批次1: [criteria1] → LLM call → 解析 → CriteriaEvalResult
            ├─ 批次2: [criteria2, criteria3] → 合并 LLM call（批量模式）
            └─ 批次N: ...
                 ↓
        mergeScores() 按 RubricMergeStrategy 合并
                 ↓
        ScorerResult（score + 各维度明细存入 extra）
```

**为何使用独立的 SCORER_CRITERIA 线程池：**

外层 DAG 的 `Scorer` 节点在 `SCORER` 线程池中并发运行，若内部的维度 LLM 调用也使用 `SCORER` 池，会导致嵌套任务占满线程池后出现**死锁**。`SCORER_CRITERIA` 是独立的线程池，专门用于 Rubric 内部维度并发，彻底规避此问题。

### 5.3 RouterScorer：Scorer + 条件路由

```
RouterScorer.eval(dataItem)
    │
    ├─ [first-match 模式]
    │   └─ 按顺序检查每条 ScorerRoute.matches(dataItem)
    │       └─ 第一个命中 → 委托到 route.scorer.eval(dataItem) → 返回
    │
    ├─ [match-all 模式]
    │   └─ 收集所有命中路由 → 并行委托各 scorer → 取平均分合并
    │
    └─ [无命中]
        └─ defaultScorer != null → 委托 defaultScorer → 返回
        └─ defaultScorer == null → 返回跳过结果
```

**路由机制与其他 Scorer 的关系：** `RouterScorer` 充当其他 Scorer 的"容器"，被路由到的子 Scorer **不加入 DAG**，仅在 `RouterScorer` 内部被调用，WorkflowContext 通过 `setWorkflowContext()` 注入。

---

## 六、典型拓扑模式

### 模式 A：标准线性链（最常见）

```
Begin → DataLoader → ApiCompletion → Scorer → Counter → Reporter → End
```

适用：单一评估维度，数据量适中。

### 模式 B：多评估器并行（最推荐）

```
Begin → DataLoader → ApiCompletion
                          ├─► Scorer1（维度A）
                          ├─► Scorer2（维度B）  ← 并行
                          └─► Scorer3（维度C）
                                    │（汇聚）
                                  Counter
                                    └─► Reporter → End
```

适用：多个评估维度相互独立，可以并发打分。

### 模式 C：多 Counter + 多 Reporter

```
Scorer（汇聚）
    └─►
        ├─► BasicCounter
        ├─► MetricCounter    ← 并行统计
        └─► RubricCounter
                │（汇聚）
                ├─► StdReporter
                ├─► HtmlReporter    ← 并行输出
                └─► ExcelReporter
                          └─► End
```

适用：需要多维度统计且需要输出到多种格式。

### 模式 D：带数据增强的链

```
Begin → DataLoader → MockDataLoaderWrapper → ApiCompletion → ApiCompletionWrapper → Scorer → Counter → Reporter → End
```

适用：需要 Mock 输入字段，且接口返回结果需要清洗后才能评估。

### 模式 E：DataGenerator 替代 DataLoader

```
Begin → KGBasedQueryGenerator → ApiCompletion → Scorer → Counter → Reporter → End
```

适用：没有现成测试集，通过知识图谱或 LLM 动态生成评测问题。

### 模式 F：有序多轮对话（OrderedDeltaEvalFacade）

```
Begin → DataLoader → OrderedApiCompletion → Scorer → Counter → Reporter → End
```

说明：`OrderedApiCompletion` 按 `orderKey` 将同一会话的多轮数据路由到同一线程串行执行，`invoke()` 内部可访问前序轮次的 `apiCompletionResult`，实现上下文连贯的多轮对话评测。

---

## 七、节点间约束与常见错误

### 7.1 必须遵守的前后继约束

| 规则 | 说明 |
|------|------|
| Begin 必须是第一个节点 | Begin 负责初始化 Context，后续节点依赖它写入的策略对象，若缺失会 NPE |
| DataLoader/DataGenerator 必须在 ApiCompletion 之前 | ApiCompletion 需要 `dataItems` 不为空，否则抛 `EvalException` |
| Scorer 必须在 ApiCompletion 之后 | Scorer 读取 `apiCompletionResult`，若为 null 则无法评估 |
| Counter 必须在所有 Scorer 之后 | Counter 需要读取每个 DataItem 的完整 `evalResult`（含所有 Scorer 的结果） |
| Reporter 必须在 Counter 之后（若用到统计数据） | Reporter 构建 `ReportData` 时会读取 `countResults`，若 Counter 未完成则为空 Map |
| `RubricCounter` 必须与 `RubricBasedScorer` 配套 | RubricCounter 识别 `scorerType == "rubricBasedScorer"`，若没有对应 Scorer 则统计结果为空 |

### 7.2 多 Scorer 并发写 DataItem 的线程安全

多个 Scorer 同时调用 `dataItem.addScorerResult()`，`EvalResult` 内部通过**双重检查锁**保证 `scorerResults` 列表初始化安全，`addScorerResult()` 方法本身是 `synchronized`，保证写入不冲突：

```java
// DataItem.addScorerResult()：双重检查锁初始化
if (evalResult == null) {
    synchronized (this) {
        if (evalResult == null) { evalResult = new EvalResult(); }
    }
}
// EvalResult.addScorerResult()：同步方法
public synchronized void addScorerResult(ScorerResult scorerResult) {
    this.scorerResults.add(scorerResult);
    this.updateEvalResult();  // 每次添加后立即重算 score/pass/reason
}
```

### 7.3 Counter 并发写 countResults 的线程安全

多个 Counter 并发运行时，都调用 `WorkflowContextOps.setCountResult(ctx, result)`。由于使用的是 `ConcurrentHashMap`，且各 Counter 以自身的 `counterType` 为 key，**不存在 key 冲突**，是线程安全的。

### 7.4 ApiCompletionWrapper 中修改 apiCompletionResult 的注意事项

`ApiCompletionWrapper.wrapper(dataItem)` 对 `apiCompletionResult` 的修改是**原地修改**（通过 `result.set(key, value)`），修改会直接反映到后续所有 Scorer 中。若多个 Wrapper 串联，后一个 Wrapper 看到的是前一个 Wrapper 修改后的结果。

---

## 八、Context 在节点间的传播路径

下图展示了 `WorkflowContext` 各字段的**写入节点**和**读取节点**，完整呈现了节点间的信息依赖：

```
字段                写入节点                         读取节点
─────────────────────────────────────────────────────────────────
dataItems           Begin（初始化为空列表）            所有后续节点
                    DataLoader（追加 DataItem）       ApiCompletion / Scorer / Counter / Reporter
                    DataLoaderWrapper（修改内容）

scoreStrategy       Begin                            DataLoader（初始化 EvalResult）
                                                     Scorer（decidePass 时通过 ctx 读取）

evalReasonStrategy  Begin                            DataLoader（初始化 EvalResult）
                                                     EvalResult.updateReason()（间接）

threshold           Begin                            DataLoader（初始化 EvalResult）
                                                     Scorer（decidePass 时读取）
                                                     EvalResult.updatePass()（间接）

countResults        Begin（初始化为空 Map）           Reporter（读取全量统计结果）
                    Counter（写入自己的统计）

extra               Begin（初始化为空 Map）           用户可在 End 或自定义节点中读写
                    任意节点可写
```

---

## 九、增量评测模式下的节点关系变化

使用 `DeltaEvalFacade` 时，节点关系与直接 DAG 模式有以下差异：

| 方面 | 直接 DAG 模式 | DeltaEvalFacade 模式 |
|------|-------------|---------------------|
| DataLoader 角色 | 节点加入 DAG，读取 Context | 在 `loadData()` 方法中独立调用，不进入 evalWorkflow |
| 每条数据的工作流 | 所有数据共享同一工作流实例 | 每条 DataItem 对应一个**克隆的独立工作流实例**（`evalWorkflow.clone()`） |
| 并发方式 | DAG 内并行 Scorer | MQ 多线程消费，每条 DataItem 独立运行完整评测子工作流 |
| Counter/Reporter 角色 | 在 evalWorkflow 中 | 在独立的 **reportWorkflow** 中，从 SQLite 读取已完成的全量 DataItem 后运行 |
| 数据持久化 | 无，内存中 | 每条 DataItem 评测完成后写入 SQLite，支持断点续评 |

**增量模式下的工作流拓扑（评测部分）：**

```
evalWorkflow（每条 DataItem 独立克隆）：
  Begin → ApiCompletion → Scorer1 → Scorer2 → ...
  （不含 DataLoader、Counter、Reporter）

reportWorkflow（周期性或最终上报）：
  Begin → Counter → Reporter → End
  （从 SQLite 读取 dataItems，不含 DataLoader、ApiCompletion、Scorer）

