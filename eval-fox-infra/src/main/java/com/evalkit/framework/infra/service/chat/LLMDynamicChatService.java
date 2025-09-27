package com.evalkit.framework.infra.service.chat;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 动态对话服务,结合上文信息生成当前轮的Query
 */
@Data
public class LLMDynamicChatService implements DynamicChatService {
    // 大模型服务
    private LLMService llmService;
    // 人设,提供一个默认人设,也可以自定义设置
    private String sysPrompt;
    // 用户数据
    private String userPrompt;

    public LLMDynamicChatService(LLMService llmService) {
        this.llmService = llmService;
        this.sysPrompt = "# 人设 你是一个闲聊机器人,可结合上文信息给出下一次对话的Query # 输出格式 直接输出Query,不输出思考过程 # 输入数据 上文信息:{{input}}";
    }

    public LLMDynamicChatService(LLMService llmService, String sysPrompt) {
        this.llmService = llmService;
        this.sysPrompt = sysPrompt;
    }

    public LLMDynamicChatService(LLMService llmService, String sysPrompt, String userPrompt) {
        this.llmService = llmService;
        this.sysPrompt = sysPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public String generateQuery() {
        if (llmService == null || StringUtils.isEmpty(sysPrompt)) return null;
        String replace = sysPrompt.replace("{{input}}", userPrompt);
        return llmService.chat(replace);
    }
}
