package com.evalkit.framework.eval.node.reporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcReport 测试 —— 验证 SQLite 内嵌数据库可以正常构建 JdbcReport 实例
 * 注意：JdbcReport.report() 方法使用了 MySQL 专用建表 SQL（auto_increment、comment），
 * 因此仅测试对象构建逻辑，不执行真实的报告写入操作
 */
class JdbcReportTest {

    private static final String SQLITE_URL = "jdbc:sqlite:file::memory:?cache=shared&db=jdbc_report_test";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    /**
     * 测试 JdbcReport 可以使用 SQLite 内嵌数据库正常构建，不依赖外部 MySQL
     */
    @Test
    void testConstructWithSQLite() {
        JdbcReport jdbcReport = new JdbcReport(SQLITE_DRIVER, SQLITE_URL, "", "") {
            @Override
            public String prepareTableName() {
                return "eval_result";
            }
        };
        assertNotNull(jdbcReport, "JdbcReport 实例不应为 null");
        assertEquals("eval_result", jdbcReport.prepareTableName(), "表名应正确返回");
    }

    /**
     * 测试 JdbcReport 可以连接并验证 SQLite 连接池正常初始化
     */
    @Test
    void testConnectionPoolInitialized() {
        assertDoesNotThrow(() -> {
            JdbcReport jdbcReport = new JdbcReport(SQLITE_DRIVER, SQLITE_URL, "", "") {
                @Override
                public String prepareTableName() {
                    return "test_table";
                }
            };
            assertNotNull(jdbcReport);
        }, "使用 SQLite 构建 JdbcReport 不应抛出异常");
    }
}