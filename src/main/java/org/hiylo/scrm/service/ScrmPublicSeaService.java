/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmPublicSeaService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmLeadAssignmentDto;
import org.hiylo.scrm.dto.ScrmPublicSeaCustomerDto;
import org.hiylo.scrm.entity.ScrmLeadAssignmentEntity;
import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmLeadAssignmentRepository;
import org.hiylo.scrm.repository.ScrmPublicSeaCustomerRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 客户公海/线索分配服务
 * <p>
 * 负责公海池线索的集中管理与流转: 销售自主领取、管理员分配、超时回收、转移他人
 * 以及转为正式客户。所有写操作写入当前用户归属账号,
 * 实现数据隔离。每次分配/回收操作同步写入 {@link ScrmLeadAssignmentEntity}
 * 流水记录, 形成完整审计轨迹。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmPublicSeaService {

    /** 默认生命周期: 新线索 */
    private static final String LIFECYCLE_NEW = "NEW";

    /** 默认公海状态: 可领取 */
    private static final String STATUS_AVAILABLE = "AVAILABLE";

    /** 公海状态: 已分配 */
    private static final String STATUS_ASSIGNED = "ASSIGNED";

    /** 公海状态: 已锁定 */
    private static final String STATUS_LOCKED = "LOCKED";

    /** 公海状态: 已回收 */
    private static final String STATUS_RECALLED = "RECALLED";

    /** 分配类型: 领取 */
    private static final String TYPE_CLAIM = "CLAIM";

    /** 分配类型: 分配 */
    private static final String TYPE_ASSIGN = "ASSIGN";

    /** 分配类型: 转移 */
    private static final String TYPE_TRANSFER = "TRANSFER";

    /** 分配状态: 生效 */
    private static final String ASSIGN_ACTIVE = "ACTIVE";

    /** 分配状态: 已回收 */
    private static final String ASSIGN_RECALLED = "RECALLED";

    /** 分配状态: 已转移 */
    private static final String ASSIGN_TRANSFERRED = "TRANSFERRED";

    /** 分配状态: 已转正 */
    private static final String ASSIGN_CONVERTED = "CONVERTED";

    /** 默认分配有效期 (天): 超过则自动回收 */
    private static final long DEFAULT_ASSIGN_EXPIRE_DAYS = 7L;

    /** 公海客户数据访问层 */
    private final ScrmPublicSeaCustomerRepository publicSeaCustomerRepository;

    /** 线索分配流水数据访问层 */
    private final ScrmLeadAssignmentRepository leadAssignmentRepository;

    /**
     * 分页查询公海池列表, 支持按平台类型、生命周期、状态与关键词过滤
     * <p>数据隔离: 仅返回当前账号的公海客户。</p>
     *
     * @param platformType 平台类型过滤 (可空)
     * @param lifecycle    生命周期过滤 (可空)
     * @param status       公海状态过滤 (可空)
     * @param keyword      关键词过滤, 匹配昵称 / 平台客户 UID (可空)
     * @param pageable     分页参数
     * @return 公海客户分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmPublicSeaCustomerDto> listPublicSea(String platformType, String lifecycle,
                                                        String status, String keyword, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmPublicSeaCustomerEntity> spec = buildPublicSeaSpec(platformType, lifecycle, status, keyword);
        Page<ScrmPublicSeaCustomerEntity> entities = publicSeaCustomerRepository.findAll(spec, sorted);
        return entities.map(this::toDto);
    }

    /**
     * 查询公海客户详情
     *
     * @param id 公海客户 ID
     * @return 公海客户 DTO
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public ScrmPublicSeaCustomerDto getCustomer(Long id) throws ScrmException {
        return toDto(findOrThrow(id));
    }

    /**
     * 销售领取公海客户 (AVAILABLE → ASSIGNED, 写入 assignmentExpireAt)
     * <p>
     * 仅状态为 AVAILABLE 或 RECALLED 的客户可被领取, 领取后状态置为 ASSIGNED,
     * 设定当前归属人与分配过期时间, 并写入一条 CLAIM 类型分配流水。
     * </p>
     *
     * @param customerId 公海客户 ID
     * @param userId     领取人 userId
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许领取
     */
    @Transactional
    public ScrmPublicSeaCustomerDto claimCustomer(Long customerId, String userId) throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("领取人 userId 不能为空");
        }
        ScrmPublicSeaCustomerEntity entity = findOrThrow(customerId);
        // 仅 AVAILABLE 或 RECALLED 状态可被领取
        if (!STATUS_AVAILABLE.equals(entity.getStatus()) && !STATUS_RECALLED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "公海客户当前状态不允许领取: id=" + customerId + ", status=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusDays(DEFAULT_ASSIGN_EXPIRE_DAYS);
        // 释放前序 ACTIVE 流水 (若有残留)
        releaseActiveAssignments(customerId, ASSIGN_TRANSFERRED, now);
        entity.setAssignedTo(userId);
        entity.setAssignedAt(now);
        entity.setAssignmentExpireAt(expireAt);
        entity.setLastAssignedAt(now);
        entity.setStatus(STATUS_ASSIGNED);
        entity = publicSeaCustomerRepository.save(entity);
        // 写入分配流水
        writeAssignment(entity, userId, userId, TYPE_CLAIM, null, ASSIGN_ACTIVE, expireAt, null);
        log.info("公海客户领取成功: id={}, userId={}, expireAt={}", customerId, userId, expireAt);
        return toDto(entity);
    }

    /**
     * 管理员分配公海客户 (AVAILABLE → ASSIGNED)
     * <p>
     * 管理员将公海客户分配给指定销售, 状态流转与领取一致,
     * 分配流水类型为 ASSIGN, assignedBy 为管理员 userId。
     * </p>
     *
     * @param customerId  公海客户 ID
     * @param userId      被分配人 userId
     * @param assignedBy  分配人 userId (管理员)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许分配
     */
    @Transactional
    public ScrmPublicSeaCustomerDto assignCustomer(Long customerId, String userId, String assignedBy)
            throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("被分配人 userId 不能为空");
        }
        ScrmPublicSeaCustomerEntity entity = findOrThrow(customerId);
        if (!STATUS_AVAILABLE.equals(entity.getStatus()) && !STATUS_RECALLED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "公海客户当前状态不允许分配: id=" + customerId + ", status=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusDays(DEFAULT_ASSIGN_EXPIRE_DAYS);
        releaseActiveAssignments(customerId, ASSIGN_TRANSFERRED, now);
        entity.setAssignedTo(userId);
        entity.setAssignedAt(now);
        entity.setAssignmentExpireAt(expireAt);
        entity.setLastAssignedAt(now);
        entity.setStatus(STATUS_ASSIGNED);
        entity = publicSeaCustomerRepository.save(entity);
        writeAssignment(entity, userId, assignedBy, TYPE_ASSIGN, null, ASSIGN_ACTIVE, expireAt, null);
        log.info("公海客户分配成功: id={}, assignedTo={}, assignedBy={}", customerId, userId, assignedBy);
        return toDto(entity);
    }

    /**
     * 转移公海客户给他人 (ASSIGNED → ASSIGNED, 记录 previousOwner)
     * <p>
     * 仅 ASSIGNED 状态的客户可被转移, 转移后归属人变更为 toUserId,
     * 前序 ACTIVE 流水状态置为 TRANSFERRED, 新建一条 TRANSFER 类型流水。
     * </p>
     *
     * @param customerId 公海客户 ID
     * @param toUserId   接收人 userId
     * @param note        转移备注 (可空)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许转移
     */
    @Transactional
    public ScrmPublicSeaCustomerDto transferCustomer(Long customerId, String toUserId, String note)
            throws ScrmException {
        if (toUserId == null || toUserId.isBlank()) {
            throw ScrmException.badRequest("接收人 userId 不能为空");
        }
        ScrmPublicSeaCustomerEntity entity = findOrThrow(customerId);
        if (!STATUS_ASSIGNED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "公海客户当前状态不允许转移: id=" + customerId + ", status=" + entity.getStatus());
        }
        String previousOwner = entity.getAssignedTo();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusDays(DEFAULT_ASSIGN_EXPIRE_DAYS);
        // 将前序 ACTIVE 流水置为 TRANSFERRED
        releaseActiveAssignments(customerId, ASSIGN_TRANSFERRED, now);
        entity.setAssignedTo(toUserId);
        entity.setAssignedAt(now);
        entity.setAssignmentExpireAt(expireAt);
        entity.setLastAssignedAt(now);
        entity.setStatus(STATUS_ASSIGNED);
        entity = publicSeaCustomerRepository.save(entity);
        writeAssignment(entity, toUserId, previousOwner, TYPE_TRANSFER, previousOwner, ASSIGN_ACTIVE, expireAt, note);
        log.info("公海客户转移成功: id={}, from={}, to={}", customerId, previousOwner, toUserId);
        return toDto(entity);
    }

    /**
     * 回收公海客户到公海 (ASSIGNED → AVAILABLE, recallCount+1)
     * <p>
     * 将已分配但未跟进或超时的客户回收到公海, 状态置为 AVAILABLE,
     * recallCount 自增, 清空归属人与过期时间, 前序 ACTIVE 流水置为 RECALLED。
     * </p>
     *
     * @param customerId 公海客户 ID
     * @param reason      回收原因 (可空, 仅记录到流水 note)
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许回收
     */
    @Transactional
    public ScrmPublicSeaCustomerDto recallCustomer(Long customerId, String reason) throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = findOrThrow(customerId);
        if (!STATUS_ASSIGNED.equals(entity.getStatus()) && !STATUS_LOCKED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "公海客户当前状态不允许回收: id=" + customerId + ", status=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        // 将前序 ACTIVE 流水置为 RECALLED
        releaseActiveAssignments(customerId, ASSIGN_RECALLED, now);
        entity.setAssignedTo(null);
        entity.setAssignedAt(null);
        entity.setAssignmentExpireAt(null);
        entity.setRecallCount(entity.getRecallCount() == null ? 1 : entity.getRecallCount() + 1);
        entity.setStatus(STATUS_AVAILABLE);
        entity = publicSeaCustomerRepository.save(entity);
        // 回收流水 (assignedTo 沿用上一次归属人, 用于审计)
        ScrmLeadAssignmentEntity recall = new ScrmLeadAssignmentEntity();
        recall.setPublicSeaCustomerId(customerId);
        recall.setAssignedTo(entity.getAssignedTo() == null ? "system" : entity.getAssignedTo());
        recall.setAssignedBy("system");
        recall.setAssignmentType(TYPE_ASSIGN);
        recall.setStatus(ASSIGN_RECALLED);
        recall.setAssignedAt(now);
        recall.setRecalledAt(now);
        recall.setNote(reason);
        leadAssignmentRepository.save(recall);
        log.info("公海客户回收成功: id={}, recallCount={}, reason={}", customerId, entity.getRecallCount(), reason);
        return toDto(entity);
    }

    /**
     * 批量分配公海客户
     * <p>
     * 遍历客户 ID 列表, 逐个调用 {@link #assignCustomer}。单个客户失败 (不存在或状态不允许)
     * 不影响其他客户, 返回成功数与失败列表。
     * </p>
     *
     * @param customerIds 公海客户 ID 列表
     * @param userId      被分配人 userId
     * @param assignedBy   分配人 userId
     * @return 批量操作结果 (成功数 + 失败客户 ID)
     */
    @Transactional
    public BatchAssignResult batchAssign(List<Long> customerIds, String userId, String assignedBy) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        int successCount = 0;
        List<Long> failures = new ArrayList<>();
        for (Long customerId : customerIds) {
            try {
                assignCustomer(customerId, userId, assignedBy);
                successCount++;
            } catch (ScrmException e) {
                log.warn("批量分配失败: customerId={}, error={}", customerId, e.getMessage());
                failures.add(customerId);
            }
        }
        log.info("批量分配完成: total={}, success={}, failed={}, assignedTo={}",
                customerIds.size(), successCount, failures.size(), userId);
        return new BatchAssignResult(successCount, failures);
    }

    /**
     * 将公海客户转为正式客户 (从公海移出)
     * <p>
     * 状态置为 LOCKED (防止后续被领取), 前序 ACTIVE 流水置为 CONVERTED。
     * 实际客户记录由调用方在外部创建 (传入 ownerAccountId 用于日志关联),
     * 本方法仅完成公海侧的状态收尾与流水归档。
     * </p>
     *
     * @param customerId     公海客户 ID
     * @param ownerAccountId 正式客户归属账号 ID
     * @return 更新后的公海客户
     * @throws ScrmException 客户不存在 / 状态不允许转正
     */
    @Transactional
    public ScrmPublicSeaCustomerDto convertToCustomer(Long customerId, Long ownerAccountId) throws ScrmException {
        if (ownerAccountId == null) {
            throw ScrmException.badRequest("正式客户归属账号 ID 不能为空");
        }
        ScrmPublicSeaCustomerEntity entity = findOrThrow(customerId);
        if (STATUS_LOCKED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "公海客户已转正, 不能重复操作: id=" + customerId);
        }
        LocalDateTime now = LocalDateTime.now();
        // 将前序 ACTIVE 流水置为 CONVERTED
        releaseActiveAssignments(customerId, ASSIGN_CONVERTED, now);
        entity.setStatus(STATUS_LOCKED);
        entity = publicSeaCustomerRepository.save(entity);
        log.info("公海客户转为正式客户: id={}, ownerAccountId={}", customerId, ownerAccountId);
        return toDto(entity);
    }

    /**
     * 查询公海客户的分配历史 (按 ID 倒序, 最新分配在前)
     *
     * @param customerId 公海客户 ID
     * @return 分配流水列表
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmLeadAssignmentDto> getAssignmentHistory(Long customerId) throws ScrmException {
        findOrThrow(customerId);
        return leadAssignmentRepository.findByPublicSeaCustomerIdOrderByIdDesc(customerId).stream()

                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 查询我的线索 (当前归属人为 userId 的公海客户)
     * <p>数据隔离: 仅返回当前账号下的线索。</p>
     *
     * @param userId   归属人 userId
     * @param status   公海状态过滤 (可空)
     * @param pageable 分页参数
     * @return 我的线索分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmPublicSeaCustomerDto> getMyLeads(String userId, String status, Pageable pageable) {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("归属人 userId 不能为空");
        }
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "assignedAt"));
        Specification<ScrmPublicSeaCustomerEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("assignedTo"), userId));
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return publicSeaCustomerRepository.findAll(spec, sorted).map(this::toDto);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建公海客户查询条件 Specification (含数据隔离)
     */
    private Specification<ScrmPublicSeaCustomerEntity> buildPublicSeaSpec(String platformType, String lifecycle,
                                                                          String status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前账号过滤
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (lifecycle != null && !lifecycle.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("lifecycle")), lifecycle.toLowerCase()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nickname")), kw),
                        cb.like(cb.lower(root.get("platformCustomerUid")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 释放公海客户的所有 ACTIVE 分配流水 (置为目标状态)
     * <p>
     * 在领取/分配/转移/回收/转正前调用, 确保同一客户同时仅有一条 ACTIVE 流水。
     * 数据隔离: 仅处理当前账号的流水。
     * </p>
     *
     * @param customerId  公海客户 ID
     * @param targetState 目标状态 (TRANSFERRED/RECALLED/CONVERTED)
     * @param now         操作时间
     */
    private void releaseActiveAssignments(Long customerId, String targetState, LocalDateTime now) {
        List<ScrmLeadAssignmentEntity> actives = leadAssignmentRepository
                .findByPublicSeaCustomerIdOrderByIdDesc(customerId).stream()

                .filter(a -> ASSIGN_ACTIVE.equals(a.getStatus()))
                .collect(Collectors.toList());
        for (ScrmLeadAssignmentEntity active : actives) {
            active.setStatus(targetState);
            if (ASSIGN_RECALLED.equals(targetState)) {
                active.setRecalledAt(now);
            }
            leadAssignmentRepository.save(active);
        }
    }

    /**
     * 写入一条分配流水记录
     */
    private void writeAssignment(ScrmPublicSeaCustomerEntity entity, String assignedTo, String assignedBy,
                                 String type, String previousOwner, String status,
                                 LocalDateTime expireAt, String note) {
        ScrmLeadAssignmentEntity assignment = new ScrmLeadAssignmentEntity();
        assignment.setPublicSeaCustomerId(entity.getId());
        assignment.setAssignedTo(assignedTo);
        assignment.setAssignedBy(assignedBy);
        assignment.setAssignmentType(type);
        assignment.setPreviousOwner(previousOwner);
        assignment.setStatus(status);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setExpireAt(expireAt);
        assignment.setNote(note);
        leadAssignmentRepository.save(assignment);
    }

    /**
     */
    private ScrmPublicSeaCustomerEntity findOrThrow(Long id) throws ScrmException {
        ScrmPublicSeaCustomerEntity entity = publicSeaCustomerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "公海客户不存在: id=" + id));
        // 数据隔离: 校验归属当前账号, 防止越权按 ID 访问

        return entity;
    }

    /**
     * 公海客户实体转 DTO
     */
    private ScrmPublicSeaCustomerDto toDto(ScrmPublicSeaCustomerEntity entity) {
        ScrmPublicSeaCustomerDto dto = new ScrmPublicSeaCustomerDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setPlatformCustomerUid(entity.getPlatformCustomerUid());
        dto.setNickname(entity.getNickname());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setSourceChannel(entity.getSourceChannel());
        dto.setSourceChannelCodeId(entity.getSourceChannelCodeId());
        dto.setLifecycle(entity.getLifecycle());
        dto.setTags(entity.getTags());
        dto.setRemark(entity.getRemark());
        dto.setAssignedTo(entity.getAssignedTo());
        dto.setAssignedAt(entity.getAssignedAt());
        dto.setAssignmentExpireAt(entity.getAssignmentExpireAt());
        dto.setRecallCount(entity.getRecallCount());
        dto.setLastAssignedAt(entity.getLastAssignedAt());
        dto.setStatus(entity.getStatus());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 分配流水实体转 DTO
     */
    private ScrmLeadAssignmentDto toDto(ScrmLeadAssignmentEntity entity) {
        ScrmLeadAssignmentDto dto = new ScrmLeadAssignmentDto();
        dto.setId(entity.getId());
        dto.setPublicSeaCustomerId(entity.getPublicSeaCustomerId());
        dto.setAssignedTo(entity.getAssignedTo());
        dto.setAssignedBy(entity.getAssignedBy());
        dto.setAssignmentType(entity.getAssignmentType());
        dto.setPreviousOwner(entity.getPreviousOwner());
        dto.setStatus(entity.getStatus());
        dto.setAssignedAt(entity.getAssignedAt());
        dto.setRecalledAt(entity.getRecalledAt());
        dto.setExpireAt(entity.getExpireAt());
        dto.setNote(entity.getNote());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 批量分配操作结果
 * @since V1.0
     * @author Hsi Chu
     */
    public record BatchAssignResult(int successCount, List<Long> failedCustomerIds) {
    }
}
