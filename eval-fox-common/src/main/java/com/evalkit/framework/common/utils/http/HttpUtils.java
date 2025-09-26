package com.evalkit.framework.common.utils.http;

import com.evalkit.framework.common.utils.hex.HexUtils;
import okhttp3.*;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpUtils {
    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);
    private static final String QUESTION_SEPARATE = "?";
    private static final String PARAM_SEPARATE = "&";
    private static final String KV_SEPARATE = "=";
    private static final String FORM_URL_ENCODED = "application/x-www-form-urlencoded";
    private static OkHttpClient client;

    private HttpUtils() {
    }

    public static Resp doGet(String url) throws IOException {
        return executeRequest((new Request.Builder()).get().url(url).build());
    }

    public static Resp doGetWithHeader(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = (new Request.Builder()).get().url(url);
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header((String) entry.getKey(), (String) entry.getValue());
            }
        }

        return executeRequest(builder.build());
    }

    public static Resp doGet(String url, Map<String, String> params) throws IOException {
        StringBuilder builder = new StringBuilder(url);
        if (!StringUtils.contains(url, "?")) {
            builder.append("?").append("_r=1");
        }

        if (MapUtils.isNotEmpty(params)) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.append("&").append((String) entry.getKey()).append("=").append((String) entry.getValue());
            }
        }

        return doGet(builder.toString());
    }

    public static Resp doPost(String url) throws IOException {
        return doPost(url, null);
    }

    public static Resp doPost(String url, Map<String, String> params) throws IOException {
        FormBody.Builder builder = new FormBody.Builder();
        if (MapUtils.isNotEmpty(params)) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                builder.add(entry.getKey(), entry.getValue());
            }
        }

        Request request = (new Request.Builder()).post(builder.build()).url(url).build();
        return executeRequest(request);
    }

    public static Resp invokeWrapper(String url, String method, Map<String, String> headers, String contentType, Map<String, String> paramsMap, String body, List<HttpPartModel> parts, Map<String, String> postFormBody) throws IOException {
        Map<String, String[]> paramListMap = new HashMap();
        Map<String, String[]> postFormBodyMap = new HashMap();
        if (null != paramsMap) {
            for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
                paramListMap.put(entry.getKey(), new String[]{entry.getValue()});
            }
        }

        if (null != postFormBody) {
            for (Map.Entry<String, String> entry : postFormBody.entrySet()) {
                postFormBodyMap.put(entry.getKey(), new String[]{entry.getValue()});
            }
        }

        return invoke(url, method, headers, contentType, paramListMap, body, parts, postFormBodyMap);
    }

    public static Resp invoke(String url, String method, Map<String, String> headers, String contentType, Map<String, String[]> paramsMap, String body, List<HttpPartModel> parts, Map<String, String[]> postFormBodyMap) throws IOException {
        HttpMethod resolve = HttpMethod.resolve(method);
        if (resolve == null) {
            return Resp.builder().code(500).message("Unsupported http method : " + method).build();
        } else {
            switch (HttpMethod.resolve(method)) {
                case GET:
                case HEAD:
                case TRACE:
                case OPTIONS:
                    return invokeGet(method, url, headers, paramsMap);
                default:
                    return invokeHttpMethod(method, url, headers, contentType, paramsMap, body, parts, postFormBodyMap);
            }
        }
    }

    private static Resp invokeGet(String method, String url, Map<String, String> headers, Map<String, String[]> paramsMap) throws IOException {
        HttpUrl hu = HttpUrl.parse(url);
        if (hu == null) {
            return Resp.builder().code(500).message("Parse http url failed,url=" + url).build();
        } else {
            if (MapUtils.isNotEmpty(paramsMap)) {
                HttpUrl.Builder builder = hu.newBuilder();

                for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                    for (String value : (String[]) entry.getValue()) {
                        builder.addQueryParameter((String) entry.getKey(), value);
                    }
                }

                hu = builder.build();
            }

            Request.Builder rb = (new Request.Builder()).method(method, (RequestBody) null).url(hu);
            if (MapUtils.isNotEmpty(headers)) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    rb.header((String) entry.getKey(), (String) entry.getValue());
                }
            }

            return executeRequest(rb.build());
        }
    }

    private static Resp invokeHttpMethod(String method, String url, Map<String, String> headers, String contentType, Map<String, String[]> paramsMap, String body, List<HttpPartModel> parts, Map<String, String[]> postFormBodyMap) throws IOException {
        if (!StringUtils.startsWith(contentType, "application/x-www-form-urlencoded")) {
            if (MapUtils.isNotEmpty(postFormBodyMap)) {
                StringBuilder stringBuilder = new StringBuilder();

                for (Map.Entry<String, String[]> entry : postFormBodyMap.entrySet()) {
                    for (String value : (String[]) entry.getValue()) {
                        stringBuilder.append("&").append((String) entry.getKey()).append("=").append(value);
                    }
                }

                body = stringBuilder.substring(1);
            }

            return invokePostBody(method, url, headers, contentType, paramsMap, body, parts);
        } else {
            StringBuilder urlBuilder = new StringBuilder(url);
            if (MapUtils.isEmpty(postFormBodyMap)) {
                postFormBodyMap = paramsMap;
            } else {
                urlBuilder = appendQueryString(url, paramsMap);
            }

            Request.Builder rb;
            if (StringUtils.isNotBlank(body)) {
                rb = (new Request.Builder()).method(method, RequestBody.create(MediaType.get(contentType), body)).url(urlBuilder.toString());
            } else {
                FormBody.Builder fb = new FormBody.Builder();
                if (MapUtils.isNotEmpty(postFormBodyMap)) {
                    for (Map.Entry<String, String[]> entry : postFormBodyMap.entrySet()) {
                        for (String value : (String[]) entry.getValue()) {
                            fb.add((String) entry.getKey(), value);
                        }
                    }
                }

                rb = (new Request.Builder()).method(method, fb.build()).url(urlBuilder.toString());
            }

            if (MapUtils.isNotEmpty(headers)) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    rb.header((String) entry.getKey(), (String) entry.getValue());
                }
            }

            return executeRequest(rb.build());
        }
    }

    public static Resp invokePostBody(String method, String url, Map<String, String> headers, String contentType, Map<String, String[]> paramMap, String body, List<HttpPartModel> parts) throws IOException {
        StringBuilder urlBuilder = appendQueryString(url, paramMap);
        RequestBody b = null;
        if (CollectionUtils.isNotEmpty(parts)) {
            b = buildFileUploadRequestBody(contentType, parts);
        } else if (null != contentType) {
            body = null == body ? "" : body;
            b = RequestBody.create(MediaType.parse(contentType), body);
        }

        Request.Builder rb = (new Request.Builder()).method(method, b).url(urlBuilder.toString());
        if (MapUtils.isNotEmpty(headers)) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                rb.header((String) entry.getKey(), (String) entry.getValue());
            }
        }

        return executeRequest(rb.build());
    }

    private static RequestBody buildFileUploadRequestBody(String contentType, List<HttpPartModel> partModels) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();

        for (HttpPartModel partModel : partModels) {
            bodyBuilder.addPart(buildPart(partModel));
        }

        MediaType mediaType = MediaType.parse(contentType);
        bodyBuilder.setType(mediaType);
        return bodyBuilder.build();
    }

    private static MultipartBody.Part buildPart(HttpPartModel partModel) {
        Headers.Builder headersBuilder = new Headers.Builder();

        for (Map.Entry<String, List<String>> entry : partModel.getHeaders().entrySet()) {
            for (String value : entry.getValue()) {
                headersBuilder.add(entry.getKey(), value);
            }
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse(partModel.getContentType()), HexUtils.fromHexString(partModel.getBytes()));
        return MultipartBody.Part.create(headersBuilder.build(), requestBody);
    }

    private static Resp executeRequest(Request request) throws IOException {
        Response response = client.newCall(request).execute();
        return Resp.builder().code(response.code()).message(response.message()).body(bodyToString(response.body())).url(response.request().url().url().toString()).build();
    }

    private static StringBuilder appendQueryString(String url, Map<String, String[]> paramMap) {
        StringBuilder urlBuilder = new StringBuilder(url);
        if (MapUtils.isNotEmpty(paramMap)) {
            if (!StringUtils.contains(url, "?")) {
                urlBuilder.append("?");
            }

            for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
                for (String value : (String[]) entry.getValue()) {
                    urlBuilder.append((String) entry.getKey()).append("=").append(value).append("&");
                }
            }

            urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        }

        return urlBuilder;
    }

    private static String bodyToString(ResponseBody body) throws IOException {
        return body == null ? "" : body.string();
    }

    public static void setTimeout(long timeout, TimeUnit unit) {
        setTimeout(timeout, timeout, timeout, unit);
    }

    public static void setTimeout(long connectTimeout, long readTimeout, long writeTimeout, TimeUnit unit) {
        client = client.newBuilder().connectTimeout(connectTimeout, unit).readTimeout(readTimeout, unit).writeTimeout(writeTimeout, unit).build();
    }


    static {
        client = (new OkHttpClient()).newBuilder().connectTimeout(10L, TimeUnit.SECONDS).readTimeout(30L, TimeUnit.SECONDS).writeTimeout(30L, TimeUnit.SECONDS).build();

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }};
            SSLContext trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init((KeyManager[]) null, trustAllCerts, new SecureRandom());
            SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();
            client = client.newBuilder().sslSocketFactory(trustAllSslSocketFactory, (X509TrustManager) trustAllCerts[0]).hostnameVerifier((s, sslSession) -> true).build();
        } catch (Throwable e) {
            log.error("certs init error, reason: {}", e.getMessage(), e);
        }

    }

    static enum HttpMethod {
        GET,
        HEAD,
        POST,
        PUT,
        PATCH,
        DELETE,
        OPTIONS,
        TRACE;

        private static final Map<String, HttpMethod> CACHED = new HashMap(16);

        private HttpMethod() {
        }

        public static HttpMethod resolve(String method) {
            return method != null ? (HttpMethod) CACHED.get(method) : null;
        }

        public boolean matches(String method) {
            return this == resolve(method);
        }

        static {
            for (HttpMethod httpMethod : values()) {
                CACHED.put(httpMethod.name(), httpMethod);
            }

        }
    }

    public static class Resp {
        private int code;
        private String body;
        private String message;
        private String url;

        public static RespBuilder builder() {
            return new RespBuilder();
        }

        public int getCode() {
            return this.code;
        }

        public String getBody() {
            return this.body;
        }

        public String getMessage() {
            return this.message;
        }

        public String getUrl() {
            return this.url;
        }

        public void setCode(final int code) {
            this.code = code;
        }

        public void setBody(final String body) {
            this.body = body;
        }

        public void setMessage(final String message) {
            this.message = message;
        }

        public void setUrl(final String url) {
            this.url = url;
        }

        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof Resp)) {
                return false;
            } else {
                Resp other = (Resp) o;
                if (!other.canEqual(this)) {
                    return false;
                } else if (this.getCode() != other.getCode()) {
                    return false;
                } else {
                    Object this$body = this.getBody();
                    Object other$body = other.getBody();
                    if (this$body == null) {
                        if (other$body != null) {
                            return false;
                        }
                    } else if (!this$body.equals(other$body)) {
                        return false;
                    }

                    Object this$message = this.getMessage();
                    Object other$message = other.getMessage();
                    if (this$message == null) {
                        if (other$message != null) {
                            return false;
                        }
                    } else if (!this$message.equals(other$message)) {
                        return false;
                    }

                    Object this$url = this.getUrl();
                    Object other$url = other.getUrl();
                    if (this$url == null) {
                        if (other$url != null) {
                            return false;
                        }
                    } else if (!this$url.equals(other$url)) {
                        return false;
                    }

                    return true;
                }
            }
        }

        protected boolean canEqual(final Object other) {
            return other instanceof Resp;
        }

        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            result = result * 59 + this.getCode();
            Object $body = this.getBody();
            result = result * 59 + ($body == null ? 43 : $body.hashCode());
            Object $message = this.getMessage();
            result = result * 59 + ($message == null ? 43 : $message.hashCode());
            Object $url = this.getUrl();
            result = result * 59 + ($url == null ? 43 : $url.hashCode());
            return result;
        }

        public String toString() {
            return "HttpUtils.Resp(code=" + this.getCode() + ", body=" + this.getBody() + ", message=" + this.getMessage() + ", url=" + this.getUrl() + ")";
        }

        public Resp(final int code, final String body, final String message, final String url) {
            this.code = code;
            this.body = body;
            this.message = message;
            this.url = url;
        }

        public Resp() {
        }

        public static class RespBuilder {
            private int code;
            private String body;
            private String message;
            private String url;

            RespBuilder() {
            }

            public RespBuilder code(final int code) {
                this.code = code;
                return this;
            }

            public RespBuilder body(final String body) {
                this.body = body;
                return this;
            }

            public RespBuilder message(final String message) {
                this.message = message;
                return this;
            }

            public RespBuilder url(final String url) {
                this.url = url;
                return this;
            }

            public Resp build() {
                return new Resp(this.code, this.body, this.message, this.url);
            }

            public String toString() {
                return "HttpUtils.Resp.RespBuilder(code=" + this.code + ", body=" + this.body + ", message=" + this.message + ", url=" + this.url + ")";
            }
        }
    }
}
