package com.evalkit.framework.eval.mapper;

import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;
import org.apache.commons.collections4.CollectionUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * MQ消息处理记录Mapper
 */
public class MQMessageProcessedMapper {
    private final SQLiteEmbeddedServer server;

    public MQMessageProcessedMapper(SQLiteEmbeddedServer server) throws SQLException {
        this.server = server;
        if (!server.isTableExists("tb_mq_message_processed")) {
            createTable();
        }
    }

    /**
     * 消息幂等表，只防重，不存业务数据
     */
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE tb_mq_message_processed (\n" +
                "  message_id  TEXT PRIMARY KEY,\n" +
                "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP\n" +
                ")";
        server.createTable(sql);
    }

    /**
     * 幂等插入，冲突忽略
     */
    public void insert(String messageId) throws SQLException {
        String sql = "INSERT INTO tb_mq_message_processed (message_id) VALUES (?)";
        server.executeUpdate(sql, messageId);
    }

    /**
     * messageId是否存在
     */
    public boolean exists(String messageId) throws SQLException {
        String sql = "SELECT message_id FROM tb_mq_message_processed WHERE message_id = ?";
        List<Map<String, Object>> maps = server.executeQuery(sql, messageId);
        return CollectionUtils.isNotEmpty(maps);
    }
}
