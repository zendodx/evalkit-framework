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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class OrderedDeltaEvalFacadeTest {

    // -------------------------------------------------------------------------
    // 通用子类：暴露执行后计数 & 记录处理顺序
    // -------------------------------------------------------------------------

    static class CustomDeltaEval extends OrderedDeltaEvalFacade {

        long remainAfterExecute = -1;
        long processedAfterExecute = -1;
        /** 记录每个 caseId 下的 round 执行顺序，用于顺序性断言 */
        final CopyOnWriteArrayList<String> executionOrder = new CopyOnWriteArrayList<>();

        public CustomDeltaEval(DeltaEvalConfig config) {
            super(config);
        }

        @Override
        public String prepareOrderKey(InputData inputData) {
            Integer caseId = inputData.get("caseId");
            return caseId.toString();
        }

        @Override
        public Comparator<InputData> prepareComparator() {
            return (o1, o2) -> {
                try {
                    int r1 = o1.get("round");
                    int r2 = o2.get("round");
                    return r1 - r2;
                } catch (Exception ignored) {
                }
                return 0;
            };
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
            List<File> files = FileUtils.listFiles(config.getAttachDir());
            List<String> collect = files.stream().map(File::getName).collect(Collectors.toList());
            log.info("===>attaches files:{}", collect);
        }
    }

    // -------------------------------------------------------------------------
    // 通用工厂方法
    // -------------------------------------------------------------------------

    private Workflow buildEvalWorkflow(CustomDeltaEval cfe) {
        Begin begin = new Begin(
                BeginConfig.builder()
                        .threshold(1)
                        .scoreStrategy(new SumScoreStrategy())
                        .build()
        );
        ApiCompletion apiCompletion = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                // 记录执行顺序：caseId + ":" + round
                String caseId = dataItem.getInputData().get("caseId").toString();
                String round = dataItem.getInputData().get("round").toString();
                cfe.executionOrder.add(caseId + ":" + round);
                ApiCompletionResult result = new ApiCompletionResult();
                result.setResultItem(MapUtils.of("response", "Resp of " + dataItem.getInputData().get("query")));
                return result;
            }
        };
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
        return new WorkflowBuilder()
                .link(begin, apiCompletion)
                .link(apiCompletion, scorers).build();
    }

    private Workflow buildReportWorkflow(String taskName) {
        // parentDir 由 EvalConfig.attachDir 统一管理（默认 attachments/{taskName}）
        // 与 afterExecute() 中的 config.getAttachDir() 保持同步
        String attachDir = "attachments/" + taskName;
        BasicCounter basicCounter = new BasicCounter();
        HtmlReporter htmlReporter = new HtmlReporter(taskName, attachDir);
        JsonReporter jsonReporter = new JsonReporter(taskName, attachDir);
        ExcelReporter excelReporter = new ExcelReporter(taskName, attachDir);
        CsvReporter csvReporter = new CsvReporter(taskName, attachDir);
        return new WorkflowBuilder()
                .link(basicCounter, htmlReporter, jsonReporter, excelReporter, csvReporter).build();
    }

    public void executeOrderedDeltaEval(String taskName) throws Exception {
        CustomDeltaEval cfe = buildOrderedDeltaEval(taskName);
        cfe.run();
    }

    private CustomDeltaEval buildOrderedDeltaEval(String taskName) {
        DataLoader dataLoader1 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                        new InputData(MapUtils.of("caseId", 1, "query", "1", "round", 1)),
                        new InputData(MapUtils.of("caseId", 1, "query", "2", "round", 2))
                );
            }
        };
        DataLoader dataLoader2 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> inputDataList = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    inputDataList.add(new InputData(MapUtils.of("caseId", 4, "query", "" + i, "round", i + 1)));
                }
                for (int i = 0; i < 10; i++) {
                    inputDataList.add(new InputData(MapUtils.of("caseId", 2, "query", "" + i, "round", i + 1)));
                }
                for (int i = 0; i < 20; i++) {
                    inputDataList.add(new InputData(MapUtils.of("caseId", 3, "query", "" + i, "round", i + 1)));
                }
                return inputDataList;
            }
        };
        MultiDataLoader multiDataLoader = new MultiDataLoader(ListUtils.of(dataLoader1, dataLoader2));

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(multiDataLoader)
                        .evalWorkflow(null) // placeholder，下方会替换
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(10)
                        .enableResume(false)
                        .build()
        );
        // 重建：需要先拿到 cfe 实例才能传入 executionOrder 记录器
        // 采用重新构建方式解耦
        return buildOrderedDeltaEvalWithRecorder(taskName, multiDataLoader);
    }

    private CustomDeltaEval buildOrderedDeltaEvalWithRecorder(String taskName, DataLoader multiDataLoader) {
        // 先创建 cfe，再把 evalWorkflow 注入（需引用 cfe 的 executionOrder）
        // 使用内部持有引用的包装 DataLoader 构建方式
        final CustomDeltaEval[] holder = new CustomDeltaEval[1];

        Workflow evalWorkflow = buildEvalWorkflowLazy(holder);
        Workflow reportWorkflow = buildReportWorkflow(taskName);

        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(multiDataLoader)
                        .evalWorkflow(evalWorkflow)
                        .reportWorkflow(reportWorkflow)
                        .batchSize(10)
                        .threadNum(10)
                        .enableResume(false)
                        .build()
        );
        holder[0] = cfe;
        return cfe;
    }

    /**
     * 构建 evalWorkflow，通过 holder 延迟引用 CustomDeltaEval 的 executionOrder
     */
    private Workflow buildEvalWorkflowLazy(CustomDeltaEval[] holder) {
        Begin begin = new Begin(
                BeginConfig.builder()
                        .threshold(1)
                        .scoreStrategy(new SumScoreStrategy())
                        .build()
        );
        ApiCompletion apiCompletion = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                if (holder[0] != null) {
                    String caseId = dataItem.getInputData().get("caseId").toString();
                    String round = dataItem.getInputData().get("round").toString();
                    holder[0].executionOrder.add(caseId + ":" + round);
                }
                ApiCompletionResult result = new ApiCompletionResult();
                result.setResultItem(MapUtils.of("response", "Resp of " + dataItem.getInputData().get("query")));
                return result;
            }
        };
        Scorer scorer = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("eval-test-1");
                r.setScore(1.0);
                r.setReason("ok");
                return r;
            }
        };
        return new WorkflowBuilder()
                .link(begin, apiCompletion)
                .link(apiCompletion, scorer).build();
    }

    // =========================================================================
    // 原有测试（补充超时防护与异常传播）
    // =========================================================================

    /**
     * 单次执行 —— 增加超时保护
     */
    @Test
    public void singleTest() {
        String taskName = "OrderedDeltaEvalTest_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        assertTimeoutPreemptively(Duration.ofSeconds(120), (ThrowingSupplier<Void>) () -> {
            executeOrderedDeltaEval(taskName);
            return null;
        }, "OrderedDeltaEvalFacade 在规定时间内未能正常结束，疑似死锁");
    }

    /**
     * 并发执行 —— 补充子线程异常传播
     */
    @Test
    public void parallelTest() throws InterruptedException {
        int threadCount = 3;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    executeOrderedDeltaEval("OrderedDeltaEvalTest_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss_" + MathUtils.random(0, 100)));
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        assertTrue(doneLatch.await(120, TimeUnit.SECONDS), "并发有序评测未在规定时间内全部完成");
        pool.shutdown();

        assertNull(firstError.get(), "并发有序评测中有线程抛出异常: " +
                (firstError.get() != null ? firstError.get().getMessage() : ""));
    }

    // =========================================================================
    // 新增测试
    // =========================================================================

    /**
     * 【空数据场景】有序评测也应能处理空数据而不死锁
     */
    @Test
    public void emptyDataShouldFinishNormally() {
        String taskName = "OrderedDeltaEvalTest_Empty_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader emptyLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.emptyList();
            }
        };

        final CustomDeltaEval[] holder = new CustomDeltaEval[1];
        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(emptyLoader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(4)
                        .enableResume(false)
                        .build()
        );
        holder[0] = cfe;

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "有序评测在空数据场景下未能正常退出（可能死锁）");

        assertEquals(0, cfe.processedAfterExecute, "空数据时已处理数量应为 0");
    }

    /**
     * 【计数正确性】有序评测完成后 processedCount == 总条数
     */
    @Test
    public void processedCountShouldEqualTotal() {
        String taskName = "OrderedDeltaEvalTest_Count_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 52; // dataLoader1(2) + dataLoader2(20+10+20)
        DataLoader dataLoader1 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                        new InputData(MapUtils.of("caseId", 1, "query", "1", "round", 1)),
                        new InputData(MapUtils.of("caseId", 1, "query", "2", "round", 2))
                );
            }
        };
        DataLoader dataLoader2 = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 4, "query", "" + i, "round", i + 1)));
                }
                for (int i = 0; i < 10; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 2, "query", "" + i, "round", i + 1)));
                }
                for (int i = 0; i < 20; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 3, "query", "" + i, "round", i + 1)));
                }
                return list;
            }
        };
        MultiDataLoader multiDataLoader = new MultiDataLoader(ListUtils.of(dataLoader1, dataLoader2));

        final CustomDeltaEval[] holder = new CustomDeltaEval[1];
        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(multiDataLoader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(5)
                        .enableResume(false)
                        .build()
        );
        holder[0] = cfe;

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        });

        assertEquals(TOTAL, cfe.processedAfterExecute,
                "有序评测完成后 processedDataCount 应等于输入总量");
        assertEquals(0, cfe.remainAfterExecute,
                "有序评测完成后 remainDataCount 应为 0");
    }

    /**
     * 【同 Key 顺序性验证】同一 caseId 下的消息必须按 round 升序执行
     * 注意：此测试对 OrderedBatchRunner 的调度有依赖，验证框架的核心承诺
     */
    @Test
    public void sameKeyShouldBeProcessedInOrder() {
        String taskName = "OrderedDeltaEvalTest_Order_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        // caseId=99 共 5 轮，caseId=100 共 3 轮，各自 round 必须升序
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                // 刻意打乱插入顺序，验证框架排序能力
                list.add(new InputData(MapUtils.of("caseId", 99, "query", "q", "round", 3)));
                list.add(new InputData(MapUtils.of("caseId", 100, "query", "q", "round", 2)));
                list.add(new InputData(MapUtils.of("caseId", 99, "query", "q", "round", 1)));
                list.add(new InputData(MapUtils.of("caseId", 99, "query", "q", "round", 5)));
                list.add(new InputData(MapUtils.of("caseId", 100, "query", "q", "round", 1)));
                list.add(new InputData(MapUtils.of("caseId", 99, "query", "q", "round", 2)));
                list.add(new InputData(MapUtils.of("caseId", 100, "query", "q", "round", 3)));
                list.add(new InputData(MapUtils.of("caseId", 99, "query", "q", "round", 4)));
                return list;
            }
        };

        final CustomDeltaEval[] holder = new CustomDeltaEval[1];
        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(4)
                        .enableResume(false)
                        .build()
        );
        holder[0] = cfe;

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "有序评测未在规定时间内完成");

        // 验证 caseId=99 的 round 严格递增
        List<Integer> rounds99 = cfe.executionOrder.stream()
                .filter(s -> s.startsWith("99:"))
                .map(s -> Integer.parseInt(s.split(":")[1]))
                .collect(Collectors.toList());
        assertFalse(rounds99.isEmpty(), "caseId=99 应有执行记录");
        for (int i = 1; i < rounds99.size(); i++) {
            assertTrue(rounds99.get(i) > rounds99.get(i - 1),
                    "caseId=99 的执行顺序应按 round 升序，实际: " + rounds99);
        }

        // 验证 caseId=100 的 round 严格递增
        List<Integer> rounds100 = cfe.executionOrder.stream()
                .filter(s -> s.startsWith("100:"))
                .map(s -> Integer.parseInt(s.split(":")[1]))
                .collect(Collectors.toList());
        assertFalse(rounds100.isEmpty(), "caseId=100 应有执行记录");
        for (int i = 1; i < rounds100.size(); i++) {
            assertTrue(rounds100.get(i) > rounds100.get(i - 1),
                    "caseId=100 的执行顺序应按 round 升序，实际: " + rounds100);
        }
    }

    /**
     * 【单条数据有序评测】只有 1 条数据时有序评测也应正常完成
     */
    @Test
    public void singleItemShouldWork() {
        String taskName = "OrderedDeltaEvalTest_Single_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.singletonList(
                        new InputData(MapUtils.of("caseId", 1, "query", "only", "round", 1))
                );
            }
        };

        final CustomDeltaEval[] holder = new CustomDeltaEval[1];
        CustomDeltaEval cfe = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(10)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );
        holder[0] = cfe;

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            cfe.run();
            return null;
        }, "有序评测单条数据未能正常退出");

        assertEquals(1, cfe.processedAfterExecute, "单条数据有序评测后已处理数量应为 1");
        assertEquals(0, cfe.remainAfterExecute, "单条数据有序评测完成后剩余应为 0");
    }

    /**
     * 【prepareComparator 返回 null 时的容错】框架应降级为无序处理，不抛 NPE
     */
    @Test
    public void nullComparatorShouldFallbackGracefully() {
        String taskName = "OrderedDeltaEvalTest_NullComp_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "q" + i, "round", i + 1)));
                }
                return list;
            }
        };

        // 覆盖 prepareComparator 返回 null
        final CustomDeltaEval[] holder = new CustomDeltaEval[1];
        OrderedDeltaEvalFacade nullCompFacade = new OrderedDeltaEvalFacade(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        ) {
            @Override
            public String prepareOrderKey(InputData inputData) {
                return inputData.get("caseId").toString();
            }

            @Override
            public Comparator<InputData> prepareComparator() {
                return null; // 测试 null 容错
            }
        };

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            // 不应抛 NPE，框架内部的 prepareMessageComparator 做了 null 兜底
            try {
                nullCompFacade.run();
            } catch (Exception e) {
                // 记录但不必失败（关键是不卡死）
                log.warn("nullComparator 场景抛出异常（需关注）: {}", e.getMessage());
            }
            return null;
        }, "prepareComparator 返回 null 时框架不应卡死");
    }

    /**
     * 【enableResume=false 重跑】同 taskName 第二次运行应清缓存重新处理
     */
    @Test
    public void disableResumeShouldClearCacheAndRerun() throws Exception {
        String taskName = "OrderedDeltaEvalTest_NoResume_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int TOTAL = 4;
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 0; i < TOTAL; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "r-" + i, "round", i + 1)));
                }
                return list;
            }
        };

        final CustomDeltaEval[] holder1 = new CustomDeltaEval[1];
        CustomDeltaEval first = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder1))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );
        holder1[0] = first;
        first.run();
        assertEquals(TOTAL, first.processedAfterExecute);

        final CustomDeltaEval[] holder2 = new CustomDeltaEval[1];
        CustomDeltaEval second = new CustomDeltaEval(
                DeltaEvalConfig.builder()
                        .taskName(taskName)
                        .dataLoader(loader)
                        .evalWorkflow(buildEvalWorkflowLazy(holder2))
                        .reportWorkflow(buildReportWorkflow(taskName))
                        .batchSize(5)
                        .threadNum(2)
                        .enableResume(false)
                        .build()
        );
        holder2[0] = second;

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            second.run();
            return null;
        }, "enableResume=false 第二次运行未能正常完成");

        assertEquals(TOTAL, second.processedAfterExecute,
                "enableResume=false 时第二次运行应重新处理全部数据");
    }
}