package com.evalkit.framework.eval.facade;

import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.thread.ThreadPoolManager;
import com.evalkit.framework.common.utils.file.FileUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.constants.EvalTaskStatus;
import com.evalkit.framework.eval.context.WorkflowContextOps;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.mapper.DataItemMapper;
import com.evalkit.framework.eval.mapper.EvalTaskMapper;
import com.evalkit.framework.eval.mapper.MQMessageProcessedMapper;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.EvalTask;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;
import com.evalkit.framework.eval.node.dataloader.injector.DataInjector;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer;
import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer.BatchResult;
import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;
import com.evalkit.framework.workflow.TaskExecutor;
import com.evalkit.framework.workflow.Workflow;
import com.evalkit.framework.workflow.WorkflowContextHolder;
import com.evalkit.framework.workflow.exception.WorkflowException;
import com.evalkit.framework.workflow.model.WorkflowContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 增量式评测
 * 支持断点重试,增量评测,周期结果上报
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class DeltaEvalFacade extends EvalFacade {
    /* 缓存文件存储位置 */
    protected final static String CACHE_FILE_PATH = "eval_cache_data/";
    /* 增量评测配置 */
    protected DeltaEvalConfig config;
    /* 评测结果上报 */
    protected final ScheduledExecutorService reporterScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "reporter-scheduler"));
    protected volatile ScheduledFuture<?> reporterFuture;
    /* MQ */
    protected ActiveMQEmbeddedServer activeMQEmbeddedServer;
    /* DB */
    protected SQLiteEmbeddedServer sqLiteEmbeddedServer;
    protected DataItemMapper dataItemMapper;
    protected MQMessageProcessedMapper mqMessageProcessedMapper;
    protected EvalTaskMapper evalTaskMapper;

    public DeltaEvalFacade(DeltaEvalConfig config) {
        validConfig(config);
        this.config = config;
        // 根据任务名创建多嵌入实例,解决单机运行占用问题
        String taskNameUuid = config.getTaskNameUuid();
        activeMQEmbeddedServer = ActiveMQEmbeddedServer.getInstance(taskNameUuid);
        sqLiteEmbeddedServer = SQLiteEmbeddedServer.getInstance(taskNameUuid);
    }

    /**
     * 校验配置
     */
    protected void validConfig(DeltaEvalConfig config) {
        if (StringUtils.isBlank(config.getTaskName())) {
            throw new IllegalArgumentException("Task name is required");
        }
        if (config.getDataLoader() == null) {
            throw new IllegalArgumentException("Data loader is required");
        }
        if (config.getEvalWorkflow() == null) {
            throw new IllegalArgumentException("Eval workflow is required");
        }
        if (config.getReportWorkflow() == null) {
            throw new IllegalArgumentException("Report workflow is required");
        }
    }

    /**
     * 初始化环境
     */
    @Override
    protected void init() {
        try {
            // 中间件文件存储路径
            String parentPath = CACHE_FILE_PATH;
            String taskNameUUid = config.getTaskNameUuid();
            // 如果没有开启断点续评则每次初始化时删除缓存(MQ和DB数据)
            if (!config.isEnableResume()) {
                log.info("Not open resume eval from breakpoint, delete cache data");
                FileUtils.deleteDirectory(parentPath + taskNameUUid);
                FileUtils.deleteFile(parentPath + taskNameUUid + ".db");
            }
            // 启动MQ
            activeMQEmbeddedServer.start(parentPath + taskNameUUid);
            // 启动DB
            sqLiteEmbeddedServer.start(parentPath + taskNameUUid);
            // 初始化Mapper
            dataItemMapper = new DataItemMapper(sqLiteEmbeddedServer);
            mqMessageProcessedMapper = new MQMessageProcessedMapper(sqLiteEmbeddedServer);
            evalTaskMapper = new EvalTaskMapper(sqLiteEmbeddedServer);
            log.info("Initialize workflow success, middleware file save path: {}", parentPath + taskNameUUid);
        } catch (Exception e) {
            // 初始化出错时要关闭MQ和DB连接
            if (activeMQEmbeddedServer != null) {
                activeMQEmbeddedServer.stop();
            }
            if (sqLiteEmbeddedServer != null) {
                sqLiteEmbeddedServer.stop();
            }
            throw new WorkflowException("Initialize workflow error: " + e.getMessage(), e);
        }
    }

    /**
     * 执行工作流
     */
    @Override
    protected void execute() {
        String taskNameUUid = config.getTaskNameUuid();
        try {
            // 初始化评测任务
            initEvalTask();
            // 加载评测数据
            loadDataWrapper();
            // 消费MQ并评测
            CompletableFuture<Void> consumeFuture = eval();
            // 周期性上报最新评测结果
            report();
            // 等待消费完成
            consumeFuture.get();
            evalTaskMapper.updateStatus(taskNameUUid, EvalTaskStatus.FINISH);
        } catch (Exception e) {
            try {
                evalTaskMapper.updateStatus(taskNameUUid, EvalTaskStatus.FAILED);
            } catch (Exception ignored) {
            }
            throw new WorkflowException("Workflow execution error: " + e.getMessage(), e);
        } finally {
            // 停止上报调度
            stopReporter();
            // 执行最后最终上报
            doReport();
            // 先关闭线程池
            ThreadPoolManager.shutdown(PoolName.MQ_CONSUME);
            // 再停止MQ
            try {
                activeMQEmbeddedServer.stop();
            } catch (Exception ignored) {

            }
        }
    }

    /**
     * 初始化评测任务
     */
    protected void initEvalTask() {
        String taskName = config.getTaskName();
        String taskNameUUid = config.getTaskNameUuid();
        try {
            boolean evalTaskExists = evalTaskMapper.isEvalTaskExists(taskNameUUid);
            if (evalTaskExists) {
                return;
            }
            Date now = new Date();
            EvalTask evalTask = EvalTask.builder()
                    .taskName(taskName)
                    .taskNameUuid(taskNameUUid)
                    .allCount(0)
                    .status(EvalTaskStatus.INIT)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            evalTaskMapper.createEvalTask(evalTask);
            log.info("Init eval task success, taskName: {}, taskNameUuid: {}", taskName, taskNameUUid);
        } catch (SQLException e) {
            log.error("Init eval task error: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载数据到MQ
     */
    protected void loadData() {
        String taskNameUuid = config.getTaskNameUuid();
        long queueSize = activeMQEmbeddedServer.getQueueMessageCount(taskNameUuid);
        int count;
        try {
            count = dataItemMapper.count();
        } catch (Exception e) {
            log.error("Count dataItem error: {}", e.getMessage(), e);
            return;
        }
        // 已经加载过数据,不需要重复加载
        if (queueSize > 0 || (queueSize == 0 && count > 0)) {
            log.info("Data already loaded to MQ, queue size: {}", queueSize);
            return;
        }
        // 加载输入数据
        DataLoader dataLoader = config.getDataLoader();
        DataLoaderConfig dataLoaderConfig = dataLoader.getConfig();
        int limit = dataLoaderConfig.getLimit();
        int offset = dataLoaderConfig.getOffset();
        int curLimit = limit;
        int curOffset = offset;
        int batchSize = 100;
        long loadedCount = 0;
        try {
            do {
                // 分页加载数据
                dataLoaderConfig.setOffset(curOffset);
                if (curLimit == -1) {
                    dataLoaderConfig.setLimit(batchSize);
                } else {
                    if (curOffset + batchSize > curLimit) {
                        dataLoaderConfig.setLimit(curLimit - curOffset);
                    }
                }
                List<InputData> inputDataList = dataLoader.loadWrapper();
                if (CollectionUtils.isEmpty(inputDataList)) break;
                // 更新评测数据索引
                long startIndex = loadedCount;
                for (InputData inputData : inputDataList) {
                    inputData.setDataIndex(startIndex);
                    startIndex++;
                }
                loadedCount += inputDataList.size();
                curOffset += batchSize;

                // inputData集合转dataItem集合
                List<DataItem> dataItems = inputDataList.stream().map(inputData -> {
                    DataItem dataItem = new DataItem();
                    dataItem.setDataIndex(inputData.getDataIndex());
                    dataItem.setInputData(inputData);
                    return dataItem;
                }).collect(Collectors.toList());

                // 数据加载器开启数据注入后需要将inputData中的已有数据注入到dataItem
                boolean openInjectData = dataLoaderConfig.isOpenInjectData();
                if (openInjectData) {
                    DataInjector.batchInject(dataItems, dataLoaderConfig.isInjectDataIndex(), dataLoaderConfig.isInjectInputData(),
                            dataLoaderConfig.isInjectApiCompletionResult(), dataLoaderConfig.isInjectEvalResult(),
                            dataLoaderConfig.isInjectExtra());
                }
                // MQ存储dataItems
                if (CollectionUtils.isNotEmpty(dataItems)) {
                    List<String> messages = dataItems.stream()
                            .map(JsonUtils::toJson)
                            .collect(Collectors.toList());
                    activeMQEmbeddedServer.batchSendTextMessageToQueue(taskNameUuid, messages);
                }
                // 清空dataItems
                dataItems.clear();
                inputDataList.clear();
            } while (curLimit == -1 || (curLimit >= 0 && loadedCount < curLimit));
        } finally {
            // 恢复原始分页参数，避免 DataLoaderConfig 被污染影响下次调用（如 enableResume=false 重跑）
            dataLoaderConfig.setOffset(offset);
            dataLoaderConfig.setLimit(limit);
        }
        // 数据库更新任务数量：使用 loadedCount（精确值），避免依赖 JMX QueueSize 的异步延迟
        try {
            evalTaskMapper.updateAllCount(taskNameUuid, loadedCount);
            evalTaskMapper.updateStatus(taskNameUuid, EvalTaskStatus.PROCESSING);
        } catch (SQLException e) {
            log.error("Update all count error: {}", e.getMessage(), e);
        }
        log.info("Load data to MQ success, loaded count: {}", loadedCount);
    }

    /**
     * 消费MQ并评测,事务控制,中断后再重启也不会丢失消息
     */
    @Override
    protected CompletableFuture<Void> eval() {
        String taskNameUuid = config.getTaskNameUuid();
        int threadNum = config.getThreadNum();
        int mqReceiveTimeout = config.getMqReceiveTimeout();
        int batchSize = config.getBatchSize();
        ThreadPoolExecutor pool = ThreadPoolManager.get(PoolName.MQ_CONSUME);
        AtomicLong consumed = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(1);
        // 等待 JMX QueueSize 统计与实际入队数量一致（JMX 更新为异步，小数据量时可能延迟）
        // 最多等待 3 秒，避免死等；若超时则仍使用当前值（兜底由 MAX_EMPTY_ROUNDS 保护）
        long total = waitForQueueSize(taskNameUuid, 3000);
        // total=0 说明无需处理（全部已完成或无数据），提前放行
        if (total <= 0) {
            log.info("No remaining data to eval, skip consuming");
            latch.countDown();
        } else {
            for (int i = 0; i < threadNum; i++) {
                pool.submit(() -> {
                    // 连续空批次上限，兜底防止MQ异常时死循环
                    final int MAX_EMPTY_ROUNDS = 10;
                    int emptyRounds = 0;
                    try {
                        while (consumed.get() < total && emptyRounds < MAX_EMPTY_ROUNDS) {
                            activeMQEmbeddedServer.batchReceiveInTx(taskNameUuid, batchSize, mqReceiveTimeout, (batch, session) -> {
                                if (batch.isEmpty()) {
                                    // 空批次：回滚（无消息可提交），由外层计数器兜底退出
                                    return BatchResult.ROLLBACK;
                                }
                                long actualProcessed = 0;
                                for (Message m : batch) {
                                    // 幂等检查,已经处理过则跳过（不计入本次）
                                    String messageId = m.getJMSMessageID();
                                    if (isProcess(messageId)) {
                                        log.info("Message already processed, messageId: {}", messageId);
                                        continue;
                                    }
                                    evalAndInsert(m);
                                    // 去重表落库
                                    makeProcessed(messageId);
                                    actualProcessed++;
                                    log.info("Eval data consume and eval success, messageId: {}", messageId);
                                }
                                long newConsumed = consumed.addAndGet(actualProcessed);
                                if (newConsumed >= total) {
                                    // 消费完毕：提交本批并停止
                                    latch.countDown();
                                    return BatchResult.STOP;
                                }
                                return BatchResult.CONTINUE;
                            });
                            // 检查是否空批次（batchReceiveInTx 返回 false 表示 ROLLBACK/STOP）
                            if (consumed.get() < total && activeMQEmbeddedServer.getQueueMessageCount(taskNameUuid) == 0) {
                                emptyRounds++;
                            } else {
                                emptyRounds = 0;
                            }
                        }
                    } finally {
                        // 兜底：若循环因 emptyRounds 超限退出，确保 latch 被释放
                        latch.countDown();
                    }
                });
            }
        }
        // 把等待逻辑包成 CompletableFuture，主线程可以继续干别的
        return CompletableFuture.runAsync(() -> {
            try {
                latch.await();
                log.info("Eval data consume and eval finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * 执行评测并将结果落库
     */
    protected void evalAndInsert(Message message) throws SQLException, JMSException {
        // 执行评测并落库
        String json = ((TextMessage) message).getText();
        DataItem dataItem = JsonUtils.fromJson(json, DataItem.class);
        // 加载同组历史数据（默认空列表；子类可重写以支持多轮对话上下文）
        List<DataItem> historyItems = loadHistoryItems(dataItem);
        // 构建DataItem列表：历史条目在前，当前条目在末尾
        List<DataItem> dataItems = new CopyOnWriteArrayList<>(historyItems);
        dataItems.add(dataItem);
        Workflow evalWorkflow = null;
        try {
            // 克隆工作流
            evalWorkflow = config.getEvalWorkflow().clone();
            // 禁用自动关闭线程池
            evalWorkflow.setAutoShutdown(false);
            // 构建上下文
            WorkflowContext ctx = new WorkflowContext();
            WorkflowContextOps.setDataItems(ctx, dataItems);
            evalWorkflow.setWorkflowContext(ctx);
            // 执行评测
            evalWorkflow.execute();
            // 执行后结果落库（只落当前条，历史条已落库）
            List<DataItem> afterItems = WorkflowContextOps.getDataItems(ctx);
            if (afterItems != null && !afterItems.isEmpty()) {
                dataItemMapper.insert(afterItems.get(afterItems.size() - 1));
            }
        } catch (Exception e) {
            log.error("[DeltaEvalFacade] Eval data consume and eval failed, error: {}", e.getMessage(), e);
            throw e;
        } finally {
            if (evalWorkflow != null) {
                // 上文设置禁用自动关闭线程池,此处需要手动关闭,避免线程池资源泄露
                TaskExecutor taskExecutor = evalWorkflow.getTaskExecutor();
                if (taskExecutor != null) {
                    taskExecutor.shutdown();
                }
                // 清空上下文,避免内存泄漏
                WorkflowContextHolder.clear();
            }
        }
    }

    /**
     * 加载当前 DataItem 的同组历史数据（已完成，已落库）。
     * 默认返回空列表，适用于不需要多轮历史的普通评测场景。
     * <p>
     * 子类（如 {@link OrderedDeltaEvalFacade}）可重写此方法，
     * 按 orderKey 从 SQLite 中查询同组的已完成 DataItem，
     * 使 {@link com.evalkit.framework.eval.node.api.OrderedApiCompletion}
     * 的多轮历史访问方法（getPrevDataItem 等）在增量评测中正常工作。
     *
     * @param current 当前正在处理的 DataItem
     * @return 同组历史 DataItem 列表（按执行顺序排列，不含当前条）
     */
    protected List<DataItem> loadHistoryItems(DataItem current) throws SQLException {
        return Collections.emptyList();
    }

    /**
     * 幂等检查,已经处理过消息则跳过
     */
    protected boolean isProcess(String messageId) throws SQLException {
        return mqMessageProcessedMapper.exists(messageId);
    }

    /**
     * 落去重表
     */
    protected void makeProcessed(String messageId) throws SQLException {
        mqMessageProcessedMapper.insert(messageId);
    }

    /**
     * 启动周期上报
     */
    @Override
    protected void report() {
        if (reporterFuture != null && !reporterFuture.isCancelled()) {
            return;
        }
        reporterFuture = reporterScheduler.scheduleWithFixedDelay(this::doReport,
                0, config.getReportInterval(), TimeUnit.SECONDS);
    }

    /**
     * 优雅停止上报：等当前批次跑完再停
     */
    protected void stopReporter() {
        if (reporterFuture != null) {
            reporterFuture.cancel(false);
        }
        reporterScheduler.shutdown();
        try {
            if (!reporterScheduler.awaitTermination(config.getReportInterval(), TimeUnit.SECONDS)) {
                reporterScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reporterScheduler.shutdownNow();
        }
    }

    /**
     * 执行上报
     */
    protected void doReport() {
        try {
            List<DataItem> dataItems = dataItemMapper.queryAll();
            if (CollectionUtils.isEmpty(dataItems)) {
                return;
            }
            WorkflowContext ctx = new WorkflowContext();
            WorkflowContextOps.setThreshold(ctx, 0L);
            WorkflowContextOps.setScorerStrategy(ctx, new SumScoreStrategy());
            WorkflowContextOps.setDataItems(ctx, dataItems);
            Workflow reportWorkflow = config.getReportWorkflow().clone();
            reportWorkflow.setAutoShutdown(false);
            reportWorkflow.setWorkflowContext(ctx);
            reportWorkflow.execute();
            log.info("Reporter executed, size={}", dataItems.size());
        } catch (Exception e) {
            // 禁止抛异常，否则调度器会停止
            log.error("Reporter error", e);
        }
    }

    /**
     * 获取待处理数据量
     */
    @Override
    public long getRemainDataCount() {
        String taskNameUuid = config.getTaskNameUuid();
        return activeMQEmbeddedServer.getQueueMessageCount(taskNameUuid);
    }

    /**
     * 获取已处理数据量
     */
    @Override
    public long getProcessedDataCount() {
        try {
            return dataItemMapper.count();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 等待 JMX QueueSize 达到非零（或超时）
     * ActiveMQ 的 JMX QueueSize 统计是异步更新的，数据量小时入队后可能短暂返回 0
     *
     * @param queueName  队列名
     * @param timeoutMs  最长等待毫秒数
     * @return 队列实际大小（可能为 0 若真的没有数据或超时）
     */
    protected long waitForQueueSize(String queueName, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long size = activeMQEmbeddedServer.getQueueMessageCount(queueName);
        while (size == 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            size = activeMQEmbeddedServer.getQueueMessageCount(queueName);
        }
        return size;
    }

    /**
     * 获取总数据量
     */
    public long getTotalCount() {
        try {
            String taskNameUuid = config.getTaskNameUuid();
            return evalTaskMapper.queryTotalCount(taskNameUuid);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}