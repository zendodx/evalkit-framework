package com.evalkit.framework.common.client.deepseek.request.chat;

import com.evalkit.framework.common.client.deepseek.request.chat.message.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeepseekChatCompletionsRequest {
    // 对话的消息列表
    private List<Message> messages;
    // 您可以使用 deepseek-chat
    private String model;
    // 介于 -2.0 和 2.0 之间的数字。如果该值为正，那么新 token 会根据其在已有文本中的出现频率受到相应的惩罚，降低模型重复相同内容的可能性。
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;
    // 介于 1 到 8192 间的整数，限制一次请求中模型生成 completion 的最大 token 数。输入 token 和输出 token 的总长度受模型的上下文长度的限制。
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    // 介于 -2.0 和 2.0 之间的数字。如果该值为正，那么新 token 会根据其是否已在已有文本中出现受到相应的惩罚，从而增加模型谈论新主题的可能性。
    @JsonProperty("presence_penalty")
    private Integer presencePenalty;
    // 一个 object，指定模型必须输出的格式。
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;
    // 一个 string 或最多包含 16 个 string 的 list，在遇到这些词时，API 将停止生成更多的 token。
    private String[] stop;
    // 如果设置为 True，将会以 SSE（server-sent events）的形式以流式发送消息增量。
    private Boolean stream;
    // 流式输出相关选项。只有在 stream 参数为 true 时，才可设置此参数。
    @JsonProperty("stream_options")
    private StreamOptions streamOptions;
    // 采样温度，介于 0 和 2 之间。更高的值，如 0.8，会使输出更随机，而更低的值，如 0.2，会使其更加集中和确定。
    private Double temperature;
    // 作为调节采样温度的替代方案，模型会考虑前 top_p 概率的 token 的结果。所以 0.1 就意味着只有包括在最高 10% 概率中的 token 会被考虑。
    @JsonProperty("top_p")
    private Double topP;
    // 模型可能会调用的 tool 的列表。目前，仅支持 function 作为工具。使用此参数来提供以 JSON 作为输入参数的 function 列表。最多支持 128 个 function。
    private List<Tool> tools;
    // 控制模型调用 tool 的行为。
    @JsonProperty("tool_choice")
    private Object toolChoice;
    // 是否返回所输出 token 的对数概率。如果为 true，则在 message 的 content 中返回每个输出 token 的对数概率。
    private Boolean logprobs;
    // 一个介于 0 到 20 之间的整数 N，指定每个输出位置返回输出概率 top N 的 token，且返回这些 token 的对数概率。
    @JsonProperty("top_logprobs")
    private Integer topLogprobs;
}
