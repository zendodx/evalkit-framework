package com.evalkit.framework.eval.facade.config;

import com.evalkit.framework.common.utils.time.DateUtils;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * 评测运行基础配置
 */
@Data
@SuperBuilder
public class EvalConfig {
    /* 任务名称,增量评测统一任务需保持任务名称一致, 默认 EvalTest_运行时间 */
    @Builder.Default
    private String taskName = "EvalTest_" + DateUtils.nowToString("yyyyMMddHHmmss");
    /* 评测数据集文件路径,默认空*/
    private String filePath;
    /* 分页偏移量, 默认0 */
    @Builder.Default
    private int offset = 0;
    /* 分页页大小, 默认-1,加载所有 */
    @Builder.Default
    private int limit = -1;
    /* 并发数,默认1 */
    @Builder.Default
    private int threadNum = 1;
    /* 评测通过分数 */
    @Builder.Default
    private double passScore = 0.0;
    /* 额外配置 */
    private Map<String, Object> extraConfig;

    /**
     * 获取额外配置
     *
     * @param key 配置key
     * @return 配置值
     */
    public Object getExtraConfig(String key) {
        if (extraConfig != null) {
            return extraConfig.getOrDefault(key, null);
        }
        return null;
    }

    /**
     * 设置额外配置
     *
     * @param key   配置key
     * @param value 配置值
     */
    public void setExtraConfig(String key, Object value) {
        if (extraConfig == null) {
            extraConfig = new HashMap<>();
        }
        extraConfig.put(key, value);
    }
}
