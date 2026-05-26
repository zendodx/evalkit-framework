package com.evalkit.framework.eval.node.scorer.checker.model;

import com.evalkit.framework.eval.node.scorer.checker.constants.CheckMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class CheckItemTest {

    // ─────────────────────────── 默认值验证 ────────────────────────────

    @Test
    void defaultValues_areCorrect() {
        CheckItem item = CheckItem.builder().name("检查项").build();
        assertEquals("检查项", item.getName());
        assertEquals(1.0, item.getTotalScore(), 1e-6);
        assertEquals(1.0, item.getWeight(), 1e-6);
        assertFalse(item.isStar());
        assertTrue(item.isSupport());
        assertEquals(0.0, item.getDefaultScore(), 1e-6);
        assertFalse(item.isExecuted());
        assertEquals(CheckMethod.NONE, item.getCheckMethod());
    }

    // ─────────────────────────── 参数校验 ─────────────────────────────

    @Test
    void build_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> CheckItem.builder().name("").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void build_negativeTotalScore_throwsIllegalArgument() {
        assertThatThrownBy(() -> CheckItem.builder().name("x").totalScore(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void build_negativeWeight_throwsIllegalArgument() {
        assertThatThrownBy(() -> CheckItem.builder().name("x").weight(-0.1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void build_negativeDefaultScore_throwsIllegalArgument() {
        assertThatThrownBy(() -> CheckItem.builder().name("x").defaultScore(-1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─────────────────────────── getWeightScore ───────────────────────

    @Test
    void getWeightScore_normalCase() {
        CheckItem item = CheckItem.builder().name("x").weight(2.0).build();
        item.setScore(0.8);
        assertEquals(1.6, item.getWeightScore(), 1e-6);
    }

    @Test
    void getWeightScore_zeroScore() {
        CheckItem item = CheckItem.builder().name("x").weight(3.0).build();
        item.setScore(0.0);
        assertEquals(0.0, item.getWeightScore(), 1e-6);
    }

    // ─────────────────────────── support=false 时初始分数取 defaultScore ─

    @Test
    void support_false_scoreEqualsDefaultScore() {
        CheckItem item = CheckItem.builder()
                .name("x")
                .support(false)
                .defaultScore(0.5)
                .build();
        assertFalse(item.isSupport());
        assertEquals(0.5, item.getScore(), 1e-6);
    }

    // ─────────────────────────── star 标志 ────────────────────────────

    @Test
    void star_flag_isSetCorrectly() {
        CheckItem item = CheckItem.builder().name("必过项").star(true).build();
        assertTrue(item.isStar());
    }

    // ─────────────────────────── setter/getter ─────────────────────────

    @Test
    void setters_workCorrectly() {
        CheckItem item = CheckItem.builder().name("item").build();
        item.setScore(0.9);
        item.setReason("测试理由");
        item.setExecuted(true);
        item.setCheckMethod(CheckMethod.LLM);

        assertEquals(0.9, item.getScore(), 1e-6);
        assertEquals("测试理由", item.getReason());
        assertTrue(item.isExecuted());
        assertEquals(CheckMethod.LLM, item.getCheckMethod());
    }

    // ─────────────────────────── checkDescription ─────────────────────

    @Test
    void checkDescription_isSetAndRetrieved() {
        CheckItem item = CheckItem.builder()
                .name("x")
                .checkDescription("这是检查描述")
                .build();
        assertEquals("这是检查描述", item.getCheckDescription());
    }
}