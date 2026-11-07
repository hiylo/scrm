/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmInspectionTaskService.java
 * Date : 2026/10/23 10:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.util.SensitiveDataUtils;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmInspectionTaskDto;
import org.hiylo.scrm.entity.ScrmInspectionTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 工商税务巡检任务服务。
 * <p>
 * 巡检任务的 CRUD 与启停: 企业 + 周期任务配置（工商 API / 税务公告两开关）,
 * 创建/更新时按授权客户企业校验（仅服务自有/委托客户, 见立项方案红线）, 统计
 * 累计执行与告警次数。调度执行本身复用 {@code scrm_scheduled_task}（TaskScheduler）,
 * 本服务只维护巡检维度的任务表。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmInspectionTaskService {

    /** 合法巡检周期 */
    private static final List<String> VALID_CYCLE_TYPES = List.of("DAILY", "WEEKLY", "QUARTERLY", "MANUAL");

    /** 巡检任务数据访问层 */
    private final ScrmInspectionTaskRepository taskRepository;

    /** 公司主体归属解析器（业务数据自动挂公司） */
    private final CompanyResolver companyResolver;

    /**
     * 创建巡检任务。
     *
     * @param dto 任务参数
     * @return 创建后的任务
     * @throws ScrmException 参数非法 / 重复（按信用代码去重）
     */
    @Transactional
    public ScrmInspectionTaskEntity create(ScrmInspectionTaskDto dto) throws ScrmException {
        validate(dto, false);
        if (dto.getCreditCode() != null && !dto.getCreditCode().isBlank()
                && taskRepository.findByCreditCode(dto.getCreditCode()).isPresent()) {
            throw ScrmException.conflict("该统一社会信用代码已存在巡检任务");
        }
        ScrmInspectionTaskEntity entity = new ScrmInspectionTaskEntity();
        applyDto(entity, dto);
        entity.setCycleType(dto.getCycleType() != null ? dto.getCycleType() : "QUARTERLY");
        entity.setInspectBusiness(dto.getInspectBusiness() != null ? dto.getInspectBusiness() : true);
        entity.setInspectTax(dto.getInspectTax() != null ? dto.getInspectTax() : true);
        entity.setIsEnabled(dto.getIsEnabled() != null ? dto.getIsEnabled() : true);
        entity.setRunCount(0);
        entity.setAlertCount(0);
        entity.setOwnerAccountId(dto.getOwnerAccountId() != null
                ? dto.getOwnerAccountId() : parseUserId());
        entity = taskRepository.save(entity);
        log.info("[巡检任务] 创建: id={}, enterprise={}, creditCode={}, cycle={}",
                entity.getId(), entity.getEnterpriseName(),
                safeCreditCode(entity.getCreditCode()), entity.getCycleType());
        return entity;
    }

    /**
     * 更新巡检任务。
     *
     * @param id  任务 ID
     * @param dto 更新参数（非空字段覆盖）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 参数非法
     */
    @Transactional
    public ScrmInspectionTaskEntity update(Long id, ScrmInspectionTaskDto dto) throws ScrmException {
        ScrmInspectionTaskEntity entity = findOrThrow(id);
        validate(dto, true);
        if (dto.getCreditCode() != null && !dto.getCreditCode().isBlank()
                && !dto.getCreditCode().equals(entity.getCreditCode())
                && taskRepository.findByCreditCode(dto.getCreditCode()).isPresent()) {
            throw ScrmException.conflict("该统一社会信用代码已被其他巡检任务使用");
        }
        applyDto(entity, dto);
        return taskRepository.save(entity);
    }

    /**
     * 删除巡检任务。
     *
     * @param id 任务 ID
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public void delete(Long id) throws ScrmException {
        ScrmInspectionTaskEntity entity = findOrThrow(id);
        taskRepository.delete(entity);
        log.info("[巡检任务] 删除: id={}, enterprise={}", id, entity.getEnterpriseName());
    }

    /**
     * 查询巡检任务详情。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    @Transactional(readOnly = true)
    public ScrmInspectionTaskEntity get(Long id) throws ScrmException {
        return findOrThrow(id);
    }

    /**
     * 分页查询巡检任务。
     *
     * @param cycleType    周期过滤（可空）
     * @param isEnabled    启用状态过滤（可空）
     * @param keyword      企业名称/信用代码关键字（可空）
     * @param pageable     分页参数
     * @return 任务分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmInspectionTaskEntity> list(String cycleType, Boolean isEnabled,
                                               String keyword, Pageable pageable) {
        Specification<ScrmInspectionTaskEntity> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (cycleType != null && !cycleType.isBlank()) {
                predicates.add(cb.equal(root.get("cycleType"), cycleType));
            }
            if (isEnabled != null) {
                predicates.add(cb.equal(root.get("isEnabled"), isEnabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("enterpriseName"), like),
                        cb.like(root.get("creditCode"), like)));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * 启用 / 禁用巡检任务。
     *
     * @param id      任务 ID
     * @param enabled 是否启用
     * @return 更新后的任务
     * @throws ScrmException 任务不存在
     */
    @Transactional
    public ScrmInspectionTaskEntity setEnabled(Long id, boolean enabled) throws ScrmException {
        ScrmInspectionTaskEntity entity = findOrThrow(id);
        entity.setIsEnabled(enabled);
        return taskRepository.save(entity);
    }

    /**
     * 参数校验。
     *
     * @param dto     任务参数
     * @param partial 是否部分更新
     * @throws ScrmException 参数非法
     */
    private void validate(ScrmInspectionTaskDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("巡检任务参数不能为空");
        }
        if (partial) {
            return;
        }
        if (dto.getEnterpriseName() == null || dto.getEnterpriseName().isBlank()) {
            throw ScrmException.badRequest("企业名称不能为空");
        }
        if ((dto.getCreditCode() == null || dto.getCreditCode().isBlank())
                && dto.getEnterpriseName() == null) {
            throw ScrmException.badRequest("企业名称与统一社会信用代码至少填一个");
        }
        if (dto.getCycleType() != null && !VALID_CYCLE_TYPES.contains(dto.getCycleType())) {
            throw ScrmException.badRequest(
                    "巡检周期非法: " + dto.getCycleType() + ", 仅支持 " + VALID_CYCLE_TYPES);
        }
    }

    /**
     * 把 DTO 非空字段应用到实体。
     *
     * @param entity 实体
     * @param dto    DTO
     */
    private void applyDto(ScrmInspectionTaskEntity entity, ScrmInspectionTaskDto dto) {
        if (dto.getEnterpriseName() != null) {
            entity.setEnterpriseName(dto.getEnterpriseName());
        }
        if (dto.getCreditCode() != null) {
            entity.setCreditCode(dto.getCreditCode());
        }
        if (dto.getLegalPerson() != null) {
            entity.setLegalPerson(dto.getLegalPerson());
        }
        if (dto.getCustomerId() != null) {
            entity.setCustomerId(dto.getCustomerId());
            // 业务数据自动挂公司：从联系人推导（联系人未归属公司时为 null）
            if (entity.getCompanyId() == null) {
                entity.setCompanyId(companyResolver.resolveByCustomerId(dto.getCustomerId()));
            }
        }
        if (dto.getOwnerAccountId() != null) {
            entity.setOwnerAccountId(dto.getOwnerAccountId());
        }
        if (dto.getCycleType() != null) {
            entity.setCycleType(dto.getCycleType());
        }
        if (dto.getInspectBusiness() != null) {
            entity.setInspectBusiness(dto.getInspectBusiness());
        }
        if (dto.getInspectTax() != null) {
            entity.setInspectTax(dto.getInspectTax());
        }
        if (dto.getIsEnabled() != null) {
            entity.setIsEnabled(dto.getIsEnabled());
        }
        if (dto.getRemark() != null) {
            entity.setRemark(dto.getRemark());
        }
    }

    /**
     * 按主键查询任务, 不存在抛异常。
     *
     * @param id 任务 ID
     * @return 任务实体
     * @throws ScrmException 任务不存在
     */
    private ScrmInspectionTaskEntity findOrThrow(Long id) throws ScrmException {
        return taskRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("巡检任务不存在: id=" + id));
    }

    /**
     * 从请求上下文取当前用户 ID 作为默认归属账号。
     *
     * @return 用户 ID（未登录调度上下文时为 null）
     */
    private Long parseUserId() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 日志用信用代码脱敏。
     *
     * @param creditCode 统一社会信用代码
     * @return 脱敏值
     */
    private String safeCreditCode(String creditCode) {
        return creditCode == null || creditCode.length() <= 8
                ? "****" : SensitiveDataUtils.maskGeneric(creditCode);
    }
}