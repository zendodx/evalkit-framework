package com.evalkit.framework.eval.node.dataloader.datagen.querygen;

import java.util.List;

/**
 * Query生成器
 */
public interface QueryGenerator {
    /**
     * 生成Query
     *
     * @return 生成的Query
     */
    List<String> generate();
}
