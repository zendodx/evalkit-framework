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
 * DeepSeek大模型服务
 */
public class DeepSeekLLMService extends AbstractLLMService {
    /* DeepSeek客户端 */
    private final DeepseekClient client = DeepseekClient.INSTANCE;
    /* DeepSeek大模型服务配置 */
    private final DeepseekLLMServiceConfig config;

    public DeepSeekLLMService(DeepseekLLMServiceConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    public String doChat(String prompt) {
        client.initClient(config.getApiToken());
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(prompt));
        DeepseekChatCompletionsRequest request = DeepseekChatCompletionsRequest.builder()
                .messages(messages)
                .model(config.getModel())
                .frequencyPenalty(config.getFrequencyPenalty())
                .maxTokens((int) config.getMaxTokens())
                .presencePenalty((int) config.getPresencePenalty())
                .responseFormat(new ResponseFormat(ResponseFormatOption.TEXT))
                .stop(null)
                .stream(false)
                .streamOptions(null)
                .temperature(config.getTemperature())
                .topP(config.getTopP())
                .tools(null)
                .toolChoice(ToolChoiceOption.NONE)
                .logprobs(false)
                .topLogprobs(null)
                .build();
        DeepseekChatCompletionsResponse response;
        try {
            response = client.chatCompletions(request);
            assert response != null : "DeepSeekLLMService chat failed, response is null!";
            return response.getContent();
        } catch (Exception e) {
            throw new RuntimeException("DeepSeek LLM service chat error:" + e.getMessage(), e);
        }
    }

    @Override
    public String getModel() {
        return config.getModel();
    }
}
