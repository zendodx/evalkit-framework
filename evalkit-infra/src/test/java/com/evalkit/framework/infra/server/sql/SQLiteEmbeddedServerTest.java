package com.evalkit.framework.infra.server.sql;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class SQLiteEmbeddedServerTest {

    /**
     * 单机多实例测试
     */
    @Test
    public void testMultiEmbeddedServer() {
        String task1 = "EvalTest_1";
        String task2 = "EvalTest_2";
        String task3 = "EvalTest_3";
        try {
            SQLiteEmbeddedServer server1 = SQLiteEmbeddedServer.getInstance(task1);
            SQLiteEmbeddedServer server2 = SQLiteEmbeddedServer.getInstance(task2);
            SQLiteEmbeddedServer server3 = SQLiteEmbeddedServer.getInstance(task3);

            String tempDir = System.getProperty("java.io.tmpdir");

            // 启动多个实例
            server1.start(tempDir + "/" + task1);
            server2.start(tempDir + "/" + task2);
            server3.start(tempDir + "/" + task3);

            // 创建表并插入数据
            server1.createTable("CREATE TABLE test1 (id INTEGER PRIMARY KEY, name TEXT)");
            server2.createTable("CREATE TABLE test2 (id INTEGER PRIMARY KEY, name TEXT)");
            server3.createTable("CREATE TABLE test3 (id INTEGER PRIMARY KEY, name TEXT)");

            server1.executeUpdate("INSERT INTO test1 (name) VALUES (?)", "database1");
            server2.executeUpdate("INSERT INTO test2 (name) VALUES (?)", "database2");
            server3.executeUpdate("INSERT INTO test3 (name) VALUES (?)", "database3");

            // 查询数据
            int count1 = server1.executeQuery("SELECT COUNT(*) FROM test1").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) server1.executeQuery("SELECT COUNT(*) FROM test1").get(0).get("COUNT(*)")).intValue() : 0;
            int count2 = server2.executeQuery("SELECT COUNT(*) FROM test2").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) server2.executeQuery("SELECT COUNT(*) FROM test2").get(0).get("COUNT(*)")).intValue() : 0;
            int count3 = server3.executeQuery("SELECT COUNT(*) FROM test3").get(0).get("COUNT(*)") instanceof Number ?
                    ((Number) server3.executeQuery("SELECT COUNT(*) FROM test3").get(0).get("COUNT(*)")).intValue() : 0;

            log.info("Database1 records: {}", count1);
            log.info("Database2 records: {}", count2);
            log.info("Database3 records: {}", count3);

            // 停止实例
            server1.stop();
            server2.stop();
            server3.stop();

            // 清理文件
            server1.deleteDBFile();
            server2.deleteDBFile();
            server3.deleteDBFile();

            log.info("finish multiEmbeddedServerTest");
        } catch (Exception e) {
            log.error("SQLiteEmbeddedServerTest testMultiEmbeddedServer error: {}", e.getMessage(), e);
        }
    }
}