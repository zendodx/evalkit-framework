package com.evalkit.framework.common.utils.file;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel工具类
 */
public class ExcelUtils {

    private ExcelUtils() {
    }

    /**
     * 写Excel
     */
    public static void writeExcel(String filePath, List<Map<String, Object>> data, boolean isWriteHeader) {
        if (data == null || data.isEmpty()) {
            throw new IllegalArgumentException("data is empty");
        }
        List<String> headers = new ArrayList<>(data.get(0).keySet());
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(filePath)) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            int rowNum = 0;
            // 写表头
            if (isWriteHeader) {
                Row headerRow = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.size(); i++) {
                    headerRow.createCell(i).setCellValue(headers.get(i));
                }
            }
            // 写数据
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < headers.size(); i++) {
                    Object value = rowData.get(headers.get(i));
                    row.createCell(i).setCellValue(value != null ? value.toString() : "");
                }
            }
            workbook.write(fos);
        } catch (IOException e) {
            throw new RuntimeException("Write excel failed: " + filePath, e);
        }
    }

    /**
     * 读取Excel为二维字符串列表
     */
    public static List<List<String>> readExcelAs2DStringList(String filePath, int sheetIndex, boolean skipFirstRow) {
        if (sheetIndex < 0) {
            throw new IllegalArgumentException("sheetIndex < 0");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(getInputStream(filePath))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("excel sheet is empty");
            }
            XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
            List<List<String>> excelData = new ArrayList<>();
            boolean firstRowSkipped = false;
            for (Row row : sheet) {
                if (skipFirstRow && !firstRowSkipped) {
                    firstRowSkipped = true;
                    continue;
                }
                List<String> rowData = new ArrayList<>();
                for (Cell cell : row) {
                    rowData.add(getCellStringValue(cell));
                }
                excelData.add(rowData);
            }
            return excelData;
        } catch (IOException e) {
            throw new RuntimeException("Read excel failed: " + filePath, e);
        }
    }

    /**
     * 读取Excel为List->Map，支持自定义header、分页
     */
    public static List<Map<String, String>> readExcelAsListMap(String filePath, int sheetIndex, List<String> headers, int offset, int limit) throws IOException {
        try (InputStream inputStream = getInputStream(filePath)) {
            return readExcelAsListMap(inputStream, sheetIndex, headers, offset, limit);
        }
    }

    public static List<Map<String, String>> readExcelAsListMap(File file, int sheetIndex, List<String> headers, int offset, int limit) throws IOException {
        try (InputStream inputStream = getInputStream(file)) {
            return readExcelAsListMap(inputStream, sheetIndex, headers, offset, limit);
        }
    }

    public static List<Map<String, String>> readExcelAsListMap(URL url, int sheetIndex, List<String> headers, int offset, int limit) throws IOException {
        try (InputStream inputStream = getInputStream(url)) {
            return readExcelAsListMap(inputStream, sheetIndex, headers, offset, limit);
        }
    }

    public static List<Map<String, String>> readExcelAsListMap(InputStream inputStream, int sheetIndex, List<String> headers, int offset, int limit) {
        if (sheetIndex < 0) {
            throw new IllegalArgumentException("sheetIndex < 0");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new RuntimeException("Excel sheet is empty!");
            }
            XSSFSheet sheet = workbook.getSheetAt(sheetIndex);
            List<Map<String, String>> excelData = new ArrayList<>();
            List<String> actualHeaders = headers;
            int rowNum = 0;
            for (Row row : sheet) {
                if (rowNum == 0 && actualHeaders == null) {
                    // 自动获取header
                    actualHeaders = new ArrayList<>();
                    for (Cell cell : row) {
                        String cellValue = getCellStringValue(cell);
                        if (StringUtils.isNotBlank(cellValue)) {
                            actualHeaders.add(cellValue);
                        }
                    }
                    rowNum++;
                    continue;
                }
                if (offset > 0) {
                    offset--;
                    rowNum++;
                    continue;
                }
                if (limit >= 0 && excelData.size() >= limit) {
                    break;
                }
                Map<String, String> rowData = new LinkedHashMap<>();
                for (int i = 0; i < actualHeaders.size(); i++) {
                    String cellValue = getCellStringValue(row.getCell(i));
                    rowData.put(actualHeaders.get(i), cellValue);
                }
                excelData.add(rowData);
                rowNum++;
            }
            return excelData;
        } catch (Exception ex) {
            throw new RuntimeException("Read excel failed", ex);
        }
    }

    // 读取Excel为List<Map>，自动header，无分页
    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(String filePath, int sheetIndex) throws IOException {
        return readExcelAsListMap(filePath, sheetIndex, null, 0, -1);
    }

    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(File file, int sheetIndex) throws IOException {
        return readExcelAsListMap(file, sheetIndex, null, 0, -1);
    }

    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(URL url, int sheetIndex) throws IOException {
        return readExcelAsListMap(url, sheetIndex, null, 0, -1);
    }

    // 读取Excel为List<Map>，自动header，支持分页
    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(String filePath, int sheetIndex, int offset, int limit) throws IOException {
        return readExcelAsListMap(filePath, sheetIndex, null, offset, limit);
    }

    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(File file, int sheetIndex, int offset, int limit) throws IOException {
        return readExcelAsListMap(file, sheetIndex, null, offset, limit);
    }

    public static List<Map<String, String>> readExcelAsListMapWithDefaultHeaders(URL url, int sheetIndex, int offset, int limit) throws IOException {
        return readExcelAsListMap(url, sheetIndex, null, offset, limit);
    }

    // 读取Excel为List<Map>，指定header，无分页
    public static List<Map<String, String>> readExcelAsListMapWithTargetHeaders(String filePath, int sheetIndex, List<String> headers) throws IOException {
        return readExcelAsListMap(filePath, sheetIndex, headers, 0, -1);
    }

    public static List<Map<String, String>> readExcelAsListMapWithTargetHeaders(File file, int sheetIndex, List<String> headers) throws IOException {
        return readExcelAsListMap(file, sheetIndex, headers, 0, -1);
    }

    // 读取Excel为List<Map>，指定header，支持分页
    public static List<Map<String, String>> readExcelAsListMapWithTargetHeaders(String filePath, int sheetIndex, List<String> headers, int offset, int limit) throws IOException {
        return readExcelAsListMap(filePath, sheetIndex, headers, offset, limit);
    }

    public static List<Map<String, String>> readExcelAsListMapWithTargetHeaders(File file, int sheetIndex, List<String> headers, int offset, int limit) throws IOException {
        return readExcelAsListMap(file, sheetIndex, headers, offset, limit);
    }

    // 工具方法：获取输入流
    private static InputStream getInputStream(String filePath) throws IOException {
        if (StringUtils.isBlank(filePath)) {
            throw new IllegalArgumentException("filePath is blank");
        }
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return new URL(filePath).openStream();
        } else if (filePath.startsWith("classpath:")) {
            String realFilePath = filePath.replace("classpath:", "");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            InputStream is = classLoader.getResourceAsStream(realFilePath);
            if (is == null) {
                throw new FileNotFoundException("File not found in classpath: " + realFilePath);
            }
            return is;
        } else {
            return new FileInputStream(filePath);
        }
    }

    private static InputStream getInputStream(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("File not found: " + file);
        }
        return new FileInputStream(file);
    }

    private static InputStream getInputStream(URL url) throws IOException {
        if (url == null) {
            throw new IllegalArgumentException("URL is null");
        }
        return url.openStream();
    }

    /**
     * 获取单元格字符串值，空值返回""，日期自动格式化
     */
    private static String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // 日期格式可自定义
                    return cell.getDateCellValue().toString();
                } else {
                    double num = cell.getNumericCellValue();
                    // 去除小数点后.0
                    if (num == (long) num) {
                        return String.valueOf((long) num);
                    } else {
                        return String.valueOf(num);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
