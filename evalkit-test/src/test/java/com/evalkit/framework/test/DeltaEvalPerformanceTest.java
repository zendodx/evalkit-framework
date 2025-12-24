package com.evalkit.framework.test;

import com.evalkit.framework.common.utils.file.ExcelUtils;
import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.evalkit.framework.common.utils.runtime.PerformanceMonitor;
import com.evalkit.framework.eval.facade.DeltaEvalFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 增量评测模式评测性能测试, 内存占用,耗时
 */
public class DeltaEvalPerformanceTest {
    private static final Logger logger = LoggerFactory.getLogger(DeltaEvalPerformanceTest.class);
    private static final String tempDir = System.getProperty("java.io.tmpdir");
    private static final String fileName = "DAGEvalTest_" + UuidUtils.generateUuid() + ".xlsx";
    public static int caseCount = 1000;
    private static PerformanceMonitor performanceMonitor;

    /**
     * 构建临时评测文件
     */
    @BeforeAll
    public static void setUp() {
        String reportName = String.format("DeltaEvalPerformanceTest_caseCount_%s", caseCount);
        performanceMonitor = new PerformanceMonitor(reportName);
        performanceMonitor.recordMemorySnapshot("测试准备开始");

        String queryTemplate = "{{future_date 10 yyyy月MM日dd}}打算去{{province}}";
        List<Map<String, Object>> cases = new ArrayList<>();
        for (int i = 0; i < caseCount; i++) {
            Map<String, Object> caseMap = new HashMap<>();
            caseMap.put("query", queryTemplate);
            cases.add(caseMap);
        }
        ExcelUtils.writeExcel(tempDir + fileName, cases, true);

        performanceMonitor.recordMemorySnapshot("测试准备完成");
        logger.info("测试数据准备完成，共 {} 个测试用例", caseCount);
    }

    /**
     * 删除临时评测文件
     */
    @AfterAll
    public static void tearDown() {
        performanceMonitor.recordMemorySnapshot("测试清理开始");
        FileUtils.deleteDirectory(new File(tempDir));
        performanceMonitor.finish();

        // 输出完整的性能报告
        logger.info("\n{}", performanceMonitor.generateReport());
    }

    @Test
    public void testPerformance() {
        logger.info("开始增量评测模式性能测试，测试用例数量: {}", caseCount);

        // 记录工作流构建前的性能状态
        performanceMonitor.recordMemorySnapshot("工作流构建前");

        EvalTest evalTest = new EvalTest();
        DeltaEvalFacade deltaEvalFacade = evalTest.buildDeltaEvalFacade(tempDir + fileName);

        // 记录工作流构建完成后的性能状态
        performanceMonitor.recordMemorySnapshot("工作流构建完成");

        logger.info("开始执行...");

        // 执行工作流并记录性能
        performanceMonitor.recordMemorySnapshot("工作流执行前");

        try {
            deltaEvalFacade.run();
            performanceMonitor.recordMemorySnapshot("工作流执行完成");
            logger.info("工作流执行成功完成");
        } catch (Exception e) {
            performanceMonitor.recordMemorySnapshot("工作流执行异常");
            logger.error("工作流执行失败", e);
            throw e;
        }

        // 记录最终性能状态
        performanceMonitor.recordMemorySnapshot("测试完成");

        // 输出关键性能指标
        logger.info("=== 关键性能指标 ===");
        logger.info("总耗时: {} ms", performanceMonitor.getTotalTimeMs());
        logger.info("内存峰值: {} MB", performanceMonitor.getPeakMemoryUsageMb());
        logger.info("内存增长: {} MB", performanceMonitor.getMemoryGrowthMb());
        logger.info("GC次数变化: {}", performanceMonitor.getGcCountChange());
        logger.info("GC时间变化: {} ms", performanceMonitor.getGcTimeChange());
    }
}
