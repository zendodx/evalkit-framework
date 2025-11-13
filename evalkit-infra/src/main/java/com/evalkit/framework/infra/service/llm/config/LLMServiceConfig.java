package com.evalkit.framework.infra.service.llm.config;

import com.evalkit.framework.infra.service.llm.constants.LLMResponseType;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 大模型服务基础配置
 */
@SuperBuilder
@Data
public class LLMServiceConfig {
    /* 大模型版本或类型 */
    protected String model;
    /* 最大token限制,控制模型最多生成多少个 token
    默认值：4068 token
    */
    @Builder.Default
    protected long maxTokens = 4068;
    /* 温度,控制生成文本的随机性,0 ~ 2（常见为 0 ~ 1）,
    越低（如 0.1）：输出越确定、重复、保守,越高（如 1.2）：输出越随机、创意、不可控
    默认值：0.3（偏低，偏向保守、确定性强）
    */
    @Builder.Default
    protected double temperature = 0.3;
    /* 核采样,控制模型在每一步只从概率累积前 topP 的 token 中采样,范围(0 ~ 1) ,
    默认值：0.95（即只从概率累积前 95% 的 token 中选）
    */
    @Builder.Default
    protected double topP = 0.95;
    /* 频率惩罚,降低重复词的出现频率,范围：-2 ~ 2 ,正值：惩罚已经出现过的词，减少重复,负值：鼓励重复，增加重复
    默认 0.0 不使用惩罚
    */
    @Builder.Default
    protected double frequencyPenalty = 0.0;
    /* 存在惩罚,鼓励引入新主题或新词,范围：-2 ~ 2,正值：惩罚已出现过的任何 token，鼓励模型说“新东西”,负值：鼓励模型“围着旧话题转”
    默认 0.0 不使用惩罚
    */
    @Builder.Default
    protected double presencePenalty = 0.0;
    /* 是否开启失败重试 */
    @Builder.Default
    protected boolean openRetry = true;
    /* 重试间隔, 默认10秒 */
    @Builder.Default
    protected long retryInterval = 10;
    /* 重试时间单位 */
    @Builder.Default
    protected TimeUnit retryTimeUnit = TimeUnit.SECONDS;
    /* 最大重试次数, 默认6次 */
    @Builder.Default
    protected int retryTimes = 6;
    /* 大模型回复类型, 默认文本类型 */
    @Builder.Default
    protected LLMResponseType responseType = LLMResponseType.TEXT;
    /* 每百万token输入价格 */
    @Builder.Default
    protected double inPrice = 0.0;
    /* 每百万token输出价格 */
    @Builder.Default
    protected double outPrice = 0.0;
}
