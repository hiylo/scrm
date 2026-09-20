/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExportService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * SCRM 数据导出服务。
 * <p>
 * 提供通用的 Excel (xlsx) 与 CSV 导出能力, 由各 Controller 构建 {@link ExportColumn} 列定义后调用。
 * Excel 使用 Apache POI ({@link XSSFWorkbook}) 生成, 表头加粗, 自动列宽;
 * CSV 使用 UTF-8 BOM 头保证 Excel 打开不乱码, 字段含逗号 / 引号 / 换行时按 RFC 4180 转义。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmExportService {

    /** Jackson JSON 序列化器, 用于复杂对象值的格式化 */
    private final ObjectMapper objectMapper;

    /**
     * 通用 Excel 导出。
     *
     * @param data      数据列表
     * @param columns   列定义
     * @param sheetName 工作表名称
     * @param <T>       数据类型
     * @return xlsx 字节流
     */
    public <T> byte[] exportToExcel(List<T> data, List<ExportColumn<T>> columns, String sheetName) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName);
            // 表头样式: 加粗
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            // 写表头行
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                headerRow.createCell(i).setCellValue(columns.get(i).getHeader());
                headerRow.getCell(i).setCellStyle(headerStyle);
            }
            // 写数据行
            if (data != null) {
                for (int r = 0; r < data.size(); r++) {
                    T item = data.get(r);
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < columns.size(); c++) {
                        Object value = columns.get(c).getGetter().apply(item);
                        row.createCell(c).setCellValue(formatValue(value));
                    }
                }
            }
            // 自动调整列宽
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Excel 导出失败: sheetName={}, rows={}",
                    sheetName, data == null ? 0 : data.size(), e);
            throw new IllegalStateException("Excel 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通用 CSV 导出。
     *
     * @param data    数据列表
     * @param columns 列定义
     * @param <T>     数据类型
     * @return CSV 字节流 (UTF-8, 含 BOM)
     */
    public <T> byte[] exportToCsv(List<T> data, List<ExportColumn<T>> columns) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // UTF-8 BOM 头, 保证 Excel 打开不乱码
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                // 表头
                StringBuilder header = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        header.append(',');
                    }
                    header.append(escapeCsv(columns.get(i).getHeader()));
                }
                header.append("\r\n");
                writer.write(header.toString());
                // 数据行
                if (data != null) {
                    for (T item : data) {
                        StringBuilder line = new StringBuilder();
                        for (int i = 0; i < columns.size(); i++) {
                            if (i > 0) {
                                line.append(',');
                            }
                            line.append(escapeCsv(formatValue(columns.get(i).getGetter().apply(item))));
                        }
                        line.append("\r\n");
                        writer.write(line.toString());
                    }
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            log.error("CSV 导出失败: rows={}", data == null ? 0 : data.size(), e);
            throw new IllegalStateException("CSV 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建导出响应实体, 统一设置 Content-Type 与 Content-Disposition。
     * <p>
     * Content-Disposition 同时提供 ASCII {@code filename} 与 RFC 5987 编码的
     * {@code filename*}, 以兼容中文文件名。
     * </p>
     *
     * @param data     导出字节流
     * @param fileName 文件名 (含扩展名)
     * @param csv      是否 CSV 格式 (true=csv, false=xlsx)
     * @return 包含导出数据的响应实体
     */
    public ResponseEntity<byte[]> buildExportResponse(byte[] data, String fileName, boolean csv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(csv
                ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        headers.add("Content-Disposition",
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * 便捷导出方法: 根据格式生成字节流并构建响应实体。
     * <p>
     * 文件名格式为 {@code <filePrefix>_<yyyyMMddHHmmss>.<ext>}, 由各 Controller 直接返回。
     * </p>
     *
     * @param data       数据列表
     * @param columns    列定义
     * @param sheetName  Excel 工作表名称 (CSV 导出时忽略)
     * @param filePrefix 文件名前缀
     * @param format     导出格式: xlsx (默认) / csv
     * @param <T>        数据类型
     * @return 导出响应实体
     */
    public <T> ResponseEntity<byte[]> export(List<T> data, List<ExportColumn<T>> columns,
                                             String sheetName, String filePrefix, String format) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        boolean csv = "csv".equalsIgnoreCase(format);
        String ext = csv ? "csv" : "xlsx";
        String fileName = filePrefix + "_" + timestamp + "." + ext;
        byte[] bytes = csv
                ? exportToCsv(data, columns)
                : exportToExcel(data, columns, sheetName);
        return buildExportResponse(bytes, fileName, csv);
    }

    /**
     * 格式化单元格值: null → 空字符串; 基础类型 → toString; 复杂对象 → JSON。
     *
     * @param value 原始值
     * @return 字符串表示
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Enum
                || value instanceof Character) {
            return value.toString();
        }
        // 复杂对象使用 ObjectMapper 序列化为 JSON, 失败回退 toString
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return value.toString();
        }
    }

    /**
     * CSV 字段转义 (RFC 4180): 值含逗号 / 引号 / 换行时用双引号包裹, 内部引号转义为两个引号。
     *
     * @param value 原始值
     * @return 转义后的值
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * 导出列定义。
     *
     * @author Hsi Chu
     * @param <T> 数据类型
     */
    @Getter
    @AllArgsConstructor
    public static class ExportColumn<T> {

        /** 列标题 */
        private String header;

        /** 值提取函数 */
        private Function<T, Object> getter;

        /**
         * 静态工厂方法, 简化列定义构建。
         *
         * @param header 列标题
         * @param getter 值提取函数
         * @param <T>    数据类型
         * @return ExportColumn 实例
         */
        public static <T> ExportColumn<T> of(String header, Function<T, Object> getter) {
            return new ExportColumn<>(header, getter);
        }
    }
}
