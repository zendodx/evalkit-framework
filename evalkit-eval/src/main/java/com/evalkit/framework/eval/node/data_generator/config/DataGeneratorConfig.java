package com.evalkit.framework.eval.node.data_generator.config;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.node.data_generator.exporter.ExcelGenDataExporter;
import com.evalkit.framework.eval.node.data_generator.exporter.GenDataExporter;
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
    protected String outputFilePath = "attachments";
    /* 导出文件名 */
    @Builder.Default
    protected String outputFileName = "export_" + DateUtils.nowToString("yyyyMMddHHmmss");
    /* 文件导出器列表 */
    @Builder.Default
    protected List<GenDataExporter> genDataExporterList = ListUtils.of(new ExcelGenDataExporter());
    /* 生成并发数 */
    @Builder.Default
    protected Integer threadNum = 1;
}
