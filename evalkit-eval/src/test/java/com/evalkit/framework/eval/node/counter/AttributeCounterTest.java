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
class AttributeCounterTest {

    /**
     * 构造一个 mock LLMService：
     * - 第一次调用（问题类型提取）：返回 "编号|问题类型" 格式
     * - 后续调用（同义词归一化）：返回合法 JSON 格式
     */
    private LLMService buildMockLLMService() {
        AtomicInteger callCount = new AtomicInteger(0);
        return new LLMService() {
            @Override
            public String chat(String prompt) {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // 第一次：提取问题类型，格式为 "编号|问题类型"
                    return "0|查询机票#价格咨询\n1|预订问题";
                } else {
                    // 后续：同义词归一化，返回合法 JSON
                    return "{\"价格咨询\": [\"查询机票\", \"价格咨询\"], \"预订问题\": [\"预订问题\"]}";
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
        AttributeCounter counter = new AttributeCounter(llmService);
        CountResult countResult = counter.count(dataItems);

        assertNotNull(countResult, "统计结果不应为 null");
        log.info("countResult: {}", JsonUtils.toJson(countResult));
    }
}