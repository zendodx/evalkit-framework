# Query 生成器（QueryGenerator）

## 概述

`QueryGenerator`（Query 生成器）是一个独立的**工具接口**，专门用于生成测试用的查询语句（Query）。它通常作为**数据生成器**（`EvalCaseDataGenerator`）的一个组件，负责提供每一轮对话的用户 Query 内容。

与数据生成器不同，`QueryGenerator` 是一个轻量的接口，只有一个方法：

```java
List<String> generate();
```

调用后返回一批生成好的 Query 字符串列表。

---

## 接口定义

```java
public interface QueryGenerator {
    List<String> generate();
}
```

---

## 内置实现

### 1. MockQueryGenerator（基于 Mock 规则生成）

通过在模板 Query 中嵌入 **Mock 规则表达式**，每次调用时随机填充生成新的 Query。

适合**数据规模大、需要快速批量生成、内容有一定随机性**的场景，例如生成大量带日期、城市、价格的搜索请求。

#### 工作原理

1. 用户实现 `prepareTemplateQuery()` 方法，返回一个包含 Mock 规则占位符的模板字符串
2. `MockQueryGenerator` 会解析占位符，调用 Mock 引擎填充随机数据
3. 按 `genCount` 生成指定数量的 Query

#### 使用示例

**步骤一：继承 `MockQueryGenerator`，实现模板方法**

```java
public class TravelQueryGenerator extends MockQueryGenerator {
    public TravelQueryGenerator() {
        super(MockerQueryGeneratorConfig.builder()
            .genCount(5)                    // 每次生成5条Query
            .fillEmptyStringOnMockFail(true) // Mock失败时用空字符串填充（而非报错）
            .build());
    }

    @Override
    public String prepareTemplateQuery() {
        // 模板中用 #{规则} 表示需要 Mock 填充的位置
        return "帮我搜索#{city()}附近的#{hotel()}，入住日期#{date()}";
    }
}
```

> 💡 `#{...}` 中的规则是 SpEL 表达式，框架会调用内置的 Mock 方法填充随机数据。常用 Mock 方法见 [数据装饰器文档](./dataloader-wrapper.md)。

**步骤二：在数据生成器中使用**

```java
QueryGenerator queryGenerator = new TravelQueryGenerator();

EvalCaseDataGeneratorConfig config = EvalCaseDataGeneratorConfig.builder()
    .genCount(100)          // 生成100个会话
    .roundCount(3)          // 每个会话3轮对话
    .queryGenerator(queryGenerator)
    .build();

EvalCaseDataGenerator generator = new EvalCaseDataGenerator(config);
```

#### 配置说明（`MockerQueryGeneratorConfig`）

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `genCount` | int | 1 | 每次生成 Query 的数量 |
| `fillEmptyStringOnMockFail` | boolean | false | Mock 规则执行失败时，是否用空字符串填充（false 则抛异常） |

---

### 2. PromptBasedQueryGenerator（基于大模型生成）

通过调用**大型语言模型（LLM）**来生成 Query，适合需要生成**语义丰富、贴近真实用户**的 Query 的场景。

#### 工作原理

1. 将 `sysPrompt`（系统提示词）+ `langStyle`（语言风格）+ `genCount`（数量要求）+ `userPrompt`（用户需求描述）拼接成完整 Prompt
2. 调用配置的 `LLMService` 发起请求
3. 按行切分 LLM 返回的文本，每行一条 Query

#### 使用示例

```java
// 配置大模型服务
LLMService llmService = LLMServiceFactory.create(
    LLMServiceConfig.builder()
        .apiKey("your-api-key")
        .model("gpt-4o")
        .build()
);

// 构建基于Prompt的Query生成器
PromptBasedQueryGeneratorConfig config = PromptBasedQueryGeneratorConfig.builder()
    .llmService(llmService)
    .genCount(10)           // 一次生成10条Query
    .userPrompt("请生成10条关于酒店搜索的用户查询语句，要求包含城市、价格、日期等信息")
    .langStyle("口语化，像真实用户发送的搜索请求")
    // sysPrompt 有默认值，通常不需要设置
    .build();

QueryGenerator queryGenerator = new PromptBasedQueryGenerator(config);
```

#### 配置说明（`PromptBasedQueryGeneratorConfig`）

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `llmService` | `LLMService` | 无（**必填**） | 调用的大模型服务实例 |
| `genCount` | int | 1 | 要生成的 Query 数量 |
| `userPrompt` | String | 无 | 用户需求描述（自定义部分） |
| `sysPrompt` | String | 内置默认提示词 | 系统提示词，通常无需修改 |
| `langStyle` | String | `"逻辑正确,语气自然"` | 语言风格描述 |

**默认系统提示词包含以下要求：**
- 每条 Query 不少于 8 个汉字，不超过 20 个汉字
- 覆盖信息型、导航型、交易型、本地型、疑问型五种搜索意图
- 不得出现符号、emoji
- 输出纯文本，一行一条，不加序号

---

## 自定义 QueryGenerator

如果内置实现不满足需求，直接实现 `QueryGenerator` 接口即可：

```java
public class MyQueryGenerator implements QueryGenerator {
    private final List<String> queryPool;

    public MyQueryGenerator(List<String> queryPool) {
        this.queryPool = queryPool;
    }

    @Override
    public List<String> generate() {
        // 从预定义的Query池中随机抽取一条
        Random random = new Random();
        String query = queryPool.get(random.nextInt(queryPool.size()));
        return Collections.singletonList(query);
    }
}
```

**从文件或数据库中加载 Query 的示例：**

```java
public class FileBasedQueryGenerator implements QueryGenerator {
    private final List<String> queries;

    public FileBasedQueryGenerator(String filePath) throws IOException {
        this.queries = Files.readAllLines(Paths.get(filePath));
    }

    @Override
    public List<String> generate() {
        // 每次随机返回一条
        return Collections.singletonList(
            queries.get(new Random().nextInt(queries.size()))
        );
    }
}
```

---

## 使用场景对比

| 生成器 | 适用场景 | 特点 |
|---|---|---|
| `MockQueryGenerator` | 需要大量规则化随机 Query | 速度快，不依赖外部服务，适合批量生成 |
| `PromptBasedQueryGenerator` | 需要语义自然、贴近真实用户的 Query | 质量高，但需要调用 LLM，耗时较长 |
| 自定义实现 | 从现有数据集中采样，或有特殊生成逻辑 | 灵活，完全自定义 |

---

## 完整使用示例

下面展示一个使用 `PromptBasedQueryGenerator` + `EvalCaseDataGenerator` 生成评测数据集的完整示例：

```java
// 1. 配置大模型服务
LLMService llmService = LLMServiceFactory.create(
    LLMServiceConfig.builder()
        .apiKey("your-api-key")
        .model("gpt-4o-mini")
        .build()
);

// 2. 创建Query生成器
QueryGenerator queryGenerator = new PromptBasedQueryGenerator(
    PromptBasedQueryGeneratorConfig.builder()
        .llmService(llmService)
        .genCount(1)
        .userPrompt("生成一条关于机票预订的用户查询语句")
        .langStyle("自然口语，包含出发地、目的地和日期")
        .build()
);

// 3. 配置评测数据生成器
EvalCaseDataGeneratorConfig genConfig = EvalCaseDataGeneratorConfig.builder()
    .genCount(50)           // 生成50个会话
    .roundCount(3)          // 每个会话最多3轮
    .randomRound(true)      // 轮次随机（1~3轮）
    .queryGenerator(queryGenerator)
    .threadNum(5)           // 5线程并发生成
    .enableOutputFile(true) // 开启结果导出
    .outputFileName("travel_eval_dataset")
    .build();

// 4. 创建数据生成节点并加入工作流
EvalCaseDataGenerator generator = new EvalCaseDataGenerator(genConfig);

Workflow workflow = Workflow.builder()
    .addNode(generator)
    // ...后续评测节点
    .build();

