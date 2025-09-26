package com.evalkit.framework.common.client.deepseek.request.chat;

import lombok.Data;

import java.util.List;

@Data
public class Tool {
    private String type;
    private Function function;

    @Data
    public static class Function {
        private String name;
        private String description;
        private List<Object> parameters;
    }
}
