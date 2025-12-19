package com.evalkit.framework.infra.server.mq;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import javax.jms.JMSException;
import javax.jms.Message;
import java.io.File;
import java.util.Arrays;
import java.util.List;

@Slf4j
class ActiveMQEmbeddedServerTest {
    ActiveMQEmbeddedServer activeMQEmbeddedServer = ActiveMQEmbeddedServer.getInstance();

    @BeforeEach
    public void setUp() throws Exception {
        activeMQEmbeddedServer.start("testMQ");
    }

    @Test
    @Order(1)
    public void testSendMessage() {
        activeMQEmbeddedServer.sendTextMessageToQueue("testQueue", "Hello, ActiveMQ!");
    }

    @Test
    @Order(2)
    public void testBatchSendMessage() {
        activeMQEmbeddedServer.batchSendTextMessageToQueue("testQueue", Arrays.asList("Hello, ActiveMQ!", "Hello, ActiveMQ!"));
    }

    @Test
    @Order(3)
    public void testReceiveMessage() throws JMSException {
        Message message = activeMQEmbeddedServer.receiveMessageFromQueue("testQueue", 1000 * 3);
        log.info("received text: {}", message);
    }

    @Test
    @Order(4)
    public void testBatchReceiveMessage() {
        List<Message> texts = activeMQEmbeddedServer.batchReceiveMessageFromQueue("testQueue", 1000 * 3, 10);
        log.info("received texts: {}", texts);
    }

    /**
     * 单机多实例测试
     */
    @Test
    public void multiEmbeddedServerTest() {
        String task1 = "EvalTest_1";
        String task2 = "EvalTest_2";
        String task3 = "EvalTest_3";
        try {
            ActiveMQEmbeddedServer server1 = ActiveMQEmbeddedServer.getInstance(task1);
            ActiveMQEmbeddedServer server2 = ActiveMQEmbeddedServer.getInstance(task2);
            ActiveMQEmbeddedServer server3 = ActiveMQEmbeddedServer.getInstance(task3);

            String tempDir = System.getProperty("java.io.tmpdir");

            server1.start(tempDir + "/" + task1);
            server2.start(tempDir + "/" + task2);
            server3.start(tempDir + "/" + task3);

            log.info("Server1 - Name: {}, Port: {}", server1.getBrokerName(), server1.getTcpUrl());
            log.info("Server2 - Name: {}, Port: {}", server2.getBrokerName(), server2.getTcpUrl());
            log.info("Server3 - Name: {}, Port: {}", server3.getBrokerName(), server3.getTcpUrl());

            // 测试发送消息
            server1.sendTextMessageToQueue("test-queue", "Hello from " + task1);
            server2.sendTextMessageToQueue("test-queue", "Hello from " + task2);
            server3.sendTextMessageToQueue("test-queue", "Hello from " + task3);

            log.info("success send message to multiple brokers");

            // 停止所有实例
            server1.stop();
            server2.stop();
            server3.stop();

            // 清理临时文件
            deleteDirectory(new File(tempDir + "/" + task1));
            deleteDirectory(new File(tempDir + "/" + task2));
            deleteDirectory(new File(tempDir + "/" + task3));

            log.info("finish multiEmbeddedServerTest");
        } catch (Exception e) {
            log.error("ActiveMQEmbeddedServerTest multiEmbeddedServerTest error: {}", e.getMessage(), e);
        }
    }

    /**
     * 删除目录
     */
    private static void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        activeMQEmbeddedServer.stop();
    }
}