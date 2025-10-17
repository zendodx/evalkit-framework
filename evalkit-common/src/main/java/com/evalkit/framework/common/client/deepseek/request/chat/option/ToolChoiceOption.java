package com.evalkit.framework.common.client.deepseek.request.chat.option;

public class ToolChoiceOption {
    // 模型不会调用任何 tool，而是生成一条消息
    public static final String NONE = "none";
    // 模型可以选择生成一条消息或调用一个或多个 tool
    public static final String AUTO = "auto";
    // 模型必须调用一个或多个 tool
    public static final String REQUIRED = "required";
}
