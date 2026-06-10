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
     * 按 InputData 中的指定字段值查询已完成的 DataItem 列表（精确匹配）。
     * 底层遍历全表在 Java 侧过滤，适用于同组数据量小（如多轮对话同 sessionId）的场景。
     *
     * @param fieldKey   InputData 中的字段名
     * @param fieldValue 要匹配的字段值
     * @return 满足条件的 DataItem 列表（保持插入顺序）
     */
    public List<DataItem> queryByInputField(String fieldKey, Object fieldValue) throws SQLException {
        List<DataItem> all = queryAll();
        List<DataItem> result = new ArrayList<>();
        for (DataItem item : all) {
            if (item.getInputData() == null) continue;
            Object v = item.getInputData().get(fieldKey);
            if (java.util.Objects.equals(String.valueOf(v), String.valueOf(fieldValue))) {
                result.add(item);
            }
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
