package com.evalkit.framework.eval.facade.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaEvalConfigTest {
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
        System.clearProperty("batchSize");
        System.clearProperty("reportInterval");
        System.clearProperty("mqReceiveTimeout");
        System.clearProperty("enableResume");
    }

    @Test
    void defaultValue() {
        DeltaEvalConfig config = DeltaEvalConfig.builder().build();
        assertThat(config.getTaskName()).startsWith("EvalTest_");
        assertThat(config.getOffset()).isZero();
        assertThat(config.getLimit()).isEqualTo(-1);
        assertThat(config.getThreadNum()).isOne();
        assertThat(config.getPassScore()).isZero();
        assertThat(config.getExtra()).isNull();
        assertThat(config.getDataLoader()).isNull();
        assertThat(config.getEvalWorkflow()).isNull();
        assertThat(config.getReportWorkflow()).isNull();
        assertThat(config.getBatchSize()).isEqualTo(1);
        assertThat(config.getReportInterval()).isEqualTo(30);
        assertThat(config.getMqReceiveTimeout()).isEqualTo(10000);
        assertThat(config.isEnableResume()).isTrue();
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
        setProp("batchSize", "10");
        setProp("reportInterval", "60");
        setProp("mqReceiveTimeout", "1000");
        setProp("enableResume", "false");
        // when
        DeltaEvalConfig config = DeltaEvalConfig.builder().build();
        // then
        assertThat(config.getTaskName()).isEqualTo("envTask");
        assertThat(config.getFilePath()).isEqualTo("/tmp/eval.json");
        assertThat(config.getOffset()).isEqualTo(10);
        assertThat(config.getLimit()).isEqualTo(100);
        assertThat(config.getThreadNum()).isEqualTo(8);
        assertThat(config.getPassScore()).isEqualTo(0.85);
        assertThat(config.getExtra()).containsEntry("model", "gpt-4").containsEntry("timeout", 30);
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getReportInterval()).isEqualTo(60);
        assertThat(config.getMqReceiveTimeout()).isEqualTo(1000);
        assertThat(config.isEnableResume()).isFalse();
    }

    @Test
    void customValueThenEnvEmptyShouldKeepCustom() {
        // 环境给空串或 0
        setProp("taskName", "");
        setProp("threadNum", "0");
        setProp("passScore", "0.0");
        setProp("batchSize", "10");
        // given
        DeltaEvalConfig config = DeltaEvalConfig.builder()
                .taskName("customTask")
                .threadNum(16)
                .passScore(0.9)
                .batchSize(1)
                .build();
        // then
        assertThat(config.getTaskName()).isEqualTo("customTask");
        assertThat(config.getThreadNum()).isEqualTo(16);
        assertThat(config.getPassScore()).isEqualTo(0.9);
        assertThat(config.getBatchSize()).isEqualTo(10);
    }
}