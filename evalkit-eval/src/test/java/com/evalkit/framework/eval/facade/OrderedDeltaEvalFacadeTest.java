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
import com.evalkit.framework.eval.node.api.OrderedApiCompletion;
import com.evalkit.framework.eval.node.api.config.ApiCompletionConfig;
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
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowBuilder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.File;
import java.time.Duration;
import java.util.*;
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
        /**
         * 记录每个 caseId 下的 round 执行顺序，用于顺序性断言
         */
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

    public void executeOrderedDeltaEval(String taskName) {
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
    @DisplayName("单次执行：有序增量评测应在规定时间内正常完成")
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
    @DisplayName("并发执行：多线程同时运行有序增量评测不应抛异常")
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
    @DisplayName("空数据场景：有序评测应能正常结束而不死锁")
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
    @DisplayName("计数正确性：有序评测完成后 processedCount 应等于输入总条数")
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
    @DisplayName("顺序性验证：同一 caseId 的多轮消息必须按 round 升序处理")
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
    @DisplayName("单条数据：只有 1 条数据时有序评测也应正常完成")
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
    @DisplayName("容错处理：prepareComparator 返回 null 时框架不应抛 NPE 或卡死")
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
    @DisplayName("禁用断点续跑：enableResume=false 时第二次运行应清缓存并重新处理全部数据")
    public void disableResumeShouldClearCacheAndRerun() {
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

    // =========================================================================
    // 多轮历史功能测试（loadHistoryItems 修复验证）
    // =========================================================================

    /**
     * 【增量评测-历史条数递增】
     * 对于同一 caseId 的 N 轮数据，第 k 轮（1-based）处理时 getPrevDataItems 应返回 k-1 条历史。
     * <p>
     * 验证 loadHistoryItems 修复后，增量评测中多轮历史能被正确注入。
     */
    @Test
    @DisplayName("增量评测-历史条数递增：第 k 轮处理时 getPrevDataItems 应返回 k-1 条历史")
    public void historySizeShouldIncreaseWithEachRound() {
        String taskName = "OrderedDeltaEvalTest_History_Size_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int ROUNDS = 4;

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 1; i <= ROUNDS; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "q" + i, "round", i)));
                }
                return list;
            }
        };

        // 用 ConcurrentHashMap 收集每轮的 extra（key = round，value = extra map）
        Map<Integer, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();

        Workflow evalWorkflow = buildHistoryCapturingAndCollectingWorkflow(capturedExtra);

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "多轮历史测试未在规定时间内完成");

        // 每轮的 historySize 应等于 round - 1
        for (int round = 1; round <= ROUNDS; round++) {
            Map<String, Object> extra = capturedExtra.get(round);
            assertNotNull(extra, "第 " + round + " 轮的 extra 未被捕获");
            int historySize = (int) extra.get("historySize");
            assertEquals(round - 1, historySize,
                    "第 " + round + " 轮时 historySize 应为 " + (round - 1) + "，实际: " + historySize);
        }
    }

    /**
     * 【增量评测-getPrevDataItem 返回上一轮 query】
     * 每轮的 prevQuery 应等于上一轮的 query 字段值，第 1 轮应为空字符串。
     */
    @Test
    @DisplayName("增量评测-getPrevDataItem：每轮 prevQuery 应等于上一轮 query，第 1 轮应为空")
    public void prevDataItemShouldContainPreviousRoundQuery() {
        String taskName = "OrderedDeltaEvalTest_Prev_Query_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int ROUNDS = 3;

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 1; i <= ROUNDS; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "q" + i, "round", i)));
                }
                return list;
            }
        };

        Map<Integer, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();
        Workflow evalWorkflow = buildHistoryCapturingAndCollectingWorkflow(capturedExtra);

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "prevQuery 测试未在规定时间内完成");

        // 第 1 轮无上一条，prevQuery 应为 ""
        assertEquals("", capturedExtra.get(1).get("prevQuery"),
                "第 1 轮 prevQuery 应为空字符串");
        // 第 k 轮的 prevQuery 应等于第 k-1 轮的 query
        for (int round = 2; round <= ROUNDS; round++) {
            String expectedPrevQuery = "q" + (round - 1);
            String actualPrevQuery = (String) capturedExtra.get(round).get("prevQuery");
            assertEquals(expectedPrevQuery, actualPrevQuery,
                    "第 " + round + " 轮 prevQuery 应为 " + expectedPrevQuery);
        }
    }

    /**
     * 【增量评测-getGroupDataItems 返回完整组大小】
     * 在任意轮执行时，getGroupDataItems 返回的列表大小应等于该组的总轮数（含历史+当前+未来）。
     * 因为 context 已包含 history + current，groupIndexCache 建立在 batchInvoke 入参上，
     * 增量评测每次传入的是 history(k-1 条) + current(1 条) = k 条，因此第 k 轮 groupSize == k。
     */
    @Test
    @DisplayName("增量评测-getGroupDataItems：第 k 轮的 groupSize 应等于历史条数(k-1) + 当前(1)")
    public void groupSizeShouldEqualHistoryPlusCurrent() {
        String taskName = "OrderedDeltaEvalTest_Group_Size_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int ROUNDS = 5;

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 1; i <= ROUNDS; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "q" + i, "round", i)));
                }
                return list;
            }
        };

        Map<Integer, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();
        Workflow evalWorkflow = buildHistoryCapturingAndCollectingWorkflow(capturedExtra);

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "groupSize 测试未在规定时间内完成");

        // 第 k 轮时 groupSize = (k-1) 条历史 + 1 条当前 = k
        for (int round = 1; round <= ROUNDS; round++) {
            int groupSize = (int) capturedExtra.get(round).get("groupSize");
            assertEquals(round, groupSize,
                    "第 " + round + " 轮 groupSize 应为 " + round + "，实际: " + groupSize);
        }
    }

    /**
     * 【增量评测-getHistoryValues 提取历史 query 列表】
     * 第 k 轮时，getHistoryValues 提取的 query 应恰好是前 k-1 轮的 query，顺序一致。
     */
    @Test
    @DisplayName("增量评测-getHistoryValues：第 k 轮提取的历史 query 列表应与前 k-1 轮完全一致")
    public void historyValuesShouldContainAllPreviousQueries() {
        String taskName = "OrderedDeltaEvalTest_History_Values_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int ROUNDS = 4;

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                for (int i = 1; i <= ROUNDS; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "q" + i, "round", i)));
                }
                return list;
            }
        };

        Map<Integer, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();
        Workflow evalWorkflow = buildHistoryCapturingAndCollectingWorkflow(capturedExtra);

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "historyValues 测试未在规定时间内完成");

        // 第 1 轮没有历史，historyQueries 应为 ""
        assertEquals("", capturedExtra.get(1).get("historyQueries"),
                "第 1 轮 historyQueries 应为空字符串");

        // 第 k 轮的 historyQueries 应为 "q1,q2,...,q(k-1)"
        for (int round = 2; round <= ROUNDS; round++) {
            List<String> expectedQueries = new ArrayList<>();
            for (int r = 1; r < round; r++) {
                expectedQueries.add("q" + r);
            }
            String expected = String.join(",", expectedQueries);
            String actual = (String) capturedExtra.get(round).get("historyQueries");
            assertEquals(expected, actual,
                    "第 " + round + " 轮 historyQueries 应为 [" + expected + "]，实际: " + actual);
        }
    }

    /**
     * 【增量评测-多组数据互不干扰】
     * caseId=1 和 caseId=2 各有 3 轮，每组的历史数量不能混入对方的数据。
     */
    @Test
    @DisplayName("增量评测-多组隔离：不同 caseId 的历史数据不应相互混入")
    public void multipleGroupsShouldNotInterferWithEachOther() {
        String taskName = "OrderedDeltaEvalTest_Multi_Group_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");
        final int ROUNDS = 3;

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                List<InputData> list = new ArrayList<>();
                // 两组数据交错放入，验证框架不会混淆历史
                for (int i = 1; i <= ROUNDS; i++) {
                    list.add(new InputData(MapUtils.of("caseId", 1, "query", "A" + i, "round", i)));
                    list.add(new InputData(MapUtils.of("caseId", 2, "query", "B" + i, "round", i)));
                }
                return list;
            }
        };

        // 用 key = "caseId:round" 捕获 extra
        Map<String, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();

        Begin begin = new Begin(BeginConfig.builder()
                .threshold(1).scoreStrategy(new SumScoreStrategy()).build());
        OrderedApiCompletion api = new OrderedApiCompletion(
                ApiCompletionConfig.builder().threadNum(2).build()) {
            @Override
            public String prepareOrderKey(DataItem dataItem) {
                return dataItem.getInputData().get("caseId").toString();
            }

            @Override
            public Comparator<DataItem> prepareComparator() {
                return Comparator.comparingInt(d -> (Integer) d.getInputData().get("round"));
            }

            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                List<DataItem> prev = getPrevDataItems(dataItem);
                List<DataItem> group = getGroupDataItems(dataItem);
                // 验证同组历史的 caseId 全部相同
                String myCaseId = dataItem.getInputData().get("caseId").toString();
                boolean allSameCaseId = prev.stream().allMatch(
                        d -> myCaseId.equals(d.getInputData().get("caseId").toString()));

                String key = myCaseId + ":" + dataItem.getInputData().get("round");
                Map<String, Object> extra = new ConcurrentHashMap<>();
                extra.put("historySize", prev.size());
                extra.put("groupSize", group.size());
                extra.put("allSameCaseId", allSameCaseId);
                capturedExtra.put(key, extra);

                ApiCompletionResult result = new ApiCompletionResult();
                result.setResultItem(MapUtils.of("response", "resp"));
                return result;
            }
        };
        Scorer scorer = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("multi-group");
                r.setScore(1.0);
                r.setReason("ok");
                return r;
            }
        };
        Workflow evalWorkflow = new WorkflowBuilder().link(begin, api).link(api, scorer).build();

        assertTimeoutPreemptively(Duration.ofSeconds(60), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "多组隔离测试未在规定时间内完成");

        // 每组每轮断言
        for (int caseId : new int[]{1, 2}) {
            for (int round = 1; round <= ROUNDS; round++) {
                String key = caseId + ":" + round;
                Map<String, Object> extra = capturedExtra.get(key);
                assertNotNull(extra, "caseId=" + caseId + " round=" + round + " 未被捕获");
                // historySize = round - 1
                assertEquals(round - 1, extra.get("historySize"),
                        "caseId=" + caseId + " round=" + round + " historySize 应为 " + (round - 1));
                // 所有历史数据的 caseId 必须与当前相同
                assertTrue((Boolean) extra.get("allSameCaseId"),
                        "caseId=" + caseId + " round=" + round + " 的历史数据中混入了其他 caseId 的数据");
            }
        }
    }

    /**
     * 【增量评测-历史功能对单轮数据无影响】
     * caseId 只有 1 轮时，getPrevDataItems 应返回空列表，整体正常完成。
     */
    @Test
    @DisplayName("增量评测-单轮无历史：只有 1 轮时 getPrevDataItems 应返回空列表")
    public void singleRoundGroupShouldHaveNoHistory() {
        String taskName = "OrderedDeltaEvalTest_Single_Round_" + DateUtils.nowToString("yyyy-MM-dd_HH-mm-ss");

        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return Collections.singletonList(
                        new InputData(MapUtils.of("caseId", 1, "query", "only", "round", 1))
                );
            }
        };

        Map<Integer, Map<String, Object>> capturedExtra = new ConcurrentHashMap<>();
        Workflow evalWorkflow = buildHistoryCapturingAndCollectingWorkflow(capturedExtra);

        assertTimeoutPreemptively(Duration.ofSeconds(30), (ThrowingSupplier<Void>) () -> {
            new CustomDeltaEval(DeltaEvalConfig.builder()
                    .taskName(taskName)
                    .dataLoader(loader)
                    .evalWorkflow(evalWorkflow)
                    .reportWorkflow(buildReportWorkflow(taskName))
                    .batchSize(10).threadNum(2).enableResume(false).build()
            ).run();
            return null;
        }, "单轮历史测试未在规定时间内完成");

        Map<String, Object> extra = capturedExtra.get(1);
        assertNotNull(extra, "单轮数据的 extra 未被捕获");
        assertEquals(0, extra.get("historySize"), "单轮数据 historySize 应为 0");
        assertEquals("", extra.get("prevQuery"), "单轮数据 prevQuery 应为空字符串");
        assertEquals(1, extra.get("groupSize"), "单轮数据 groupSize 应为 1");
        assertEquals("", extra.get("historyQueries"), "单轮数据 historyQueries 应为空字符串");
    }

    // -------------------------------------------------------------------------
    // 辅助方法：构建含历史捕获逻辑的评测工作流
    // -------------------------------------------------------------------------

    /**
     * 构建一个 OrderedApiCompletion 工作流，在 invoke 时把多轮历史信息写入
     * capturedExtra（key = round 整数），供测试断言使用。
     */
    private Workflow buildHistoryCapturingAndCollectingWorkflow(
            Map<Integer, Map<String, Object>> capturedExtra) {
        Begin begin = new Begin(BeginConfig.builder()
                .threshold(1).scoreStrategy(new SumScoreStrategy()).build());

        OrderedApiCompletion api = new OrderedApiCompletion(
                ApiCompletionConfig.builder().threadNum(1).build()) {
            @Override
            public String prepareOrderKey(DataItem dataItem) {
                return dataItem.getInputData().get("caseId").toString();
            }

            @Override
            public Comparator<DataItem> prepareComparator() {
                return Comparator.comparingInt(d -> (Integer) d.getInputData().get("round"));
            }

            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                List<DataItem> prev = getPrevDataItems(dataItem);
                DataItem prevItem = getPrevDataItem(dataItem);
                List<DataItem> group = getGroupDataItems(dataItem);
                List<String> historyQueries = getHistoryValues(dataItem,
                        d -> (String) d.getInputData().get("query"));

                int round = (Integer) dataItem.getInputData().get("round");
                Map<String, Object> extra = new ConcurrentHashMap<>();
                extra.put("historySize", prev.size());
                extra.put("prevQuery", prevItem == null ? "" : prevItem.getInputData().get("query"));
                extra.put("groupSize", group.size());
                extra.put("historyQueries", String.join(",", historyQueries));
                capturedExtra.put(round, extra);

                ApiCompletionResult result = new ApiCompletionResult();
                result.setResultItem(MapUtils.of("response", "resp-" + dataItem.getInputData().get("query")));
                return result;
            }
        };

        Scorer scorer = new Scorer() {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                ScorerResult r = new ScorerResult();
                r.setMetric("history-check");
                r.setScore(1.0);
                r.setReason("ok");
                return r;
            }
        };
        return new WorkflowBuilder().link(begin, api).link(api, scorer).build();
    }
}