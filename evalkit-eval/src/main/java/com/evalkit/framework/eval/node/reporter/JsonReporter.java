package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.common.utils.json.JsonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * json文件上报
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class JsonReporter extends FileReporter {
    public JsonReporter(String fileName) {
        this(fileName, null);
    }

    public JsonReporter(String fileName, String parentDir) {
        super(fileName, parentDir);
    }


    @Override
    public void report(ReportData reportData) {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();
        String outputFilename = StringUtils.isNotBlank(this.fileName) ? this.fileName : generateDefaultOutputFileName();
        Map<String, Object> output = new HashMap<>();
        output.put("dataItems", dataItems);
        output.put("countResult", countResultMap);
        JsonUtils.writeJsonFile(String.format("%s/%s.json", this.parentDir, outputFilename), output);
    }
}
