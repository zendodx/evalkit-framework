package com.evalkit.framework.eval.node.dataloader.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * json文件数据加载器配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class JsonFileDataLoaderConfig extends DataLoaderConfig {
    /* json文件路径, 支持三种形式:绝对路径,classpath:路径,远程文件 */
    private String filePath;
    /* 要提取的json字段 */
    @Builder.Default
    private String jsonPath = "$";
}
