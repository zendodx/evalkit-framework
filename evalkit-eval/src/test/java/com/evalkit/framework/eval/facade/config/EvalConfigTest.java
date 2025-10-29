package com.evalkit.framework.eval.facade.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvalConfigTest {

    private void setProp(String key, String val) {
        if (val == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, val);
        }
    }

    @AfterEach
    void tearDown() {
        // 清掉本次测试加的 -D 参数，避免污染别的用例
        System.clearProperty("taskName");
        System.clearProperty("filePath");
        System.clearProperty("offset");
        System.clearProperty("limit");
        System.clearProperty("threadNum");
        System.clearProperty("passScore");
        System.clearProperty("extra");
        System.clearProperty("openInjectData");
        System.clearProperty("injectDataIndex");
        System.clearProperty("injectInputData");
        System.clearProperty("injectApiCompletionResult");
        System.clearProperty("injectEvalResult");
        System.clearProperty("injectExtra");
    }

    @Test
    void defaultValue() {
        EvalConfig config = EvalConfig.builder().build();
        assertThat(config.getTaskName()).startsWith("EvalTest_");
        assertThat(config.getOffset()).isZero();
        assertThat(config.getLimit()).isEqualTo(-1);
        assertThat(config.getThreadNum()).isOne();
        assertThat(config.getPassScore()).isZero();
        assertThat(config.getExtra()).isNull();
        assertThat(config.isOpenInjectData()).isFalse();
        assertThat(config.isInjectDataIndex()).isTrue();
        assertThat(config.isInjectInputData()).isTrue();
        assertThat(config.isInjectApiCompletionResult()).isTrue();
        assertThat(config.isInjectEvalResult()).isTrue();
        assertThat(config.isInjectExtra()).isTrue();
    }

    @Test
    void updateFromEnv() {
        // given
        setProp("taskName", "envTask");
        setProp("filePath", "/tmp/eval.json");
        setProp("offset", "10");
        setProp("limit", "100");
        setProp("threadNum", "8");
        setProp("passScore", "0.85");
        setProp("extra", "{\"model\":\"gpt-4\",\"timeout\":30}");
        setProp("openInjectData", "true");
        // when
        EvalConfig config = EvalConfig.builder().build();
        // then
        assertThat(config.getTaskName()).isEqualTo("envTask");
        assertThat(config.getFilePath()).isEqualTo("/tmp/eval.json");
        assertThat(config.getOffset()).isEqualTo(10);
        assertThat(config.getLimit()).isEqualTo(100);
        assertThat(config.getThreadNum()).isEqualTo(8);
        assertThat(config.getPassScore()).isEqualTo(0.85);
        assertThat(config.getExtra()).containsEntry("model", "gpt-4")
                .containsEntry("timeout", 30);
        assertThat(config.isOpenInjectData()).isTrue();
    }

    @Test
    void customValueThenEnvEmptyShouldKeepCustom() {
        // 环境给空串或 0
        setProp("taskName", "");
        setProp("threadNum", "0");
        setProp("passScore", "0.0");
        setProp("openInjectData", "true");
        // given
        EvalConfig config = EvalConfig.builder()
                .taskName("customTask")
                .threadNum(16)
                .passScore(0.9)
                .openInjectData(false)
                .build();
        // then
        assertThat(config.getTaskName()).isEqualTo("customTask"); // 空串不覆盖
        assertThat(config.getThreadNum()).isEqualTo(16);          // 0 不覆盖
        assertThat(config.getPassScore()).isEqualTo(0.9);         // 0.0 不覆盖
        assertThat(config.isOpenInjectData()).isTrue();
    }

    @Test
    void extraPutGet() {
        EvalConfig config = EvalConfig.builder().build();
        config.setExtraConfig("key1", "value1");
        assertThat(config.getExtraConfig("key1")).isEqualTo("value1");
        assertThat(config.getExtra()).hasSize(1);
    }
}