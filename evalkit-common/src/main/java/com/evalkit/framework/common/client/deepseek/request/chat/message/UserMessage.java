package com.evalkit.framework.common.client.deepseek.request.chat.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserMessage extends Message {
    private String name;
    private String role = "user";
    private String content;

    public UserMessage(String content) {
        this.content = content;
    }

    public UserMessage(String name, String content) {
        this.name = name;
        this.content = content;
    }
}
