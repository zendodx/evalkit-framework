package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.config.PromptBasedScorerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * RAG 上下文召回率评估器（Context Recall Scorer）。
 *
 * <p>评测检索系统返回的上下文（Context）是否覆盖了回答用户问题所需的全部关键信息，
 * 以标准答案（Ground Truth）为参照基准。召回率越高，表示上下文对标准答案的覆盖越完整。
 *
 * <p><b>评测逻辑</b>：
 * <ul>
 *   <li>将标准答案拆解为若干关键信息点（claim）</li>
 *   <li>逐条判断每个信息点是否能在检索上下文中找到支撑</li>
 *   <li>分数 = 被上下文覆盖的信息点数 / 标准答案信息点总数</li>
 * </ul>
 *
 * <p><b>与 Faithfulness 的区别</b>：
 * <ul>
 *   <li>Faithfulness 评测「答案是否忠实于上下文」（评测生成质量）</li>
 *   <li>ContextRecall 评测「上下文是否覆盖了标准答案所需信息」（评测检索质量）</li>
 * </ul>
 *
 * <p><b>使用方式</b>：子类实现三个数据准备方法：
 * <pre>
 * ContextRecallScorer scorer = new ContextRecallScorer(
 *     PromptBasedScorerConfig.builder()
 *         .metricName("上下文召回率")
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
 *     public String prepareGroundTruth(InputData in, ApiCompletionResult out) {
 *         return in.get("groundTruth"); // 人工标注的标准答案
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
 * @see ContextPrecisionScorer
 */
@Slf4j
public abstract class ContextRecallScorer extends PromptBasedScorer {

    protected static final String DEFAULT_SYS_PROMPT =
            "【角色】你是「ContextRecallScorer」——RAG 系统的上下文召回率裁判，专门评估检索返回的上下文（Context）" +
                    "是否覆盖了回答用户问题所需的全部关键信息（以标准答案 Ground Truth 为基准）。\n" +
                    "【评测步骤】\n" +
                    "1. 将标准答案拆解为若干独立的关键信息点（claim），每个 claim 是一个可独立验证的事实陈述。\n" +
                    "2. 对每个 claim，判断其是否能在检索上下文中找到直接或语义等价的支撑。\n" +
                    "3. 计算召回率 = 被上下文覆盖的 claim 数 / claim 总数，保留两位小数。\n" +
                    "【注意】\n" +
                    "- 只评判上下文的覆盖程度，不评判模型生成的答案质量。\n" +
                    "- 若标准答案为空，分数记为 0。\n" +
                    "- 若上下文为空，分数记为 0。\n" +
                    "【输出格式】严格输出以下 JSON，不要添加任何额外内容：\n" +
                    "{\"score\": <0.0~1.0 的浮点数>, \"reason\": \"<逐条 claim 分析：哪些被覆盖、哪些未被覆盖，及最终召回率计算过程>\"}\n" +
                    "打分标准：\n" +
                    "- 1.0：标准答案中全部关键信息点均可在上下文中找到\n" +
                    "- 0.7~0.9：大部分关键信息点被覆盖，少数遗漏\n" +
                    "- 0.4~0.6：约一半关键信息点被覆盖\n" +
                    "- 0.1~0.3：仅少数关键信息点被覆盖\n" +
                    "- 0.0：上下文中几乎没有与标准答案相关的信息\n" +
                    "执行完成后请自检：确保输出是合法 JSON，score 为数字。";

    public ContextRecallScorer(PromptBasedScorerConfig config) {
        super(config);
        if (StringUtils.isEmpty(config.getSysPrompt())) {
            config.setSysPrompt(DEFAULT_SYS_PROMPT);
        }
        super.scorerType = "contextRecallScorer";
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
        return String.format("用户问题：%s\n\n标准答案（Ground Truth）：%s\n\n检索上下文：\n%s",
                query, groundTruth, context);
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
     * 准备标准答案（Ground Truth）
     *
     * <p>标准答案是评测召回率的参照基准，通常由人工标注提供。
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 人工标注的标准答案文本
     */
    public abstract String prepareGroundTruth(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 准备检索上下文（RAG 检索到的文档片段）
     *
     * @param inputData           输入数据
     * @param apiCompletionResult API 调用结果
     * @return 检索到的上下文文本，多段内容建议用换行分隔
     */
    public abstract String prepareContext(InputData inputData, ApiCompletionResult apiCompletionResult);

    @Override
    public LLMResult parseLLMReply(String reply) {
        String jsonBlock = RegexUtils.extractMarkdownJsonBlock(reply);
        return JsonUtils.fromJson(StringUtils.isEmpty(jsonBlock) ? reply : jsonBlock, LLMResult.class);
    }
}

