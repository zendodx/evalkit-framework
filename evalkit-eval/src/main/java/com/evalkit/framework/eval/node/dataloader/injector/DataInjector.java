package com.evalkit.framework.eval.node.dataloader.injector;

import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.constants.DataItemField;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalResult;
import com.evalkit.framework.eval.model.InputData;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * 数据注入器, 将输入数据中的数据注入到DataItem中
 * 适用于已经有接口,评测数据,不需要重复调用接口,评测的场景
 */
public class DataInjector {
    /**
     * 注入数据索引
     *
     * @param dataItem 输入数据
     */
    private static void injectDataIndex(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        Integer dataIndexTmp = inputData.get(DataItemField.dataIndexKey, null);
        if (dataIndexTmp == null) {
            return;
        }
        Long dataIndex = new Long(dataIndexTmp);
        dataItem.setDataIndex(dataIndex);
    }

    /**
     * 注入输入数据
     *
     * @param dataItem 输入数据
     */
    private static void injectInputData(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        Map<String, Object> map = inputData.get(DataItemField.inputDataKey, null);
        if (map == null) {
            return;
        }
        InputData result = MapUtils.fromMap(map, InputData.class);
        dataItem.setInputData(result);
    }

    /**
     * 注入接口调用结果
     *
     * @param dataItem 输入数据
     */
    private static void injectApiCompletionResult(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        Map<String, Object> map = inputData.get(DataItemField.apiCompletionResultKey, null);
        if (map == null) {
            return;
        }
        ApiCompletionResult result = MapUtils.fromMap(map, ApiCompletionResult.class);
        dataItem.setApiCompletionResult(result);
    }

    /**
     * 注入评测结果
     *
     * @param dataItem 输入数据
     */
    private static void injectEvalResult(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        Map<String, Object> map = inputData.get(DataItemField.evalResultKey, null);
        if (map == null) {
            return;
        }
        EvalResult result = MapUtils.fromMap(map, EvalResult.class);
        dataItem.setEvalResult(result);
    }

    /**
     * 注入额外数据
     *
     * @param dataItem 输入数据
     */
    private static void injectExtra(DataItem dataItem) {
        InputData inputData = dataItem.getInputData();
        Map<String, Object> map = inputData.get(DataItemField.extraKey, null);
        if (map == null) {
            return;
        }
        dataItem.setExtra(map);
    }

    private static void inject(DataItem dataItem) {
        injectDataIndex(dataItem);
        injectApiCompletionResult(dataItem);
        injectEvalResult(dataItem);
        injectExtra(dataItem);
        // input放在最后注入
        injectInputData(dataItem);
    }

    public static void batchInject(List<DataItem> dataItems) {
        if (CollectionUtils.isEmpty(dataItems)) {
            return;
        }
        dataItems.forEach(DataInjector::inject);
    }
}
