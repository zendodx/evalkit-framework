---
layout: default
title: 数据加载器（DataLoader）
parent: 用户指南
nav_order: 3
---

# 数据加载器（DataLoader）

数据加载器负责将评测数据加载到工作流上下文中，是整个评测流程的第一个数据节点。

---

## 体系结构

```
DataLoader（抽象基类）
├── ExcelDataLoader     从 Excel 文件加载
├── CsvDataLoader       从 CSV 文件加载
├── JsonFileDataLoader  从 JSON 文件加载
├── JsonTextDataLoader  从 JSON 字符串加载（抽象）
├── ApiDataLoader       调用 HTTP API 加载（抽象）
├── JdbcDataLoader      从数据库加载（抽象）
└── MultiDataLoader     聚合多个加载器
```

---

## DataLoader（基类）

所有加载器的基类，可以直接继承并实现 `prepareDataList()` 方法来提供任意来源的数据。

### 配置项

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `offset` | 跳过前 N 条数据 | 否 | 0 |
| `limit` | 最多加载多少条，`-1` 表示全部 | 否 | -1 |
| `filters` | 过滤器列表，只加载满足条件的数据 | 否 | 无 |
| `shuffle` | 是否打乱数据顺序 | 否 | false |
| `openInjectData` | 是否开启数据注入（把已有的接口结果/评测结果直接注入上下文，跳过重复执行） | 否 | false |
| `injectDataIndex` | 注入数据索引 | 否 | true |
| `injectInputData` | 注入输入数据 | 否 | true |
| `injectApiCompletionResult` | 注入接口调用结果 | 否 | true |
| `injectEvalResult` | 注入评测结果 | 否 | true |
| `injectExtra` | 注入额外数据 | 否 | true |

### 自定义数据加载器

```java
DataLoader dataLoader = new DataLoader(
        DataLoaderConfig.builder()
                .limit(100)     // 最多加载 100 条
                .shuffle(true)  // 随机打乱
                .build()
) {
    @Override
    public List<InputData> prepareDataList() throws Exception {
        // 从任意来源构建 InputData 列表
        List<InputData> list = new ArrayList<>();
        list.add(new InputData(MapUtils.of("query", "帮我查今天的天气")));
        list.add(new InputData(MapUtils.of("query", "明天去北京需要带伞吗")));
        return list;
    }
};
```

### 数据注入功能

**使用场景**：接口已经调用过一次，想复用上次的接口结果重新跑评估，不需要再调接口。

```java
// 假设 JSON 文件里已经包含了 inputData + apiCompletionResult
DataLoader dataLoader = new JsonFileDataLoader(
        JsonFileDataLoaderConfig.builder()
                .filePath("eval_result.json")
                .openInjectData(true)           // 开启注入
                .injectApiCompletionResult(true) // 注入接口调用结果
                .injectEvalResult(false)         // 不注入历史评测结果（重新评）
                .build()
);
```

---

## ExcelDataLoader

从 Excel 文件加载数据，文件第一行作为字段名（Header），后续每行是一条测试用例。

### 配置项

包含 `DataLoader` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `filePath` | 文件路径，支持：绝对路径 / 类路径（classpath） / 远程 HTTP 链接 | 是 | 无 |
| `sheetIndex` | 读取哪个 Sheet（从 0 开始） | 否 | 0 |

### 示例

```java
// Excel 文件内容示例：
// | query              | groundTruth        |
// | 成都有什么好吃的？ | 火锅、串串、麻辣烫 |

ExcelDataLoader loader = new ExcelDataLoader(
        ExcelDataLoaderConfig.builder()
                .filePath("/data/testcases.xlsx")  // 绝对路径
                .sheetIndex(0)
                .limit(200)
                .build()
);

// 使用 classpath 路径（文件在 src/main/resources 下）
ExcelDataLoader loader2 = new ExcelDataLoader(
        ExcelDataLoaderConfig.builder()
                .filePath("testcases.xlsx")
                .sheetIndex(1)
                .build()
);

// 使用远程链接
ExcelDataLoader loader3 = new ExcelDataLoader(
        ExcelDataLoaderConfig.builder()
                .filePath("https://example.com/testcases.xlsx")
                .build()
);
```

---

## CsvDataLoader

从 CSV 文件加载数据。

### 配置项

包含 `DataLoader` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `filePath` | 文件路径，支持绝对路径 / 类路径 / 远程链接 | 是 | 无 |
| `delimiter` | 分隔符 | 否 | `,` |
| `hasHeader` | 第一行是否为字段名 | 否 | true |

### 示例

```java
// CSV 文件内容示例：
// query,groundTruth
// "去成都玩3天，有什么推荐？","宽窄巷子、大熊猫基地、都江堰"

CsvDataLoader loader = new CsvDataLoader(
        CsvDataLoaderConfig.builder()
                .filePath("testcases.csv")
                .delimiter(",")
                .hasHeader(true)
                .build()
);
```

---

## JsonFileDataLoader

从 JSON 文件加载数据，支持通过 JSONPath 指定数据所在的节点路径。

### 配置项

包含 `DataLoader` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `filePath` | 文件路径，支持绝对路径 / 类路径 / 远程链接 | 是 | 无 |
| `jsonPath` | JSONPath 表达式，指向数据数组 | 否 | `$`（根节点） |

### 示例

```java
// JSON 文件格式1：根节点是数组
// [{"query":"...","groundTruth":"..."},{"query":"..."}]
JsonFileDataLoader loader1 = new JsonFileDataLoader(
        JsonFileDataLoaderConfig.builder()
                .filePath("testcases.json")
                .jsonPath("$")
                .build()
);

// JSON 文件格式2：数组嵌套在某个字段下
// {"total":10,"data":[{"query":"..."},{"query":"..."}]}
JsonFileDataLoader loader2 = new JsonFileDataLoader(
        JsonFileDataLoaderConfig.builder()
                .filePath("testcases.json")
                .jsonPath("$.data")     // 从 data 字段取数组
                .build()
);
```

---

## JsonTextDataLoader

从 JSON 字符串（而非文件）加载数据，适合动态生成测试数据的场景。

> **注意**：`JsonTextDataLoader` 是抽象类，需继承并实现 `prepareJsonpath()` 和 `prepareJson()` 方法。

### 示例

```java
JsonTextDataLoader loader = new JsonTextDataLoader() {
    @Override
    public String prepareJsonpath() {
        return "$.cases";  // 从 cases 字段取数组
    }

    @Override
    public String prepareJson() {
        // 可以在这里动态拼接 JSON，或调用接口获取
        return "{\"cases\":[{\"query\":\"今天天气怎么样\"},{\"query\":\"明天会下雨吗\"}]}";
    }
};
```

---

## ApiDataLoader

通过调用 HTTP API 接口来获取评测数据，适合从测试平台或数据管理系统动态拉取用例。

> **注意**：`ApiDataLoader` 是抽象类，需继承并实现相关方法。

### 配置项

包含 `DataLoader` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `host` | 请求域名（含协议，如 `https://api.example.com`） | 是 | 无 |
| `api` | 接口路径（如 `/v1/testcases`） | 是 | 无 |
| `method` | HTTP 方法（`get` / `post`） | 是 | 无 |
| `timeout` | 超时时间 | 否 | 120 |
| `timeUnit` | 超时时间单位 | 否 | 秒 |

### 示例

```java
ApiDataLoader loader = new ApiDataLoader(
        ApiDataLoaderConfig.builder()
                .host("https://api.example.com")
                .api("/v1/testcases")
                .method("post")
                .timeout(30)
                .timeUnit(TimeUnit.SECONDS)
                .build()
) {
    @Override
    public Map<String, Object> prepareBody() {
        // POST 请求体
        return MapUtils.of("projectId", "my-project", "pageSize", 100);
    }

    @Override
    public Map<String, String[]> prepareParam() {
        // URL 查询参数
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> prepareHeader() {
        // 请求头（如鉴权 Token）
        return MapUtils.of("Authorization", "Bearer my-token");
    }

    @Override
    public String prepareJsonpath() {
        // 从接口返回的 JSON 中取数据的路径
        return "$.data.list";
    }
};
```

---

## JdbcDataLoader

从关系型数据库（MySQL、PostgreSQL 等）加载数据。

> **注意**：`JdbcDataLoader` 是抽象类，需继承并实现 `prepareSql()` 方法。

### 配置项

包含 `DataLoader` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `driver` | JDBC 驱动类名 | 是 | 无 |
| `url` | 数据库连接 URL | 是 | 无 |
| `user` | 用户名 | 是 | 无 |
| `password` | 密码 | 是 | 无 |
| `maximumPoolSize` | 连接池最大连接数 | 否 | 10 |
| `minimumIdle` | 最小空闲连接数 | 否 | 2 |
| `connectionTimeout` | 连接超时（毫秒） | 否 | 5000 |

### 示例

```java
// 需要在 pom.xml 中引入对应的 JDBC 驱动依赖
JdbcDataLoader loader = new JdbcDataLoader(
        JdbcDataLoaderConfig.builder()
                .driver("com.mysql.cj.jdbc.Driver")
                .url("jdbc:mysql://127.0.0.1:3306/evalkit?useSSL=false&serverTimezone=Asia/Shanghai")
                .user("root")
                .password("root123")
                .build()
) {
    @Override
    public String prepareSql() {
        // SQL 查询结果的列名会直接作为字段名
        return "SELECT query, ground_truth AS groundTruth FROM test_cases WHERE status = 'active'";
    }
};
```

---

## MultiDataLoader

将多个 `DataLoader` 合并成一个，依次加载并合并结果。

### 示例

```java
DataLoader xlsxLoader = new ExcelDataLoader(
        ExcelDataLoaderConfig.builder().filePath("batch1.xlsx").build()
);
DataLoader csvLoader = new CsvDataLoader(
        CsvDataLoaderConfig.builder().filePath("batch2.csv").build()
);

// 合并两个来源，批量 10 条，最多 200 条
MultiDataLoader multiLoader = new MultiDataLoader(
        ListUtils.of(xlsxLoader, csvLoader),
        10,    // batchSize
        200    // maxCount，-1 表示无限制
);
```

---

## 常见问题

**Q：文件路径怎么填？**

框架按以下顺序查找：
1. 作为文件系统绝对/相对路径（如 `/data/test.xlsx` 或 `./test.xlsx`）
2. 作为 Classpath 路径（如 `testcases.xlsx`，文件放在 `src/main/resources/` 下）
3. 作为远程 HTTP 链接（如 `https://...`）

**Q：如何只评估某个范围的数据？**

使用 `offset` + `limit` 参数：

```java
DataLoaderConfig.builder()
    .offset(100)   // 跳过前 100 条
    .limit(50)     // 取第 101~150 条
    .build()

