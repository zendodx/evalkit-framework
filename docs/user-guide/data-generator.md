---
layout: default
title: 数据生成器（DataGenerator）
parent: 用户指南
nav_order: 7
---

# 数据生成器（DataGenerator）

## 概述

**数据生成器（DataGenerator）** 是 EvalKit 框架中用于**程序化生成评测数据集**的组件。

在实际评测中，你可能不总是有现成的数据集，而需要通过某种逻辑（如 Mock 规则、LLM 生成、知识图谱推导等）来自动构造评测数据。`DataGenerator` 就是为这类场景设计的。

`DataGenerator` 继承自 `DataLoader`，也是工作流中的一个节点，它在执行时**生成数据**并将结果注入到工作流上下文，后续节点（如评估器）可以直接使用这些数据。

---

## 类继承关系

```
DataLoader（数据加载器）
└── DataGenerator（抽象，数据生成器基类）
    ├── EvalCaseDataGenerator         多轮对话评测数据生成器
    ├── LoaderBasedDataGenerator      基于数据加载器的数据生成器（抽象）
    ├── KGBasedQueryGenerator         基于知识图谱的数据生成器
    └── MultiDataGenerator            多源数据生成器（组合多个生成器）
```

---

## 公共配置（`DataGeneratorConfig`）

所有数据生成器都继承自 `DataGeneratorConfig`，包含以下通用配置：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `enableOutputFile` | boolean | false | 是否将生成结果导出到文件 |
| `outputFilePath` | String | `attachments` | 导出文件的目录路径 |
| `outputFileName` | String | `export_yyyyMMddHHmmss` | 导出文件名（不含扩展名） |
| `genDataExporterList` | `List<GenDataExporter>` | `[ExcelGenDataExporter]` | 导出器列表，默认导出 Excel |
| `threadNum` | Integer | 1 | 并发生成的线程数 |

---

## 数据导出器（GenDataExporter）

数据生成完成后，如果开启了 `enableOutputFile`，框架会调用配置的导出器将数据保存到文件。

### 内置导出器

| 导出器 | 输出格式 | 说明 |
|---|---|---|
| `ExcelGenDataExporter` | `.xlsx` | 输出 Excel 文件（默认） |
| `JsonFileGenDataExporter` | `.json` | 输出 JSON 文件 |

### 同时导出多种格式

```java
DataGeneratorConfig config = EvalCaseDataGeneratorConfig.builder()
    .enableOutputFile(true)
    .outputFilePath("attachments/generated")
    .outputFileName("my_eval_data")
    .genDataExporterList(ListUtils.of(
        new ExcelGenDataExporter(),       // 同时导出Excel
        new JsonFileGenDataExporter()     // 同时导出JSON
    ))
    .build();
```

---

## 内置实现详解

### 1. EvalCaseDataGenerator — 多轮对话数据生成器

`EvalCaseDataGenerator` 是最常用的数据生成器，专门用于生成**多轮对话形式**的评测用例。

它通过 `QueryGenerator` 按轮次生成 Query，并维护每个会话的上下文依赖关系，支持随机轮次。

#### 配置说明（`EvalCaseDataGeneratorConfig`）

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `genCount` | int | 1 | 生成的会话数量 |
| `roundCount` | int | 1 | 每个会话的最大轮次数 |
| `randomRound` | boolean | false | 是否随机轮次（1~`roundCount`随机） |
| `queryGenerator` | `QueryGenerator` | 无（**必填**） | Query 生成器实例 |
| `sessionFieldKey` | String | `sessionId` | 输出数据中会话 ID 的字段名 |
| `roundFieldKey` | String | `round` | 输出数据中轮次的字段名 |
| `queryFieldKey` | String | `query` | 输出数据中 Query 的字段名 |
| `groundTruthFieldKey` | String | `groundTruth` | 标准答案字段名 |
| `intentFieldKey` | String | `intent` | 意图字段名 |
| `contextDependencyFieldKey` | String | `contextDependency` | 上下文依赖字段名 |

#### 输出数据格式

每条生成的数据是一个 Map，包含以下字段：

```json
{
  "sessionId": "uuid-xxxx",
  "round": 1,
  "query": "帮我找北京的酒店",
  "groundTruth": "",
  "intent": "",
  "contextDependency": "",
  "extra": ""
}
```

> 💡 `groundTruth`、`intent` 字段默认为空字符串。如果需要填充这些字段，继承 `EvalCaseDataGenerator` 并覆盖 `prepareGroundTruth()` 和 `prepareIntent()` 方法即可。

#### 生命周期钩子

你可以通过继承 `EvalCaseDataGenerator` 来覆盖以下方法：

| 方法 | 说明 |
|---|---|
| `prepareQuery(contextDependency)` | 生成当前轮次的 Query，`contextDependency` 是前几轮的对话历史 |
| `prepareGroundTruth()` | 生成标准答案，默认返回空字符串 |
| `prepareIntent()` | 生成意图标签，默认返回空字符串 |
| `prepareExtra()` | 生成额外字段，默认返回 `{"extra": ""}` |
| `genSessionId()` | 生成会话 ID，默认使用 UUID |

#### 使用示例

```java
// 方式1：使用内置的MockQueryGenerator
QueryGenerator queryGenerator = new MyMockQueryGenerator();

EvalCaseDataGenerator generator = new EvalCaseDataGenerator(
    EvalCaseDataGeneratorConfig.builder()
        .genCount(100)          // 生成100个会话
        .roundCount(3)          // 每个会话3轮
        .randomRound(true)      // 随机轮次（1~3轮）
        .queryGenerator(queryGenerator)
        .threadNum(4)           // 4线程并发
        .enableOutputFile(true) // 导出数据
        .outputFileName("hotel_eval_data")
        .build()
);
```

**方式2：继承并自定义标准答案**

```java
public class HotelEvalCaseGenerator extends EvalCaseDataGenerator {
    public HotelEvalCaseGenerator() {
        super(EvalCaseDataGeneratorConfig.builder()
            .genCount(200)
            .roundCount(5)
            .queryGenerator(new HotelQueryGenerator())
            .build());
    }

    @Override
    protected String prepareGroundTruth() {
        // 从数据库或文件中查询对应的标准答案
        return hotelService.getGroundTruth();
    }

    @Override
    protected String prepareIntent() {
        return "酒店预订";
    }
}
```

---

### 2. LoaderBasedDataGenerator — 基于数据加载器的生成器（抽象类）

当你需要**先加载一批原始数据，再对每条数据进行二次加工**时，使用 `LoaderBasedDataGenerator`。

**应用场景示例：**
- 从数据库中加载一批商品数据，对每条商品数据调用 LLM 生成搜索 Query
- 从 CSV 中读取用户历史记录，为每条记录扩展额外的评测字段

#### 配置说明（`LoaderBasedDataGeneratorConfig`）

继承自 `DataGeneratorConfig`，额外配置：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `dataLoader` | `DataLoader` | 无（**必填**） | 原始数据加载器 |
| `threadNum` | Integer | 1 | 并发处理线程数 |

#### 使用示例

```java
public class ProductQueryGenerator extends LoaderBasedDataGenerator {
    private final LLMService llmService;

    public ProductQueryGenerator(LLMService llmService) {
        super(LoaderBasedDataGeneratorConfig.builder()
            .dataLoader(new ExcelDataLoader("products.xlsx", ExcelDataLoaderConfig.builder().build()))
            .threadNum(5)
            .enableOutputFile(true)
            .build());
        this.llmService = llmService;
    }

    @Override
    public List<Map<String, Object>> processSingleInputData(Map<String, Object> inputItem) {
        // inputItem 是每一行原始数据（如 {productName: "XX手机", category: "电子产品"}）
        String productName = (String) inputItem.get("productName");

        // 调用LLM为每个商品生成搜索Query
        String query = llmService.chat("请为以下商品生成一条用户搜索Query：" + productName);

        Map<String, Object> result = new HashMap<>(inputItem);
        result.put("query", query);
        return Collections.singletonList(result);
    }
}
```

---

### 3. KGBasedQueryGenerator — 基于知识图谱的数据生成器

`KGBasedQueryGenerator` 是 EvalKit 框架中最强大的数据生成器，专门用于**基于知识图谱（Knowledge Graph）生成多轮对话评测数据**。

#### 核心工作流程

```
知识图谱文件 (.ttl)
       ↓  SPARQL查询抽取数据
    实体属性Map
       ↓  传给 LLM 生成对话
    多轮 Turn 列表
       ↓  相似度过滤（可选）
    组装 TestCase
       ↓
    格式化输出
```

具体步骤：
1. **加载场景配置**：从 JSON 文件中读取场景描述、SPARQL 查询模板和标杆用例（GoldenCase）
2. **图谱数据抽取**：用 SPARQL 查询从 TTL 格式的知识图谱中抽取实体数据，并随机采样一条
3. **LLM 生成对话**：将抽取的实体数据和标杆用例传给 LLM，生成新的多轮对话
4. **相似度过滤**（可选）：检查生成的对话与标杆用例的相似度是否在合理范围内
5. **组装测试用例**：将 LLM 生成的对话与场景配置中的断言规则合并，形成完整的 `TestCase`
6. **格式化输出**：将 `TestCase` 转换为评测框架需要的 Map 结构

#### 场景配置文件（scenario_config.json）

每个场景需要一个 JSON 配置文件，格式如下：

```json
{
  "scenarioId": "hotel_booking",
  "sparqlTemplate": "PREFIX ex: <http://example.com/hotel#> SELECT ?hotelName ?price ?location WHERE { ?hotel ex:name ?hotelName; ex:price ?price; ex:location ?location. }",
  "minSimilarity": 0.3,
  "maxSimilarity": 0.8,
  "goldenCase": {
    "kgDataUsed": {
      "hotelName": "故宫旁豪华大床房",
      "price": "688",
      "location": "北京市东城区"
    },
    "dialogue": [
      {
        "turn": 1,
        "query": "我想找北京东城区的酒店",
        "assertType": "KEYWORD_MATCH",
        "expectedVars": ["location"]
      },
      {
        "turn": 2,
        "query": "有没有688元左右的",
        "assertType": "KEYWORD_MATCH",
        "expectedVars": ["price"]
      },
      {
        "turn": 3,
        "query": "帮我预订故宫旁豪华大床房吧",
        "assertType": "KEYWORD_MATCH",
        "expectedVars": ["hotelName"]
      }
    ]
  }
}
```

**字段说明：**

| 字段 | 说明 |
|---|---|
| `scenarioId` | 场景唯一标识 |
| `sparqlTemplate` | 用于从知识图谱中抽取数据的 SPARQL 查询语句 |
| `minSimilarity` | 生成对话与标杆对话的最小相似度（余弦相似度） |
| `maxSimilarity` | 生成对话与标杆对话的最大相似度（超过此值视为过于相似，同样丢弃） |
| `goldenCase.kgDataUsed` | 标杆用例使用的图谱数据（键值对） |
| `goldenCase.dialogue` | 标杆对话轮次列表 |
| `dialogue[].turn` | 轮次编号（从 1 开始） |
| `dialogue[].query` | 该轮的标杆 Query |
| `dialogue[].assertType` | 断言类型（如 `KEYWORD_MATCH`） |
| `dialogue[].expectedVars` | 期望关键词对应的图谱变量名，运行时会替换为真实图谱数据 |

#### 知识图谱文件（Turtle 格式 .ttl）

```turtle
@prefix ex: <http://example.com/hotel#> .

ex:hotel1 a ex:Hotel ;
    ex:name "故宫旁豪华大床房" ;
    ex:price "688" ;
    ex:location "北京市东城区" .

ex:hotel2 a ex:Hotel ;
    ex:name "西湖景观大床房" ;
    ex:price "528" ;
    ex:location "浙江省杭州市西湖区" .
```

#### 配置说明（`KGBasedQueryGeneratorConfig`）

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `kgFilePath` | String | 无（**必填**） | 知识图谱文件路径（文件系统路径或 classpath 路径） |
| `scenarioConfigFilePath` | `List<String>` | 无（**必填**） | 场景配置文件路径列表 |
| `llmService` | `LLMService` | 无（**必填**） | 调用的大模型服务 |
| `enableSimilarityFilter` | Boolean | false | 是否开启相似度过滤 |
| `generateCount` | Integer | 1 | 每个场景配置生成的会话数 |
| `enableOneRawOneSession` | Boolean | false | true=每行数据代表一个完整会话；false=每行代表一轮 Query |
| `caseIdPrefix` | String | `gen_case_` | 生成用例 ID 的前缀 |
| `sessionIdFieldName` | String | `sessionId` | 输出中会话 ID 的字段名 |
| `turnFieldName` | String | `turn` | 输出中轮次的字段名 |
| `queryFieldName` | String | `query` | 输出中 Query 的字段名 |

#### 两种输出格式对比

**`enableOneRawOneSession = false`（默认，每行一轮 Query）**

更常见的格式，方便与评测框架中的 `OrderedApiCompletion`（有序 API 调用器）配合使用：

```json
[
  {"testCaseId": "gen_case_001", "scenarioId": "hotel_booking", "sessionId": "uuid-xxx", "turn": 1, "query": "帮我搜索一下杭州的酒店"},
  {"testCaseId": "gen_case_001", "scenarioId": "hotel_booking", "sessionId": "uuid-xxx", "turn": 2, "query": "有528元的吗"},
  {"testCaseId": "gen_case_001", "scenarioId": "hotel_booking", "sessionId": "uuid-xxx", "turn": 3, "query": "帮我预订西湖景观大床房"}
]
```

**`enableOneRawOneSession = true`（每行一个完整会话）**

每行数据包含整个会话的所有轮次：

```json
[
  {
    "testCaseId": "gen_case_001",
    "scenarioId": "hotel_booking",
    "kgSource": {"hotelName": "西湖景观大床房", "price": "528"},
    "queries": [
      {"sessionId": "uuid-xxx", "turn": 1, "query": "帮我搜索一下杭州的酒店"},
      {"sessionId": "uuid-xxx", "turn": 2, "query": "有528元的吗"}
    ]
  }
]
```

#### 完整使用示例

```java
// 1. 配置LLM服务
LLMService llmService = LLMServiceFactory.create(
    LLMServiceConfig.builder()
        .apiKey("your-api-key")
        .model("gpt-4o")
        .build()
);

// 2. 构建KGBasedQueryGenerator配置
KGBasedQueryGeneratorConfig config = KGBasedQueryGeneratorConfig.builder()
    .kgFilePath("src/test/resources/knowledge_graph/hotel.ttl")  // KG文件路径
    .scenarioConfigFilePath(ListUtils.of(
        "src/test/resources/scenarios/hotel_booking.json",       // 场景配置1
        "src/test/resources/scenarios/hotel_cancel.json"         // 场景配置2
    ))
    .llmService(llmService)
    .generateCount(10)                  // 每个场景生成10个会话
    .enableSimilarityFilter(true)       // 开启相似度过滤
    .threadNum(3)                       // 3线程并发
    .enableOutputFile(true)             // 导出生成数据
    .outputFileName("hotel_kg_eval_data")
    .genDataExporterList(ListUtils.of(
        new ExcelGenDataExporter(),
        new JsonFileGenDataExporter()
    ))
    .build();

// 3. 将生成器加入工作流
KGBasedQueryGenerator generator = new KGBasedQueryGenerator(config);

Workflow workflow = Workflow.builder()
    .addNode(generator)
    // ... 后续评测节点
    .build();
```

---

### 4. MultiDataGenerator — 多源数据生成器

`MultiDataGenerator` 将**多个数据生成器**组合成一个，顺序执行并合并所有结果。适合需要从多种来源生成数据的场景。

```java
MultiDataGeneratorConfig config = MultiDataGeneratorConfig.builder()
    .dataGenerators(ListUtils.of(
        new EvalCaseDataGenerator(config1),  // 来源1
        new EvalCaseDataGenerator(config2),  // 来源2
        new KGBasedQueryGenerator(kgConfig)  // 来源3
    ))
    .build();

MultiDataGenerator generator = new MultiDataGenerator(config);
```

---

## 数据生成器 vs 数据加载器

| 对比维度 | DataLoader | DataGenerator |
|---|---|---|
| 数据来源 | 外部文件/数据库/API | 程序逻辑动态生成 |
| 常见用途 | 加载已有的评测数据集 | 自动化构建新的评测数据集 |
| 是否依赖外部存储 | 是（文件、数据库等） | 否（可完全通过代码生成） |
| 支持断点续评 | 不直接支持 | 不直接支持（需配合 `DeltaEvalFacade`） |

---

## 完整流程示例：自动构建评测数据集

下面展示一个完整的流程：使用 `KGBasedQueryGenerator` 生成数据集，然后直接进行评测：

```java
// === 第一步：生成数据 ===
KGBasedQueryGeneratorConfig genConfig = KGBasedQueryGeneratorConfig.builder()
    .kgFilePath("resources/hotel.ttl")
    .scenarioConfigFilePath(Arrays.asList("resources/scenarios/hotel.json"))
    .llmService(llmService)
    .generateCount(50)
    .enableOutputFile(true)
    .outputFileName("generated_eval_data")
    .build();

// === 第二步：评测生成的数据 ===
FullEvalConfig evalConfig = FullEvalConfig.builder()
    .taskName("酒店评测-KG生成数据")
    .dataLoader(new JsonDataLoader("attachments/generated_eval_data.json", config))
    .evalWorkflow(Workflow.builder()
        .addNode(new MyHotelApiCompletion())
        .addNode(new VectorSimilarityScorer(scorerConfig))
        .build())
    .reportWorkflow(Workflow.builder()
        .addNode(new BasicCounter())
        .addNode(new HtmlReporter("hotel_eval_report"))
        .build())
    .threadNum(5)
    .passScore(0.6)
    .build();

new FullEvalFacade(evalConfig).run();

