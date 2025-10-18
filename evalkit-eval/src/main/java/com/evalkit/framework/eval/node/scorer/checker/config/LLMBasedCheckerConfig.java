package com.evalkit.framework.eval.node.scorer.checker.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class LLMBasedCheckerConfig extends CheckerConfig {
    /* 检查器的默认sysPrompt */
    @Builder.Default
    private String sysPrompt = "你是一名检查助手,任务是对用户输入数据进行打分,并以标准json结构返回结果,输出之前，你要执行自检查,确保生成的 JSON 严格可解析、无语法错误、无多余或缺失符号。若自检发现任何可能导致解析失败的问题（如多余逗号、引号未闭合、非法字符等），必须立即修正，直到 JSON 能一次性通过 JSON.parse 或等价校验。";
    /* 大模型服务 */
    private LLMService llmService;
    /* 开始轮次 */
    @Builder.Default
    private int beginRound = 1;
    /* 结束轮次 */
    @Builder.Default
    private int endRound = 1;
}
