package com.evalkit.framework.infra.server;

import com.evalkit.framework.infra.server.mq.ActiveMQEmbeddedServer;
import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.File;

@Slf4j
public class MixedEmbeddedServerTest {
    /**
     * 混合多嵌入服务测试
     */
    @Test
    public void test() {
        String task1 = "EvalTest_1";
        String task2 = "EvalTest_2";
        String task3 = "EvalTest_3";
        try {
            // 嵌入MQ服务
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

            // 嵌入SQLite服务
            SQLiteEmbeddedServer sqlServer1 = SQLiteEmbeddedServer.getInstance(task1);
            SQLiteEmbeddedServer sqlServer2 = SQLiteEmbeddedServer.getInstance(task2);
            SQLiteEmbeddedServer sqlServer3 = SQLiteEmbeddedServer.getInstance(task3);

            // 启动多个实例
            sqlServer1.start(tempDir + "/" + task1);
            sqlServer2.start(tempDir + "/" + task2);
            sqlServer3.start(tempDir + "/" + task3);

            // 创建表并插入数据
            sqlServer1.createTable("CREATE TABLE test1 (id INTEGER PRIMARY KEY, name TEXT)");
            sqlServer2.createTable("CREATE TABLE test2 (id INTEGER PRIMARY KEY, name TEXT)");
            sqlServer3.createTable("CREATE TABLE test3 (id INTEGER PRIMARY KEY, name TEXT)");

            sqlServer1.executeUpdate("INSERT INTO test1 (name) VALUES (?)", "database1");
            sqlServer2.executeUpdate("INSERT INTO test2 (name) VALUES (?)", "database2");
            sqlServer3.executeUpdate("INSERT INTO test3 (name) VALUES (?)", "database3");

            // 查询数据
            int count1 = sqlServer1.executeQuery("SELECT COUNT(*) FROM test1").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) sqlServer1.executeQuery("SELECT COUNT(*) FROM test1").get(0).get("COUNT(*)")).intValue() : 0;
            int count2 = sqlServer2.executeQuery("SELECT COUNT(*) FROM test2").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) sqlServer2.executeQuery("SELECT COUNT(*) FROM test2").get(0).get("COUNT(*)")).intValue() : 0;
            int count3 = sqlServer3.executeQuery("SELECT COUNT(*) FROM test3").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) sqlServer3.executeQuery("SELECT COUNT(*) FROM test3").get(0).get("COUNT(*)")).intValue() : 0;

            log.info("Database1 records: {}", count1);
            log.info("Database2 records: {}", count2);
            log.info("Database3 records: {}", count3);

            // 停止所有实例
            server1.stop();
            server2.stop();
            server3.stop();

            sqlServer1.stop();
            sqlServer2.stop();
            sqlServer3.stop();

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
}
