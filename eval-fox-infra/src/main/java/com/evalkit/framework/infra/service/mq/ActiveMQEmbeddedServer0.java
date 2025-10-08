//package com.evalkit.framework.infra.service.mq;
//
//import lombok.extern.slf4j.Slf4j;
//import org.apache.activemq.ActiveMQConnectionFactory;
//import org.apache.activemq.RedeliveryPolicy;
//import org.apache.activemq.broker.BrokerService;
//import org.apache.activemq.command.ActiveMQQueue;
//import org.apache.commons.lang3.StringUtils;
//
//import javax.jms.IllegalStateException;
//import javax.jms.*;
//import javax.management.MBeanServer;
//import javax.management.ObjectName;
//import java.io.File;
//import java.lang.management.ManagementFactory;
//import java.util.Collections;
//import java.util.List;
//
///**
// * 嵌入式 ActiveMQ （JDK8 + 5.17.6）
// */
//@Slf4j
//public class ActiveMQEmbeddedServer {
//    private static final String BROKER_NAME = "embeddedBroker";
//    private static final String VM_URL = "vm://" + BROKER_NAME + "?create=false";
//    private static final String TCP_URL = "tcp://0.0.0.0:61616";
//
//    private BrokerService broker;
//    private ConnectionFactory factory;
//
//    private static final class InstanceHolder {
//        static final ActiveMQEmbeddedServer instance = new ActiveMQEmbeddedServer();
//    }
//
//    public static ActiveMQEmbeddedServer getInstance() {
//        return InstanceHolder.instance;
//    }
//
//    /**
//     * 启动嵌入式 Broker（全局一次）
//     */
//    public synchronized void start(String pathName) throws Exception {
//        if (StringUtils.isEmpty(pathName)) {
//            throw new IllegalStateException("ActiveMQ path name is empty");
//        }
//        // 如果已经创建过broker,则跳过
//        if (broker != null) {
//            return;
//        }
//        broker = new BrokerService();
//        broker.setBrokerName(BROKER_NAME);
//        broker.setPersistent(true);
//        broker.setDataDirectoryFile(new File(pathName));
//        broker.addConnector(TCP_URL);
//        broker.start();
//        broker.waitUntilStarted();
//
//        RedeliveryPolicy policy = new RedeliveryPolicy();
//        policy.setInitialRedeliveryDelay(2000);
//        policy.setRedeliveryDelay(3000);
//        policy.setMaximumRedeliveries(3);
//
//        ActiveMQConnectionFactory amqFactory = new ActiveMQConnectionFactory(VM_URL);
//        amqFactory.setRedeliveryPolicy(policy);
//        amqFactory.getPrefetchPolicy().setQueuePrefetch(10);
//        this.factory = amqFactory;
//
//        log.info("ActiveMQ embedded broker started");
//    }
//
//    /**
//     * 关闭 Broker 并释放所有消费者连接
//     */
//    public synchronized void stop() throws Exception {
//        if (broker != null) {
//            broker.stop();
//            broker.waitUntilStopped();
//            broker = null;
//            log.info("ActiveMQ embedded broker stopped");
//        }
//    }
//
//    /**
//     * 通用JMS执行模板,统一管理conn和session
//     */
//    private void executeInSession(JmsCallback callback) {
//        Connection conn = null;
//        Session session = null;
//        try {
//            conn = factory.createConnection();
//            session = conn.createSession(true, Session.SESSION_TRANSACTED);
//            callback.doInSession(session);
//            session.commit();
//        } catch (JMSException e) {
//            rollback(session);
//            log.error(e.getMessage(), e);
//        } finally {
//            closeQuietly(session, conn);
//        }
//    }
//
//    /**
//     * JMS回调
//     */
//    @FunctionalInterface
//    private interface JmsCallback {
//        void doInSession(Session session) throws JMSException;
//    }
//
//    /**
//     * 会话回滚
//     */
//    private static void rollback(Session session) {
//        if (session != null) {
//            try {
//                session.rollback();
//            } catch (Exception ignored) {
//            }
//        }
//    }
//
//    /**
//     * 优雅关闭MQ连接和会话
//     */
//    private static void closeQuietly(Session session, Connection conn) {
//        if (session != null) {
//            try {
//                session.close();
//            } catch (Exception ignored) {
//            }
//        }
//        if (conn != null) {
//            try {
//                conn.close();
//            } catch (Exception ignored) {
//            }
//        }
//    }
//
//    /**
//     * 发送单条持久化文本消息到队列
//     */
//    public void sendQueue(String queueName, String message) throws JMSException {
//        batchSendQueue(queueName, Collections.singletonList(message));
//    }
//
//    /**
//     * 批量发送持久化文本消息到队列
//     */
//    public void batchSendQueue(String queueName, List<String> messageList) throws JMSException {
//        if (factory == null) {
//            throw new IllegalStateException("Broker not started");
//        }
//        if (messageList == null || messageList.isEmpty()) {
//            return;
//        }
//        executeInSession(session -> {
//            Destination queue = new ActiveMQQueue(queueName);
//            MessageProducer producer = session.createProducer(queue);
//            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
//            for (String msg : messageList) {
//                producer.send(session.createTextMessage(msg));
//            }
//            log.info("Send{} messages to {}", messageList.size(), queueName);
//        });
//    }
//
//    /**
//     * 获取指定队列的待消费消息数量
//     */
//    public long getQueueSize(String queueName) {
//        try {
//            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
//            ObjectName name = new ObjectName("org.apache.activemq:type=Broker,brokerName=" + broker.getBrokerName() +
//                    ",destinationType=Queue,destinationName=" + queueName);
//            return (Long) mBeanServer.getAttribute(name, "QueueSize");
//        } catch (Exception e) {
//            return 0;
//        }
//    }
//
//    /**
//     * 注册队列消费者
//     */
//    public void consumeQueue(String queueName, MessageListener listener) throws JMSException {
//        if (factory == null) {
//            throw new IllegalStateException("Broker not started");
//        }
//        executeInSession(session -> {
//            Destination queue = new ActiveMQQueue(queueName);
//            MessageConsumer consumer = session.createConsumer(queue);
//            consumer.setMessageListener(message -> {
//                try {
//                    listener.onMessage(message);
//                    message.acknowledge();
//                } catch (Exception e) {
//                    log.error("Consume message error: {}", e.getMessage(), e);
//                    throw new RuntimeException(e);
//                }
//            });
//        });
//    }
//}