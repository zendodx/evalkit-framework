package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.file.ExcelUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Excel文件上报
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ExcelReporter extends FileReporter {
    public ExcelReporter(String fileName) {
        this(fileName, null);
    }

    public ExcelReporter(String fileName, String parentDir) {
        super(fileName, parentDir);
    }

    @Override
    public void report(ReportData reportData) {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();
        String outputFilename = StringUtils.isNotBlank(this.fileName) ? this.fileName : generateDefaultOutputFileName();
        String dataItemsFileName = String.format("%s/%s.xlsx", this.parentDir, outputFilename);
        ExcelUtils.writeExcel(dataItemsFileName, convertDataItems(dataItems), true);
        String countFileName = String.format("%s/%s.count.xlsx", this.parentDir, outputFilename);
        ExcelUtils.writeExcel(countFileName, convertCountResult(countResultMap), true);
    }
}
