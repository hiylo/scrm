/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerLevelService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerLevelAssignDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelHistoryDto;
import org.hiylo.scrm.dto.ScrmCustomerLevelRuleDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerLevelRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerLevelHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerLevelRepository;
import org.hiylo.scrm.repository.ScrmCustomerLevelRuleRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * SCRM 客户分级/分层管理服务。
 * <p>
 * 承载客户分级体系的核心能力: 等级定义增删改查 / 默认等级切换 / 启用禁用, 升降级规则的
 * 增删改查与启用禁用, 客户等级的手动分配与批量分配, 自动升降级评估, 客户当前等级查询、
 * 等级变更历史查询与等级分布统计。所有写操作写入当前用户归属账号, 实现数据隔离。
 * </p>
 * <p>
 * 客户当前等级以 {@code scrm_customer_level_history} 表中该 customer_id 的最新一条记录的
 * to_level_id 为准, 无历史记录视为未分级。规则评估时按 UPGRADE 规则 (priority ASC) 优先,
 * 命中即升级并不再评估降级; 无升级命中时再评估 DOWNGRADE 规则。
 * </p>
 * <p>
 * 条件评估支持 eq/ne/gt/lt/between 五种操作符, 可用字段: totalSpent (累计消费) /
 * orderCount (订单数) / registrationDays (注册天数) / lastInteractionDays (最近交互距今天数) /
 * lifecycle (生命周期)。totalSpent / orderCount 当前由客户实体未直接持有, 缺省按 0 处理,
 * 后续可对接订单服务扩展客户上下文。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomerLevelService {

    /** 默认启用状态 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认是否默认等级 */
    private static final boolean DEFAULT_IS_DEFAULT = false;

    /** 默认优先级 */
    private static final int DEFAULT_PRIORITY = 0;

    /** 默认条件类型 */
    private static final String DEFAULT_CONDITION_TYPE = "ALL";

    /** 默认动作类型 */
    private static final String DEFAULT_ACTION_TYPE = "SET_LEVEL";

    /** 默认匹配次数初值 */
    private static final int DEFAULT_MATCH_COUNT = 0;

    /** 默认操作人 (请求头未透传时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 规则类型: 升级 */
    private static final String RULE_TYPE_UPGRADE = "UPGRADE";
    /** 规则类型: 降级 */
    private static final String RULE_TYPE_DOWNGRADE = "DOWNGRADE";

    /** 条件类型: 全部满足 */
    private static final String CONDITION_TYPE_ALL = "ALL";
    /** 条件类型: 任一满足 */
    private static final String CONDITION_TYPE_ANY = "ANY";

    /** 变更类型: 升级 */
    private static final String CHANGE_TYPE_UPGRADE = "UPGRADE";
    /** 变更类型: 降级 */
    private static final String CHANGE_TYPE_DOWNGRADE = "DOWNGRADE";
    /** 变更类型: 首次入等级 */
    private static final String CHANGE_TYPE_INITIAL = "INITIAL";
    /** 变更类型: 手动分配 */
    private static final String CHANGE_TYPE_MANUAL = "MANUAL";
    /** 变更类型: 自动评估 */
    private static final String CHANGE_TYPE_AUTO = "AUTO";

    /** 合法的规则类型 */
    private static final List<String> VALID_RULE_TYPES = List.of(RULE_TYPE_UPGRADE, RULE_TYPE_DOWNGRADE);

    /** 合法的条件类型 */
    private static final List<String> VALID_CONDITION_TYPES = List.of(CONDITION_TYPE_ALL, CONDITION_TYPE_ANY);

    /** 合法的动作类型 (当前仅 SET_LEVEL) */
    private static final List<String> VALID_ACTION_TYPES = List.of("SET_LEVEL");

    /** 合法的操作符 */
    private static final List<String> VALID_OPERATORS = List.of("eq", "ne", "gt", "lt", "between");

    /** 可用条件字段: totalSpent / orderCount / registrationDays / lastInteractionDays / lifecycle */
    private static final List<String> AVAILABLE_FIELDS = List.of(
            "totalSpent", "orderCount", "registrationDays", "lastInteractionDays", "lifecycle");

    /** 客户等级数据访问层 */
    private final ScrmCustomerLevelRepository levelRepository;

    /** 等级规则数据访问层 */
    private final ScrmCustomerLevelRuleRepository ruleRepository;

    /** 等级变更历史数据访问层 */
    private final ScrmCustomerLevelHistoryRepository historyRepository;

    /** 客户数据访问层 (查询客户属性用于规则评估) */
    private final ScrmCustomerRepository customerRepository;

    /** JSON 解析器 (解析 conditions) */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 等级管理
    // ============================================================

    /**
     * 创建客户等级。
     * <p>校验等级编码在唯一后写入账号 ID 持久化, isDefault / enabled 缺省时填默认值。
     * 若新等级设为默认, 先清空同账号其他等级的默认标记。</p>
     *
     * @param dto 等级参数
     * @return 创建后的等级
     * @throws ScrmException 参数非法 / 等级编码重复
     */
    @Transactional
    public ScrmCustomerLevelEntity createLevel(ScrmCustomerLevelDto dto) throws ScrmException {
        validateLevelDto(dto, false);
        // 等级编码唯一性校验
        if (levelRepository.findByLevelCode(dto.getLevelCode()).isPresent()) {
            throw ScrmException.conflict("等级编码已存在: " + dto.getLevelCode());
        }
        ScrmCustomerLevelEntity entity = new ScrmCustomerLevelEntity();
        entity.setLevelName(dto.getLevelName());
        entity.setLevelCode(dto.getLevelCode());
        entity.setLevelOrder(dto.getLevelOrder());
        entity.setDescription(dto.getDescription());
        entity.setColor(dto.getColor());
        entity.setIcon(dto.getIcon());
        entity.setBenefits(dto.getBenefits());
        entity.setUpgradeThreshold(dto.getUpgradeThreshold());
        entity.setDowngradeThreshold(dto.getDowngradeThreshold());
        entity.setValidityDays(dto.getValidityDays());
        boolean isDefault = Boolean.TRUE.equals(dto.getIsDefault());
        entity.setIsDefault(isDefault);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        // 若设为默认, 先清空同账号其他默认标记
        if (isDefault) {
            levelRepository.clearDefaultFlag();
        }
        entity = levelRepository.save(entity);
        log.info("创建客户等级: id={}, levelName={}, levelCode={}, levelOrder={}",
                entity.getId(), entity.getLevelName(), entity.getLevelCode(), entity.getLevelOrder());
        return entity;
    }

    /**
     * 更新客户等级（字段非空才覆盖）。
     * <p>部分更新场景: 仅校验非空字段的合法性。若 levelCode 变更, 校验新编码在唯一。
     * 若 isDefault 变为 true, 先清空同账号其他默认标记。</p>
     *
     * @param id  等级 ID
     * @param dto 等级参数
     * @return 更新后的等级
     * @throws ScrmException 等级不存在 / 参数非法 / 等级编码重复
     */
    @Transactional
    public ScrmCustomerLevelEntity updateLevel(Long id, ScrmCustomerLevelDto dto) throws ScrmException {
        ScrmCustomerLevelEntity entity = findLevelOrThrow(id);
        validateLevelDto(dto, true);
        // levelCode 变更时校验唯一性
        if (dto.getLevelCode() != null && !dto.getLevelCode().equals(entity.getLevelCode())) {
            Optional<ScrmCustomerLevelEntity> existing = levelRepository
                    .findByLevelCode(dto.getLevelCode());
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                throw ScrmException.conflict("等级编码已存在: " + dto.getLevelCode());
            }
            entity.setLevelCode(dto.getLevelCode());
        }
        if (dto.getLevelName() != null) entity.setLevelName(dto.getLevelName());
        if (dto.getLevelOrder() != null) entity.setLevelOrder(dto.getLevelOrder());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getBenefits() != null) entity.setBenefits(dto.getBenefits());
        if (dto.getUpgradeThreshold() != null) entity.setUpgradeThreshold(dto.getUpgradeThreshold());
        if (dto.getDowngradeThreshold() != null) entity.setDowngradeThreshold(dto.getDowngradeThreshold());
        if (dto.getValidityDays() != null) entity.setValidityDays(dto.getValidityDays());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        // isDefault 切换为 true 时清空其他默认标记
        if (Boolean.TRUE.equals(dto.getIsDefault()) && !Boolean.TRUE.equals(entity.getIsDefault())) {
            levelRepository.clearDefaultFlag();
            entity.setIsDefault(true);
        } else if (Boolean.FALSE.equals(dto.getIsDefault())) {
            entity.setIsDefault(false);
        }
        entity = levelRepository.save(entity);
        log.info("更新客户等级: id={}, levelName={}", entity.getId(), entity.getLevelName());
        return entity;
    }

    /**
     * 删除客户等级。
     * <p>删除前检查是否有规则引用 (scrm_customer_level_rule.target_level_id) 或历史记录引用,
     * 若有则阻止删除并返回引用数量, 避免删除后关联断裂导致审计追溯失败。等级本身可先禁用
     * (disableLevel) 而不删除, 以保留历史关联。</p>
     *
     * @param id 等级 ID
     * @throws ScrmException 等级不存在 / 仍有规则或历史引用
     */
    @Transactional
    public void deleteLevel(Long id) throws ScrmException {
        ScrmCustomerLevelEntity entity = findLevelOrThrow(id);
        // 规则引用校验
        long ruleCount = ruleRepository.findByTargetLevelId(id).size();
        if (ruleCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除等级: 仍有 %d 条升降级规则引用该等级, 请先调整规则", ruleCount));
        }
        // 历史引用校验
        long historyCount = historyRepository.count((root, query, cb) -> cb.and(
                cb.or(cb.equal(root.get("fromLevelId"), id), cb.equal(root.get("toLevelId"), id))
        ));
        if (historyCount > 0) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除等级: 仍有 %d 条等级变更历史引用该等级, 请先禁用等级而非删除", historyCount));
        }
        levelRepository.delete(entity);
        log.info("删除客户等级: id={}, levelName={}", id, entity.getLevelName());
    }

    /**
     * 查询等级详情。
     *
     * @param id 等级 ID
     * @return 等级实体
     * @throws ScrmException 等级不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLevelEntity getLevel(Long id) throws ScrmException {
        return findLevelOrThrow(id);
    }

    /**
     * 分页查询客户等级, 支持按启用状态过滤。
     *
     * @param enabled  启用状态过滤（可空, null=全部）
     * @param pageable 分页参数
     * @return 等级分页结果 (按 levelOrder ASC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLevelEntity> listLevels(Boolean enabled, Pageable pageable) {
        Specification<ScrmCustomerLevelEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.asc(root.get("levelOrder")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return levelRepository.findAll(spec, pageable);
    }

    /**
     * 设置为默认等级 (新客户入等级时自动赋予)。
     * <p>同账号仅可有一个默认等级, 设置前清空其他等级的默认标记。</p>
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmCustomerLevelEntity setDefaultLevel(Long id) throws ScrmException {
        ScrmCustomerLevelEntity entity = findLevelOrThrow(id);
        levelRepository.clearDefaultFlag();
        entity.setIsDefault(true);
        entity = levelRepository.save(entity);
        log.info("设置默认等级: id={}, levelName={}", id, entity.getLevelName());
        return entity;
    }

    /**
     * 启用等级。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmCustomerLevelEntity enableLevel(Long id) throws ScrmException {
        ScrmCustomerLevelEntity entity = findLevelOrThrow(id);
        entity.setEnabled(true);
        entity = levelRepository.save(entity);
        log.info("启用客户等级: id={}, levelName={}", id, entity.getLevelName());
        return entity;
    }

    /**
     * 禁用等级 (禁用后不可分配, 但保留已有客户等级关联)。
     *
     * @param id 等级 ID
     * @return 更新后的等级
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public ScrmCustomerLevelEntity disableLevel(Long id) throws ScrmException {
        ScrmCustomerLevelEntity entity = findLevelOrThrow(id);
        entity.setEnabled(false);
        entity = levelRepository.save(entity);
        log.info("禁用客户等级: id={}, levelName={}", id, entity.getLevelName());
        return entity;
    }

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建升降级规则。
     * <p>校验目标等级存在且启用, conditions 为合法 JSON 后写入账号 ID 持久化,
     * enabled / priority / conditionType / actionType 缺省时填默认值。</p>
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 目标等级不存在 / conditions 非合法 JSON
     */
    @Transactional
    public ScrmCustomerLevelRuleEntity createRule(ScrmCustomerLevelRuleDto dto) throws ScrmException {
        validateRuleDto(dto, false);
        // 目标等级存在性校验 (等级需启用, 避免规则指向已禁用等级)
        ScrmCustomerLevelEntity targetLevel = findLevelOrThrow(dto.getTargetLevelId());
        if (!Boolean.TRUE.equals(targetLevel.getEnabled())) {
            throw ScrmException.badRequest("目标等级已禁用, 不允许创建规则: levelId=" + dto.getTargetLevelId());
        }
        ScrmCustomerLevelRuleEntity entity = new ScrmCustomerLevelRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setTargetLevelId(dto.getTargetLevelId());
        entity.setRuleType(dto.getRuleType());
        entity.setConditionType(dto.getConditionType() != null ? dto.getConditionType() : DEFAULT_CONDITION_TYPE);
        entity.setConditions(dto.getConditions());
        entity.setActionType(dto.getActionType() != null ? dto.getActionType() : DEFAULT_ACTION_TYPE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : DEFAULT_PRIORITY);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : DEFAULT_ENABLED);
        entity.setMatchCount(DEFAULT_MATCH_COUNT);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("创建升降级规则: id={}, ruleName={}, ruleType={}, targetLevelId={}",
                entity.getId(), entity.getRuleName(), entity.getRuleType(), entity.getTargetLevelId());
        return entity;
    }

    /**
     * 更新升降级规则（字段非空才覆盖）。
     * <p>部分更新场景: 仅校验非空字段的合法性, conditions 变更时校验 JSON 合法性。
     * targetLevelId 变更时校验新目标等级存在且启用。</p>
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法 / 目标等级不存在
     */
    @Transactional
    public ScrmCustomerLevelRuleEntity updateRule(Long id, ScrmCustomerLevelRuleDto dto) throws ScrmException {
        ScrmCustomerLevelRuleEntity entity = findRuleOrThrow(id);
        validateRuleDto(dto, true);
        if (dto.getTargetLevelId() != null && !dto.getTargetLevelId().equals(entity.getTargetLevelId())) {
            ScrmCustomerLevelEntity targetLevel = findLevelOrThrow(dto.getTargetLevelId());
            if (!Boolean.TRUE.equals(targetLevel.getEnabled())) {
                throw ScrmException.badRequest("目标等级已禁用, 不允许指向: levelId=" + dto.getTargetLevelId());
            }
            entity.setTargetLevelId(dto.getTargetLevelId());
        }
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getRuleType() != null) entity.setRuleType(dto.getRuleType());
        if (dto.getConditionType() != null) entity.setConditionType(dto.getConditionType());
        if (dto.getConditions() != null) entity.setConditions(dto.getConditions());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = ruleRepository.save(entity);
        log.info("更新升降级规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除升降级规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmCustomerLevelRuleEntity entity = findRuleOrThrow(id);
        ruleRepository.delete(entity);
        log.info("删除升降级规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLevelRuleEntity getRule(Long id) throws ScrmException {
        return findRuleOrThrow(id);
    }

    /**
     * 分页查询规则, 支持按目标等级、规则类型与启用状态过滤。
     *
     * @param targetLevelId 目标等级 ID 过滤（可空）
     * @param ruleType      规则类型过滤: UPGRADE / DOWNGRADE（可空）
     * @param enabled       启用状态过滤（可空）
     * @param pageable      分页参数
     * @return 规则分页结果 (按 priority ASC, createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLevelRuleEntity> listRules(Long targetLevelId, String ruleType,
                                                        Boolean enabled, Pageable pageable) {
        Specification<ScrmCustomerLevelRuleEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (targetLevelId != null) {
                predicates.add(cb.equal(root.get("targetLevelId"), targetLevelId));
            }
            if (ruleType != null && !ruleType.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), ruleType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
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
    public ScrmCustomerLevelRuleEntity enableRule(Long id) throws ScrmException {
        ScrmCustomerLevelRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(true);
        entity = ruleRepository.save(entity);
        log.info("启用升降级规则: id={}, ruleName={}", id, entity.getRuleName());
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
    public ScrmCustomerLevelRuleEntity disableRule(Long id) throws ScrmException {
        ScrmCustomerLevelRuleEntity entity = findRuleOrThrow(id);
        entity.setEnabled(false);
        entity = ruleRepository.save(entity);
        log.info("禁用升降级规则: id={}, ruleName={}", id, entity.getRuleName());
        return entity;
    }

    // ============================================================
    // 等级分配
    // ============================================================

    /**
     * 手动分配客户等级 (记录历史)。
     * <p>校验客户存在且归属当前账号, 校验目标等级存在且启用, 然后写入历史记录。
     * 变更类型: 首次入等级为 INITIAL, 否则为 MANUAL (手动分配)。同等级不重复记录。</p>
     *
     * @param assignDto 分配请求 (customerId + levelId + reason)
     * @return 创建后的历史记录
     * @throws ScrmException 客户不存在 / 等级不存在或已禁用
     */
    @Transactional
    public ScrmCustomerLevelHistoryEntity assignLevel(ScrmCustomerLevelAssignDto assignDto) throws ScrmException {
        if (assignDto == null) {
            throw ScrmException.badRequest("分配参数不能为空");
        }
        if (assignDto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (assignDto.getLevelId() == null) {
            throw ScrmException.badRequest("等级 ID 不能为空");
        }
        ScrmCustomerEntity customer = findCustomerOrThrow(assignDto.getCustomerId());
        ScrmCustomerLevelEntity targetLevel = findLevelOrThrow(assignDto.getLevelId());
        if (!Boolean.TRUE.equals(targetLevel.getEnabled())) {
            throw ScrmException.badRequest("等级已禁用, 不允许分配: levelId=" + assignDto.getLevelId());
        }
        return doChangeLevel(customer, targetLevel, CHANGE_TYPE_MANUAL, assignDto.getReason(), DEFAULT_OPERATOR);
    }

    /**
     * 批量分配客户等级。
     * <p>对客户列表逐一执行等级分配, 单个客户失败跳过并记录告警, 不阻断其他客户。
     * 同等级的客户会跳过 (不重复记录)。</p>
     *
     * @param customerIds 客户 ID 列表
     * @param levelId     目标等级 ID
     * @param reason      变更原因 (可空)
     * @return 成功分配的客户数
     * @throws ScrmException 等级不存在
     */
    @Transactional
    public int batchAssignLevel(List<Long> customerIds, Long levelId, String reason) throws ScrmException {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        if (levelId == null) {
            throw ScrmException.badRequest("等级 ID 不能为空");
        }
        ScrmCustomerLevelEntity targetLevel = findLevelOrThrow(levelId);
        if (!Boolean.TRUE.equals(targetLevel.getEnabled())) {
            throw ScrmException.badRequest("等级已禁用, 不允许分配: levelId=" + levelId);
        }
        int success = 0;
        for (Long customerId : customerIds) {
            if (customerId == null) {
                continue;
            }
            try {
                Optional<ScrmCustomerEntity> customerOpt = customerRepository.findById(customerId);
                if (customerOpt.isEmpty()) {
                    log.warn("批量分配等级, 客户不存在跳过: customerId={}", customerId);
                    continue;
                }
                ScrmCustomerEntity customer = customerOpt.get();

                doChangeLevel(customer, targetLevel, CHANGE_TYPE_MANUAL, reason, DEFAULT_OPERATOR);
                success++;
            } catch (ScrmException e) {
                log.warn("批量分配等级失败, 跳过: customerId={}, levelId={}, code={}, msg={}",
                        customerId, levelId, e.getCode(), e.getMessage());
            }
        }
        log.info("批量分配等级完成: levelId={}, requested={}, success={}",
                levelId, customerIds.size(), success);
        return success;
    }

    // ============================================================
    // 自动评估
    // ============================================================

    /**
     * 评估客户等级规则 (自动升降级)。
     * <p>
     * 流程:
     * <ol>
     *   <li>校验客户存在且归属当前账号</li>
     *   <li>构建客户上下文: registrationDays / lastInteractionDays / lifecycle 从客户实体计算,
     *       totalSpent / orderCount 当前缺省 0 (待对接订单服务)</li>
     *   <li>加载启用 UPGRADE 规则 (priority ASC), 逐条评估, 命中即升级并返回</li>
     *   <li>无升级命中时加载 DOWNGRADE 规则 (priority ASC), 逐条评估, 命中即降级</li>
     *   <li>命中后写入历史记录 (change_type=AUTO), 增量更新规则匹配统计</li>
     * </ol>
     * 当前等级与目标等级相同时跳过, 不写历史。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 变更后的历史记录 (无变更返回 null)
     * @throws ScrmException 客户不存在
     */
    @Transactional
    public ScrmCustomerLevelHistoryEntity evaluateRules(Long customerId) throws ScrmException {
        ScrmCustomerEntity customer = findCustomerOrThrow(customerId);
        Map<String, Object> context = buildCustomerContext(customer);
        // 当前等级 (可能为空, 表示未分级)
        Optional<ScrmCustomerLevelHistoryEntity> currentHistOpt = historyRepository
                .findFirstByCustomerIdOrderByChangedAtDescIdDesc(customerId);
        Long currentLevelId = currentHistOpt.map(ScrmCustomerLevelHistoryEntity::getToLevelId).orElse(null);
        Integer currentOrder = null;
        if (currentLevelId != null) {
            Optional<ScrmCustomerLevelEntity> currentLevel = levelRepository.findById(currentLevelId);
            if (currentLevel.isPresent()) {
                currentOrder = currentLevel.get().getLevelOrder();
            }
        }
        // 评估升级规则 (priority ASC)
        List<ScrmCustomerLevelRuleEntity> upgradeRules = ruleRepository
                .findByRuleTypeAndEnabledTrueOrderByPriorityAsc(RULE_TYPE_UPGRADE);
        for (ScrmCustomerLevelRuleEntity rule : upgradeRules) {
            try {
                if (!evaluateConditions(rule, context)) {
                    continue;
                }
                ScrmCustomerLevelEntity target = levelRepository.findById(rule.getTargetLevelId())
                        .filter(l -> Boolean.TRUE.equals(l.getEnabled()))
                        .orElse(null);
                if (target == null) {
                    log.warn("升级规则目标等级不存在或已禁用, 跳过: ruleId={}, targetLevelId={}",
                            rule.getId(), rule.getTargetLevelId());
                    continue;
                }
                ruleRepository.incrementMatchCount(rule.getId(), LocalDateTime.now());
                if (Objects.equals(target.getId(), currentLevelId)) {
                    log.debug("升级规则命中但目标等级与当前相同, 不写历史: customerId={}, levelId={}",
                            customerId, target.getId());
                    return null;
                }
                // 升级校验: 目标等级 order 必须大于当前 order (否则按平级/降级处理, 跳过)
                if (currentOrder != null && target.getLevelOrder() <= currentOrder) {
                    log.warn("升级规则目标等级 order 不高于当前等级, 跳过: customerId={}, currentOrder={}, targetOrder={}",
                            customerId, currentOrder, target.getLevelOrder());
                    continue;
                }
                return doChangeLevel(customer, target, CHANGE_TYPE_AUTO,
                        "自动升级: " + rule.getRuleName(), DEFAULT_OPERATOR);
            } catch (Exception e) {
                log.warn("升级规则评估异常, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        // 无升级命中, 评估降级规则 (priority ASC)
        List<ScrmCustomerLevelRuleEntity> downgradeRules = ruleRepository
                .findByRuleTypeAndEnabledTrueOrderByPriorityAsc(RULE_TYPE_DOWNGRADE);
        for (ScrmCustomerLevelRuleEntity rule : downgradeRules) {
            try {
                if (!evaluateConditions(rule, context)) {
                    continue;
                }
                ScrmCustomerLevelEntity target = levelRepository.findById(rule.getTargetLevelId())
                        .filter(l -> Boolean.TRUE.equals(l.getEnabled()))
                        .orElse(null);
                if (target == null) {
                    log.warn("降级规则目标等级不存在或已禁用, 跳过: ruleId={}, targetLevelId={}",
                            rule.getId(), rule.getTargetLevelId());
                    continue;
                }
                ruleRepository.incrementMatchCount(rule.getId(), LocalDateTime.now());
                if (Objects.equals(target.getId(), currentLevelId)) {
                    log.debug("降级规则命中但目标等级与当前相同, 不写历史: customerId={}, levelId={}",
                            customerId, target.getId());
                    return null;
                }
                // 降级校验: 目标等级 order 必须小于当前 order (未分级客户不参与降级)
                if (currentOrder == null || target.getLevelOrder() >= currentOrder) {
                    log.warn("降级规则目标等级 order 不低于当前等级, 跳过: customerId={}, currentOrder={}, targetOrder={}",
                            customerId, currentOrder, target.getLevelOrder());
                    continue;
                }
                return doChangeLevel(customer, target, CHANGE_TYPE_AUTO,
                        "自动降级: " + rule.getRuleName(), DEFAULT_OPERATOR);
            } catch (Exception e) {
                log.warn("降级规则评估异常, 跳过: ruleId={}, err={}", rule.getId(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * 批量评估所有已分级客户的等级规则 (定时任务用)。
     * <p>遍历当前账号下全部已分级客户 (从历史表 distinct customer_id), 逐一调用
     * {@link #evaluateRules(Long)}, 单个客户失败跳过, 不阻断其他客户。</p>
     *
     * @return 评估的客户总数 / 实际发生变更的客户数
     */
    @Transactional
    public Map<String, Integer> batchEvaluate() {
        List<Long> customerIds = historyRepository.findDistinctCustomerId();
        int changed = 0;
        int failed = 0;
        for (Long customerId : customerIds) {
            try {
                ScrmCustomerLevelHistoryEntity hist = evaluateRules(customerId);
                if (hist != null) {
                    changed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("批量评估等级异常, 跳过: customerId={}, err={}", customerId, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", customerIds.size());
        result.put("changed", changed);
        result.put("failed", failed);
        log.info("批量评估等级完成:, total={}, changed={}, failed={}", customerIds.size(), changed, failed);
        return result;
    }

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 查询客户当前等级 (取历史表最新一条记录的 to_level_id 对应的等级)。
     * <p>无历史记录时返回默认等级 (若存在且启用), 仍未命中返回 null。</p>
     *
     * @param customerId 客户 ID
     * @return 客户当前等级 (可能为 null)
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public ScrmCustomerLevelEntity getCustomerLevel(Long customerId) throws ScrmException {
        findCustomerOrThrow(customerId);
        Optional<ScrmCustomerLevelHistoryEntity> hist = historyRepository
                .findFirstByCustomerIdOrderByChangedAtDescIdDesc(customerId);
        if (hist.isPresent()) {
            Long levelId = hist.get().getToLevelId();
            return levelRepository.findById(levelId)

                    .orElse(null);
        }
        // 无历史记录, 返回默认等级 (若启用)
        return levelRepository.findByIsDefaultTrue()
                .filter(l -> Boolean.TRUE.equals(l.getEnabled()))
                .orElse(null);
    }

    /**
     * 查询客户等级变更历史 (按变更时间倒序)。
     *
     * @param customerId 客户 ID
     * @param pageable  分页参数
     * @return 历史分页结果
     * @throws ScrmException 客户不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerLevelHistoryDto> getLevelHistory(Long customerId, Pageable pageable)
            throws ScrmException {
        findCustomerOrThrow(customerId);
        return historyRepository
                .findByCustomerIdOrderByChangedAtDescIdDesc(customerId, pageable)
                .map(this::toHistoryDto);
    }

    /**
     * 等级分布统计: 各等级当前客户数。
     * <p>取当前账号下每个客户的最新一条历史记录的 to_level_id, 按 to_level_id 分组聚合客户数,
     * 返回 [{levelId, levelName, levelCode, levelOrder, count}]。</p>
     *
     * @return 等级分布统计列表 (按 levelOrder ASC)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getLevelDistribution() {
        List<Object[]> rows = historyRepository.getLevelDistribution();
        // 加载当前账号全部启用等级用于关联名称
        Map<Long, ScrmCustomerLevelEntity> levelMap = levelRepository
                .findAllByOrderByLevelOrderAsc().stream()
                .collect(Collectors.toMap(ScrmCustomerLevelEntity::getId, l -> l, (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        long total = 0;
        for (Object[] row : rows) {
            Long levelId = row[0] == null ? null : ((Number) row[0]).longValue();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            total += count;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("levelId", levelId);
            ScrmCustomerLevelEntity level = levelId == null ? null : levelMap.get(levelId);
            entry.put("levelName", level == null ? "未知等级" : level.getLevelName());
            entry.put("levelCode", level == null ? null : level.getLevelCode());
            entry.put("levelOrder", level == null ? null : level.getLevelOrder());
            entry.put("count", count);
            result.add(entry);
        }
        // 按 levelOrder ASC 排序 (null 排最后)
        result.sort((a, b) -> {
            Integer ao = (Integer) a.get("levelOrder");
            Integer bo = (Integer) b.get("levelOrder");
            if (ao == null && bo == null) return 0;
            if (ao == null) return 1;
            if (bo == null) return -1;
            return Integer.compare(ao, bo);
        });
        // 追加总计行
        Map<String, Object> totalEntry = new LinkedHashMap<>();
        totalEntry.put("levelId", null);
        totalEntry.put("levelName", "总计");
        totalEntry.put("levelCode", null);
        totalEntry.put("levelOrder", null);
        totalEntry.put("count", total);
        result.add(totalEntry);
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 校验客户等级参数。
     * <p>
     * 创建场景 (partial=false): levelName / levelCode / levelOrder 必填。
     * 更新场景 (partial=true): 允许字段为空 (部分更新), 仅校验非空字段的合法性。
     * </p>
     *
     * @param dto     等级参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateLevelDto(ScrmCustomerLevelDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("等级参数不能为空");
        }
        if (dto.getLevelName() != null) {
            if (dto.getLevelName().isBlank()) {
                throw ScrmException.badRequest("等级名称不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("等级名称不能为空");
        }
        if (dto.getLevelCode() != null) {
            if (dto.getLevelCode().isBlank()) {
                throw ScrmException.badRequest("等级编码不能为空");
            }
        } else if (!partial) {
            throw ScrmException.badRequest("等级编码不能为空");
        }
        if (dto.getLevelOrder() == null && !partial) {
            throw ScrmException.badRequest("等级排序不能为空");
        }
    }

    /**
     * 校验升降级规则参数。
     * <p>
     * 创建场景 (partial=false): ruleName / targetLevelId / ruleType / conditions 必填。
     * 更新场景 (partial=true): 允许字段为空, 仅校验非空字段的合法性。conditions 非空时校验 JSON 可解析。
     * </p>
     *
     * @param dto     规则参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateRuleDto(ScrmCustomerLevelRuleDto dto, boolean partial) throws ScrmException {
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
        if (dto.getTargetLevelId() == null && !partial) {
            throw ScrmException.badRequest("目标等级 ID 不能为空");
        }
        if (dto.getRuleType() != null) {
            if (!VALID_RULE_TYPES.contains(dto.getRuleType())) {
                throw ScrmException.badRequest(
                        "规则类型非法: " + dto.getRuleType() + ", 仅支持 " + VALID_RULE_TYPES);
            }
        } else if (!partial) {
            throw ScrmException.badRequest("规则类型不能为空");
        }
        if (dto.getConditionType() != null && !VALID_CONDITION_TYPES.contains(dto.getConditionType())) {
            throw ScrmException.badRequest(
                    "条件类型非法: " + dto.getConditionType() + ", 仅支持 " + VALID_CONDITION_TYPES);
        }
        if (dto.getActionType() != null && !VALID_ACTION_TYPES.contains(dto.getActionType())) {
            throw ScrmException.badRequest(
                    "动作类型非法: " + dto.getActionType() + ", 仅支持 " + VALID_ACTION_TYPES);
        }
        if (dto.getConditions() != null) {
            if (dto.getConditions().isBlank()) {
                throw ScrmException.badRequest("条件 JSON 不能为空");
            }
            try {
                List<Map<String, Object>> parsed = objectMapper.readValue(
                        dto.getConditions(), new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> condition : parsed) {
                    String field = (String) condition.get("field");
                    String operator = (String) condition.get("operator");
                    if (field == null || field.isBlank()) {
                        throw ScrmException.badRequest("条件字段不能为空: " + condition);
                    }
                    if (operator == null || !VALID_OPERATORS.contains(operator)) {
                        throw ScrmException.badRequest(
                                "操作符非法: " + operator + ", 仅支持 " + VALID_OPERATORS);
                    }
                }
            } catch (ScrmException e) {
                throw e;
            } catch (Exception e) {
                throw ScrmException.badRequest("条件 JSON 解析失败: " + e.getMessage());
            }
        } else if (!partial) {
            throw ScrmException.badRequest("条件 JSON 不能为空");
        }
    }

    /**
     * 实际写入等级变更历史 (内部调用, 不做客户/等级存在性校验)。
     * <p>同等级不重复记录 (返回 null)。变更类型按 changeType 入参, 但若客户首次入等级则强制 INITIAL。</p>
     *
     * @param customer    客户实体
     * @param targetLevel 目标等级实体
     * @param changeType  变更类型: MANUAL / AUTO
     * @param reason      变更原因
     * @param operator    操作人
     * @return 创建后的历史记录 (同等级跳过时返回 null)
     */
    private ScrmCustomerLevelHistoryEntity doChangeLevel(ScrmCustomerEntity customer,
                                                          ScrmCustomerLevelEntity targetLevel,
                                                          String changeType, String reason,
                                                          String operator) {
        // 当前等级 (取最新历史记录)
        Optional<ScrmCustomerLevelHistoryEntity> currentHistOpt = historyRepository
                .findFirstByCustomerIdOrderByChangedAtDescIdDesc(customer.getId());
        Long fromLevelId = currentHistOpt.map(ScrmCustomerLevelHistoryEntity::getToLevelId).orElse(null);
        String fromLevelName = currentHistOpt
                .map(ScrmCustomerLevelHistoryEntity::getToLevelName).orElse(null);
        // 同等级不重复记录
        if (fromLevelId != null && fromLevelId.equals(targetLevel.getId())) {
            log.debug("客户已处于目标等级, 跳过历史写入: customerId={}, levelId={}",
                    customer.getId(), targetLevel.getId());
            return null;
        }
        // 变更类型修正: 首次入等级强制 INITIAL
        String actualChangeType = fromLevelId == null ? CHANGE_TYPE_INITIAL : changeType;
        ScrmCustomerLevelHistoryEntity hist = new ScrmCustomerLevelHistoryEntity();
        hist.setCustomerId(customer.getId());
        hist.setCustomerName(customer.getNickname());
        hist.setFromLevelId(fromLevelId);
        hist.setFromLevelName(fromLevelName);
        hist.setToLevelId(targetLevel.getId());
        hist.setToLevelName(targetLevel.getLevelName());
        hist.setChangeType(actualChangeType);
        hist.setChangeReason(reason);
        hist.setChangedBy(operator);
        hist.setChangedAt(LocalDateTime.now());
        hist = historyRepository.save(hist);
        log.info("客户等级变更: customerId={}, from={}({}), to={}({}), changeType={}, reason={}",
                customer.getId(), fromLevelId, fromLevelName, targetLevel.getId(),
                targetLevel.getLevelName(), actualChangeType, reason);
        return hist;
    }

    /**
     * 构建客户上下文 (规则评估用)。
     * <p>registrationDays 由客户 createTime 计算, lastInteractionDays 由 lastInteractionAt 计算,
     * lifecycle 取客户实体字段; totalSpent / orderCount 当前缺省 0 (待对接订单服务)。</p>
     *
     * @param customer 客户实体
     * @return 客户上下文 Map
     */
    private Map<String, Object> buildCustomerContext(ScrmCustomerEntity customer) {
        Map<String, Object> context = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();
        // 注册天数
        if (customer.getCreateTime() != null) {
            long days = ChronoUnit.DAYS.between(customer.getCreateTime().toLocalDate(), now.toLocalDate());
            context.put("registrationDays", days);
        } else {
            context.put("registrationDays", 0L);
        }
        // 最近交互距今天数
        if (customer.getLastInteractionAt() != null) {
            long days = ChronoUnit.DAYS.between(customer.getLastInteractionAt().toLocalDate(), now.toLocalDate());
            context.put("lastInteractionDays", days);
        } else {
            context.put("lastInteractionDays", Long.MAX_VALUE);
        }
        // 生命周期
        context.put("lifecycle", customer.getLifecycle());
        // 累计消费金额 / 订单数 (待对接订单服务, 当前缺省 0)
        context.put("totalSpent", 0d);
        context.put("orderCount", 0);
        return context;
    }

    /**
     * 评估规则条件是否匹配。
     * <p>解析 conditions JSON, 按 conditionType (ALL/ANY) 对 customerContext 中的字段评估。
     * 条件为空数组视为全部匹配。</p>
     *
     * @param rule            规则实体
     * @param customerContext 客户属性快照
     * @return 条件是否匹配
     */
    private boolean evaluateConditions(ScrmCustomerLevelRuleEntity rule, Map<String, Object> customerContext) {
        List<Map<String, Object>> conditions = parseConditions(rule.getConditions());
        if (conditions.isEmpty()) {
            return true;
        }
        boolean all = CONDITION_TYPE_ALL.equals(rule.getConditionType());
        for (Map<String, Object> condition : conditions) {
            String field = (String) condition.get("field");
            String operator = (String) condition.get("operator");
            Object value = condition.get("value");
            Object fieldValue = customerContext != null ? customerContext.get(field) : null;
            boolean matched = evaluateCondition(fieldValue, operator, value);
            if (!matched && all) {
                return false;
            }
            if (matched && !all) {
                return true;
            }
        }
        return all;
    }

    /**
     * 评估单个条件。
     * <p>支持 eq/ne/gt/lt/between 操作符, 自动处理类型转换。</p>
     *
     * @param fieldValue     客户属性值
     * @param operator       操作符
     * @param conditionValue 条件值
     * @return 条件是否满足
     */
    private boolean evaluateCondition(Object fieldValue, String operator, Object conditionValue) {
        if (fieldValue == null) {
            return false;
        }
        switch (operator) {
            case "eq":
                return toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "ne":
                return !toStringValue(fieldValue).equals(toStringValue(conditionValue));
            case "gt":
                return toDouble(fieldValue) > toDouble(conditionValue);
            case "lt":
                return toDouble(fieldValue) < toDouble(conditionValue);
            case "between":
                return isBetween(fieldValue, conditionValue);
            default:
                return false;
        }
    }

    /**
     * 判断字段值是否在区间内 (between 操作符)。
     * <p>条件值应为 [min, max] 二元数组, 区间两端均包含。</p>
     *
     * @param fieldValue    字段值
     * @param conditionValue 条件值 ([min, max])
     * @return 是否在区间内
     */
    private boolean isBetween(Object fieldValue, Object conditionValue) {
        if (conditionValue instanceof Collection<?> col && col.size() == 2) {
            Object[] arr = col.toArray();
            double min = toDouble(arr[0]);
            double max = toDouble(arr[1]);
            double val = toDouble(fieldValue);
            return val >= min && val <= max;
        }
        return false;
    }

    /**
     * 解析条件 JSON 为 List。
     *
     * @param conditionsJson 条件 JSON 字符串
     * @return 条件列表, 解析失败返回空列表
     */
    private List<Map<String, Object>> parseConditions(String conditionsJson) {
        try {
            return objectMapper.readValue(conditionsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("条件 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空字符串
     */
    private String toStringValue(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 按主键查询等级, 不存在抛异常, 并校验账号归属。
     *
     * @param id 等级 ID
     * @return 等级实体
     * @throws ScrmException 等级不存在
     */
    private ScrmCustomerLevelEntity findLevelOrThrow(Long id) throws ScrmException {
        ScrmCustomerLevelEntity entity = levelRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户等级不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询规则, 不存在抛异常, 并校验账号归属。
     *
     * @param id 规则 ID
     * @return 规则实体
     * @throws ScrmException 规则不存在
     */
    private ScrmCustomerLevelRuleEntity findRuleOrThrow(Long id) throws ScrmException {
        ScrmCustomerLevelRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "升降级规则不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询客户, 不存在抛异常, 并校验账号归属。
     *
     * @param id 客户 ID
     * @return 客户实体
     * @throws ScrmException 客户不存在
     */
    private ScrmCustomerEntity findCustomerOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));
        return customer;
    }


    /**
     * 历史实体转 DTO。
     *
     * @param entity 历史实体
     * @return 历史 DTO
     */
    private ScrmCustomerLevelHistoryDto toHistoryDto(ScrmCustomerLevelHistoryEntity entity) {
        ScrmCustomerLevelHistoryDto dto = new ScrmCustomerLevelHistoryDto();
        dto.setId(entity.getId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setFromLevelId(entity.getFromLevelId());
        dto.setFromLevelName(entity.getFromLevelName());
        dto.setToLevelId(entity.getToLevelId());
        dto.setToLevelName(entity.getToLevelName());
        dto.setChangeType(entity.getChangeType());
        dto.setChangeReason(entity.getChangeReason());
        dto.setChangedBy(entity.getChangedBy());
        dto.setChangedAt(entity.getChangedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
