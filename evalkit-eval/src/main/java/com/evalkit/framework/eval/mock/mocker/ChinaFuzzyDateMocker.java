package com.evalkit.framework.eval.mock.mocker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 中国模糊日期Mocker - 支持未来/过去时间方向区分
 * <p>
 * 支持的参数组合:
 * - 模糊类型: day(日), week(周), month(月), year(年), season(季节), human(口语), 默认全部
 * - 时间方向: future(未来), past(过去), 默认混合
 * <p>
 * 使用示例:
 * {{fuzzy_date}} - 随机任意模糊日期
 * {{fuzzy_date future}} - 仅未来的模糊日期
 * {{fuzzy_date past}} - 仅过去的模糊日期
 * {{fuzzy_date day}} - 模糊日表达(包含过去和未来)
 * {{fuzzy_date day future}} - 仅未来的模糊日
 * {{fuzzy_date day past}} - 仅过去的模糊日
 * {{fuzzy_date week future}} - 仅未来的模糊周
 * {{fuzzy_date month past}} - 仅过去的模糊月
 * {{fuzzy_date year future}} - 仅未来的模糊年
 * {{fuzzy_date season past}} - 仅过去的模糊季节
 * {{fuzzy_date human future}} - 仅未来的口语表达
 * <p>
 * 具体表达范围:
 * 未来模糊日: 不日, 即日, 改日, 他日, 来日, 当日
 * 过去模糊日: 近日, 近来, 最近, 日前, 昔日, 往日
 * 未来模糊周: 本周, 下周, 周末, 未来一周, 未来二周, 未来三周
 * 过去模糊周: 上周, 上上周, 大上周, 过去一周, 过去二周, 过去三周
 * 未来模糊月: 月初, 月中, 月末, 上旬, 中旬, 下旬, 月底, 未来一月, 未来二月, 未来三月
 * 过去模糊月: 上月, 过去一月, 过去二月, 过去三月
 * 未来模糊年: 今年, 明年, 后年, 年初, 年中, 年底, 下半年, 来年, 翌年, 未来一年, 未来二年, 未来三年
 * 过去模糊年: 去年, 前年, 往年, 往年同期, 历年, 经年, 上半年, 过去一年, 过去二年, 过去三年
 * 未来模糊季节: 二季度, 三季度, 四季度, 夏季, 秋季, 冬季, 初夏, 盛夏, 深秋, 初冬
 * 过去模糊季节: 去年春季, 去年夏季, 去年秋季, 去年冬季, 前年春季, 前年夏季, 前年秋季, 前年冬季
 * 未来口语常用: 过两天, 等会儿, 回头, 赶明儿
 * 过去口语常用: 前两三天, 前几天
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

        /* 时间方向: future/past/all, 默认all */
        private String timeDirection;

        /* 未来模糊日表达 */
        private static final String[] FUZZY_DAY_FUTURE = {
                "不日", "即日", "改日", "他日", "来日", "当日"
        };

        /* 过去模糊日表达 */
        private static final String[] FUZZY_DAY_PAST = {
                "近日", "近来", "最近", "日前", "昔日", "往日"
        };

        /* 未来模糊周表达 */
        private static final String[] FUZZY_WEEK_FUTURE = {
                "本周", "下周", "周末", "未来一周", "未来二周", "未来三周"
        };

        /* 过去模糊周表达 */
        private static final String[] FUZZY_WEEK_PAST = {
                "上周", "上上周", "大上周", "过去一周", "过去二周", "过去三周"
        };

        /* 未来模糊月表达 */
        private static final String[] FUZZY_MONTH_FUTURE = {
                "月初", "月中", "月末", "上旬", "中旬", "下旬", "月底", "未来一月", "未来二月", "未来三月"
        };

        /* 过去模糊月表达 */
        private static final String[] FUZZY_MONTH_PAST = {
                "上月", "过去一月", "过去二月", "过去三月"
        };

        /* 未来模糊年表达 */
        private static final String[] FUZZY_YEAR_FUTURE = {
                "今年", "明年", "后年", "年初", "年中", "年底", "下半年", "来年", "翌年", "未来一年", "未来二年", "未来三年"
        };

        /* 过去模糊年表达 */
        private static final String[] FUZZY_YEAR_PAST = {
                "去年", "前年", "往年", "往年同期", "历年", "经年", "上半年", "过去一年", "过去二年", "过去三年"
        };

        /* 未来模糊季节表达 */
        private static final String[] FUZZY_SEASON_FUTURE = {
                "二季度", "三季度", "四季度", "夏季", "秋季", "冬季", "初夏", "盛夏", "深秋", "初冬"
        };

        /* 过去模糊季节表达 */
        private static final String[] FUZZY_SEASON_PAST = {
                "去年春季", "去年夏季", "去年秋季", "去年冬季", "前年春季", "前年夏季", "前年秋季", "前年冬季"
        };

        /* 未来口语常用表达 */
        private static final String[] FUZZY_HUMAN_FUTURE = {
                "过两天", "等会儿", "回头", "赶明儿"
        };

        /* 过去口语常用表达 */
        private static final String[] FUZZY_HUMAN_PAST = {
                "前两三天", "前几天"
        };

        public FuzzyContext(List<String> args) {
            // 从参数解析模糊类型和时间方向
            this.fuzzyType = "all";
            this.timeDirection = "all";

            if (args != null && !args.isEmpty()) {
                for (String arg : args) {
                    String lowerArg = StringUtils.lowerCase(arg);

                    // 解析模糊类型
                    if ("day".equals(lowerArg) || "week".equals(lowerArg) || "month".equals(lowerArg) ||
                            "year".equals(lowerArg) || "season".equals(lowerArg) || "human".equals(lowerArg)) {
                        this.fuzzyType = lowerArg;
                    }

                    // 解析时间方向
                    if ("future".equals(lowerArg) || "past".equals(lowerArg)) {
                        this.timeDirection = lowerArg;
                    }
                }
            }
        }

        /**
         * 生成模糊日期
         */
        public String generateFuzzyDate() {
            if ("day".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_DAY_FUTURE, FUZZY_DAY_PAST);
            } else if ("week".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_WEEK_FUTURE, FUZZY_WEEK_PAST);
            } else if ("month".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_MONTH_FUTURE, FUZZY_MONTH_PAST);
            } else if ("year".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_YEAR_FUTURE, FUZZY_YEAR_PAST);
            } else if ("season".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_SEASON_FUTURE, FUZZY_SEASON_PAST);
            } else if ("human".equals(fuzzyType)) {
                return getRandomFuzzyDateByDirection(FUZZY_HUMAN_FUTURE, FUZZY_HUMAN_PAST);
            } else {
                return getRandomAllFuzzyDate();
            }
        }

        /**
         * 根据时间方向随机选择模糊日期
         */
        private String getRandomFuzzyDateByDirection(String[] futureArray, String[] pastArray) {
            if ("future".equals(timeDirection)) {
                return getRandomFuzzyDate(futureArray);
            } else if ("past".equals(timeDirection)) {
                return getRandomFuzzyDate(pastArray);
            } else {
                // 混合模式：将两个数组合并后随机选择
                String[] combined = new String[futureArray.length + pastArray.length];
                System.arraycopy(futureArray, 0, combined, 0, futureArray.length);
                System.arraycopy(pastArray, 0, combined, futureArray.length, pastArray.length);
                return getRandomFuzzyDate(combined);
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
            String[][] allFuzzyFuture = {FUZZY_DAY_FUTURE, FUZZY_WEEK_FUTURE, FUZZY_MONTH_FUTURE, FUZZY_YEAR_FUTURE, FUZZY_SEASON_FUTURE, FUZZY_HUMAN_FUTURE};
            String[][] allFuzzyPast = {FUZZY_DAY_PAST, FUZZY_WEEK_PAST, FUZZY_MONTH_PAST, FUZZY_YEAR_PAST, FUZZY_SEASON_PAST, FUZZY_HUMAN_PAST};

            String[][] toUse = "future".equals(timeDirection) ? allFuzzyFuture : "past".equals(timeDirection) ? allFuzzyPast : allFuzzyFuture;
            String[] selected = toUse[ThreadLocalRandom.current().nextInt(toUse.length)];
            return getRandomFuzzyDate(selected);
        }
    }
}
