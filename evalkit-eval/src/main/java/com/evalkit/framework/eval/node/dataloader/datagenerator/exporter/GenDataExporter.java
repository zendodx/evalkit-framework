package com.evalkit.framework.eval.node.dataloader.datagenerator.exporter;

import com.evalkit.framework.eval.model.InputData;

import java.util.List;

/**
 * 生成数据导出器
 */
public interface GenDataExporter {
    /**
     * 导出数据
     *
     * @param inputDataList 待导出的生成数据
     * @param outputPath    导出路径
     * @param fileName      导出文件名
     * @return 导出后的文件路径
     */
    String export(List<InputData> inputDataList, String outputPath, String fileName);
}
