package com.evalkit.framework.common.utils.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * LLM Token工具类
 */
public class LLMTokenUtils {

    private static final double CHINESE_FACTOR = 1.2;   // 每汉字
    private static final double ENGLISH_FACTOR = 0.25;  // 每字母
    private static final double MIX_FACTOR = 4.0;   // 每 token 约 4 字符

    public static final class Model {
        final BigDecimal inPrice;   // 每 1M 输入
        final BigDecimal outPrice;  // 每 1M 输出

        Model(double in, double out) {
            this.inPrice = BigDecimal.valueOf(in);
            this.outPrice = BigDecimal.valueOf(out);
        }
    }

    private LLMTokenUtils() {
    }

    /**
     * 估算文本的 token 数量
     *
     * @param text 待估算的文本
     * @return 估算的 token 数量
     */
    public static long tokenCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (isHan(c)) chinese++;
            else if (isAscii(c)) other++;
        }
        long cn = Math.round(chinese * CHINESE_FACTOR);
        long en = Math.round(other * ENGLISH_FACTOR);
        return Math.max(cn + en, Math.round(text.length() / MIX_FACTOR));
    }

    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    private static boolean isAscii(char c) {
        return c < 128;
    }

    /**
     * 计算文本的token费用
     *
     * @param text  待估算文本
     * @param price 每百万token价格
     * @return 价格
     */
    public static double calTokenPrice(String text, double price) {
        long token = tokenCount(text);
        // 计算单token费用
        double tokenPrice = BigDecimal.valueOf(price).divide(BigDecimal.valueOf(1000000), 10, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(token).multiply(BigDecimal.valueOf(tokenPrice)).doubleValue();
    }
}
