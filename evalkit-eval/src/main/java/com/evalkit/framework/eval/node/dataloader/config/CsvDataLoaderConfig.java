package com.evalkit.framework.eval.node.dataloader.config;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Csv数据加载器配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class CsvDataLoaderConfig extends DataLoaderConfig {
    // 文件路径, 支持三种形式:绝对路径,classpath:路径,远程文件
    protected String filePath;
    // 分隔符,默认,
    @Builder.Default
    protected String delimiter = ",";
    // 是否有header
    @Builder.Default
    protected boolean hasHeader = true;
}
