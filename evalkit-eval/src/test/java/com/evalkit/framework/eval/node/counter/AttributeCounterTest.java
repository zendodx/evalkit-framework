package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.CountResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.evalkit.framework.infra.utils.DebugUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class AttributeCounterTest {
    @Test
    @Disabled
    public void test() {
        LLMService llmService = DebugUtils.buildLLMService();
        List<DataItem> dataItems = JsonUtils.readJsonFile("classpath:dataItems.json", new TypeReference<List<DataItem>>() {
        });
        dataItems = dataItems.subList(0, 2);
        AttributeCounter counter = new AttributeCounter(llmService);
        CountResult countResult = counter.count(dataItems);
        log.info("countResult: {}", JsonUtils.toJson(countResult));
    }
}