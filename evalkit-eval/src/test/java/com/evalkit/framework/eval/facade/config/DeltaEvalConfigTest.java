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
        System.clearProperty("enablePeriodicReport");
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
        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getReportInterval()).isEqualTo(600);
        assertThat(config.getMqReceiveTimeout()).isEqualTo(10000);
        assertThat(config.isEnableResume()).isTrue();
        assertThat(config.isEnablePeriodicReport()).isTrue();
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
        // env 设置 batchSize=10 且 10>1，所以会覆盖代码中的 batchSize(1)
        assertThat(config.getBatchSize()).isEqualTo(10);
    }

    /**
     * batchSize=1 是合法的最小值，不应触发 checkParams 抛异常
     * 同时验证 env 的 batchSize 判断为 >1（等于1时不覆盖），代码设定的 batchSize=1 应被保留
     */
    @Test
    void batchSizeOneIsValidAndNotOverriddenByEnvWhenEnvIsOne() {
        // env batchSize=1（条件是 >1，所以 1 不覆盖）
        setProp("batchSize", "1");

        DeltaEvalConfig config = DeltaEvalConfig.builder()
                .taskName("batchOne")
                .batchSize(1)
                .build();

        // batchSize=1 应被保留（env 的 1 不满足 >1 的覆盖条件）
        assertThat(config.getBatchSize()).isEqualTo(1);
    }

    /**
     * batchSize=0 应触发 checkParams 校验失败
     */
    @Test
    void batchSizeZeroShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeltaEvalConfig.builder()
                        .taskName("batchZero")
                        .batchSize(0)
                        .build(),
                "batchSize=0 应抛出 IllegalArgumentException"
        );
    }

    /**
     * batchSize 负值应触发 checkParams 校验失败
     */
    @Test
    void batchSizeNegativeShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeltaEvalConfig.builder()
                        .taskName("batchNeg")
                        .batchSize(-5)
                        .build(),
                "batchSize<0 应抛出 IllegalArgumentException"
        );
    }

    /**
     * messageProcessMaxTime 默认值验证（OrderedDeltaEvalFacade 专用字段）
     */
    @Test
    void messageProcessMaxTimeDefault() {
        DeltaEvalConfig config = DeltaEvalConfig.builder().build();
        assertThat(config.getMessageProcessMaxTime()).isEqualTo(60L);
    }

    /**
     * messageProcessMaxTime=0 应触发校验失败
     */
    @Test
    void messageProcessMaxTimeZeroShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeltaEvalConfig.builder()
                        .taskName("mptZero")
                        .messageProcessMaxTime(0)
                        .build(),
                "messageProcessMaxTime=0 应抛出 IllegalArgumentException"
        );
    }

    /**
     * enableResume 默认为 true，可被 env 覆盖为 false
     */
    @Test
    void enableResumeDefaultTrueAndCanBeOverriddenByEnv() {
        // 默认值
        DeltaEvalConfig defaultConfig = DeltaEvalConfig.builder().build();
        assertThat(defaultConfig.isEnableResume()).isTrue();

        // env 覆盖
        setProp("enableResume", "false");
        DeltaEvalConfig envConfig = DeltaEvalConfig.builder().build();
        assertThat(envConfig.isEnableResume()).isFalse();
    }

    /**
     * reportInterval=0 且 enablePeriodicReport=true 应触发校验失败
     */
    @Test
    void reportIntervalZeroShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeltaEvalConfig.builder()
                        .taskName("riZero")
                        .reportInterval(0)
                        .build(),
                "reportInterval=0 应抛出 IllegalArgumentException"
        );
    }

    /**
     * enablePeriodicReport=false 时，reportInterval=0 不应触发校验失败
     */
    @Test
    void reportIntervalZeroAllowedWhenPeriodicReportDisabled() {
        DeltaEvalConfig config = DeltaEvalConfig.builder()
                .taskName("riZeroDisabled")
                .reportInterval(0)
                .enablePeriodicReport(false)
                .build();
        assertThat(config.isEnablePeriodicReport()).isFalse();
        assertThat(config.getReportInterval()).isZero();
    }

    /**
     * enablePeriodicReport 默认为 true，可被 env 覆盖为 false
     */
    @Test
    void enablePeriodicReportDefaultTrueAndCanBeOverriddenByEnv() {
        // 默认值
        DeltaEvalConfig defaultConfig = DeltaEvalConfig.builder().build();
        assertThat(defaultConfig.isEnablePeriodicReport()).isTrue();

        // env 覆盖
        setProp("enablePeriodicReport", "false");
        DeltaEvalConfig envConfig = DeltaEvalConfig.builder().build();
        assertThat(envConfig.isEnablePeriodicReport()).isFalse();
    }

    /**
     * mqReceiveTimeout=0 应触发校验失败
     */
    @Test
    void mqReceiveTimeoutZeroShouldThrow() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeltaEvalConfig.builder()
                        .taskName("mqZero")
                        .mqReceiveTimeout(0)
                        .build(),
                "mqReceiveTimeout=0 应抛出 IllegalArgumentException"
        );
    }
}