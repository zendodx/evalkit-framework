package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.model.InputData;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库数据加载器
 */
@Slf4j
public abstract class JdbcDataLoader extends DataLoader {

    protected final HikariDataSource ds;

    public JdbcDataLoader(String driverClassName, String jdbcUrl, String user, String pwd) {
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

    /**
     * 准备加载数据的SQL
     */
    public abstract String prepareSql();

    @Override
    public List<InputData> prepareDataList() throws SQLException {
        try (Connection conn = ds.getConnection()) {
            List<InputData> inputDataList = new ArrayList<>();
            // 获取读取数据的SQL
            String sql = prepareSql();
            PreparedStatement statement = conn.prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                InputData inputData = new InputData();
                Map<String, Object> inputItem = new HashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    inputItem.put(rs.getMetaData().getColumnName(i), rs.getObject(i));
                }
                inputData.setInputItem(inputItem);
                inputDataList.add(inputData);
            }
            return inputDataList;
        } catch (SQLException e) {
            log.error("Load JDBC data error");
            throw e;
        }
    }
}