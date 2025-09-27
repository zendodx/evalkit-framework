package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.file.CsvUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Csv文件上报
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class CsvReporter extends FileReporter {
    /* 分隔符 */
    protected String delimiter;

    public CsvReporter(String filename) {
        this(filename, ",", null);
    }

    public CsvReporter(String filename, String parentDir) {
        this(filename, ",", parentDir);
    }

    public CsvReporter(String filename, String delimiter, String parentDir) {
        super(filename, parentDir);
        this.delimiter = delimiter;
        this.parentDir = parentDir;
    }

    @Override
    public void report(ReportData reportData) {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();
        String outputFilename = StringUtils.isNotBlank(this.fileName) ? this.fileName : generateDefaultOutputFileName();
        String dataItemsFileName = String.format("%s/%s.csv", this.parentDir, outputFilename);
        CsvUtils.writeCsv(dataItemsFileName, convertDataItems(dataItems), delimiter);
        String countFileName = String.format("%s/%s.count.csv", this.parentDir, outputFilename);
        CsvUtils.writeCsv(countFileName, convertCountResult(countResultMap), delimiter);
    }
}
