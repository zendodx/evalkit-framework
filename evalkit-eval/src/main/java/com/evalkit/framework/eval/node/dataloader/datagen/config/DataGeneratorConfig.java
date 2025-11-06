package com.evalkit.framework.eval.node.dataloader.datagen.config;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.node.dataloader.datagen.exporter.ExcelGenDataExporter;
import com.evalkit.framework.eval.node.dataloader.datagen.exporter.GenDataExporter;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
public class DataGeneratorConfig {
    /* 是否导出数据 */
    @Builder.Default
    protected boolean enableOutputFile = false;
    /* 导出文件路径 */
    @Builder.Default
    protected String outputFilePath = "attaches";
    /* 导出文件名 */
    @Builder.Default
    protected String outputFileName = "gendata_" + DateUtils.nowToString("yyyyMMddHHmmss");
    /* 文件导出器列表 */
    @Builder.Default
    protected List<GenDataExporter> genDataExporterList = ListUtils.of(new ExcelGenDataExporter());
}
