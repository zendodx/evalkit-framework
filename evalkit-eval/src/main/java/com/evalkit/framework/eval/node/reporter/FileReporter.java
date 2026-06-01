package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.random.NanoIdUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.constants.DataItemField;
import com.evalkit.framework.eval.model.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件类型的上报器,具体实现由excel,csv,json等
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class FileReporter extends Reporter {
    /* 默认文件输出文件夹为attachments */
    protected static String DEFAULT_PARENT_DIR = "attachments";
    /* 输出文件名 */
    protected String fileName;
    /* 输出文件夹 */
    protected String parentDir;

    public FileReporter() {
        this(DateUtils.nowToString(), DEFAULT_PARENT_DIR);
    }

    public FileReporter(String fileName) {
        this(fileName, DEFAULT_PARENT_DIR);
    }

    public FileReporter(String fileName, String parentDir) {
        // fileName 中的 "/" 替换为 "_"，避免被误解析为路径分隔符（如 taskName 含 "/"）
        this.fileName = sanitizeFileName(fileName);
        if (StringUtils.isEmpty(parentDir)) {
            this.parentDir = DEFAULT_PARENT_DIR;
        } else {
            this.parentDir = parentDir;
        }
    }

    /**
     * 将文件名中的路径分隔符 "/" 替换为 "_"，保证文件名安全
     */
    protected static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        return fileName.replace("/", "_");
    }

    @Override
    protected void beforeReport(ReportData reportData) {
        super.beforeReport(reportData);
        Path attach = Paths.get(parentDir + "/");
        if (!attach.toFile().exists()) {
            attach.toFile().mkdirs();
        }
    }

    /**
     * 生成默认的文件名
     */
    protected String generateDefaultOutputFileName() {
        return NanoIdUtils.random(8);
    }

    /**
     * 处理统计结果
     */
    protected List<Map<String, Object>> convertCountResult(Map<String, String> countResultMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> t = new HashMap<>(countResultMap);
        result.add(t);
        return result;
    }

    /**
     * 处理数据项评测结果
     */
    protected List<Map<String, Object>> convertDataItems(List<DataItem> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DataItem item : items) {
            Map<String, Object> itemMap = new HashMap<>();
            // 保存数据索引
            itemMap.put(DataItemField.dataIndexKey, item.getDataIndex());
            // 保存输入数据
            InputData inputData = item.getInputData();
            itemMap.put(DataItemField.inputDataKey, JsonUtils.toJson(inputData));
            // 保存接口数据
            ApiCompletionResult apiCompletionResult = item.getApiCompletionResult();
            itemMap.put(DataItemField.apiCompletionResultKey, JsonUtils.toJson(apiCompletionResult));
            // 保存评测结果
            EvalResult evalResult = item.getEvalResult();
            itemMap.put(DataItemField.evalResultKey, JsonUtils.toJson(evalResult));
            // 保存额外数据
            Map<String, Object> extra = item.getExtra();
            itemMap.put(DataItemField.extraKey, JsonUtils.toJson(extra));
            result.add(itemMap);
        }
        return result;
    }
}
