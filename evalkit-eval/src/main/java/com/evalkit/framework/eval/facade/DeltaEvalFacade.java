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
import com.evalkit.framework.eval.node.dataloader.injector.DataInjector;
import com.evalkit.framework.eval.node.scorer.strategy.SumScoreStrategy;
import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer;
import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;
import com.evalkit.framework.workflow.Workflow;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;
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
    protected final static String CACHE_FILE_PATH = "cache_data/";
    /* 增量评测配置 */
    protected DeltaEvalConfig config;
    /* 评测结果上报 */
    protected final ScheduledExecutorService reporterScheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "reporter-scheduler"));
    protected volatile ScheduledFuture<?> reporterFuture;
    /* MQ */
    protected final ActiveMQEmbeddedServer activeMQEmbeddedServer = ActiveMQEmbeddedServer.getInstance();
    /* DB */
    protected final SQLiteEmbeddedServer sqLiteEmbeddedServer = SQLiteEmbeddedServer.getInstance();
    protected DataItemMapper dataItemMapper;
    protected MQMessageProcessedMapper mqMessageProcessedMapper;
    protected EvalTaskMapper evalTaskMapper;

    public DeltaEvalFacade(DeltaEvalConfig config) {
        validConfig(config);
        this.config = config;
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
            String taskName = config.getTaskName();
            // 如果没有开启断点续评则每次初始化时删除缓存(MQ和DB数据)
            if (!config.isEnableResume()) {
                log.info("Not open resume eval from breakpoint, delete cache data");
                FileUtils.deleteDirectory(parentPath + taskName);
                FileUtils.deleteFile(parentPath + taskName + ".db");
            }
            // 启动MQ
            activeMQEmbeddedServer.start(parentPath + taskName);
            // 启动DB
            sqLiteEmbeddedServer.start(parentPath + taskName);
            // 初始化Mapper
            dataItemMapper = new DataItemMapper(sqLiteEmbeddedServer);
            mqMessageProcessedMapper = new MQMessageProcessedMapper(sqLiteEmbeddedServer);
            evalTaskMapper = new EvalTaskMapper(sqLiteEmbeddedServer);
            log.info("Initialize workflow success, middleware file save path: {}", parentPath + taskName);
        } catch (Exception e) {
            throw new WorkflowException("Initialize workflow error: " + e.getMessage(), e);
        }
    }

    /**
     * 执行工作流
     */
    @Override
    protected void execute() {
        String taskName = config.getTaskName();
        try {
            // 初始化评测任务
            initEvalTask();
            // 加载评测数据
            loadDataWrapper();
            CompletableFuture<Void> consumeFuture = eval();
            // 周期性上报最新评测结果
            report();
            // 等待消费完成
            consumeFuture.get();
            evalTaskMapper.updateStatus(taskName, EvalTaskStatus.FINISH);
        } catch (Exception e) {
            try {
                evalTaskMapper.updateStatus(taskName, EvalTaskStatus.FAILED);
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
        try {
            boolean evalTaskExists = evalTaskMapper.isEvalTaskExists(taskName);
            if (evalTaskExists) {
                return;
            }
            Date now = new Date();
            EvalTask evalTask = EvalTask.builder()
                    .taskName(taskName)
                    .allCount(0)
                    .status(EvalTaskStatus.INIT)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            evalTaskMapper.createEvalTask(evalTask);
            log.info("Init eval task success, taskName: {}", taskName);
        } catch (SQLException e) {
            log.error("Init eval task error: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载数据到MQ
     */
    protected void loadData() {
        String taskName = config.getTaskName();
        long queueSize = activeMQEmbeddedServer.getQueueMessageCount(taskName);
        int count;
        try {
            count = dataItemMapper.count();
        } catch (Exception e) {
            log.error("Count dataItem error: {}", e.getMessage(), e);
            return;
        }
        if (queueSize > 0 || (queueSize == 0 && count > 0)) {
            log.info("Data already loaded to MQ, queue size: {}", queueSize);
            return;
        }
        // 加载输入数据
        DataLoader dataLoader = config.getDataLoader();
        List<InputData> inputDataList = dataLoader.loadWrapper();
        if (CollectionUtils.isEmpty(inputDataList)) {
            return;
        }
        // 构建dataItem
        List<DataItem> dataItems = new CopyOnWriteArrayList<>();
        inputDataList.forEach(inputData -> {
            DataItem dataItem = new DataItem();
            dataItem.setDataIndex(inputData.getDataIndex());
            dataItem.setInputData(inputData);
            dataItems.add(dataItem);
        });
        // 数据加载器开启数据注入后需要将inputData中的已有数据注入到dataItem
        boolean openInjectData = dataLoader.getConfig().isOpenInjectData();
        if (openInjectData) {
            DataInjector.batchInject(dataItems);
        }
        // MQ存储dataItems
        if (CollectionUtils.isNotEmpty(dataItems)) {
            List<String> messages = dataItems.stream()
                    .map(JsonUtils::toJson)
                    .collect(Collectors.toList());
            activeMQEmbeddedServer.batchSendTextMessageToQueue(taskName, messages);
        }
        try {
            evalTaskMapper.updateAllCount(taskName, activeMQEmbeddedServer.getQueueMessageCount(taskName));
            evalTaskMapper.updateStatus(taskName, EvalTaskStatus.PROCESSING);
        } catch (SQLException e) {
            log.error("Update all count error: {}", e.getMessage(), e);
        }
        log.info("Load data to MQ success, queue size: {}", activeMQEmbeddedServer.getQueueMessageCount(taskName));
    }

    /**
     * 消费MQ并评测,事务控制,中断后再重启也不会丢失消息
     */
    @Override
    protected CompletableFuture<Void> eval() {
        String taskName = config.getTaskName();
        int threadNum = config.getThreadNum();
        int mqReceiveTimeout = config.getMqReceiveTimeout();
        int batchSize = config.getBatchSize();
        ThreadPoolExecutor pool = ThreadPoolManager.get(PoolName.MQ_CONSUME);
        AtomicLong consumed = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(1);
        long total = getRemainDataCount();
        for (int i = 0; i < threadNum; i++) {
            pool.submit(() -> {
                do {
                    activeMQEmbeddedServer.batchReceiveInTx(taskName, batchSize, mqReceiveTimeout, (batch, session) -> {
                        if (batch.isEmpty()) {
                            return false;
                        }
                        for (Message m : batch) {
                            // 幂等检查,已经处理过则跳过
                            String messageId = m.getJMSMessageID();
                            if (isProcess(messageId)) {
                                log.info("Message already processed, messageId: {}", messageId);
                                continue;
                            }
                            evalAndInsert(m);
                            // 去重表落库
                            makeProcessed(messageId);
                            log.info("Eval data consume and eval success, messageId: {}", messageId);
                        }
                        consumed.addAndGet(batch.size());
                        if (consumed.get() >= total) {
                            // 消费完毕
                            latch.countDown();
                            return false;
                        }
                        return true;
                    });
                    // 如果消费完毕，则退出循环
                } while (consumed.get() < total);
            });
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
        // 构建DataItem
        List<DataItem> dataItems = new CopyOnWriteArrayList<>();
        dataItems.add(dataItem);
        // 数据加载器开启数据注入后需要将inputData中的已有数据注入到dataItem
        boolean openInjectData = config.getDataLoader().getConfig().isOpenInjectData();
        if (openInjectData) {
            DataInjector.batchInject(dataItems);
        }
        // 克隆工作流
        Workflow evalWorkflow = config.getEvalWorkflow().clone();
        // 禁用自动关闭线程池
        evalWorkflow.setAutoShutdown(false);
        // 构建上下文
        WorkflowContext ctx = new WorkflowContext();
        WorkflowContextOps.setDataItems(ctx, dataItems);
        evalWorkflow.setWorkflowContext(ctx);
        // 执行评测
        evalWorkflow.execute();
        // 执行后结果落库
        Optional<DataItem> result = WorkflowContextOps.getDataItems(ctx).stream().findFirst();
        if (result.isPresent()) {
            dataItemMapper.insert(result.get());
        }
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
        return activeMQEmbeddedServer.getQueueMessageCount(config.getTaskName());
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
     * 获取总数据量
     */
    public long getTotalCount() {
        try {
            return evalTaskMapper.queryTotalCount(config.getTaskName());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}