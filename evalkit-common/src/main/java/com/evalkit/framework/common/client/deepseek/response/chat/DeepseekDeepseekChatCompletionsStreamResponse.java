package com.evalkit.framework.common.client.deepseek.response.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DeepseekDeepseekChatCompletionsStreamResponse extends DeepseekChatCompletionsResponse {
    private List<SSEData> data;
    private String rawData;

    public DeepseekDeepseekChatCompletionsStreamResponse(List<SSEData> data) {
        this.data = data;
    }

    public DeepseekDeepseekChatCompletionsStreamResponse(String rawData) {
        this.rawData = rawData;
    }

    public DeepseekDeepseekChatCompletionsStreamResponse(List<SSEData> data, String rawData) {
        this.data = data;
        this.rawData = rawData;
    }

    @Override
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        data.forEach(sseData -> {
            String curContent = sseData.getChoices().stream().findFirst().map(choice -> choice.getDelta().getContent()).orElse(null);
            sb.append(curContent);
        });
        return sb.toString();
    }
}
