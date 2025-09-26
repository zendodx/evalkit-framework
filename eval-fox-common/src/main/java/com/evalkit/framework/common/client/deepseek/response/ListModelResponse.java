package com.evalkit.framework.common.client.deepseek.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ListModelResponse {
    private String object;
    private List<Model> data;

    @Data
    static class Model {
        private String id;  // 模型的标识符
        private String object;  // 对象的类型
        @JsonProperty("owned_by")
        private String ownedBy;     // 拥有该模型的组织
    }
}
