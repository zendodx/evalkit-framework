package com.evalkit.framework.common.utils.runtime;

import com.evalkit.framework.common.utils.text.TextFileUtils;
import com.evalkit.framework.common.utils.time.DateUtils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控工具类，用于监控内存使用、GC情况、CPU时间等性能指标
 */
public class PerformanceMonitor {
    private static final String DEFAULT_OUTPUT_DIR = "test_report/";
    private static final DecimalFormat DF = new DecimalFormat("#.##");

    private final List<MemorySnapshot> memorySnapshots = new ArrayList<>();
    private final long startTime;
    private long endTime;
    private final String reportName;

    public PerformanceMonitor() {
        this("EvalPerformanceTest_" + DateUtils.nowToString());
    }

    public PerformanceMonitor(String reportName) {
        this.startTime = System.nanoTime();
        this.reportName = reportName;
        recordMemorySnapshot("初始化");
    }

    /**
     * 记录内存快照
     */
    public void recordMemorySnapshot(String phase) {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long timestamp = System.currentTimeMillis();
        long elapsedTime = System.nanoTime() - startTime;

        MemorySnapshot snapshot = new MemorySnapshot(
                phase,
                timestamp,
                elapsedTime,
                heapUsage.getUsed(),
                heapUsage.getCommitted(),
                heapUsage.getMax(),
                nonHeapUsage.getUsed(),
                nonHeapUsage.getCommitted(),
                getGcCount(),
                getGcTime()
        );

        memorySnapshots.add(snapshot);
    }

    /**
     * 结束监控
     */
    public void finish() {
        this.endTime = System.nanoTime();
        recordMemorySnapshot("结束");
    }

    /**
     * 获取总耗时（毫秒）
     */
    public long getTotalTimeMs() {
        long end = endTime > 0 ? endTime : System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(end - startTime);
    }

    /**
     * 获取内存使用峰值（MB）
     */
    public long getPeakMemoryUsageMb() {
        return memorySnapshots.stream()
                .mapToLong(MemorySnapshot::getHeapUsed)
                .max()
                .orElse(0) / 1024 / 1024;
    }

    /**
     * 获取初始内存使用（MB）
     */
    public long getInitialMemoryUsageMb() {
        return memorySnapshots.isEmpty() ? 0 :
                memorySnapshots.get(0).getHeapUsed() / 1024 / 1024;
    }

    /**
     * 获取最终内存使用（MB）
     */
    public long getFinalMemoryUsageMb() {
        return memorySnapshots.isEmpty() ? 0 :
                memorySnapshots.get(memorySnapshots.size() - 1).getHeapUsed() / 1024 / 1024;
    }

    /**
     * 获取内存使用增长（MB）
     */
    public long getMemoryGrowthMb() {
        return getFinalMemoryUsageMb() - getInitialMemoryUsageMb();
    }

    /**
     * 获取GC次数
     */
    public long getGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount)
                .sum();
    }

    /**
     * 获取GC总时间（毫秒）
     */
    public long getGcTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime)
                .sum();
    }

    /**
     * 获取GC次数变化
     */
    public long getGcCountChange() {
        if (memorySnapshots.size() < 2) return 0;
        return getGcCount() - memorySnapshots.get(0).getGcCount();
    }

    /**
     * 获取GC时间变化（毫秒）
     */
    public long getGcTimeChange() {
        if (memorySnapshots.size() < 2) return 0;
        return getGcTime() - memorySnapshots.get(0).getGcTime();
    }

    /**
     * 生成性能报告
     */
    public String generateReport() {
        String now = DateUtils.nowToString();
        StringBuilder report = new StringBuilder();

        report.append("=== 性能测试报告 ===\n");
        report.append(String.format("测试名称: %s\n", reportName));
        report.append(String.format("测试时间: %s\n", now));
        report.append("\n");
        report.append(String.format("总耗时: %d ms\n", getTotalTimeMs()));
        report.append(String.format("初始内存: %d MB\n", getInitialMemoryUsageMb()));
        report.append(String.format("最终内存: %d MB\n", getFinalMemoryUsageMb()));
        report.append(String.format("内存峰值: %d MB\n", getPeakMemoryUsageMb()));
        report.append(String.format("内存增长: %d MB\n", getMemoryGrowthMb()));
        report.append(String.format("GC次数变化: %d\n", getGcCountChange()));
        report.append(String.format("GC时间变化: %d ms\n", getGcTimeChange()));
        report.append(String.format("CPU核心数: %d\n", RuntimeEnvUtils.cpuCores()));
        report.append(String.format("最大可用内存: %d MB\n", RuntimeEnvUtils.maxMemoryMb()));

        report.append("\n=== 内存使用详情 ===\n");
        for (MemorySnapshot snapshot : memorySnapshots) {
            report.append(snapshot.toString()).append("\n");
        }

        // 输出到文件
        if (reportName != null) {
            TextFileUtils.writeString(DEFAULT_OUTPUT_DIR + reportName + "_" + now + ".txt", report.toString());
        }

        return report.toString();
    }

    /**
     * 内存快照内部类
     */
    private static class MemorySnapshot {
        private final String phase;
        private final long timestamp;
        private final long elapsedTime;
        private final long heapUsed;
        private final long heapCommitted;
        private final long heapMax;
        private final long nonHeapUsed;
        private final long nonHeapCommitted;
        private final long gcCount;
        private final long gcTime;

        public MemorySnapshot(String phase, long timestamp, long elapsedTime,
                              long heapUsed, long heapCommitted, long heapMax,
                              long nonHeapUsed, long nonHeapCommitted,
                              long gcCount, long gcTime) {
            this.phase = phase;
            this.timestamp = timestamp;
            this.elapsedTime = elapsedTime;
            this.heapUsed = heapUsed;
            this.heapCommitted = heapCommitted;
            this.heapMax = heapMax;
            this.nonHeapUsed = nonHeapUsed;
            this.nonHeapCommitted = nonHeapCommitted;
            this.gcCount = gcCount;
            this.gcTime = gcTime;
        }

        public long getHeapUsed() {
            return heapUsed;
        }

        public long getGcCount() {
            return gcCount;
        }

        public long getGcTime() {
            return gcTime;
        }

        @Override
        public String toString() {
            return String.format("阶段: %s, 耗时: %d ms, 堆内存: %s/%s MB, 非堆内存: %s MB, GC: %d次/%dms",
                    phase,
                    TimeUnit.NANOSECONDS.toMillis(elapsedTime),
                    DF.format(heapUsed / 1024.0 / 1024.0),
                    DF.format(heapCommitted / 1024.0 / 1024.0),
                    DF.format(nonHeapUsed / 1024.0 / 1024.0),
                    gcCount,
                    gcTime);
        }
    }
}

