package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.attribute.v1.Attribute;
import com.evalkit.framework.eval.model.attribute.v1.AttributeCountResult;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 整体评测结果归因
 */
@Slf4j
public class AttributeCounter extends Counter {
    private static final double TOKEN_PER_CN_CHAR = 0.75;
    // 8k 模型留余量
    private static final int MAX_TOKENS_PER_CHUNK = 6000;

    protected final LLMService llmService;
    private final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    public AttributeCounter(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    protected CountResult count(List<DataItem> dataItems) {
        List<CaseInput> caseInputs = buildCaseInputs(dataItems);
        AttributeCountResult result = attribute(caseInputs);
        log.debug("Attribute {} cases into {}", caseInputs.size(), result);
        return result;
    }

    /**
     * 主流程
     *
     * @param cases 输入用例
     * @return 输出结果
     */
    public AttributeCountResult attribute(List<CaseInput> cases) {
        if (cases == null || cases.isEmpty()) {
            return new AttributeCountResult();
        }
        // 分片 + 流式提取（支持大数据量）
        List<Pair<Long, String>> pairs = extractBatch(cases);
        // 聚合到 Map<issue, Set<caseId>>
        Map<String, Set<Long>> index = new ConcurrentHashMap<>();
        pairs.forEach(p -> index
                .computeIfAbsent(p.getValue(), k -> ConcurrentHashMap.newKeySet())
                .add(p.getKey()));
        // 同义词合并（保持原逻辑）
        Map<String, Set<Long>> merged = normalize(index);
        // 组装 & 排序
        AttributeCountResult result = new AttributeCountResult();
        merged.forEach((issue, caseSet) -> {
            Attribute attr = new Attribute();
            attr.setIssueName(issue);
            attr.setCaseIds(new ArrayList<>(caseSet));
            result.addAttribute(attr);
        });
        result.getOverallAttribution()
                .sort((a, b) -> Integer.compare(b.getCaseIds().size(), a.getCaseIds().size()));
        return result;
    }

    /**
     * 分片 + 流式聚合
     *
     * @param cases 输入用例
     * @return 输出结果
     */
    private List<Pair<Long, String>> extractBatch(List<CaseInput> cases) {
        // 按 token 分片
        List<List<CaseInput>> chunks = chunkByToken(cases);
        log.info("Large input split into {} chunks", chunks.size());

        // 片内并行，片间顺序聚合（避免 QPS 打满）
        List<Pair<Long, String>> all = new ArrayList<>();
        for (List<CaseInput> chunk : chunks) {
            List<Pair<Long, String>> one = CompletableFuture
                    .supplyAsync(() -> extractChunk(chunk), pool)
                    .join();
            all.addAll(one);
        }
        return all;
    }

    /**
     * 单片内单次 LLM 调用
     *
     * @param chunk 输入用例
     * @return 输出结果
     */
    private List<Pair<Long, String>> extractChunk(List<CaseInput> chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名客服工单分析师，请逐条给出【问题类型】；\n")
                .append("如存在多种现象，用中文'#'分隔；相同现象返回完全一致的关键词。\n")
                .append("注意: 输出编号必须和输入数据的编号对应\n")
                .append("注意: 问题类型描述不超过20字\n")
                .append("输出格式：编号|问题类型\n");
        for (CaseInput caseInput : chunk) {
            sb.append(caseInput.caseId).append("|").append(caseInput.getDescription()).append("\n");
        }

        String reply = llmService.chat(sb.toString());
        if (StringUtils.isEmpty(reply)) {
            return chunk.stream()
                    .map(c -> Pair.of(c.getCaseId(), "未知问题"))
                    .collect(Collectors.toList());
        }

        /* 解析：每行 -> 编号|问题类型[#类型2] */
        List<Pair<Long, String>> res = new ArrayList<>();
        for (String line : reply.split("\n")) {
            String[] arr = line.split("\\|", 2);
            if (arr.length < 2) continue;
            int idx = TypeConvertUtils.toInteger(arr[0].trim());
            if (idx >= chunk.size()) {
                throw new RuntimeException("Extract chunk failed for index error, index: " + idx + ", chunk size: " + chunk.size());
            }
            CaseInput in = chunk.get(idx);
            for (String issue : arr[1].split("#")) {
                issue = issue.trim();
                if (!issue.isEmpty()) {
                    res.add(Pair.of(in.getCaseId(), issue));
                }
            }
        }
        return res;
    }

    /**
     * 按 token 贪心分片
     *
     * @param list 输入用例
     * @return 输出结果
     */
    private List<List<CaseInput>> chunkByToken(List<CaseInput> list) {
        List<List<CaseInput>> chunks = new ArrayList<>();
        List<CaseInput> current = new ArrayList<>();
        int currentTokens = estimateTokens("固定 prompt 头部");
        for (CaseInput in : list) {
            int lineTokens = estimateTokens(in.getDescription()) + 10;
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

    private int estimateTokens(String text) {
        return (int) (text.length() * TOKEN_PER_CN_CHAR) + text.split("\n").length;
    }

    /* -------------------- 原逻辑保持不动 -------------------- */
    private List<CaseInput> buildCaseInputs(List<DataItem> dataItems) {
        List<CaseInput> list = new ArrayList<>();
        for (DataItem item : dataItems) {
            EvalResult er = item.getEvalResult();
            if (er != null && StringUtils.isNotEmpty(er.getReason())) {
                list.add(new CaseInput(item.getDataIndex(), er.getReason()));
            }
        }
        return list;
    }

    /**
     * 同义词合并
     *
     * @param raw 原始结果
     * @return 合并后结果
     */
    private Map<String, Set<Long>> normalize(Map<String, Set<Long>> raw) {
        if (raw.isEmpty()) return raw;
        String prompt = "以下是一份问题类型列表，请把含义相同或非常相近的短语合并成一个标准词，并返回纯 JSON，不要任何解释。\n" +
                "格式：{ \"标准词1\": [\"同义词A\",\"同义词B\"], \"标准词2\": [...] }\n" +
                "列表：" + String.join(",", raw.keySet());
        String reply = llmService.chat(prompt);
        if (StringUtils.isEmpty(reply)) {
            return raw;
        }
        reply = reply.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        try {
            Map<String, List<String>> normMap = JsonUtils.fromJson(reply, new TypeReference<Map<String, List<String>>>() {
            });
            Map<String, Set<Long>> merged = new LinkedHashMap<>();
            normMap.forEach((std, synList) -> {
                Set<Long> bucket = merged.computeIfAbsent(std, k -> new LinkedHashSet<>());
                synList.forEach(s -> bucket.addAll(raw.getOrDefault(s, new LinkedHashSet<>())));
            });
            return merged;
        } catch (Exception e) {
            log.warn("Normalize json parse fail, return raw map", e);
            return raw;
        }
    }

    @Data
    @AllArgsConstructor
    public static class CaseInput {
        private Long caseId;
        private String description;
    }
}