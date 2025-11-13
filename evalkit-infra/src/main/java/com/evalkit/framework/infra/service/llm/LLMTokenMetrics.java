package com.evalkit.framework.infra.service.llm;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class LLMTokenMetrics {
    /* 模型集合 */
    public static final Set<String> MODELS = new HashSet<>();
    /* 总输入token */
    public static final Map<String, AtomicLong> TOTAL_INPUT_TOKENS = new LinkedHashMap<>();
    /* 总输出token */
    public static final Map<String, AtomicLong> TOTAL_OUTPUT_TOKENS = new LinkedHashMap<>();
    /* 总输入花费 */
    public static final Map<String, DoubleAdder> TOTAL_IN_PRICE = new LinkedHashMap<>();
    /* 总输出花费 */
    public static final Map<String, DoubleAdder> TOTAL_OUT_PRICE = new LinkedHashMap<>();

    /**
     * 单次调用上报
     */
    public static void record(String model, long inTokens, long outTokens, double inPrice, double outPrice) {
        MODELS.add(model);
        TOTAL_INPUT_TOKENS.computeIfAbsent(model, k -> new AtomicLong()).addAndGet(inTokens);
        TOTAL_OUTPUT_TOKENS.computeIfAbsent(model, k -> new AtomicLong()).addAndGet(outTokens);
        TOTAL_IN_PRICE.computeIfAbsent(model, k -> new DoubleAdder()).add(inPrice);
        TOTAL_OUT_PRICE.computeIfAbsent(model, k -> new DoubleAdder()).add(outPrice);
    }

    /**
     * 人类可读报表
     */
    public static String report() {
        StringBuilder sb = new StringBuilder();
        for (String model : MODELS) {
            long inToken = TOTAL_INPUT_TOKENS.get(model).get();
            long outToken = TOTAL_OUTPUT_TOKENS.get(model).get();
            long totalToken = inToken + outToken;
            double inPrice = TOTAL_IN_PRICE.get(model).sum();
            double outPrice = TOTAL_OUT_PRICE.get(model).sum();
            double totalPrice = inPrice + outPrice;
            String cur = String.format("Model=%s : TotalToken=%d tokens (InputToken=%d tokens | OutputToken=%d tokens) | TotalPrice=%.6f (InputPrice=%.6f | OutputPrice=%.6f)",
                    model, totalToken, inToken, outToken, totalPrice, inPrice, outPrice);
            sb.append(cur).append("\n");
        }
        return sb.toString();
    }

    private LLMTokenMetrics() {
    }
}
