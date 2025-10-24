package com.evalkit.framework.eval.mapper;

import com.evalkit.framework.common.utils.time.DateUtils;
import com.evalkit.framework.eval.constants.EvalTaskStatus;
import com.evalkit.framework.eval.model.EvalTask;
import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 评测任务Mapper
 */
public class EvalTaskMapper {
    private final SQLiteEmbeddedServer server;

    public EvalTaskMapper(SQLiteEmbeddedServer server) throws SQLException {
        this.server = server;
        if (!server.isTableExists("tb_eval_task")) {
            createTable();
        }
    }

    /**
     * 创建评测任务
     */
    public void createEvalTask(EvalTask task) throws SQLException {
        String sql = "INSERT INTO tb_eval_task (task_name, all_count, status, create_time, update_time) VALUES (?, ?, ?, ?, ?)";
        Date now = new Date();
        String timestamp = String.valueOf(DateUtils.timestamp(now));
        server.executeUpdate(sql, task.getTaskName(), task.getAllCount(), task.getStatus(), timestamp, timestamp);
    }

    /**
     * 查询评测任务是否存在
     */
    public boolean isEvalTaskExists(String taskName) throws SQLException {
        String sql = "SELECT * FROM tb_eval_task WHERE task_name = ?";
        List<Map<String, Object>> maps = server.executeQuery(sql, taskName);
        return !maps.isEmpty();
    }

    /**
     * 查询评测数据总数executeQueryForLong
     */
    public int queryTotalCount(String taskName) throws SQLException {
        String sql = "SELECT all_count FROM tb_eval_task WHERE task_name = ?";
        List<Map<String, Object>> maps = server.executeQuery(sql, taskName);
        return (int) maps.get(0).get("all_count");
    }

    /**
     * 查询评测任务状态
     */
    public int queryStatus(String taskName) throws SQLException {
        String sql = "SELECT status FROM tb_eval_task WHERE task_name = ?";
        List<Map<String, Object>> maps = server.executeQuery(sql, taskName);
        return (int) maps.get(0).get("status");
    }

    /**
     * 更新评测任务状态
     */
    public void updateStatus(String taskName, int status) throws SQLException {
        String sql = "UPDATE tb_eval_task SET status = ?, update_time = ? WHERE task_name = ?";
        Date now = new Date();
        String timestamp = String.valueOf(DateUtils.timestamp(now));
        if (status == EvalTaskStatus.FINISH) {
            // 任务完成状态则更新finish_time
            sql = "UPDATE tb_eval_task SET status = ?, update_time = ?, finish_time = ? WHERE task_name = ?";
            server.executeUpdate(sql, status, timestamp, timestamp, taskName);
        } else {
            server.executeUpdate(sql, status, timestamp, taskName);
        }
    }

    /**
     * 更新评测数据总数
     */
    public void updateAllCount(String taskName, long count) throws SQLException {
        String sql = "UPDATE tb_eval_task SET all_count = ? WHERE task_name = ?";
        server.executeUpdate(sql, count, taskName);
    }

    /**
     * 创建tb_eval_task表
     */
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE tb_eval_task\n" +
                "(\n" +
                "    task_name TEXT PRIMARY KEY,\n" +
                "    all_count INTEGER NOT NULL,\n" +
                "    status INTEGER NOT NULL,\n" +
                "    create_time TEXT NOT NULL,\n" +
                "    update_time TEXT NOT NULL,\n" +
                "    finish_time TEXT \n" +
                ")";
        server.createTable(sql);
    }
}
