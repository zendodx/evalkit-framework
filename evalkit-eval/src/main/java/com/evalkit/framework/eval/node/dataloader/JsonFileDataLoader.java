package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.JsonFileDataLoaderConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * json文件数据加载器
 */
public class JsonFileDataLoader extends JsonDataLoader {
    protected JsonFileDataLoaderConfig config;

    public JsonFileDataLoader(JsonFileDataLoaderConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
    }

    protected void validConfig(JsonFileDataLoaderConfig config) {
        if (StringUtils.isEmpty(config.getFilePath())) {
            throw new IllegalArgumentException("filePath is empty");
        }
        if (StringUtils.isEmpty(config.getJsonPath())) {
            throw new IllegalArgumentException("jsonpath is empty");
        }
    }

    @Override
    public String prepareJsonpath() {
        return config.getJsonPath();
    }

    @Override
    public String prepareJson() {
        // 读取Json文件内容,获取Json字符串
        try (InputStream inputStream = FileUtils.getInputStream(config.getFilePath())) {
            Map<String, Object> tmp = JsonUtils.readJsonStream(inputStream, new TypeReference<Map<String, Object>>() {
            });
            return JsonUtils.toJson(tmp);
        } catch (Exception e) {
            throw new EvalException("Read json file error", e);
        }
    }

    @Override
    public List<InputData> prepareDataList() throws Exception {
        return parseJson(prepareJson(), prepareJsonpath());
    }
}
