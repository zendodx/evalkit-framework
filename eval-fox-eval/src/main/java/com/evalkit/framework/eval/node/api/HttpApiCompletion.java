package com.evalkit.framework.eval.node.api;

import com.evalkit.framework.common.client.http.HttpApiClient;
import com.evalkit.framework.common.client.http.model.HttpApiRequest;
import com.evalkit.framework.common.client.http.model.HttpApiResponse;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP接口调用器
 */
@Slf4j
public abstract class HttpApiCompletion extends ApiCompletion {
    /* http调用客户端 */
    protected final HttpApiClient client;

    public HttpApiCompletion(String host, String api, String method) {
        this(1, host, api, method);
    }

    public HttpApiCompletion(int threadNum, String host, String api, String method) {
        this(threadNum, 120L, TimeUnit.SECONDS, host, api, method);
    }

    public HttpApiCompletion(long timeout, TimeUnit timeUnit, String host, String api, String method) {
        this(1, timeout, timeUnit, host, api, method);
    }

    public HttpApiCompletion(int threadNum, long timeout, TimeUnit timeUnit, String host, String api, String method) {
        super(threadNum, timeout, timeUnit);
        this.client = new HttpApiClient(timeout, timeUnit, host, api, method);
    }

    public abstract Map<String, Object> prepareBody(InputData inputData);

    public abstract Map<String, String[]> prepareParam(InputData inputData);

    public abstract Map<String, String> prepareHeader(InputData inputData);

    public abstract ApiCompletionResult buildApiCompletionResult(InputData inputData, HttpApiResponse response);

    @Override
    public ApiCompletionResult invoke(DataItem dataItem) throws IOException {
        InputData inputData = dataItem.getInputData();
        Map<String, Object> body = prepareBody(inputData);
        if (body != null) {
            client.setBody(body);
        }
        Map<String, String[]> param = prepareParam(inputData);
        if (param != null) {
            client.setParam(param);
        }
        Map<String, String> header = prepareHeader(inputData);
        if (header != null) {
            client.setHeader(header);
        }
        HttpApiRequest request = client.getRequest();
        try {
            HttpApiResponse response = client.invoke();
            ApiCompletionResult result = buildApiCompletionResult(inputData, response);
            log.info("Invoke api success, request:{}, response:{}, apiCompletionResult: {}", request, response, request);
            return result;
        } catch (Exception e) {
            log.error("Invoke api error, request: {}", request);
            throw e;
        }
    }
}