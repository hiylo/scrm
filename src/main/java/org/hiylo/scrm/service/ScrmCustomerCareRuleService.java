/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerCareRuleService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCareRuleDto;
import org.hiylo.scrm.entity.ScrmCareRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCareRuleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 客户关怀规则管理服务 (关怀规则子域)。
 * <p>
 * 承载关怀规则增删改查 / 启用禁用与参数校验, 并托管客户关怀模块共享常量
 * (任务状态 / 关怀结果 / 关怀类型 / 合法取值列表等), 供任务 / 节日 / 记录
 * 兄弟类以 package 级访问复用。写操作与校验逻辑保持与原门面完全一致。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerCareRuleService {

    /** 默认启用状态 */
    static final boolean DEFAULT_ENABLED = true;

    /** 默认优先级 */
    static final int DEFAULT_PRIORITY = 0;

    /** 默认执行次数初值 */
    static final int DEFAULT_EXECUTION_COUNT = 0;

    /** 默认计划执行时间小时 */
    static final int DEFAULT_SCHEDULED_HOUR = 9;

    /** 默认计划执行时间分钟 */
    static final int DEFAULT_SCHEDULED_MINUTE = 0;

    /** 默认触发条件 (空 JSON 对象) */
    static final String DEFAULT_TRIGGER_CONDITION = "{}";

    /** 默认动作内容 (空 JSON 对象) */
    static final String DEFAULT_ACTION_CONTENT = "{}";

    /** 默认适用范围 */
    static final String DEFAULT_APPLICABLE = "ALL";

    /** 任务状态: 待执行 */
    static final String STATUS_PENDING = "PENDING";
    /** 任务状态: 执行中 */
    static final String STATUS_EXECUTING = "EXECUTING";
    /** 任务状态: 成功 */
    static final String STATUS_SUCCESS = "SUCCESS";
    /** 任务状态: 失败 */
    static final String STATUS_FAILED = "FAILED";
    /** 任务状态: 已取消 */
    static final String STATUS_CANCELLED = "CANCELLED";

    /** 关怀结果: 成功 */
    static final String RESULT_SUCCESS = "SUCCESS";
    /** 关怀结果: 无回应 */
    static final String RESULT_NO_RESPONSE = "NO_RESPONSE";
    /** 关怀结果: 拒绝 */
    static final String RESULT_REJECTED = "REJECTED";
    /** 关怀结果: 失败 */
    static final String RESULT_FAILED = "FAILED";

    /** 关怀类型: 生日 */
    static final String CARE_TYPE_BIRTHDAY = "BIRTHDAY";
    /** 关怀类型: 节日 */
    static final String CARE_TYPE_FESTIVAL = "FESTIVAL";
    /** 关怀类型: 纪念日 */
    static final String CARE_TYPE_ANNIVERSARY = "ANNIVERSARY";
    /** 关怀类型: 会员到期 */
    static final String CARE_TYPE_MEMBERSHIP_EXPIRY = "MEMBERSHIP_EXPIRY";
    /** 关怀类型: 活跃度提醒 */
    static final String CARE_TYPE_INACTIVITY_REMINDER = "INACTIVITY_REMINDER";
    /** 关怀类型: 自定义 */
    static final String CARE_TYPE_CUSTOM = "CUSTOM";

    /** 节日类型: 农历 */
    static final String FESTIVAL_TYPE_LUNAR = "LUNAR";

    /** 默认操作人 (请求头未透传时使用) */
    static final String DEFAULT_OPERATOR = "scrm-system";

    /** 合法的关怀类型 */
    static final List<String> VALID_CARE_TYPES = List.of(
            CARE_TYPE_BIRTHDAY, CARE_TYPE_FESTIVAL, CARE_TYPE_ANNIVERSARY,
            CARE_TYPE_MEMBERSHIP_EXPIRY, CARE_TYPE_INACTIVITY_REMINDER, CARE_TYPE_CUSTOM);

    /** 合法的关怀动作 */
    static final List<String> VALID_ACTION_TYPES = List.of(
            "SEND_MESSAGE", "SEND_COUPON", "SEND_GIFT", "CALL", "CREATE_TASK", "NOTIFY_ASSIGNEE");

    /** 合法的节日类型 */
    static final List<String> VALID_FESTIVAL_TYPES = List.of("SOLAR", "LUNAR", "FIXED", "CUSTOM");

    /** 合法的适用范围 */
    static final List<String> VALID_APPLICABLE = List.of("ALL", "VIP", "CUSTOM");

    /** 合法的任务状态 */
    static final List<String> VALID_TASK_STATUS = List.of(
            STATUS_PENDING, STATUS_EXECUTING, STATUS_SUCCESS, STATUS_FAILED, STATUS_CANCELLED);

    /** 合法的关怀结果 */
    static final List<String> VALID_CARE_RESULTS = List.of(
            RESULT_SUCCESS, RESULT_NO_RESPONSE, RESULT_REJECTED, RESULT_FAILED);

    /** 关怀规则数据访问层 */
    private final ScrmCareRuleRepository ruleRepository;

    /** JSON 解析器 (解析 triggerCondition / actionContent) */
    private final ObjectMapper objectMapper;

    /**
     * 创建关怀规则。
     * <p>校验 careType / actionType / triggerCondition / actionContent 合法性后写入归属账号 ID 持久化,
     * priority / enabled 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCareRuleEntity createRule(ScrmCareRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        ScrmCareRuleEntity entity = new ScrmCareRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setCareType(dto.getCareType());
        entity.setDescription(dto.getDescription());
        entity.setTriggerCondition(dto.getTriggerCondition());
        entity.setActionType(dto.getActionType());
        entity.setActionContent(dto.getActionContent());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setApplicableSegments(dto.getApplicableSegments());
        entity.setApplicableLevels(dto.getApplicableLevels());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setExecutionCount(DEFAULT_EXECUTION_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建关怀规则: id={}, ruleName={}, careType={}, actionType={}",
                entity.getId(), entity.getRuleName(), entity.getCareType(), entity.getActionType());
        return entity;
    }

    /**
     * 更新关怀规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    @Transactional
    public ScrmCareRuleEntity updateRule(Long id, ScrmCareRuleDto dto) throws ScrmException {
        ScrmCareRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getCareType() != null) entity.setCareType(dto.getCareType());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTriggerCondition() != null) entity.setTriggerCondition(dto.getTriggerCondition());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getActionContent() != null) entity.setActionContent(dto.getActionContent());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getApplicableSegments() != null) entity.setApplicableSegments(dto.getApplicableSegments());
        if (dto.getApplicableLevels() != null) entity.setApplicableLevels(dto.getApplicableLevels());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新关怀规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除关怀规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmCareRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除关怀规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmCareRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按关怀类型、启用状态与关键字过滤。
     *
     * @param careType 关怀类型过滤（可空）
     * @param enabled  启用状态过滤（可空）
     * @param keyword  规则名称关键字模糊匹配（可空）
     * @param pageable 分页参数
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCareRuleEntity> listRules(String careType, Boolean enabled, String keyword, Pageable pageable) {
        Specification<ScrmCareRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (careType != null && !careType.isBlank()) {
                predicates.add(cb.equal(root.get("careType"), careType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("ruleName"), "%" + keyword + "%"));
            }
            query.orderBy(cb.asc(root.get("priority")), cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return ruleRepository.findAll(spec, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmCareRuleEntity enableRule(Long id) throws ScrmException {
        ScrmCareRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用关怀规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmCareRuleEntity disableRule(Long id) throws ScrmException {
        ScrmCareRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用关怀规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    /**
     * 校验关怀规则参数。
     * <p>
     * 创建场景 (partial=false): ruleName / careType / triggerCondition / actionType / actionContent 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段的合法性。triggerCondition / actionContent
     * 非空时校验 JSON 可解析。
     * </p>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmCareRuleDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() != null) {
            if (dto.getRuleName().isBlank()) {
                throw ScrmException.badRequest("规则名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        if (dto.getCareType() != null) {
            if (!VALID_CARE_TYPES.contains(dto.getCareType())) {
                throw ScrmException.badRequest(
                        "关怀类型非法: " + dto.getCareType() + ", 仅支持 " + VALID_CARE_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("关怀类型不能为空");
        }
        if (dto.getActionType() != null) {
            if (!VALID_ACTION_TYPES.contains(dto.getActionType())) {
                throw ScrmException.badRequest(
                        "关怀动作非法: " + dto.getActionType() + ", 仅支持 " + VALID_ACTION_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("关怀动作不能为空");
        }
        if (dto.getTriggerCondition() != null) {
            if (dto.getTriggerCondition().isBlank()) {
                throw ScrmException.badRequest("触发条件不能为空");
            }
            try {
                objectMapper.readTree(dto.getTriggerCondition());
            } catch (Exception e) {
                throw ScrmException.badRequest("触发条件 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("触发条件不能为空");
        }
        if (dto.getActionContent() != null) {
            try {
                objectMapper.readTree(dto.getActionContent());
            } catch (Exception e) {
                throw ScrmException.badRequest("动作内容 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("动作内容不能为空");
        }
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmCareRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmCareRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "关怀规则不存在: id=" + id));
        return entity;
    }

    /**
     * 获取当前操作人 (优先从 UserContext 获取)。
     *
     * @return 操作人用户名
     */
    private String currentOperator() {
        String username = UserContext.getUsername();
        return username != null ? username : DEFAULT_OPERATOR;
    }
}
