package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.JsonFileDataLoaderConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * json文件数据加载器
 * <p>
 * 1. json对象加载数据, 支持jsonpath提取, 默认jsonpath为$
 * 2. json数组加载数据, 不需要设置jsonpath
 */
@Slf4j
public class JsonFileDataLoader extends JsonDataLoader {
    protected JsonFileDataLoaderConfig config;

    public JsonFileDataLoader(JsonFileDataLoaderConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(JsonFileDataLoaderConfig config) {
        if (StringUtils.isEmpty(config.getFilePath())) {
            throw new IllegalArgumentException("JsonFileDataLoaderConfig invalid: filePath is empty");
        }
        if (StringUtils.isEmpty(config.getJsonPath())) {
            throw new IllegalArgumentException("JsonFileDataLoaderConfig invalid: jsonpath is empty");
        }
    }

    @Override
    public String prepareJsonpath() {
        // Json文件的数据注入,默认取$.dataItems
        if (config.isOpenInjectData()) {
            return "$.dataItems";
        }
        return config.getJsonPath();
    }

    @Override
    public String prepareJson() throws IOException {
        // 读取Json文件内容,获取Json字符串
        try (InputStream inputStream = FileUtils.getInputStream(config.getFilePath())) {
            Object jsonObj = JsonUtils.readJsonStream(inputStream, new TypeReference<Object>() {
            });
            return JsonUtils.toJson(jsonObj);
        } catch (IOException e) {
            log.error("[JsonFileDataLoader] Read json file failed, filePath: {}, error: {}",
                    config.getFilePath(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<InputData> prepareDataList() throws Exception {
        return parseJson(prepareJson(), prepareJsonpath());
    }
}
