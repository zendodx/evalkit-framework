# 增量评测技术说明

> 本文档面向框架开发者，描述增量评测（`DeltaEvalFacade` / `OrderedDeltaEvalFacade`）的内部设计、数据流、并发模型及关键实现细节。

---

## 一、整体架构

增量评测在全量评测（`FullEvalFacade`）的基础上引入了两个嵌入式中间件：

| 中间件 | 类 | 作用 |
|---|---|---|
| 嵌入式消息队列 | `ActiveMQEmbeddedServer` | 持久化存储待评测数据，保证断点续评 |
| 嵌入式关系数据库 | `SQLiteEmbeddedServer` | 持久化存储评测结果、幂等去重表、任务状态 |

两者的数据文件均存储在 `eval_cache_data/{taskNameUuid}/` 目录下，以 `taskName` 的 UUID 作为隔离 key，支持同机多任务并行运行。

```
                 ┌───────────────────────────────────────────┐
                 │             DeltaEvalFacade                │
                 │                                           │
  DataLoader ──► │  loadData()  ──► ActiveMQ Queue          │
                 │                        │                  │
                 │              eval() ◄──┘  (N threads)     │
                 │                │                          │
                 │          evalAndInsert()                  │
                 │                │                          │
                 │          SQLite (data_item)               │
                 │                │                          │
                 │    doReport() ◄┘  (scheduled)             │
                 └───────────────────────────────────────────┘
```

---

## 二、生命周期

```
run()
 ├── init()                   // 启动 MQ、DB；enableResume=false 时清空缓存
 └── execute()
      ├── initEvalTask()      // 初始化任务状态记录（幂等，已存在则跳过）
      ├── loadDataWrapper()   // 分批加载数据并写入 MQ Queue（幂等，已加载则跳过）
      ├── eval()              // 异步消费 MQ 并评测，返回 CompletableFuture
      ├── report()            // 启动周期性上报调度器
      ├── consumeFuture.get() // 主线程等待所有消费完成
      └── [finally]
           ├── stopReporter() // 优雅停止上报调度
           ├── doReport()     // 最终全量上报一次
           ├── ThreadPoolManager.shutdown(MQ_CONSUME)
           └── activeMQEmbeddedServer.stop()
```

---

## 三、断点续评机制

### 3.1 数据加载的幂等判断

`loadData()` 通过以下条件判断是否需要重新加载数据：

```java
long queueSize = activeMQEmbeddedServer.getQueueMessageCount(taskNameUuid);
int count = dataItemMapper.count();
// 队列有消息 OR 数据库有结果 → 已加载，跳过
if (queueSize > 0 || (queueSize == 0 && count > 0)) {
    return;
}
```

| 场景 | queueSize | DB count | 行为 |
|---|---|---|---|
| 首次运行 | 0 | 0 | 重新加载数据到 MQ |
| 正常运行中 | > 0 | >= 0 | 跳过加载 |
| 断点续评（部分完成） | > 0 | > 0 | 跳过加载，MQ 中剩余消息即为未处理数据 |
| 已全部完成 | 0 | > 0 | 跳过加载 |

### 3.2 评测的幂等判断

每条消息被成功处理后，其 `JMSMessageID` 会写入去重表 `mq_message_processed`。断点续评时：

1. MQ 基于 **KahaDB 持久化**存储，重启后未 commit 的消息会重新投递
2. 消费线程先查去重表 `isProcess(messageId)`，已处理则跳过
3. ActiveMQ 的重投递策略配置：初始延迟 2s、重试间隔 3s、最多重试 3 次

### 3.3 `enableResume` 开关

| 值 | 行为 |
|---|---|
| `true`（默认） | 保留 MQ 和 DB 数据文件，断点后从上次进度继续 |
| `false` | 每次启动前删除 `eval_cache_data/{taskNameUuid}/` 目录和 `.db` 文件，全量重跑 |

---

## 四、消费并发模型（DeltaEvalFacade）

### 4.1 整体流程

```
eval()
 ├── total = getRemainDataCount()      // MQ 当前队列深度（快照值）
 ├── total <= 0 → latch.countDown()   // 无需处理，提前放行
 └── for i in [0, threadNum):
      pool.submit(消费线程)

消费线程:
 └── while consumed < total && emptyRounds < MAX_EMPTY_ROUNDS:
      batchReceiveInTx(...)
       ├── batch 为空 → ROLLBACK（空批次回滚），emptyRounds++
       └── batch 非空 → emptyRounds = 0
            ├── for 每条消息:
            │    ├── isProcess? → 跳过（不计入 actualProcessed）
            │    └── evalAndInsert() → makeProcessed() → actualProcessed++
            ├── consumed += actualProcessed
            ├── consumed >= total → latch.countDown() → STOP（提交并停止）
            └── else → CONTINUE（提交并继续）
      [finally] latch.countDown()  // 兜底，防止异常时主线程永久等待
```

### 4.2 `BatchResult` 事务语义

`batchReceiveInTx` 回调返回三态枚举，决定本批次的 JMS 事务行为：

| 返回值 | 事务操作 | 适用场景 |
|---|---|---|
| `CONTINUE` | `session.commit()` | 正常处理完一批，继续拉取 |
| `STOP` | `session.commit()` | 消费完毕或主动终止，本批提交 |
| `ROLLBACK` | `session.rollback()` | 空批次或处理异常，消息重新入队 |

> **关键设计**：消费完毕时必须返回 `STOP`（而非 `ROLLBACK`），确保最后一批消息被 commit，不会重复投递。

### 4.3 `consumed` 计数语义

`consumed` 只累加**本次真正执行了 `evalAndInsert` 的消息数**（`actualProcessed`），幂等跳过的消息不计入。这确保了：

- 断点续评场景下，已处理的消息不会被重复计数
- `consumed >= total` 时确实意味着所有未处理消息都被处理完毕

### 4.4 空批次兜底（MAX_EMPTY_ROUNDS）

MQ 因异常状态无法拉到消息时，通过 `emptyRounds` 计数器防止线程死循环：

```java
if (consumed.get() < total && activeMQEmbeddedServer.getQueueMessageCount(taskNameUuid) == 0) {
    emptyRounds++;  // MQ 已空但 consumed 未达标，累计空轮询次数
} else {
    emptyRounds = 0;
}
// 超过 10 次空轮询，强制退出，由 finally 兜底 latch.countDown()
```

> 空批次本身返回 `ROLLBACK`（无消息可提交，回滚是安全的）。

---

## 五、有序消费模型（OrderedDeltaEvalFacade）

`OrderedDeltaEvalFacade` 继承 `DeltaEvalFacade`，重写 `eval()` 方法，实现**同 key 数据在同一线程中按顺序处理**，适合多轮对话评测（同一 session 的多轮必须按轮次顺序执行）。

### 5.1 有序处理核心：`OrderedDispatcher`

```
批次消息
  └── OrderedBatchRunner.runOrderedBatch(batch, task, keyExtractor, comparator, threadNum, timeout)
        ├── 按 keyExtractor 分组（如 sessionId）
        ├── 组内按 comparator 排序（如 turnIndex）
        └── 分发到 OrderedDispatcher
              ├── 相同 key → 路由到同一 EventLoop（单线程队列）
              └── 不同 key → 哈希分发到不同 EventLoop（并行）
```

`OrderedDispatcher` 内部维护 `loopCount` 个 `EventLoop`（每个都是独立线程 + `LinkedBlockingQueue`），相同 key 的任务始终由同一个 EventLoop 串行执行。

### 5.2 与父类 `eval()` 的差异

| 对比项 | `DeltaEvalFacade` | `OrderedDeltaEvalFacade` |
|---|---|---|
| 并发模型 | N 个消费线程并行拉取 MQ | 单线程拉取 MQ，`OrderedDispatcher` 内部并发处理 |
| 顺序保证 | 无顺序保证 | 同 key 内严格顺序 |
| `consumed` 计数 | 只计真正处理的消息 | 计入本批所有消息（含失败），防止失败消息阻塞退出 |
| 空批次处理 | 外层 `emptyRounds` 计数 | 内层 `AtomicInteger emptyRounds` 计数 |
| 超时计算 | 无 | `size * messageProcessMaxTime`（秒） |

### 5.3 `consumed` 计数为何包含失败消息

`OrderedDeltaEvalFacade` 中 `consumed.addAndGet(batch.size())`（含失败）的原因：

- 失败的消息在 `OrderedBatchRunner` 内部返回 `null`，但**整批事务已 commit**（`BatchResult.CONTINUE/STOP`）
- 失败消息无法重新回到 MQ（已 commit），不会被重投递
- 若只计成功数，失败消息会造成 `consumed` 永远无法达到 `remainCount`，导致永不结束
- 失败情况已记录 `warn` 日志，上层可通过日志排查

### 5.4 子类需实现的抽象方法

```java
// 1. 返回数据的分组 key（同 key 的数据会被同一线程顺序处理）
public abstract String prepareOrderKey(InputData inputData);

// 2. 返回组内元素的排序器（null 表示按到达顺序处理）
public abstract Comparator<InputData> prepareComparator();
```

**示例**：多轮对话评测，按 `sessionId` 分组，按 `turnIndex` 排序：

```java
@Override
public String prepareOrderKey(InputData inputData) {
    return inputData.getFieldValue("sessionId");
}

@Override
public Comparator<InputData> prepareComparator() {
    return Comparator.comparingInt(d -> Integer.parseInt(d.getFieldValue("turnIndex")));
}
```

---

## 六、周期性上报机制

```
report()
 └── reporterScheduler.scheduleWithFixedDelay(doReport, 0, reportInterval, SECONDS)

doReport()
 ├── dataItemMapper.queryAll()    // 从 SQLite 读取所有已完成数据
 ├── 克隆 reportWorkflow
 └── reportWorkflow.execute()    // 执行上报工作流（Counter → Reporter）
```

> `doReport()` 内部捕获所有异常（不向上抛出），防止调度器因单次异常停止。

### 上报调度器生命周期

| 阶段 | 操作 |
|---|---|
| `eval()` 调用后立刻 | `report()` 启动调度，`initialDelay=0`（立即执行第一次） |
| `consumeFuture.get()` 返回后 | `stopReporter()` 取消调度，等待当前批次完成 |
| `stopReporter()` 后 | `doReport()` 执行最终全量上报 |

---

## 七、SQLite 数据库表结构

| 表名 | 字段 | 说明 |
|---|---|---|
| `eval_task` | `task_name_uuid`, `all_count`, `status`, `create_time`, `update_time` | 任务元信息和状态 |
| `data_item` | `data_index`, `data_item_json` | 评测结果持久化（JSON 序列化的 `DataItem`） |
| `mq_message_processed` | `message_id` | 幂等去重表，记录已处理的 MQ 消息 ID |

---

## 八、`DeltaEvalConfig` 关键配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `batchSize` | `10` | 每次从 MQ 拉取的消息数量 |
| `reportInterval` | `30`（秒） | 周期上报间隔 |
| `mqReceiveTimeout` | `10000`（毫秒） | 单次 `consumer.receive()` 的超时时间 |
| `enableResume` | `true` | 是否开启断点续评 |
| `messageProcessMaxTime` | `60`（秒） | `OrderedDeltaEvalFacade` 中每条消息的最大处理时间，用于计算 `OrderedBatchRunner` 的总超时 |
| `threadNum` | 继承自 `EvalConfig` | 消费线程数（`DeltaEvalFacade`）/ `OrderedDispatcher` 的 EventLoop 数（`OrderedDeltaEvalFacade`） |

> 以上配置均可通过 JVM 参数在运行时覆盖，如 `-DbatchSize=20`。

---

## 九、历史 Bug 记录

以下记录了曾存在的并发死锁 Bug 及修复方案，方便后续开发者理解代码中的防御性逻辑。

### Bug 1：消费完毕时 rollback 导致消息重复投递

**现象**：消费完最后一批消息后，框架仍不退出，日志中反复出现"Message already processed"。

**根因**：`batchReceiveInTx` 回调返回 `false`（当时语义为"停止"）会触发 `session.rollback()`，导致最后一批已处理的消息被重新投递回 MQ。

**修复**：引入 `BatchResult` 三态枚举（`CONTINUE` / `STOP` / `ROLLBACK`），消费完毕返回 `STOP`，确保最后一批 commit。

### Bug 2：空批次导致消费线程死循环，latch 永不触发

**现象**：所有数据处理完后，程序不退出，CPU 持续占用（线程空轮询）。

**根因**：MQ 空后 `consumer.receive(timeout)` 立即返回 `null`，批次为空，旧代码直接 `return false`（rollback），循环条件 `consumed < total` 仍为 `true`（因为 `total` 是快照值，而 MQ 此时为空导致 `consumed` 无法递增），线程永远轮询空 MQ。

**修复**：空批次返回 `ROLLBACK`；外层增加 `emptyRounds` 计数器，超过 `MAX_EMPTY_ROUNDS=10` 次后强制退出；`finally` 块兜底 `latch.countDown()`。

### Bug 3：`total=0` 时 latch 永不触发

**现象**：断点续评且上次已全部完成时，程序启动后永久阻塞不退出。

**根因**：`total = getRemainDataCount()` 返回 0，`for (int i = 0; i < threadNum && total > 0; i++)` 不提交任何线程，但 `latch` 初始值为 1，`latch.await()` 永远阻塞。

**修复**：在 `eval()` 中提前判断 `total <= 0` 并执行 `latch.countDown()`，跳过消费阶段。

### Bug 4：`consumed` 计数包含幂等跳过消息，导致计数失真

**现象**：断点续评时，部分数据被重复计数，`consumed` 超过 `total`，提前触发 `latch.countDown()`，导致上报数据不完整。

**根因**：旧代码在 for 循环外执行 `consumed.addAndGet(batch.size())`，把幂等跳过的消息也计入了 `consumed`。

**修复**：改为循环内 `actualProcessed++`，只累加真正执行了 `evalAndInsert` 的消息数。

