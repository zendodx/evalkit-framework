package com.evalkit.framework.common.client.deepseek.request.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StreamOptions {
    // 如果设置为 true，在流式消息最后的 data: [DONE] 之前将会传输一个额外的块。
    // 此块上的 usage 字段显示整个请求的 token 使用统计信息，而 choices 字段将始终是一个空数组。所有其他块也将包含一个 usage 字段，但其值为 null。
    @JsonProperty("include_usage")
    private Boolean includeUsage;
}
