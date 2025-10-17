package com.evalkit.framework.common.client.deepseek.request.chat;

import lombok.Data;

@Data
public class ResponseFormat {
    private String type;

    public ResponseFormat(String type) {
        this.type = type;
    }
}
