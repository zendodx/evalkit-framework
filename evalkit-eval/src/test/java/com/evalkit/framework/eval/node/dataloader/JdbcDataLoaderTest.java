package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.JdbcDataLoaderConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdbcDataLoader 测试 —— 使用 SQLite 文件型数据库替代外部 MySQL，无需外部服务
 * <p>
 * 注意：SQLite 内存模式（file::memory:）与 HikariCP 连接池不兼容（DriverManager 创建的连接
 * 与 HikariCP 连接池使用的连接相互隔离），因此改用临时文件型 SQLite，确保连接共享同一数据库。
 */
class JdbcDataLoaderTest {

    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";
    private File tempDbFile;
    private String sqliteUrl;

    @BeforeEach
    void setUp() throws Exception {
        // 创建临时 SQLite 文件，确保 DriverManager 和 HikariCP 访问同一数据库
        tempDbFile = File.createTempFile("jdbcloader_test_", ".db");
        tempDbFile.deleteOnExit();
        sqliteUrl = "jdbc:sqlite:" + tempDbFile.getAbsolutePath();

        // 在 SQLite 文件中创建测试表并插入数据
        try (Connection conn = DriverManager.getConnection(sqliteUrl);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS testcase (" +
                    "id INTEGER PRIMARY KEY, " +
                    "query TEXT NOT NULL, " +
                    "expected TEXT)");
            st.execute("DELETE FROM testcase");
            st.execute("INSERT INTO testcase (query, expected) VALUES ('hello world', '预期回复1')");
            st.execute("INSERT INTO testcase (query, expected) VALUES ('test query', '预期回复2')");
        }
    }

    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }

    /**
     * 测试 JdbcDataLoader 可以通过 SQLite 文件数据库正常加载数据
     */
    @Test
    void testLoadDataFromSQLite() throws Exception {
        JdbcDataLoader jdbcDataLoader = new JdbcDataLoader(
                JdbcDataLoaderConfig.builder()
                        .driver(SQLITE_DRIVER)
                        .url(sqliteUrl)
                        // SQLite 不需要用户名，但 validConfig 要求非空，传 "sa" 作为占位符
                        .user("sa")
                        .password("")
                        .build()
        ) {
            @Override
            public String prepareSql() {
                return "SELECT * FROM testcase";
            }
        };

        List<InputData> dataList = jdbcDataLoader.prepareDataList();
        assertNotNull(dataList, "加载的数据列表不应为 null");
        assertEquals(2, dataList.size(), "应加载 2 条测试数据");

        // 验证数据内容
        InputData first = dataList.get(0);
        assertNotNull(first.getInputItem(), "数据项的 inputItem 不应为 null");
        assertTrue(first.getInputItem().containsKey("query"), "应包含 query 字段");
    }

    /**
     * 测试 JdbcDataLoader 校验逻辑：driver 为空时应抛出异常
     */
    @Test
    void testEmptyDriverThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new JdbcDataLoader(
                        JdbcDataLoaderConfig.builder()
                                .driver("")
                                .url(sqliteUrl)
                                .user("")
                                .password("")
                                .build()
                ) {
                    @Override
                    public String prepareSql() { return "SELECT * FROM testcase"; }
                }
        );
    }
}