---
layout: default
title: 接口结果装饰器（ApiCompletionWrapper）
parent: 用户指南
nav_order: 9
has_toc: true
---

# 接口结果装饰器（ApiCompletionWrapper）

接口结果装饰器在**接口调用完成后、评估器执行前**运行，用于对 `ApiCompletionResult` 进行转化、清洗或补充，将接口原始输出变换为评估器所需的格式。


## 体系结构

```
ApiCompletionWrapper（抽象基类）
└── LLMBasedApiCompletionWrapper（抽象）  使用大模型转化接口输出
```


## ApiCompletionWrapper（基类）

最通用的装饰器基类，实现 `wrapper(DataItem)` 方法即可。

### 配置项

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `threadNum` | 并发处理线程数 | 否 | 1 |

### 生命周期钩子

| 方法 | 说明 |
|------|------|
| `beforeWrapper(DataItem)` | 装饰前钩子 |
| `wrapper(DataItem)` | **核心方法**，实现对 `ApiCompletionResult` 的转化逻辑 |
| `afterWrapper(DataItem)` | 装饰后钩子 |
| `onWrapperError(DataItem, Throwable)` | 装饰异常时的处理钩子 |

> 单条数据装饰失败不会中断整体流程，框架会记录失败状态并继续处理其他数据。

### 示例：提取并规范化回答字段

```java
ApiCompletionWrapper wrapper = new ApiCompletionWrapper() {
    @Override
    protected void wrapper(DataItem dataItem) {
        ApiCompletionResult result = dataItem.getApiCompletionResult();
        // 从接口原始返回中提取 content 字段，写入 response 字段供评估器使用
        String rawContent = result.get("data.content");
        result.set("response", rawContent != null ? rawContent.trim() : "");
    }
};
```

### 读写 ApiCompletionResult

在 `wrapper()` 方法中，通过 `dataItem.getApiCompletionResult()` 获取接口调用结果，可以读取已有字段，也可以写入新字段：

```java
@Override
protected void wrapper(DataItem dataItem) {
    ApiCompletionResult result = dataItem.getApiCompletionResult();

    // 读取接口返回的原始字段
    String rawResponse = result.get("response", "");

    // 写入/覆盖字段，供后续评估器使用
    result.set("normalized_answer", rawResponse.toLowerCase().trim());
    result.set("has_answer", !rawResponse.isEmpty());
}
```


## LLMBasedApiCompletionWrapper

使用大模型对接口返回结果进行二次转化，适合需要**语义理解或格式转换**的场景，例如：把流式拼接的 Markdown 转换为纯文本、从多段话中提取关键结论等。

### 配置项

包含 `ApiCompletionWrapper` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 |
|--------|------|------|
| `llmService` | 大模型服务 | 是 |

### 需要实现的方法

| 方法 | 说明 |
|------|------|
| `preparePrompt(DataItem)` | 构造发送给大模型的提示词（可同时读取输入数据和接口结果） |
| `applyLLMOutput(ApiCompletionResult, String)` | 将大模型返回的内容写回 `ApiCompletionResult` |

### 示例：用大模型提取接口回答中的关键结论

```java
LLMBasedApiCompletionWrapper wrapper = new LLMBasedApiCompletionWrapper(
        LLMBasedApiCompletionConfig.builder()
                .llmService(myLLMService)
                .threadNum(4)
                .build()
) {
    @Override
    public String preparePrompt(DataItem dataItem) {
        // 同时获取用户问题和接口的原始回答
        String query = dataItem.getInputData().get("query");
        String rawAnswer = dataItem.getApiCompletionResult().get("raw_response", "");
        return "以下是用户问题和AI的回答，请提取回答中的核心结论，用一句话表达：\n"
                + "问题：" + query + "\n"
                + "回答：" + rawAnswer;
    }

    @Override
    public void applyLLMOutput(ApiCompletionResult result, String llmOutput) {
        // 将大模型提取出的结论写入新字段，供 Scorer 直接使用
        result.set("conclusion", llmOutput.trim());
    }
};
```


## 在工作流中使用

`ApiCompletionWrapper` 是工作流中的一个节点，放在 `ApiCompletion` 之后、`Scorer` 之前：

```java
new WorkflowBuilder()
    .link(
        begin,
        dataLoader,
        apiCompletion,          // 1. 调用业务接口
        apiCompletionWrapper,   // 2. 对接口结果进行装饰转化
        scorer,                 // 3. 评估装饰后的结果
        reporter,
        end
    )
    .build()
    .execute();
```


## 注意事项

1. **失败不中断**：单条数据装饰失败时，框架会捕获异常并继续，该条数据保持原始 `ApiCompletionResult` 不变。
2. **就地修改**：`wrapper()` 直接操作 `ApiCompletionResult` 对象，修改会自动反映到后续节点，无需返回值。
3. **可选节点**：如果接口返回格式已符合评估要求，可以不使用此节点，直接将 `ApiCompletion` 连接到 `Scorer`。

