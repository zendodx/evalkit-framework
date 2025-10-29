package com.evalkit.framework.infra.server.mq;

import com.evalkit.framework.common.utils.list.ListUtils;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.broker.BrokerService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.*;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import java.io.File;
import java.lang.IllegalStateException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嵌入式 ActiveMQ （JDK8 + 5.17.6）
 */
public class ActiveMQEmbeddedServer {
    /* MQ服务名 */
    private static final String BROKER_NAME = "embeddedBroker";
    private static final String VM_URL = "vm://" + BROKER_NAME + "?create=false";
    private static final String TCP_URL = "tcp://0.0.0.0:61616";
    private static final Logger log = LogManager.getLogger(ActiveMQEmbeddedServer.class);

    /* MQ服务 */
    private BrokerService broker;
    /* MQ连接工厂 */
    private ConnectionFactory factory;
    /* 缓存链接 */
    private final Set<Connection> activeConnections = ConcurrentHashMap.newKeySet();

    /* 单例 */
    private static final class InstanceHolder {
        static final ActiveMQEmbeddedServer instance = new ActiveMQEmbeddedServer();
    }

    public static ActiveMQEmbeddedServer getInstance() {
        return InstanceHolder.instance;
    }

    /**
     * 启动嵌入式MQ
     */
    public synchronized void start(String pathName) throws Exception {
        if (StringUtils.isEmpty(pathName)) {
            throw new IllegalStateException("ActiveMQ path name is empty");
        }
        // 已经创建则跳过
        if (broker != null) {
            log.info("ActiveMQ Embedded Broker is already started");
            return;
        }
        // 创建broker
        broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(true);
        broker.setDataDirectoryFile(new File(pathName));
        broker.addConnector(TCP_URL);
        broker.start();
        broker.waitUntilStarted();
        // 初始化连接工厂
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.setInitialRedeliveryDelay(2000);
        policy.setRedeliveryDelay(3000);
        policy.setMaximumRedeliveries(3);
        ActiveMQConnectionFactory amqFactory = new ActiveMQConnectionFactory(VM_URL);
        amqFactory.setRedeliveryPolicy(policy);
        this.factory = amqFactory;
        log.info("ActiveMQ embedded broker started");
    }

    /**
     * 停止嵌入式MQ
     */
    public synchronized void stop() throws Exception {
        // 先关所有连接
        for (Connection c : activeConnections) {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        }
        activeConnections.clear();
        // 最后停止broker
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
            broker = null;
            log.info("ActiveMQ embedded broker stopped");
        }
    }

    /**
     * 通用JMS执行模板,统一管理conn和session
     */
    private void executeInSession(JmsCallback callback) {
        if (broker == null || !broker.isStarted()) {
            throw new IllegalStateException("ActiveMQ embedded broker is not started. Call start() first.");
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
        batchSendTextMessageToQueue(queueName, ListUtils.of(text));
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
                TextMessage message = session.createTextMessage(text);
                producer.send(message);
            }
        });
    }

    /**
     * 接收单条文本消息
     */
    public Message receiveMessageFromQueue(String queueName, long timeout) {
        return batchReceiveMessageFromQueue(queueName, timeout, 1).stream().findFirst().orElse(null);
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

    @FunctionalInterface
    public interface JmsBatchCallback {
        /**
         * true 表示处理成功，会 commit；false 或抛异常会 rollback
         */
        boolean apply(List<Message> batch, Session session) throws Exception;
    }

    /**
     * 接收消息并处理,自主事务控制
     */
    public void batchReceiveInTx(String queueName, int batchSize, long timeout, JmsBatchCallback callback) {
        if (!isStarted()) {
            throw new IllegalStateException("Broker already stopped");
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
            // 交给调用方处理，成功就 commit，异常就 rollback
            boolean ok = callback.apply(batch, session);
            if (ok) {
                session.commit();
            } else {
                session.rollback();
            }
        } catch (Exception e) {
            rollback(session);
            log.error("MQ batch receive failed, error: {}", e.getMessage(), e);
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
                    "org.apache.activemq:type=Broker,brokerName=" + BROKER_NAME +
                            ",destinationType=Queue,destinationName=" + queueName);
            return ((Long) ManagementFactory.getPlatformMBeanServer()
                    .getAttribute(name, "QueueSize")).intValue();
        } catch (InstanceNotFoundException e) {
            // 队列还没创建，返回 0
            return 0;
        } catch (Exception e) {
            log.error("Failed to get queue message count for queue: {}", queueName, e);
            return 0;
        }
    }
}
