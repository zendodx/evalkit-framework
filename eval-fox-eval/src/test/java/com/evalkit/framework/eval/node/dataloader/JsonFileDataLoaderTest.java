package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.JsonFileDataLoaderConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
class JsonFileDataLoaderTest {

    private String jsonObjectFilePath;
    private String jsonArrayFilePath;

    /**
     * 构造Json临时文件
     */
    @BeforeEach
    public void setUp() throws IOException {
        String j1 = "{\"code\":0,\"success\":true,\"data\":{\t\"query\":\"hello\",\"type\":\"test\"}}";
        String j2 = "{\"code\":0,\"success\":true,\"data\":[{\"query\":\"hello\",\"type\":\"test\"},{\"query\":\"hi\",\"type\":\"test\"}]}";

        Path jsonObjectTempFile = Files.createTempFile("temp", ".json");
        jsonObjectFilePath = jsonObjectTempFile.toString();
        Path jsonArrayTempFile = Files.createTempFile("temp", ".json");
        jsonArrayFilePath = jsonArrayTempFile.toString();
        JsonUtils.writeJsonFile(jsonObjectFilePath, JsonUtils.fromJson(j1, Map.class));
        JsonUtils.writeJsonFile(jsonArrayFilePath, JsonUtils.fromJson(j2, Map.class));
    }

    /**
     * 执行删除临时文件
     */
    @AfterEach
    public void tearDown() {
        FileUtils.deleteFile(jsonObjectFilePath);
        FileUtils.deleteFile(jsonArrayFilePath);
    }

    @Test
    public void testLoadJsonObject() throws Exception {
        JsonFileDataLoader dataLoader = new JsonFileDataLoader(
                JsonFileDataLoaderConfig.builder()
                        .jsonPath("$")
                        .filePath(jsonObjectFilePath)
                        .build()
        );
        List<InputData> inputData = dataLoader.prepareDataList();
        log.info("Json File DataLoader: {}", inputData);
        Assertions.assertEquals(1, inputData.size());
    }

    @Test
    public void testLoadJsonObjectWithJsonpath() throws Exception {
        JsonFileDataLoader dataLoader = new JsonFileDataLoader(
                JsonFileDataLoaderConfig.builder()
                        .jsonPath("$.data")
                        .filePath(jsonObjectFilePath)
                        .build()
        );
        List<InputData> inputData = dataLoader.prepareDataList();
        log.info("Json File DataLoader: {}", inputData);
        Assertions.assertEquals(1, inputData.size());
    }

    @Test
    public void testLoadJsonArray() throws Exception {
        JsonFileDataLoader dataLoader = new JsonFileDataLoader(
                JsonFileDataLoaderConfig.builder()
                        .jsonPath("$.data")
                        .filePath(jsonArrayFilePath)
                        .build()
        );
        List<InputData> inputData = dataLoader.prepareDataList();
        log.info("Json File DataLoader: {}", inputData);
        Assertions.assertEquals(2, inputData.size());
    }
}