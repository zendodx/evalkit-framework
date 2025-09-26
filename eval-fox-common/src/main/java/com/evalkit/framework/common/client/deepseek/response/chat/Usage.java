package com.evalkit.framework.common.client.deepseek.response.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Usage {
    @JsonProperty("completion_tokens")
    private long completionTokens;
    @JsonProperty("prompt_tokens")
    private long promptTokens;
    @JsonProperty("prompt_cache_hit_tokens")
    private long promptCacheHitTokens;
    @JsonProperty("prompt_cache_miss_tokens")
    private long promptCacheMissTokens;
    @JsonProperty("total_tokens")
    private long totalTokens;
    @JsonProperty("completion_tokens_details")
    private List<CompletionTokensDetail> completionTokensDetails;
    @JsonProperty("prompt_tokens_details")
    private PromptTokensDetails promptTokensDetails;

    @Data
    static class CompletionTokensDetail {
        @JsonProperty("reasoning_tokens")
        private long reasoningTokens;
    }

    @Data
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        private Long cachedTokens;
    }
}
