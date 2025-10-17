package com.evalkit.framework.common.client.deepseek.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DeepseekDeepseekChatCompletionsNoStreamResponse extends DeepseekChatCompletionsResponse {
    private String id;
    private List<Choice> choices;
    private long created;
    private String model;
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    private String object;
    private Usage usage;

    @Data
    static class Choice {
        @JsonProperty("finish_reason")
        private String finishReason;
        private long index;
        private Message message;
        private Logprobs logprobs;

        @Data
        static class Message {
            private String content;
            @JsonProperty("reasoning_content")
            private String reasoningContent;
            @JsonProperty("tool_calls")
            private List<ToolCall> toolCalls;
            private String role;


            @Data
            static class ToolCall {
                private String id;
                private String type;
                private Function function;

                @Data
                static class Function {
                    private String name;
                    private String arguments;
                }
            }
        }
    }

    @Override
    public String getContent() {
        return this.getChoices().stream()
                .findFirst()
                .map(choice -> choice.getMessage().getContent())
                .orElse(null);
    }
}
