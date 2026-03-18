package com.evalkit.framework.eval.mock.mocker;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 数字Mock
 * {{int}} 随机整数
 * {{float}} 随机小数
 * <p>
 * {{int 10}} 大于某整数的随机整数
 * {{int 10 20}} 在某范围内随机整数
 * <p>
 * {{float 10}} 大于某数的随机小数
 * {{float 10 20}} 在某范围内随机小数
 */
public class NumberMocker implements Mocker {
    /* 规则 -> 策略缓存（线程安全） */
    private static final Map<String, NumberStrategy> STRATEGY_POOL = new ConcurrentHashMap<>();

    static {
        STRATEGY_POOL.put("int", new IntegerStrategy());
        STRATEGY_POOL.put("float", new FloatStrategy());
    }

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return StringUtils.containsIgnoreCase(ruleName, "int") || StringUtils.containsIgnoreCase(ruleName, "float");
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        NumberStrategy strategy = STRATEGY_POOL.get(StringUtils.lowerCase(ruleName));
        if (strategy == null) {
            return null;
        }
        NumberContext ctx = new NumberContext(ruleParams);
        return strategy.generate(ctx);
    }

    /**
     * 数字参数上下文:
     * None  没有参数,使用默认值
     * [最小值]   第1位是最小值,使用默认最大值
     * [最小值] [最大值]  第1位是最小值,第2位是最大值
     */
    @Data
    private static class NumberContext {
        /* 默认最小整数 */
        private final static int DEFAULT_MIN_INT = 0;
        /* 默认最大整数 */
        private final static int DEFAULT_MAX_INT = 100;
        /* 默认最小小数 */
        private final static double DEFAULT_MIN_FLOAT = 0.0;
        /* 默认最大小数 */
        private final static double DEFAULT_MAX_FLOAT = 100.0;
        /* 最小值 */
        private double minValue;
        /* 最大值 */
        private double maxValue;
        /* 是否为整数类型 */
        private boolean isInteger;

        public NumberContext(List<String> args) {
            updateParams(args);
        }

        /**
         * 解析参数
         */
        public void updateParams(List<String> args) {
            try {
                if (CollectionUtils.isEmpty(args)) {
                    // None 没有参数,使用默认值
                    this.minValue = DEFAULT_MIN_INT;
                    this.maxValue = DEFAULT_MAX_INT;
                    this.isInteger = true;
                } else if (args.size() == 1) {
                    // [最小值] 第1位是最小值,使用默认最大值
                    double min = Double.parseDouble(args.get(0));
                    this.minValue = min;
                    // 判断是否为整数
                    if (min == (long) min) {
                        this.maxValue = DEFAULT_MAX_INT;
                        this.isInteger = true;
                    } else {
                        this.maxValue = DEFAULT_MAX_FLOAT;
                        this.isInteger = false;
                    }
                } else if (args.size() == 2) {
                    // [最小值] [最大值] 第1位是最小值,第2位是最大值
                    double min = Double.parseDouble(args.get(0));
                    double max = Double.parseDouble(args.get(1));
                    this.minValue = min;
                    this.maxValue = max;
                    // 判断是否为整数（两个都是整数）
                    this.isInteger = (min == (long) min) && (max == (long) max);
                } else {
                    throw new IllegalArgumentException("Invalid number of arguments");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Error parsing args: " + args, e);
            }
        }
    }

    /**
     * 数字策略
     */
    private interface NumberStrategy {
        String generate(NumberContext ctx);
    }

    /**
     * 整数策略
     */
    private static class IntegerStrategy implements NumberStrategy {
        @Override
        public String generate(NumberContext ctx) {
            long min = (long) ctx.getMinValue();
            long max = (long) ctx.getMaxValue();
            // 直接生成指定范围内的随机整数
            long randomInt = ThreadLocalRandom.current().nextLong(min, max + 1);
            return String.valueOf(randomInt);
        }
    }

    /**
     * 小数策略
     */
    private static class FloatStrategy implements NumberStrategy {
        @Override
        public String generate(NumberContext ctx) {
            double min = ctx.getMinValue();
            double max = ctx.getMaxValue();
            // 直接生成指定范围内的随机小数
            double randomFloat = ThreadLocalRandom.current().nextDouble(min, max);
            return String.valueOf(randomFloat);
        }
    }
}
