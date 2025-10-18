package com.evalkit.framework.common.utils.file;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV工具类
 */
public class CsvUtils {
    private CsvUtils() {
    }

    /**
     * 写入Csv文件, header取Map键名
     */
    public static void writeCsv(String fileName, List<Map<String, Object>> dataList, String delimiter) {
        if (CollectionUtils.isEmpty(dataList)) {
            throw new IllegalArgumentException("Data is empty");
        }
        List<String> headers = new ArrayList<>(dataList.get(0).keySet());
        List<Object[]> rows = new ArrayList<>(dataList.size());
        for (Map<String, Object> row : dataList) {
            Object[] cols = headers.stream().map(h -> row.getOrDefault(h, "")).toArray();
            rows.add(cols);
        }
        writeCsv(fileName, headers, rows, delimiter);
    }

    /**
     * 写入Csv文件,指定header
     */
    public static void writeCsv(String fileName, List<String> headers, List<Object[]> dataList, String delimiter) {
        if (CollectionUtils.isEmpty(headers)) {
            throw new IllegalArgumentException("Header is empty");
        }
        if (CollectionUtils.isEmpty(dataList)) {
            throw new IllegalArgumentException("Data is empty");
        }
        Path path = Paths.get(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = CSVFormat.DEFAULT.builder()
                     .setHeader(headers.toArray(new String[0]))
                     .setDelimiter(delimiter != null ? delimiter : ",")
                     .build().print(writer)) {
            for (Object[] row : dataList) {
                printer.printRecord(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("Writing csv file error:" + e.getMessage(), e);
        }
    }

    /**
     * 读取所有Csv文件
     */
    public static List<Map<String, Object>> readCsv(String fileName, String delimiter, boolean hasHeader) {
        return readCsv(fileName, delimiter, hasHeader, 0, -1);
    }

    /**
     * 分页读取Csv文件
     */
    public static List<Map<String, Object>> readCsv(String fileName, String delimiter, boolean hasHeader, int offset, int limit) {
        Path path = getPath(fileName);
        List<Map<String, Object>> data = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setDelimiter(delimiter != null ? delimiter : ",")
                     .build().parse(reader)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                throw new RuntimeException("CSV file is empty");
            }
            List<String> headers;
            CSVRecord firstRow = records.get(0);
            if (hasHeader) {
                headers = new ArrayList<>();
                firstRow.forEach(headers::add);
            } else {
                headers = new ArrayList<>();
                for (int i = 0; i < firstRow.size(); i++) {
                    headers.add(String.valueOf(i));
                }
            }
            if (CollectionUtils.isEmpty(headers)) {
                throw new RuntimeException("Header is empty");
            }
            int startIndex = hasHeader ? 1 : 0;
            int rowCount = 0;
            for (int i = startIndex; i < records.size(); i++) {
                if (offset > 0) {
                    offset--;
                    continue;
                }
                if (limit >= 0 && rowCount >= limit) {
                    break;
                }
                CSVRecord record = records.get(i);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int col = 0; col < headers.size(); col++) {
                    String header = headers.get(col);
                    row.put(header, col < record.size() ? record.get(col) : "");
                }
                data.add(row);
                rowCount++;
            }
        } catch (Exception e) {
            throw new RuntimeException("Reading CSV file error: " + e.getMessage(), e);
        }
        return data;
    }

    /**
     * 获取文件的路径
     */
    private static Path getPath(String fileName) {
        if (fileName.startsWith("classpath:")) {
            String realFilePath = fileName.replace("classpath:", "");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL resource = classLoader.getResource(realFilePath);
            if (resource == null) {
                throw new RuntimeException("Cannot find file " + realFilePath);
            }
            return Paths.get(resource.getPath());
        }
        return Paths.get(fileName);
    }
}
