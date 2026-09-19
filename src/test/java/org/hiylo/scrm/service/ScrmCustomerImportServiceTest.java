/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerImportServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.CustomerImportResultVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCustomerImportService 单元测试
 * <p>
 * 聚焦批量导入核心逻辑: 表头校验、必填字段校验、三元组去重 (DB 已存在 / 本批次已处理)、
 * CONFLICT 异常视为跳过、其他异常计入失败、CSV 解析 (BOM 跳过 / 引号转义)、
 * lifecycle 缺省 NEW、失败行摘要生成与私有工具方法行为。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerImportService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerImportServiceTest {

    /** 客户服务 Mock 桩 */
    @Mock
    private ScrmCustomerService customerService;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmCustomerImportService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerImportService(customerService, customerRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== processRows (private) ====================

    @Test
    @DisplayName("processRows: 空行列表抛 BAD_REQUEST (文件为空或无表头)")
    void processRows_emptyRows_throws() {
        List<String[]> rows = new ArrayList<>();
        assertThatThrownBy(() -> invokeProcessRows(rows, 100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("文件为空或无表头");
    }

    @Test
    @DisplayName("processRows: 表头缺少 platformType 列抛 BAD_REQUEST")
    void processRows_missingPlatformType_throws() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformCustomerUid", "nickname"});
        assertThatThrownBy(() -> invokeProcessRows(rows, 100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("表头缺少必要列");
    }

    @Test
    @DisplayName("processRows: 表头缺少 platformCustomerUid 列抛 BAD_REQUEST")
    void processRows_missingPlatformCustomerUid_throws() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "nickname"});
        assertThatThrownBy(() -> invokeProcessRows(rows, 100L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("表头缺少必要列");
    }

    @Test
    @DisplayName("processRows: 新客户成功导入, 验证 DTO 字段映射与 lifecycle 缺省 NEW")
    void processRows_success() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid", "nickname", "avatarUrl", "lifecycle", "personaId"});
        rows.add(new String[]{"wechat_personal", "uid_001", "张三", "http://avatar.png", "ACTIVE", "p001"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(0);
        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService, times(1)).createCustomer(captor.capture());
        ScrmCustomerDto dto = captor.getValue();
        assertThat(dto.getPlatformType()).isEqualTo("wechat_personal");
        assertThat(dto.getPlatformCustomerUid()).isEqualTo("uid_001");
        assertThat(dto.getNickname()).isEqualTo("张三");
        assertThat(dto.getAvatarUrl()).isEqualTo("http://avatar.png");
        assertThat(dto.getLifecycle()).isEqualTo("ACTIVE");
        assertThat(dto.getPersonaId()).isEqualTo("p001");
        assertThat(dto.getOwnerAccountId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("processRows: lifecycle 为空时默认填充 NEW")
    void processRows_lifecycleDefault() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid", "nickname", "avatarUrl", "lifecycle", "personaId"});
        rows.add(new String[]{"wechat_personal", "uid_002", null, null, null, null});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        invokeProcessRows(rows, 100L);

        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).createCustomer(captor.capture());
        assertThat(captor.getValue().getLifecycle()).isEqualTo("NEW");
    }

    @Test
    @DisplayName("processRows: 平台类型为空计入 failedRows")
    void processRows_blankPlatformType_failed() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"", "uid_003"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(0);
        assertThat(result.getFailedRows()).hasSize(1);
        assertThat(result.getFailedRows().get(0).getRowNumber()).isEqualTo(2);
        assertThat(result.getFailedRows().get(0).getReason()).isEqualTo("平台类型为空");
        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @DisplayName("processRows: 平台客户UID为空计入 failedRows")
    void processRows_blankPlatformCustomerUid_failed() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", ""});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedRows().get(0).getReason()).isEqualTo("平台客户UID为空");
        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @DisplayName("processRows: DB 已存在重复客户计入 skippedCount")
    void processRows_existingInDb_skipped() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_dup"});
        ScrmCustomerEntity existing = new ScrmCustomerEntity();
        existing.setPlatformType("wechat_personal");
        existing.setPlatformCustomerUid("uid_dup");
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(List.of(existing));

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(customerService, never()).createCustomer(any());
    }

    @Test
    @DisplayName("processRows: 本批次已处理客户计入 skippedCount (批内去重)")
    void processRows_duplicateInBatch_skipped() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_batch"});
        rows.add(new String[]{"wechat_personal", "uid_batch"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(customerService, times(1)).createCustomer(any());
    }

    @Test
    @DisplayName("processRows: createCustomer 抛 CONFLICT 异常视为跳过")
    void processRows_conflictException_skipped() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_conflict"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class)))
                .thenThrow(new ScrmException(ScrmExceptionConstants.CONFLICT, "客户已存在"));

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("processRows: createCustomer 抛非 CONFLICT 的 ScrmException 计入失败")
    void processRows_nonConflictScrmException_failed() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_fail"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class)))
                .thenThrow(ScrmException.badRequest("参数错误"));

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedRows().get(0).getReason()).isEqualTo("参数错误");
    }

    @Test
    @DisplayName("processRows: createCustomer 抛 RuntimeException 计入失败")
    void processRows_runtimeException_failed() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_rt"});
        when(customerRepository.findByOwnerAccountId(100L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class)))
                .thenThrow(new RuntimeException("NPE"));

        CustomerImportResultVo result = invokeProcessRows(rows, 100L);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedRows().get(0).getReason()).isEqualTo("NPE");
    }

    @Test
    @DisplayName("processRows: defaultOwnerAccountId 为 null 时不查询已存在客户")
    void processRows_nullOwnerAccountId_noDbQuery() throws Exception {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"platformType", "platformCustomerUid"});
        rows.add(new String[]{"wechat_personal", "uid_null_owner"});
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        CustomerImportResultVo result = invokeProcessRows(rows, null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(customerRepository, never()).findByOwnerAccountId(any());
    }

    // ==================== importFromCsv ====================

    @Test
    @DisplayName("importFromCsv: 真实 CSV 解析并成功导入, 跳过 UTF-8 BOM 头")
    void importFromCsv_success() throws Exception {
        String csv = "\uFEFFplatformType,platformCustomerUid,nickname,avatarUrl,lifecycle,personaId\r\n"
                + "wechat_personal,csv_001,李四,,ACTIVE,p002\r\n";
        when(customerRepository.findByOwnerAccountId(200L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        CustomerImportResultVo result = service.importFromCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), 200L);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).createCustomer(captor.capture());
        assertThat(captor.getValue().getPlatformCustomerUid()).isEqualTo("csv_001");
        assertThat(captor.getValue().getNickname()).isEqualTo("李四");
    }

    @Test
    @DisplayName("importFromCsv: 引号包裹字段含逗号正确解析")
    void importFromCsv_quotedFieldWithComma() throws Exception {
        String csv = "platformType,platformCustomerUid,nickname\r\n"
                + "wechat_personal,csv_002,\"王,五\"\r\n";
        when(customerRepository.findByOwnerAccountId(200L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        service.importFromCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), 200L);

        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).createCustomer(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("王,五");
    }

    @Test
    @DisplayName("importFromCsv: 引号内双引号转义为单个引号")
    void importFromCsv_escapedQuote() throws Exception {
        String csv = "platformType,platformCustomerUid,nickname\r\n"
                + "wechat_personal,csv_003,\"\"\"hello\"\"\r\n";
        when(customerRepository.findByOwnerAccountId(200L)).thenReturn(Collections.emptyList());
        when(customerService.createCustomer(any(ScrmCustomerDto.class))).thenReturn(new ScrmCustomerDto());

        service.importFromCsv(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), 200L);

        ArgumentCaptor<ScrmCustomerDto> captor = ArgumentCaptor.forClass(ScrmCustomerDto.class);
        verify(customerService).createCustomer(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("\"hello\"");
    }

    // ==================== 私有工具方法 ====================

    @Test
    @DisplayName("buildColumnMap: 表头映射去重, 仅保留首次出现的列, 空白列名跳过")
    void buildColumnMap_dedupAndTrim() {
        String[] header = {"platformType", " platformCustomerUid ", "", "platformType"};
        @SuppressWarnings("unchecked")
        Map<String, Integer> map = (Map<String, Integer>) ReflectionTestUtils.invokeMethod(
                service, "buildColumnMap", (Object) header);
        assertThat(map).hasSize(2);
        assertThat(map.get("platformType")).isEqualTo(0);
        assertThat(map.get("platformCustomerUid")).isEqualTo(1);
    }

    @Test
    @DisplayName("normalizeRow: 缺失列填 null, 存在列取 trimmed 值")
    void normalizeRow_missingColumnNull() {
        String[] header = {"platformType", "platformCustomerUid"};
        @SuppressWarnings("unchecked")
        Map<String, Integer> colMap = (Map<String, Integer>) ReflectionTestUtils.invokeMethod(
                service, "buildColumnMap", (Object) header);
        String[] rawRow = {"wechat_personal", "uid_n"};
        String[] normalized = (String[]) ReflectionTestUtils.invokeMethod(
                service, "normalizeRow", rawRow, colMap);
        assertThat(normalized[0]).isEqualTo("wechat_personal");
        assertThat(normalized[1]).isEqualTo("uid_n");
        assertThat(normalized[2]).isNull();
        assertThat(normalized[3]).isNull();
    }

    @Test
    @DisplayName("safeGet: 越界索引返回 null")
    void safeGet_outOfBounds() {
        String[] row = {"a"};
        String result = (String) ReflectionTestUtils.invokeMethod(service, "safeGet", row, 5);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("summarize: 生成 JSON 摘要含 EXPECTED_COLUMNS 字段名")
    void summarize_jsonOutput() {
        String[] row = {"wechat_personal", "uid_s", null, null, null, null};
        String json = (String) ReflectionTestUtils.invokeMethod(service, "summarize", (Object) row);
        assertThat(json).contains("platformType").contains("wechat_personal");
        assertThat(json).contains("platformCustomerUid").contains("uid_s");
    }

    @Test
    @DisplayName("isBlank: null 与纯空白返回 true, 非空返回 false")
    void isBlank_various() {
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isBlank", (String) null)).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isBlank", "   ")).isTrue();
        assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "isBlank", "x")).isFalse();
    }

    @Test
    @DisplayName("parseCsvLine: 简单 CSV 行按逗号分割")
    void parseCsvLine_simple() {
        String[] fields = (String[]) ReflectionTestUtils.invokeMethod(service, "parseCsvLine", "a,b,c");
        assertThat(fields).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("parseCsvLine: 空行返回单元素数组")
    void parseCsvLine_empty() {
        String[] fields = (String[]) ReflectionTestUtils.invokeMethod(service, "parseCsvLine", "");
        assertThat(fields).hasSize(1);
        assertThat(fields[0]).isEmpty();
    }

    // ==================== 辅助方法 ====================

    /**
     * 反射调用 private processRows 方法
     */
    private CustomerImportResultVo invokeProcessRows(List<String[]> rows, Long defaultOwnerAccountId) {
        return (CustomerImportResultVo) ReflectionTestUtils.invokeMethod(
                service, "processRows", rows, defaultOwnerAccountId);
    }
}
