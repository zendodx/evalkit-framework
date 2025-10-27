package com.evalkit.framework.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单评估器结果
 */
@Data
@Builder
@AllArgsConstructor
public class ScorerResult {
    /* 数据索引 */
    private Long dataIndex;
    /* 指标名称 */
    private String metric;
    /* 评估器得分 */
    private double score;
    /* 评估器得分率 */
    private double scoreRate;
    /* 评估器总分 */
    private double totalScore;
    /* 评估器理由 */
    private String reason;
    /* 额外信息 */
    private Map<String, Object> extra;
    /* 评估开始时间 */
    private long statTime;
    /* 评估结束时间 */
    private long endTime;
    /* 评估耗时 */
    private long timeCost;
    /* 评测是否成功 */
    private boolean success;
    /* 评测是否通过 */
    private boolean pass;
    /* 评估器通过阈值 */
    private double threshold;
    /* 是否为必过评估器,如果该评估器没过,则直接忽略其他评估器结果,设置最终结果为不通过 */
    private boolean star;

    public ScorerResult() {
    }

    public ScorerResult(String metric, double score, String reason) {
        this(metric, score, 0, reason, null);
    }

    public ScorerResult(String metric, double score, double totalScore, String reason) {
        this(metric, score, totalScore, reason, null);
    }

    public ScorerResult(String metric, double score, double totalScore, String reason, Map<String, Object> extra) {
        this.metric = metric;
        this.score = score;
        this.totalScore = totalScore;
        this.reason = reason;
        this.extra = extra;
    }


    /* 获取额外项 */
    public <T> T getExtraItem(String key) {
        return getExtraItem(key, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getExtraItem(String key, T defaultValue) {
        if (extra == null) {
            return defaultValue;
        }
        return (T) extra.getOrDefault(key, defaultValue);
    }

    /* 添加额外项 */
    public void addExtraItem(String key, Object value) {
        synchronized (this) {
            if (extra == null) {
                extra = new ConcurrentHashMap<>();
            }
            extra.put(key, value);
        }
    }

    /* 批量添加额外项 */
    public void addExtraItems(Map<String, Object> extraItems) {
        extraItems.forEach(this::addExtraItem);
    }
}
