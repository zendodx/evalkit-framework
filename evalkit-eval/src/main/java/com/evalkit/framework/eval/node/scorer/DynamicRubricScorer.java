package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.config.DynamicRubricScorerConfig;
import com.evalkit.framework.eval.node.scorer.config.RubricBasedScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.GeneratedRubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricScoreType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动态量规（Dynamic Rubric）效果评估器。
 * <p>
 * 在静态 {@link RubricBasedScorer} 的基础上，增加了"先生成 Rubric、再执行打分"的两阶段流程：
 * <ol>
 *   <li><b>Rubric 生成阶段</b>：调用 LLM，根据当前数据项的 query/task 自动生成一批
 *       任务特定的评估维度（{@link GeneratedRubricCriteria}），每个数据项的评估维度都可以不同。</li>
 *   <li><b>评分阶段</b>：将生成的维度（可合并静态公共维度）传入父类 {@link RubricBasedScorer}
 *       的标准打分流程，完成多维度并发评估。</li>
 * </ol>
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li>通过覆盖 {@link #resolveCriteria(DataItem)} 钩子方法，将动态生成的维度注入父类评估流程，
 *       无需修改父类任何逻辑。</li>
 *   <li>支持<b>混合模式</b>：{@code staticCriteria}（静态公共维度，如安全检测）与动态生成维度合并，
 *       静态维度排在前面以保证优先级。</li>
 *   <li>支持<b>兜底策略</b>：当 LLM 生成 Rubric 失败时，可降级到 {@code fallbackCriteria}（静态兜底维度）
 *       继续评估，保证流程不中断；若不配置兜底，则直接抛出异常。</li>
 *   <li>支持<b>维度数量上限</b>：{@code maxGeneratedCriteria} 限制动态生成维度数量，防止 LLM
 *       过度生成导致 Token 浪费。</li>
 * </ul>
 *
 * <h3>典型用法（纯动态模式）</h3>
 * <pre>
 * public class AgentQualityScorer extends DynamicRubricScorer {
 *
 *     public AgentQualityScorer() {
 *         super(DynamicRubricScorerConfig.builder()
 *             .metricName("AgentQuality")
 *             .llmService(myLLMService)
 *             .rubricGenPrompt(
 *                 "你是一位专业的 AI 评估专家。\n" +
 *                 "请根据以下用户请求，生成 3~5 个关键评估维度，评估 AI 助手的回复质量。\n" +
 *                 "要求：维度名使用英文，评分范围 1~5 分，只输出 JSON 数组。\n" +
 *                 "格式：[{\"name\":\"...\",\"definition\":\"...\",\"scoringGuide\":\"5=...; 3=...; 1=...\"}]"
 *             )
 *             .maxGeneratedCriteria(5)
 *             .dynamicMergeStrategy(RubricMergeStrategy.WEIGHTED_AVERAGE)
 *             .build());
 *     }
 *
 *     {@literal @}Override
 *     public String prepareUserPrompt(InputData input, ApiCompletionResult result) {
 *         return "用户请求: " + input.get("query") + "\nAI 回复: " + result.getAnswer();
 *     }
 *
 *     {@literal @}Override
 *     protected String prepareRubricGenInput(DataItem dataItem) {
 *         // 提取 query 供 Rubric 生成阶段使用
 *         return "用户请求: " + dataItem.getInputData().get("query");
 *     }
 * }
 * </pre>
 *
 * <h3>混合模式（静态 + 动态）</h3>
 * <pre>
 * DynamicRubricScorerConfig.builder()
 *     .staticCriteria(Arrays.asList(
 *         RubricCriteria.builder()
 *             .name("Safety").star(true)
 *             .definition("是否包含有害内容").scoringGuide("1=无害; 0=有害")
 *             .build()
 *     ))
 *     .rubricGenPrompt("根据 query 生成任务专属评估维度...")
 *     .build();
 * </pre>
 */
@Slf4j
public abstract class DynamicRubricScorer extends RubricBasedScorer {

    /** 动态 Rubric 专用 extra key：本次动态生成的原始维度列表 */
    public static final String EXTRA_KEY_GENERATED_CRITERIA = "dynamic_rubric_generated_criteria";

    /** 动态 Rubric 专用 extra key：是否启用了兜底策略 */
    public static final String EXTRA_KEY_RUBRIC_FALLBACK = "dynamic_rubric_fallback";

    protected final DynamicRubricScorerConfig dynamicConfig;

    public DynamicRubricScorer(DynamicRubricScorerConfig config) {
        super(config);
        this.dynamicConfig = config;
    }

    // ==================== 父类校验覆盖 ====================

    /**
     * 覆盖父类校验，放宽 {@code criteria} 的非空约束。
     * <p>
     * 动态 Rubric 在初始化阶段 criteria 为空（运行时才生成），
     * 但需要校验 {@code rubricGenPrompt} 必须配置。
     */
    @Override
    protected void validRubricConfig(RubricBasedScorerConfig config) {
        if (config.getLlmService() == null) {
            throw new IllegalArgumentException("[DynamicRubricScorer] LLMService must not be null");
        }
        if (config.getSamplingTimes() < 1) {
            throw new IllegalArgumentException("[DynamicRubricScorer] samplingTimes must >= 1");
        }
        if (config instanceof DynamicRubricScorerConfig) {
            DynamicRubricScorerConfig dc = (DynamicRubricScorerConfig) config;
            if (dc.getMaxGeneratedCriteria() < 1) {
                throw new IllegalArgumentException("[DynamicRubricScorer] maxGeneratedCriteria must >= 1");
            }
        }
    }

    // ==================== 子类扩展点 ====================

    /**
     * 准备 Rubric 生成阶段的输入文本（子类可覆盖）。
     * <p>
     * 框架将此文本追加到 {@code rubricGenPrompt} 之后，发送给 LLM 生成评估维度。
     * 通常返回当前 query 或任务描述。
     * <p>
     * 默认实现尝试从 {@code inputData} 中依次读取 {@code "query"}、{@code "task"}、
     * {@code "input"} 字段；若均不存在，则返回空字符串（LLM 将仅根据 rubricGenPrompt 生成）。
     *
     * @param dataItem 当前评估数据项
     * @return 注入 Rubric 生成 Prompt 的任务描述文本
     */
    protected String prepareRubricGenInput(DataItem dataItem) {
        if (dataItem.getInputData() == null) {
            return "";
        }
        for (String key : new String[]{"query", "task", "input"}) {
            Object val = dataItem.getInputData().get(key);
            if (val != null && StringUtils.isNotBlank(val.toString())) {
                return val.toString();
            }
        }
        return "";
    }

    // ==================== 核心：动态 Rubric 解析 ====================

    /**
     * 覆盖父类钩子方法，实现动态 Rubric 生成逻辑。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>调用 LLM 根据当前 query/task 生成评估维度列表。</li>
     *   <li>将生成的 {@link GeneratedRubricCriteria} 转换为标准 {@link RubricCriteria}。</li>
     *   <li>若配置了 {@code staticCriteria}，将其前置合并（静态维度优先）。</li>
     *   <li>若生成失败且配置了 {@code fallbackCriteria}，降级使用兜底维度。</li>
     * </ol>
     *
     * @param dataItem 当前评估数据项
     * @return 本次评估应使用的维度列表
     */
    @Override
    protected List<RubricCriteria> resolveCriteria(DataItem dataItem) {
        List<RubricCriteria> generated;
        boolean usedFallback = false;
        try {
            generated = generateCriteria(dataItem);
        } catch (Exception e) {
            log.warn("[DynamicRubricScorer] Rubric generation failed, dataIndex={}, error={}",
                    dataItem.getDataIndex(), e.getMessage());
            if (CollectionUtils.isNotEmpty(dynamicConfig.getFallbackCriteria())) {
                log.warn("[DynamicRubricScorer] Falling back to static fallback criteria, dataIndex={}",
                        dataItem.getDataIndex());
                generated = dynamicConfig.getFallbackCriteria();
                usedFallback = true;
            } else {
                throw new EvalException("[DynamicRubricScorer] Rubric generation failed and no fallbackCriteria configured: "
                        + e.getMessage(), e);
            }
        }

        // 将生成结果和兜底标志存入 DataItem.extra，供报告层使用
        dataItem.addExtraItem(EXTRA_KEY_GENERATED_CRITERIA, generated);
        dataItem.addExtraItem(EXTRA_KEY_RUBRIC_FALLBACK, usedFallback);

        // 混合模式：静态公共维度排在前面
        List<RubricCriteria> staticCriteria = dynamicConfig.getStaticCriteria();
        if (CollectionUtils.isNotEmpty(staticCriteria)) {
            List<RubricCriteria> merged = new ArrayList<>(staticCriteria);
            merged.addAll(generated);
            return merged;
        }
        return generated;
    }

    /**
     * 调用 LLM 生成评估维度列表，并转换为标准 {@link RubricCriteria}。
     * <p>
     * 若 LLM 回复解析失败，则抛出 {@link EvalException}，
     * 调用方可根据 {@code fallbackCriteria} 配置决定是否降级。
     *
     * @param dataItem 当前评估数据项
     * @return 转换后的标准维度列表
     */
    private List<RubricCriteria> generateCriteria(DataItem dataItem) {
        String genInput = prepareRubricGenInput(dataItem);
        String prompt = buildRubricGenPrompt(genInput);

        boolean enableRetry = dynamicConfig.isEnableRetry();
        int maxRetry = dynamicConfig.getRetryTimes();
        Exception lastEx = null;

        for (int retry = 0; retry <= (enableRetry ? maxRetry : 0); retry++) {
            try {
                String reply = dynamicConfig.getLlmService().chat(prompt);
                log.debug("[DynamicRubricScorer] rubric gen reply, dataIndex={}, reply={}", dataItem.getDataIndex(), reply);
                List<RubricCriteria> criteria = parseGeneratedCriteria(reply);
                if (CollectionUtils.isEmpty(criteria)) {
                    throw new EvalException("[DynamicRubricScorer] LLM returned empty criteria list");
                }
                log.info("[DynamicRubricScorer] Generated {} criteria for dataIndex={}", criteria.size(), dataItem.getDataIndex());
                return criteria;
            } catch (Exception e) {
                lastEx = e;
                log.warn("[DynamicRubricScorer] Rubric gen retry={}/{}, error={}", retry, maxRetry, e.getMessage());
                if (retry < maxRetry) {
                    try {
                        Thread.sleep(dynamicConfig.getRetryTimeUnit().toMillis(dynamicConfig.getRetryInterval()));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        throw new EvalException("[DynamicRubricScorer] Rubric generation failed after retry: "
                + (lastEx != null ? lastEx.getMessage() : "unknown"), lastEx);
    }

    // ==================== Prompt 构建 ====================

    /**
     * 构建 Rubric 生成阶段的完整 Prompt。
     * <p>
     * 结构：{@code rubricGenPrompt} + 分隔符 + 任务输入文本。
     *
     * @param genInput 当前数据项的任务描述文本
     * @return 完整的 Rubric 生成 Prompt
     */
    private String buildRubricGenPrompt(String genInput) {
        StringBuilder sb = new StringBuilder();
        sb.append(dynamicConfig.getRubricGenPrompt());
        int max = dynamicConfig.getMaxGeneratedCriteria();
        // 将最大维度数注入 Prompt，让 LLM 控制输出数量
        sb.append("\n注意：最多生成 ").append(max).append(" 个维度，不得超过此数量。\n");
        if (StringUtils.isNotBlank(genInput)) {
            sb.append("\n----------以下是当前任务/请求----------\n");
            sb.append(genInput);
        }
        return sb.toString();
    }

    // ==================== 回复解析 ====================

    /**
     * 解析 LLM 回复，将 JSON 数组转换为 {@link RubricCriteria} 列表。
     *
     * @param reply LLM 原始回复文本
     * @return 解析后的标准评估维度列表
     * @throws EvalException 解析失败时抛出
     */
    private List<RubricCriteria> parseGeneratedCriteria(String reply) {
        String jsonStr = RegexUtils.extractMarkdownJsonBlock(reply);
        if (StringUtils.isEmpty(jsonStr)) {
            jsonStr = reply;
        }
        List<GeneratedRubricCriteria> generated = JsonUtils.fromJsonToList(jsonStr, GeneratedRubricCriteria.class);
        if (CollectionUtils.isEmpty(generated)) {
            throw new EvalException("[DynamicRubricScorer] Failed to parse rubric gen reply as JSON array, reply: " + reply);
        }

        int maxCount = dynamicConfig.getMaxGeneratedCriteria();
        if (generated.size() > maxCount) {
            log.warn("[DynamicRubricScorer] Generated {} criteria exceeds max {}, truncating",
                    generated.size(), maxCount);
            generated = generated.subList(0, maxCount);
        }

        List<RubricCriteria> result = new ArrayList<>();
        for (GeneratedRubricCriteria g : generated) {
            if (StringUtils.isBlank(g.getName())) {
                log.warn("[DynamicRubricScorer] Skipping generated criteria with blank name");
                continue;
            }
            RubricCriteria criteria = convertToCriteria(g);
            result.add(criteria);
        }

        if (result.isEmpty()) {
            throw new EvalException("[DynamicRubricScorer] All generated criteria are invalid (blank names), reply: " + reply);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 将 {@link GeneratedRubricCriteria} 转换为标准 {@link RubricCriteria}。
     * <p>
     * 转换规则：
     * <ul>
     *   <li>二元分判断：{@code maxScore == 1 && minScore == 0} 时自动识别为 BINARY。</li>
     *   <li>其余情况统一使用 STEPPED（阶梯分）。</li>
     *   <li>名称中的前后空格会被自动 trim。</li>
     * </ul>
     *
     * @param g LLM 生成的原始维度描述
     * @return 标准量规维度
     */
    private RubricCriteria convertToCriteria(GeneratedRubricCriteria g) {
        boolean isBinary = (g.getMaxScore() == 1.0 && g.getMinScore() == 0.0);
        RubricScoreType scoreType = isBinary ? RubricScoreType.BINARY : RubricScoreType.STEPPED;

        double passScore = g.getPassScore();
        // passScore 合法性保护：不得超过 maxScore，也不得低于 minScore
        passScore = Math.min(passScore, g.getMaxScore());
        passScore = Math.max(passScore, g.getMinScore());

        return RubricCriteria.builder()
                .name(g.getName().trim())
                .definition(g.getDefinition() != null ? g.getDefinition() : "")
                .scoringGuide(g.getScoringGuide())
                .scoreType(scoreType)
                .maxScore(g.getMaxScore())
                .minScore(g.getMinScore())
                .passScore(passScore)
                .weight(g.getWeight() > 0 ? g.getWeight() : 1.0)
                .build();
    }
}

