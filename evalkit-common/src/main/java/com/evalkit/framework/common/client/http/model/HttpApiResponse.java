package com.evalkit.framework.common.client.http.model;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Http响应
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class HttpApiResponse {
    private int statusCode;
    private String body;
    private String message;
    private String url;

    public Map<String, Object> getBodyMap() {
        if (StringUtils.isEmpty(body)) {
            return null;
        }
        try {
            return JsonUtils.fromJson(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.error("Parse body map error, body:{}", body, e);
            return null;
        }
    }
}
