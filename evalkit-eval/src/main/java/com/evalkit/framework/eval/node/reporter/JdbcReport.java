package com.evalkit.framework.eval.node.reporter;

import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Jdbc结果上报器
 */
@Slf4j
public abstract class JdbcReport extends Reporter {
    private final HikariDataSource ds;
    private final String createTableSqlTemplate = "create table `{{tableName}}`\n" +
            "(\n" +
            "    `id`                    bigint       not null auto_increment comment 'id',\n" +
            "    `group_id`              varchar(100) not null comment 'group_id',\n" +
            "    `data_index`            bigint       not null comment '数据索引',\n" +
            "    `input_data`            json comment '输入数据',\n" +
            "    `api_completion_result` json comment '接口调用结果',\n" +
            "    `scorer_results`        json comment '评估器结果',\n" +
            "    `extra`                 json comment '额外数据',\n" +
            "    primary key (`id`),\n" +
            "    index idx_group_id (`group_id`)\n" +
            ");";
    private final String insertSqlTemplate = "insert into `{{tableName}}` (group_id, data_index, input_data, api_completion_result, scorer_results, extra) VALUES (?, ?, ?, ?, ?, ?);";

    public JdbcReport(String driverClassName, String jdbcUrl, String user, String pwd) {
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName(driverClassName);
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pwd);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(5000);
        this.ds = new HikariDataSource(cfg);
    }

    public abstract String prepareTableName();

    @Override
    protected void report(ReportData reportData) {
        List<DataItem> items = reportData.getDataItems();
        String tableName = prepareTableName();
        if (StringUtils.isEmpty(tableName)) {
            throw new EvalException("Table name is empty");
        }
        String createTableSql = createTableSqlTemplate.replace("{{tableName}}", tableName);
        String insertSql = insertSqlTemplate.replace("{{tableName}}", tableName);
        String groupId = UuidUtils.generateUuid();
        Connection conn = null;
        try {
            conn = ds.getConnection();
            conn.setAutoCommit(false);
            // 建表
            if (!tableExists(conn, tableName)) {
                Statement st = null;
                try {
                    st = conn.createStatement();
                    st.executeUpdate(createTableSql);
                } finally {
                    if (st != null) {
                        st.close();
                    }
                }
            }
            // 批量插入
            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(insertSql);
                for (DataItem item : items) {
                    InputData inputData = item.getInputData();
                    ApiCompletionResult apiCompletionResult = item.getApiCompletionResult();
                    List<ScorerResult> scorerResults = item.getEvalResult().getScorerResults();
                    Map<String, Object> extra = item.getExtra();
                    ps.setString(1, groupId);
                    ps.setLong(2, inputData.getDataIndex());
                    ps.setString(3, JsonUtils.toJson(inputData));
                    ps.setString(4, JsonUtils.toJson(apiCompletionResult));
                    ps.setString(5, JsonUtils.toJson(scorerResults));
                    ps.setString(6, JsonUtils.toJson(extra));
                    ps.executeUpdate();
                }
            } finally {
                if (ps != null) {
                    ps.close();
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            log.error("Jdbc report execute error", ex);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    log.error("Jdbc report rollback error", e);
                }
            }
            throw new RuntimeException(ex);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    log.error("Jdbc report connection close error", e);
                }
            }
        }
    }

    public static boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String tn = tableName;
        // 注意：部分数据库区分大小写
        ResultSet rs = null;
        try {
            rs = meta.getTables(null, null, tn, new String[]{"TABLE"});
            return rs.next();
        } finally {
            if (rs != null) {
                rs.close();
            }
        }
    }
}
