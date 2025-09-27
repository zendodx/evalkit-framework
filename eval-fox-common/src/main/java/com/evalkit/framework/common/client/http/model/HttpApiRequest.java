package com.evalkit.framework.common.client.http.model;


import com.evalkit.framework.common.utils.http.HttpPartModel;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Http请求
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class HttpApiRequest {
    private String host;
    private String api;
    private String method;
    private String body;
    private String contentType;
    private Map<String, String> headers;
    private Map<String, String[]> params;
    private List<HttpPartModel> parts;
    private Map<String, String[]> postFormBodyMap;

    public String getContentTypeFromHeader() {
        if (headers == null) {
            return "";
        }
        String contentType = headers.getOrDefault("Content-Type", null);
        return null == contentType ? "" : contentType;
    }

    public String getUrl() {
        return host + api;
    }

    public Map<String, Object> getBodyMap() {
        if (StringUtils.isEmpty(body)) {
            return null;
        }
        return JsonUtils.fromJson(body, new TypeReference<Map<String, Object>>() {
        });
    }
}
