package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.attribute.v2.AttributeCountResultV2;
import com.evalkit.framework.eval.model.attribute.v2.Category;
import com.evalkit.framework.eval.model.attribute.v2.Issue;
import com.evalkit.framework.eval.model.attribute.v2.Sentiment;
import com.evalkit.framework.eval.node.counter.config.AttributeCounterV2Config;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 整体评测结果归因V2版本
 * <p>
 * 支持通过 {@link AttributeCounterV2Config} 传入完整配置（标准类别等可选项均有默认值）；
 * 也提供仅传 {@link LLMService} 的简便构造函数，其余选项全部使用默认值。
 * </p>
 * <p>并发调用统一使用框架 {@link BatchRunner} + {@link PoolName#ATTRIBUTE_COUNTER} 线程池，由 {@link com.evalkit.framework.common.thread.ThreadPoolManager} 统一管理。</p>
 */
@Slf4j
public class AttributeCounterV2 extends Counter {

    private static final double TOKEN_PER_CN_CHAR = 0.75;
    // "其他" 类别名，用于 LLM 调用失败时的降级兜底
    private static final String FALLBACK_CATEGORY = "其他";

    protected final LLMService llmService;
    /**
     * 标准类别枚举，用于约束 LLM 提取时的分类范围
     */
    protected final List<String> standardCategories;
    /**
     * 每批最大 token 数
     */
    private final int maxTokensPerChunk;
    /**
     * 摘要取样条数
     */
    private final int summarySampleSize;
    /**
     * 摘要最大字数（写入 Prompt）
     */
    private final int summaryMaxChars;
    /**
     * 是否跳过 normalizeCategories（已有枚举约束时）
     */
    private final boolean skipNormalizeCategoriesIfEnumConstrained;
    /**
     * BatchRunner 线程数（透传给 {@link BatchRunner#runBatch}）
     */
    private final int parallelism;
    /**
     * Prompt 固定头部的预估 token（懒计算，首次 chunkByToken 时写入）
     */
    private volatile int promptHeaderTokens = -1;

    /**
     * 简便构造：使用默认配置（所有可选项均取默认值）
     *
     * @param llmService LLM 服务
     */
    public AttributeCounterV2(LLMService llmService) {
        this(AttributeCounterV2Config.builder().llmService(llmService).build());
    }

    /**
     * 完整构造：通过 config 传入所有选项
     *
     * @param config 归因配置，{@link AttributeCounterV2Config#builder()} 构建，未设置的选项自动使用默认值
     */
    public AttributeCounterV2(AttributeCounterV2Config config) {
        if (config == null || config.getLlmService() == null) {
            throw new IllegalArgumentException("config and llmService must not be null");
        }
        if (config.getStandardCategories() == null || config.getStandardCategories().isEmpty()) {
            throw new IllegalArgumentException("standardCategories must not be empty");
        }
        this.llmService = config.getLlmService();
        this.standardCategories = Collections.unmodifiableList(new ArrayList<>(config.getStandardCategories()));
        this.maxTokensPerChunk = config.getMaxTokensPerChunk();
        this.summarySampleSize = config.getSummarySampleSize();
        this.summaryMaxChars = config.getSummaryMaxChars();
        this.skipNormalizeCategoriesIfEnumConstrained = config.isSkipNormalizeCategoriesIfEnumConstrained();
        this.parallelism = config.getParallelism();
        super.counterType = "attributeCounterV2";
    }

    // ==================== 内部数据类 ====================

    /**
     * 输入用例
     */
    @Data
    @AllArgsConstructor
    private static class CaseInput {
        private Long caseId;
        private String description;
    }

    /**
     * 提取结果
     */
    @AllArgsConstructor
    private static class Extracted {
        String category;
        String issue;
        double confidence;
        Sentiment sentiment;
        long caseId;
    }

    // ==================== 主流程 ====================

    @Override
    protected CountResult count(List<DataItem> dataItems) {
        List<CaseInput> inputs = buildInputs(dataItems);
        AttributeCountResultV2 result = attribute(inputs);
        log.info("[AttributeCounterV2] {} cases -> {} categories, {} issues",
                inputs.size(), result.getCategories().size(),
                result.getCategories().stream().mapToInt(c -> c.getIssues().size()).sum());
        return result;
    }

    private AttributeCountResultV2 attribute(List<CaseInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new AttributeCountResultV2();
        }

        // 批量并行提取 category + issue
        List<Extracted> extracted = extractBatch(inputs);

        // 聚合到临时 map：categoryName -> Category
        Map<String, Category> temp = new LinkedHashMap<>();
        extracted.forEach(e -> {
            Category cat = temp.computeIfAbsent(e.category, k -> {
                Category c = new Category();
                c.setCategoryName(k);
                c.setCategoryCode(toCode(k));
                return c;
            });
            Issue issue = new Issue();
            issue.setIssueName(e.issue);
            issue.setIssueCode(toCode(e.issue));
            issue.setConfidence(e.confidence);
            issue.setSentiment(e.sentiment);
            issue.getCaseIds().add(e.caseId);
            cat.getIssues().add(issue);
        });

        // ① 对 Category 名称做语义归一化（跨批次同义合并）
        // 当已使用枚举约束且配置了跳过时，省略这次 LLM 调用
        Map<String, Category> normalized;
        if (skipNormalizeCategoriesIfEnumConstrained) {
            normalized = temp;
            log.debug("[AttributeCounterV2] skipNormalizeCategoriesIfEnumConstrained=true, skip normalizeCategories");
        } else {
            normalized = normalizeCategories(temp);
        }

        // ② 同类别下 Issue 做语义归一化合并 + 代表摘要
        normalized.values().forEach(this::mergeAndSummarize);

        // 排序：category 按 unique caseId 数降序
        List<Category> categories = new ArrayList<>(normalized.values());
        categories.sort((a, b) -> Integer.compare(countUniqueCaseIds(b), countUniqueCaseIds(a)));
        categories.forEach(cat ->
                cat.getIssues().sort((a, b) -> {
                    int s = a.getSentiment().compareTo(b.getSentiment());
                    if (s != 0) return s;
                    int c = Double.compare(b.getConfidence(), a.getConfidence());
                    if (c != 0) return c;
                    return Integer.compare(b.getCaseIds().size(), a.getCaseIds().size());
                }));
        AttributeCountResultV2 result = new AttributeCountResultV2();
        result.setCategories(categories);
        return result;
    }

    /**
     * 统计 Category 下所有 Issue 中去重后的 caseId 总数（fix #9）
     */
    private int countUniqueCaseIds(Category cat) {
        return (int) cat.getIssues().stream()
                .flatMap(i -> i.getCaseIds().stream())
                .distinct()
                .count();
    }

    // ==================== 提取阶段 ====================

    /**
     * 批量并行提取：通过 {@link BatchRunner} 将各 chunk 并行提交到 {@link PoolName#ATTRIBUTE_COUNTER} 线程池。
     * <p>超时按每条用例 5 秒估算，最小 30 秒兜底。</p>
     */
    private List<Extracted> extractBatch(List<CaseInput> list) {
        List<List<CaseInput>> chunks = chunkByToken(list);
        log.info("[AttributeCounterV2] {} cases split into {} chunks", list.size(), chunks.size());

        List<List<Extracted>> results = BatchRunner.runBatch(
                chunks,
                this::extractChunk,
                PoolName.ATTRIBUTE_COUNTER,
                parallelism,
                size -> Math.max(30L, size * 5L)
        );

        if (results == null) {
            log.error("[AttributeCounterV2] extractBatch failed entirely, return empty");
            return Collections.emptyList();
        }
        List<Extracted> all = new ArrayList<>();
        results.forEach(all::addAll);
        return all;
    }

    /**
     * 单次 LLM 调用（片内）。
     * LLM 调用失败或返回空时，将该批用例全部降级归入 FALLBACK_CATEGORY（fix #3 / #4）。
     */
    private List<Extracted> extractChunk(List<CaseInput> chunk) {
        // 预建 caseId -> CaseInput 索引（fix #5: O(1) 查找替代 O(n)）
        Map<Long, CaseInput> idIndex = new HashMap<>(chunk.size() * 2);
        for (CaseInput c : chunk) {
            idIndex.put(c.getCaseId(), c);
        }

        String prompt = buildExtractPrompt(chunk);
        String reply;
        try {
            reply = llmService.chat(prompt);
        } catch (Exception e) {
            log.error("[AttributeCounterV2] LLM call failed in extractChunk, fallback {} cases to '{}'",
                    chunk.size(), FALLBACK_CATEGORY, e);
            return buildFallbackExtracted(chunk);
        }

        if (StringUtils.isEmpty(reply)) {
            log.warn("[AttributeCounterV2] LLM returned empty in extractChunk, fallback {} cases to '{}'",
                    chunk.size(), FALLBACK_CATEGORY);
            return buildFallbackExtracted(chunk);
        }

        return parseReply(reply, idIndex);
    }

    /**
     * 构造提取阶段 Prompt（fix #8: 用实际 Prompt 内容估算头部 token）
     */
    private String buildExtractPrompt(List<CaseInput> chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深客服分析经理，请逐条分析每条评测失败原因，给出【根因类别】+【具体问题】+【置信度0-1】+【情感极性NEG/NEUTRAL】。\n")
                .append("\n")
                .append("【分类要求】\n")
                .append("1. 根因类别必须从以下标准类别中选择，不要自创新类别：\n")
                .append("   ").append(String.join("、", standardCategories)).append("\n")
                .append("2. 具体问题描述要简洁精确，不超过20字，相同现象务必使用完全一致的表述\n")
                .append("3. 置信度为0.0~1.0的小数\n")
                .append("4. 情感极性：有明确错误或负面现象输出NEG，其余输出NEUTRAL\n")
                .append("\n")
                .append("注意: 输出编号必须和输入数据的编号对应，每条输入对应一行输出\n")
                .append("输出格式：编号|类别|问题|置信度|极性\n")
                .append("\n")
                .append("输入数据：\n");
        for (CaseInput caseInput : chunk) {
            sb.append(caseInput.caseId).append("|").append(caseInput.getDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * LLM 失败时的降级兜底：将整批用例归入 FALLBACK_CATEGORY，confidence=0，NEUTRAL（fix #4）
     */
    private List<Extracted> buildFallbackExtracted(List<CaseInput> chunk) {
        return chunk.stream()
                .map(c -> new Extracted(FALLBACK_CATEGORY, "解析失败", 0.0, Sentiment.NEUTRAL, c.getCaseId()))
                .collect(Collectors.toList());
    }

    /**
     * 按 token 数切分 list（贪心算法）。
     * 头部 token 使用真实 Prompt 头内容估算（fix #8），懒初始化避免反复计算。
     */
    private List<List<CaseInput>> chunkByToken(List<CaseInput> list) {
        if (promptHeaderTokens < 0) {
            // 用空 chunk 构造一次 prompt 估算固定头部 token
            promptHeaderTokens = estimateTokens(buildExtractPrompt(Collections.emptyList()));
        }
        List<List<CaseInput>> chunks = new ArrayList<>();
        List<CaseInput> current = new ArrayList<>();
        int currentTokens = promptHeaderTokens;
        for (CaseInput in : list) {
            int lineTokens = estimateTokens(in.getDescription()) + 10; // 编号+分隔符
            if (currentTokens + lineTokens > maxTokensPerChunk && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                currentTokens = promptHeaderTokens;
            }
            current.add(in);
            currentTokens += lineTokens;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    private int estimateTokens(String text) {
        return (int) (text.length() * TOKEN_PER_CN_CHAR) + text.split("\n").length;
    }

    // ==================== 解析阶段 ====================

    /**
     * 解析回复（fix #5: 使用预建 Map 而非 O(n) 遍历）
     */
    private List<Extracted> parseReply(String reply, Map<Long, CaseInput> idIndex) {
        List<Extracted> res = new ArrayList<>();
        for (String line : reply.split("\n")) {
            Extracted extracted = buildExtractedWithLLMReply(line, idIndex);
            if (extracted != null) {
                res.add(extracted);
            }
        }
        return res;
    }

    /**
     * 解析单行 LLM 输出（fix #5: 参数改为 Map 索引）
     */
    private Extracted buildExtractedWithLLMReply(String line, Map<Long, CaseInput> idIndex) {
        String[] arr = line.split("\\|", 5);
        if (arr.length < 5) {
            return null;
        }
        try {
            long idx = TypeConvertUtils.toLong(arr[0].trim());
            CaseInput in = idIndex.get(idx); // O(1) 查找（fix #5）
            if (in == null) {
                return null;
            }
            String category = arr[1].trim();
            String issue = arr[2].trim();
            Double confidence = TypeConvertUtils.toDouble(arr[3].trim());
            Sentiment sentiment = "NEG".equals(arr[4].trim()) ? Sentiment.NEG : Sentiment.NEUTRAL;
            return new Extracted(category, issue, confidence, sentiment, in.getCaseId());
        } catch (Exception e) {
            log.error("[AttributeCounterV2] buildExtractedWithLLMReply failed, error: {}", e.getMessage(), e);
            return null;
        }
    }

    // ==================== 归一化阶段 ====================

    /**
     * 对所有 Category 名称做语义归一化（fix #6: 补漏时先检查 stdName 是否已在 merged 中，避免 key 冲突）
     */
    private Map<String, Category> normalizeCategories(Map<String, Category> raw) {
        if (raw.size() <= 1) {
            return raw;
        }
        String categoryList = String.join("、", raw.keySet());
        String prompt = "以下是从多批用例中提取的【根因类别】列表，请把含义相同或高度相似的类别合并为统一标准名。\n"
                + "要求：\n"
                + "1. 优先使用列表中已有的词作为标准名\n"
                + "2. 标准名不超过10个字\n"
                + "3. 每个原始类别必须出现在且仅出现在一个标准名的列表中\n"
                + "4. 返回纯 JSON，不要任何解释\n"
                + "格式：{ \"标准名1\": [\"同义类别A\", \"同义类别B\"], \"标准名2\": [\"同义类别C\"] }\n"
                + "类别列表：" + categoryList;

        String reply = llmService.chat(prompt);
        if (StringUtils.isEmpty(reply)) {
            log.warn("[AttributeCounterV2] normalizeCategories LLM returned empty, skip");
            return raw;
        }
        reply = reply.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        try {
            Map<String, List<String>> normMap = JsonUtils.fromJson(reply,
                    new TypeReference<Map<String, List<String>>>() {
                    });

            // 构建 synonym -> stdName 反向索引，用于补漏时判断是否已被覆盖（fix #6）
            Map<String, String> synonymToStd = new HashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                if (synonyms != null) {
                    synonyms.forEach(syn -> synonymToStd.put(syn, stdName));
                }
            });

            Map<String, Category> merged = new LinkedHashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                Category stdCat = merged.computeIfAbsent(stdName, k -> {
                    Category c = new Category();
                    c.setCategoryName(k);
                    c.setCategoryCode(toCode(k));
                    return c;
                });
                if (synonyms != null) {
                    synonyms.forEach(syn -> {
                        Category synCat = raw.get(syn);
                        if (synCat != null) {
                            stdCat.getIssues().addAll(synCat.getIssues());
                        }
                    });
                }
            });

            // 补漏：原始中有但未出现在任何 synonyms 列表的 category，直接保留
            // fix #6: 使用 synonymToStd 精确判断，而非 normMap.values().flatMap，
            // 且先检查 merged 是否已存在该 key，存在则合并 issues 而非覆盖
            raw.forEach((name, cat) -> {
                if (!synonymToStd.containsKey(name)) {
                    merged.merge(name, cat, (existing, incoming) -> {
                        existing.getIssues().addAll(incoming.getIssues());
                        return existing;
                    });
                }
            });

            log.info("[AttributeCounterV2] normalizeCategories: {} -> {} categories",
                    raw.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[AttributeCounterV2] normalizeCategories json parse fail, use raw", e);
            return raw;
        }
    }

    // ==================== 合并 + 摘要阶段 ====================

    /**
     * 同类别下 Issue 语义归一化合并 + 代表摘要
     */
    private void mergeAndSummarize(Category cat) {
        List<Issue> issues = cat.getIssues();
        if (issues.isEmpty()) {
            return;
        }

        // Step 1：exact-merge 降噪
        Map<String, Issue> exactMap = new LinkedHashMap<>();
        issues.forEach(issue -> {
            Issue exist = exactMap.computeIfAbsent(issue.getIssueName(), k -> issue);
            if (exist != issue) {
                exist.getCaseIds().addAll(issue.getCaseIds());
                exist.setConfidence(Math.max(exist.getConfidence(), issue.getConfidence()));
                if (issue.getSentiment() == Sentiment.NEG) {
                    exist.setSentiment(Sentiment.NEG);
                }
            }
        });

        // Step 2：LLM 语义归一化（fix #6: normalizeIssues 同样修复 key 冲突）
        Map<String, Issue> normalizedMap = normalizeIssues(exactMap, cat.getCategoryName());
        cat.setIssues(new ArrayList<>(normalizedMap.values()));

        // Step 3：为每个 issue 生成代表摘要，通过 BatchRunner 并行调用
        // 超时按每条 issue 10 秒估算，最小 30 秒兜底
        BatchRunner.runBatch(
                cat.getIssues(),
                issue -> {
                    List<String> descList = issue.getCaseIds().stream()
                            .limit(summarySampleSize)
                            .map(descriptionCache::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    if (!descList.isEmpty()) {
                        String prompt = "请用" + summaryMaxChars + "字以内概括下列用户问题的共同现象：\n"
                                + String.join("\n", descList);
                        try {
                            issue.setRepresentative(llmService.chat(prompt));
                        } catch (Exception e) {
                            log.warn("[AttributeCounterV2] summarize issue='{}' failed: {}",
                                    issue.getIssueName(), e.getMessage());
                        }
                    }
                    return issue;
                },
                PoolName.ATTRIBUTE_COUNTER,
                parallelism,
                size -> Math.max(30L, size * 10L)
        );
    }

    /**
     * 对同类别下 Issue 列表做语义归一化（fix #6: 修复补漏时 key 冲突）
     */
    private Map<String, Issue> normalizeIssues(Map<String, Issue> issueMap, String categoryName) {
        if (issueMap.size() <= 1) {
            return issueMap;
        }
        String issueList = String.join("、", issueMap.keySet());
        String prompt = "以下是【" + categoryName + "】类别下的具体问题列表，请把含义相同或高度相似的问题合并为统一标准描述。\n"
                + "要求：\n"
                + "1. 标准描述不超过20字，优先使用列表中已有的表述\n"
                + "2. 只合并真正相似的问题，保留语义不同的问题\n"
                + "3. 每个原始问题必须出现在且仅出现在一个标准问题的列表中\n"
                + "4. 返回纯 JSON，不要任何解释\n"
                + "格式：{ \"标准问题1\": [\"同义问题A\", \"同义问题B\"], \"标准问题2\": [\"同义问题C\"] }\n"
                + "问题列表：" + issueList;

        String reply = llmService.chat(prompt);
        if (StringUtils.isEmpty(reply)) {
            log.warn("[AttributeCounterV2] normalizeIssues LLM returned empty for category={}, skip", categoryName);
            return issueMap;
        }
        reply = reply.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        try {
            Map<String, List<String>> normMap = JsonUtils.fromJson(reply,
                    new TypeReference<Map<String, List<String>>>() {
                    });

            // 反向索引（fix #6）
            Map<String, String> synonymToStd = new HashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                if (synonyms != null) {
                    synonyms.forEach(syn -> synonymToStd.put(syn, stdName));
                }
            });

            Map<String, Issue> merged = new LinkedHashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                Issue stdIssue = merged.computeIfAbsent(stdName, k -> {
                    Issue i = new Issue();
                    i.setIssueName(k);
                    i.setIssueCode(toCode(k));
                    i.setConfidence(0.0);
                    i.setSentiment(Sentiment.NEUTRAL);
                    return i;
                });
                if (synonyms != null) {
                    synonyms.forEach(syn -> {
                        Issue synIssue = issueMap.get(syn);
                        if (synIssue != null) {
                            stdIssue.getCaseIds().addAll(synIssue.getCaseIds());
                            if (synIssue.getConfidence() > stdIssue.getConfidence()) {
                                stdIssue.setConfidence(synIssue.getConfidence());
                            }
                            if (synIssue.getSentiment() == Sentiment.NEG) {
                                stdIssue.setSentiment(Sentiment.NEG);
                            }
                        }
                    });
                }
            });

            // 补漏（fix #6: 使用 synonymToStd 判断，并用 merge 而非 put 防覆盖）
            issueMap.forEach((name, issue) -> {
                if (!synonymToStd.containsKey(name)) {
                    merged.merge(name, issue, (existing, incoming) -> {
                        existing.getCaseIds().addAll(incoming.getCaseIds());
                        if (incoming.getConfidence() > existing.getConfidence()) {
                            existing.setConfidence(incoming.getConfidence());
                        }
                        if (incoming.getSentiment() == Sentiment.NEG) {
                            existing.setSentiment(Sentiment.NEG);
                        }
                        return existing;
                    });
                }
            });

            log.info("[AttributeCounterV2] normalizeIssues [{}]: {} -> {} issues",
                    categoryName, issueMap.size(), merged.size());
            return merged;
        } catch (Exception e) {
            log.warn("[AttributeCounterV2] normalizeIssues json parse fail for category={}, use raw", categoryName, e);
            return issueMap;
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 中文转 code：使用 Unicode 码点拼接，确保中文不被直接过滤（fix #10）。
     * 格式：u{codepoint} 拼接，非字母数字的 ASCII 字符替换为下划线。
     */
    private static String toCode(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if ((cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9')) {
                sb.append((char) cp);
            } else if (cp > 127) {
                // 中文及其他非 ASCII 字符用 uXXXX 表示
                sb.append("u").append(Integer.toHexString(cp));
            } else {
                // 其他 ASCII 特殊字符替换为下划线，连续下划线只保留一个
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
            }
            i += Character.charCount(cp);
        }
        // 去除首尾下划线
        String result = sb.toString().toLowerCase();
        while (result.startsWith("_")) result = result.substring(1);
        while (result.endsWith("_")) result = result.substring(0, result.length() - 1);
        return result;
    }

    /**
     * 根据 dataItem 构造归因语料，过滤评估理由为空的用例
     */
    private List<CaseInput> buildInputs(List<DataItem> dataItems) {
        List<CaseInput> list = new ArrayList<>();
        for (DataItem item : dataItems) {
            EvalResult er = item.getEvalResult();
            if (er != null && StringUtils.isNotEmpty(er.getReason())) {
                list.add(new CaseInput(item.getDataIndex(), er.getReason()));
                descriptionCache.put(item.getDataIndex(), er.getReason());
            }
        }
        return list;
    }

    /* 本地缓存：caseId -> description，用于生成代表摘要 */
    private final Map<Long, String> descriptionCache = new ConcurrentHashMap<>();
}
