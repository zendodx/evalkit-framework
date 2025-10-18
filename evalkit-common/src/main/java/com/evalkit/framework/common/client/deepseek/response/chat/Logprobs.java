package com.evalkit.framework.common.client.deepseek.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Logprobs {
    private List<Content> content;

    @Data
    static class Content {
        private String token;
        private Long logprob;
        private long[] bytes;
        @JsonProperty("top_logprobs")
        private List<TopLogProbs> topLogProbs;

        @Data
        static class TopLogProbs {
            private String token;
            private Long logprob;
            private long[] bytes;
        }
    }
}
