package com.evalkit.framework.eval.node.counter;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 整体评测结果归因V2版本
 * <p>
 * 支持通过 {@link AttributeCounterV2Config} 传入完整配置（标准类别等可选项均有默认值）；
 * 也提供仅传 {@link LLMService} 的简便构造函数，其余选项全部使用默认值。
 * </p>
 */
@Slf4j
public class AttributeCounterV2 extends Counter {
    // 8k 模型留余量
    private static final int MAX_TOKENS_PER_CHUNK = 6000;
    private static final double TOKEN_PER_CN_CHAR = 0.75;

    protected final LLMService llmService;

    /**
     * 标准类别枚举，用于约束 LLM 提取时的分类范围
     */
    protected final List<String> standardCategories;

    protected final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    /**
     * 简便构造：使用默认配置（标准类别等均取默认值）
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
        super.counterType = "attributeCounterV2";
    }

    /**
     * 输入用例
     */
    @Data
    @AllArgsConstructor
    private static class CaseInput {
        private Long caseId;
        private String description;
    }

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

        // 批量提取 category + issue
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
        Map<String, Category> normalized = normalizeCategories(temp);

        // ② 同类别下 Issue 做语义归一化合并 + 代表摘要
        normalized.values().forEach(this::mergeAndSummarize);

        // 排序：负向 > 中性，置信度 > 频次
        List<Category> categories = new ArrayList<>(normalized.values());
        // category 按问题数降序
        categories.sort((a, b) -> Integer.compare(b.getIssues().size(), a.getIssues().size()));
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
     * 对所有 Category 名称做语义归一化：调用 LLM 把相似类别合并为统一标准名，
     * 然后把被合并类别的 Issue 全部迁移到标准类别下。
     *
     * @param raw 原始 categoryName -> Category 映射
     * @return 归一化后的映射（key 已更新为标准名）
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
                + "3. 返回纯 JSON，不要任何解释\n"
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
            Map<String, Category> merged = new LinkedHashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                // 创建或获取标准类别
                Category stdCat = merged.computeIfAbsent(stdName, k -> {
                    Category c = new Category();
                    c.setCategoryName(k);
                    c.setCategoryCode(toCode(k));
                    return c;
                });
                // 把每个同义类别下的 issue 合并进来
                synonyms.forEach(syn -> {
                    Category synCat = raw.get(syn);
                    if (synCat != null) {
                        stdCat.getIssues().addAll(synCat.getIssues());
                    }
                });
            });
            // 补漏：原始中有但未出现在 normMap 值列表的 category 直接保留
            Set<String> covered = normMap.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            raw.forEach((name, cat) -> {
                if (!covered.contains(name)) {
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

    /**
     * 批量提取Extracted
     *
     * @param list 输入用例列表
     * @return 提取结果
     */
    private List<Extracted> extractBatch(List<CaseInput> list) {
        List<Extracted> all = new ArrayList<>();
        // 按 token 切片
        List<List<CaseInput>> chunks = chunkByToken(list);
        log.info("Large input split into {} chunks", chunks.size());

        // 片内异步，片间顺序
        for (List<CaseInput> chunk : chunks) {
            List<Extracted> chunkRes = CompletableFuture
                    .supplyAsync(() -> extractChunk(chunk), pool)
                    .join();   // 顺序聚合，避免 QPS 打满
            all.addAll(chunkRes);
        }
        return all;
    }

    /**
     * 真正单次 LLM 调用（片内）
     * <p>Prompt 约束：给出有限的标准大类，减少同义词分散问题</p>
     */
    private List<Extracted> extractChunk(List<CaseInput> chunk) {
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
        String reply = llmService.chat(sb.toString());
        return parseReply(reply, chunk);
    }

    /**
     * 按 token 数切分 list（贪心算法）
     *
     * @param list 输入用例列表
     * @return 切分结果
     */
    private List<List<CaseInput>> chunkByToken(List<CaseInput> list) {
        List<List<CaseInput>> chunks = new ArrayList<>();
        List<CaseInput> current = new ArrayList<>();
        int currentTokens = estimateTokens("固定 prompt 头部"); // 初始开销
        for (CaseInput in : list) {
            int lineTokens = estimateTokens(in.getDescription()) + 10; // 编号+分隔符
            if (currentTokens + lineTokens > MAX_TOKENS_PER_CHUNK && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                currentTokens = estimateTokens("固定 prompt 头部");
            }
            current.add(in);
            currentTokens += lineTokens;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    /**
     * 估算 token 数（含分隔符）
     *
     * @param text 文本
     * @return token 数
     */
    private int estimateTokens(String text) {
        return (int) (text.length() * TOKEN_PER_CN_CHAR) + text.split("\n").length;
    }

    /**
     * 解析回复
     *
     * @param reply 大模型回复
     * @param chunk 输入用例列表
     * @return 解析的Extracted结果列表
     */
    private List<Extracted> parseReply(String reply, List<CaseInput> chunk) {
        List<Extracted> res = new ArrayList<>();
        if (StringUtils.isEmpty(reply)) return res;
        String[] lines = reply.split("\n");
        for (String line : lines) {
            // 解析过程中可能因为格式问题报错返回null,此时跳过
            Extracted extracted = buildExtractedWithLLMReply(line, chunk);
            if (extracted != null) {
                res.add(extracted);
            }
        }
        return res;
    }

    /**
     * 解析大模型结果构建Extracted,找不到构建结果时返回null
     *
     * @param line  大模型回复行
     * @param chunk 输入用例列表
     * @return 构建的Extracted结果
     */
    private Extracted buildExtractedWithLLMReply(String line, List<CaseInput> chunk) {
        String[] arr = line.split("\\|", 5);
        if (arr.length < 5) {
            return null;
        }
        try {
            long idx = TypeConvertUtils.toLong(arr[0].trim());
            // 根据idx在chunk找到实际的CaseInput
            CaseInput in = findCaseInputByCaseId(idx, chunk);
            if (in == null) {
                return null;
            }
            String category = arr[1].trim();
            String issue = arr[2].trim();
            Double confidence = TypeConvertUtils.toDouble(arr[3].trim());
            Sentiment sentiment = "NEG".equals(arr[4]) ? Sentiment.NEG : Sentiment.NEUTRAL;
            Long caseId = in.getCaseId();
            return new Extracted(category, issue, confidence, sentiment, caseId);
        } catch (Exception e) {
            log.error("Build extracted with LLM reply failed, error: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据caseId在chunk中找到对应的CaseInput
     *
     * @param caseId 用例id
     * @param chunk  输入用例列表
     * @return 对应的CaseInput
     */
    private CaseInput findCaseInputByCaseId(Long caseId, List<CaseInput> chunk) {
        for (CaseInput in : chunk) {
            if (in.getCaseId().equals(caseId)) {
                return in;
            }
        }
        return null;
    }


    /**
     * 同类别下 Issue 语义归一化合并 + 代表摘要
     * <p>
     * 先用 LLM 把该 Category 下语义相近的 Issue 合并为标准问题名，
     * 再 exact-merge caseIds，最后为每个 issue 生成代表摘要。
     * </p>
     *
     * @param cat 类别
     */
    private void mergeAndSummarize(Category cat) {
        List<Issue> issues = cat.getIssues();
        if (issues.isEmpty()) {
            return;
        }

        // Step 1：先做一次 exact-merge 降噪
        Map<String, Issue> exactMap = new LinkedHashMap<>();
        issues.forEach(issue -> {
            Issue exist = exactMap.computeIfAbsent(issue.getIssueName(), k -> issue);
            if (exist != issue) {
                exist.getCaseIds().addAll(issue.getCaseIds());
                exist.setConfidence(Math.max(exist.getConfidence(), issue.getConfidence()));
                // 负面情绪优先
                if (issue.getSentiment() == Sentiment.NEG) {
                    exist.setSentiment(Sentiment.NEG);
                }
            }
        });

        // Step 2：若 issue 数 > 1，调用 LLM 做语义归一化
        Map<String, Issue> normalizedMap = normalizeIssues(exactMap, cat.getCategoryName());

        cat.setIssues(new ArrayList<>(normalizedMap.values()));

        // Step 3：为每个 issue 生成 50 字代表性描述
        cat.getIssues().parallelStream().forEach(issue -> {
            List<String> descList = issue.getCaseIds().stream()
                    .limit(5)
                    .map(descriptionCache::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (descList.isEmpty()) {
                return;
            }
            String prompt = "请用50字以内概括下列用户问题的共同现象：\n"
                    + String.join("\n", descList);
            issue.setRepresentative(llmService.chat(prompt));
        });
    }

    /**
     * 对同类别下的 Issue 列表做语义归一化：调用 LLM 合并含义相近的问题描述。
     *
     * @param issueMap     exact-merge 后的 issueName -> Issue 映射
     * @param categoryName 所属类别名（上下文提示用）
     * @return 语义归一化后的映射
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
                + "3. 返回纯 JSON，不要任何解释\n"
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
            Map<String, Issue> merged = new LinkedHashMap<>();
            normMap.forEach((stdName, synonyms) -> {
                // 收集所有同义 issue 中置信度最高的作为代表
                Issue stdIssue = merged.computeIfAbsent(stdName, k -> {
                    Issue i = new Issue();
                    i.setIssueName(k);
                    i.setIssueCode(toCode(k));
                    i.setConfidence(0.0);
                    i.setSentiment(Sentiment.NEUTRAL);
                    return i;
                });
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
            });
            // 补漏：原始中有但未被 normMap 覆盖的 issue 直接保留
            Set<String> covered = normMap.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            issueMap.forEach((name, issue) -> {
                if (!covered.contains(name)) {
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

    /* ----- 工具 ----- */
    private static String toCode(String chinese) {
        return chinese.replaceAll("\\s+", "_")
                .replaceAll("[^\\w_]", "")
                .toLowerCase();
    }

    /**
     * 根据dataItem构造归因语料,过滤评估理由为空的用例
     *
     * @param dataItems 原数据
     * @return 归因语料
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
