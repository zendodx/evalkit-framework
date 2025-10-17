package com.evalkit.framework.infra.service.sql;

import com.evalkit.framework.common.utils.map.MapUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite嵌入式服务
 */
@Slf4j
public class SQLiteEmbeddedServer {
    private String dbUrl;
    private String dbFilePath;

    private static class InstanceHolder {
        static final SQLiteEmbeddedServer instance = new SQLiteEmbeddedServer();
    }

    public static SQLiteEmbeddedServer getInstance() {
        return InstanceHolder.instance;
    }

    /**
     * 启动嵌入式服务
     */
    public void start(String filePath) throws SQLException {
        this.dbFilePath = filePath + ".db";
        this.dbUrl = "jdbc:sqlite:" + dbFilePath;
        // 启动时创建db文件然后关闭
        getConnection().close();
    }

    /**
     * 获取数据库连接
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * 执行更新
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            return ps.executeUpdate();
        }
    }

    /**
     * 执行查询,返回Map列表
     */
    public List<Map<String, Object>> executeQuery(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                int columnCount = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        }
        return result;
    }

    /**
     * 执行查询,返回对象列表
     */
    public <T> List<T> executeQuery(String sql, Class<T> clazz, Object... params) throws SQLException {
        List<Map<String, Object>> maps = executeQuery(sql, params);
        List<T> result = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            result.add(MapUtils.fromMap(map, clazz));
        }
        return result;
    }

    /**
     * 创建表
     */
    public void createTable(String sql) throws SQLException {
        executeUpdate(sql);
    }

    /**
     * 设置参数
     */
    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    /**
     * 删除db文件
     */
    public void deleteDBFile() {
        if (StringUtils.isEmpty(dbFilePath)) {
            return;
        }
        File file = new File(dbFilePath);
        if (file.exists()) {
            boolean delete = file.delete();
            log.info("Delete db file: {}, result: {}", dbFilePath, delete);
        }
    }

    /**
     * 判断表是否存在
     */
    public boolean isTableExists(String tableName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
                return rs.next();
            }
        }
    }
}
