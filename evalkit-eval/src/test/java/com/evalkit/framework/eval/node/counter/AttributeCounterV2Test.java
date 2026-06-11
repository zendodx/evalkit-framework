package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
class AttributeCounterV2Test {

    /**
     * 构造一个 mock LLMService，符合 AttributeCounterV2 的期望格式：
     * - 提取阶段：返回 "编号|类别|问题|置信度|情感" 格式（每行5字段，用|分隔）
     * - 归一化阶段：返回合法 JSON（{ "标准名": ["同义名"] } 格式）
     * - 摘要阶段：返回简短文本描述
     */
    private LLMService buildMockLLMService() {
        AtomicInteger callCount = new AtomicInteger(0);
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // 提取阶段：返回 "编号|类别|问题|置信度|情感" 格式
                    return "0|查询问题|机票价格查询|0.9|NEG\n1|预订问题|座位预订失败|0.8|NEG";
                } else if (prompt.contains("合并") || prompt.contains("归一化") || prompt.contains("标准名")) {
                    // 归一化阶段：返回 JSON 格式
                    return "{\"查询问题\": [\"查询问题\"], \"预订问题\": [\"预订问题\"]}";
                } else {
                    // 摘要阶段：返回简短描述
                    return "用户反馈机票查询和预订相关问题";
                }
            }

            @Override
            public String getModel() {
                return "mock-model";
            }
        };
    }

    @Test
    public void test() {
        LLMService llmService = buildMockLLMService();
        // 从 classpath 加载预置测试数据，不依赖外部文件
        List<DataItem> dataItems = JsonUtils.readJsonFile("classpath:dataItems.json", new TypeReference<List<DataItem>>() {
        });
        dataItems = dataItems.subList(0, 2);
        AttributeCounterV2 counter = new AttributeCounterV2(llmService);
        CountResult countResult = counter.count(dataItems);

        assertNotNull(countResult, "统计结果不应为 null");
        log.info("countResult: {}", JsonUtils.toJson(countResult));
    }
}