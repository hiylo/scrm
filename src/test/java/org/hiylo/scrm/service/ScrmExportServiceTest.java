/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmExportServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.service.ScrmExportService.ExportColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ScrmExportService 单元测试
 * <p>
 * 聚焦通用 Excel (xlsx) 与 CSV 导出能力: 表头与数据行写入、CSV 字段转义 (RFC 4180)、
 * null 值与复杂对象 JSON 格式化、空数据列表处理、默认 Sheet 名兜底、
 * buildExportResponse 的 Content-Type 与 Content-Disposition 头设置, 以及
 * 便捷 export 方法按格式 (xlsx/csv) 生成响应。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmExportService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmExportServiceTest {

    /** 被测服务实例 */
    private ScrmExportService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ScrmExportService(objectMapper);
    }

    // ==================== Excel 导出 ====================

    @Test
    @DisplayName("exportToExcel: 写入表头与数据行, 返回非空 xlsx 字节流")
    void exportToExcel_success() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "张三", "age", 28),
                Map.of("name", "李四", "age", 35));

        byte[] bytes = service.exportToExcel(data,
                Arrays.asList(
                        ExportColumn.of("姓名", row -> row.get("name")),
                        ExportColumn.of("年龄", row -> row.get("age"))),
                "客户列表");

        assertThat(bytes).isNotEmpty();
        // xlsx 文件头 magic bytes: PK (ZIP)
        assertThat(bytes[0]).isEqualTo((byte) 0x50);
        assertThat(bytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("exportToExcel: sheetName 为空时兜底 Sheet1")
    void exportToExcel_blankSheetName_fallbackSheet1() {
        byte[] bytes = service.exportToExcel(Collections.emptyList(),
                Collections.singletonList(ExportColumn.of("列1", row -> "")),
                "");
        assertThat(bytes).isNotEmpty();
    }

    @Test
    @DisplayName("exportToExcel: data 为 null 时不抛异常, 仅输出表头")
    void exportToExcel_nullData_onlyHeader() {
        byte[] bytes = service.exportToExcel(null,
                Collections.singletonList(ExportColumn.of("列1", row -> "")),
                "Sheet1");
        assertThat(bytes).isNotEmpty();
    }

    // ==================== CSV 导出 ====================

    @Test
    @DisplayName("exportToCsv: 写入 UTF-8 BOM 头与表头数据行")
    void exportToCsv_success() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "张三", "city", "北京"));

        byte[] bytes = service.exportToCsv(data,
                Arrays.asList(
                        ExportColumn.of("姓名", row -> row.get("name")),
                        ExportColumn.of("城市", row -> row.get("city"))));

        assertThat(bytes).isNotEmpty();
        // UTF-8 BOM: 0xEF 0xBB 0xBF
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("姓名").contains("城市").contains("张三").contains("北京");
    }

    @Test
    @DisplayName("exportToCsv: 字段含逗号时按 RFC 4180 双引号包裹")
    void exportToCsv_valueWithComma_quoted() {
        List<Map<String, Object>> data = List.of(
                Map.of("text", "你好,世界"));

        byte[] bytes = service.exportToCsv(data,
                Collections.singletonList(ExportColumn.of("内容", row -> row.get("text"))));

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("\"你好,世界\"");
    }

    @Test
    @DisplayName("exportToCsv: 字段含双引号时转义为两个双引号并整体包裹")
    void exportToCsv_valueWithQuote_escaped() {
        List<Map<String, Object>> data = List.of(
                Map.of("text", "说\"嗨\""));

        byte[] bytes = service.exportToCsv(data,
                Collections.singletonList(ExportColumn.of("内容", row -> row.get("text"))));

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("\"说\"\"嗨\"\"\"");
    }

    @Test
    @DisplayName("exportToCsv: 字段含换行时按双引号包裹")
    void exportToCsv_valueWithNewline_quoted() {
        List<Map<String, Object>> data = List.of(
                Map.of("text", "第一行\n第二行"));

        byte[] bytes = service.exportToCsv(data,
                Collections.singletonList(ExportColumn.of("内容", row -> row.get("text"))));

        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("\"第一行\n第二行\"");
    }

    @Test
    @DisplayName("exportToCsv: data 为 null 时仅输出表头")
    void exportToCsv_nullData_onlyHeader() {
        byte[] bytes = service.exportToCsv(null,
                Collections.singletonList(ExportColumn.of("列1", row -> "")));
        String content = new String(bytes, StandardCharsets.UTF_8);
        // 仅 BOM + 表头 + CRLF
        assertThat(content).contains("列1").doesNotContain("null");
    }

    @Test
    @DisplayName("exportToCsv: null 值格式化为空字符串")
    void exportToCsv_nullValue_emptyString() {
        List<Map<String, Object>> data = List.of(
                Map.of("name", "张三"));

        byte[] bytes = service.exportToCsv(data,
                Arrays.asList(
                        ExportColumn.of("姓名", row -> row.get("name")),
                        ExportColumn.of("年龄", row -> row.get("age"))));

        String content = new String(bytes, StandardCharsets.UTF_8);
        // 张三,  (年龄列空字符串)
        assertThat(content).contains("张三,");
    }

    @Test
    @DisplayName("exportToCsv: 复杂对象值通过 ObjectMapper 序列化为 JSON")
    void exportToCsv_complexObject_jsonValue() {
        List<Map<String, Object>> data = List.of(
                Map.of("meta", Map.of("k", "v")));

        byte[] bytes = service.exportToCsv(data,
                Collections.singletonList(ExportColumn.of("元数据", row -> row.get("meta"))));

        String content = new String(bytes, StandardCharsets.UTF_8);
        // CSV 转义: JSON {"k":"v"} 内的双引号被翻倍为 ""
        assertThat(content).contains("\"\"k\"\"").contains("\"\"v\"\"");
    }

    // ==================== 响应构建 ====================

    @Test
    @DisplayName("buildExportResponse: csv=true 设置 text/csv Content-Type 与 Content-Disposition")
    void buildExportResponse_csv() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<byte[]> resp = service.buildExportResponse(data, "客户列表.csv", true);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType()).isNotNull();
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(resp.getHeaders().getFirst("Content-Disposition")).contains("客户列表.csv");
        // RFC 5987 编码的 filename* 字段
        assertThat(resp.getHeaders().getFirst("Content-Disposition")).contains("filename*=");
        assertThat(resp.getHeaders().getContentLength()).isEqualTo(data.length);
        assertThat(resp.getBody()).isEqualTo(data);
    }

    @Test
    @DisplayName("buildExportResponse: csv=false 设置 xlsx Content-Type")
    void buildExportResponse_xlsx() {
        byte[] data = "PK".getBytes(StandardCharsets.UTF_8);
        ResponseEntity<byte[]> resp = service.buildExportResponse(data, "客户列表.xlsx", false);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType()).isNotNull();
        assertThat(resp.getHeaders().getContentType().toString())
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(resp.getHeaders().getFirst("Content-Disposition")).contains("客户列表.xlsx");
    }

    @Test
    @DisplayName("buildExportResponse: 中文文件名经 URLEncoder 编码替换 + 为 %20")
    void buildExportResponse_chineseFilename_encoded() {
        byte[] data = new byte[]{1, 2, 3};
        ResponseEntity<byte[]> resp = service.buildExportResponse(data, "客户 列表.csv", true);
        String disposition = resp.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull();
        // 空格编码为 %20 而非 +
        assertThat(disposition).contains("%20").doesNotContain("+");
    }

    // ==================== 便捷导出 ====================

    @Test
    @DisplayName("export: 默认 xlsx 格式, 文件名含前缀与时间戳")
    void export_defaultXlsx() {
        List<Map<String, Object>> data = List.of(Map.of("name", "张三"));
        ResponseEntity<byte[]> resp = service.export(data,
                Collections.singletonList(ExportColumn.of("姓名", row -> row.get("name"))),
                "Sheet1", "客户导出", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String disposition = resp.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull().contains("客户导出_").contains(".xlsx");
    }

    @Test
    @DisplayName("export: format=csv 生成 csv 文件名与 text/csv 类型")
    void export_csv() {
        List<Map<String, Object>> data = List.of(Map.of("name", "张三"));
        ResponseEntity<byte[]> resp = service.export(data,
                Collections.singletonList(ExportColumn.of("姓名", row -> row.get("name"))),
                "Sheet1", "客户导出", "csv");

        String disposition = resp.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull().contains("客户导出_").contains(".csv");
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/csv");
    }

    @Test
    @DisplayName("export: format 大小写不敏感 (CSV 同 csv)")
    void export_csvCaseInsensitive() {
        List<Map<String, Object>> data = List.of(Map.of("name", "张三"));
        ResponseEntity<byte[]> resp = service.export(data,
                Collections.singletonList(ExportColumn.of("姓名", row -> row.get("name"))),
                "Sheet1", "客户导出", "CSV");

        String disposition = resp.getHeaders().getFirst("Content-Disposition");
        assertThat(disposition).isNotNull().contains(".csv");
    }
}
