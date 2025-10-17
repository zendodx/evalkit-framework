package com.evalkit.framework.common.client.deepseek.request.chat.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SystemMessage extends Message {
    private String name;
    private String role = "system";
    private String content;
}
