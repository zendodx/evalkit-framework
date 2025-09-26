package com.evalkit.framework.common.client.deepseek.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class SSEData {
    private String id;
    private List<Choice> choices;
    private Long created;
    private String model;
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;
    private String object;
    private Usage usage;

    @Data
    public static class Choice {
        private Delta delta;
        @JsonProperty("finish_reason")
        private String finishReason;
        private Long index;
        private Logprobs logprobs;

        @Data
        public static class Delta {
            private String content;
            @JsonProperty("reasoning_content")
            private String reasoningContent;
            private String role;
        }
    }
}
