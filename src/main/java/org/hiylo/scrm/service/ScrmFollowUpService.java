/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFollowUpService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFollowUpRecordDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmFollowUpTemplateDto;
import org.hiylo.scrm.entity.ScrmFollowUpRecordEntity;
import org.hiylo.scrm.entity.ScrmFollowUpTaskEntity;
import org.hiylo.scrm.entity.ScrmFollowUpTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmFollowUpRecordRepository;
import org.hiylo.scrm.repository.ScrmFollowUpTaskRepository;
import org.hiylo.scrm.repository.ScrmFollowUpTemplateRepository;
import org.hiylo.scrm.vo.FollowUpCalendarVo;
import org.hiylo.scrm.vo.FollowUpTaskStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 跟进计划/任务管理服务
 * <p>
 * 负责销售跟进任务的全生命周期管理: 任务创建/分配/完成/取消/批量创建, 跟进模板维护与应用,
 * 跟进记录登记与查询, 待提醒任务扫描与标记, 以及任务统计与跟进日历聚合。
 * </p>
 * <p>
 * 任务状态流转: PENDING (待处理) → IN_PROGRESS (进行中) → COMPLETED (已完成);
 * 任意非终态可 → CANCELLED (已取消); 计划时间过期且未完成 → OVERDUE (已逾期)。
 * </p>
 * <p>
 * 所有写操作均写入当前用户归属账号, 实现数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFollowUpService {

    // ==================== 任务状态常量 ====================

    /** 任务状态: 待处理 */
    private static final String STATUS_PENDING = "PENDING";
    /** 任务状态: 进行中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 任务状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";
    /** 任务状态: 已逾期 */
    private static final String STATUS_OVERDUE = "OVERDUE";

    // ==================== 默认值常量 ====================

    /** 默认优先级 */
    private static final String DEFAULT_PRIORITY = "MEDIUM";
    /** 默认提醒分钟数 */
    private static final int DEFAULT_REMINDER_MINUTES = 30;
    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;
    /** 日期格式 (yyyy-MM-dd) */
    private static final String DATE_PATTERN = "yyyy-MM-dd";
    /** 月份格式 (yyyy-MM) */
    private static final String MONTH_PATTERN = "yyyy-MM";
    /** 日期时间格式 (用于统计区间展示) */
    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /** 跟进任务数据仓库 */
    private final ScrmFollowUpTaskRepository taskRepository;
    /** 跟进模板数据仓库 */
    private final ScrmFollowUpTemplateRepository templateRepository;
    /** 跟进记录数据仓库 */
    private final ScrmFollowUpRecordRepository recordRepository;

    // ============================================================
    // 任务管理
    // ============================================================

    /**
     * 创建跟进任务
     * <p>
     * 默认状态 PENDING, 优先级取 dto.priority 或 MEDIUM, 提醒分钟数取 dto.reminderMinutes 或 30,
     * reminded 初始化为 false。
     * </p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmFollowUpTaskDto createTask(ScrmFollowUpTaskDto dto) throws ScrmException {
        validateTaskDto(dto);
        ScrmFollowUpTaskEntity entity = new ScrmFollowUpTaskEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setAccountId(dto.getAccountId());
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setAssigneeName(dto.getAssigneeName());
        entity.setTaskType(dto.getTaskType());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setPlannedAt(dto.getPlannedAt());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_PENDING);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setReminderMinutes(dto.getReminderMinutes() != null
                ? dto.getReminderMinutes() : DEFAULT_REMINDER_MINUTES);
        entity.setReminded(Boolean.FALSE);
        entity.setFollowUpResult(dto.getFollowUpResult());
        entity.setNextFollowUpAt(dto.getNextFollowUpAt());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = taskRepository.save(entity);
        log.info("创建跟进任务: id={}, customerId={}, assigneeId={}, taskType={}, plannedAt={}",
                entity.getId(), entity.getCustomerId(), entity.getAssigneeId(),
                entity.getTaskType(), entity.getPlannedAt());
        return toTaskDto(entity);
    }

    /**
     * 更新跟进任务
     * <p>
     * 已完成或已取消的任务不允许修改关键字段 (customerId/taskType/plannedAt), 仅允许调整备注类字段。
     * 字段非空才覆盖, status 不在此处修改 (使用 complete/cancel 专用接口)。
     * </p>
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmFollowUpTaskDto updateTask(Long id, ScrmFollowUpTaskDto dto) throws ScrmException {
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(id);
        boolean terminal = STATUS_COMPLETED.equals(entity.getStatus())
                || STATUS_CANCELLED.equals(entity.getStatus());
        if (terminal) {
            if (dto.getCustomerId() != null && !dto.getCustomerId().equals(entity.getCustomerId())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "已完成/取消的任务不允许修改客户: id=" + id);
            }
            if (dto.getTaskType() != null && !dto.getTaskType().equals(entity.getTaskType())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "已完成/取消的任务不允许修改跟进类型: id=" + id);
            }
            if (dto.getPlannedAt() != null && !dto.getPlannedAt().equals(entity.getPlannedAt())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "已完成/取消的任务不允许修改计划时间: id=" + id);
            }
        }
        if (dto.getCustomerId() != null) {
            entity.setCustomerId(dto.getCustomerId());
        }
        if (dto.getCustomerName() != null) {
            entity.setCustomerName(dto.getCustomerName());
        }
        if (dto.getAccountId() != null) {
            entity.setAccountId(dto.getAccountId());
        }
        if (dto.getAssigneeId() != null) {
            if (dto.getAssigneeId().isBlank()) {
                throw ScrmException.badRequest("负责人不能为空");
            }
            entity.setAssigneeId(dto.getAssigneeId());
        }
        if (dto.getAssigneeName() != null) {
            entity.setAssigneeName(dto.getAssigneeName());
        }
        if (dto.getTaskType() != null) {
            entity.setTaskType(dto.getTaskType());
        }
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("任务标题不能为空");
            }
            entity.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
        }
        if (dto.getPlannedAt() != null) {
            entity.setPlannedAt(dto.getPlannedAt());
        }
        if (dto.getPriority() != null) {
            entity.setPriority(dto.getPriority());
        }
        if (dto.getReminderMinutes() != null) {
            entity.setReminderMinutes(dto.getReminderMinutes());
        }
        if (dto.getFollowUpResult() != null) {
            entity.setFollowUpResult(dto.getFollowUpResult());
        }
        if (dto.getNextFollowUpAt() != null) {
            entity.setNextFollowUpAt(dto.getNextFollowUpAt());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        entity = taskRepository.save(entity);
        log.info("更新跟进任务: id={}", id);
        return toTaskDto(entity);
    }

    /**
     * 删除跟进任务
     * <p>
     * 同时清理关联的跟进记录 (将 record.taskId 置空由外键 ON DELETE SET NULL 处理)。
     * </p>
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void deleteTask(Long id) throws ScrmException {
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(id);
        taskRepository.delete(entity);
        log.info("删除跟进任务: id={}, title={}", id, entity.getTitle());
    }

    /**
     * 查询跟进任务详情
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmFollowUpTaskDto getTask(Long id) throws ScrmException {
        return toTaskDto(findTaskOrThrow(id));
    }

    /**
     * 分页查询跟进任务, 支持按客户 / 负责人 / 状态 / 类型 / 优先级 / 计划时间区间过滤
     *
     * @param customerId  客户 ID 过滤 (可空)
     * @param assigneeId  负责人 ID 过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param taskType    跟进类型过滤 (可空)
     * @param priority    优先级过滤 (可空)
     * @param startDate   计划起始时间过滤 (可空)
     * @param endDate     计划截止时间过滤 (可空)
     * @param pageable    分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFollowUpTaskDto> listTasks(Long customerId, String assigneeId, String status,
                                                String taskType, String priority,
                                                LocalDateTime startDate, LocalDateTime endDate,
                                                Pageable pageable) {
        Specification<ScrmFollowUpTaskEntity> spec = buildTaskSpec(
                customerId, assigneeId, status, taskType, priority, startDate, endDate);
        Pageable sorted = ensureSort(pageable, "plannedAt");
        return taskRepository.findAll(spec, sorted).map(this::toTaskDto);
    }

    /**
     * 完成跟进任务
     * <p>
     * 将状态置 COMPLETED, 记录完成时间与跟进结果, 可选设置下次跟进时间。
     * 已取消的任务不允许完成。
     * </p>
     *
     * @param id              任务 ID
     * @param followUpResult  跟进结果 (可空)
     * @param nextFollowUpAt  下次跟进时间 (可空, 设置后会同步到任务 nextFollowUpAt)
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmFollowUpTaskDto completeTask(Long id, String followUpResult,
                                             LocalDateTime nextFollowUpAt) throws ScrmException {
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(id);
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "已取消的任务不允许完成: id=" + id);
        }
        if (STATUS_COMPLETED.equals(entity.getStatus())) {
            // 幂等: 已完成则仅更新结果字段
            log.info("任务已完成, 幂等更新结果: id={}", id);
        }
        entity.setStatus(STATUS_COMPLETED);
        entity.setCompletedAt(LocalDateTime.now());
        entity.setFollowUpResult(followUpResult);
        if (nextFollowUpAt != null) {
            entity.setNextFollowUpAt(nextFollowUpAt);
        }
        entity = taskRepository.save(entity);
        log.info("完成跟进任务: id={}, result={}", id, followUpResult);
        return toTaskDto(entity);
    }

    /**
     * 取消跟进任务
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmFollowUpTaskDto cancelTask(Long id) throws ScrmException {
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(id);
        if (STATUS_COMPLETED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "已完成的任务不允许取消: id=" + id);
        }
        if (STATUS_CANCELLED.equals(entity.getStatus())) {
            return toTaskDto(entity);
        }
        entity.setStatus(STATUS_CANCELLED);
        entity = taskRepository.save(entity);
        log.info("取消跟进任务: id={}", id);
        return toTaskDto(entity);
    }

    /**
     * 分配跟进任务 (变更负责人)
     *
     * @param id          任务 ID
     * @param assigneeId  新负责人 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 负责人非法
     */
    @Transactional
    public ScrmFollowUpTaskDto assignTask(Long id, String assigneeId) throws ScrmException {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(id);
        entity.setAssigneeId(assigneeId);
        entity = taskRepository.save(entity);
        log.info("分配跟进任务: id={}, assigneeId={}", id, assigneeId);
        return toTaskDto(entity);
    }

    /**
     * 批量创建跟进任务
     * <p>
     * 单条失败跳过并记录告警, 不阻断其他任务。
     * </p>
     *
     * @param tasks 任务参数列表
     * @return 成功创建的任务列表
     * @throws ScrmException 列表为空
     */
    @Transactional
    public List<ScrmFollowUpTaskDto> batchCreate(List<ScrmFollowUpTaskDto> tasks) throws ScrmException {
        if (tasks == null || tasks.isEmpty()) {
            throw ScrmException.badRequest("任务列表不能为空");
        }
        List<ScrmFollowUpTaskDto> created = new ArrayList<>(tasks.size());
        for (ScrmFollowUpTaskDto dto : tasks) {
            if (dto == null) {
                continue;
            }
            try {
                created.add(createTask(dto));
            } catch (ScrmException e) {
                log.warn("批量创建跟进任务失败, 跳过: customerId={}, assigneeId={}, code={}, msg={}",
                        dto.getCustomerId(), dto.getAssigneeId(), e.getCode(), e.getMessage());
            }
        }
        log.info("批量创建跟进任务完成: requested={}, success={}", tasks.size(), created.size());
        return created;
    }

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建跟进模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmFollowUpTemplateDto createTemplate(ScrmFollowUpTemplateDto dto) throws ScrmException {
        validateTemplateDto(dto);
        ScrmFollowUpTemplateEntity entity = new ScrmFollowUpTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setTaskType(dto.getTaskType());
        entity.setTitleTemplate(dto.getTitleTemplate());
        entity.setContentTemplate(dto.getContentTemplate());
        entity.setDefaultPriority(dto.getDefaultPriority() != null
                ? dto.getDefaultPriority() : DEFAULT_PRIORITY);
        entity.setDefaultReminderMinutes(dto.getDefaultReminderMinutes() != null
                ? dto.getDefaultReminderMinutes() : DEFAULT_REMINDER_MINUTES);
        entity.setPlatformType(dto.getPlatformType());
        entity.setScenario(dto.getScenario());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setUseCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = templateRepository.save(entity);
        log.info("创建跟进模板: id={}, templateName={}, taskType={}",
                entity.getId(), entity.getTemplateName(), entity.getTaskType());
        return toTemplateDto(entity);
    }

    /**
     * 更新跟进模板
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public ScrmFollowUpTemplateDto updateTemplate(Long id, ScrmFollowUpTemplateDto dto) throws ScrmException {
        ScrmFollowUpTemplateEntity entity = findTemplateOrThrow(id);
        if (dto.getTemplateName() != null) {
            if (dto.getTemplateName().isBlank()) {
                throw ScrmException.badRequest("模板名称不能为空");
            }
            entity.setTemplateName(dto.getTemplateName());
        }
        if (dto.getTaskType() != null) {
            entity.setTaskType(dto.getTaskType());
        }
        if (dto.getTitleTemplate() != null) {
            if (dto.getTitleTemplate().isBlank()) {
                throw ScrmException.badRequest("标题模板不能为空");
            }
            entity.setTitleTemplate(dto.getTitleTemplate());
        }
        if (dto.getContentTemplate() != null) {
            entity.setContentTemplate(dto.getContentTemplate());
        }
        if (dto.getDefaultPriority() != null) {
            entity.setDefaultPriority(dto.getDefaultPriority());
        }
        if (dto.getDefaultReminderMinutes() != null) {
            entity.setDefaultReminderMinutes(dto.getDefaultReminderMinutes());
        }
        if (dto.getPlatformType() != null) {
            entity.setPlatformType(dto.getPlatformType());
        }
        if (dto.getScenario() != null) {
            entity.setScenario(dto.getScenario());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        entity = templateRepository.save(entity);
        log.info("更新跟进模板: id={}", id);
        return toTemplateDto(entity);
    }

    /**
     * 删除跟进模板
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmFollowUpTemplateEntity entity = findTemplateOrThrow(id);
        templateRepository.delete(entity);
        log.info("删除跟进模板: id={}, name={}", id, entity.getTemplateName());
    }

    /**
     * 查询跟进模板详情
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmFollowUpTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 分页查询跟进模板, 支持按跟进类型 / 场景 / 启用状态过滤
     *
     * @param taskType 跟进类型过滤 (可空)
     * @param scenario 场景过滤 (可空)
     * @param enabled 启用状态过滤 (可空)
     * @param pageable 分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFollowUpTemplateDto> listTemplates(String taskType, String scenario,
                                                        Boolean enabled, Pageable pageable) {
        Specification<ScrmFollowUpTemplateEntity> spec = buildTemplateSpec(taskType, scenario, enabled);
        Pageable sorted = ensureSort(pageable, "createTime");
        return templateRepository.findAll(spec, sorted).map(this::toTemplateDto);
    }

    /**
     * 应用模板创建任务
     * <p>
     * 基于模板的 taskType / titleTemplate / contentTemplate / defaultPriority /
     * defaultReminderMinutes 创建一条跟进任务, 模板 useCount 自增。
     * </p>
     *
     * @param templateId 模板 ID
     * @param customerId 客户 ID
     * @param assigneeId 负责人 ID
     * @param plannedAt  计划跟进时间
     * @return 创建后的任务
     * @throws ScrmException 模板不存在 / 已禁用
     */
    @Transactional
    public ScrmFollowUpTaskDto applyTemplate(Long templateId, Long customerId, String assigneeId,
                                              LocalDateTime plannedAt) throws ScrmException {
        if (customerId == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        if (plannedAt == null) {
            throw ScrmException.badRequest("计划跟进时间不能为空");
        }
        ScrmFollowUpTemplateEntity template = findTemplateOrThrow(templateId);
        if (Boolean.FALSE.equals(template.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "模板已禁用, 不允许应用: id=" + templateId);
        }
        ScrmFollowUpTaskDto taskDto = new ScrmFollowUpTaskDto();
        taskDto.setCustomerId(customerId);
        taskDto.setAssigneeId(assigneeId);
        taskDto.setTaskType(template.getTaskType());
        taskDto.setTitle(template.getTitleTemplate());
        taskDto.setContent(template.getContentTemplate());
        taskDto.setPlannedAt(plannedAt);
        taskDto.setStatus(STATUS_PENDING);
        taskDto.setPriority(template.getDefaultPriority() != null
                ? template.getDefaultPriority() : DEFAULT_PRIORITY);
        taskDto.setReminderMinutes(template.getDefaultReminderMinutes() != null
                ? template.getDefaultReminderMinutes() : DEFAULT_REMINDER_MINUTES);
        ScrmFollowUpTaskDto created = createTask(taskDto);
        // 模板使用次数 +1
        template.setUseCount((template.getUseCount() == null ? 0 : template.getUseCount()) + 1);
        templateRepository.save(template);
        log.info("应用模板创建任务: templateId={}, templateName={}, taskId={}",
                templateId, template.getTemplateName(), created.getId());
        return created;
    }

    // ============================================================
    // 跟进记录
    // ============================================================

    /**
     * 创建跟进记录
     * <p>
     * 可关联任务 (taskId 非空时), 同时将关联任务标记为 IN_PROGRESS (若为 PENDING)。
     * 若指定了 nextFollowUpAt, 同步更新关联任务的 nextFollowUpAt。
     * recordedAt 缺省取当前时间。
     * </p>
     *
     * @param dto 跟进记录参数
     * @return 创建后的跟进记录
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmFollowUpRecordDto createRecord(ScrmFollowUpRecordDto dto) throws ScrmException {
        validateRecordDto(dto);
        ScrmFollowUpRecordEntity entity = new ScrmFollowUpRecordEntity();
        entity.setTaskId(dto.getTaskId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setContactMethod(dto.getContactMethod());
        entity.setContactResult(dto.getContactResult());
        entity.setContent(dto.getContent());
        entity.setDurationMinutes(dto.getDurationMinutes());
        entity.setSentiment(dto.getSentiment());
        entity.setNextAction(dto.getNextAction());
        entity.setNextFollowUpAt(dto.getNextFollowUpAt());
        entity.setRecordedBy(dto.getRecordedBy());
        entity.setRecordedAt(dto.getRecordedAt() != null ? dto.getRecordedAt() : LocalDateTime.now());
        entity = recordRepository.save(entity);
        // 关联任务联动: PENDING → IN_PROGRESS, 并同步下次跟进时间
        if (dto.getTaskId() != null) {
            taskRepository.findById(dto.getTaskId()).ifPresent(task -> {
                if (STATUS_PENDING.equals(task.getStatus())) {
                    task.setStatus(STATUS_IN_PROGRESS);
                }
                if (dto.getNextFollowUpAt() != null) {
                    task.setNextFollowUpAt(dto.getNextFollowUpAt());
                }
                taskRepository.save(task);
            });
        }
        log.info("创建跟进记录: id={}, customerId={}, taskId={}, contactMethod={}",
                entity.getId(), entity.getCustomerId(), entity.getTaskId(), entity.getContactMethod());
        return toRecordDto(entity);
    }

    /**
     * 分页查询跟进记录, 支持按客户 / 任务过滤
     *
     * @param customerId 客户 ID 过滤 (可空)
     * @param taskId     任务 ID 过滤 (可空)
     * @param pageable    分页参数
     * @return 跟进记录分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFollowUpRecordDto> getRecords(Long customerId, Long taskId, Pageable pageable) {
        Specification<ScrmFollowUpRecordEntity> spec = buildRecordSpec(customerId, taskId);
        Pageable sorted = ensureSort(pageable, "recordedAt");
        return recordRepository.findAll(spec, sorted).map(this::toRecordDto);
    }

    // ============================================================
    // 提醒
    // ============================================================

    /**
     * 获取需要提醒的任务列表
     * <p>
     * 筛选条件: reminded=false 且未完成, 且 plannedAt - reminderMinutes <= now。
     * 由 Repository 拉取未提醒候选集, Service 层按各任务的提前分钟数二次过滤。
     * </p>
     *
     * @return 待提醒任务列表
     */
    @Transactional(readOnly = true)
    public List<ScrmFollowUpTaskDto> getPendingReminders() {
        List<ScrmFollowUpTaskEntity> candidates = taskRepository.findUnremindedTasks();
        if (candidates.isEmpty()) {
            return new ArrayList<>();
        }
        LocalDateTime now = LocalDateTime.now();
        List<ScrmFollowUpTaskDto> result = candidates.stream()
                .filter(t -> {
                    Integer minutes = t.getReminderMinutes();
                    if (minutes == null) {
                        return false;
                    }
                    LocalDateTime reminderAt = t.getPlannedAt().minusMinutes(minutes);
                    return !reminderAt.isAfter(now);
                })
                .map(this::toTaskDto)
                .collect(Collectors.toList());
        log.debug("获取待提醒任务:, candidates={}, hit={}", candidates.size(), result.size());
        return result;
    }

    /**
     * 标记任务已提醒
     *
     * @param taskId 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public ScrmFollowUpTaskDto markReminded(Long taskId) throws ScrmException {
        ScrmFollowUpTaskEntity entity = findTaskOrThrow(taskId);
        entity.setReminded(Boolean.TRUE);
        entity = taskRepository.save(entity);
        log.info("标记任务已提醒: id={}", taskId);
        return toTaskDto(entity);
    }

    // ============================================================
    // 统计与日历
    // ============================================================

    /**
     * 任务统计
     * <p>
     * 按负责人与计划时间区间聚合: 总数 / 完成数 / 完成率 / 逾期数 / 进行中数 / 待处理数 / 已取消数,
     * 以及各跟进类型与优先级的分布。
     * </p>
     *
     * @param assigneeId 负责人 ID 过滤 (可空, 空表示全员)
     * @param startDate  计划起始时间 (可空)
     * @param endDate    计划截止时间 (可空)
     * @return 任务统计结果
     */
    @Transactional(readOnly = true)
    public FollowUpTaskStatsVo getTaskStats(String assigneeId, LocalDateTime startDate,
                                             LocalDateTime endDate) {
        Specification<ScrmFollowUpTaskEntity> spec = buildTaskSpec(
                null, assigneeId, null, null, null, startDate, endDate);
        List<ScrmFollowUpTaskEntity> tasks = taskRepository.findAll(spec);
        long total = tasks.size();
        long completed = tasks.stream().filter(t -> STATUS_COMPLETED.equals(t.getStatus())).count();
        long overdue = tasks.stream().filter(t -> STATUS_OVERDUE.equals(t.getStatus())
                || (isOverdue(t) && !STATUS_COMPLETED.equals(t.getStatus()) && !STATUS_CANCELLED.equals(t.getStatus()))).count();
        long inProgress = tasks.stream().filter(t -> STATUS_IN_PROGRESS.equals(t.getStatus())).count();
        long pending = tasks.stream().filter(t -> STATUS_PENDING.equals(t.getStatus())).count();
        long cancelled = tasks.stream().filter(t -> STATUS_CANCELLED.equals(t.getStatus())).count();
        double completionRate = total == 0 ? 0d : round2(completed * PERCENT_BASE / total);
        // 类型分布
        Map<String, Long> typeDist = tasks.stream()
                .filter(t -> t.getTaskType() != null)
                .collect(Collectors.groupingBy(ScrmFollowUpTaskEntity::getTaskType,
                        LinkedHashMap::new, Collectors.counting()));
        // 优先级分布
        Map<String, Long> priorityDist = tasks.stream()
                .filter(t -> t.getPriority() != null)
                .collect(Collectors.groupingBy(ScrmFollowUpTaskEntity::getPriority,
                        LinkedHashMap::new, Collectors.counting()));
        // 各类型完成率
        List<FollowUpTaskStatsVo.TaskTypeStatsVo> typeStats = typeDist.entrySet().stream()
                .map(e -> {
                    long typeTotal = e.getValue();
                    long typeCompleted = tasks.stream()
                            .filter(t -> e.getKey().equals(t.getTaskType()) && STATUS_COMPLETED.equals(t.getStatus()))
                            .count();
                    double rate = typeTotal == 0 ? 0d : round2(typeCompleted * PERCENT_BASE / typeTotal);
                    return FollowUpTaskStatsVo.TaskTypeStatsVo.builder()
                            .taskType(e.getKey())
                            .totalCount(typeTotal)
                            .completedCount(typeCompleted)
                            .completionRate(rate)
                            .build();
                })
                .collect(Collectors.toList());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        return FollowUpTaskStatsVo.builder()
                .assigneeId(assigneeId)
                .startDate(startDate != null ? startDate.format(fmt) : null)
                .endDate(endDate != null ? endDate.format(fmt) : null)
                .totalCount(total)
                .completedCount(completed)
                .completionRate(completionRate)
                .overdueCount(overdue)
                .inProgressCount(inProgress)
                .pendingCount(pending)
                .cancelledCount(cancelled)
                .taskTypeDistribution(typeDist)
                .priorityDistribution(priorityDist)
                .typeStats(typeStats)
                .build();
    }

    /**
     * 跟进日历
     * <p>
     * 按负责人与月份聚合, 返回当月每日的任务总数 / 完成数 / 待处理数, 用于日历视图。
     * </p>
     *
     * @param assigneeId 负责人 ID 过滤 (可空, 空表示全员)
     * @param month      月份 (yyyy-MM, 可空表示当月)
     * @return 跟进日历结果
     */
    @Transactional(readOnly = true)
    public FollowUpCalendarVo getFollowUpCalendar(String assigneeId, String month) {
        YearMonth ym = parseMonth(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        Specification<ScrmFollowUpTaskEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get("plannedAt"), start));
            predicates.add(cb.lessThan(root.get("plannedAt"), end));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmFollowUpTaskEntity> tasks = taskRepository.findAll(spec);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern(DATE_PATTERN);
        // 按天聚合
        Map<String, List<ScrmFollowUpTaskEntity>> byDay = tasks.stream()
                .filter(t -> t.getPlannedAt() != null)
                .collect(Collectors.groupingBy(t -> t.getPlannedAt().format(dayFmt),
                        LinkedHashMap::new, Collectors.toList()));
        List<FollowUpCalendarVo.DayCountVo> days = byDay.entrySet().stream()
                .map(e -> {
                    List<ScrmFollowUpTaskEntity> dayTasks = e.getValue();
                    long dayTotal = dayTasks.size();
                    long dayCompleted = dayTasks.stream()
                            .filter(t -> STATUS_COMPLETED.equals(t.getStatus())).count();
                    long dayPending = dayTasks.stream()
                            .filter(t -> STATUS_PENDING.equals(t.getStatus())
                                    || STATUS_IN_PROGRESS.equals(t.getStatus()))
                            .count();
                    return FollowUpCalendarVo.DayCountVo.builder()
                            .date(e.getKey())
                            .totalCount(dayTotal)
                            .completedCount(dayCompleted)
                            .pendingCount(dayPending)
                            .build();
                })
                .sorted(Comparator.comparing(FollowUpCalendarVo.DayCountVo::getDate))
                .collect(Collectors.toList());
        long total = tasks.size();
        long completed = tasks.stream().filter(t -> STATUS_COMPLETED.equals(t.getStatus())).count();
        return FollowUpCalendarVo.builder()
                .assigneeId(assigneeId)
                .month(ym.format(DateTimeFormatter.ofPattern(MONTH_PATTERN)))
                .totalCount(total)
                .completedCount(completed)
                .days(days)
                .build();
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 校验任务 DTO 必填字段
     */
    private void validateTaskDto(ScrmFollowUpTaskDto dto) throws ScrmException {
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getAssigneeId() == null || dto.getAssigneeId().isBlank()) {
            throw ScrmException.badRequest("负责人不能为空");
        }
        if (dto.getTaskType() == null || dto.getTaskType().isBlank()) {
            throw ScrmException.badRequest("跟进类型不能为空");
        }
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw ScrmException.badRequest("任务标题不能为空");
        }
        if (dto.getPlannedAt() == null) {
            throw ScrmException.badRequest("计划跟进时间不能为空");
        }
    }

    /**
     * 校验模板 DTO 必填字段
     */
    private void validateTemplateDto(ScrmFollowUpTemplateDto dto) throws ScrmException {
        if (dto.getTemplateName() == null || dto.getTemplateName().isBlank()) {
            throw ScrmException.badRequest("模板名称不能为空");
        }
        if (dto.getTaskType() == null || dto.getTaskType().isBlank()) {
            throw ScrmException.badRequest("跟进类型不能为空");
        }
        if (dto.getTitleTemplate() == null || dto.getTitleTemplate().isBlank()) {
            throw ScrmException.badRequest("标题模板不能为空");
        }
    }

    /**
     * 校验跟进记录 DTO 必填字段
     */
    private void validateRecordDto(ScrmFollowUpRecordDto dto) throws ScrmException {
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getContactMethod() == null || dto.getContactMethod().isBlank()) {
            throw ScrmException.badRequest("接触方式不能为空");
        }
        if (dto.getContactResult() == null || dto.getContactResult().isBlank()) {
            throw ScrmException.badRequest("接触结果不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw ScrmException.badRequest("跟进内容不能为空");
        }
        if (dto.getRecordedBy() == null || dto.getRecordedBy().isBlank()) {
            throw ScrmException.badRequest("记录人不能为空");
        }
    }

    /**
     * 构建任务查询 Specification
     */
    private Specification<ScrmFollowUpTaskEntity> buildTaskSpec(Long customerId, String assigneeId,
                                                                  String status, String taskType,
                                                                  String priority,
                                                                  LocalDateTime startDate,
                                                                  LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (taskType != null && !taskType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("taskType")), taskType.toLowerCase()));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("priority")), priority.toLowerCase()));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("plannedAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("plannedAt"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建模板查询 Specification
     */
    private Specification<ScrmFollowUpTemplateEntity> buildTemplateSpec(String taskType, String scenario,
                                                                        Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (taskType != null && !taskType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("taskType")), taskType.toLowerCase()));
            }
            if (scenario != null && !scenario.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("scenario")), scenario.toLowerCase()));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建跟进记录查询 Specification
     */
    private Specification<ScrmFollowUpRecordEntity> buildRecordSpec(Long customerId, Long taskId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序 (按指定字段倒序)
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 判断任务是否逾期 (计划时间早于当前且未完成/未取消)
     */
    private boolean isOverdue(ScrmFollowUpTaskEntity task) {
        if (task.getPlannedAt() == null) {
            return false;
        }
        return task.getPlannedAt().isBefore(LocalDateTime.now());
    }

    /**
     * 解析月份字符串 (yyyy-MM), null/非法时取当月
     */
    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month, DateTimeFormatter.ofPattern(MONTH_PATTERN));
        } catch (Exception e) {
            log.warn("月份格式非法, 回退当月: month={}", month);
            return YearMonth.now();
        }
    }

    /**
     * 保留两位小数
     */
    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }

    /**
     * 按主键查询任务并校验账号归属, 不存在或越权抛异常
     */
    private ScrmFollowUpTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmFollowUpTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "跟进任务不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询模板并校验账号归属, 不存在或越权抛异常
     */
    private ScrmFollowUpTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmFollowUpTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "跟进模板不存在: id=" + id));
        return entity;
    }


    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 任务实体转 DTO
     */
    private ScrmFollowUpTaskDto toTaskDto(ScrmFollowUpTaskEntity entity) {
        ScrmFollowUpTaskDto dto = new ScrmFollowUpTaskDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setAccountId(entity.getAccountId());
        dto.setAssigneeId(entity.getAssigneeId());
        dto.setAssigneeName(entity.getAssigneeName());
        dto.setTaskType(entity.getTaskType());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setPlannedAt(entity.getPlannedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setStatus(entity.getStatus());
        dto.setPriority(entity.getPriority());
        dto.setReminderMinutes(entity.getReminderMinutes());
        dto.setReminded(entity.getReminded());
        dto.setFollowUpResult(entity.getFollowUpResult());
        dto.setNextFollowUpAt(entity.getNextFollowUpAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 模板实体转 DTO
     */
    private ScrmFollowUpTemplateDto toTemplateDto(ScrmFollowUpTemplateEntity entity) {
        ScrmFollowUpTemplateDto dto = new ScrmFollowUpTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setTaskType(entity.getTaskType());
        dto.setTitleTemplate(entity.getTitleTemplate());
        dto.setContentTemplate(entity.getContentTemplate());
        dto.setDefaultPriority(entity.getDefaultPriority());
        dto.setDefaultReminderMinutes(entity.getDefaultReminderMinutes());
        dto.setPlatformType(entity.getPlatformType());
        dto.setScenario(entity.getScenario());
        dto.setEnabled(entity.getEnabled());
        dto.setUseCount(entity.getUseCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 跟进记录实体转 DTO
     */
    private ScrmFollowUpRecordDto toRecordDto(ScrmFollowUpRecordEntity entity) {
        ScrmFollowUpRecordDto dto = new ScrmFollowUpRecordDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setContactMethod(entity.getContactMethod());
        dto.setContactResult(entity.getContactResult());
        dto.setContent(entity.getContent());
        dto.setDurationMinutes(entity.getDurationMinutes());
        dto.setSentiment(entity.getSentiment());
        dto.setNextAction(entity.getNextAction());
        dto.setNextFollowUpAt(entity.getNextFollowUpAt());
        dto.setRecordedBy(entity.getRecordedBy());
        dto.setRecordedAt(entity.getRecordedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
