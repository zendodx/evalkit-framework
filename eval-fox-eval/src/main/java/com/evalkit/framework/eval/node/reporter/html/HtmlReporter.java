package com.evalkit.framework.eval.node.reporter.html;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ReportData;
import com.evalkit.framework.eval.node.reporter.FileReporter;
import com.evalkit.framework.eval.node.reporter.html.enums.HtmlReportStyle;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * html报告导出器
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Data
public class HtmlReporter extends FileReporter {
    /* thymeleaf模版引擎 */
    protected final TemplateEngine engine;
    /* html报告风格 */
    protected HtmlReportStyle style;
    /* cdn */
    protected String cdn;

    public HtmlReporter(String filename) {
        this(filename, null, HtmlReportStyle.DEFAULT);
    }

    public HtmlReporter(String filename, HtmlReportStyle style) {
        this(filename, null, style);
    }

    public HtmlReporter(String filename, String parentDir) {
        this(filename, parentDir, HtmlReportStyle.DEFAULT);
    }

    public HtmlReporter(String filename, String parentDir, HtmlReportStyle style) {
        super(filename, parentDir);
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("/templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        this.style = style;
    }

    @Override
    protected void report(ReportData reportData) throws IOException {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();
        Context ctx = new Context();
        // 报告名称
        ctx.setVariable("reportName", this.fileName);
        // cdn
        ctx.setVariable("cdn", this.cdn);
        // 基础统计结果统计结果
        ctx.setVariable("metricsData", convertJsonToMap(countResultMap.getOrDefault("basicCountResult", null)));
        // 归因结果
        ctx.setVariable("attributeCountResult", convertJsonToMap(countResultMap.getOrDefault("attributeCountResult", null)));
        // 评测数据
        ctx.setVariable("evaluationData", dataItems);
        String templateName = "report-default";
        if (style == HtmlReportStyle.DEFAULT) {
            templateName = "report-default";
        }
        String outputFileName = StringUtils.isNotBlank(this.fileName) ? this.fileName : generateDefaultOutputFileName();
        try (FileWriter writer = new FileWriter(String.format("%s/%s.html", this.parentDir, outputFileName))) {
            engine.process(templateName, ctx, writer);
        } catch (Exception e) {
            log.error("Report error", e);
        }
    }

    private Map<String, Object> convertJsonToMap(String json) {
        Map<String, Object> obj = new HashMap<>();
        if (StringUtils.isNotBlank(json)) {
            obj = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {
            });
        }
        return obj;
    }
}
