package com.evalkit.framework.eval.node.dataloader;

import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.node.dataloader.config.DataLoaderConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("DataLoader 单元测试")
class DataLoaderTest {

    /**
     * 构造一个简单的 DataLoader 匿名实现，返回指定数量的 InputData
     */
    private DataLoader buildDataLoader(int dataSize) {
        return buildDataLoader(DataLoaderConfig.builder().build(), dataSize);
    }

    private DataLoader buildDataLoader(DataLoaderConfig config, int dataSize) {
        return new DataLoader(config) {
            @Override
            public List<InputData> prepareDataList() {
                return buildInputDataList(dataSize);
            }
        };
    }

    /**
     * 构造测试用的 InputData 列表
     */
    private List<InputData> buildInputDataList(int size) {
        List<InputData> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("value", "v" + i);
            list.add(new InputData((long) i, item));
        }
        return list;
    }

    // ===================== validConfig 测试 =====================

    @Test
    @DisplayName("config 为 null 时应抛出 IllegalArgumentException")
    void testValidConfig_nullConfigThrows() {
        assertThrows(IllegalArgumentException.class, () -> new DataLoader(null) {
            @Override
            public List<InputData> prepareDataList() {
                return new ArrayList<>();
            }
        }, "Config 为 null 时应抛出 IllegalArgumentException");
    }

    @Test
    @DisplayName("offset 为负数时应抛出 IllegalArgumentException")
    void testValidConfig_negativeOffsetThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                        buildDataLoader(DataLoaderConfig.builder().offset(-1).build(), 5),
                "offset 为负数时应抛出 IllegalArgumentException");
    }

    @Test
    @DisplayName("limit 小于 -1 时应抛出 IllegalArgumentException")
    void testValidConfig_limitLessThanNegativeOneThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                        buildDataLoader(DataLoaderConfig.builder().limit(-2).build(), 5),
                "limit 小于 -1 时应抛出 IllegalArgumentException");
    }

    @Test
    @DisplayName("offset=0, limit=-1 为合法配置，不应抛出异常")
    void testValidConfig_zeroOffsetAndNegativeOneLimitOk() {
        assertDoesNotThrow(() -> buildDataLoader(DataLoaderConfig.builder().offset(0).limit(-1).build(), 5),
                "offset=0, limit=-1 为合法配置");
    }

    // ===================== addFilter / addFilters 测试 =====================

    @Test
    @DisplayName("添加 null 过滤器时不应写入过滤器列表")
    void testAddFilter_nullFilterIsIgnored() {
        DataLoader loader = buildDataLoader(5);
        loader.addFilter(null);
        assertTrue(loader.getConfig().getFilters().isEmpty(), "添加 null 过滤器时不应写入列表");
    }

    @Test
    @DisplayName("添加单个过滤器后过滤器列表大小为 1")
    void testAddFilter_singleFilter() {
        DataLoader loader = buildDataLoader(5);
        loader.addFilter(inputData -> true);
        assertEquals(1, loader.getConfig().getFilters().size(), "应成功添加 1 个过滤器");
    }

    @Test
    @DisplayName("批量添加过滤器后过滤器列表大小正确")
    void testAddFilters_multipleFilters() {
        DataLoader loader = buildDataLoader(5);
        loader.addFilters(Arrays.asList(inputData -> true, inputData -> false));
        assertEquals(2, loader.getConfig().getFilters().size(), "应成功添加 2 个过滤器");
    }

    // ===================== setOffsetAndLimit 测试 =====================

    @Test
    @DisplayName("setOffsetAndLimit 应正确更新 config 中的 offset 和 limit")
    void testSetOffsetAndLimit() {
        DataLoader loader = buildDataLoader(10);
        loader.setOffsetAndLimit(2, 3);
        assertEquals(2, loader.getConfig().getOffset());
        assertEquals(3, loader.getConfig().getLimit());
    }

    // ===================== slice 测试 =====================

    @Test
    @DisplayName("limit=-1 时 slice 应返回全部数据")
    void testSlice_limitNegativeOne_returnsAll() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(0).limit(-1).build(), 10);
        List<InputData> data = buildInputDataList(10);
        List<InputData> result = loader.slice(data);
        assertEquals(10, result.size(), "limit=-1 时应返回全部数据");
    }

    @Test
    @DisplayName("slice 按 offset 和 limit 正确截取数据")
    void testSlice_offsetAndLimit() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(2).limit(3).build(), 10);
        List<InputData> data = buildInputDataList(10);
        List<InputData> result = loader.slice(data);
        assertEquals(3, result.size(), "slice 后应返回 3 条数据");
        assertEquals(2L, result.get(0).getDataIndex());
        assertEquals(3L, result.get(1).getDataIndex());
        assertEquals(4L, result.get(2).getDataIndex());
    }

    @Test
    @DisplayName("offset 超过数据总量时 slice 返回空列表")
    void testSlice_offsetBeyondTotal_returnsEmpty() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(20).limit(5).build(), 10);
        List<InputData> data = buildInputDataList(10);
        List<InputData> result = loader.slice(data);
        assertTrue(result.isEmpty(), "offset 超过数据总量时应返回空列表");
    }

    @Test
    @DisplayName("空列表 slice 后仍为空列表")
    void testSlice_emptyList_returnsEmpty() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(0).limit(5).build(), 0);
        List<InputData> result = loader.slice(new ArrayList<>());
        assertTrue(result.isEmpty(), "空列表 slice 后依然为空");
    }

    @Test
    @DisplayName("limit 超过剩余数据量时 slice 返回剩余全部数据")
    void testSlice_limitExceedsRemaining_returnsRest() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(8).limit(5).build(), 10);
        List<InputData> data = buildInputDataList(10);
        List<InputData> result = loader.slice(data);
        assertEquals(2, result.size(), "limit 超过剩余数据量时应返回剩余所有数据");
    }

    // ===================== filter 测试 =====================

    @Test
    @DisplayName("无过滤器时数据列表不应被修改")
    void testFilter_noFilters_listUnchanged() {
        DataLoader loader = buildDataLoader(5);
        List<InputData> data = new ArrayList<>(buildInputDataList(5));
        loader.filter(data);
        assertEquals(5, data.size(), "没有过滤器时数据不应被过滤");
    }

    @Test
    @DisplayName("过滤器拒绝所有数据时列表应为空")
    void testFilter_filterOutAll() {
        DataLoader loader = buildDataLoader(5);
        loader.addFilter(inputData -> false);
        List<InputData> data = new ArrayList<>(buildInputDataList(5));
        loader.filter(data);
        assertTrue(data.isEmpty(), "过滤器拦截所有数据后列表应为空");
    }

    @Test
    @DisplayName("按字段值过滤时只保留满足条件的数据")
    void testFilter_filterByValue() {
        DataLoader loader = buildDataLoader(5);
        loader.addFilter(inputData -> (int) inputData.get("id") < 3);
        List<InputData> data = new ArrayList<>(buildInputDataList(5));
        loader.filter(data);
        assertEquals(3, data.size(), "过滤后应只保留 id=0,1,2 的三条数据");
    }

    @Test
    @DisplayName("多个过滤器之间为 AND 逻辑，同时满足才保留")
    void testFilter_multipleFilters_andLogic() {
        DataLoader loader = buildDataLoader(10);
        loader.addFilter(inputData -> (int) inputData.get("id") >= 2);
        loader.addFilter(inputData -> (int) inputData.get("id") <= 7);
        List<InputData> data = new ArrayList<>(buildInputDataList(10));
        loader.filter(data);
        assertEquals(6, data.size(), "多过滤器应 AND 逻辑，保留 id=2..7 共 6 条数据");
    }

    // ===================== addDataIndex 测试 =====================

    @Test
    @DisplayName("addDataIndex 应从 0 开始顺序为数据项赋予索引")
    void testAddDataIndex_assignsSequentialIndex() {
        DataLoader loader = buildDataLoader(5);
        List<InputData> data = buildInputDataList(5);
        data.forEach(d -> d.setDataIndex(null));
        loader.addDataIndex(data);
        for (int i = 0; i < data.size(); i++) {
            assertEquals(i, data.get(i).getDataIndex(), "索引应从 0 开始顺序递增");
        }
    }

    @Test
    @DisplayName("空列表调用 addDataIndex 不应抛出异常")
    void testAddDataIndex_emptyList_noException() {
        DataLoader loader = buildDataLoader(0);
        assertDoesNotThrow(() -> loader.addDataIndex(new ArrayList<>()),
                "空列表调用 addDataIndex 不应抛出异常");
    }

    // ===================== loadWrapper 测试 =====================

    @Test
    @DisplayName("loadWrapper 正常加载时应返回完整数据列表")
    void testLoadWrapper_success_returnsDataList() {
        DataLoader loader = buildDataLoader(DataLoaderConfig.builder().offset(0).limit(-1).build(), 5);
        List<InputData> result = loader.loadWrapper();
        assertNotNull(result, "loadWrapper 正常情况下应返回非 null 列表");
        assertEquals(5, result.size(), "应返回全部 5 条数据");
    }

    @Test
    @DisplayName("prepareDataList 返回空时 loadWrapper 应返回 null")
    void testLoadWrapper_emptyPrepareDataList_returnsNull() {
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return new ArrayList<>();
            }
        };
        List<InputData> result = loader.loadWrapper();
        assertNull(result, "prepareDataList 返回空时 loadWrapper 应返回 null");
    }

    @Test
    @DisplayName("loadWrapper 配合过滤器应正确过滤数据")
    void testLoadWrapper_withFilter_filtersCorrectly() {
        DataLoaderConfig config = DataLoaderConfig.builder().offset(0).limit(-1).build();
        DataLoader loader = buildDataLoader(config, 10);
        loader.addFilter(inputData -> (int) inputData.get("id") % 2 == 0);
        List<InputData> result = loader.loadWrapper();
        assertNotNull(result);
        assertEquals(5, result.size(), "过滤奇数 id 后应只剩 5 条数据");
        result.forEach(d -> assertEquals(0, (int) d.get("id") % 2, "保留的 id 应均为偶数"));
    }

    @Test
    @DisplayName("loadWrapper 应正确应用 offset 和 limit 截取数据")
    void testLoadWrapper_withOffsetAndLimit() {
        DataLoaderConfig config = DataLoaderConfig.builder().offset(2).limit(3).build();
        DataLoader loader = buildDataLoader(config, 10);
        List<InputData> result = loader.loadWrapper();
        assertNotNull(result);
        assertEquals(3, result.size(), "offset=2, limit=3 时应返回 3 条");
    }

    @Test
    @DisplayName("loadWrapper 返回的每条数据都应设置了 dataIndex")
    void testLoadWrapper_dataIndexAssigned() {
        DataLoader loader = buildDataLoader(5);
        List<InputData> result = loader.loadWrapper();
        assertNotNull(result);
        for (InputData inputData : result) {
            assertNotNull(inputData.getDataIndex(), "每条数据的 dataIndex 不应为 null");
        }
    }

    @Test
    @DisplayName("开启 shuffle 后数据总条数不变且内容完整")
    void testLoadWrapper_shuffleDoesNotLoseData() {
        DataLoaderConfig config = DataLoaderConfig.builder().shuffle(true).build();
        DataLoader loader = buildDataLoader(config, 20);
        List<InputData> result = loader.loadWrapper();
        assertNotNull(result);
        assertEquals(20, result.size(), "shuffle 后数据条数不应改变");
        List<Integer> ids = result.stream()
                .map(d -> (int) d.get("id"))
                .sorted()
                .collect(Collectors.toList());
        List<Integer> expected = IntStream.range(0, 20).boxed().collect(Collectors.toList());
        assertEquals(expected, ids, "shuffle 后 id 集合应仍为 0..19");
    }

    // ===================== constructor 测试 =====================

    @Test
    @DisplayName("无参构造器应初始化默认 config：offset=0, limit=-1")
    void testConstructor_defaultConfig() {
        DataLoader loader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return buildInputDataList(1);
            }
        };
        assertNotNull(loader.getConfig(), "默认构造器应初始化 config");
        assertEquals(0, loader.getConfig().getOffset());
        assertEquals(-1, loader.getConfig().getLimit());
    }

    @Test
    @DisplayName("(offset, limit) 构造器应正确设置 config 中的 offset 和 limit")
    void testConstructor_offsetAndLimit() {
        DataLoader loader = new DataLoader(3, 7) {
            @Override
            public List<InputData> prepareDataList() {
                return buildInputDataList(1);
            }
        };
        assertEquals(3, loader.getConfig().getOffset());
        assertEquals(7, loader.getConfig().getLimit());
    }
}