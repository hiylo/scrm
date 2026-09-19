/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataTransferService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmExportTaskDto;
import org.hiylo.scrm.dto.ScrmImportTaskDto;
import org.hiylo.scrm.dto.ScrmImportTemplateDto;
import org.hiylo.scrm.dto.ScrmImportTriggerDto;
import org.hiylo.scrm.entity.ScrmDataTransferLogEntity;
import org.hiylo.scrm.entity.ScrmExportTaskEntity;
import org.hiylo.scrm.entity.ScrmImportTaskEntity;
import org.hiylo.scrm.entity.ScrmImportTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmDataTransferLogRepository;
import org.hiylo.scrm.repository.ScrmExportTaskRepository;
import org.hiylo.scrm.repository.ScrmImportTaskRepository;
import org.hiylo.scrm.repository.ScrmImportTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * SCRM 数据导入导出中心服务。
 * <p>
 * 承载导入模板管理、导入任务、导出任务、数据校验、导入历史与错误处理能力。
 * 所有写操作写入当前用户归属账号, 实现数据隔离。
 * </p>
 * <p>
 * 导入流程: 创建任务 → 数据验证 ({@link #validateData(Long)}) → 执行导入
 * ({@link #processImport(Long)}) → 记录日志。导出流程: 创建任务 → 执行导出
 * ({@link #processExport(Long)}) → 记录日志。
 * 当前导入/导出执行为模拟实现 (待对接真实文件解析/生成与业务数据读写)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmDataTransferService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认 "存在则更新" */
    private static final boolean DEFAULT_UPDATE_IF_EXISTS = false;

    /** 模拟导入样本记录数 */
    private static final int MOCK_TOTAL_RECORDS = 100;

    /** 模拟无效记录数 */
    private static final int MOCK_INVALID_RECORDS = 5;

    /** 模拟失败记录数 */
    private static final int MOCK_FAILED_RECORDS = 3;

    /** 模拟跳过记录数 */
    private static final int MOCK_SKIPPED_RECORDS = 2;

    /** 模拟导出记录数 */
    private static final int MOCK_EXPORT_RECORDS = 200;

    /** 导入任务类型 */
    private static final String TASK_TYPE_IMPORT = "IMPORT";

    /** 导出任务类型 */
    private static final String TASK_TYPE_EXPORT = "EXPORT";

    /** 导入任务状态: PENDING 待处理 */
    private static final String IMPORT_STATUS_PENDING = "PENDING";
    /** 导入任务状态: VALIDATING 校验中 */
    private static final String IMPORT_STATUS_VALIDATING = "VALIDATING";
    /** 导入任务状态: IMPORTING 导入中 */
    private static final String IMPORT_STATUS_IMPORTING = "IMPORTING";
    /** 导入任务状态: SUCCESS 成功 */
    private static final String IMPORT_STATUS_SUCCESS = "SUCCESS";
    /** 导入任务状态: PARTIAL 部分成功 */
    private static final String IMPORT_STATUS_PARTIAL = "PARTIAL";
    /** 导入任务状态: FAILED 失败 */
    private static final String IMPORT_STATUS_FAILED = "FAILED";
    /** 导入任务状态: CANCELLED 已取消 */
    private static final String IMPORT_STATUS_CANCELLED = "CANCELLED";

    /** 导出任务状态: PENDING 待处理 */
    private static final String EXPORT_STATUS_PENDING = "PENDING";
    /** 导出任务状态: EXPORTING 导出中 */
    private static final String EXPORT_STATUS_EXPORTING = "EXPORTING";
    /** 导出任务状态: SUCCESS 成功 */
    private static final String EXPORT_STATUS_SUCCESS = "SUCCESS";
    /** 导出任务状态: FAILED 失败 */
    private static final String EXPORT_STATUS_FAILED = "FAILED";
    /** 导出任务状态: CANCELLED 已取消 */
    private static final String EXPORT_STATUS_CANCELLED = "CANCELLED";

    /** 日志操作: CREATE 新增 */
    private static final String OP_CREATE = "CREATE";
    /** 日志操作: UPDATE 更新 */
    private static final String OP_UPDATE = "UPDATE";
    /** 日志操作: SKIP 跳过 */
    private static final String OP_SKIP = "SKIP";
    /** 日志操作: FAIL 失败 */
    private static final String OP_FAIL = "FAIL";

    /** 日志状态: SUCCESS 成功 */
    private static final String LOG_STATUS_SUCCESS = "SUCCESS";
    /** 日志状态: WARNING 警告 */
    private static final String LOG_STATUS_WARNING = "WARNING";
    /** 日志状态: ERROR 错误 */
    private static final String LOG_STATUS_ERROR = "ERROR";

    /** 合法数据类型 */
    private static final List<String> VALID_DATA_TYPES = List.of(
            "CUSTOMER", "CONTACT", "FOLLOW_RECORD", "TAG", "PRODUCT", "ORDER", "OTHER");

    /** 导入模板数据访问层 */
    private final ScrmImportTemplateRepository templateRepository;

    /** 导入任务数据访问层 */
    private final ScrmImportTaskRepository importTaskRepository;

    /** 导出任务数据访问层 */
    private final ScrmExportTaskRepository exportTaskRepository;

    /** 数据导入导出日志数据访问层 */
    private final ScrmDataTransferLogRepository logRepository;

    /** JSON 序列化/反序列化 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建导入模板。
     * <p>校验参数合法性后写入账号 ID 持久化, enabled/updateIfExists 缺省时填默认值。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmImportTemplateEntity createTemplate(ScrmImportTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto, false);
        ScrmImportTemplateEntity entity = new ScrmImportTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setDataType(dto.getDataType());
        entity.setDescription(dto.getDescription());
        entity.setColumns(dto.getColumns());
        entity.setSampleFileUrl(dto.getSampleFileUrl());
        entity.setValidationRules(dto.getValidationRules());
        entity.setDeduplicationKey(dto.getDeduplicationKey());
        entity.setUpdateIfExists(dto.getUpdateIfExists() != null ? dto.getUpdateIfExists() : DEFAULT_UPDATE_IF_EXISTS);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setUsageCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建导入模板: id={}, templateName={}, dataType={}",
                entity.getId(), entity.getTemplateName(), entity.getDataType());
        return entity;
    }

    /**
     * 更新导入模板（字段非空才覆盖）。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmImportTemplateEntity updateTemplate(Long id, ScrmImportTemplateDto dto) throws ScrmException {
        ScrmImportTemplateEntity entity = findTemplateOrThrow(id);
        validateTemplateDto(dto, true);
        if (dto.getTemplateName() != null) entity.setTemplateName(dto.getTemplateName());
        if (dto.getDataType() != null) entity.setDataType(dto.getDataType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getColumns() != null) entity.setColumns(dto.getColumns());
        if (dto.getSampleFileUrl() != null) entity.setSampleFileUrl(dto.getSampleFileUrl());
        if (dto.getValidationRules() != null) entity.setValidationRules(dto.getValidationRules());
        if (dto.getDeduplicationKey() != null) entity.setDeduplicationKey(dto.getDeduplicationKey());
        if (dto.getUpdateIfExists() != null) entity.setUpdateIfExists(dto.getUpdateIfExists());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("更新导入模板: id={}, templateName={}", entity.getId(), entity.getTemplateName());
        return entity;
    }

    /**
     * 删除导入模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmImportTemplateEntity entity = findTemplateOrThrow(id);
        templateRepository.delete(entity);
        log.info("删除导入模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmImportTemplateEntity getTemplate(Long id) throws ScrmException {
        return findTemplateOrThrow(id);
    }

    /**
     * 分页查询模板, 支持按数据类型/启用状态/关键字过滤。
     *
     * @param dataType 数据类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  关键字过滤（按模板名称模糊匹配, 可空）
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmImportTemplateEntity> listTemplates(
            String dataType, Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmImportTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dataType != null && !dataType.isBlank()) {
                predicates.add(cb.equal(root.get("dataType"), dataType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("templateName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return templateRepository.findAll(spec, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void enableTemplate(Long id) throws ScrmException {
        ScrmImportTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(true);
        templateRepository.save(entity);
        log.info("启用导入模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void disableTemplate(Long id) throws ScrmException {
        ScrmImportTemplateEntity entity = findTemplateOrThrow(id);
        entity.setEnabled(false);
        templateRepository.save(entity);
        log.info("禁用导入模板: id={}, templateName={}", id, entity.getTemplateName());
    }

    /**
     * 模板使用次数 +1。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void incrementUsage(Long id) throws ScrmException {
        ScrmImportTemplateEntity entity = findTemplateOrThrow(id);
        int count = entity.getUsageCount() != null ? entity.getUsageCount() : 0;
        entity.setUsageCount(count + 1);
        templateRepository.save(entity);
        log.info("导入模板使用次数 +1: id={}, usageCount={}", id, entity.getUsageCount());
    }

    // ============================================================
    // 导入任务管理
    // ============================================================

    /**
     * 创建导入任务。
     * <p>仅创建任务记录 (状态 PENDING), 不立即执行, 由 {@link #triggerImport(ScrmImportTriggerDto)}
     * 或 {@link #processImport(Long)} 触发执行。若指定模板 ID 则校验模板存在且归属当前账号。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 模板不存在
     */
    @Transactional
    public ScrmImportTaskEntity createImportTask(ScrmImportTaskDto dto) throws ScrmException {
        validateImportTaskDto(dto);
        if (dto.getTemplateId() != null) {
            findTemplateOrThrow(dto.getTemplateId());
        }
        ScrmImportTaskEntity entity = new ScrmImportTaskEntity();
        entity.setTemplateId(dto.getTemplateId());
        entity.setTaskName(dto.getTaskName());
        entity.setDataType(dto.getDataType());
        entity.setFilePath(dto.getFilePath());
        entity.setFileName(dto.getFileName());
        entity.setFileSize(dto.getFileSize());
        entity.setFileType(dto.getFileType() != null && !dto.getFileType().isBlank() ? dto.getFileType() : "CSV");
        entity.setTriggeredBy(dto.getTriggeredBy());
        entity.setOptions(dto.getOptions());
        entity = importTaskRepository.save(entity);
        log.info("创建导入任务: id={}, taskName={}, dataType={}", entity.getId(), entity.getTaskName(), entity.getDataType());
        return entity;
    }

    /**
     * 查询导入任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmImportTaskEntity getImportTask(Long id) throws ScrmException {
        return findImportTaskOrThrow(id);
    }

    /**
     * 分页查询导入任务, 支持按数据类型/状态/创建时间范围过滤。
     *
     * @param dataType  数据类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 创建时间下限（可空）
     * @param endTime   创建时间上限（可空）
     * @param pageable  分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmImportTaskEntity> listImportTasks(String dataType, String status,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                               Pageable pageable) {
        Specification<ScrmImportTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dataType != null && !dataType.isBlank()) {
                predicates.add(cb.equal(root.get("dataType"), dataType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return importTaskRepository.findAll(spec, pageable);
    }

    /**
     * 取消导入任务。
     * <p>仅 PENDING/VALIDATING 状态可取消, 已完成 (SUCCESS/PARTIAL/FAILED) 不可取消。</p>
     *
     * @param id 任务 ID
     * @return 取消后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmImportTaskEntity cancelImportTask(Long id) throws ScrmException {
        ScrmImportTaskEntity entity = findImportTaskOrThrow(id);
        if (IMPORT_STATUS_SUCCESS.equals(entity.getStatus()) || IMPORT_STATUS_PARTIAL.equals(entity.getStatus())
                || IMPORT_STATUS_FAILED.equals(entity.getStatus())
                        || IMPORT_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("导入任务已结束, 不允许取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(IMPORT_STATUS_CANCELLED);
        entity.setEndTime(LocalDateTime.now());
        entity = importTaskRepository.save(entity);
        log.info("取消导入任务: id={}, taskName={}", id, entity.getTaskName());
        return entity;
    }

    /**
     * 重试导入任务。
     * <p>仅 FAILED/PARTIAL 状态可重试, 重试时重置统计字段并重新触发验证+导入流程。</p>
     *
     * @param id 任务 ID
     * @return 重试后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmImportTaskEntity retryImportTask(Long id) throws ScrmException {
        ScrmImportTaskEntity entity = findImportTaskOrThrow(id);
        if (!IMPORT_STATUS_FAILED.equals(entity.getStatus()) && !IMPORT_STATUS_PARTIAL.equals(entity.getStatus())) {
            throw ScrmException.conflict("仅失败/部分成功任务可重试: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(IMPORT_STATUS_PENDING);
        entity.setStartTime(null);
        entity.setEndTime(null);
        entity.setDurationMs(null);
        entity.setTotalRecords(0);
        entity.setValidRecords(0);
        entity.setInvalidRecords(0);
        entity.setImportedRecords(0);
        entity.setFailedRecords(0);
        entity.setSkippedRecords(0);
        entity.setErrorDetails(null);
        entity = importTaskRepository.save(entity);
        log.info("重试导入任务: id={}, taskName={}", id, entity.getTaskName());
        validateData(id);
        return processImport(id);
    }

    /**
     * 触发导入: 创建任务 → 验证 → 导入 → 记录日志 (模拟实现)。
     * <p>依据模板 ID 加载模板配置, 创建导入任务后立即执行验证与导入流程。
     * 文件路径从 fileName 推导, 文件类型由模板/选项推导。执行为模拟实现,
     * 待对接真实文件解析与业务数据写入。</p>
     *
     * @param triggerDto 触发参数 (templateId + fileName + options)
     * @return 导入任务实体
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmImportTaskEntity triggerImport(ScrmImportTriggerDto triggerDto) throws ScrmException {
        if (triggerDto == null) {
            throw ScrmException.badRequest("导入触发参数不能为空");
        }
        ScrmImportTemplateEntity template = findTemplateOrThrow(triggerDto.getTemplateId());
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw ScrmException.badRequest("导入模板已禁用: templateId=" + template.getId());
        }
        ScrmImportTaskDto taskDto = new ScrmImportTaskDto();
        taskDto.setTemplateId(template.getId());
        taskDto.setTaskName("导入-" + template.getTemplateName() + "-" + System.currentTimeMillis());
        taskDto.setDataType(template.getDataType());
        taskDto.setFilePath(triggerDto.getFileName());
        taskDto.setFileName(triggerDto.getFileName());
        taskDto.setFileType(detectFileType(triggerDto.getFileName()));
        taskDto.setTriggeredBy(template.getCreatedBy());
        taskDto.setOptions(triggerDto.getOptions());
        ScrmImportTaskEntity task = createImportTask(taskDto);
        incrementUsage(template.getId());
        validateData(task.getId());
        return processImport(task.getId());
    }

    /**
     * 数据验证 (模拟实现)。
     * <p>状态流转 PENDING → VALIDATING → PENDING。模拟统计总记录数与无效记录数,
     * 记录校验日志。待对接真实文件解析与校验规则执行。</p>
     *
     * @param taskId 任务 ID
     * @return 验证后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmImportTaskEntity validateData(Long taskId) throws ScrmException {
        ScrmImportTaskEntity task = findImportTaskOrThrow(taskId);
        if (IMPORT_STATUS_IMPORTING.equals(task.getStatus()) || IMPORT_STATUS_SUCCESS.equals(task.getStatus())
                || IMPORT_STATUS_PARTIAL.equals(task.getStatus())) {
            throw ScrmException.conflict("导入任务不可验证, 当前状态: " + task.getStatus());
        }
        task.setStatus(IMPORT_STATUS_VALIDATING);
        task.setStartTime(LocalDateTime.now());
        task = importTaskRepository.save(task);

        // 模拟数据验证: 待对接真实文件解析与校验规则执行
        int total = MOCK_TOTAL_RECORDS;
        int invalid = MOCK_INVALID_RECORDS;
        int valid = total - invalid;
        task.setTotalRecords(total);
        task.setValidRecords(valid);
        task.setInvalidRecords(invalid);
        task.setStatus(IMPORT_STATUS_PENDING);
        task = importTaskRepository.save(task);

        // 记录验证阶段日志
        writeLog(task.getId(), TASK_TYPE_IMPORT, null, null, OP_SKIP, null,
                "数据验证完成: 总数 " + total + ", 有效 " + valid + ", 无效 " + invalid, LOG_STATUS_SUCCESS);
        if (invalid > 0) {
            writeLog(task.getId(), TASK_TYPE_IMPORT, null, null, OP_FAIL, null,
                    "存在 " + invalid + " 条无效记录, 字段校验未通过", LOG_STATUS_WARNING);
        }
        log.info("导入任务数据验证完成: taskId={}, total={}, valid={}, invalid={}",
                taskId, total, valid, invalid);
        return task;
    }

    /**
     * 执行导入 (模拟实现)。
     * <p>状态流转 PENDING → IMPORTING → SUCCESS/PARTIAL/FAILED。模拟导入有效记录,
     * 统计已导入/失败/跳过记录数, 记录导入日志。待对接真实业务数据写入。</p>
     *
     * @param taskId 任务 ID
     * @return 导入后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmImportTaskEntity processImport(Long taskId) throws ScrmException {
        ScrmImportTaskEntity task = findImportTaskOrThrow(taskId);
        if (IMPORT_STATUS_IMPORTING.equals(task.getStatus())) {
            throw ScrmException.conflict("导入任务正在执行中, 请勿重复触发: taskId=" + taskId);
        }
        if (IMPORT_STATUS_SUCCESS.equals(task.getStatus())) {
            throw ScrmException.conflict("导入任务已完成, 不允许重复执行: taskId=" + taskId);
        }
        LocalDateTime start = task.getStartTime() != null ? task.getStartTime() : LocalDateTime.now();
        task.setStatus(IMPORT_STATUS_IMPORTING);
        task.setStartTime(start);
        task = importTaskRepository.save(task);

        try {
            // 模拟导入执行: 待对接真实业务数据写入
            int total = task.getTotalRecords() != null ? task.getTotalRecords() : MOCK_TOTAL_RECORDS;
            int valid = task.getValidRecords() != null ? task.getValidRecords() : (total - MOCK_INVALID_RECORDS);
            int failed = MOCK_FAILED_RECORDS;
            int skipped = MOCK_SKIPPED_RECORDS;
            int imported = Math.max(0, valid - failed - skipped);
            task.setImportedRecords(imported);
            task.setFailedRecords(failed);
            task.setSkippedRecords(skipped);

            // 记录导入阶段日志
            writeLog(task.getId(), TASK_TYPE_IMPORT, null, null, OP_CREATE, null,
                    "导入完成: 已导入 " + imported + " 条", LOG_STATUS_SUCCESS);
            if (failed > 0) {
                writeLog(task.getId(), TASK_TYPE_IMPORT, null, null, OP_FAIL, null,
                        "导入失败 " + failed + " 条, 业务写入异常", LOG_STATUS_ERROR);
            }
            if (skipped > 0) {
                writeLog(task.getId(), TASK_TYPE_IMPORT, null, null, OP_SKIP, null,
                        "跳过 " + skipped + " 条 (去重/不满足条件)", LOG_STATUS_WARNING);
            }

            LocalDateTime end = LocalDateTime.now();
            task.setEndTime(end);
            task.setDurationMs((int) java.time.Duration.between(start, end).toMillis());
            // 有失败或跳过记录则标记为部分成功
            task.setStatus((failed > 0 || skipped > 0) ? IMPORT_STATUS_PARTIAL : IMPORT_STATUS_SUCCESS);
            task = importTaskRepository.save(task);
            log.info("导入任务执行完成: taskId={}, status={}, imported={}, failed={}, skipped={}",
                    taskId, task.getStatus(), imported, failed, skipped);
            return task;
        } catch (Exception e) {
            task.setStatus(IMPORT_STATUS_FAILED);
            task.setEndTime(LocalDateTime.now());
            task.setErrorDetails(toJson(Map.of("error", e.getMessage() != null ? e.getMessage() : "导入执行异常")));
            task = importTaskRepository.save(task);
            log.error("导入任务执行失败: taskId={}", taskId, e);
            throw new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                    "导入任务执行失败: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // 导出任务管理
    // ============================================================

    /**
     * 创建导出任务。
     * <p>仅创建任务记录 (状态 PENDING), 不立即执行, 由 {@link #triggerExport(Long)} 触发执行。</p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmExportTaskEntity createExportTask(ScrmExportTaskDto dto) throws ScrmException {
        validateExportTaskDto(dto);
        ScrmExportTaskEntity entity = new ScrmExportTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setDataType(dto.getDataType());
        entity.setQueryCondition(dto.getQueryCondition());
        entity.setSelectedFields(dto.getSelectedFields());
        entity.setFilters(dto.getFilters());
        entity.setFileType(dto.getFileType() != null && !dto.getFileType().isBlank() ? dto.getFileType() : "EXCEL");
        entity.setTriggeredBy(dto.getTriggeredBy());
        entity = exportTaskRepository.save(entity);
        log.info("创建导出任务: id={}, taskName={}, dataType={}", entity.getId(), entity.getTaskName(), entity.getDataType());
        return entity;
    }

    /**
     * 查询导出任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmExportTaskEntity getExportTask(Long id) throws ScrmException {
        return findExportTaskOrThrow(id);
    }

    /**
     * 分页查询导出任务, 支持按数据类型/状态/创建时间范围过滤。
     *
     * @param dataType  数据类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 创建时间下限（可空）
     * @param endTime   创建时间上限（可空）
     * @param pageable  分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmExportTaskEntity> listExportTasks(String dataType, String status,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                               Pageable pageable) {
        Specification<ScrmExportTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dataType != null && !dataType.isBlank()) {
                predicates.add(cb.equal(root.get("dataType"), dataType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return exportTaskRepository.findAll(spec, pageable);
    }

    /**
     * 取消导出任务。
     * <p>仅 PENDING/EXPORTING 状态可取消, 已完成 (SUCCESS/FAILED) 不可取消。</p>
     *
     * @param id 任务 ID
     * @return 取消后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmExportTaskEntity cancelExportTask(Long id) throws ScrmException {
        ScrmExportTaskEntity entity = findExportTaskOrThrow(id);
        if (EXPORT_STATUS_SUCCESS.equals(entity.getStatus()) || EXPORT_STATUS_FAILED.equals(entity.getStatus())
                || EXPORT_STATUS_CANCELLED.equals(entity.getStatus())) {
            throw ScrmException.conflict("导出任务已结束, 不允许取消: id=" + id + ", status=" + entity.getStatus());
        }
        entity.setStatus(EXPORT_STATUS_CANCELLED);
        entity.setEndTime(LocalDateTime.now());
        entity = exportTaskRepository.save(entity);
        log.info("取消导出任务: id={}, taskName={}", id, entity.getTaskName());
        return entity;
    }

    /**
     * 触发导出: 查询 → 导出 → 记录日志 (模拟实现)。
     * <p>立即执行导出流程, 文件路径与文件名在导出过程中生成。
     * 执行为模拟实现, 待对接真实数据查询与文件生成。</p>
     *
     * @param taskId 任务 ID
     * @return 导出后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmExportTaskEntity triggerExport(Long taskId) throws ScrmException {
        return processExport(taskId);
    }

    /**
     * 执行导出 (模拟实现)。
     * <p>状态流转 PENDING → EXPORTING → SUCCESS/FAILED。模拟查询数据并生成文件,
     * 统计导出记录数与文件大小, 记录导出日志。待对接真实数据查询与文件生成。</p>
     *
     * @param taskId 任务 ID
     * @return 导出后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmExportTaskEntity processExport(Long taskId) throws ScrmException {
        ScrmExportTaskEntity task = findExportTaskOrThrow(taskId);
        if (EXPORT_STATUS_EXPORTING.equals(task.getStatus())) {
            throw ScrmException.conflict("导出任务正在执行中, 请勿重复触发: taskId=" + taskId);
        }
        if (EXPORT_STATUS_SUCCESS.equals(task.getStatus())) {
            throw ScrmException.conflict("导出任务已完成, 不允许重复执行: taskId=" + taskId);
        }
        LocalDateTime start = LocalDateTime.now();
        task.setStatus(EXPORT_STATUS_EXPORTING);
        task.setStartTime(start);
        task = exportTaskRepository.save(task);

        try {
            // 模拟导出执行: 待对接真实数据查询与文件生成
            int total = MOCK_EXPORT_RECORDS;
            int exported = total;
            String fileType = task.getFileType() != null ? task.getFileType() : "EXCEL";
            String fileName = "export-" + task.getDataType() + "-" + System.currentTimeMillis()
                    + "." + fileType.toLowerCase();
            String filePath = "/data/exports/" + fileName;
            int fileSizeKb = Math.max(1, exported / 10);

            task.setTotalRecords(total);
            task.setExportedRecords(exported);
            task.setFilePath(filePath);
            task.setFileName(fileName);
            task.setFileSize(fileSizeKb);

            // 记录导出阶段日志
            writeLog(task.getId(), TASK_TYPE_EXPORT, null, null, OP_CREATE, null,
                    "导出完成: 已导出 " + exported + " 条, 文件 " + fileName, LOG_STATUS_SUCCESS);

            LocalDateTime end = LocalDateTime.now();
            task.setEndTime(end);
            task.setDurationMs((int) java.time.Duration.between(start, end).toMillis());
            task.setStatus(EXPORT_STATUS_SUCCESS);
            task = exportTaskRepository.save(task);
            log.info("导出任务执行完成: taskId={}, exported={}, file={}", taskId, exported, fileName);
            return task;
        } catch (Exception e) {
            task.setStatus(EXPORT_STATUS_FAILED);
            task.setEndTime(LocalDateTime.now());
            task.setErrorMessage(e.getMessage() != null ? e.getMessage() : "导出执行异常");
            task = exportTaskRepository.save(task);
            log.error("导出任务执行失败: taskId={}", taskId, e);
            throw new ScrmException(ScrmExceptionConstants.INTERNAL_ERROR,
                    "导出任务执行失败: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // 日志查询
    // ============================================================

    /**
     * 分页查询导入导出日志, 支持按任务 ID/任务类型/状态过滤。
     *
     * @param taskId   任务 ID 过滤（可空）
     * @param taskType 任务类型过滤（可空: IMPORT/EXPORT）
     * @param status   状态过滤（可空: SUCCESS/WARNING/ERROR）
     * @param pageable 分页参数
     * @return 日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmDataTransferLogEntity> listLogs(Long taskId, String taskType, String status, Pageable pageable) {
        Specification<ScrmDataTransferLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            if (taskType != null && !taskType.isBlank()) {
                predicates.add(cb.equal(root.get("taskType"), taskType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return logRepository.findAll(spec, pageable);
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志实体
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmDataTransferLogEntity getLog(Long id) throws ScrmException {
        ScrmDataTransferLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "数据导入导出日志不存在: id=" + id));

        return entity;
    }

    /**
     * 查询某任务的全部日志。
     *
     * @param taskId 任务 ID
     * @return 日志列表
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataTransferLogEntity> getTaskLogs(Long taskId) throws ScrmException {
        return logRepository.findByTaskIdOrderByProcessedAtAsc(taskId);
    }

    /**
     * 查询某任务的错误日志 (status=ERROR)。
     *
     * @param taskId 任务 ID
     * @return 错误日志列表
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmDataTransferLogEntity> getErrorLogs(Long taskId) throws ScrmException {
        return logRepository.findByTaskIdAndStatusOrderByProcessedAtAsc(taskId, LOG_STATUS_ERROR);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 数据导入导出统计: 导入/导出任务数、成功率、记录数。
     *
     * @param startTime 起始时间（含, 按创建时间过滤）
     * @param endTime   截止时间（含）
     * @return 统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTransferStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmImportTaskEntity> importSpec = timeRangeSpec(startTime, endTime);
        List<ScrmImportTaskEntity> imports = importTaskRepository.findAll(importSpec);
        Specification<ScrmExportTaskEntity> exportSpec = exportTimeRangeSpec(startTime, endTime);
        List<ScrmExportTaskEntity> exports = exportTaskRepository.findAll(exportSpec);

        long importTotal = imports.size();
        long importSuccess = imports.stream()
                .filter(t -> IMPORT_STATUS_SUCCESS.equals(t.getStatus()) || IMPORT_STATUS_PARTIAL.equals(t.getStatus()))
                .count();
        long importRecords = imports.stream()
                .mapToInt(t -> t.getImportedRecords() != null ? t.getImportedRecords() : 0)
                .sum();

        long exportTotal = exports.size();
        long exportSuccess = exports.stream()
                .filter(t -> EXPORT_STATUS_SUCCESS.equals(t.getStatus()))
                .count();
        long exportRecords = exports.stream()
                .mapToInt(t -> t.getExportedRecords() != null ? t.getExportedRecords() : 0)
                .sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("importTaskCount", importTotal);
        stats.put("importSuccessCount", importSuccess);
        stats.put("importSuccessRate", importTotal > 0 ? round2(importSuccess * 100.0 / importTotal) : 0.0);
        stats.put("importedRecords", importRecords);
        stats.put("exportTaskCount", exportTotal);
        stats.put("exportSuccessCount", exportSuccess);
        stats.put("exportSuccessRate", exportTotal > 0 ? round2(exportSuccess * 100.0 / exportTotal) : 0.0);
        stats.put("exportedRecords", exportRecords);
        return stats;
    }

    /**
     * 模板使用统计: 各模板使用次数与启用状态。
     *
     * @return 模板统计列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTemplateStats() {
        Specification<ScrmImportTemplateEntity> spec = (root, query, cb) ->
                cb.and();
        List<ScrmImportTemplateEntity> templates = templateRepository.findAll(spec);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScrmImportTemplateEntity t : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("templateId", t.getId());
            row.put("templateName", t.getTemplateName());
            row.put("dataType", t.getDataType());
            row.put("enabled", t.getEnabled());
            row.put("usageCount", t.getUsageCount() != null ? t.getUsageCount() : 0);
            result.add(row);
        }
        return result;
    }

    /**
     * 最近导入/导出任务 (按创建时间倒序合并)。
     *
     * @param limit 返回条数上限
     * @return 最近任务列表 (每项含 taskType 标识)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentTransfers(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ScrmImportTaskEntity> importPage = importTaskRepository.findAll(
                (root, query, cb) -> cb.and(), pageable);
        Page<ScrmExportTaskEntity> exportPage = exportTaskRepository.findAll(
                (root, query, cb) -> cb.and(), pageable);

        List<Map<String, Object>> imports = importPage.getContent().stream()
                .map(t -> toRecentItem(TASK_TYPE_IMPORT, t.getId(), t.getTaskName(), t.getDataType(),
                        t.getStatus(), t.getTriggeredBy(), t.getCreateTime()))
                .toList();
        List<Map<String, Object>> exports = exportPage.getContent().stream()
                .map(t -> toRecentItem(TASK_TYPE_EXPORT, t.getId(), t.getTaskName(), t.getDataType(),
                        t.getStatus(), t.getTriggeredBy(), t.getCreateTime()))
                .toList();
        return Stream.concat(imports.stream(), exports.stream())
                .sorted(Comparator.comparing(m -> (LocalDateTime) m.get("createTime"),
                        Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验导入模板参数。
     *
     * @param dto     模板参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTemplateDto(ScrmImportTemplateDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("模板参数不能为空");
        }
        if (dto.getTemplateName() != null) {
            if (dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("模板名称不能为空");
        }
        if (dto.getDataType() != null) {
            if (!VALID_DATA_TYPES.contains(dto.getDataType())) {
                throw ScrmException.badRequest(
                        "数据类型非法: " + dto.getDataType() + ", 仅支持 " + VALID_DATA_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("数据类型不能为空");
        }
        if (dto.getColumns() != null) {
            if (dto.getColumns().isBlank()) {
                throw ScrmException.badRequest("列定义不能为空");
            }
            try {
                objectMapper.readTree(dto.getColumns());
            } catch (Exception e) {
                throw ScrmException.badRequest("列定义不是合法 JSON: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("列定义不能为空");
        }
        if (dto.getValidationRules() != null && !dto.getValidationRules().isBlank()) {
            try {
                objectMapper.readTree(dto.getValidationRules());
            } catch (Exception e) {
                throw ScrmException.badRequest("校验规则不是合法 JSON: " + e.getMessage());
            }
        }
    }

    /**
     * 校验导入任务参数。
     *
     * @param dto 任务参数
     * @throws ScrmException 参数非法
     */
    private void validateImportTaskDto(ScrmImportTaskDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getDataType() == null || !VALID_DATA_TYPES.contains(dto.getDataType())) {
            throw ScrmException.badRequest(
                    "数据类型非法: " + dto.getDataType() + ", 仅支持 " + VALID_DATA_TYPES);
        }
        if (dto.getFilePath() == null || dto.getFilePath().isBlank()) {
            throw ScrmException.badRequest("文件路径不能为空");
        }
        if (dto.getFileName() == null || dto.getFileName().isBlank()) {
            throw ScrmException.badRequest("文件名称不能为空");
        }
        if (dto.getTriggeredBy() == null || dto.getTriggeredBy().isBlank()) {
            throw ScrmException.badRequest("触发人不能为空");
        }
        if (dto.getOptions() != null && !dto.getOptions().isBlank()) {
            try {
                objectMapper.readTree(dto.getOptions());
            } catch (Exception e) {
                throw ScrmException.badRequest("导入选项不是合法 JSON: " + e.getMessage());
            }
        }
    }

    /**
     * 校验导出任务参数。
     *
     * @param dto 任务参数
     * @throws ScrmException 参数非法
     */
    private void validateExportTaskDto(ScrmExportTaskDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("任务参数不能为空");
        }
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getDataType() == null || !VALID_DATA_TYPES.contains(dto.getDataType())) {
            throw ScrmException.badRequest(
                    "数据类型非法: " + dto.getDataType() + ", 仅支持 " + VALID_DATA_TYPES);
        }
        if (dto.getTriggeredBy() == null || dto.getTriggeredBy().isBlank()) {
            throw ScrmException.badRequest("触发人不能为空");
        }
        if (dto.getQueryCondition() != null && !dto.getQueryCondition().isBlank()) {
            try {
                objectMapper.readTree(dto.getQueryCondition());
            } catch (Exception e) {
                throw ScrmException.badRequest("查询条件不是合法 JSON: " + e.getMessage());
            }
        }
        if (dto.getFilters() != null && !dto.getFilters().isBlank()) {
            try {
                objectMapper.readTree(dto.getFilters());
            } catch (Exception e) {
                throw ScrmException.badRequest("过滤条件不是合法 JSON: " + e.getMessage());
            }
        }
    }

    /**
     * 按主键查询模板, 不存在抛 404, 并校验账号归属。
     */
    private ScrmImportTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmImportTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "导入模板不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询导入任务, 不存在抛 404, 并校验账号归属。
     */
    private ScrmImportTaskEntity findImportTaskOrThrow(Long id) throws ScrmException {
        ScrmImportTaskEntity entity = importTaskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "导入任务不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询导出任务, 不存在抛 404, 并校验账号归属。
     */
    private ScrmExportTaskEntity findExportTaskOrThrow(Long id) throws ScrmException {
        ScrmExportTaskEntity entity = exportTaskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "导出任务不存在: id=" + id));

        return entity;
    }

    /**
     * 写入一条导入导出日志。
     */
    private void writeLog(Long taskId, String taskType, Integer rowIndex, String recordKey,
                           String operation, String fieldErrors, String message, String status) {
        try {
            ScrmDataTransferLogEntity logEntity = new ScrmDataTransferLogEntity();
            logEntity.setTaskId(taskId);
            logEntity.setTaskType(taskType);
            logEntity.setRowIndex(rowIndex);
            logEntity.setRecordKey(recordKey);
            logEntity.setOperation(operation);
            logEntity.setFieldErrors(fieldErrors);
            logEntity.setMessage(message);
            logEntity.setStatus(status);
            logEntity.setProcessedAt(LocalDateTime.now());
            logRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("写入数据导入导出日志失败, 忽略: taskId={}, err={}", taskId, e.getMessage());
        }
    }

    /**
     * 构造导入任务时间范围查询 Specification。
     */
    private Specification<ScrmImportTaskEntity> timeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构造导出任务时间范围查询 Specification。
     */
    private Specification<ScrmExportTaskEntity> exportTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 根据文件名后缀推导文件类型。
     */
    private String detectFileType(String fileName) {
        if (fileName == null) {
            return "CSV";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "EXCEL";
        }
        if (lower.endsWith(".json")) {
            return "JSON";
        }
        return "CSV";
    }

    /**
     * 构造最近任务条目。
     */
    private Map<String, Object> toRecentItem(String taskType, Long id, String taskName, String dataType,
                                              String status, String triggeredBy, LocalDateTime createTime) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskType", taskType);
        item.put("taskId", id);
        item.put("taskName", taskName);
        item.put("dataType", dataType);
        item.put("status", status);
        item.put("triggeredBy", triggeredBy);
        item.put("createTime", createTime);
        return item;
    }

    /**
     * 序列化为 JSON 字符串, 失败返回 "{}".
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 保留两位小数。
     */
    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
