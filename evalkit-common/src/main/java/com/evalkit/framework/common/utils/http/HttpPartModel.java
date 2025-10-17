package com.evalkit.framework.common.utils.http;


import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class HttpPartModel {
    private Map<String, List<String>> headers;
    private String name;
    private String contentType;
    private String bytes;

    public static HttpPartModelBuilder builder() {
        return new HttpPartModelBuilder();
    }

    public HttpPartModel(Map<String, List<String>> headers, String name, String contentType, String bytes) {
        this.headers = headers;
        this.name = name;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    public HttpPartModel() {
    }

    public static class HttpPartModelBuilder {
        private Map<String, List<String>> headers;
        private String name;
        private String contentType;
        private String bytes;

        HttpPartModelBuilder() {
        }

        public HttpPartModelBuilder headers(Map<String, List<String>> headers) {
            this.headers = headers;
            return this;
        }

        public HttpPartModelBuilder name(String name) {
            this.name = name;
            return this;
        }

        public HttpPartModelBuilder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public HttpPartModelBuilder bytes(String bytes) {
            this.bytes = bytes;
            return this;
        }

        public HttpPartModel build() {
            return new HttpPartModel(this.headers, this.name, this.contentType, this.bytes);
        }

        public String toString() {
            return "HttpPartModel.HttpPartModelBuilder(headers=" + this.headers + ", name=" + this.name + ", contentType=" + this.contentType + ", bytes=" + this.bytes + ")";
        }
    }
}
