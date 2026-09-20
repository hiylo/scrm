/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerImportService.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.CustomerImportResultVo;
import org.hiylo.scrm.vo.CustomerImportResultVo.FailedRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户批量导入服务。
 * <p>
 * 支持 Excel (xlsx) 与 CSV 两种格式, 解析后统一委托 {@link #processRows} 处理:
 * 表头校验 → 批量去重查询 → 逐行构建 DTO 并调用 {@link ScrmCustomerService#createCustomer}。
 * </p>
 * <p>
 * 事务策略: 单行异常不回滚整体。{@link ScrmCustomerService#createCustomer} 自身声明
 * {@code @Transactional}, 每行调用独立事务, 异常被本服务逐行 try-catch 捕获后记录到
 * failedRows, 不影响其他行的导入。{@link #processRows} 上的 {@code @Transactional}
 * 仅作为写操作元数据声明 (private 方法 Spring AOP 不生效), 实际事务边界由 createCustomer 划定。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerImportService {

    /** 默认生命周期: 新客户 */
    private static final String LIFECYCLE_NEW = "NEW";

    /** 三元组去重 key 分隔符 */
    private static final String KEY_SEPARATOR = "|";

    /** 表头列定义 (顺序与模板一致) */
    private static final List<String> EXPECTED_COLUMNS = List.of(
            "platformType", "platformCustomerUid", "nickname", "avatarUrl", "lifecycle", "personaId");

    /** 必填列: 平台类型 */
    private static final String REQUIRED_COL_PLATFORM_TYPE = "platformType";
    /** 必填列: 平台客户 UID */
    private static final String REQUIRED_COL_PLATFORM_CUSTOMER_UID = "platformCustomerUid";

    /** 客户服务 (复用其创建逻辑与事务边界) */
    private final ScrmCustomerService customerService;

    /** 客户数据访问层 (用于批量去重查询, 避免 N+1) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 序列化器, 用于失败行原始数据摘要 */
    private final ObjectMapper objectMapper;

    /**
     * 从 Excel (xlsx) 流批量导入客户。
     *
     * @param inputStream          xlsx 文件输入流
     * @param defaultOwnerAccountId 默认归属账号 ID (导入客户统一归属)
     * @return 导入结果统计
     */
    public CustomerImportResultVo importFromExcel(InputStream inputStream, Long defaultOwnerAccountId) {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                int lastCol = row.getLastCellNum();
                if (lastCol < 0) {
                    continue;
                }
                String[] cells = new String[lastCol];
                for (int c = 0; c < lastCol; c++) {
                    Cell cell = row.getCell(c);
                    cells[c] = cell == null ? null : formatter.formatCellValue(cell);
                }
                rows.add(cells);
            }
        } catch (IOException e) {
            log.error("Excel 解析失败", e);
            throw ScrmException.badRequest("Excel 解析失败: " + e.getMessage());
        }
        return processRows(rows, defaultOwnerAccountId);
    }

    /**
     * 从 CSV 流批量导入客户。
     * <p>
     * 自动检测并跳过 UTF-8 BOM 头, 支持引号包裹的字段 (含逗号、引号转义)。
     * </p>
     *
     * @param inputStream          CSV 文件输入流 (UTF-8)
     * @param defaultOwnerAccountId 默认归属账号 ID
     * @return 导入结果统计
     */
    public CustomerImportResultVo importFromCsv(InputStream inputStream, Long defaultOwnerAccountId) {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            // 检测并跳过 UTF-8 BOM 头
            reader.mark(1);
            int first = reader.read();
            if (first != -1 && first != '\uFEFF') {
                reader.reset();
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                rows.add(parseCsvLine(line));
            }
        } catch (IOException e) {
            log.error("CSV 解析失败", e);
            throw ScrmException.badRequest("CSV 解析失败: " + e.getMessage());
        }
        return processRows(rows, defaultOwnerAccountId);
    }

    /**
     * 统一处理行数据 (Excel 与 CSV 共用)。
     * <p>
     * 第一行为表头, 校验必填列 (platformType, platformCustomerUid) 后批量查询已存在客户
     * (避免逐行 N+1 查询), 再逐行构建 DTO 调用 {@link ScrmCustomerService#createCustomer}。
     * 重复客户 (三元组重复) 计入跳过数, 校验失败或创建异常计入失败数, 单行异常不中断整体。
     * </p>
     *
     * @param rows                 行数据 (首行为表头)
     * @param defaultOwnerAccountId 默认归属账号 ID
     * @return 导入结果统计
     */
    @Transactional
    private CustomerImportResultVo processRows(List<String[]> rows, Long defaultOwnerAccountId) {
        if (rows.isEmpty()) {
            throw ScrmException.badRequest("文件为空或无表头");
        }
        // 构建表头列索引映射
        String[] header = rows.get(0);
        Map<String, Integer> colMap = buildColumnMap(header);
        if (!colMap.containsKey(REQUIRED_COL_PLATFORM_TYPE)
                || !colMap.containsKey(REQUIRED_COL_PLATFORM_CUSTOMER_UID)) {
            throw ScrmException.badRequest("表头缺少必要列: platformType, platformCustomerUid");
        }
        // 批量查询已存在客户 (按 ownerAccountId), 构建 (platformType|platformCustomerUid) 集合
        Set<String> existingKeys = loadExistingKeys(defaultOwnerAccountId);
        Set<String> processedKeys = new HashSet<>();
        List<FailedRow> failedRows = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int totalRows = rows.size() - 1;
        // 遍历数据行 (从第 2 行, 即索引 1 开始)
        for (int i = 1; i < rows.size(); i++) {
            String[] rawRow = rows.get(i);
            int rowNumber = i + 1;
            String[] normalized = normalizeRow(rawRow, colMap);
            String platformType = normalized[0];
            String platformCustomerUid = normalized[1];
            // 必填字段校验
            if (isBlank(platformType)) {
                failedRows.add(FailedRow.builder()
                        .rowNumber(rowNumber)
                        .reason("平台类型为空")
                        .rowData(summarize(normalized))
                        .build());
                failedCount++;
                continue;
            }
            if (isBlank(platformCustomerUid)) {
                failedRows.add(FailedRow.builder()
                        .rowNumber(rowNumber)
                        .reason("平台客户UID为空")
                        .rowData(summarize(normalized))
                        .build());
                failedCount++;
                continue;
            }
            // 重复客户识别 (DB 已存在或本批次已处理)
            String key = platformType + KEY_SEPARATOR + platformCustomerUid;
            if (existingKeys.contains(key) || processedKeys.contains(key)) {
                skippedCount++;
                log.info("跳过重复客户: row={}, platformType={}, platformCustomerUid={}",
                        rowNumber, platformType, platformCustomerUid);
                continue;
            }
            // 构建 DTO 并创建客户, 单行异常不中断
            try {
                ScrmCustomerDto dto = buildDto(normalized, defaultOwnerAccountId);
                customerService.createCustomer(dto);
                processedKeys.add(key);
                successCount++;
            } catch (ScrmException e) {
                if (ScrmExceptionConstants.CONFLICT.equals(e.getCode())) {
                    // 并发场景下重复创建, 视为跳过
                    skippedCount++;
                    existingKeys.add(key);
                    log.info("客户已存在, 跳过: row={}, platformType={}, platformCustomerUid={}",
                            rowNumber, platformType, platformCustomerUid);
                } else {
                    failedRows.add(FailedRow.builder()
                            .rowNumber(rowNumber)
                            .reason(e.getMessage())
                            .rowData(summarize(normalized))
                            .build());
                    failedCount++;
                    log.warn("导入客户失败: row={}, error={}", rowNumber, e.getMessage());
                }
            } catch (Exception e) {
                failedRows.add(FailedRow.builder()
                        .rowNumber(rowNumber)
                        .reason(e.getMessage())
                        .rowData(summarize(normalized))
                        .build());
                failedCount++;
                log.warn("导入客户异常: row={}", rowNumber, e);
            }
        }
        log.info("客户导入完成: total={}, success={}, failed={}, skipped={}",
                totalRows, successCount, failedCount, skippedCount);
        return CustomerImportResultVo.builder()
                .totalCount(totalRows)
                .successCount(successCount)
                .failedCount(failedCount)
                .skippedCount(skippedCount)
                .failedRows(failedRows)
                .build();
    }

    /**
     * 批量加载指定归属账号下已存在客户的去重 key 集合。
     *
     * @param ownerAccountId 归属账号 ID (null 或 <=0 返回空集合)
     * @return (platformType|platformCustomerUid) 字符串集合
     */
    private Set<String> loadExistingKeys(Long ownerAccountId) {
        if (ownerAccountId == null || ownerAccountId <= 0) {
            return new HashSet<>();
        }
        return customerRepository.findByOwnerAccountId(ownerAccountId).stream()
                .map(e -> e.getPlatformType() + KEY_SEPARATOR + e.getPlatformCustomerUid())
                .collect(Collectors.toSet());
    }

    /**
     * 构建表头列名到列索引的映射 (去重, 仅保留首次出现的列)。
     *
     * @param header 表头行
     * @return 列名 → 列索引
     */
    private Map<String, Integer> buildColumnMap(String[] header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String name = header[i] == null ? "" : header[i].trim();
            if (!name.isEmpty() && !map.containsKey(name)) {
                map.put(name, i);
            }
        }
        return map;
    }

    /**
     * 将原始行按 EXPECTED_COLUMNS 顺序归一化, 缺失列填 null。
     *
     * @param rawRow 原始行
     * @param colMap 列索引映射
     * @return 归一化后的行 (顺序与 EXPECTED_COLUMNS 一致)
     */
    private String[] normalizeRow(String[] rawRow, Map<String, Integer> colMap) {
        String[] normalized = new String[EXPECTED_COLUMNS.size()];
        for (int i = 0; i < EXPECTED_COLUMNS.size(); i++) {
            Integer idx = colMap.get(EXPECTED_COLUMNS.get(i));
            normalized[i] = idx != null ? safeGet(rawRow, idx) : null;
        }
        return normalized;
    }

    /**
     * 由归一化行构建客户 DTO。
     * <p>
     * 字段顺序: platformType, platformCustomerUid, nickname, avatarUrl, lifecycle, personaId。
     * lifecycle 为空时默认 NEW; ownerAccountId 统一使用 defaultOwnerAccountId。
     * </p>
     *
     * @param row                  归一化行
     * @param defaultOwnerAccountId 默认归属账号 ID
     * @return 客户 DTO
     */
    private ScrmCustomerDto buildDto(String[] row, Long defaultOwnerAccountId) {
        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setPlatformType(safeGet(row, 0));
        dto.setPlatformCustomerUid(safeGet(row, 1));
        dto.setNickname(safeGet(row, 2));
        dto.setAvatarUrl(safeGet(row, 3));
        String lifecycle = safeGet(row, 4);
        dto.setLifecycle(isBlank(lifecycle) ? LIFECYCLE_NEW : lifecycle);
        dto.setPersonaId(safeGet(row, 5));
        dto.setOwnerAccountId(defaultOwnerAccountId);
        return dto;
    }

    /**
     * 安全取值, 越界或 null 返回 null。
     *
     * @param row   行数据
     * @param index 列索引
     * @return 去除首尾空白后的值, 越界返回 null
     */
    private String safeGet(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return null;
        }
        String value = row[index];
        return value == null ? null : value.trim();
    }

    /**
     * 生成失败行原始数据摘要 (JSON 字符串), 序列化失败回退为 | 分隔。
     *
     * @param row 归一化行
     * @return 摘要字符串
     */
    private String summarize(String[] row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < EXPECTED_COLUMNS.size() && i < row.length; i++) {
            map.put(EXPECTED_COLUMNS.get(i), row[i]);
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return String.join(KEY_SEPARATOR, row);
        }
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param s 字符串
     * @return true 表示 null 或纯空白
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 解析单行 CSV (支持引号包裹字段、逗号转义与引号转义)。
     *
     * @param line 单行文本 (不含换行符)
     * @return 字段数组
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    // 双引号转义为单个引号
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i += 2;
                        continue;
                    }
                    // 引号闭合
                    inQuotes = false;
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    fields.add(sb.toString());
                    sb.setLength(0);
                    i++;
                } else {
                    sb.append(c);
                    i++;
                }
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
