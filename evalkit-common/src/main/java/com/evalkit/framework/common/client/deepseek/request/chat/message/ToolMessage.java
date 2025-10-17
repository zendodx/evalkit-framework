package com.evalkit.framework.common.client.deepseek.request.chat.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ToolMessage extends Message {
    private String role = "tool";
    private String content;
    @JsonProperty("toolCallId")
    private String toolCallId;
}
