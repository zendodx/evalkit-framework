package com.evalkit.framework.eval.mapper;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.infra.server.sql.SQLiteEmbeddedServer;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * tb_data_item表Mapper
 */
@Slf4j
public class DataItemMapper {
    private final SQLiteEmbeddedServer server;

    public DataItemMapper(SQLiteEmbeddedServer server) throws SQLException {
        this.server = server;
        if (!server.isTableExists("tb_data_item")) {
            createTable();
        }
    }

    /**
     * 插入data_item
     */
    public void insert(DataItem dataItem) throws SQLException {
        if (dataItem == null) {
            throw new IllegalArgumentException("DataItem cannot be null");
        }
        String sql = "INSERT INTO tb_data_item (id,data_item) VALUES (?,?)";
        String dataItemJson = JsonUtils.toJson(dataItem);
        server.executeUpdate(sql, dataItem.getDataIndex(), dataItemJson);
    }

    /**
     * 获取data_item数量
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM tb_data_item";
        List<Map<String, Object>> maps = server.executeQuery(sql);
        return (int) maps.get(0).get("COUNT(*)");
    }

    /**
     * 获取所有data_item
     */
    public List<DataItem> queryAll() throws SQLException {
        String sql = "SELECT data_item FROM tb_data_item";
        List<Map<String, Object>> maps = server.executeQuery(sql);
        List<DataItem> result = new ArrayList<>();
        for (Map<String, Object> map : maps) {
            String dataItemJson = (String) map.get("data_item");
            DataItem dataItem = JsonUtils.fromJson(dataItemJson, DataItem.class);
            result.add(dataItem);
        }
        return result;
    }

    /**
     * 创建data_item表
     */
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE tb_data_item\n" +
                "(\n" +
                "    id        INTEGER PRIMARY KEY,\n" +
                "    data_item TEXT NOT NULL\n" +
                ")";
        server.createTable(sql);
    }
}
