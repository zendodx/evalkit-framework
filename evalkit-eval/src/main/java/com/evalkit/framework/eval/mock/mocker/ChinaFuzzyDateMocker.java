package com.evalkit.framework.eval.mock.mocker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 中国模糊日期Mocker
 * <p>
 * 模糊日:近日,近来,最近,日前,不日,即日,当日,改日,他日,来日,昔日,往日
 * 模糊周:本周,下周,上周,周末
 * 模糊月:月初,月中,月末,上旬,中旬,下旬,月底,月初,月中
 * 模糊年:今年,去年,明年,前年,后年,年初,年中,年底,上半年,下半年,往年,往年同期,来年,翌年,经年,历年
 * 模糊季节:一季度,二季度,三季度,四季度,春季,夏季,秋季,冬季,早春,暮春,初夏,盛夏,深秋,初冬
 * 口语常用:过两天,等会儿,回头,赶明儿
 * <p>
 * {{fuzzy_date}} 模糊日期表达
 * {{fuzzy_date day}} 模糊日表达
 * {{fuzzy_date week}} 模糊周表达
 * {{fuzzy_date month}} 模糊月表达
 * {{fuzzy_date year}} 模糊年表达
 * {{fuzzy_date season}} 模糊季节表达
 * {{fuzzy_date human}} 模糊日期口语表达
 */
@Slf4j
public class ChinaFuzzyDateMocker implements Mocker {
    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return "fuzzy_date".equalsIgnoreCase(ruleName);
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        if (!support(ruleName, ruleParams)) {
            return null;
        }
        FuzzyContext ctx = new FuzzyContext(ruleParams);
        return ctx.generateFuzzyDate();
    }

    /**
     * 模糊日期上下文
     */
    @Data
    private static class FuzzyContext {
        /* 日期类型: day, week, month, year, season, human, 默认all */
        private String fuzzyType;

        /* 模糊日表达 */
        private static final String[] FUZZY_DAY = {
                "近日", "近来", "最近", "日前", "不日", "即日", "当日",
                "改日", "他日", "来日", "昔日", "往日"
        };

        /* 模糊周表达 */
        private static final String[] FUZZY_WEEK = {
                "本周", "下周", "上周", "周末"
        };

        /* 模糊月表达 */
        private static final String[] FUZZY_MONTH = {
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底"
        };

        /* 模糊年表达 */
        private static final String[] FUZZY_YEAR = {
                "今年", "去年", "明年", "前年", "后年", "年初", "年中",
                "年底", "上半年", "下半年", "往年", "来年", "翌年", "经年", "历年"
        };

        /* 模糊季节表达 */
        private static final String[] FUZZY_SEASON = {
                "一季度", "二季度", "三季度", "四季度",
                "春季", "夏季", "秋季", "冬季",
                "早春", "暮春", "初夏", "盛夏", "深秋", "初冬"
        };

        /* 口语常用表达 */
        private static final String[] FUZZY_HUMAN = {
                "过两天", "等会儿", "回头", "赶明儿"
        };

        public FuzzyContext(List<String> args) {
            // 从参数解析模糊类型
            this.fuzzyType = "all";
            if (args != null && !args.isEmpty()) {
                String type = StringUtils.lowerCase(args.get(0));
                if ("day".equals(type) || "week".equals(type) || "month".equals(type) ||
                        "year".equals(type) || "season".equals(type) || "human".equals(type)) {
                    this.fuzzyType = type;
                }
            }
        }

        /**
         * 生成模糊日期
         */
        public String generateFuzzyDate() {
            if ("day".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_DAY);
            } else if ("week".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_WEEK);
            } else if ("month".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_MONTH);
            } else if ("year".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_YEAR);
            } else if ("season".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_SEASON);
            } else if ("human".equals(fuzzyType)) {
                return getRandomFuzzyDate(FUZZY_HUMAN);
            } else {
                return getRandomAllFuzzyDate();
            }
        }

        /**
         * 从数组中随机选择一个模糊日期
         */
        private String getRandomFuzzyDate(String[] fuzzyArray) {
            if (fuzzyArray == null || fuzzyArray.length == 0) {
                return null;
            }
            int idx = ThreadLocalRandom.current().nextInt(fuzzyArray.length);
            return fuzzyArray[idx];
        }

        /**
         * 随机选择所有类别中的一个模糊日期
         */
        private String getRandomAllFuzzyDate() {
            String[][] allFuzzy = {FUZZY_DAY, FUZZY_WEEK, FUZZY_MONTH, FUZZY_YEAR, FUZZY_SEASON, FUZZY_HUMAN};
            String[] selected = allFuzzy[ThreadLocalRandom.current().nextInt(allFuzzy.length)];
            return getRandomFuzzyDate(selected);
        }
    }
}
