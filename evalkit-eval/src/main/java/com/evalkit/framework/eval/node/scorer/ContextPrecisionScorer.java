package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * RAG 上下文精度评估器（Context Precision Scorer）。
 *
 * <p>评测检索系统返回的上下文（Context）中，有多少内容是真正与回答用户问题相关的，
 * 即衡量检索结果的「精准度」——减少噪声文档对最终答案质量的负面影响。
 *
 * <p><b>评测逻辑</b>：
 * <ul>
 *   <li>将检索上下文按段落/块拆分</li>
 *   <li>判断每个块是否对回答用户问题有实质性贡献</li>
 *   <li>分数 = 有用块数 / 总块数（考虑排名加权：排名靠前的有用块权重更高）</li>
 * </ul>
 *
 * <p><b>与 ContextRecall 的区别</b>：
 * <ul>
 *   <li>ContextRecall 关注「有没有召回所需信息」（覆盖广度）</li>
 *   <li>ContextPrecision 关注「召回的信息有多少是有用的」（干扰少）</li>
 *   <li>两者共同反映检索系统的整体质量</li>
 * </ul>
 *
 * <p><b>使用方式</b>：子类实现三个数据准备方法：
 * <pre>
 * ContextPrecisionScorer scorer = new ContextPrecisionScorer(
 *     PromptBasedScorerConfig.builder()
 *         .metricName("上下文精度")
 *         .llmService(llmService)
 *         .threshold(0.6)
 *         .build()
 * ) {
 *     {@literal @}Override
 *     public String prepareQuery(InputData in, ApiCompletionResult out) {
 *         return in.get("query");
 *     }
 *
 *     {@literal @}Override
 *     public String prepareGroundTruth(InputData in, ApiCompletionResult out) {
 *         return in.get("groundTruth"); // 可选：提供标准答案辅助判断相关性
 *     }
 *
 *     {@literal @}Override
 *     public String prepareContext(InputData in, ApiCompletionResult out) {
 *         return in.get("context"); // 检索到的上下文文档片段
 *     }
 * };
 * </pre>
 *
 * @see FaithfulnessScorer
 * @see ContextRecallScorer
 */
@Slf4j
public abstract class ContextPrecisionScorer extends PromptBasedScorer {

    protected static final String DEFAULT_SYS_PROMPT =
            "【角色】你是「ContextPrecisionScorer」——RAG 系统的上下文精度裁判，专门评估检索返回的上下文（Context）中" +
                    "有多少内容是真正有助于回答用户问题的，衡量检索结果的精准程度（噪声比例）。\n" +
                    "【评测步骤】\n" +
                    "1. 以换行或段落标记为分割，将上下文拆分为若干独立片段（chunk）。\n" +
                    "2. 对每个 chunk，判断其是否对回答用户问题有实质性贡献（参考标准答案辅助判断，若无标准答案则仅凭问题判断）。\n" +
                    "3. 计算精度时采用「加权精度」：排名靠前的有用 chunk 贡献更高权重。\n" +
                    "   计算公式：Precision@k = (前k个chunk中有用chunk的累计比例) 的平均值（仅对有用chunk的位置求均值）\n" +
                    "   若上下文整体相关性高则趋向 1.0，大量噪声文档排在前列则趋向 0.0。\n" +
                    "【注意】\n" +
                    "- 若上下文为空，分数记为 0。\n" +
                    "- 评测重点是检索质量（有多少噪声），不评测模型生成的答案。\n" +
                    "- 「有实质性贡献」指：该 chunk 包含有助于直接回答问题的关键信息，而非泛泛相关的背景知识。\n" +
                    "【输出格式】严格输出以下 JSON，不要添加任何额外内容：\n" +
                    "{\"score\": <0.0~1.0 的浮点数>, \"reason\": \"<逐 chunk 分析：是否有用及原因，及最终精度计算过程>\"}\n" +
                    "打分标准：\n" +
                    "- 1.0：所有检索到的 chunk 都对回答问题有直接贡献，且最相关的排在最前\n" +
                    "- 0.7~0.9：大部分 chunk 有用，少量噪声，且有用 chunk 整体排名靠前\n" +
                    "- 0.4~0.6：约一半 chunk 有用，有用与无用混排\n" +
                    "- 0.1~0.3：仅少数 chunk 有用，大量噪声文档被检索回来\n" +
                    "- 0.0：检索结果与问题几乎无关\n" +
                    "执行完成后请自检：确保输出是合法 JSON，score 为数字。";

    public ContextPrecisionScorer(PromptBasedScorerConfig config) {
        super(config);
        if (StringUtils.isEmpty(config.getSysPrompt())) {
            config.setSysPrompt(DEFAULT_SYS_PROMPT);
        }
        super.scorerType = "contextPrecisionScorer";
    }

    @Override
    public String prepareSysPrompt() {
        return config.getSysPrompt();
    }

    @Override
    public String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult) {
        String query = prepareQuery(inputData, apiCompletionResult);
        String groundTruth = prepareGroundTruth(inputData, apiCompletionResult);
        String context = prepareContext(inputData, apiCompletionResult);
        if (StringUtils.isNotEmpty(groundTruth)) {
            return String.format("用户问题：%s\n\n标准答案（Ground Truth，仅供参考）：%s\n\n检索上下文（按检索排名顺序排列）：\n%s",
                    query, groundTruth, context);
        }
        return String.format("用户问题：%s\n\n检索上下文（按检索排名顺序排列）：\n%s", query, context);
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
     * 准备标准答案（Ground Truth，可选）
     *
     * <p>提供标准答案有助于 LLM 更准确地判断哪些 chunk 真正有用，但非必须。
     * 若无标准答案，可返回空字符串，评测器将仅凭问题本身判断上下文相关性。
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 人工标注的标准答案文本，若不提供则返回空字符串
     */
    public abstract String prepareGroundTruth(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备检索上下文（RAG 检索到的文档片段，按检索排名顺序）
     *
     * <p>建议按检索系统的排名顺序排列，上下文精度的计算考虑排名加权。
     * 多个 chunk 可以用 {@code \n\n} 分隔以便 LLM 识别段落边界。
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 按排名排列的检索上下文文本
     */
    public abstract String prepareContext(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(StringUtils.isEmpty(jsonBlock) ? reply : jsonBlock, LLMResult.class);
    }
}

