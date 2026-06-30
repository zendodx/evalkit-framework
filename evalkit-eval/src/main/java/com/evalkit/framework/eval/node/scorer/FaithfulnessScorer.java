package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * RAG 忠实度评估器（Faithfulness Scorer）。
 *
 * <p>评测模型输出的答案是否忠实于给定的检索上下文（Context），即答案中的每一个关键声明
 * 都能在上下文中找到依据，不包含凭空捏造或与上下文相悖的信息。
 *
 * <p><b>评测逻辑</b>：
 * <ul>
 *   <li>1.0 分：答案的全部关键声明均可从上下文中找到直接支撑依据，无任何捏造</li>
 *   <li>0.7~0.9 分：答案整体忠实，但存在少量推断性延伸，超出上下文范围但未与之矛盾</li>
 *   <li>0.4~0.6 分：答案部分忠实，有明显声明无法在上下文中找到依据</li>
 *   <li>0.1~0.3 分：答案大部分内容与上下文无关或存在严重推断</li>
 *   <li>0.0 分：答案与上下文完全无关，或存在明确与上下文矛盾的声明</li>
 * </ul>
 *
 * <p><b>使用方式</b>：子类实现三个数据准备方法：
 * <pre>
 * FaithfulnessScorer scorer = new FaithfulnessScorer(
 *     PromptBasedScorerConfig.builder()
 *         .metricName("忠实度")
 *         .llmService(llmService)
 *         .threshold(0.7)
 *         .build()
 * ) {
 *     {@literal @}Override
 *     public String prepareQuery(InputData in, ApiCompletionResult out) {
 *         return in.get("query");
 *     }
 *
 *     {@literal @}Override
 *     public String prepareContext(InputData in, ApiCompletionResult out) {
 *         return in.get("context"); // 检索到的上下文文档片段
 *     }
 *
 *     {@literal @}Override
 *     public String prepareAnswer(InputData in, ApiCompletionResult out) {
 *         return out.get("response");
 *     }
 * };
 * </pre>
 *
 * @see ContextRecallScorer
 * @see ContextPrecisionScorer
 */
@Slf4j
public abstract class FaithfulnessScorer extends PromptBasedScorer {

    protected static final String DEFAULT_SYS_PROMPT =
            "【角色】你是「FaithfulnessScorer」——RAG 系统的忠实度裁判，专门评估模型答案是否忠实于给定的检索上下文（Context），" +
            "即答案中的每一个关键声明都能在上下文中找到事实依据，不含捏造或与上下文矛盾的内容。\n" +
            "【评测要点】\n" +
            "1. 逐条分析答案中的关键声明（claim）。\n" +
            "2. 判断每条声明是否在上下文中有直接支撑（verbatim 或语义等价）。\n" +
            "3. 区分以下情况：\n" +
            "   - 「完全支撑」：声明内容直接来自上下文\n" +
            "   - 「推断延伸」：声明超出上下文但未与之矛盾（扣分但不为 0）\n" +
            "   - 「幻觉/矛盾」：声明与上下文内容相悖或无中生有（重扣）\n" +
            "【忽略以下因素】语言流畅度、格式美观、答案长度、是否完整回答问题（完整性由 ContextRecall 评测）。\n" +
            "【输出格式】严格输出以下 JSON，不要添加任何额外内容：\n" +
            "{\"score\": <0.0~1.0 的浮点数>, \"reason\": \"<逐条声明分析及最终判定依据>\"}\n" +
            "打分标准：\n" +
            "- 1.0：全部关键声明均有上下文直接支撑，无任何捏造\n" +
            "- 0.7~0.9：整体忠实，存在少量推断延伸但未矛盾\n" +
            "- 0.4~0.6：部分声明有依据，有明显声明无法在上下文找到支撑\n" +
            "- 0.1~0.3：大部分内容无上下文依据或存在严重推断\n" +
            "- 0.0：答案与上下文完全无关，或存在明确矛盾的声明\n" +
            "执行完成后请自检：确保输出是合法 JSON，score 为数字。";

    public FaithfulnessScorer(PromptBasedScorerConfig config) {
        super(config);
        if (StringUtils.isEmpty(config.getSysPrompt())) {
            config.setSysPrompt(DEFAULT_SYS_PROMPT);
        }
        super.scorerType = "faithfulnessScorer";
    }

    @Override
    public String prepareSysPrompt() {
        return config.getSysPrompt();
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        String query = prepareQuery(inputData, apiCompletionResult);
        String context = prepareContext(inputData, apiCompletionResult);
        String answer = prepareAnswer(inputData, apiCompletionResult);
        return String.format("用户问题：%s\n\n检索上下文：\n%s\n\n模型答案：%s", query, context, answer);
    }

    /**
     * 准备用户问题
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 用户原始问题文本
     */
    public abstract String prepareQuery(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备检索上下文（RAG 检索到的文档片段）
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 检索到的上下文文本，多段内容建议用换行分隔
     */
    public abstract String prepareContext(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备模型答案（被评测的 RAG 系统输出）
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 模型生成的答案文本
     */
    public abstract String prepareAnswer(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(StringUtils.isEmpty(jsonBlock) ? reply : jsonBlock, LLMResult.class);
    }
}

