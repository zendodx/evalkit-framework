package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.RubricBasedScorerConfig;
import com.evalkit.framework.eval.node.scorer.model.RubricCriteria;
import com.evalkit.framework.eval.node.scorer.model.RubricMergeStrategy;
import com.evalkit.framework.eval.node.scorer.model.RubricScoreType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 量规（Rubric）效果评估器。
 * <p>
 * 量规是一种结构化的评估工具，通过预先定义的多个评估维度（criteria）和对应的评分规则，
 * 对模型输出进行系统性、可量化的质量评估。
 * <p>
 * 核心设计要点：
 * <ul>
 *   <li>支持单维度模式（默认）与批量模式：{@code criteriaBatchSize=1} 时每个维度独立调用，
 *       注意力最集中；{@code criteriaBatchSize>1} 时多维度合并为一次调用，节省 Token。</li>
 *   <li>Prompt 强制要求先推理后打分（Chain-of-Thought），防止 LLM 先锚定分数再补理由。</li>
 *   <li>各维度得分在聚合前先归一化到 [0, 1]，避免不同量程维度之间的量纲错乱。</li>
 *   <li>支持在每个维度配置 Few-shot 分值示例，校准中间分值漂移（可选）。</li>
 *   <li>支持对同一维度多次采样取均值，提升打分稳定性（可选，默认 1 次）。</li>
 * </ul>
 * <p>
 * 使用方式（子类只需实现 {@link #prepareUserPrompt}）：
 * <pre>
 * public class MyScorer extends RubricBasedScorer {
 *     public MyScorer() {
 *         super(RubricBasedScorerConfig.builder()
 *             .metricName("内容质量")
 *             .llmService(myLLMService)
 *             .criteria(Arrays.asList(
 *                 RubricCriteria.builder()
 *                     .name("Faithfulness")
 *                     .definition("输出是否忠实于源材料")
 *                     .scoreType(RubricScoreType.STEPPED)
 *                     .maxScore(5).passScore(3).weight(2.0)
 *                     .scoringGuide("5=完全忠实; 3=基本忠实但有小偏差; 1=存在明显捏造")
 *                     .build(),
 *                 RubricCriteria.builder()
 *                     .name("Harmfulness").star(true)
 *                     .definition("是否包含有害内容").scoringGuide("0=无害; 1=有害")
 *                     .build()
 *             ))
 *             .build());
 *     }
 *
 *     {@literal @}Override
 *     public String prepareUserPrompt(InputData input, ApiCompletionResult result) {
 *         return "问题: " + input.get("query") + "\n回答: " + result.getAnswer();
 *     }
 * }
 * </pre>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public abstract class RubricBasedScorer extends Scorer {

    protected final RubricBasedScorerConfig config;

    // ==================== extra 字段 Key ====================

    /**
     * 各维度原始分数，{@code Map<criteriaName, rawScore>}
     */
    public static final String EXTRA_KEY_CRITERIA_RAW_SCORES = "rubric_criteria_raw_scores";

    /**
     * 各维度归一化分数，{@code Map<criteriaName, normalizedScore>}
     */
    public static final String EXTRA_KEY_CRITERIA_NORM_SCORES = "rubric_criteria_norm_scores";

    /**
     * 各维度打分理由，{@code Map<criteriaName, reason>}
     */
    public static final String EXTRA_KEY_CRITERIA_REASONS = "rubric_criteria_reasons";

    /**
     * 各维度推理过程，{@code Map<criteriaName, reasoning>}
     */
    public static final String EXTRA_KEY_CRITERIA_REASONINGS = "rubric_criteria_reasonings";

    /**
     * 各维度通过率阈值，{@code Map<criteriaName, passRate>}，用于报告层判断每个维度是否达标
     */
    public static final String EXTRA_KEY_CRITERIA_PASS_RATES = "rubric_criteria_pass_rates";

    /**
     * 最终合并策略名称
     */
    public static final String EXTRA_KEY_MERGE_STRATEGY = "rubric_merge_strategy";

    public RubricBasedScorer(RubricBasedScorerConfig config) {
        super(config);
        validRubricConfig(config);
        this.config = config;
        super.scorerType = "rubricBasedScorer";
    }

    private void validRubricConfig(RubricBasedScorerConfig config) {
        if (config.getLlmService() == null) {
            throw new IllegalArgumentException("[RubricBasedScorer] LLMService must not be null");
        }
        if (CollectionUtils.isEmpty(config.getCriteria())) {
            throw new IllegalArgumentException("[RubricBasedScorer] criteria must not be empty");
        }
        if (config.getSamplingTimes() < 1) {
            throw new IllegalArgumentException("[RubricBasedScorer] samplingTimes must >= 1");
        }
        if (config.getCriteriaBatchSize() != 1 && config.getCriteriaBatchSize() < 0) {
            // <= 0 的值在运行时会被 clamp 为全量维度数，此处仅排除明确非法的负数（-1以下）
            // 实际上 <= 0 统一视为「全量」，不抛异常
        }
        // 校验各维度参数合法性及名称唯一性
        Set<String> nameSet = new HashSet<>();
        for (RubricCriteria c : config.getCriteria()) {
            if (StringUtils.isBlank(c.getName())) {
                throw new IllegalArgumentException("[RubricBasedScorer] criteria name must not be blank");
            }
            if (!nameSet.add(c.getName())) {
                throw new IllegalArgumentException("[RubricBasedScorer] duplicate criteria name: " + c.getName());
            }
            if (c.getMaxScore() <= 0) {
                throw new IllegalArgumentException("[RubricBasedScorer] criteria maxScore must > 0, criteria: " + c.getName());
            }
            if (c.getMinScore() >= c.getMaxScore()) {
                throw new IllegalArgumentException("[RubricBasedScorer] criteria minScore must < maxScore, criteria: " + c.getName());
            }
            if (c.getPassScore() > c.getMaxScore()) {
                throw new IllegalArgumentException("[RubricBasedScorer] criteria passScore must <= maxScore, criteria: " + c.getName());
            }
            if (c.getWeight() < 0) {
                throw new IllegalArgumentException("[RubricBasedScorer] criteria weight must >= 0, criteria: " + c.getName());
            }
        }
    }

    // ==================== 子类扩展点 ====================

    /**
     * 准备用户侧待评估内容（子类必须实现）。
     * <p>
     * 通常包含问题、模型回答、参考上下文等，直接拼接注入各维度 Prompt。
     *
     * @param inputData           当前样本的输入数据
     * @param apiCompletionResult 当前样本的接口返回结果
     * @return 用于评估的用户侧文本
     */
    public abstract String prepareUserPrompt(InputData inputData, ApiCompletionResult apiCompletionResult);

    // ==================== 核心评估流程 ====================

    @Override
    public ScorerResult eval(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
        String userPrompt = prepareUserPrompt(inputData, apiCompletionResult);

        List<RubricCriteria> criteriaList = config.getCriteria();

        // 存储每个维度的评分结果
        Map<String, Double> rawScores = new LinkedHashMap<>();
        Map<String, Double> normalizedScores = new LinkedHashMap<>();
        Map<String, String> reasons = new LinkedHashMap<>();
        Map<String, String> reasonings = new LinkedHashMap<>();

        // 条件过滤：将维度拆分为「需要执行」和「跳过」两组
        List<RubricCriteria> activeCriteria = new ArrayList<>();
        for (RubricCriteria c : criteriaList) {
            if (c.shouldEval(dataItem)) {
                activeCriteria.add(c);
            } else {
                // 跳过的维度直接用 skipScore 填充，不发起 LLM 调用
                double skipRaw = c.getSkipScore();
                double skipNorm = c.normalize(skipRaw);
                rawScores.put(c.getName(), skipRaw);
                normalizedScores.put(c.getName(), skipNorm);
                reasons.put(c.getName(), "[skipped]");
                reasonings.put(c.getName(), "");
                log.debug("[RubricBasedScorer] criteria={} skipped, skipScore={}, normalizedScore={}",
                        c.getName(), skipRaw, skipNorm);
            }
        }

        // 对需要执行的维度发起并发 LLM 调用。
        // 使用独立的 SCORER_CRITERIA 线程池，避免与外层 SCORER 池形成嵌套死锁。
        if (!activeCriteria.isEmpty()) {
            // 按 criteriaBatchSize 将维度分组，每组作为一次 LLM 调用任务
            int batchSize = config.getCriteriaBatchSize();
            if (batchSize <= 0) {
                batchSize = activeCriteria.size(); // <= 0 视为全量合并为一次调用
            }
            List<List<RubricCriteria>> batches = partitionList(activeCriteria, batchSize);

            int criteriaThreadNum = Math.min(batches.size(), config.getCriteriaThreadNum());
            List<List<CriteriaEvalResult>> batchResults = BatchRunner.runBatch(
                    batches,
                    batch -> evalCriteriaBatch(batch, userPrompt),
                    PoolName.SCORER_CRITERIA,
                    criteriaThreadNum,
                    size -> size * config.getBatchTimeoutSec()
            );
            if (CollectionUtils.isEmpty(batchResults) || batchResults.size() != batches.size()) {
                throw new EvalException("[RubricBasedScorer] Partial or all criteria eval failed, expected=" +
                        batches.size() + " batches, actual=" + (batchResults == null ? 0 : batchResults.size()));
            }
            // 将各批次结果打平，按 activeCriteria 顺序写入
            int idx = 0;
            for (int b = 0; b < batches.size(); b++) {
                List<RubricCriteria> batch = batches.get(b);
                List<CriteriaEvalResult> results = batchResults.get(b);
                for (int j = 0; j < batch.size(); j++) {
                    RubricCriteria criteria = batch.get(j);
                    CriteriaEvalResult result = results.get(j);
                    rawScores.put(criteria.getName(), result.rawScore);
                    normalizedScores.put(criteria.getName(), result.normalizedScore);
                    reasons.put(criteria.getName(), result.reason);
                    reasonings.put(criteria.getName(), result.reasoning);
                    log.debug("[RubricBasedScorer] criteria={}, rawScore={}, normalizedScore={}, reason={}",
                            criteria.getName(), result.rawScore, result.normalizedScore, result.reason);
                    idx++;
                }
            }
        }

        // 合并各维度得分。
        // normalizeScore=true（默认）：使用归一化分数合并，totalScore 固定为 1.0。
        // normalizeScore=false：使用原始分数合并，totalScore 动态计算为各维度加权 maxScore。
        boolean normalize = config.isNormalizeScore();
        Map<String, Double> mergeScoreMap = normalize ? normalizedScores : rawScores;
        double finalScore = mergeScores(criteriaList, mergeScoreMap);
        double totalScore = normalize ? 1.0 : calcRawTotalScore(criteriaList);

        // 构造汇总 reason
        String reason = buildReason(criteriaList, rawScores, normalizedScores, reasons);

        // 各维度通过率阈值（passRate = passScore / maxScore），供报告层判断每个维度是否达标
        Map<String, Double> passRates = new LinkedHashMap<>();
        for (RubricCriteria c : criteriaList) {
            passRates.put(c.getName(), c.getPassRate());
        }

        // 将各维度详情透传到 extra，供报告层使用
        ScorerResult scorerResult = new ScorerResult();
        scorerResult.setMetric(config.getMetricName());
        scorerResult.setScore(finalScore);
        scorerResult.setTotalScore(totalScore);
        scorerResult.setReason(reason);
        scorerResult.addExtraItem(EXTRA_KEY_CRITERIA_RAW_SCORES, rawScores);
        scorerResult.addExtraItem(EXTRA_KEY_CRITERIA_NORM_SCORES, normalizedScores);
        scorerResult.addExtraItem(EXTRA_KEY_CRITERIA_PASS_RATES, passRates);
        scorerResult.addExtraItem(EXTRA_KEY_CRITERIA_REASONS, reasons);
        scorerResult.addExtraItem(EXTRA_KEY_CRITERIA_REASONINGS, reasonings);
        scorerResult.addExtraItem(EXTRA_KEY_MERGE_STRATEGY, config.getMergeStrategy().name());
        return scorerResult;
    }

    // ==================== 维度评估调度 ====================

    /**
     * 将列表按 batchSize 均匀分组。
     */
    private static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            result.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return result;
    }

    /**
     * 评估一批维度（大小可为 1 或 >1）。
     * <p>
     * 当批次大小为 1 时走单维度路径（兼容原行为）；否则走批量路径，将多个维度合并为一次 LLM 调用。
     *
     * @param batch      一批评估维度
     * @param userPrompt 用户侧待评估文本
     * @return 与 batch 顺序一一对应的评估结果列表
     */
    private List<CriteriaEvalResult> evalCriteriaBatch(List<RubricCriteria> batch, String userPrompt) {
        if (batch.size() == 1) {
            return Collections.singletonList(evalSingleCriteria(batch.get(0), userPrompt));
        }
        return evalMultiCriteria(batch, userPrompt);
    }

    // ==================== 单维度评估 ====================

    /**
     * 对单个维度发起 LLM 评估，支持多次采样取均值以提升打分稳定性。
     *
     * @param criteria   评估维度
     * @param userPrompt 用户侧待评估文本
     * @return 该维度的评估结果
     */
    private CriteriaEvalResult evalSingleCriteria(RubricCriteria criteria, String userPrompt) {
        int samplingTimes = config.getSamplingTimes();
        List<CriteriaEvalResult> samples = new ArrayList<>();

        for (int i = 0; i < samplingTimes; i++) {
            try {
                samples.add(callLLMForCriteria(criteria, userPrompt));
            } catch (Exception e) {
                log.warn("[RubricBasedScorer] LLM call failed for criteria={}, sample={}/{}, error={}",
                        criteria.getName(), i + 1, samplingTimes, e.getMessage());
                // 单次采样失败不终止，跳过该次；若全部失败则在后面统一抛出
            }
        }

        if (samples.isEmpty()) {
            throw new EvalException(String.format(
                    "[RubricBasedScorer] All %d sampling attempts failed for criteria: %s", samplingTimes, criteria.getName()));
        }

        // 多次采样取均值，reason/reasoning 保留与均值最接近的那次采样
        double avgRaw = samples.stream().mapToDouble(s -> s.rawScore).average().orElse(0.0);
        double normalizedScore = criteria.normalize(avgRaw);
        CriteriaEvalResult representative = samples.stream()
                .min(Comparator.comparingDouble(s -> Math.abs(s.rawScore - avgRaw)))
                .orElse(samples.get(samples.size() - 1));
        return new CriteriaEvalResult(avgRaw, normalizedScore, representative.reason, representative.reasoning);
    }

    /**
     * 单次 LLM 调用：构建 CoT Prompt，调用 LLM，解析并返回结果。
     *
     * @param criteria   评估维度
     * @param userPrompt 用户侧待评估文本
     * @return 解析后的维度评估结果
     * @throws EvalException LLM 调用或解析全部失败时抛出
     */
    private CriteriaEvalResult callLLMForCriteria(RubricCriteria criteria, String userPrompt) {
        String prompt = buildCriteriaPrompt(criteria, userPrompt);

        boolean enableRetry = config.isEnableRetry();
        int maxRetry = config.getRetryTimes();
        Exception lastEx = null;

        for (int retry = 0; retry <= (enableRetry ? maxRetry : 0); retry++) {
            try {
                String reply = config.getLlmService().chat(prompt);
                log.debug("[RubricBasedScorer] criteria={}, prompt={}, reply={}", criteria.getName(), prompt, reply);
                return parseCriteriaReply(criteria, reply);
            } catch (Exception e) {
                lastEx = e;
                log.warn("[RubricBasedScorer] criteria={} retry={}/{} error={}", criteria.getName(), retry, maxRetry, e.getMessage());
                if (retry < maxRetry) {
                    try {
                        Thread.sleep(config.getRetryTimeUnit().toMillis(config.getRetryInterval()));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        throw new EvalException(String.format("[RubricBasedScorer] criteria=%s LLM call failed after retry: %s",
                criteria.getName(), lastEx != null ? lastEx.getMessage() : "unknown"));
    }

    // ==================== 批量维度评估 ====================

    /**
     * 将多个维度合并为一次 LLM 调用进行评估，节省 Token。
     * <p>
     * LLM 被要求按维度顺序逐一输出 JSON 对象，整体包裹在 JSON 数组中。
     * 若解析失败，将整批重试；若仍失败则对批次内每个维度降级为单独调用。
     *
     * @param batch      待评估的一批维度
     * @param userPrompt 用户侧待评估文本
     * @return 与 batch 顺序一一对应的评估结果列表
     */
    private List<CriteriaEvalResult> evalMultiCriteria(List<RubricCriteria> batch, String userPrompt) {
        String prompt = buildBatchCriteriaPrompt(batch, userPrompt);

        boolean enableRetry = config.isEnableRetry();
        int maxRetry = config.getRetryTimes();
        Exception lastEx = null;

        for (int retry = 0; retry <= (enableRetry ? maxRetry : 0); retry++) {
            try {
                String reply = config.getLlmService().chat(prompt);
                log.debug("[RubricBasedScorer] batch size={}, prompt={}, reply={}", batch.size(), prompt, reply);
                List<CriteriaEvalResult> results = parseBatchCriteriaReply(batch, reply);
                if (results.size() == batch.size()) {
                    return results;
                }
                throw new EvalException(String.format(
                        "[RubricBasedScorer] Batch reply size mismatch: expected=%d, got=%d",
                        batch.size(), results.size()));
            } catch (Exception e) {
                lastEx = e;
                log.warn("[RubricBasedScorer] batch retry={}/{} error={}", retry, maxRetry, e.getMessage());
                if (retry < maxRetry) {
                    try {
                        Thread.sleep(config.getRetryTimeUnit().toMillis(config.getRetryInterval()));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 整批重试耗尽后，降级为逐个单独调用
        log.warn("[RubricBasedScorer] Batch eval failed after {} retries, falling back to single-criteria calls", maxRetry);
        List<CriteriaEvalResult> fallbackResults = new ArrayList<>();
        for (RubricCriteria criteria : batch) {
            fallbackResults.add(evalSingleCriteria(criteria, userPrompt));
        }
        return fallbackResults;
    }

    // ==================== Prompt 构建 ====================

    /**
     * 为多个维度构建批量 CoT Prompt，将所有维度合并为一次 LLM 调用。
     * <p>
     * LLM 被要求输出 JSON 数组，每个元素对应一个维度，顺序与 batch 一致。
     *
     * @param batch      待评估的一批维度
     * @param userPrompt 用户侧待评估文本
     * @return 完整的批量评估 Prompt
     */
    private String buildBatchCriteriaPrompt(List<RubricCriteria> batch, String userPrompt) {
        StringBuilder sb = new StringBuilder();

        // 系统角色
        sb.append("你是一位严格、客观的量规评估专家，负责对指定内容按多个维度进行精确打分。\n\n");

        // 逐维度说明
        sb.append("【评估维度列表】\n");
        for (int i = 0; i < batch.size(); i++) {
            RubricCriteria c = batch.get(i);
            sb.append(String.format("\n--- 维度 %d: %s ---\n", i + 1, c.getName()));
            sb.append("定义: ").append(c.getDefinition()).append("\n");
            if (c.getScoreType() == RubricScoreType.BINARY) {
                sb.append("评分类型: 二元分（只能输出 0 或 1）\n");
            } else {
                sb.append("评分类型: 阶梯分（").append((int) c.getMinScore())
                        .append(" ~ ").append((int) c.getMaxScore()).append(" 分）\n");
            }
            if (StringUtils.isNotBlank(c.getScoringGuide())) {
                sb.append("打分标准: ").append(c.getScoringGuide()).append("\n");
            }
            if (CollectionUtils.isNotEmpty(c.getAnchors())) {
                sb.append("分值锚点:\n");
                for (RubricCriteria.ScoringAnchor anchor : c.getAnchors()) {
                    sb.append(String.format("  %.0f 分示例: %s\n", anchor.getScore(), anchor.getDescription()));
                }
            }
        }

        // CoT 输出格式约束
        sb.append("\n【输出要求】\n");
        sb.append("请严格按照以下 JSON 数组格式输出，数组元素顺序必须与上方维度列表顺序完全一致，不要输出任何其他内容：\n");
        sb.append("```json\n");
        sb.append("[\n");
        for (int i = 0; i < batch.size(); i++) {
            sb.append("  {\n");
            sb.append("    \"criteria\": \"").append(batch.get(i).getName()).append("\",\n");
            sb.append("    \"reasoning\": \"<逐步分析待评估内容与该维度定义的匹配程度，给出具体依据，不超过300字>\",\n");
            sb.append("    \"score\": <数值，根据推理过程得出的最终分数>,\n");
            sb.append("    \"reason\": \"<一句话总结打分结论，不超过80字>\"\n");
            sb.append(i < batch.size() - 1 ? "  },\n" : "  }\n");
        }
        sb.append("]\n");
        sb.append("```\n");
        sb.append("重要: 每个对象中 reasoning 字段必须先于 score 字段，先分析后打分，不得倒置顺序。\n");
        sb.append("重要: JSON 字符串字段中如需使用双引号，必须用转义形式 \\\" 表示，不得使用未转义的 \"。\n");
        sb.append("重要: 数组元素数量必须等于维度数量（").append(batch.size()).append(" 个），不得增减。\n");
        sb.append("输出完成后请自检，确保 JSON 严格可解析。\n\n");

        // 用户待评估数据
        sb.append("----------以下是待评估内容----------\n");
        sb.append(userPrompt);

        return sb.toString();
    }

    /**
     * 为单个维度构建 CoT Prompt。
     * <p>
     * 格式：系统角色 + 维度规范 + Few-shot 锚点（可选）+ 输出格式约束 + 用户待评估数据。
     *
     * @param criteria   评估维度
     * @param userPrompt 用户侧待评估文本
     * @return 完整的评估 Prompt
     */
    private String buildCriteriaPrompt(RubricCriteria criteria, String userPrompt) {
        StringBuilder sb = new StringBuilder();

        // 系统角色
        sb.append("你是一位严格、客观的量规评估专家，负责对指定内容按单一维度进行精确打分。\n\n");

        // 维度规范
        sb.append("【评估维度】\n");
        sb.append("名称: ").append(criteria.getName()).append("\n");
        sb.append("定义: ").append(criteria.getDefinition()).append("\n");

        // 分数范围说明
        if (criteria.getScoreType() == RubricScoreType.BINARY) {
            sb.append("评分类型: 二元分（只能输出 0 或 1）\n");
        } else {
            sb.append("评分类型: 阶梯分（").append((int) criteria.getMinScore())
                    .append(" ~ ").append((int) criteria.getMaxScore()).append(" 分）\n");
        }

        // 打分指引
        if (StringUtils.isNotBlank(criteria.getScoringGuide())) {
            sb.append("打分标准: ").append(criteria.getScoringGuide()).append("\n");
        }

        // Few-shot 分值锚点示例
        if (CollectionUtils.isNotEmpty(criteria.getAnchors())) {
            sb.append("\n【分值锚点示例（帮助你校准分值）】\n");
            for (RubricCriteria.ScoringAnchor anchor : criteria.getAnchors()) {
                sb.append(String.format("  %.0f 分示例: %s\n", anchor.getScore(), anchor.getDescription()));
            }
        }

        // CoT 输出格式约束：要求先填写 reasoning 再给出 score，防止先锚定分数再补理由
        sb.append("\n【输出要求】\n");
        sb.append("请严格按照以下 JSON 格式输出，不要输出任何其他内容：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"reasoning\": \"<逐步分析待评估内容与该维度定义的匹配程度，给出具体依据，不超过500字>\",\n");
        sb.append("  \"score\": <数值，根据推理过程得出的最终分数>,\n");
        sb.append("  \"reason\": \"<一句话总结打分结论，不超过100字>\"\n");
        sb.append("}\n");
        sb.append("```\n");
        sb.append("重要: reasoning 字段必须先于 score 字段出现，先分析后打分，不得倒置顺序。\n");
        sb.append("重要: JSON 字符串字段中如需使用双引号，必须用转义形式 \\\" 表示，不得使用未转义的 \"。\n");
        sb.append("重要: reasoning 字段不超过500字，reason 字段不超过100字，避免输出过长。\n");
        sb.append("输出完成后请自检，确保 JSON 严格可解析。\n\n");

        // 用户待评估数据
        sb.append("----------以下是待评估内容----------\n");
        sb.append(userPrompt);

        return sb.toString();
    }

    // ==================== LLM 回复解析 ====================

    /**
     * 解析批量 LLM 回复（JSON 数组格式），与 batch 顺序一一对应。
     *
     * @param batch 评估维度列表
     * @param reply LLM 原始回复文本
     * @return 与 batch 顺序一一对应的评估结果列表
     * @throws EvalException 回复格式无法解析时抛出
     */
    private List<CriteriaEvalResult> parseBatchCriteriaReply(List<RubricCriteria> batch, String reply) {
        String jsonStr = RegexUtils.extractMarkdownJsonBlock(reply);
        if (StringUtils.isEmpty(jsonStr)) {
            jsonStr = reply;
        }
        List<CriteriaLLMOutput> outputs = JsonUtils.fromJsonToList(jsonStr, CriteriaLLMOutput.class);
        if (CollectionUtils.isEmpty(outputs)) {
            throw new EvalException("[RubricBasedScorer] Failed to parse batch LLM reply as JSON array, reply: " + reply);
        }
        if (outputs.size() != batch.size()) {
            throw new EvalException(String.format(
                    "[RubricBasedScorer] Batch reply count mismatch: expected=%d, got=%d, reply=%s",
                    batch.size(), outputs.size(), reply));
        }
        List<CriteriaEvalResult> results = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            RubricCriteria criteria = batch.get(i);
            CriteriaLLMOutput output = outputs.get(i);
            if (output == null || output.getScore() == null) {
                throw new EvalException("[RubricBasedScorer] Null score in batch reply for criteria: " + criteria.getName());
            }
            double rawScore = output.getScore();
            if (criteria.getScoreType() == RubricScoreType.BINARY) {
                rawScore = rawScore > 0 ? 1.0 : 0.0;
            }
            double normalizedScore = criteria.normalize(rawScore);
            results.add(new CriteriaEvalResult(
                    rawScore,
                    normalizedScore,
                    output.getReason() != null ? output.getReason() : "",
                    output.getReasoning() != null ? output.getReasoning() : ""
            ));
        }
        return results;
    }

    /**
     * 解析单维度 LLM 回复，提取原始分和归一化分。
     *
     * @param criteria 评估维度
     * @param reply    LLM 原始回复文本
     * @return 解析后的维度评估结果
     * @throws EvalException 回复格式无法解析时抛出
     */
    private CriteriaEvalResult parseCriteriaReply(RubricCriteria criteria, String reply) {
        String jsonStr = RegexUtils.extractMarkdownJsonBlock(reply);
        if (StringUtils.isEmpty(jsonStr)) {
            jsonStr = reply;
        }
        CriteriaLLMOutput output = JsonUtils.fromJson(jsonStr, CriteriaLLMOutput.class);
        if (output == null || output.getScore() == null) {
            throw new EvalException("[RubricBasedScorer] Failed to parse LLM reply for criteria: " + criteria.getName() + ", reply: " + reply);
        }
        double rawScore = output.getScore();
        // 二元分强制约束：非零即 1
        if (criteria.getScoreType() == RubricScoreType.BINARY) {
            rawScore = rawScore > 0 ? 1.0 : 0.0;
        }
        // normalizedScore 始终计算，供各合并策略的 passRate 判断使用
        double normalizedScore = criteria.normalize(rawScore);
        return new CriteriaEvalResult(
                rawScore,
                normalizedScore,
                output.getReason() != null ? output.getReason() : "",
                output.getReasoning() != null ? output.getReasoning() : ""
        );
    }

    // ==================== 分数合并 ====================

    /**
     * 在 {@code normalizeScore=false} 时，计算原始分数域下的 totalScore。
     * <p>
     * 取各维度加权 maxScore 之和除以总权重，与 {@code WEIGHTED_AVERAGE} 的分母保持一致，
     * 使得 {@code score / totalScore} 仍可解读为得分率。
     * 对 {@code SIMPLE_AVERAGE}，等价于各维度 maxScore 的简单平均。
     *
     * @param criteriaList 所有评估维度
     * @return 原始分数域下的满分值
     */
    private double calcRawTotalScore(List<RubricCriteria> criteriaList) {
        RubricMergeStrategy strategy = config.getMergeStrategy();
        if (strategy == RubricMergeStrategy.SIMPLE_AVERAGE) {
            if (criteriaList.isEmpty()) return 1.0;
            double sum = criteriaList.stream().mapToDouble(RubricCriteria::getMaxScore).sum();
            return sum / criteriaList.size();
        }
        // WEIGHTED_AVERAGE / LOGICAL_AND / STAR_GATE / COMPLETION_RATE 统一使用加权 maxScore
        double weightedMaxSum = 0;
        double totalWeight = 0;
        for (RubricCriteria c : criteriaList) {
            weightedMaxSum += c.getMaxScore() * c.getWeight();
            totalWeight += c.getWeight();
        }
        return totalWeight > 0 ? weightedMaxSum / totalWeight : 1.0;
    }

    /**
     * 将各维度分数按配置的 {@link RubricMergeStrategy} 合并为最终得分。
     * <p>
     * 入参 {@code scoreMap} 可能是归一化分数或原始分数，取决于 {@code normalizeScore} 配置。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度分数映射（key 为维度名称）
     * @return 合并后的最终得分
     */
    private double mergeScores(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        RubricMergeStrategy strategy = config.getMergeStrategy();

        switch (strategy) {
            case WEIGHTED_AVERAGE:
                return mergeWeightedAverage(criteriaList, scoreMap);
            case SIMPLE_AVERAGE:
                return mergeSimpleAverage(criteriaList, scoreMap);
            case LOGICAL_AND:
                return mergeLogicalAnd(criteriaList, scoreMap);
            case STAR_GATE:
                return mergeStarGate(criteriaList, scoreMap);
            case COMPLETION_RATE:
                return mergeCompletionRate(criteriaList, scoreMap);
            default:
                throw new IllegalArgumentException("[RubricBasedScorer] Unsupported mergeStrategy: " + strategy);
        }
    }

    /**
     * 加权平均：{@code Σ(score_i × weight_i) / Σ(weight_i)}。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度分数映射
     * @return 加权平均得分
     */
    private double mergeWeightedAverage(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        double weightedSum = 0;
        double totalWeight = 0;
        for (RubricCriteria c : criteriaList) {
            double score = scoreMap.getOrDefault(c.getName(), 0.0);
            weightedSum += score * c.getWeight();
            totalWeight += c.getWeight();
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0;
    }

    /**
     * 简单平均：{@code Σ(score_i) / N}，忽略权重。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度分数映射
     * @return 简单平均得分
     */
    private double mergeSimpleAverage(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        if (criteriaList.isEmpty()) return 0;
        double sum = criteriaList.stream()
                .mapToDouble(c -> scoreMap.getOrDefault(c.getName(), 0.0))
                .sum();
        return sum / criteriaList.size();
    }

    /**
     * 逻辑合取：任意维度未达 passRate 则返回最差失败维度的得分，否则取加权均值。
     * <p>
     * 仅在失败维度中取最小值，以明确反映最差短板，而非全局最小。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度归一化分数映射
     * @return 合并得分
     */
    private double mergeLogicalAnd(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        double worstFailScore = Double.MAX_VALUE;
        boolean anyFail = false;
        for (RubricCriteria c : criteriaList) {
            double normScore = scoreMap.getOrDefault(c.getName(), 0.0);
            if (normScore < c.getPassRate()) {
                anyFail = true;
                worstFailScore = Math.min(worstFailScore, normScore);
            }
        }
        return anyFail ? worstFailScore : mergeWeightedAverage(criteriaList, scoreMap);
    }

    /**
     * Star Gate：任意 {@code star=true} 的维度归一化分低于 passRate 则整体返回 0.0，否则取加权均值。
     * <p>
     * 以 {@code normScore < passRate} 而非 {@code == 0.0} 作为判断条件，避免浮点精度问题。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度归一化分数映射
     * @return 合并得分
     */
    private double mergeStarGate(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        for (RubricCriteria c : criteriaList) {
            if (c.isStar()) {
                double normScore = scoreMap.getOrDefault(c.getName(), 0.0);
                if (normScore < c.getPassRate()) {
                    log.debug("[RubricBasedScorer] Star criteria [{}] not passed (normScore={} < passRate={}), final score = 0",
                            c.getName(), normScore, c.getPassRate());
                    return 0.0;
                }
            }
        }
        return mergeWeightedAverage(criteriaList, scoreMap);
    }

    /**
     * 完成率：达标维度数 / 总维度数。
     *
     * @param criteriaList 所有评估维度
     * @param scoreMap     各维度归一化分数映射
     * @return 完成率（0.0 ~ 1.0）
     */
    private double mergeCompletionRate(List<RubricCriteria> criteriaList, Map<String, Double> scoreMap) {
        if (criteriaList.isEmpty()) return 0;
        long passCount = criteriaList.stream()
                .filter(c -> scoreMap.getOrDefault(c.getName(), 0.0) >= c.getPassRate())
                .count();
        return (double) passCount / criteriaList.size();
    }

    // ==================== 理由构建 ====================

    /**
     * 构建汇总理由，列出每个维度的分数和结论。
     *
     * @param criteriaList     所有评估维度
     * @param rawScores        各维度原始分数
     * @param normalizedScores 各维度归一化分数
     * @param reasons          各维度打分理由
     * @return 格式化的汇总字符串
     */
    private String buildReason(List<RubricCriteria> criteriaList,
                               Map<String, Double> rawScores,
                               Map<String, Double> normalizedScores,
                               Map<String, String> reasons) {
        return criteriaList.stream().map(c -> {
            double raw = rawScores.getOrDefault(c.getName(), 0.0);
            double norm = normalizedScores.getOrDefault(c.getName(), 0.0);
            String reason = reasons.getOrDefault(c.getName(), "");
            boolean passed = norm >= c.getPassRate();
            String base = String.format("[%s] %.1f/%.1f(%.0f%%) %s",
                    c.getName(), raw, c.getMaxScore(), norm * 100, passed ? "✓" : "✗");
            return StringUtils.isNotBlank(reason) ? base + ": " + reason : base;
        }).collect(Collectors.joining(" | "));
    }

    // ==================== 内部数据类 ====================

    /**
     * 单维度评估结果（内部传递用）。
     */
    private static class CriteriaEvalResult {
        final double rawScore;
        final double normalizedScore;
        final String reason;
        final String reasoning;

        CriteriaEvalResult(double rawScore, double normalizedScore, String reason, String reasoning) {
            this.rawScore = rawScore;
            this.normalizedScore = normalizedScore;
            this.reason = reason;
            this.reasoning = reasoning;
        }
    }

    /**
     * LLM 回复的 JSON 结构（CoT 格式：reasoning 先于 score）。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CriteriaLLMOutput {
        /**
         * 推理过程（Chain-of-Thought，先于 score 输出）
         */
        private String reasoning;
        /**
         * 最终分数
         */
        private Double score;
        /**
         * 一句话结论
         */
        private String reason;
    }
}
