package com.evalkit.framework.infra.service.llm;


import com.evalkit.framework.common.client.deepseek.DeepseekClient;
import com.evalkit.framework.common.client.deepseek.request.chat.DeepseekChatCompletionsRequest;
import com.evalkit.framework.common.client.deepseek.request.chat.ResponseFormat;
import com.evalkit.framework.common.client.deepseek.request.chat.message.Message;
import com.evalkit.framework.common.client.deepseek.request.chat.message.UserMessage;
import com.evalkit.framework.common.client.deepseek.request.chat.option.ResponseFormatOption;
import com.evalkit.framework.common.client.deepseek.request.chat.option.ToolChoiceOption;
import com.evalkit.framework.common.client.deepseek.response.chat.DeepseekChatCompletionsResponse;
import com.evalkit.framework.infra.service.llm.config.DeepseekLLMServiceConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Deepseek大模型服务
 */
public class DeepseekLLMService implements LLMService {
    // Deepseek客户端
    private final DeepseekClient client = DeepseekClient.INSTANCE;
    // 接口访问密钥
    private DeepseekLLMServiceConfig config;

    public DeepseekLLMService(DeepseekLLMServiceConfig config) {
        this.config = config;
    }

    @Override
    public String chat(String prompt) {
        client.initClient(config.getApiToken());
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));
        DeepseekChatCompletionsRequest request = DeepseekChatCompletionsRequest.builder()
                .messages(messages)
                .model(config.getModel())
                .frequencyPenalty(0.0)
                .maxTokens((int) config.getMaxTokens())
                .presencePenalty(0)
                .responseFormat(new ResponseFormat(ResponseFormatOption.TEXT))
                .stop(null)
                .stream(false)
                .streamOptions(null)
                .temperature(1.0)
                .topP(1.0)
                .tools(null)
                .toolChoice(ToolChoiceOption.NONE)
                .logprobs(false)
                .topLogprobs(null)
                .build();
        DeepseekChatCompletionsResponse response;
        try {
            response = client.chatCompletions(request);
            assert response != null : "DeepseekLLMService chat failed, response is null!";
            return response.getContent();
        } catch (Exception e) {
            throw new RuntimeException("Deepseek LLM service chat error:" + e.getMessage(), e);
        }
    }

    @Override
    public String getModel() {
        return "deepseek-chat";
    }
}
