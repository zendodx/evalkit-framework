package com.evalkit.framework.common.client.sse;

import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.BufferedSource;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * SSE流式接口客户端
 */
@Slf4j
public final class OkHttpStreamClient {
    private final String method;
    private final Map<String, String> cookie;
    private OkHttpClient client;
    /* 流式输出结束的标记 */
    private final FishedCheckStrategy fishedCheckStrategy;
    private boolean isStdPrint = false;

    public OkHttpStreamClient(String method, FishedCheckStrategy fishedCheckStrategy, boolean isStdPrint) {
        this(method, new HashMap<>(), 60L, fishedCheckStrategy, isStdPrint);
    }

    public OkHttpStreamClient(String method, Map<String, String> cookie, FishedCheckStrategy fishedCheckStrategy, boolean isStdPrint) {
        this(method, cookie, 60L, fishedCheckStrategy, isStdPrint);
    }

    public OkHttpStreamClient(String method, Map<String, String> cookie, Long timeout, FishedCheckStrategy fishedCheckStrategy, boolean isStdPrint) {
        this.method = method.toUpperCase();
        this.cookie = cookie;
        this.fishedCheckStrategy = fishedCheckStrategy;
        this.isStdPrint = isStdPrint;
        initClient(timeout);
    }

    private void initClient(Long timeout) {
        client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .callTimeout(Duration.ofSeconds(timeout))
                .readTimeout(Duration.ofSeconds(timeout))
                .connectTimeout(Duration.ofSeconds(timeout))
                .writeTimeout(Duration.ofSeconds(timeout))
                .build();
    }

    public StreamResponse proposeRequest(Map<String, Object> body,
                                         Map<String, String> headers,
                                         Map<String, Object> params,
                                         String url) throws IOException {

        String finalUrl = buildUrl(url, params);

        Request.Builder builder = new Request.Builder()
                .url(finalUrl)
                .method(method, body == null ? null : RequestBody.create(MediaType.parse("application/json"), toJson(body)));

        builder.addHeader("Cookie", buildCookieHeader());

        Optional.ofNullable(headers).orElse(new HashMap<>())
                .forEach(builder::addHeader);

        Request request = builder.build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Unexpected code " + resp);
            }
            StringBuilder sb = new StringBuilder();
            assert resp.body() != null;
            try (BufferedSource source = resp.body().source()) {
                while (!source.exhausted()) {
                    String chunk = source.readUtf8Line();
                    sb.append(chunk).append('\n');
                    if (isStdPrint) {
                        // 实时显示接口输出
                        System.out.print(chunk);
                        System.out.println();
                        System.out.flush();
                    }
                    if (fishedCheckStrategy.isFinished(chunk)) {
                        break;
                    }
                }
            }

            return new StreamResponse(sb.toString(), resp.code(), resp.headers().toMultimap());
        }
    }

    private String buildUrl(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return url;
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        params.forEach((k, v) -> urlBuilder.addQueryParameter(k, String.valueOf(v)));
        return urlBuilder.build().toString();
    }

    private String buildCookieHeader() {
        StringBuilder sb = new StringBuilder();
        cookie.forEach((k, v) -> sb.append(k).append('=').append(v).append("; "));
        return sb.toString();
    }

    private static String toJson(Map<String, Object> map) {
        return JsonUtils.toJson(map);
    }

    public static final class StreamResponse {
        public final String content;
        public final int statusCode;
        public final Map<String, java.util.List<String>> headers;

        StreamResponse(String content, int statusCode, Map<String, java.util.List<String>> headers) {
            this.content = content;
            this.statusCode = statusCode;
            this.headers = headers;
        }

        public String getContent() {
            return content;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
