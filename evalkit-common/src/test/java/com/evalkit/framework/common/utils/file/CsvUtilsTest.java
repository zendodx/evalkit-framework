package com.evalkit.framework.common.utils.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvUtilsTest {

    @TempDir
    Path tempDir;

    /**
     * writeCsv(Map列表) + readCsv(文件路径) 基本读写场景
     */
    @Test
    void testWriteAndReadCsv_withMapData() {
        String fileName = tempDir.resolve("map-data.csv").toString();

        List<Map<String, Object>> dataList = new ArrayList<>();
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("name", "张三");
        row1.put("age", 18);
        dataList.add(row1);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("name", "李四");
        row2.put("age", 20);
        dataList.add(row2);

        CsvUtils.writeCsv(fileName, dataList, ",");

        List<Map<String, Object>> result = CsvUtils.readCsv(fileName, ",", true);

        assertEquals(2, result.size());
        assertEquals("张三", result.get(0).get("name"));
        assertEquals("18", result.get(0).get("age"));
        assertEquals("李四", result.get(1).get("name"));
        assertEquals("20", result.get(1).get("age"));
    }

    /**
     * writeCsv(指定header) 基本读写场景
     */
    @Test
    void testWriteAndReadCsv_withHeaderAndRows() {
        String fileName = tempDir.resolve("header-data.csv").toString();

        List<String> headers = Arrays.asList("id", "name");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1, "苹果"});
        rows.add(new Object[]{2, "香蕉"});

        CsvUtils.writeCsv(fileName, headers, rows, ",");

        List<Map<String, Object>> result = CsvUtils.readCsv(fileName, ",", true);

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).get("id"));
        assertEquals("苹果", result.get(0).get("name"));
        assertEquals("2", result.get(1).get("id"));
        assertEquals("香蕉", result.get(1).get("name"));
    }

    /**
     * writeCsv header 为空时应抛出异常
     */
    @Test
    void testWriteCsv_emptyHeader_throwsException() {
        String fileName = tempDir.resolve("empty-header.csv").toString();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{1});

        assertThrows(IllegalArgumentException.class, () -> CsvUtils.writeCsv(fileName, Collections.emptyList(), rows, ","));
    }

    /**
     * writeCsv 数据为空时应抛出异常
     */
    @Test
    void testWriteCsv_emptyData_throwsException() {
        String fileName = tempDir.resolve("empty-data.csv").toString();

        assertThrows(IllegalArgumentException.class, () -> CsvUtils.writeCsv(fileName, new ArrayList<>(), ","));
        assertThrows(IllegalArgumentException.class, () -> CsvUtils.writeCsv(fileName, Collections.singletonList("id"), new ArrayList<>(), ","));
    }

    /**
     * readCsv(InputStream) 使用输入流读取，验证分页 offset/limit
     */
    @Test
    void testReadCsv_fromInputStream_withOffsetAndLimit() {
        String csvContent = "name,age\n张三,18\n李四,20\n王五,22\n";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))) {
            List<Map<String, Object>> result = CsvUtils.readCsv(inputStream, ",", true, 1, 1);
            assertEquals(1, result.size());
            assertEquals("李四", result.get(0).get("name"));
            assertEquals("20", result.get(0).get("age"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * readCsv(InputStream) 不含header场景，使用索引作为header
     */
    @Test
    void testReadCsv_fromInputStream_withoutHeader() {
        String csvContent = "张三,18\n李四,20\n";

        try (InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))) {
            List<Map<String, Object>> result = CsvUtils.readCsv(inputStream, ",", false, 0, -1);
            assertEquals(2, result.size());
            assertEquals("张三", result.get(0).get("0"));
            assertEquals("18", result.get(0).get("1"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * readCsv(InputStream) 空文件应抛出异常
     */
    @Test
    void testReadCsv_emptyFile_throwsException() {
        try (InputStream inputStream = new ByteArrayInputStream(new byte[0])) {
            assertThrows(RuntimeException.class, () -> CsvUtils.readCsv(inputStream, ",", true, 0, -1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * readCsv(文件路径) 使用自定义分隔符
     */
    @Test
    void testReadCsv_withCustomDelimiter() {
        String fileName = tempDir.resolve("semicolon-data.csv").toString();
        List<String> headers = Arrays.asList("name", "age");
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"张三", 18});

        CsvUtils.writeCsv(fileName, headers, rows, ";");

        List<Map<String, Object>> result = CsvUtils.readCsv(fileName, ";", true);
        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).get("name"));
        assertEquals("18", result.get(0).get("age"));
    }

    /**
     * readCsv(文件路径) 文件不存在时应抛出异常
     */
    @Test
    void testReadCsv_fileNotExist_throwsException() {
        String fileName = tempDir.resolve("not-exist.csv").toString();
        assertThrows(RuntimeException.class, () -> CsvUtils.readCsv(fileName, ",", true));
    }
}