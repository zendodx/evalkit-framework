package com.evalkit.framework.infra.service.mq;

import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import javax.jms.JMSException;
import javax.jms.Message;
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

    @AfterEach
    public void tearDown() throws Exception {
        activeMQEmbeddedServer.stop();
    }
}