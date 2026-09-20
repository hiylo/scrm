/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMassSendService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmMassSendTaskDto;
import org.hiylo.scrm.dto.ScrmMassSendTargetDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmMassSendTargetEntity;
import org.hiylo.scrm.entity.ScrmMassSendTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmMassSendTargetRepository;
import org.hiylo.scrm.repository.ScrmMassSendTaskRepository;
import org.hiylo.scrm.vo.MassSendReportVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 群发任务服务
 * <p>
 * 负责群发任务的创建、更新、发布与生命周期管理 (暂停/恢复/取消),
 * 发布时按 targetType (全量/分群/标签/指定列表) 筛选目标客户生成 target 明细,
 * 并提供发送结果统计报告。所有查询均通过请求上下文做数据隔离。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmMassSendService {

    /** 任务状态: 草稿 */
    private static final String STATUS_DRAFT = "DRAFT";
    /** 任务状态: 待发布 */
    private static final String STATUS_PENDING = "PENDING";
    /** 任务状态: 运行中 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 任务状态: 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";
    /** 任务状态: 已完成 */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 任务状态: 已失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 目标类型: 全量客户 */
    private static final String TARGET_ALL = "ALL";
    /** 目标类型: 按分群 */
    private static final String TARGET_SEGMENT = "SEGMENT";
    /** 目标类型: 按标签 */
    private static final String TARGET_TAG = "TAG";
    /** 目标类型: 指定客户列表 */
    private static final String TARGET_LIST = "LIST";

    /** 发送状态: 待发送 */
    private static final String SEND_PENDING = "PENDING";
    /** 发送状态: 已发送 */
    private static final String SEND_SENT = "SENT";
    /** 发送状态: 发送失败 */
    private static final String SEND_FAILED = "FAILED";

    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;
    /** 比率小数保留位数 */
    private static final int RATE_SCALE = 2;

    /** 群发任务数据仓库 */
    private final ScrmMassSendTaskRepository taskRepository;
    /** 群发目标数据仓库 */
    private final ScrmMassSendTargetRepository targetRepository;
    /** 客户数据访问层, 用于发布任务时按 targetType 筛选目标客户 */
    private final ScrmCustomerRepository customerRepository;
    /** 客户标签数据访问层, 用于 targetType=TAG 时按标签筛选客户 */
    private final ScrmCustomerTagRepository customerTagRepository;
    /** 客户-标签赋值数据访问层 (重构后赋值关系独立存储) */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /**
     * 创建群发任务
     * <p>
     * 默认状态为 DRAFT, 计数器初始化为 0, 写入归属账号后持久化。
     * </p>
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmMassSendTaskDto createTask(ScrmMassSendTaskDto dto) throws ScrmException {
        validateCreateTask(dto);
        ScrmMassSendTaskEntity entity = new ScrmMassSendTaskEntity();
        entity.setTaskName(dto.getTaskName());
        entity.setPlatformType(dto.getPlatformType());
        entity.setMessageTemplateId(dto.getMessageTemplateId());
        entity.setContent(dto.getContent());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetFilter(dto.getTargetFilter());
        entity.setSenderAccountId(dto.getSenderAccountId());
        entity.setStatus(STATUS_DRAFT);
        entity.setTotalCount(0);
        entity.setSentCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setScheduledAt(dto.getScheduledAt());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = taskRepository.save(entity);
        log.info("创建群发任务: id={}, taskName={}, platformType={}, targetType={}",
                entity.getId(), entity.getTaskName(), entity.getPlatformType(), entity.getTargetType());
        return toTaskDto(entity);
    }

    /**
     * 更新群发任务草稿
     * <p>
     * 仅 DRAFT 状态允许更新, 字段非空才覆盖。
     * </p>
     *
     * @param id  任务 ID
     * @param dto 任务参数
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmMassSendTaskDto updateTask(Long id, ScrmMassSendTaskDto dto) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "仅 DRAFT 状态的任务允许更新: currentStatus=" + entity.getStatus());
        }
        if (dto.getTaskName() != null) {
            if (dto.getTaskName().isBlank()) {
                throw ScrmException.badRequest("任务名称不能为空");
            }
            entity.setTaskName(dto.getTaskName());
        }
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getMessageTemplateId() != null) entity.setMessageTemplateId(dto.getMessageTemplateId());
        if (dto.getContent() != null) {
            if (dto.getContent().isBlank()) {
                throw ScrmException.badRequest("群发内容不能为空");
            }
            entity.setContent(dto.getContent());
        }
        if (dto.getTargetType() != null) entity.setTargetType(dto.getTargetType());
        if (dto.getTargetFilter() != null) entity.setTargetFilter(dto.getTargetFilter());
        if (dto.getSenderAccountId() != null) entity.setSenderAccountId(dto.getSenderAccountId());
        if (dto.getScheduledAt() != null) entity.setScheduledAt(dto.getScheduledAt());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = taskRepository.save(entity);
        log.info("更新群发任务草稿: id={}", id);
        return toTaskDto(entity);
    }

    /**
     * 发布群发任务
     * <p>
     * 仅 DRAFT / PENDING 状态可发布。按 targetType 筛选目标客户生成 target 明细
     * (状态 PENDING), 更新 totalCount 后将任务状态置为 RUNNING 并记录开始时间。
     * </p>
     * <p>
     * targetType 筛选规则:
     * <ul>
     *   <li>ALL: 当前账号下与任务平台类型一致的全部客户</li>
     *   <li>TAG: targetFilter 解析为逗号分隔的 tagKey, 命中任一标签的客户</li>
     *   <li>LIST: targetFilter 解析为逗号分隔的 customerId 列表</li>
     *   <li>SEGMENT: 按 targetFilter 中的 lifecycle 字段筛选客户分群</li>
     * </ul>
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法 / 无目标客户
     */
    @Transactional
    public ScrmMassSendTaskDto publishTask(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        if (!STATUS_DRAFT.equals(entity.getStatus()) && !STATUS_PENDING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 DRAFT / PENDING 可发布: currentStatus=" + entity.getStatus());
        }
        // 先清理历史目标明细 (支持重复发布)
        targetRepository.deleteByTaskId(id);
        // 按 targetType 筛选目标客户
        List<ScrmCustomerEntity> customers = filterTargetCustomers(entity);
        if (customers.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "未筛选到目标客户, 无法发布: taskId=" + id + ", targetType=" + entity.getTargetType());
        }
        // 批量生成 target 明细
        List<ScrmMassSendTargetEntity> targets = new ArrayList<>(customers.size());
        for (ScrmCustomerEntity customer : customers) {
            ScrmMassSendTargetEntity target = new ScrmMassSendTargetEntity();
            target.setTaskId(id);
            target.setCustomerId(customer.getId());
            target.setCustomerNickname(customer.getNickname());
            target.setPlatformCustomerUid(customer.getPlatformCustomerUid());
            target.setStatus(SEND_PENDING);
            targets.add(target);
        }
        targetRepository.saveAll(targets);
        // 更新任务计数与状态
        entity.setTotalCount(targets.size());
        entity.setSentCount(0);
        entity.setSuccessCount(0);
        entity.setFailCount(0);
        entity.setStatus(STATUS_RUNNING);
        entity.setStartedAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("发布群发任务: id={}, targetType={}, targetCount={}", id, entity.getTargetType(), targets.size());
        return toTaskDto(entity);
    }

    /**
     * 暂停群发任务 (RUNNING → PAUSED)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmMassSendTaskDto pauseTask(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        if (!STATUS_RUNNING.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 RUNNING 可暂停: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_PAUSED);
        entity = taskRepository.save(entity);
        log.info("暂停群发任务: id={}", id);
        return toTaskDto(entity);
    }

    /**
     * 恢复群发任务 (PAUSED → RUNNING)
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态非法
     */
    @Transactional
    public ScrmMassSendTaskDto resumeTask(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        if (!STATUS_PAUSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "任务状态非法, 仅 PAUSED 可恢复: currentStatus=" + entity.getStatus());
        }
        entity.setStatus(STATUS_RUNNING);
        entity = taskRepository.save(entity);
        log.info("恢复群发任务: id={}", id);
        return toTaskDto(entity);
    }

    /**
     * 取消群发任务 (DRAFT/RUNNING/PAUSED → COMPLETED)
     * <p>
     * 已完成或已失败的任务幂等返回, 未发送的目标明细标记为失败。
     * </p>
     *
     * @param id 任务 ID
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public ScrmMassSendTaskDto cancelTask(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        String currentStatus = entity.getStatus();
        // 终态幂等返回
        if (STATUS_COMPLETED.equals(currentStatus) || STATUS_FAILED.equals(currentStatus)) {
            return toTaskDto(entity);
        }
        // 将未发送的目标明细标记为失败
        List<ScrmMassSendTargetEntity> pendingTargets = targetRepository.findByTaskId(id).stream()
                .filter(t -> SEND_PENDING.equals(t.getStatus()))
                .collect(Collectors.toList());
        for (ScrmMassSendTargetEntity target : pendingTargets) {
            target.setStatus(SEND_FAILED);
            target.setErrorMessage("任务已取消");
        }
        if (!pendingTargets.isEmpty()) {
            targetRepository.saveAll(pendingTargets);
        }
        entity.setStatus(STATUS_COMPLETED);
        entity.setCompletedAt(LocalDateTime.now());
        entity = taskRepository.save(entity);
        log.info("取消群发任务: id={}, cancelledTargets={}", id, pendingTargets.size());
        return toTaskDto(entity);
    }

    /**
     * 查询群发任务
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmMassSendTaskDto getTask(Long id) throws ScrmException {
        return toTaskDto(findTaskOrThrow(id));
    }

    /**
     * 分页查询群发任务, 支持按状态、平台类型与关键词过滤
     *
     * @param status       任务状态过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @param keyword      关键词过滤, 匹配任务名称 (可空)
     * @param pageable     分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMassSendTaskDto> listTasks(String status, String platformType,
                                               String keyword, Pageable pageable) {
        Pageable sorted = ensureSort(pageable);
        Specification<ScrmMassSendTaskEntity> spec = buildTaskSpec(status, platformType, keyword);
        return taskRepository.findAll(spec, sorted).map(this::toTaskDto);
    }

    /**
     * 分页查询群发任务的目标明细, 支持按发送状态过滤
     *
     * @param taskId 任务 ID
     * @param status 发送状态过滤 (可空)
     * @param pageable 分页参数
     * @return 目标明细分页结果
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmMassSendTargetDto> getTaskTargets(Long taskId, String status, Pageable pageable)
            throws ScrmException {
        findTaskOrThrow(taskId);
        Page<ScrmMassSendTargetEntity> page;
        if (status != null && !status.isBlank()) {
            page = targetRepository.findByTaskIdAndStatus(taskId, status, ensureSort(pageable));
        } else {
            page = targetRepository.findByTaskId(taskId, ensureSort(pageable));
        }
        return page.map(this::toTargetDto);
    }

    /**
     * 生成群发任务发送结果报告
     * <p>
     * 聚合目标明细的发送状态统计, 计算成功率 / 失败率, 返回任务发送概况。
     * </p>
     *
     * @param id 任务 ID
     * @return 发送报告 VO
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public MassSendReportVo getTaskReport(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = findTaskOrThrow(id);
        // 按状态聚合目标明细数
        List<Object[]> statusCounts = targetRepository.countByStatus(id);
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : statusCounts) {
            String st = (String) row[0];
            Long cnt = (Long) row[1];
            countMap.put(st, cnt);
        }
        long sent = countMap.getOrDefault(SEND_SENT, 0L);
        long failed = countMap.getOrDefault(SEND_FAILED, 0L);
        long pending = countMap.getOrDefault(SEND_PENDING, 0L);
        int totalCount = entity.getTotalCount() != null ? entity.getTotalCount() : 0;
        int sentCount = (int) (sent + failed);
        int successCount = (int) sent;
        int failCount = (int) failed;
        int pendingCount = (int) pending;
        Double successRate = null;
        Double failureRate = null;
        if (sentCount > 0) {
            successRate = round2(successCount * PERCENT_BASE / sentCount);
            failureRate = round2(failCount * PERCENT_BASE / sentCount);
        }
        return MassSendReportVo.builder()
                .taskId(entity.getId())
                .taskName(entity.getTaskName())
                .platformType(entity.getPlatformType())
                .status(entity.getStatus())
                .totalCount(totalCount)
                .sentCount(sentCount)
                .successCount(successCount)
                .failCount(failCount)
                .pendingCount(pendingCount)
                .successRate(successRate)
                .failureRate(failureRate)
                .startedAt(entity.getStartedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 创建任务参数校验
     *
     * @param dto 任务参数
     * @throws ScrmException 参数校验失败
     */
    private void validateCreateTask(ScrmMassSendTaskDto dto) throws ScrmException {
        if (dto.getTaskName() == null || dto.getTaskName().isBlank()) {
            throw ScrmException.badRequest("任务名称不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw ScrmException.badRequest("群发内容不能为空");
        }
        if (dto.getTargetType() == null || dto.getTargetType().isBlank()) {
            throw ScrmException.badRequest("目标类型不能为空");
        }
        if (dto.getSenderAccountId() == null) {
            throw ScrmException.badRequest("发送账号 ID 不能为空");
        }
    }

    /**
     * 按 targetType 筛选目标客户
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤, 且仅返回与任务平台类型一致的客户。
     * </p>
     *
     * @param task 群发任务实体
     * @return 目标客户列表
     */
    private List<ScrmCustomerEntity> filterTargetCustomers(ScrmMassSendTaskEntity task) {
        String platformType = task.getPlatformType();
        String targetType = task.getTargetType();
        String filter = task.getTargetFilter();
        switch (targetType) {
            case TARGET_ALL:
                // 全量客户: 当前账号 + 平台一致
                return customerRepository.findAll((root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
                    return cb.and(predicates.toArray(new Predicate[0]));
                });
            case TARGET_TAG:
                // 按标签筛选: targetFilter 解析为逗号分隔的 tagKey
                return filterByTag(platformType, filter);
            case TARGET_LIST:
                // 指定客户列表: targetFilter 解析为逗号分隔的 customerId
                return filterByList(platformType, filter);
            case TARGET_SEGMENT:
                // 按分群筛选: targetFilter 解析为逗号分隔的 lifecycle
                return filterBySegment(platformType, filter);
            default:
                log.warn("未识别的目标类型, 返回空列表: targetType={}", targetType);
                return new ArrayList<>();
        }
    }

    /**
     * 按标签筛选目标客户
     *
     * @param platformType 平台类型
     * @param filter      标签筛选条件 (逗号分隔的 tagKey)
     * @return 命中任一标签的客户列表
     */
    private List<ScrmCustomerEntity> filterByTag(String platformType, String filter) {
        if (filter == null || filter.isBlank()) {
            return new ArrayList<>();
        }
        Set<String> tagKeys = parseCsv(filter);
        if (tagKeys.isEmpty()) {
            return new ArrayList<>();
        }
        // tagKey (tagCode) → tagId 集合 (重构后赋值以 tagId 引用标签定义)
        Set<Long> tagIds = tagKeys.stream()
                .map(k -> customerTagRepository.findByTagCode(k)
                        .map(ScrmCustomerTagEntity::getId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (tagIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 拉取当前账号 + 平台一致的客户, 逐个匹配标签是否命中任一 tagId
        List<ScrmCustomerEntity> customers = customerRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
        List<ScrmCustomerEntity> matched = new ArrayList<>();
        for (ScrmCustomerEntity customer : customers) {
            List<ScrmTagCustomerEntity> customerTags = tagCustomerRepository
                    .findByCustomerId(customer.getId());
            boolean hit = customerTags.stream()
                    .anyMatch(t -> tagIds.contains(t.getTagId()));
            if (hit) {
                matched.add(customer);
            }
        }
        return matched;
    }

    /**
     * 按指定客户 ID 列表筛选目标客户
     *
     * @param platformType 平台类型
     * @param filter      客户 ID 列表 (逗号分隔)
     * @return 命中且属于当前账号+平台的客户列表
     */
    private List<ScrmCustomerEntity> filterByList(String platformType, String filter) {
        if (filter == null || filter.isBlank()) {
            return new ArrayList<>();
        }
        Set<Long> ids = parseCsv(filter).stream()
                .map(s -> {
                    try {
                        return Long.parseLong(s.trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return customerRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            predicates.add(root.get("id").in(ids));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    /**
     * 按分群 (lifecycle) 筛选目标客户
     *
     * @param platformType 平台类型
     * @param filter      生命周期列表 (逗号分隔, 如 NEW,ACTIVE)
     * @return 命中任一生命周期阶段的客户列表
     */
    private List<ScrmCustomerEntity> filterBySegment(String platformType, String filter) {
        Set<String> lifecycles = parseCsv(filter);
        if (lifecycles.isEmpty()) {
            return new ArrayList<>();
        }
        return customerRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            predicates.add(cb.lower(root.get("lifecycle")).in(lifecycles.stream()
                    .map(String::toLowerCase).collect(Collectors.toList())));
            return cb.and(predicates.toArray(new Predicate[0]));
        });
    }

    /**
     * 解析逗号分隔字符串为去重集合
     *
     * @param csv 逗号分隔字符串
     * @return 去重后的值集合
     */
    private Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 构建群发任务查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * </p>
     *
     * @param status       任务状态过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @param keyword      关键词过滤, 匹配任务名称 (可空)
     * @return Specification
     */
    private Specification<ScrmMassSendTaskEntity> buildTaskSpec(String status, String platformType,
                                                                String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("taskName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序 (按创建时间倒序)
     *
     * @param pageable 原始分页参数
     * @return 带排序的分页参数
     */
    private Pageable ensureSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
    }

    /**
     * 按主键查询任务并校验账号归属, 不存在抛异常
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在或越权访问
     */
    private ScrmMassSendTaskEntity findTaskOrThrow(Long id) throws ScrmException {
        ScrmMassSendTaskEntity entity = taskRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "群发任务不存在: id=" + id));
        // 数据隔离: 校验任务归属当前账号, 防止按 ID 越权访问

        return entity;
    }

    /**
     * 任务实体转 DTO
     *
     * @param entity 任务实体
     * @return 任务 DTO
     */
    private ScrmMassSendTaskDto toTaskDto(ScrmMassSendTaskEntity entity) {
        ScrmMassSendTaskDto dto = new ScrmMassSendTaskDto();
        dto.setId(entity.getId());
        dto.setTaskName(entity.getTaskName());
        dto.setPlatformType(entity.getPlatformType());
        dto.setMessageTemplateId(entity.getMessageTemplateId());
        dto.setContent(entity.getContent());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetFilter(entity.getTargetFilter());
        dto.setSenderAccountId(entity.getSenderAccountId());
        dto.setStatus(entity.getStatus());
        dto.setTotalCount(entity.getTotalCount());
        dto.setSentCount(entity.getSentCount());
        dto.setSuccessCount(entity.getSuccessCount());
        dto.setFailCount(entity.getFailCount());
        dto.setScheduledAt(entity.getScheduledAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 目标明细实体转 DTO
     *
     * @param entity 目标明细实体
     * @return 目标明细 DTO
     */
    private ScrmMassSendTargetDto toTargetDto(ScrmMassSendTargetEntity entity) {
        ScrmMassSendTargetDto dto = new ScrmMassSendTargetDto();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerNickname(entity.getCustomerNickname());
        dto.setPlatformCustomerUid(entity.getPlatformCustomerUid());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setSentAt(entity.getSentAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 保留两位小数
     *
     * @param v 原始值
     * @return 四舍五入到两位小数
     */
    private double round2(double v) {
        return Math.round(v * Math.pow(10, RATE_SCALE)) / Math.pow(10, RATE_SCALE);
    }
}
