package com.evalkit.framework.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流数据项
 */
@Data
@Builder
@AllArgsConstructor
public class DataItem {
    /* 评测Case序号 */
    private Long dataIndex;
    /* 评测输入数据 */
    private InputData inputData;
    /* 业务接口调用结果 */
    private ApiCompletionResult apiCompletionResult;
    /* 评估器结果 */
    private EvalResult evalResult;
    /* 额外信息 */
    private Map<String, Object> extra;

    private DataItem() {
    }

    public DataItem(Long dataIndex, InputData inputData) {
        this.dataIndex = dataIndex;
        this.inputData = inputData;
    }

    public void addScorerResult(ScorerResult result) {
        // 添加评估器结果时要同步更新最终的评估结果
        synchronized (this) {
            if (evalResult == null) {
                evalResult = new EvalResult();
            }
            evalResult.addScorerResult(result);
            evalResult.setDataIndex(dataIndex);
        }
    }

    public void addExtraItem(String key, Object value) {
        if (extra == null) {
            extra = new ConcurrentHashMap<>();
        }
        extra.put(key, value);
    }

    public Object getExtraItem(String key) {
        if (extra == null) {
            return null;
        }
        return extra.get(key);
    }
}
