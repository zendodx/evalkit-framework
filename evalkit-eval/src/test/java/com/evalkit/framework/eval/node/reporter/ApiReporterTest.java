package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.DataItem;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiReporterTest {

    /**
     * 测试 ApiReporter 可以正常构建，不发起真实 HTTP 请求
     */
    @Test
    void testConstructApiReporter() {
        String host = "http://localhost:8080";
        String api = "/api/test";
        String method = "POST";
        ApiReporter apiReporter = new ApiReporter(host, api, method) {
            @Override
            public Map<String, Object> prepareBody(DataItem item) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> prepareHeader(DataItem item) {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String[]> prepareParams(DataItem item) {
                return Collections.emptyMap();
            }
        };

        assertNotNull(apiReporter, "ApiReporter 实例不应为 null");
        assertNotNull(apiReporter.getRequest(), "ApiReporter 的 request 不应为 null");
        assertEquals(host, apiReporter.getRequest().getHost(), "Host 应与构建时一致");
        assertEquals(api, apiReporter.getRequest().getApi(), "API 路径应与构建时一致");
    }

    /**
     * 测试 prepareBody/prepareHeader/prepareParams 可以正确返回空 Map
     */
    @Test
    void testPrepareMethods() {
        ApiReporter apiReporter = new ApiReporter("http://localhost:8080", "/api/report", "POST") {
            @Override
            public Map<String, Object> prepareBody(DataItem item) {
                return Collections.singletonMap("key", "value");
            }

            @Override
            public Map<String, String> prepareHeader(DataItem item) {
                return Collections.singletonMap("Content-Type", "application/json");
            }

            @Override
            public Map<String, String[]> prepareParams(DataItem item) {
                return Collections.emptyMap();
            }
        };

        DataItem dataItem = new DataItem();
        Map<String, Object> body = apiReporter.prepareBody(dataItem);
        assertNotNull(body, "prepareBody 不应返回 null");
        assertEquals("value", body.get("key"));

        Map<String, String> headers = apiReporter.prepareHeader(dataItem);
        assertNotNull(headers, "prepareHeader 不应返回 null");
        assertEquals("application/json", headers.get("Content-Type"));
    }
}