---
layout: default
title: 大模型服务（LLMService）
parent: 用户指南
nav_order: 2
has_toc: true
---

# 大模型服务（LLMService）

`LLMService` 是框架中调用大模型的统一接口，凡是需要 LLM 能力的节点（`PromptBasedScorer`、`AttributeCounter`、`PromptBasedQueryGenerator` 等）都依赖它。


## 接口定义

```java
public interface LLMService {
    /** 发送 prompt，返回模型回复 */
    String chat(String prompt);

    /** 获取当前使用的模型名称 */
    String getModel();
}
```


## 内置实现

框架通过 `LLMServiceFactory` 工厂创建具体实现，当前支持以下主流大模型：

| 模型类型 | 工厂方法示例 |
|----------|-------------|
| OpenAI / 兼容 OpenAI 的接口 | `LLMServiceFactory.createOpenAIService(...)` |
| DeepSeek | `LLMServiceFactory.createDeepSeekService(...)` |
| 自定义（任意 HTTP） | 实现 `LLMService` 接口 |


## 配置参数（LLMServiceConfig）

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `model` | 模型名称（如 `gpt-4o`、`deepseek-chat`） | 无 |
| `maxTokens` | 最大输出 token 数 | 4068 |
| `temperature` | 随机性（0 越确定，2 越随机） | 0.3 |
| `topP` | 核采样范围（0~1） | 0.95 |
| `frequencyPenalty` | 重复惩罚（-2~2） | 0.0 |
| `presencePenalty` | 存在惩罚（-2~2） | 0.0 |
| `openRetry` | 是否开启失败自动重试 | true |
| `retryInterval` | 重试间隔 | 10 |
| `retryTimeUnit` | 重试间隔时间单位 | 秒 |
| `retryTimes` | 最大重试次数 | 6 |
| `inPrice` | 输入 Token 价格（每百万 Token） | 0.0 |
| `outPrice` | 输出 Token 价格（每百万 Token） | 0.0 |


## 使用示例

### 1. 创建 OpenAI 兼容的 LLM 服务

```java
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.service.llm.LLMServiceFactory;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;

LLMService llmService = LLMServiceFactory.createOpenAIService(
    LLMServiceConfig.builder()
        .model("gpt-4o")
        .maxTokens(2048)
        .temperature(0.3)
        .openRetry(true)
        .retryTimes(3)
        .inPrice(5.0)    // 每百万 Token 输入 5 元
        .outPrice(15.0)  // 每百万 Token 输出 15 元
        .build(),
    "https://api.openai.com",   // API 地址
    "sk-xxxxxxxxxxxx"           // API Key
);
```

### 2. 创建 DeepSeek 服务

```java
LLMService llmService = LLMServiceFactory.createDeepSeekService(
    LLMServiceConfig.builder()
        .model("deepseek-chat")
        .temperature(0.1)
        .build(),
    "sk-xxxxxxxxxxxx"   // DeepSeek API Key
);
```

### 3. 自定义实现（适配私有化部署模型）

如果你的模型不在内置支持列表中，直接实现 `LLMService` 接口：

```java
public class MyPrivateLLMService implements LLMService {

    private final String apiUrl;
    private final String apiKey;

    public MyPrivateLLMService(String apiUrl, String apiKey) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String chat(String prompt) {
        // 调用你的私有模型 HTTP 接口
        // 返回模型的文本回复
        return HttpUtils.post(apiUrl, apiKey, prompt);
    }

    @Override
    public String getModel() {
        return "my-private-model-v1";
    }
}
```


## Token 计费统计

当你在配置中填写了 `inPrice` / `outPrice`，框架会在评测结束后通过 `BasicCounter` 自动统计 Token 消耗和费用，输出到统计结果中：

```json
{
  "llmTokenCounts": [
    {
      "model": "deepseek-chat",
      "inToken": 12000,
      "outToken": 3500,
      "totalToken": 15500,
      "inTokenPrice": 0.006,
      "outTokenPrice": 0.0105,
      "totalTokenPrice": 0.0165
    }
  ]
}
```


## 注意事项

1. **线程安全**：所有内置实现均线程安全，可在多线程评估器中共享同一个 `LLMService` 实例。
2. **重试策略**：`openRetry=true` 时，LLM 调用失败会按 `retryInterval` 间隔重试 `retryTimes` 次，适合处理网络抖动。
3. **temperature 建议**：用于打分、判断类任务时建议设为 `0.1~0.3`，追求确定性输出；用于生成类任务时可适当提高到 `0.7`。

