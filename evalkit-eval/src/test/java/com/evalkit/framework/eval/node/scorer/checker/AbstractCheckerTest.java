package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.scorer.checker.config.CheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.evalkit.framework.eval.node.scorer.checker.strategy.checkitem.SumCheckItemScoreMergeStrategy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class AbstractCheckerTest {

    // ─────────────── 辅助方法 ────────────────────────────────────────

    private DataItem buildDataItem(long idx) {
        DataItem item = new DataItem();
        item.setDataIndex(idx);
        item.setInputData(new InputData(idx, new HashMap<>()));
        return item;
    }

    /** 构建一个固定检查项分数的简单 Checker */
    private AbstractChecker buildChecker(boolean support, double totalScore,
                                         boolean star, List<CheckItem> checkItems) {
        CheckerConfig cfg = CheckerConfig.builder()
                .name("测试检查器")
                .totalScore(totalScore)
                .star(star)
                .strategy(new SumCheckItemScoreMergeStrategy())
                .build();
        return new AbstractChecker(cfg) {
            @Override
            public boolean support(DataItem dataItem) {
                return support;
            }

            @Override
            public double getTotalScore() {
                return totalScore;
            }

            @Override
            protected List<CheckItem> prepareCheckItems(DataItem dataItem) {
                return checkItems;
            }

            @Override
            protected void check(DataItem dataItem) {
                // 简单赋分
                for (CheckItem ci : checkItems) {
                    ci.setExecuted(true);
                }
            }
        };
    }

    // ─────────────────── checkWrapper: support=false 时跳过 ──────────

    @Test
    void checkWrapper_notSupport_skips() {
        CheckItem ci = CheckItem.builder().name("项A").build();
        // 初始分 0
        AbstractChecker checker = buildChecker(false, 1.0, false, Arrays.asList(ci));
        DataItem item = buildDataItem(1L);
        checker.checkWrapper(item);
        // 因为 support=false，check() 没有执行，checkItems 为默认值（builder 里的 empty list）
        // 只验证不抛异常
        assertEquals(0.0, checker.getScore(), 1e-6);
    }

    // ─────────────────── checkWrapper: 正常流程 ──────────────────────

    @Test
    void checkWrapper_normalFlow_checkItemsSetAndMerged() {
        CheckItem ci = CheckItem.builder().name("语言检查").totalScore(1.0).build();
        AbstractChecker checker = buildChecker(true, 1.0, false, Arrays.asList(ci));
        // 在 check 时手动设置分数
        DataItem item = buildDataItem(2L);
        checker.checkWrapper(item);
        // check 里只标记 executed，不设置分数，score 仍 0
        assertTrue(checker.getConfig().getCheckItems().get(0).isExecuted());
    }

    // ─────────────────── getScore / getReason ────────────────────────

    @Test
    void getScore_sumStrategy() {
        CheckItem ci1 = CheckItem.builder().name("A").totalScore(1.0).build();
        CheckItem ci2 = CheckItem.builder().name("B").totalScore(1.0).build();
        ci1.setScore(0.8);
        ci2.setScore(0.6);

        CheckerConfig cfg = CheckerConfig.builder()
                .name("checker")
                .totalScore(2.0)
                .strategy(new SumCheckItemScoreMergeStrategy())
                .checkItems(Arrays.asList(ci1, ci2))
                .build();

        AbstractChecker checker = new AbstractChecker(cfg) {
            @Override
            public boolean support(DataItem d) { return true; }
            @Override
            public double getTotalScore() { return 2.0; }
            @Override
            protected List<CheckItem> prepareCheckItems(DataItem d) { return cfg.getCheckItems(); }
            @Override
            protected void check(DataItem d) {}
        };

        assertEquals(0.8 + 0.6, checker.getScore(), 1e-6);
    }

    @Test
    void getReason_returnsZeroScoreItemReasons() {
        CheckItem pass = CheckItem.builder().name("通过项").build();
        CheckItem fail = CheckItem.builder().name("不通过项").build();
        pass.setScore(1.0);
        pass.setReason("通过");
        fail.setScore(0.0);
        fail.setReason("内容不符合要求");

        CheckerConfig cfg = CheckerConfig.builder()
                .name("checker")
                .strategy(new SumCheckItemScoreMergeStrategy())
                .checkItems(Arrays.asList(pass, fail))
                .build();

        AbstractChecker checker = new AbstractChecker(cfg) {
            @Override
            public boolean support(DataItem d) { return true; }
            @Override
            public double getTotalScore() { return 2.0; }
            @Override
            protected List<CheckItem> prepareCheckItems(DataItem d) { return cfg.getCheckItems(); }
            @Override
            protected void check(DataItem d) {}
        };

        String reason = checker.getReason();
        assertTrue(reason.contains("内容不符合要求"));
        assertFalse(reason.contains("通过"));
    }

    // ─────────────────── star 标志 ───────────────────────────────────

    @Test
    void isStar_reflectsConfig() {
        CheckItem ci = CheckItem.builder().name("x").build();
        AbstractChecker starChecker = buildChecker(true, 1.0, true, Arrays.asList(ci));
        AbstractChecker normalChecker = buildChecker(true, 1.0, false, Arrays.asList(ci));

        assertTrue(starChecker.isStar());
        assertFalse(normalChecker.isStar());
    }

    // ─────────────────── checkWrapper: 异常传播 ──────────────────────

    @Test
    void checkWrapper_exceptionPropagates() {
        CheckItem ci = CheckItem.builder().name("x").build();
        CheckerConfig cfg = CheckerConfig.builder()
                .name("错误检查器")
                .strategy(new SumCheckItemScoreMergeStrategy())
                .build();
        AbstractChecker checker = new AbstractChecker(cfg) {
            @Override
            public boolean support(DataItem d) { return true; }
            @Override
            public double getTotalScore() { return 1.0; }
            @Override
            protected List<CheckItem> prepareCheckItems(DataItem d) { return Arrays.asList(ci); }
            @Override
            protected void check(DataItem d) { throw new RuntimeException("check error"); }
        };

        assertThatThrownBy(() -> checker.checkWrapper(buildDataItem(1L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("check error");
    }
}