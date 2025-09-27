package com.evalkit.framework.eval.node.dataloader;


import com.evalkit.framework.eval.model.InputData;

import java.io.IOException;
import java.util.List;

/**
 * Json文本数据加载器
 */
public abstract class JsonTextDataLoader extends JsonDataLoader {
    /**
     * 准备目标数据所在的jsonpath
     */
    public abstract String prepareJsonpath();

    /**
     * 准备要要加载的json数据
     */
    public abstract String prepareJson();

    @Override
    public List<InputData> prepareDataList() throws IOException {
        String jsonStr = prepareJson();
        String jsonPath = prepareJsonpath();
        return parseJson(jsonStr, jsonPath);
    }
}
