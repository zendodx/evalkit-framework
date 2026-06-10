package com.evalkit.framework.eval.facade;

import com.evalkit.framework.common.thread.OrderedBatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.thread.ThreadPoolManager;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer.BatchResult;
import lombok.extern.slf4j.Slf4j;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有序的增量式评测,支持断点重试,增量评测,周期结果上报,支持同组数据自定义顺序执行
 */
@Slf4j
public abstract class OrderedDeltaEvalFacade extends DeltaEvalFacade {
    public OrderedDeltaEvalFacade(DeltaEvalConfig config) {
        super(config);
    }

    /**
     * 获取顺序key，用于有序批量处理
     * 同key的数据会被分配到同一线程并按顺序处理
     *
     * @param inputData 输入数据
     * @return 顺序key
     */
    public abstract String prepareOrderKey(InputData inputData);

    /**
     * 获取比较器，用于有序批量处理
     *
     * @return 比较器
     */
    public abstract Comparator<InputData> prepareComparator();

    /**
     * 比较器兼容处理
     */
    protected Comparator<Message> prepareMessageComparator() {
        return (o1, o2) -> {
            Comparator<InputData> inputDataComparator = prepareComparator();
            if (inputDataComparator == null) {
                return 0;
            }
            InputData i1 = parseMessage(o1);
            InputData i2 = parseMessage(o2);
            return inputDataComparator.compare(i1, i2);
        };
    }

    protected InputData parseMessage(Message message) {
        String json;
        try {
            json = ((TextMessage) message).getText();
            DataItem dataItem = JsonUtils.fromJson(json, DataItem.class);
            return dataItem.getInputData();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 重写历史数据加载：从 SQLite 中查询与当前 DataItem 同组（相同 orderKey）的已完成 DataItem。
     * <p>
     * 由于 {@link OrderedDeltaEvalFacade} 保证同组数据串行执行，当前轮处理时前序轮已落库，
     * 因此可安全从 DB 读取历史数据注入 context，使
     * {@link com.evalkit.framework.eval.node.api.OrderedApiCompletion}
     * 的 getPrevDataItem / getPrevDataItems / getGroupDataItemAt 等多轮历史方法正常工作。
     *
     * @param current 当前正在处理的 DataItem
     * @return 同组已完成的历史 DataItem 列表（按 dataIndex 升序，不含当前条）
     */
    @Override
    protected List<DataItem> loadHistoryItems(DataItem current) throws SQLException {
        String currentKey = prepareOrderKey(current.getInputData());
        List<DataItem> all = dataItemMapper.queryAll();
        List<DataItem> history = new ArrayList<>();
        for (DataItem item : all) {
            if (item.getInputData() == null) continue;
            String key = prepareOrderKey(item.getInputData());
            if (Objects.equals(key, currentKey)) {
                history.add(item);
            }
        }
        // 按 dataIndex 升序排列，保证历史顺序正确
        history.sort(Comparator.comparingLong(item -> item.getDataIndex() == null ? 0L : item.getDataIndex()));
        return history;
    }

    /**
     * 消费MQ并评测,事务控制,中断后再重启也不会丢失消息
     * 使用OrderedBatchRunner实现有序批量处理
     */
    @Override
    protected CompletableFuture<Void> eval() {
        String taskNameUuid = config.getTaskNameUuid();
        int threadNum = config.getThreadNum();
        int mqReceiveTimeout = config.getMqReceiveTimeout();
        int batchSize = config.getBatchSize();
        long messageProcessMaxTime = config.getMessageProcessMaxTime();
        ThreadPoolExecutor pool = ThreadPoolManager.get(PoolName.MQ_CONSUME);
        AtomicLong consumed = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(1);
        // 没有进行消息确认,每次拉取的都是所有消息数量
        long remainCount = getRemainDataCount();
        long processedCount = getProcessedDataCount();
        long totalCount = getTotalCount();
        if (remainCount <= 0 || processedCount == totalCount) {
            log.info("No data to eval, remain count:{}", remainCount);
            latch.countDown();
        }
        if (latch.getCount() == 1) {
            // 单线程拉取消息
            pool.submit(() -> {
                // 连续空轮询上限
                final int MAX_EMPTY_ROUNDS = 10;
                // 空轮询计数器
                AtomicInteger emptyRounds = new AtomicInteger(0);
                try {
                    do {
                        activeMQEmbeddedServer.batchReceiveInTx(taskNameUuid, batchSize, mqReceiveTimeout, (batch, session) -> {
                            if (batch.isEmpty()) {
                                log.info("Empty batch, start empty rounds count:{}", emptyRounds.get());
                                emptyRounds.incrementAndGet();
                                // 空批次回滚（无消息可提交）
                                return BatchResult.ROLLBACK;
                            }
                            emptyRounds.set(0); // 重置空轮询计数
                            // 使用OrderedBatchRunner进行有序批量处理
                            List<Message> processedData = OrderedBatchRunner.runOrderedBatch(
                                    batch,
                                    message -> {
                                        try {
                                            // 幂等检查,已经处理过则跳过
                                            String messageId = message.getJMSMessageID();
                                            if (isProcess(messageId)) {
                                                log.info("Message already processed, messageId: {}", messageId);
                                                return null;
                                            }
                                            // 执行评测并落库
                                            evalAndInsert(message);
                                            // 去重表落库
                                            makeProcessed(messageId);
                                            log.info("Eval data consume and eval success, messageId: {}, message: {}", messageId, ((TextMessage) message).getText());
                                            return message;
                                        } catch (SQLException | JMSException e) {
                                            log.error("Error processing message", e);
                                            return null;
                                        }
                                    },
                                    message -> prepareOrderKey(parseMessage(message)),
                                    prepareMessageComparator(),
                                    threadNum,
                                    // 超时时间计算：每条消息最大处理时间
                                    size -> size * messageProcessMaxTime
                            );
                            // 计入本批所有消息（包括失败的），失败的已落幂等表或将由重投递机制重试
                            // 只要拉到消息就推进 consumed，防止失败消息无限阻塞退出条件
                            consumed.addAndGet(batch.size());
                            long successCount = processedData.stream().filter(Objects::nonNull).count();
                            long failCount = batch.size() - successCount;
                            if (failCount > 0) {
                                log.warn("Batch processed with {} failures, still advancing consumed counter", failCount);
                            }
                            // 消费完毕：提交本批并停止
                            if (consumed.get() >= remainCount) {
                                latch.countDown();
                                return BatchResult.STOP;
                            }
                            return BatchResult.CONTINUE;
                        });
                        // 如果消费完毕，则退出循环
                    } while (consumed.get() < remainCount && emptyRounds.get() < MAX_EMPTY_ROUNDS);
                } catch (Exception e) {
                    log.error("Eval failed, error: {}", e.getMessage(), e);
                    throw e;
                } finally {
                    // 兜底：若循环因 emptyRounds 超限退出，确保 latch 被释放
                    latch.countDown();
                }
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
}