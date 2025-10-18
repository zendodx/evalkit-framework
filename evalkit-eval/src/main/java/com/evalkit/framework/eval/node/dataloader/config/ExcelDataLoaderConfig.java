package com.evalkit.framework.eval.node.dataloader.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Excel数据加载器配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class ExcelDataLoaderConfig extends DataLoaderConfig {
    // 文件路径, 支持三种形式:绝对路径,classpath:路径,远程文件
    protected String filePath;
    // sheet页,从0开始
    protected int sheetIndex;
}
