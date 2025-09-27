package com.evalkit.framework.common.client.http;

import com.evalkit.framework.common.client.http.model.HttpApiRequest;
import com.evalkit.framework.common.client.http.model.HttpApiResponse;
import com.evalkit.framework.common.utils.http.HttpUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Http调用客户端
 */
@Slf4j
@Data
public class HttpApiClient {
    private final long timeout;
    private final TimeUnit timeUnit;
    private final HttpApiRequest request;

    private HttpApiRequest buildRequest(String host, String api, String method) {
        return HttpApiRequest.builder()
                .host(host)
                .api(api)
                .method(method)
                .build();
    }

    public HttpApiClient(long timeout, TimeUnit timeUnit, String host, String api, String method) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.request = buildRequest(host, api, method);
    }

    public void setHost(String host) {
        request.setHost(host);
    }

    public void setApi(String api) {
        request.setApi(api);
    }

    public void setMethod(String method) {
        request.setMethod(method);
    }

    public void setBody(String body) {
        request.setBody(body);
    }

    public void setBody(Map<String, Object> bodyMap) {
        request.setBody(JsonUtils.toJson(bodyMap));
    }

    public void setHeader(Map<String, String> headerMap) {
        request.setHeaders(headerMap);
    }

    public void addHeader(String key, String value) {
        if (request.getHeaders() == null) {
            request.setHeaders(new HashMap<>());
        }
        request.getHeaders().put(key, value);
    }

    public void setParam(Map<String, String[]> paramMap) {
        request.setParams(paramMap);
    }

    public void addParam(String key, String value) {
        if (request.getParams() == null) {
            request.setParams(new HashMap<>());
        }
        String[] split = value.split(",");
        request.getParams().put(key, split);
    }

    public HttpApiResponse invoke() throws IOException {
        if (timeout > 0) {
            HttpUtils.setTimeout(timeout, timeUnit);
        }
        HttpUtils.Resp resp = HttpUtils.invoke(
                request.getUrl(),
                request.getMethod(),
                request.getHeaders(),
                request.getContentTypeFromHeader(),
                request.getParams(),
                request.getBody(),
                request.getParts(),
                request.getPostFormBodyMap());
        return HttpApiResponse.builder()
                .statusCode(resp.getCode())
                .body(resp.getBody())
                .message(resp.getMessage())
                .url(resp.getUrl())
                .build();
    }
}
