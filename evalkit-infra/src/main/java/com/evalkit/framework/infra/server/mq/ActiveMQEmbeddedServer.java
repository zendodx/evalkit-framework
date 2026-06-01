package com.evalkit.framework.infra.server.mq;

import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.math.MathUtils;
import com.evalkit.framework.common.utils.net.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.jms.*;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.IllegalStateException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 嵌入式 ActiveMQ （JDK8 + 5.17.6）
 * 端口获取方式: 动态分配端口,随机分配一个,然后检查是否被占用递增
 */
@Slf4j
public class ActiveMQEmbeddedServer {
    /* 默认MQ服务名 */
    private static final String DEFAULT_BROKER_NAME = "embeddedBroker";

    /* 动态端口计数器 */
    private static final AtomicInteger portCounter = new AtomicInteger(61616 + MathUtils.random(0, 1000));

    /* MQ配置 */
    private final String brokerName;
    private final String tcpUrl;
    private final String vmUrl;

    /* MQ服务 */
    private BrokerService broker;
    /* MQ连接工厂 */
    private ConnectionFactory factory;
    /* 缓存链接 */
    private final Set<Connection> activeConnections = ConcurrentHashMap.newKeySet();

    /* 内存监控 */
    private ScheduledExecutorService memoryMonitor;
    private ScheduledExecutorService cleanupScheduler;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final long MEMORY_WARNING_THRESHOLD_MB = 200; // 200MB内存预警阈值
    private static final int LARGE_MESSAGE_THRESHOLD = 1024 * 1024; // 1MB大对象阈值
    private static final int CHUNK_SIZE = 512 * 1024; // 512KB分片大小

    /**
     * 私有构造函数，支持自定义配置
     */
    private ActiveMQEmbeddedServer(String brokerName, String tcpUrl) {
        this.brokerName = brokerName;
        this.tcpUrl = tcpUrl;
        this.vmUrl = "vm://" + brokerName + "?create=false";
    }

    /**
     * 从61616端口开始,获取一个可用的端口
     */
    private static int getAvailablePort() {
        int port = portCounter.getAndIncrement();
        while (NetworkUtils.isPortUsed(port)) {
            port = portCounter.getAndIncrement();
        }
        return port;
    }

    /**
     * 获取默认实例（向后兼容）
     */
    @Deprecated
    public static ActiveMQEmbeddedServer getInstance() {
        return getInstance(DEFAULT_BROKER_NAME);
    }

    /**
     * 获取指定名称的实例
     */
    public static ActiveMQEmbeddedServer getInstance(String brokerName) {
        return getInstance(brokerName, null);
    }

    /**
     * 获取指定名称和端口的实例
     */
    public static ActiveMQEmbeddedServer getInstance(String brokerName, Integer port) {
        if (StringUtils.isEmpty(brokerName)) {
            brokerName = DEFAULT_BROKER_NAME;
        }
        String tcpUrl;
        if (port != null) {
            tcpUrl = "tcp://0.0.0.0:" + port;
        } else {
            // 动态分配端口,随机分配一个,然后检查是否被占用递增
            int dynamicPort = getAvailablePort();
            tcpUrl = "tcp://0.0.0.0:" + dynamicPort;
        }
        log.info("[ActiveMQ] Embedded broker tcp url: {}", tcpUrl);
        return new ActiveMQEmbeddedServer(brokerName, tcpUrl);
    }

    /**
     * 启动嵌入式MQ
     */
    public synchronized void start(String pathName) throws Exception {
        if (StringUtils.isEmpty(pathName)) {
            throw new IllegalStateException("[ActiveMQ] path name is empty");
        }
        // 已经创建则跳过
        if (broker != null) {
            log.info("[ActiveMQ] Embedded Broker is already started");
            return;
        }
        // 创建broker
        broker = new BrokerService();
        broker.setBrokerName(brokerName);
        broker.setPersistent(true);
        broker.setDataDirectoryFile(new File(pathName));
        broker.addConnector(tcpUrl);
        // 优化队列内存页配置，减少内存占用
        PolicyEntry policy = new PolicyEntry();
        policy.setMaxPageSize(200);                    // 降低页面大小，减少内存缓存
        policy.setMaxBrowsePageSize(200);              // 限制浏览页面大小
        policy.setMemoryLimit(64 * 1024 * 1024);      // 设置队列内存限制64MB
        policy.setQueue(">");                         // 匹配所有队列
        PolicyMap policyMap = new PolicyMap();
        policyMap.setPolicyEntries(ListUtils.of(policy));
        broker.setDestinationPolicy(policyMap);

        // KahaDB内存优化配置
        KahaDBPersistenceAdapter kaha = new KahaDBPersistenceAdapter();
        kaha.setJournalMaxFileLength(2 * 1024 * 1024);     // 减小日志文件大小
        kaha.setIndexCacheSize(2 * 1024 * 1024);           // 减小索引缓存为2MB
        kaha.setEnableJournalDiskSyncs(false);              // 关闭实时磁盘同步，提升性能
        kaha.setCheckpointInterval(5000);                   // 5秒检查点间隔
        kaha.setCleanupInterval(30000);                     // 30秒清理间隔
        kaha.setDirectory(new File(pathName));
        broker.setPersistenceAdapter(kaha);
        broker.start();
        broker.waitUntilStarted();
        // 初始化连接工厂
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setInitialRedeliveryDelay(2000);
        redeliveryPolicy.setRedeliveryDelay(3000);
        redeliveryPolicy.setMaximumRedeliveries(3);
        ActiveMQConnectionFactory amqFactory = new ActiveMQConnectionFactory(vmUrl);
        amqFactory.setRedeliveryPolicy(redeliveryPolicy);
        this.factory = amqFactory;
        // 启动内存监控和定期清理
        startMemoryMonitor();
        startCleanupScheduler();

        log.info("[ActiveMQ] Embedded broker started with brokerName: {}, tcpUrl: {}", brokerName, tcpUrl);
    }

    /**
     * 停止嵌入式MQ
     */
    public synchronized void stop() {
        try {
            // 先关所有连接
            for (Connection c : activeConnections) {
                try {
                    c.close();
                } catch (Exception ignore) {
                }
            }
            activeConnections.clear();

            // 停止内存监控和清理任务
            stopMemoryMonitor();
            stopCleanupScheduler();

            // 最后停止broker
            if (broker != null) {
                broker.stop();
                broker.waitUntilStopped();
                broker = null;
                log.info("[ActiveMQ] embedded broker stopped");
            }
        } catch (Exception e) {
            log.error("[ActiveMQ] Stop embedded broker failed, error: {}", e.getMessage(), e);
        }
    }

    /**
     * 通用JMS执行模板,统一管理conn和session
     */
    private void executeInSession(JmsCallback callback) {
        if (broker == null || !broker.isStarted()) {
            throw new IllegalStateException("[ActiveMQ] Embedded broker is not started. Call start() first.");
        }
        Connection conn = null;
        Session session = null;
        try {
            conn = factory.createConnection();
            // 链接加到缓存
            activeConnections.add(conn);
            conn.start();
            session = conn.createSession(true, Session.SESSION_TRANSACTED);
            callback.doInSession(session);
            session.commit();
        } catch (JMSException e) {
            rollback(session);
            log.error(e.getMessage(), e);
        } finally {
            closeQuietly(session, conn);
            activeConnections.remove(conn);
        }
    }

    /**
     * JMS回调
     */
    @FunctionalInterface
    private interface JmsCallback {
        void doInSession(Session session) throws JMSException;
    }

    /**
     * 会话回滚
     */
    private static void rollback(Session session) {
        if (session != null) {
            try {
                session.rollback();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 优雅关闭MQ连接和会话
     */
    private static void closeQuietly(Session session, Connection conn) {
        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 发送文本消息到队列
     */
    public void sendTextMessageToQueue(String queueName, String text) {
        if (text == null) {
            return;
        }

        // 检查是否为大对象，进行分片处理
        if (text.length() > LARGE_MESSAGE_THRESHOLD) {
            sendLargeMessageChunked(queueName, text);
        } else {
            batchSendTextMessageToQueue(queueName, ListUtils.of(text));
        }
    }

    /**
     * 发送大消息分片
     */
    private void sendLargeMessageChunked(String queueName, String largeText) {
        String messageId = java.util.UUID.randomUUID().toString();
        byte[] textBytes = largeText.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int totalChunks = (int) Math.ceil((double) textBytes.length / CHUNK_SIZE);

        log.info("[ActiveMQ] 发送大消息，总大小: {} bytes, 分片数: {}", textBytes.length, totalChunks);

        executeInSession(session -> {
            Destination destination = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(destination);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, textBytes.length);
                byte[] chunk = new byte[end - start];
                System.arraycopy(textBytes, start, chunk, 0, end - start);

                // 创建分片消息
                BytesMessage chunkMessage = session.createBytesMessage();
                chunkMessage.writeBytes(chunk);
                chunkMessage.setStringProperty("messageId", messageId);
                chunkMessage.setIntProperty("chunkIndex", i);
                chunkMessage.setIntProperty("totalChunks", totalChunks);
                chunkMessage.setBooleanProperty("isChunked", true);
                chunkMessage.setLongProperty("originalSize", textBytes.length);

                producer.send(chunkMessage);
            }
        });

        log.debug("[ActiveMQ] 大消息分片发送完成，消息ID: {}", messageId);
    }

    /**
     * 批量发送文本消息到队列
     */
    public void batchSendTextMessageToQueue(String queueName, List<String> texts) {
        if (StringUtils.isEmpty(queueName) || CollectionUtils.isEmpty(texts)) {
            return;
        }
        executeInSession(session -> {
            Destination destination = session.createQueue(queueName);
            MessageProducer producer = session.createProducer(destination);
            for (String text : texts) {
                if (text != null && text.length() > LARGE_MESSAGE_THRESHOLD) {
                    // 大消息分片处理
                    sendLargeMessageChunked(queueName, text);
                } else {
                    TextMessage message = session.createTextMessage(text);
                    producer.send(message);
                }
            }
        });
    }

    /**
     * 接收单条文本消息
     */
    public Message receiveMessageFromQueue(String queueName, long timeout) {
        List<Message> messages = batchReceiveMessageFromQueue(queueName, timeout, 20);

        if (messages.isEmpty()) {
            return null;
        }

        // 首先尝试处理分片消息重组
        String reassembledText = tryReassembleChunkedMessages(messages);
        if (reassembledText != null) {
            // 通过executeInSession创建新的TextMessage
            final String[] resultHolder = new String[1];
            resultHolder[0] = reassembledText;

            try {
                return executeInSessionWithReturn(session -> {
                    return session.createTextMessage(resultHolder[0]);
                });
            } catch (Exception e) {
                log.error("[ActiveMQ] 创建重组消息失败，返回原消息", e);
            }
        }

        // 没有分片消息或重组失败，返回第一个普通消息
        for (Message msg : messages) {
            try {
                if (!(msg instanceof BytesMessage) || !msg.getBooleanProperty("isChunked")) {
                    return msg;
                }
            } catch (JMSException e) {
                return msg; // 返回无法处理属性的消息
            }
        }

        return messages.get(0); // 都返回第一个
    }

    /**
     * 尝试重组分片消息
     */
    private String tryReassembleChunkedMessages(List<Message> messages) {
        Map<String, List<Message>> chunkedMessages = new java.util.HashMap<>();

        // 收集所有分片消息
        for (Message msg : messages) {
            try {
                if (msg instanceof BytesMessage && msg.getBooleanProperty("isChunked")) {
                    String messageId = msg.getStringProperty("messageId");
                    chunkedMessages.computeIfAbsent(messageId, k -> new ArrayList<>()).add(msg);
                }
            } catch (JMSException e) {
                // 忽略属性读取错误
            }
        }

        // 如果没有分片消息，返回null
        if (chunkedMessages.isEmpty()) {
            return null;
        }

        // 重组第一个完整的分片消息
        for (Map.Entry<String, List<Message>> entry : chunkedMessages.entrySet()) {
            String reassembledText = reassembleChunkedMessage(entry.getValue());
            if (reassembledText != null) {
                log.info("[ActiveMQ] 成功重组分片消息，消息ID: {}, 大小: {} bytes",
                        entry.getKey(), reassembledText.length());
                return reassembledText;
            }
        }

        return null;
    }

    /**
     * 支持返回值的JMS执行模板
     */
    private <T> T executeInSessionWithReturn(JmsCallbackWithReturn<T> callback) {
        if (broker == null || !broker.isStarted()) {
            throw new IllegalStateException("[ActiveMQ] Embedded broker is not started. Call start() first.");
        }
        Connection conn = null;
        Session session = null;
        try {
            conn = factory.createConnection();
            conn.start();
            session = conn.createSession(true, Session.SESSION_TRANSACTED);
            T result = callback.doInSession(session);
            session.commit();
            return result;
        } catch (JMSException e) {
            rollback(session);
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            closeQuietly(session, conn);
        }
    }

    /**
     * 支持返回值的JMS回调
     */
    @FunctionalInterface
    private interface JmsCallbackWithReturn<T> {
        T doInSession(Session session) throws JMSException;
    }

    /**
     * 重组分片消息
     */
    private String reassembleChunkedMessage(List<Message> chunks) {
        if (chunks.isEmpty()) {
            return null;
        }

        try {
            // 按分片索引排序
            chunks.sort((a, b) -> {
                try {
                    return Integer.compare(a.getIntProperty("chunkIndex"), b.getIntProperty("chunkIndex"));
                } catch (JMSException e) {
                    return 0;
                }
            });

            // 获取消息元数据
            Message firstChunk = chunks.get(0);
            String messageId = firstChunk.getStringProperty("messageId");
            int totalChunks = firstChunk.getIntProperty("totalChunks");
            long originalSize = firstChunk.getLongProperty("originalSize");

            // 检查分片完整性
            if (chunks.size() != totalChunks) {
                log.warn("[ActiveMQ] 分片消息不完整，期望: {}, 实际: {}, 消息ID: {}",
                        totalChunks, chunks.size(), messageId);
                return null;
            }

            // 重组消息内容
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            for (Message chunk : chunks) {
                if (chunk instanceof BytesMessage) {
                    BytesMessage bytesMessage = (BytesMessage) chunk;
                    byte[] chunkData = new byte[(int) bytesMessage.getBodyLength()];
                    bytesMessage.readBytes(chunkData);
                    outputStream.write(chunkData);
                }
            }

            byte[] reassembledBytes = outputStream.toByteArray();
            String reassembledText = new String(reassembledBytes, java.nio.charset.StandardCharsets.UTF_8);

            log.debug("[ActiveMQ] 分片消息重组完成，消息ID: {}, 原始大小: {} bytes", messageId, originalSize);

            // 返回重组后的文本内容
            return reassembledText;

        } catch (Exception e) {
            log.error("[ActiveMQ] 重组分片消息失败", e);
            return null;
        }
    }

    /**
     * 批量接收文本消息
     */
    public List<Message> batchReceiveMessageFromQueue(String queueName, long timeout, int batchSize) {
        List<Message> messages = new ArrayList<>(batchSize);
        executeInSession(session -> {
            Destination destination = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(destination);
            int count = 0;
            while (count < batchSize) {
                Message message = consumer.receive(timeout);
                if (message == null) {
                    break;
                }
                messages.add(message);
                count++;
            }
        });
        return messages;
    }

    /**
     * 批处理回调的处理结果
     * CONTINUE  - 本批提交，继续拉取下一批
     * STOP      - 本批提交，停止拉取（消费完毕或主动终止）
     * ROLLBACK  - 本批回滚（空批次或处理失败，消息重新入队）
     */
    public enum BatchResult {
        CONTINUE, STOP, ROLLBACK
    }

    @FunctionalInterface
    public interface JmsBatchCallback {
        /**
         * @return CONTINUE 提交并继续；STOP 提交并停止；ROLLBACK 回滚
         */
        BatchResult apply(List<Message> batch, Session session) throws Exception;
    }

    /**
     * 接收消息并处理,自主事务控制
     *
     * @return true 表示调用方应继续拉取，false 表示停止
     */
    public boolean batchReceiveInTx(String queueName, int batchSize, long timeout, JmsBatchCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("[ActiveMQ] Broker already stopped");
        }
        Connection conn = null;
        Session session = null;
        try {
            conn = factory.createConnection();
            conn.start();
            session = conn.createSession(true, Session.SESSION_TRANSACTED);
            Queue queue = session.createQueue(queueName);
            MessageConsumer consumer = session.createConsumer(queue);
            // 接收批量消息
            List<Message> batch = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                Message m = consumer.receive(timeout);
                if (m == null) {
                    break;
                }
                batch.add(m);
            }
            // 交给调用方处理
            BatchResult result = callback.apply(batch, session);
            if (result == BatchResult.ROLLBACK) {
                session.rollback();
                return false;
            } else {
                // CONTINUE 或 STOP 都提交
                session.commit();
                return result == BatchResult.CONTINUE;
            }
        } catch (Exception e) {
            rollback(session);
            log.error("[ActiveMQ] Batch receive message failed, error: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            closeQuietly(session, conn);
        }
    }

    /**
     * Broker 是否还活着
     */
    public boolean isStarted() {
        return broker != null && broker.isStarted();
    }

    /**
     * 获取队列剩余消息数量
     */
    public int getQueueMessageCount(String queueName) {
        try {
            ObjectName name = new ObjectName(
                    "org.apache.activemq:type=Broker,brokerName=" + brokerName +
                            ",destinationType=Queue,destinationName=" + queueName);
            return ((Long) ManagementFactory.getPlatformMBeanServer()
                    .getAttribute(name, "QueueSize")).intValue();
        } catch (InstanceNotFoundException e) {
            // 队列还没创建，返回 0
            return 0;
        } catch (Exception e) {
            log.error("[ActiveMQ] Get queue message count from queue: {} failed, error:{}", queueName, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 清空队列内存页，不删队列
     */
    public void purgeQueue(String queueName) throws Exception {
        ObjectName name = new ObjectName("org.apache.activemq:type=Broker,brokerName=" + brokerName + ",destinationType=Queue,destinationName=" + queueName);
        ManagementFactory.getPlatformMBeanServer().invoke(name, "purge", null, null);
    }

    /**
     * 获取broker名称
     */
    public String getBrokerName() {
        return brokerName;
    }

    /**
     * 获取TCP URL
     */
    public String getTcpUrl() {
        return tcpUrl;
    }

    /**
     * 启动内存监控
     */
    private void startMemoryMonitor() {
        memoryMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ActiveMQ-MemoryMonitor");
            t.setDaemon(true);
            return t;
        });

        memoryMonitor.scheduleAtFixedRate(() -> {
            try {
                long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
                long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
                long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024;

                if (heapUsed > MEMORY_WARNING_THRESHOLD_MB) {
                    log.warn("[ActiveMQ] 内存使用过高预警 - 堆内存: {}MB/{}, 非堆内存: {}MB",
                            heapUsed, heapMax, nonHeapUsed);

                    // 触发内存清理
                    triggerMemoryCleanup();
                }

                // 每10分钟记录一次内存使用情况
                log.debug("[ActiveMQ] 内存使用情况 - 堆内存: {}MB/{}, 非堆内存: {}MB",
                        heapUsed, heapMax, nonHeapUsed);

            } catch (Exception e) {
                log.error("[ActiveMQ] 内存监控异常", e);
            }
        }, 30, 30, TimeUnit.SECONDS); // 每30秒检查一次
    }

    /**
     * 停止内存监控
     */
    private void stopMemoryMonitor() {
        if (memoryMonitor != null && !memoryMonitor.isShutdown()) {
            memoryMonitor.shutdown();
            try {
                if (!memoryMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                    memoryMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                memoryMonitor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 触发内存清理
     */
    private void triggerMemoryCleanup() {
        try {
            // 清空所有队列的内存页
            if (broker != null && broker.isStarted()) {
                for (String queueName : getQueueNames()) {
                    try {
                        purgeQueue(queueName);
                        log.info("[ActiveMQ] 已清理队列 {} 的内存页", queueName);
                    } catch (Exception e) {
                        log.warn("[ActiveMQ] 清理队列 {} 失败: {}", queueName, e.getMessage());
                    }
                }
            }

            // 建议JVM进行垃圾回收
            System.gc();
            log.info("[ActiveMQ] 已触发内存清理和GC");

        } catch (Exception e) {
            log.error("[ActiveMQ] 内存清理失败", e);
        }
    }

    /**
     * 获取所有队列名称
     */
    private List<String> getQueueNames() {
        List<String> queueNames = new ArrayList<>();
        try {
            // 通过JMX获取队列列表
            ObjectName brokerName = new ObjectName(
                    "org.apache.activemq:type=Broker,brokerName=" + this.brokerName);

            @SuppressWarnings("unchecked")
            javax.management.AttributeList attributes = ManagementFactory.getPlatformMBeanServer()
                    .getAttributes(brokerName, new String[]{"Queues"});

            if (!attributes.isEmpty()) {
                Object queues = attributes.get(0);
                if (queues instanceof javax.management.openmbean.CompositeData[]) {
                    javax.management.openmbean.CompositeData[] queueArray =
                            (javax.management.openmbean.CompositeData[]) queues;
                    for (javax.management.openmbean.CompositeData queue : queueArray) {
                        queueNames.add(queue.get("name").toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ActiveMQ] 获取队列列表失败: {}", e.getMessage());
        }
        return queueNames;
    }

    /**
     * 获取当前内存使用情况
     */
    public String getMemoryStatus() {
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / 1024 / 1024;
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / 1024 / 1024;
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed() / 1024 / 1024;

        return String.format("堆内存: %dMB/%dMB, 非堆内存: %dMB", heapUsed, heapMax, nonHeapUsed);
    }

    /**
     * 启动定期清理调度器
     */
    private void startCleanupScheduler() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ActiveMQ-CleanupScheduler");
            t.setDaemon(true);
            return t;
        });

        // 每小时执行一次清理任务
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                performScheduledCleanup();
            } catch (Exception e) {
                log.error("[ActiveMQ] 定期清理任务异常", e);
            }
        }, 1, 1, TimeUnit.HOURS);

        log.info("[ActiveMQ] 定期清理调度器已启动");
    }

    /**
     * 停止清理调度器
     */
    private void stopCleanupScheduler() {
        if (cleanupScheduler != null && !cleanupScheduler.isShutdown()) {
            cleanupScheduler.shutdown();
            try {
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 执行定期清理
     */
    private void performScheduledCleanup() {
        log.info("[ActiveMQ] 开始执行定期清理任务");

        try {
            // 1. 清空已消费消息的内存页
            cleanupConsumedMessages();

            // 2. 强制KahaDB检查点，将内存数据刷盘
            forceKahaDBCheckpoint();

            // 3. 清理临时文件和过期日志
            cleanupKahaDBFiles();

            // 4. 触发GC回收内存
            System.gc();

            // 记录清理后的内存状态
            String memoryStatus = getMemoryStatus();
            log.info("[ActiveMQ] 定期清理完成，当前内存状态: {}", memoryStatus);

        } catch (Exception e) {
            log.error("[ActiveMQ] 定期清理失败", e);
        }
    }

    /**
     * 清理已消费的消息
     */
    private void cleanupConsumedMessages() {
        if (broker == null || !broker.isStarted()) {
            return;
        }

        try {
            List<String> queueNames = getQueueNames();
            for (String queueName : queueNames) {
                int messageCount = getQueueMessageCount(queueName);
                if (messageCount > 0) {
                    // 如果队列中有消息，检查是否需要清理
                    long queueMemoryUsage = getQueueMemoryUsage(queueName);
                    if (queueMemoryUsage > 50 * 1024 * 1024) { // 50MB
                        log.info("[ActiveMQ] 清理队列 {}, 消息数: {}, 内存使用: {} MB",
                                queueName, messageCount, queueMemoryUsage / 1024 / 1024);
                        purgeQueue(queueName);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ActiveMQ] 清理已消费消息失败", e);
        }
    }

    /**
     * 强制KahaDB检查点
     */
    private void forceKahaDBCheckpoint() {
        try {
            if (broker != null && broker.isStarted()) {
                PersistenceAdapter adapter = broker.getPersistenceAdapter();
                if (adapter instanceof KahaDBPersistenceAdapter) {
                    KahaDBPersistenceAdapter kaha = (KahaDBPersistenceAdapter) adapter;
                    // 通过反射调用checkpoint方法
                    java.lang.reflect.Method checkpointMethod = kaha.getClass().getMethod("checkpoint");
                    checkpointMethod.invoke(kaha);
                    log.debug("[ActiveMQ] KahaDB检查点完成");
                }
            }
        } catch (Exception e) {
            log.warn("[ActiveMQ] 强制KahaDB检查点失败: {}", e.getMessage());
        }
    }

    /**
     * 清理KahaDB临时文件
     */
    private void cleanupKahaDBFiles() {
        try {
            if (broker != null && broker.isStarted()) {
                File dataDir = broker.getDataDirectoryFile();
                if (dataDir != null && dataDir.exists()) {
                    // 清理db-*.log文件
                    File[] logFiles = dataDir.listFiles((dir, name) -> name.startsWith("db-") && name.endsWith(".log"));
                    if (logFiles != null) {
                        long cleanedSpace = 0;
                        for (File logFile : logFiles) {
                            long fileSize = logFile.length();
                            if (logFile.delete()) {
                                cleanedSpace += fileSize;
                            }
                        }
                        if (cleanedSpace > 0) {
                            log.info("[ActiveMQ] 清理KahaDB日志文件，释放空间: {} MB",
                                    cleanedSpace / 1024 / 1024);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ActiveMQ] 清理KahaDB文件失败: {}", e.getMessage());
        }
    }

    /**
     * 获取队列内存使用量
     */
    private long getQueueMemoryUsage(String queueName) {
        try {
            ObjectName name = new ObjectName(
                    "org.apache.activemq:type=Broker,brokerName=" + brokerName +
                            ",destinationType=Queue,destinationName=" + queueName);
            return ((Long) ManagementFactory.getPlatformMBeanServer()
                    .getAttribute(name, "MemoryUsage")).longValue();
        } catch (Exception e) {
            log.debug("[ActiveMQ] 获取队列 {} 内存使用失败: {}", queueName, e.getMessage());
            return 0;
        }
    }

    /**
     * 手动触发清理
     */
    public void triggerManualCleanup() {
        log.info("[ActiveMQ] 手动触发清理任务");
        performScheduledCleanup();
    }

    /**
     * 获取KahaDB统计信息
     */
    public String getKahaDBStats() {
        try {
            if (broker == null || !broker.isStarted()) {
                return "Broker未启动";
            }

            StringBuilder stats = new StringBuilder();
            stats.append("=== KahaDB统计信息 ===\n");

            // 获取所有队列信息
            List<String> queueNames = getQueueNames();
            stats.append(String.format("队列总数: %d\n", queueNames.size()));

            long totalMessages = 0;
            long totalMemory = 0;

            for (String queueName : queueNames) {
                int messageCount = getQueueMessageCount(queueName);
                long memoryUsage = getQueueMemoryUsage(queueName);
                totalMessages += messageCount;
                totalMemory += memoryUsage;

                stats.append(String.format("队列 %s: %d 条消息, %d MB\n",
                        queueName, messageCount, memoryUsage / 1024 / 1024));
            }

            stats.append(String.format("总计: %d 条消息, %d MB\n",
                    totalMessages, totalMemory / 1024 / 1024));

            stats.append("内存状态: ").append(getMemoryStatus()).append("\n");

            return stats.toString();

        } catch (Exception e) {
            return "获取KahaDB统计信息失败: " + e.getMessage();
        }
    }
}
