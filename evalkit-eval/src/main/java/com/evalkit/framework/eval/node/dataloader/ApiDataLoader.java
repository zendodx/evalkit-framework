package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.common.client.http.HttpApiClient;
import com.evalkit.framework.common.client.http.model.HttpApiRequest;
import com.evalkit.framework.common.client.http.model.HttpApiResponse;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.ApiDataLoaderConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 接口数据加载器
 */
@Slf4j
public abstract class ApiDataLoader extends JsonDataLoader {
    /* http客户端 */
    protected final HttpApiClient client;
    protected ApiDataLoaderConfig config;

    public ApiDataLoader(ApiDataLoaderConfig config) {
        this.config = config;
        this.client = new HttpApiClient(config.getTimeout(), TimeUnit.SECONDS, config.getHost(), config.getApi(), config.getMethod());
    }

    /**
     * 准备body
     */
    public abstract Map<String, Object> prepareBody();

    /**
     * 准备请求参数param
     */
    public abstract Map<String, String[]> prepareParam();

    /**
     * 准备headers
     */
    public abstract Map<String, String> prepareHeader();

    @Override
    public List<InputData> prepareDataList() throws IOException {
        return parseJson(prepareJson(), prepareJsonpath());
    }

    @Override
    public String prepareJson() {
        Map<String, Object> body = prepareBody();
        if (body != null) {
            client.setBody(body);
        }
        Map<String, String[]> param = prepareParam();
        if (param != null) {
            client.setParam(param);
        }
        Map<String, String> header = prepareHeader();
        if (header != null) {
            client.setHeader(header);
        }
        HttpApiRequest request = client.getRequest();
        try {
            HttpApiResponse response = client.invoke();
            log.info("Call api success, request: {}, response: {}", request, response);
            return response.getBody();
        } catch (Exception e) {
            log.info("Call api failed, request: {}, error: {}", request, e.getMessage(), e);
            throw new RuntimeException("Call api failed, error: " + e.getMessage(), e);
        }
    }
}
