package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.attribute.v2.AttributeCountResultV2;
import com.evalkit.framework.eval.model.attribute.v2.Category;
import com.evalkit.framework.eval.model.attribute.v2.Issue;
import com.evalkit.framework.eval.model.attribute.v2.Sentiment;
import com.evalkit.framework.infra.service.llm.LLMService;
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
 */
@Slf4j
public class AttributeCounterV2 extends Counter {
    // 8k 模型留余量
    private static final int MAX_TOKENS_PER_CHUNK = 6000;
    private static final double TOKEN_PER_CN_CHAR = 0.75;
    protected final LLMService llmService;
    protected final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    public AttributeCounterV2(LLMService llmService) {
        this.llmService = llmService;
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

        // 聚合 & 置信度过滤
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

        // 同类别下二次合并 + 代表摘要
        temp.values().forEach(this::mergeAndSummarize);

        // 排序：负向 > 中性，置信度 > 频次
        List<Category> categories = new ArrayList<>(temp.values());
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
     */
    private List<Extracted> extractChunk(List<CaseInput> chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名资深客服分析经理，请逐条给出【根因类别】+【具体问题】+【置信度0-1】+【情感极性NEG/NEUTRAL】。\n")
                .append("注意: 输出编号必须和输入数据的编号对应\n")
                .append("注意: 根因类别描述不超过10个字, 具体问题描述不超过30字\n")
                .append("输出格式：编号|类别|问题|置信度|极性\n");
        for (CaseInput caseInput : chunk) {
            sb.append(caseInput.caseId).append("|").append(caseInput.getDescription()).append("\n");
        }
        String reply = llmService.chat(sb.toString());
        return parseReply(reply, chunk);
    }

    /**
     * 按 token 数切分 list（贪心算法）
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
     */
    private int estimateTokens(String text) {
        return (int) (text.length() * TOKEN_PER_CN_CHAR) + text.split("\n").length;
    }

    /**
     * 解析回复
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
     * 解析大模型结果构建Extracted
     */
    private Extracted buildExtractedWithLLMReply(String line, List<CaseInput> chunk) {
        String[] arr = line.split("\\|", 5);
        if (arr.length < 5) {
            return null;
        }
        try {
            int idx = TypeConvertUtils.toInteger(arr[0].trim());
            if (idx >= chunk.size()) {
                throw new RuntimeException("Extract chunk failed for index error, index: " + idx + ", chunk size: " + chunk.size());
            }
            CaseInput in = chunk.get(idx);
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
     * 同类合并 + 代表摘要
     *
     * @param cat 类别
     */
    private void mergeAndSummarize(Category cat) {
        // 简单按名称 exact 合并（已归一过）
        Map<String, Issue> map = new LinkedHashMap<>();
        cat.getIssues().forEach(issue -> {
            Issue exist = map.computeIfAbsent(issue.getIssueName(), k -> issue);
            if (exist != issue) {
                exist.getCaseIds().addAll(issue.getCaseIds());
                exist.setConfidence(Math.max(exist.getConfidence(), issue.getConfidence()));
            }
        });
        cat.setIssues(new ArrayList<>(map.values()));

        // 为每个 issue 生成 50 字代表性描述
        cat.getIssues().parallelStream().forEach(issue -> {
            List<String> descList = issue.getCaseIds().stream()
                    .limit(5)   // 取 5 条即可
                    .map(descriptionCache::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            String prompt = "请用50字以内概括下列用户问题的共同现象：\n"
                    + String.join("\n", descList);
            issue.setRepresentative(llmService.chat(prompt));
        });
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
