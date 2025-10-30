package com.evalkit.framework.eval.node.counter;

import com.evalkit.framework.infra.service.llm.LLMServiceFactory;

class AttributeCounterTest {
    void test() {
        AttributeCounter attributeCounter = new AttributeCounter(
                LLMServiceFactory.createLLMService("test", null)
        );
    }
}