package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.DataItem;

import java.util.Collections;
import java.util.Map;

class ApiReporterTest {
    void test() {
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
    }
}