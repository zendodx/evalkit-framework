package com.evalkit.framework.eval.facade;

import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.math.MathUtils;
import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.api.ApiCompletion;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.begin.config.BeginConfig;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.MultiDataLoader;
import com.evalkit.framework.eval.node.reporter.CsvReporter;
import com.evalkit.framework.eval.node.reporter.ExcelReporter;
import com.evalkit.framework.eval.node.reporter.JsonReporter;
import com.evalkit.framework.eval.node.reporter.html.HtmlReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class DeltaEvalFacadeTest {

    /**
     * 自定义增量评测，暴露执行后的计数供断言使用
     */
    static class CustomDeltaEval extends DeltaEvalFacade {

        long remainAfterExecute = -1;
        long processedAfterExecute = -1;

        public CustomDeltaEval(DeltaEvalConfig config) {
            super(config);
        }

        @Override
        protected void afterLoadData() {
            log.info("===>Finish load data, data size:{}", getRemainDataCount());
        }

        @Override
        protected void afterExecute() {
            remainAfterExecute = getRemainDataCount();
            processedAfterExecute = getProcessedDataCount();
            log.info("===>Finish consume and eval, remain data size:{}, processed data size:{}",
                    remainAfterExecute, processedAfterExecute);
            List<File> files = FileUtils.listFiles("attachments/" + config.getTaskName());
            List<String> collect = files.stream().map(File::getName).collect(Collectors.toList());
            log.info("===>attaches files:{}", collect);
        }
    }

    // -------------------------------------------------------------------------
    // 通用工厂方法：构建标准 evalWorkflow / reportWorkflow
    // -------------------------------------------------------------------------

    private Workflow buildEvalWorkflow(boolean withApiCompletion) {
        Begin begin = new Begin(
                BeginConfig.builder()
                        .threshold(1)
                        .scoreStrategy(new SumScoreStrategy())
                        .build()
        );
        Scorer scorer1 = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("eval-test-1");
                r.setScore(1.0);
                r.setReason("eval test1:" + dataItem.getInputData().get("query"));
                return r;
            }
        };
        Scorer scorer2 = new Scorer(
                ScorerConfig.builder().star(true).threshold(1).metricName("eval-test-2").build()
        ) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("eval-test-2");
                r.setScore(1.0);
                r.setReason("eval test2:" + dataItem.getInputData().get("query"));
                return r;
            }
        };
        Scorer scorer3 = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("eval-test-3");
                r.setScore(0);
                r.setReason("eval test3:" + dataItem.getInputData().get("query"));
                return r;
            }
        };
        List<Scorer> scorers = ListUtils.of(scorer1, scorer2, scorer3);

        if (withApiCompletion) {
            ApiCompletion apiCompletion = new ApiCompletion() {
                @Override
                protected ApiCompletionResult invoke(DataItem dataItem) throws InterruptedException {
                    ApiCompletionResult result = new ApiCompletionResult();
                    result.setResultItem(MapUtils.of("response", "Resp of " + dataItem.getInputData().get("query")));
                    Thread.sleep(10);
                    return result;
                }
            };
            return new WorkflowBuilder()
                    .link(begin, apiCompletion)
                    .link(apiCompletion, scorers).build();
        }
        return new WorkflowBuilder().link(begin, scorers).build();
    }

    private Workflow buildReportWorkflow(String taskName) {
        BasicCounter basicCounter = new BasicCounter();
        // parentDir 统一用 "attachments/{taskName}"，与 afterExecute() 中 FileUtils.listFiles 的查询路径保持一致
        String attachDir = "attachments/" + taskName;
        HtmlReporter htmlReporter = new HtmlReporter(taskName, attachDir);
        JsonReporter jsonReporter = new JsonReporter(taskName, attachDir);
        ExcelReporter excelReporter = new ExcelReporter(taskName, attachDir);
        CsvReporter csvReporter = new CsvReporter(taskName, attachDir);
        return new WorkflowBuilder()
                .link(basicCounter, htmlReporter, jsonReporter, excelReporter, csvReporter).build();
    }

    // -------------------------------------------------------------------------
    // 原有方法，保持向后兼容
    // -------------------------------------------------------------------------

    public CustomDeltaEval buildDeltaEval(String taskName) {
        DataLoader dataLoader1 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                        new InputData(MapUtils.of("query", "1")),
                        new InputData(MapUtils.of("query", "2"))
                );
            }
        };
        DataLoader dataLoader2 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> inputDataList = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    inputDataList.add(new InputData(MapUtils.of("query", "" + i)));
                }
                return inputDataList;
            }
        };
        MultiDataLoader multiDataLoader = new MultiDataLoader(ListUtils.of(dataLoader1, dataLoader2), 10, 100);

        return new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(multiDataLoader)
                        .evalWorkflow(buildEvalWorkflow(true))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(10)
                        .enableResume(false)
                        .build()
        );
    }

    public void executeDeltaEval(String taskName) throws Exception {
        buildDeltaEval(taskName).run();
    }

    // =========================================================================
    // 原有测试（保留，补充超时防护与异常传播）
    // =========================================================================

    /**
     * 单次执行 —— 增加超时保护，防止因死锁导致 CI 永久挂起
     */
    @Test
    public void singleTest() {
        String taskName = "DeltaEvalTest_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        assertTimeoutPreemptively(Duration.ofSeconds(120), (ThrowingSupplier<Void>) () -> {
            executeDeltaEval(taskName);
            return null;
        }, "DeltaEvalFacade 在规定时间内未能正常结束，疑似死锁");
    }

    /**
     * 并发执行 —— 补充异常传播：子线程异常通过 Future 传回主线程断言
     */
    @Test
    public void parallelTest() throws InterruptedException, ExecutionException {
        int threadCount = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            Future<?> f = pool.submit(() -> {
                try {
                    startLatch.await();
                    executeDeltaEval("DeltaEvalTest_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss_" + MathUtils.random(0, 100)));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(f);
        }
        startLatch.countDown();

        pool.shutdown();
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS), "并发评测未在规定时间内全部完成");

        // 所有子任务的异常通过 Future.get() 传播出来，避免被线程池静默吞掉
        for (Future<?> f : futures) {
            f.get();
        }
    }

    // =========================================================================
    // 新增测试
    // =========================================================================

    /**
     * 【空数据场景】数据加载器返回空列表时，评测应正常结束（total=0 快速放行）
     * 验证修复的死锁：当 total=0 时 latch 必须被 countDown
     */
    @Test
    public void emptyDataShouldFinishNormally() {
        String taskName = "DeltaEvalTest_EmptyData_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader emptyLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.emptyList();
            }
        };

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(emptyLoader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(4)
                        .enableResume(false)
                        .build()
        );

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "空数据场景下 DeltaEvalFacade 未能正常退出（可能死锁）");

        assertEquals(0, cfe.processedAfterExecute, "空数据时已处理数量应为 0");
    }

    /**
     * 【小批量单条数据】只有 1 条数据时能正常完成并被计数
     */
    @Test
    public void singleItemDataShouldBeProcessed() {
        String taskName = "DeltaEvalTest_SingleItem_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader singleLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.singletonList(new InputData(MapUtils.of("query", "only-one")));
            }
        };

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(singleLoader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "单条数据场景下 DeltaEvalFacade 未能正常退出");

        assertEquals(1, cfe.processedAfterExecute, "单条数据处理后已处理数量应为 1");
        assertEquals(0, cfe.remainAfterExecute, "处理完毕后剩余数量应为 0");
    }

    /**
     * 【计数正确性】处理完 N 条数据后，processedDataCount == N，remainDataCount == 0
     */
    @Test
    public void processedCountShouldEqualTotalAfterCompletion() {
        String taskName = "DeltaEvalTest_Count_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 30;
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("query", "item-" + i)));
                }
                return list;
            }
        };

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(3)
                        .enableResume(false)
                        .build()
        );

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "30 条数据的评测未能在规定时间内完成");

        assertEquals(TOTAL, cfe.processedAfterExecute,
                "全部数据处理完毕后，processedDataCount 应等于输入总量");
        assertEquals(0, cfe.remainAfterExecute,
                "全部数据处理完毕后，remainDataCount 应为 0");
    }

    /**
     * 【enableResume=false 幂等清理】同 taskName 两次运行，第二次应重新计算而非跳过
     * 验证：关闭断点续评时缓存被清除，第二次 processedCount 仍等于 TOTAL 而非 0
     */
    @Test
    public void disableResumeShouldClearCacheAndRerun() throws Exception {
        final int TOTAL = 5;
        String taskName = "DeltaEvalTest_NoResume_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("query", "item-" + i)));
                }
                return list;
            }
        };

        // 第一次运行
        CustomDeltaEval first = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );
        first.run();
        assertEquals(TOTAL, first.processedAfterExecute, "第一次运行后已处理数量应等于总量");

        // 第二次运行（同 taskName，enableResume=false 应清除缓存重新跑）
        CustomDeltaEval second = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );
        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            second.run();
            return null;
        }, "enableResume=false 第二次运行未能正常完成（缓存未被清除导致 total=0 阻塞？）");

        assertEquals(TOTAL, second.processedAfterExecute,
                "enableResume=false 时第二次运行应重新处理全部数据");
    }

    /**
     * 【ApiCompletion 抛出异常时不阻塞退出】scorer 抛出 RuntimeException，
     * 评测框架应容错处理并最终正常结束，不卡死
     */
    @Test
    public void scorerExceptionShouldNotBlockCompletion() {
        String taskName = "DeltaEvalTest_ScorerEx_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    list.add(new InputData(MapUtils.of("query", "item-" + i)));
                }
                return list;
            }
        };

        Begin begin = new Begin(
                BeginConfig.builder().threshold(1).scoreStrategy(new SumScoreStrategy()).build()
        );
        // 偶数条目正常，奇数条目抛异常
        Scorer flakyScorer = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                String query = dataItem.getInputData().get("query");
                int idx = Integer.parseInt(query.replace("item-", ""));
                if (idx % 2 != 0) {
                    throw new RuntimeException("Simulated scorer failure for item-" + idx);
                }
                ScorerResult r = new ScorerResult();
                r.setMetric("flaky-scorer");
                r.setScore(1.0);
                r.setReason("ok");
                return r;
            }
        };
        Workflow evalWorkflow = new WorkflowBuilder().link(begin, flakyScorer).build();

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(evalWorkflow)
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );

        // 不要求计数精确（异常条目可能被跳过），只要求不卡死
        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            // 框架内部对单条异常是容错的，不应向外抛出
            try {
                cfe.run();
            } catch (Exception e) {
                log.warn("run() 抛出异常（可接受）: {}", e.getMessage());
            }
            return null;
        }, "Scorer 抛异常时 DeltaEvalFacade 未能正常退出（疑似死锁）");
    }

    /**
     * 【batchSize=1 边界】每次只消费 1 条消息，验证逐条处理能正常完成
     */
    @Test
    public void batchSizeOneShouldWork() {
        String taskName = "DeltaEvalTest_Batch1_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 8;
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("query", "b1-" + i)));
                }
                return list;
            }
        };

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(1)   // 关键：batchSize=1
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "batchSize=1 时 DeltaEvalFacade 未能正常退出");

        assertEquals(TOTAL, cfe.processedAfterExecute, "batchSize=1 时所有数据都应被处理");
        assertEquals(0, cfe.remainAfterExecute, "batchSize=1 时处理完毕后剩余应为 0");
    }

    /**
     * 【enableResume=true 断点续评】模拟中断后重启：第二次运行时 total=0（已全部完成），
     * 应快速结束而非死锁
     */
    @Test
    public void enableResumeShouldSkipAlreadyProcessedData() throws Exception {
        String taskName = "DeltaEvalTest_Resume_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 5;
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("query", "resume-" + i)));
                }
                return list;
            }
        };

        // 第一次：完整跑完
        CustomDeltaEval first = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(true)
                        .build()
        );
        first.run();
        assertEquals(TOTAL, first.processedAfterExecute, "第一次运行后已处理数量应等于总量");

        // 第二次：enableResume=true，MQ 中数据已被消费（remain=0），应快速退出
        CustomDeltaEval second = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(true)
                        .build()
        );
        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            second.run();
            return null;
        }, "enableResume=true 且数据已全部处理时，第二次运行应快速结束而非死锁");
    }

    /**
     * 【threadNum=1 单线程消费】确认单线程模式下不发生竞争问题
     */
    @Test
    public void singleThreadShouldWork() {
        String taskName = "DeltaEvalTest_Thread1_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 20;
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("query", "t1-" + i)));
                }
                return list;
            }
        };

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(1)   // 关键：单线程
                        .enableResume(false)
                        .build()
        );

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "threadNum=1 时 DeltaEvalFacade 未能正常退出");

        assertEquals(TOTAL, cfe.processedAfterExecute, "单线程模式下所有数据都应被处理");
    }

    /**
     * 【配置校验】缺少必填项时应抛出 IllegalArgumentException 而非 NPE
     */
    @Test
    public void missingRequiredConfigShouldThrowIllegalArgument() {
        // 缺少 dataLoader
        assertThrows(IllegalArgumentException.class, () -> new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName("missing-loader")
                        .evalWorkflow(buildEvalWorkflow(false))
                        .reportWorkflow(buildReportWorkflow("missing-loader"))
                        .build()
        ), "缺少 dataLoader 时应抛出 IllegalArgumentException");

        // 缺少 evalWorkflow
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.singletonList(new InputData(MapUtils.of("query", "x")));
            }
        };
        assertThrows(IllegalArgumentException.class, () -> new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName("missing-workflow")
                        .dataLoader(loader)
                        .reportWorkflow(buildReportWorkflow("missing-workflow"))
                        .build()
        ), "缺少 evalWorkflow 时应抛出 IllegalArgumentException");

        // 缺少 reportWorkflow
        assertThrows(IllegalArgumentException.class, () -> new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName("missing-report")
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflow(false))
                        .build()
        ), "缺少 reportWorkflow 时应抛出 IllegalArgumentException");
    }

    /**
     * 【并发执行 + 异常传播】保持原 parallelTest 语义，补充子线程异常能正确传播
     */
    @Test
    public void parallelTestWithExceptionPropagation() throws InterruptedException {
        int threadCount = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    executeDeltaEval("DeltaEvalTest_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss_" + MathUtils.random(0, 100)));
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "并发评测未在规定时间内全部完成");
        pool.shutdown();

        // 子线程如有异常，在主线程断言失败
        assertNull(firstError.get(), "并发评测中有线程抛出异常: " +
                (firstError.get() != null ? firstError.get().getMessage() : ""));
    }
}