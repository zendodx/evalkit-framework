---
layout: default
title: 接口调用器（ApiCompletion）
parent: 用户指南
nav_order: 8
---

# 接口调用器（ApiCompletion）

接口调用器负责对每条测试数据**调用被测的业务接口**，并将返回结果存入 `DataItem` 的 `apiCompletionResult` 字段，供后续评估器使用。

---

## 体系结构

```
ApiCompletion（抽象基类）
├── HttpApiCompletion（抽象）  封装 HTTP 调用逻辑
└── OrderedApiCompletion（抽象）  保证同一会话内的请求按序执行
```

---

## ApiCompletion（基类）

最通用的接口调用器，实现 `invoke(DataItem)` 方法即可。

### 配置项

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `threadNum` | 并发调用线程数 | 否 | 1 |
| `timeout` | 接口调用超时时间 | 否 | 120 |
| `timeUnit` | 超时时间单位 | 否 | 秒 |

### 生命周期钩子

| 方法 | 说明 |
|------|------|
| `beforeInvoke(DataItem)` | 调用前钩子（可修改入参） |
| `invoke(DataItem)` | **核心方法**，实现实际调用逻辑 |
| `afterInvoke(DataItem, ApiCompletionResult)` | 调用后钩子（可修改结果） |
| `onErrorInvoke(DataItem, Throwable)` | 调用异常时的处理钩子 |

> 单条数据调用失败不会中断整体流程，框架会记录失败状态并继续处理其他数据。

### 示例：调用聊天机器人接口

```java
ApiCompletion apiCompletion = new ApiCompletion(
        ApiCompletionConfig.builder()
                .threadNum(10)           // 10 线程并发
                .timeout(30)
                .timeUnit(TimeUnit.SECONDS)
                .build()
) {
    @Override
    protected ApiCompletionResult invoke(DataItem dataItem) throws IOException {
        InputData inputData = dataItem.getInputData();
        String query = inputData.get("query");
        String sessionId = inputData.get("sessionId");

        // 调用你的 AI 业务接口（这里用伪代码示例）
        ChatResponse response = myChatClient.chat(sessionId, query);

        // 把结果封装成 ApiCompletionResult
        ApiCompletionResult result = new ApiCompletionResult();
        result.setResultItem(MapUtils.of(
            "response",  response.getContent(),
            "sessionId", sessionId,
            "traceId",   response.getTraceId()
        ));
        return result;
    }

    // 可选：在调用前打印日志
    @Override
    protected DataItem beforeInvoke(DataItem dataItem) {
        log.info("开始调用，dataIndex={}", dataItem.getDataIndex());
        return dataItem;
    }

    // 可选：调用失败时记录错误信息
    @Override
    protected void onErrorInvoke(DataItem dataItem, Throwable e) {
        log.error("调用失败，dataIndex={}, error={}", dataItem.getDataIndex(), e.getMessage());
    }
};
```

### 获取调用结果

在后续的 `Scorer.eval()` 中，通过以下方式获取接口调用结果：

```java
@Override
public ScorerResult eval(DataItem dataItem) {
    // 获取原始输入数据
    String query = dataItem.getInputData().get("query");

    // 获取接口调用结果（即 ApiCompletion 中 setResultItem 的内容）
    String response = dataItem.getApiCompletionResult().get("response");
    boolean callSuccess = dataItem.getApiCompletionResult().isSuccess();
    long timeCost = dataItem.getApiCompletionResult().getTimeCost();

    // ... 执行评估逻辑
}
```

---

## HttpApiCompletion

对 HTTP 接口调用进行了封装，自动处理请求的组装和发送，你只需要实现以下 4 个方法：

| 方法 | 说明 |
|------|------|
| `prepareBody(InputData)` | 准备 POST 请求体（返回 Map） |
| `prepareParam(InputData)` | 准备 URL 查询参数（返回 Map） |
| `prepareHeader(InputData)` | 准备请求 Header（返回 Map） |
| `buildApiCompletionResult(InputData, HttpApiResponse)` | 从 HTTP 响应构建 `ApiCompletionResult` |

### 配置项

包含 `ApiCompletion` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 |
|--------|------|------|
| `host` | 请求域名（如 `https://api.example.com`） | 是 |
| `api` | 接口路径（如 `/v1/chat`） | 是 |
| `method` | HTTP 方法（`get` / `post` / `put`） | 是 |

### 示例

```java
HttpApiCompletion httpApiCompletion = new HttpApiCompletion(
        HttpApiCompletionConfig.builder()
                .host("https://api.example.com")
                .api("/v1/chat/completions")
                .method("post")
                .timeout(60)
                .timeUnit(TimeUnit.SECONDS)
                .threadNum(5)
                .build()
) {
    @Override
    public Map<String, Object> prepareBody(InputData inputData) {
        // 从 InputData 中取字段，构造请求体
        return MapUtils.of(
            "model",    "gpt-4o",
            "messages", Collections.singletonList(
                MapUtils.of("role", "user", "content", inputData.get("query"))
            ),
            "stream", false
        );
    }

    @Override
    public Map<String, String[]> prepareParam(InputData inputData) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> prepareHeader(InputData inputData) {
        return MapUtils.of(
            "Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"),
            "Content-Type",  "application/json"
        );
    }

    @Override
    public ApiCompletionResult buildApiCompletionResult(InputData inputData, HttpApiResponse response) {
        // 解析 HTTP 响应，提取需要的字段
        String responseBody = response.getBody();
        String content = JsonUtils.readPath(responseBody, "$.choices[0].message.content");

        ApiCompletionResult result = new ApiCompletionResult();
        result.setResultItem(MapUtils.of("response", content));
        return result;
    }
};
```

---

## OrderedApiCompletion

**适用场景**：评测多轮对话时，同一个 `sessionId` 下的多轮 query 必须**按顺序、在同一个线程内**依次执行（这样 AI 服务才能维护正确的会话上下文）。

> `OrderedApiCompletion` 是抽象类，需要额外实现 `prepareOrderKey()` 和 `prepareComparator()` 方法。

| 方法 | 说明 |
|------|------|
| `prepareOrderKey(DataItem)` | 返回分组 key，相同 key 的数据会被分配到同一线程处理 |
| `prepareComparator()` | 定义同一组内数据的执行顺序（按轮次排序） |
| `invoke(DataItem)` | 调用接口的具体实现（与 ApiCompletion 相同） |

### 示例：多轮对话有序评测

```java
OrderedApiCompletion orderedApiCompletion = new OrderedApiCompletion(
        ApiCompletionConfig.builder()
                .threadNum(8)   // 8 个并发组（每个组顺序处理自己的会话）
                .build()
) {
    @Override
    public String prepareOrderKey(DataItem dataItem) {
        // 相同 sessionId 的数据分到同一组、同一线程
        return dataItem.getInputData().get("sessionId");
    }

    @Override
    public Comparator<DataItem> prepareComparator() {
        // 同一会话内，按 round（轮次）从小到大顺序执行
        return Comparator.comparingInt(d ->
            Integer.parseInt(d.getInputData().get("round"))
        );
    }

    @Override
    protected ApiCompletionResult invoke(DataItem dataItem) throws IOException {
        InputData inputData = dataItem.getInputData();
        String sessionId = inputData.get("sessionId");
        String query = inputData.get("query");

        // AI 服务会根据 sessionId 维护上下文，所以相同 sessionId 的请求必须按序发
        ChatResponse resp = aiChatService.chat(sessionId, query);

        ApiCompletionResult result = new ApiCompletionResult();
        result.setResultItem(MapUtils.of(
            "response",  resp.getAnswer(),
            "sessionId", sessionId
        ));
        return result;
    }
};
```

### 有序调用 vs 普通并发调用

| 对比项 | `ApiCompletion` | `OrderedApiCompletion` |
|--------|-----------------|----------------------|
| 适用场景 | 单轮问答，每条数据独立 | 多轮对话，同会话需有序执行 |
| 并发方式 | 所有数据完全并行 | 不同会话并行，同会话串行 |
| 需实现方法 | `invoke()` | `invoke()` + `prepareOrderKey()` + `prepareComparator()` |

---

## 注意事项

1. **失败不中断**：单条数据调用失败时，框架会记录 `success=false` 并继续，不会抛出异常中断整体评测。
2. **timeCost 自动统计**：框架自动记录每次调用的耗时（毫秒），无需手动计算。
3. **线程数建议**：`threadNum` 建议根据被测接口的 QPS 限制和机器核数合理设置，避免打垮被测服务。
4. **空结果处理**：`invoke()` 返回 `null` 时，框架会自动标记该条数据调用失败。

