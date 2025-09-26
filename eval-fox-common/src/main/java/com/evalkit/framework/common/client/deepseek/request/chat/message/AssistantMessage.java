package com.evalkit.framework.common.client.deepseek.request.chat.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class AssistantMessage extends Message {
    private String name;
    private String role = "assistant";
    private String content;
    private boolean prefix;
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    public AssistantMessage(String content) {
        this.content = content;
    }

    public AssistantMessage(String name, String content) {
        this.name = name;
        this.content = content;
    }

    public AssistantMessage(String name, String content, boolean prefix, String reasoningContent) {
        this.name = name;
        this.content = content;
        this.prefix = prefix;
        this.reasoningContent = reasoningContent;
    }
}
