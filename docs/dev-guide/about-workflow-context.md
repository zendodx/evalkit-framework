---
layout: default
title: 工作流上下文实现原理
parent: 开发指南
nav_order: 4
has_toc: true
---

# 工作流上下文实现原理

> 本文介绍 EvalKit 框架中 `WorkflowContext` 的设计思路、传播机制与使用规范，是理解节点间数据流转的基础。

## 一、整体设计

框架的上下文本质是一个 **线程安全的 KV 容器**，承载一次工作流执行过程中的全部共享数据：

```
Workflow（持有唯一 WorkflowContext）
    │
    ├── TaskExecutor（按拓扑序将 context 注入每个节点）
    │       ├── Begin
    │       ├── DataLoader      ── 读 ctx / 写 ctx
    │       ├── ApiCompletion   ── 读 ctx / 写 ctx
    │       ├── Scorer          ── 读 ctx / 写 ctx
    │       ├── Counter         ── 读 ctx / 写 ctx
    │       └── Reporter        ── 读 ctx
    │
    └── 所有节点共享同一个 context 实例（引用传递，非拷贝）
```

核心类分布在两个模块：

| 类                       | 模块               | 职责               |
|-------------------------|------------------|------------------|
| `WorkflowContext`       | evalkit-workflow | 上下文本体，KV 存储      |
| `WorkflowContextKey`    | evalkit-eval     | 上下文 Key 常量定义     |
| `WorkflowContextOps`    | evalkit-eval     | 类型安全的读写门面        |
| `WorkflowContextHolder` | evalkit-workflow | ThreadLocal 兜底容器 |
| `CountResult`           | evalkit-eval     | 统计结果的写回契约        |

---

## 二、WorkflowContext 本体实现

位置：`evalkit-workflow/src/main/java/com/evalkit/framework/workflow/model/WorkflowContext.java`

```java
public class WorkflowContext implements Cloneable {
    private Map<String, Object> store;

    public WorkflowContext() {
        this.store = new ConcurrentHashMap<>();
    }

    public void put(String key, Object value) {
        if (store == null) store = new ConcurrentHashMap<>();
        // ConcurrentHashMap 不允许 null value；value 为 null 等同于移除该 key
        if (value == null) {
            store.remove(key);
        } else {
            store.put(key, value);
        }
    }

    public <T> T get(String key, Class<T> clazz) {
        if (store == null) return null;
        Object obj = store.getOrDefault(key, null);
        if (obj == null) return null;
        return clazz.cast(obj);
    }
}
```

### 设计要点

**1. 线程安全：基于 ConcurrentHashMap**

工作流节点是并发执行的（DAG 中无依赖关系的节点并行运行），所有节点共享同一个 context 实例，因此底层直接使用
`ConcurrentHashMap`，无需额外加锁。

**2. null 值语义：put(null) 等价于 remove**

`ConcurrentHashMap` 不允许 null value，框架将其转译为「删除该 key」的语义：

```java
ctx.put("key",null);  // 实际效果：移除 "key"
```

**3. 取值：类型强转**

`get(key, clazz)` 内部通过 `clazz.cast(obj)` 完成 类型校验 + 转换。存取双方必须使用相同的类型，否则运行时抛出
`ClassCastException`。

**4. 克隆：JSON 序列化深拷贝**

```java

@Override
public WorkflowContext clone() {
    WorkflowContext clone = (WorkflowContext) super.clone();
    String storeJson = JsonUtils.toJson(this.store);
    clone.store = new ConcurrentHashMap<>(JsonUtils.fromJson(storeJson, ...));
    return clone;
}
```

通过「序列化 → 反序列化」实现深拷贝，克隆后的 context 与原对象完全隔离。

> ⚠️ **克隆的坑**：JSON 深拷贝会丢失部分类型信息——复杂对象反序列化后可能退化为 `Map`/`List`，`Function`、`ThreadLocal`
> 等不可序列化对象会直接丢失。克隆 context 时只适合存放「纯数据」。

---

## 三、Key 管理：WorkflowContextKey + WorkflowContextOps

### WorkflowContextKey：集中定义 Key

位置：`evalkit-eval/.../context/WorkflowContextKey.java`

```java
public class WorkflowContextKey {
    protected static final String TASK_NAME = "task_name";
    public static final String SCORE_STRATEGY = "score_strategy";
    public static final String EVAL_REASON_STRATEGY = "eval_reason_strategy";
    public static final String THRESHOLD = "threshold";
    public static final String DATA_ITEM_LIST = "data_item_list";
    public static final String COUNT_RESULT_MAP = "count_result_map";
    public static final String EXTRA = "extra";
}
```

所有内置 Key 集中定义，避免裸字符串散落在各节点中造成拼写冲突。

### WorkflowContextOps：类型安全读写门面

位置：`evalkit-eval/.../context/WorkflowContextOps.java`

每个 Key 对应一对 `setXxx / getXxx` 方法，把「Key 字符串 + 类型转换」封装在一起：

```java
public static void setDataItems(WorkflowContext ctx, List<DataItem> dataItems) {
    ctx.put(WorkflowContextKey.DATA_ITEM_LIST, dataItems);
}

public static List<DataItem> getDataItems(WorkflowContext ctx) {
    return ctx.get(WorkflowContextKey.DATA_ITEM_LIST, List.class);
}
```

同时提供便捷查询方法，供节点/报告层按条件检索数据：

```java
// 按 dataIndex 查找单条数据
WorkflowContextOps.getDataItemByIndex(ctx, 1L);

// 按输入字段精确匹配
WorkflowContextOps.

getDataItemByField(ctx, "sessionId","session-001");
```

> **开发规范**：节点代码中禁止直接 `ctx.put("裸字符串", ...)`，应新增 Key 常量并在 Ops 中封装读写方法。

---

## 四、上下文的传播机制

### 4.1 创建与注入

```java
// Workflow 构造时创建唯一 context
public Workflow(DAG dag) {
    this.dag = dag;
    this.taskExecutor = new TaskExecutor(...);
    this.workflowContext = new WorkflowContext();
}

// TaskExecutor 按拓扑序将同一引用注入每个节点
public void executeTasks(DAG dag, WorkflowContext workflowContext) {
    List<String> sortedTasks = GraphUtils.topologicalSort(dag);
    for (String taskId : sortedTasks) {
        WorkflowNode workflowNode = dag.getTask(taskId);
        workflowNode.setWorkflowContext(workflowContext);  // ← 同一引用
        // 等待依赖节点完成后再提交执行
        ...
    }
}
```

**关键结论**：所有节点拿到的是**同一个 context 对象**，上游节点 `put` 的数据，下游节点立即可见。这正是
`DataLoader → Scorer → Counter → Reporter` 能逐级传递数据的机制。

### 4.2 节点内读写标准姿势

以 `Scorer.doExecute()` 为例：

```java

@Override
public void doExecute() {
    WorkflowContext ctx = getWorkflowContext();          // 1. 取出注入的 context
    List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);  // 2. 读上游数据
    ScoreStrategy strategy = WorkflowContextOps.getScorerStrategy(ctx);
    // ... 执行评估，结果写回 DataItem（对象引用共享，无需重新 put）
}
```

以 `Counter.doExecute()` 为例（写回统计结果）：

```java

@Override
protected void doExecute() {
    WorkflowContext ctx = getWorkflowContext();
    List<DataItem> dataItems = WorkflowContextOps.getDataItems(ctx);
    CountResult countResult = countWrapper(dataItems);
    if (countResult != null) {
        countResult.writeToCtx(ctx);   // 3. 结果写回 context，供下游 Reporter 读取
    }
}
```

`CountResult.writeToCtx` 的默认实现即调用 `WorkflowContextOps.setCountResult(ctx, this)`，将统计 JSON 挂到
`count_result_map` 下，HtmlReporter 等上报节点据此渲染报告。

### 4.3 ThreadLocal 兜底：WorkflowContextHolder

```java
public class WorkflowContextHolder {
    private static final ThreadLocal<WorkflowContext> TL = new ThreadLocal<>();

    public static void set(WorkflowContext ctx) {
        TL.set(ctx);
    }

    public static WorkflowContext get() {
        return TL.get();
    }

    public static void clear() {
        TL.remove();
    }
}
```

用于跨层级传递 context 的补充手段（例如节点内部深层的工具类不方便层层传参时）。`Workflow.execute()` 结束后在 finally 中调用
`WorkflowContextHolder.clear()`，防止线程池复用导致的上下文泄漏。

---

## 五、克隆语义与隔离

工作流支持三层链式克隆，context 随之深拷贝：

```
Workflow.clone()
    ├── dag.clone()            → 每个节点 WorkflowNode.clone()
    │                             └── clone.workflowContext = this.workflowContext.clone()
    └── workflowContext.clone() → Workflow 顶层的 context 深拷贝
```

**用途**：

- 多次执行互不污染（增量评测的断点恢复场景）；
- 并发隔离：克隆出的子工作流拥有独立 context。

**注意**：由于 context 克隆走 JSON 序列化，克隆时机应保证 context 中只存放可序列化的纯数据对象。`DataItem`、`ScorerResult`
等模型均可安全克隆；但 lambda（如 `RubricCriteria.condition`）等不可序列化字段会在克隆后丢失。

---

## 六、常见问题与使用规范

### Q1：为什么我在测试里直接调节点方法会 NPE？

`WorkflowContextOps.getScorerStrategy(ctx)` 等方法内部直接访问 `ctx`。正常执行时 context 由 `TaskExecutor` 注入；但单元测试中直接调用
`scorer.evalWrapper(item)` 绕过了执行链路，`workflowContext` 为 null，导致 NPE。

**正确做法**：测试中手动构造并注入：

```java
WorkflowContext ctx = new WorkflowContext();
WorkflowContextOps.

setScorerStrategy(ctx, new MaxScoreRateStrategy());
        WorkflowContextOps.

setThreshold(ctx, 0.6);
scorer.

setWorkflowContext(ctx);
```

### Q2：往 context 里存大对象可行吗？

技术上可行（`data_item_list` 本身就是全量数据），但要意识到：

- context 是整个工作流生命周期存活的，大对象会一直占据内存；
- 克隆工作流时会对整个 store 做 JSON 序列化，过大的数据会显著拖慢克隆。

原则：**context 存共享的"骨架"数据（dataItems、策略、统计结果），节点私有的中间状态放在节点实例或 DataItem.extra 中**。

### Q3：自定义节点如何接入上下文？

1. 需要读数据：在 `doExecute()` 中 `getWorkflowContext()` + `WorkflowContextOps.getXxx(ctx)`；
2. 需要写数据：优先写回 `DataItem`（引用共享）；确需全局可见的新数据时，在 `WorkflowContextKey` 新增常量，并在
   `WorkflowContextOps` 封装读写对。

---

## 七、小结

| 问题     | 答案                                                  |
|--------|-----------------------------------------------------|
| 底层结构   | `ConcurrentHashMap<String, Object>`                 |
| 线程安全   | CHM 保证并发读写安全                                        |
| 传播方式   | `TaskExecutor` 将**同一引用**注入所有节点                      |
| 数据可见性  | 上游 put → 下游立即可见（执行顺序由 DAG 拓扑保证）                     |
| null 值 | `put(key, null)` 等价于 `remove(key)`                  |
| 克隆     | JSON 深拷贝，随 `WorkflowNode.clone()` 链式发生              |
| Key 规范 | 常量集中在 `WorkflowContextKey`，读写经 `WorkflowContextOps` |
| 兜底通道   | `WorkflowContextHolder`（ThreadLocal），用完必须 clear     |

