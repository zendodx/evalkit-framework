package com.evalkit.framework.common.client.http.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SSE响应
 */
@Data
@AllArgsConstructor
public class SSEResponse {
    private List<SSEEvent> events;

    @Data
    @AllArgsConstructor
    public static class SSEEvent {
        private String event;
        private String data;
    }

    public static SSEResponse convertToSSEResponse(String response) {
        if (!isSSEResponse(response)) {
            return null;
        }
        List<SSEEvent> events = new ArrayList<>();
        String[] lines = response.split("\n");
        if (lines.length < 3) {
            return null;
        }
        int p1 = 0, p2 = 1, p3 = 2;
        while (p1 < lines.length && p2 < lines.length) {
            String event = lines[p1].substring(6).trim();
            String data = lines[p2].substring(5).trim();
            events.add(new SSEEvent(event, data));
            p1 = p3 + 1;
            p2 = p3 + 2;
            p3 += 3;
        }
        return new SSEResponse(events);
    }

    public static boolean isSSEResponse(String response) {
        if (StringUtils.isBlank(response)) {
            return false;
        }
        String[] lines = response.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("event:") || line.startsWith("data:")) {
                continue;
            }
            if (StringUtils.isEmpty(line)) {
                continue;
            }
            return false;
        }
        return true;
    }
}
