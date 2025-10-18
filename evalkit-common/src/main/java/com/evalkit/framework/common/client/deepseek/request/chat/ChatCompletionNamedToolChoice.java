package com.evalkit.framework.common.client.deepseek.request.chat;

import lombok.Data;

@Data
public class ChatCompletionNamedToolChoice {
    private String type;
    private Function function;

    @Data
    public static class Function {
        private String name;
    }
}
