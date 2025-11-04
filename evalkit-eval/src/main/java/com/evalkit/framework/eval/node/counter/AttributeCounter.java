package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.*;
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
    /* 归因大模型 */
    protected LLMService llmService;
    /* 线程池：调模型 IO 密集型，可配大点 */
    private final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4);

    public AttributeCounter(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * caseId 问题描述 定义
     */
    @Data
    @AllArgsConstructor
    public static class CaseInput {
        private Long caseId;
        private String description;
    }

    /**
     * 根据每个DataItem(即case)的evalResult的reason进行问题归因,归因口径是问题类型,将每个Case关联到对应的问题口径下
     * 1.dataItem抽取caseInput
     * 2.调用大模型依次处理每条Case,自发总结出问题,如果单Case包含多个问题用#分隔
     * 3.解析单Case问题,合并所有Case的问题
     * 4.上述步骤处理完成后,可能存在问题描述相似的情况,需要做二次合并,大模型将相似的问题合并成一个
     * 5.返回最终的归因结果
     */
    @Override
    protected CountResult count(List<DataItem> dataItems) {
        List<CaseInput> caseInputs = buildCaseInputs(dataItems);
        AttributeCountResult result = attribute(caseInputs);
        log.debug("Attribute {} cases into {}", caseInputs.size(), result);
        return result;
    }

    /**
     * 构建归因项
     */
    private List<CaseInput> buildCaseInputs(List<DataItem> dataItems) {
        List<CaseInput> caseInputs = new ArrayList<>();
        for (DataItem dataItem : dataItems) {
            EvalResult evalResult = dataItem.getEvalResult();
            // 仅筛选有不通过原因的用例
            if (evalResult != null && evalResult.isSuccess() && StringUtils.isNotEmpty(evalResult.getReason())) {
                CaseInput caseInput = new CaseInput(dataItem.getDataIndex(), dataItem.getEvalResult().getReason());
                caseInputs.add(caseInput);
            }
        }
        return caseInputs;
    }

    /**
     * 归因
     */
    public AttributeCountResult attribute(List<CaseInput> cases) {
        if (cases == null || cases.isEmpty()) {
            return new AttributeCountResult();
        }
        // 异步提取所有 (caseId , 问题类型) 对
        List<CompletableFuture<List<Pair<Long, String>>>> futures =
                cases.stream()
                        .map(c -> CompletableFuture.supplyAsync(() -> extract(c), pool))
                        .collect(Collectors.toList());
        // 并发归集到 Map<issueName, Set<caseId>>
        Map<String, Set<Long>> index = new ConcurrentHashMap<>();
        futures.forEach(f -> {
            try {
                f.get().forEach(p -> index
                        .computeIfAbsent(p.getValue(), k -> ConcurrentHashMap.newKeySet())
                        .add(p.getKey()));
            } catch (Exception e) {
                log.error("Attribute extract error", e);
            }
        });
        // 同义词合并归一（二次聚合）
        Map<String, Set<Long>> merged = normalize(index);
        // 组装最终结果
        AttributeCountResult result = new AttributeCountResult();
        merged.forEach((issue, caseSet) -> {
            Attribute attr = new Attribute();
            attr.setIssueName(issue);
            attr.setCaseIds(new ArrayList<>(caseSet));
            result.addAttribute(attr);
        });
        // 按 case 数量倒序, 频率高的问题排在前面
        result.getOverallAttribution().sort((a, b) -> Integer.compare(b.getCaseIds().size(), a.getCaseIds().size()));
        return result;
    }

    /**
     * 单条 Case 可能返回多个类型，逗号分隔
     */
    private List<Pair<Long, String>> extract(CaseInput c) {
        String prompt = "你是一名客服工单分析师，请用不超过20个字的短语精确概括下列用户问题；\n" + "如存在多种不同现象，请用中文'#'分隔，相同现象必须返回完全一致的关键词。\n" + "用户问题：" + c.getDescription() + "\n问题类型：";
        String reply = llmService.chat(prompt);
        if (StringUtils.isEmpty(reply)) {
            List<Pair<Long, String>> r = new ArrayList<>();
            r.add(Pair.of(c.getCaseId(), "未知问题"));
            return r;
        }
        /* 按中文逗号切分，去空、去重、截断 */
        return Arrays.stream(reply.split("#"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(s -> Pair.of(c.getCaseId(), s))
                .collect(Collectors.toList());
    }

    /**
     * 同义词合并：把第一次聚合的 Map<issueName, caseIdSet> 喂给大模型做归一化，
     * 返回 Map<标准词, caseIdSet>（已合并同类项）
     */
    private Map<String, Set<Long>> normalize(Map<String, Set<Long>> raw) {
        if (raw.isEmpty()) return raw;
        // 构造 prompt：只发一次模型请求
        String prompt = "以下是一份问题类型列表，请把含义相同或非常相近的短语合并成一个标准词，并返回纯 JSON，不要任何解释。\n" +
                "格式：{ \"标准词1\": [\"同义词A\",\"同义词B\"], \"标准词2\": [...] }\n" +
                "列表：" + String.join(",", raw.keySet());
        String reply = llmService.chat(prompt);
        if (StringUtils.isEmpty(reply)) {
            return raw;
        }
        // 去掉可能的 ```json 包裹
        reply = reply.replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "").trim();

        // 解析归一映射
        Map<String, List<String>> normMap;
        try {
            normMap = JsonUtils.fromJson(reply, new TypeReference<Map<String, List<String>>>() {
            });
        } catch (Exception e) {
            log.warn("Normalize json parse fail, return raw map", e);
            return raw;
        }
        // 按标准词二次合并
        Map<String, Set<Long>> merged = new LinkedHashMap<>();
        normMap.forEach((std, synList) -> {
            Set<Long> bucket = merged.computeIfAbsent(std, k -> new LinkedHashSet<>());
            synList.forEach(s -> bucket.addAll(raw.getOrDefault(s, new LinkedHashSet<>())));
        });
        return merged;
    }
}
