package com.evalkit.framework.eval.facade;

import com.evalkit.framework.common.thread.OrderedBatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.thread.ThreadPoolManager;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.facade.config.DeltaEvalConfig;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import lombok.extern.slf4j.Slf4j;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import java.sql.SQLException;
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
     * 消费MQ并评测,事务控制,中断后再重启也不会丢失消息
     * 使用OrderedBatchRunner实现有序批量处理
     */
    @Override
    protected CompletableFuture<Void> eval() {
        String taskName = config.getTaskName();
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
                        activeMQEmbeddedServer.batchReceiveInTx(taskName, batchSize, mqReceiveTimeout, (batch, session) -> {
                            if (batch.isEmpty()) {
                                log.info("Empty batch, start empty rounds count:{}", emptyRounds.get());
                                emptyRounds.incrementAndGet();
                                return false;
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
                                    // 超时时间计算：每条消息30秒
                                    size -> size * messageProcessMaxTime
                            );
                            // 过滤掉处理失败的数据
                            long successCount = processedData.stream().filter(Objects::nonNull).count();
                            consumed.addAndGet(successCount);
                            // 消费完毕
                            if (consumed.get() >= remainCount) {
                                latch.countDown();
                                return false;
                            }
                            return true;
                        });
                        // 如果消费完毕，则退出循环
                    } while (consumed.get() < remainCount && emptyRounds.get() < MAX_EMPTY_ROUNDS);
                } catch (Exception e) {
                    log.error("Eval failed, error: {}", e.getMessage(), e);
                    throw e;
                } finally {
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