package com.evalkit.framework.eval.node.dataloader;


import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class JsonDataLoader extends DataLoader {

    protected DataLoaderConfig config;

    public JsonDataLoader() {
        super();
    }

    public JsonDataLoader(DataLoaderConfig config) {
        super(config);
        this.config = config;
    }

    /**
     * 准备目标数据所在的jsonpath
     */
    public abstract String prepareJsonpath();

    /**
     * 准备要要加载的json数据
     */
    public abstract String prepareJson();

    protected List<InputData> parseJson(String json, String jsonPath) {
        List<InputData> inputDataList = new ArrayList<>();
        Object eval = JsonUtils.fromJson(json, jsonPath, Object.class);
        if (eval instanceof List) {
            List<?> list = (List<?>) eval;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                InputData inputData = new InputData();
                inputData.setInputItem(MapUtils.beanToMap(item));
                inputData.setDataIndex((long) i);
                inputDataList.add(inputData);
            }
        } else if (eval instanceof Map) {
            InputData inputData = new InputData();
            inputData.setInputItem(MapUtils.beanToMap(eval));
            inputData.setDataIndex(0L);
            inputDataList.add(inputData);
        } else {
            throw new EvalException("Prepare json data unsupported parse value: " + eval);
        }
        return inputDataList;
    }
}
