package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 线程安全、可扩展的日期 Mocker
 * <p>
 * {{date pattern}} 当前时间
 * {{future_date days}}  未来 days 天内
 * {{future_date days pattern}}  未来 days 天内
 * {{future_date days days}}  未来 days~days 天内
 * {{future_date days days pattern}}  未来 days~days 天内
 * {{past_date days}}  过去 days 天内
 * {{past_date days pattern}}  过去 days 天内
 * {{past_date days days}}  过去 days~days 天内
 * {{past_date days days pattern}}  过去 days~days 天内
 */
@Slf4j
public class DateMocker implements Mocker {
    /*  规则 -> 策略缓存（线程安全） */
    private static final Map<String, DateStrategy> STRATEGY_POOL = new ConcurrentHashMap<>();

    static {
        STRATEGY_POOL.put("date", new NowStrategy());
        STRATEGY_POOL.put("future_date", new FutureStrategy());
        STRATEGY_POOL.put("past_date", new PastStrategy());
    }

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return StringUtils.containsIgnoreCase(ruleName, "date");
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        DateStrategy strategy = STRATEGY_POOL.get(StringUtils.lowerCase(ruleName));
        if (strategy == null) {
            // 不支持的规则
            return null;
        }
        // 参数只解析一次
        DateContext ctx = new DateContext(ruleParams);
        return strategy.generate(ctx);
    }

    /**
     * 日期参数上下文:
     * None  没有参数,使用默认值
     * [日期格式]   第1位是日期参数,使用指定日期格式
     * [天数]   第1位是天数,使用默认日期格式
     * [天数] [日期格式]   第1位是天数,第2位是日期格式
     * [天数] [天数]  第1位是至少天数,第2位是至多天数,使用默认日期格式
     * [天数] [天数]  [日期格式]  第1位是至少天数,第2位是至多天数,第3位是日期格式
     */
    @Data
    private static class DateContext {
        /* 默认日期格式 */
        private final static String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
        /* 默认至少天数0 */
        private final static int DEFAULT_AT_LEAST = 0;
        /* 默认至多天数7 */
        private final static int DEFAULT_AT_MOST = 7;
        /* 日期合适 */
        private String pattern;
        /* 至少天数 */
        private int atLeast;
        /* 至多天数 */
        private int atMost;

        public DateContext(List<String> args) {
            updateParams(args);
        }

        /**
         * 解析参数
         */
        public void updateParams(List<String> args) {
            try {
                if (CollectionUtils.isEmpty(args)) {
                    // None  没有参数,使用默认值
                    this.pattern = DEFAULT_PATTERN;
                    this.atLeast = DEFAULT_AT_LEAST;
                    this.atMost = DEFAULT_AT_MOST;
                } else if (args.size() == 1) {
                    // [日期格式]  第1位是日期参数,使用指定日期格式
                    // [天数]   第1位是天数,使用默认日期格式
                    if (NumberUtils.isCreatable(args.get(0))) {
                        this.atLeast = DEFAULT_AT_LEAST;
                        this.atMost = Integer.parseInt(args.get(0));
                        this.pattern = DEFAULT_PATTERN;
                    } else {
                        this.pattern = args.get(0);
                        this.atLeast = DEFAULT_AT_LEAST;
                        this.atMost = DEFAULT_AT_MOST;
                    }
                } else if (args.size() == 2) {
                    // [天数] [日期格式] 第1位是天数,第2位是日期格式
                    // [天数] [天数]  第1位是至少天数,第2位是至多天数,使用默认日期格式
                    if (NumberUtils.isCreatable(args.get(0)) && NumberUtils.isCreatable(args.get(1))) {
                        this.atLeast = Integer.parseInt(args.get(0));
                        this.atMost = Integer.parseInt(args.get(1));
                        this.pattern = DEFAULT_PATTERN;
                    } else if (NumberUtils.isCreatable(args.get(0)) && !NumberUtils.isCreatable(args.get(1))) {
                        this.atLeast = DEFAULT_AT_LEAST;
                        this.atMost = Integer.parseInt(args.get(0));
                        this.pattern = args.get(1);
                    }
                } else if (args.size() == 3) {
                    // [天数] [天数] [日期格式]  第1位是至少天数,第2位是至多天数,第3位是日期格式
                    this.atLeast = Integer.parseInt(args.get(0));
                    this.atMost = Integer.parseInt(args.get(1));
                    this.pattern = args.get(2);
                } else {
                    throw new IllegalArgumentException("Invalid number of arguments");
                }
                // 日期patten格式校验
                if (!DateUtils.isValidPattern(pattern)) {
                    throw new IllegalArgumentException("Invalid date pattern");
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Error parsing args: " + args, e);
            }
        }
    }

    /**
     * 日期策略
     */
    private interface DateStrategy {
        String generate(DateContext ctx);
    }

    /**
     * 日期策略
     */
    private static class NowStrategy implements DateStrategy {
        @Override
        public String generate(DateContext ctx) {
            return DateUtils.dateToString(new Date(), ctx.getPattern());
        }
    }

    /**
     * 未来日期策略
     */
    private static class FutureStrategy implements DateStrategy {
        @Override
        public String generate(DateContext ctx) {
            int atLeast = ctx.getAtLeast();
            int atMost = ctx.getAtMost();
            // 直接生成指定范围内的随机天数
            int days = ThreadLocalRandom.current().nextInt(atLeast, atMost + 1);
            long offset = TimeUnit.DAYS.toMillis(days);
            Date future = new Date(System.currentTimeMillis() + offset);
            return DateUtils.dateToString(future, ctx.getPattern());
        }
    }

    /**
     * 过去日期策略
     */
    private static class PastStrategy implements DateStrategy {
        @Override
        public String generate(DateContext ctx) {
            int atLeast = ctx.getAtLeast();
            int atMost = ctx.getAtMost();
            // 直接生成指定范围内的随机天数
            int days = ThreadLocalRandom.current().nextInt(atLeast, atMost + 1);
            long offset = TimeUnit.DAYS.toMillis(days);
            // 计算过去的时间
            Date past = new Date(System.currentTimeMillis() - offset);
            return DateUtils.dateToString(past, ctx.getPattern());
        }
    }
}