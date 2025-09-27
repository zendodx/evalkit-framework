package com.evalkit.framework.eval.node.dataloader_wrapper.mock.mocker;

import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 线程安全、可扩展的日期 Mocker
 * <p>
 * {{date pattern}} -> 当前时间
 * {{future_date days pattern}} -> 未来 days 天内
 * {{future_date days}} -> 未来 days 天内
 * {{past_date days}} -> 过去 days 天内
 * {{past_date  days pattern}} -> 过去 days 天内
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
     * None -> 没有参数,使用默认值
     * [日期格式] -> 第1位是日期参数,使用指定日期格式
     * [天数] -> 第1位是天数,使用默认日期格式
     * [天数] [日期格式] -> 第1位是天数,第2位是日期格式
     */
    @Data
    private static class DateContext {
        private final static String DEFAULT_PATTERN = "yyyy-MM-dd HH:mm:ss";
        private final static int DEFAULT_AT_MOST = 7;
        private String pattern;
        /* 天数 */
        private int atMost;

        public DateContext(List<String> args) {
            updateParams(args);
        }

        /**
         * 解析参数
         */
        public void updateParams(List<String> args) {
            if (CollectionUtils.isEmpty(args)) {
                this.pattern = DEFAULT_PATTERN;
                this.atMost = DEFAULT_AT_MOST;
            } else if (args.size() == 1) {
                try {
                    this.atMost = Integer.parseInt(args.get(0));
                    this.pattern = DEFAULT_PATTERN;
                } catch (Exception e) {
                    this.pattern = args.get(0);
                    this.atMost = DEFAULT_AT_MOST;
                }
            } else if (args.size() == 2) {
                try {
                    this.atMost = Integer.parseInt(args.get(0));
                    this.pattern = args.get(1);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Error parsing args: " + args);
                }
            } else {
                throw new IllegalArgumentException("Invalid number of arguments");
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
            int days = ctx.getAtMost();
            long offset = ThreadLocalRandom.current().nextLong(TimeUnit.DAYS.toMillis(days));
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
            int days = ctx.getAtMost();
            long offset = ThreadLocalRandom.current().nextLong(TimeUnit.DAYS.toMillis(days));
            Date past = new Date(System.currentTimeMillis() - offset);
            return DateUtils.dateToString(past, ctx.getPattern());
        }
    }
}