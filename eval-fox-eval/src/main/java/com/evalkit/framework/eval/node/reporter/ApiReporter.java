package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.common.client.http.model.HttpApiRequest;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.http.HttpUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * API上报,可上报到自定义管理后台
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ApiReporter extends Reporter {
    protected long timeout;
    protected TimeUnit timeUnit;
    protected final HttpApiRequest request;

    public ApiReporter(String host, String api, String method) {
        this.request = HttpApiRequest.builder()
                .host(host)
                .api(api)
                .method(method)
                .build();
    }

    public ApiReporter(String host, String api, String method, long timeout, TimeUnit timeUnit) {
        this.request = HttpApiRequest.builder()
                .host(host)
                .api(api)
                .method(method)
                .build();
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    public void setTimeout(long timeout, TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    public abstract Map<String, Object> prepareBody(DataItem item);

    public abstract Map<String, String> prepareHeader(DataItem item);

    public abstract Map<String, String[]> prepareParams(DataItem item);

    @Override
    public void report(ReportData reportData) throws IOException {
        List<DataItem> items = reportData.getDataItems();
        if (timeout > 0) {
            HttpUtils.setTimeout(timeout, timeUnit);
        }
        for (DataItem item : items) {
            request.setBody(JsonUtils.toJson(prepareBody(item)));
            request.setHeaders(prepareHeader(item));
            request.setParams(prepareParams(item));
            try {
                HttpUtils.Resp resp = HttpUtils.invoke(
                        request.getUrl(),
                        request.getMethod(),
                        request.getHeaders(),
                        request.getContentTypeFromHeader(),
                        request.getParams(),
                        request.getBody(),
                        request.getParts(),
                        request.getPostFormBodyMap()
                );
                log.info("Api reporter success, request:{}, response: {}", request, resp);
            } catch (Exception e) {
                log.error("Api reporter failed, request:{} error:", request, e);
                throw e;
            }
        }
    }
}
