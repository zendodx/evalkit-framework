---
layout: default
title: 全模块汇总导览
parent: 用户指南
nav_order: 16
---

# EvalKit Framework 全模块汇总导览

---

## 一、框架全景

EvalKit Framework 是一个基于 Java 的 **自动化 AI 评测框架**，核心是 **DAG 工作流**，将评测任务拆分为若干可组合的节点：

```
数据准备层                    评测核心层                    结果处理层
──────────                  ────────────                  ──────────
DataLoader                  ApiCompletion                 Counter
DataLoaderWrapper    ──▶    Scorer × N        ──▶        Reporter
DataGenerator               └── Checker
QueryGenerator
```

所有节点通过 `WorkflowBuilder.link()` 连接，最终由 **EvalFacade** 统一调度执行。

---

## 二、各模块速查表

### 2.1 数据准备

| 模块 | 类名 | 核心能力 | 文档链接 |
|---|---|---|---|
| **数据加载器** | `DataLoader`（抽象基类） | 从任意来源加载评测数据 | [dataloader.md](./dataloader.md) |
| | `ExcelDataLoader` | 读取 `.xlsx` 文件 | |
| | `CsvDataLoader` | 读取 `.csv` 文件 | |
| | `JsonFileDataLoader` | 读取 `.json` 文件，支持 JSONPath | |
| | `JsonTextDataLoader` | 从 JSON 字符串加载（抽象） | |
| | `ApiDataLoader` | 调用 HTTP API 拉取数据（抽象） | |
| | `JdbcDataLoader` | 从 MySQL 等数据库加载（抽象） | |
| | `MultiDataLoader` | 合并多个加载器的数据 | |
| **数据装饰器** | `DataLoaderWrapper` | 数据加载后的增强处理基类 | [dataloader-wrapper.md](./dataloader-wrapper.md) |
| | `MockDataLoaderWrapper` | 替换 `占位符` 为随机 Mock 值 | |
| | `PromptDataLoaderWrapper` | 用 LLM 对字段进行增强（抽象） | |
| | `PolishDataLoaderWrapper` | 将 query 改写为口语化表达（抽象） | |
| **Query 生成器** | `QueryGenerator`（接口） | 生成测试用 Query 字符串 | [querygen.md](./querygen.md) |
| | `MockQueryGenerator` | 基于 Mock 规则模板批量生成 | |
| | `PromptBasedQueryGenerator` | 调用 LLM 生成语义自然的 Query | |
| **数据生成器** | `DataGenerator`（抽象基类） | 程序化生成评测数据集 | [data-generator.md](./data-generator.md) |
| | `EvalCaseDataGenerator` | 生成多轮对话评测用例 | |
| | `LoaderBasedDataGenerator` | 对已加载数据进行二次加工（抽象） | |
| | `KGBasedQueryGenerator` | 基于知识图谱（TTL）生成多轮对话数据 | |
| | `MultiDataGenerator` | 合并多个生成器的结果 | |

### 2.2 评测核心

| 模块 | 类名 | 核心能力 | 文档链接 |
|---|---|---|---|
| **接口调用器** | `ApiCompletion` | 调用被测业务接口的通用基类 | [api-completion.md](./api-completion.md) |
| | `HttpApiCompletion` | 封装 HTTP 调用，自动组装请求（抽象） | |
| | `OrderedApiCompletion` | 保证同一会话多轮 query 有序执行（抽象） | |
| **评估器** | `Scorer` | 自定义规则打分基类 | [scorer.md](./scorer.md) |
| | `VectorSimilarityScorer` | TF-IDF 余弦相似度打分（无需 LLM） | |
| | `PromptBasedScorer` | 基于 LLM Prompt 打分（抽象） | |
| | `AnswerRelevancyScorer` | LLM 评估答案切题性（抽象） | |
| | `SemanticConsistencyScorer` | LLM 评估语义一致性（抽象） | |
| | `SecurityScorer` | LLM 内容安全检测（抽象） | |
| | `GSBScorer` | 多维度综合打分，A/B 实验对比（抽象） | |
| | `DifyWorkflowScorer` | 调用 Dify 平台工作流打分（抽象） | |
| | `RubricBasedScorer` | 多维度量规评估，每维度独立 LLM 调用 + CoT（抽象） | |
| | `MultiCheckerBasedScorer` | 组合多个 Checker 打分（抽象） | |
| **检查器** | `AbstractChecker` | 细粒度规则检查基类 | [checker.md](./checker.md) |
| | `LLMBasedChecker` | LLM 驱动的检查器（抽象） | |
| | `CheckItem` | 单个检查项（最小打分单元） | |

### 2.3 大模型服务

| 模块 | 类名 | 核心能力 | 文档链接 |
|---|---|---|---|
| **LLM 服务** | `LLMService`（接口） | 统一 LLM 调用接口 | [llm-service.md](./llm-service.md) |
| | `LLMServiceFactory` | 工厂方法，创建 OpenAI/DeepSeek 实现 | |

### 2.4 结果处理

| 模块 | 类名 | 核心能力 | 文档链接 |
|---|---|---|---|
| **统计器** | `BasicCounter` | 通过率、耗时、分数分布统计 | [counter.md](./counter.md) |
| | `MetricCounter` | 按指标名称分组统计（抽象） | |
| | `AttributeCounter` | LLM 驱动问题归因分析 V1 | |
| | `AttributeCounterV2` | LLM 归因分析 V2（含类别/置信度/情感极性） | |
| **上报器** | `StdReporter` | 打印到控制台 | [reporter.md](./reporter.md) |
| | `ExcelReporter` | 输出 `.xlsx` 文件 | |
| | `CsvReporter` | 输出 `.csv` 文件 | |
| | `JsonReporter` | 输出 `.json` 文件 | |
| | `HtmlReporter` | 输出可视化 HTML 报告 | |
| | `JdbcReport` | 写入关系型数据库（抽象） | |
| | `ApiReporter` | 逐条 HTTP 推送到远程平台（抽象） | |

### 2.5 流程控制

| 模块 | 类名 | 核心能力 | 文档链接 |
|---|---|---|---|
| **评测门面** | `FullEvalFacade` | 全量一次性评测 | [facade.md](./facade.md) |
| | `DeltaEvalFacade` | 增量评测，支持断点续评（MQ + SQLite） | |
| | `OrderedDeltaEvalFacade` | 有序增量评测，同会话按轮次串行处理 | |
| **调试器** | `JsonFileDebugger` | 注入 JSON 文件数据，跳过前置节点 | [debugger.md](./debugger.md) |
| | `JsonStringDebugger` | 注入 JSON 字符串数据，适合单元测试 | |

---

## 三、典型工作流组合

### 场景 A：单轮问答快速评测（最简）

```
DataLoader → ApiCompletion → VectorSimilarityScorer → BasicCounter → StdReporter
```

适用：数据量小、只需语义相似度打分、快速验证。

---

### 场景 B：多指标并行评测 + HTML 报告

```
DataLoader → DataLoaderWrapper → ApiCompletion
                                    ├── VectorSimilarityScorer  ┐
                                    ├── AnswerRelevancyScorer   ├─→ BasicCounter → HtmlReporter
                                    └── SecurityScorer          ┘
```

适用：需要多维度评分、对外展示评测报告。

---

### 场景 C：量规多维度评估

```
DataLoader → ApiCompletion → RubricBasedScorer（Safety★ + Accuracy + Fluency）→ BasicCounter → HtmlReporter
```

适用：需要从安全性、准确性、流畅性等多角度系统评估模型输出，且评分维度不同量程时。

---

### 场景 D：大规模多轮对话增量评测（生产级）

```
KGBasedQueryGenerator（生成数据）
    ↓
OrderedDeltaEvalFacade
    ├── OrderedApiCompletion（有序多轮调用）
    ├── MultiCheckerBasedScorer（细粒度规则+LLM检查）
    ├── BasicCounter
    ├── AttributeCounterV2（LLM归因分析）
    └── ExcelReporter + JsonReporter
```

适用：万级数据量、多轮对话、断点续评、需要根因分析。

---

### 场景 E：调试重跑（不重新调接口）

```
JsonFileDebugger（注入上次结果）
    → [VectorSimilarityScorer]（可选：重新打分）
    → BasicCounter
    → HtmlReporter
```

适用：修改了打分逻辑或统计逻辑，不想重新调用被测接口。

---

## 四、关键配置参数汇总

### EvalFacade 通用配置

| 参数 | 说明 | 典型值 |
|---|---|---|
| `taskName` | 任务名称，断点续评时需保持一致 | `"酒店评测-v1"` |
| `threadNum` | 并发线程数 | `5`～`20` |
| `passScore` | 整体通过分数线 | `0.6`～`0.8` |
| `offset` / `limit` | 数据分页范围 | 默认全量 |
| `enableResume` | 是否开启断点续评（仅 DeltaEval） | `true` |

### LLMService 常用配置

| 参数 | 说明 | 推荐值 |
|---|---|---|
| `temperature` | 评分类任务 | `0.1`～`0.3` |
| `temperature` | 生成类任务 | `0.5`～`0.7` |
| `openRetry` | 自动重试 | `true` |
| `retryTimes` | 重试次数 | `3`～`6` |
| `inPrice` / `outPrice` | Token 费用统计 | 按实际单价填写 |

### Scorer 关键配置

| 参数 | 说明 |
|---|---|
| `metricName` | 指标名称，在报告中显示 |
| `threshold` | 该指标通过的最低分数 |
| `star=true` | 必过指标——此项不过则整体不通过 |
| `dynamicTotalScore=true` | 总分由运行时动态决定（如 MultiCheckerBasedScorer） |

### RubricBasedScorer 关键配置

| 参数 | 说明 | 默认值 |
|---|---|---|
| `criteria` | 评估维度列表（`RubricCriteria`） | 必填 |
| `mergeStrategy` | 维度分数合并策略（见下表） | `WEIGHTED_AVERAGE` |
| `normalizeScore` | 是否将维度分归一化到 [0,1] 后再合并 | `true` |
| `criteriaThreadNum` | 维度并发 LLM 调用线程数 | 3 |
| `sampleTimes` | 每维度多次采样取均值 | 1 |

`RubricCriteria` 关键字段：`name`（维度名）、`definition`（定义）、`scoreType`（`STEPPED`/`BINARY`）、`maxScore`、`passScore`、`weight`、`star`（一票否决）、`condition`（条件执行函数）、`skipScore`（跳过时默认分）。

| 合并策略 | 说明 |
|---|---|
| `WEIGHTED_AVERAGE` | 加权平均（默认） |
| `SIMPLE_AVERAGE` | 简单平均 |
| `LOGICAL_AND` | 任意维度不达标则取最差维度得分 |
| `STAR_GATE` | star 维度不过则整体为 0，否则加权平均 |
| `COMPLETION_RATE` | 达标维度数 / 总维度数 |

---

## 五、三种评测模式选择

```
评测数据量 < 1000 条？
    YES ──▶ FullEvalFacade（简单、无需持久化）

    NO（数据量大）
        │
        ├── 同一会话需要有序处理（多轮对话）？
        │       YES ──▶ OrderedDeltaEvalFacade
        │
        └── 无顺序要求？
                ──▶ DeltaEvalFacade（断点续评 + 周期上报）
```

---

## 六、内置 Mocker 规则速查

框架内置 6 种 Mocker，占位符格式为`双花括号包裹规则名和参数`，下表中规则列省略花括号，实际使用时需加上。详细规则说明见 [mocker.md](./mocker)。

| 分类 | 常用规则示例 | 示例结果 |
|---|---|---|
| **精确日期** | `date` | `2026-05-26 10:30:00` |
| | `date yyyy-MM-dd` | `2026-05-26` |
| | `future_date 3 14` | `2026-06-03 10:30:00` |
| | `future_date 3 14 yyyy-MM-dd` | `2026-06-03` |
| | `past_date 14 365` | `2026-01-15 10:30:00` |
| **模糊日期** | `fuzzy_date` | `下周` / `月底` / `去年` |
| | `fuzzy_date week future` | `下周` / `周末` / `未来一周` |
| | `fuzzy_date year past` | `去年` / `前年` / `往年` |
| | `fuzzy_date human future` | `过两天` / `赶明儿` |
| **节假日** | `holiday` | `端午节` |
| | `future_holiday` | `中秋节` |
| | `solr_term_holiday` | `清明` |
| | `between_holiday 20260101 20261231` | `元宵节` |
| **行政区划** | `province` | `广东省` |
| | `city` / `city 四川省` | `成都市` |
| | `area 四川省 成都市` | `武侯区` |
| | `street 四川省 成都市 武侯区` | `玉林街道` |
| **景区 POI** | `scenic` | `故宫博物院` |
| | `scenic 四川省 成都市` | `大熊猫繁育研究基地` |
| **数字** | `int 1 100` | `42` |
| | `float 0.5 5.0` | `3.14` |

---

## 七、Reporter 输出文件对照

| 上报器 | 输出文件 | 适合场景 |
|---|---|---|
| `StdReporter` | 控制台 | 本地调试 |
| `ExcelReporter("result")` | `attachments/result.xlsx` + `result.count.xlsx` | 团队共享报告 |
| `CsvReporter("result")` | `attachments/result.csv` + `result.count.csv` | 数据二次处理 |
| `JsonReporter("result")` | `attachments/result.json` | 配合 Debugger 重播 |
| `HtmlReporter("result")` | `attachments/result.html` | 可视化展示、对外分享 |
| `JdbcReport` | 数据库表 | 历史数据持久化查询 |
| `ApiReporter` | 远程 HTTP API | 接入自研评测平台 |

> `JsonReporter` 输出的 JSON 格式与 `JsonFileDebugger` 输入格式完全匹配，可无缝衔接"保存→调试重播"工作流。

---

## 八、Checker 核心机制

```
MultiCheckerBasedScorer（Scorer 层）
  └── Checker A（如：格式检查）
  │     ├── CheckItem 1：包含必要字段（RULE 检查，star=true）
  │     └── CheckItem 2：JSON 格式合法（RULE 检查）
  └── Checker B（如：质量评估）
        ├── CheckItem 3：内容完整性（LLM 检查）
        └── CheckItem 4：语言流畅性（LLM 检查）
```

| CheckItem 特殊标记 | 含义 |
|---|---|
| `star=true` | 必过项：此项不通过则 Checker 整体失败 |
| `support=false` | 跳过此检查项，使用 `defaultScore` |
| `weight=2.0` | 该项权重为普通项的 2 倍 |

分数合并策略：`SumCheckItemScoreMergeStrategy`（求和）/ `AvgCheckItemScoreMergeStrategy`（平均）/ `MinCheckItemScoreMergeStrategy`（取最小）

---

## 九、数据流转：DataItem 结构

每条评测数据在工作流中以 `DataItem` 的形式流转：

```
DataItem
├── dataIndex          → 数据索引编号
├── inputData          → 原始输入（query / groundTruth / sessionId 等）
├── apiCompletionResult→ 接口调用结果（response / traceId / timeCost 等）
├── evalResult         → 评测结果（score / pass / scorerResults 列表）
└── extra              → 自定义扩展字段
```

各节点的数据访问方式：

```java
// 在 Scorer.eval() 中
String query    = dataItem.getInputData().get("query");
String response = dataItem.getApiCompletionResult().get("response");
double score    = dataItem.getEvalResult().getScore();
boolean pass    = dataItem.getEvalResult().isPassed();
long timeCost   = dataItem.getApiCompletionResult().getTimeCost();
```

---

## 十、模块依赖关系

```
evalkit-eval（评测核心）
    └── 依赖 evalkit-workflow（工作流引擎）
    └── 依赖 evalkit-infra（基础设施：LLMService / MQ / SQLite）
    └── 依赖 evalkit-common（工具库：JSON / HTTP / 文本 / Mock 等）
```

Maven 引入（只需引入 evalkit-eval，其余自动传递依赖）：

```xml
<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>evalkit-eval</artifactId>
    <version>1.4.0</version>
</dependency>
```

---

## 十一、快速决策：我该用哪个模块？

| 我需要… | 使用模块 |
|---|---|
| 从 Excel/CSV/JSON 文件加载数据 | `ExcelDataLoader` / `CsvDataLoader` / `JsonFileDataLoader` |
| 从数据库或 API 拉取数据 | `JdbcDataLoader` / `ApiDataLoader` |
| 在数据中随机填充城市、日期等占位符 | `MockDataLoaderWrapper` |
| 用 LLM 改写 query 为口语化表达 | `PolishDataLoaderWrapper` |
| 自动生成多轮对话测试数据集 | `EvalCaseDataGenerator` + `QueryGenerator` |
| 基于知识图谱生成语义多样的测试数据 | `KGBasedQueryGenerator` |
| 调用被测 HTTP 接口 | `HttpApiCompletion` |
| 保证多轮对话同一会话按顺序调用 | `OrderedApiCompletion` |
| 计算语义相似度（不用 LLM） | `VectorSimilarityScorer` |
| 用 LLM 判断答案质量 | `AnswerRelevancyScorer` / `GSBScorer` / `PromptBasedScorer` |
| 检查内容安全 | `SecurityScorer` |
| 多维度量规评估（每维度独立打分 + CoT） | `RubricBasedScorer` |
| 多维度细粒度检查（规则 + LLM 混合） | `MultiCheckerBasedScorer` + `AbstractChecker` |
| 统计通过率、耗时、分数分布 | `BasicCounter` |
| 对失败案例做根因分析 | `AttributeCounterV2` |
| 输出可视化 HTML 报告 | `HtmlReporter` |
| 数据量大、需要断点续评 | `DeltaEvalFacade` |
| 多轮对话 + 断点续评 | `OrderedDeltaEvalFacade` |
| 不想重新调接口，直接重新统计 | `JsonFileDebugger` |
| 接入 OpenAI / DeepSeek | `LLMServiceFactory` |
| 接入私有化部署模型 | 实现 `LLMService` 接口 |

