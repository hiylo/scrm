/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBlacklistManageService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmAppealDto;
import org.hiylo.scrm.dto.ScrmBlacklistCheckDto;
import org.hiylo.scrm.dto.ScrmBlacklistDto;
import org.hiylo.scrm.entity.ScrmBlacklistEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmBlacklistRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 黑名单管理服务。
 * <p>
 * 承载黑名单管理子域: 名单 (黑名单/灰名单/白名单/观察名单) 的增删改查、目标检查与
 * 批量检查、申诉 / 审核 / 恢复 / 延期 / 风险评分更新、按客户与风险等级分页查询、
 * 批量导入与导出。同时托管风控共享常量与辅助方法 (风险等级分值、比较、当前操作人
 * 等), 供规则、事件、统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmBlacklistManageService {

    // ==================== 默认值常量 (共享) ====================

    /** 默认风险评分 */
    static final double DEFAULT_RISK_SCORE = 0.0;
    /** 默认操作人 */
    static final String DEFAULT_OPERATOR = "scrm-system";
    /** 默认动作 */
    static final String DEFAULT_ACTION = "ALERT";
    /** 风险评分上限 */
    static final double MAX_RISK_SCORE = 100.0;
    /** 风险评分下限 */
    static final double MIN_RISK_SCORE = 0.0;
    /** 风险等级分值映射 (LOW=25, MEDIUM=50, HIGH=75, CRITICAL=100) */
    static final Map<String, Double> RISK_LEVEL_SCORE = Map.of(
            "LOW", 25.0, "MEDIUM", 50.0, "HIGH", 75.0, "CRITICAL", 100.0);
    /** 合法的名单类型 */
    static final List<String> VALID_LIST_TYPES = List.of("BLACKLIST", "GRAYLIST", "WHITELIST", "WATCHLIST");
    /** 合法的风险等级 */
    static final List<String> VALID_RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    // ==================== 默认值常量 (私有) ====================

    /** 默认风险等级 */
    private static final String DEFAULT_RISK_LEVEL = "MEDIUM";
    /** 默认名单状态 */
    private static final String DEFAULT_BLACKLIST_STATUS = "ACTIVE";
    /** 默认是否永久 */
    private static final boolean DEFAULT_PERMANENT = false;

    // ==================== 合法枚举值 ====================

    /** 合法的目标类型 */
    private static final List<String> VALID_TARGET_TYPES = List.of(
            "CUSTOMER", "PHONE", "EMAIL", "IP", "DEVICE", "ID_CARD", "BANK_CARD", "ADDRESS", "WECHAT_ID", "COMPANY");
    /** 合法的名单状态 */
    private static final List<String> VALID_BLACKLIST_STATUSES =
            List.of("ACTIVE", "EXPIRED", "REMOVED", "APPEALED", "RESTORED");
    /** 合法的来源 */
    private static final List<String> VALID_SOURCES = List.of("MANUAL", "AUTO", "RULE", "EXTERNAL", "REPORT", "SYSTEM");
    /** 合法的申诉状态 */
    private static final List<String> VALID_APPEAL_STATUSES =
            List.of("PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED");

    // ==================== 依赖注入 ====================

    /** 黑名单数据访问层 */
    private final ScrmBlacklistRepository blacklistRepository;

    /**
     * 加入名单 (黑名单/灰名单/白名单/观察名单)。
     * <p>校验目标类型合法, riskLevel / status / isPermanent 缺省时填默认值, effectiveDate
     * 缺省取当天。</p>
     *
     * @param dto 名单参数
     * @return 创建后的名单条目
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmBlacklistEntity addToBlacklist(ScrmBlacklistDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("名单参数不能为空");
        }
        validateBlacklistEnums(dto, false);
        ScrmBlacklistEntity entity = new ScrmBlacklistEntity();
        entity.setListType(dto.getListType());
        entity.setTargetType(dto.getTargetType());
        entity.setTargetValue(dto.getTargetValue());
        entity.setTargetName(dto.getTargetName());
        entity.setCustomerId(dto.getCustomerId());
        entity.setReason(dto.getReason());
        entity.setRiskLevel(dto.getRiskLevel() != null ? dto.getRiskLevel() : DEFAULT_RISK_LEVEL);
        entity.setRiskScore(dto.getRiskScore() != null ? dto.getRiskScore() : DEFAULT_RISK_SCORE);
        entity.setRiskTags(dto.getRiskTags());
        entity.setSource(dto.getSource());
        entity.setSourceDetail(dto.getSourceDetail());
        entity.setEvidence(dto.getEvidence());
        entity.setRelatedEventId(dto.getRelatedEventId());
        entity.setEffectiveDate(dto.getEffectiveDate() != null ? dto.getEffectiveDate() : LocalDate.now());
        entity.setExpiryDate(dto.getExpiryDate());
        entity.setIsPermanent(dto.getIsPermanent() != null ? dto.getIsPermanent() : DEFAULT_PERMANENT);
        // 永久名单清空到期日
        if (Boolean.TRUE.equals(entity.getIsPermanent())) {
            entity.setExpiryDate(null);
        }
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_BLACKLIST_STATUS);
        entity.setAddedBy(dto.getAddedBy());
        entity.setAddedAt(LocalDateTime.now());
        entity.setReviewCount(0);
        entity.setAlertCount(0);
        entity.setMetadata(dto.getMetadata());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : currentOperator());
        entity = blacklistRepository.save(entity);
        log.info("加入名单: id={}, listType={}, targetType={}, targetValue={}",
                entity.getId(), entity.getListType(), entity.getTargetType(), entity.getTargetValue());
        return entity;
    }

    /**
     * 从名单移出 (状态置为 REMOVED)。
     *
     * @param id        名单条目 ID
     * @param reason    移除原因
     * @param removedBy 移除人
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @Transactional
    public ScrmBlacklistEntity removeFromBlacklist(Long id, String reason, String removedBy) throws ScrmException {
        ScrmBlacklistEntity entity = findBlacklistOrThrow(id);
        if ("REMOVED".equals(entity.getStatus())) {
            throw ScrmException.badRequest("名单条目已移除: id=" + id);
        }
        entity.setStatus("REMOVED");
        entity.setRemovedBy(removedBy != null ? removedBy : currentOperator());
        entity.setRemovedAt(LocalDateTime.now());
        entity.setRemoveReason(reason);
        entity = blacklistRepository.save(entity);
        log.info("移出名单: id={}, removedBy={}", id, entity.getRemovedBy());
        return entity;
    }

    /**
     * 查询名单详情。
     *
     * @param id 名单条目 ID
     * @return 名单条目
     * @throws ScrmException 名单不存在
     */
    @Transactional(readOnly = true)
    public ScrmBlacklistEntity getBlacklist(Long id) throws ScrmException {
        return findBlacklistOrThrow(id);
    }

    /**
     * 分页查询名单, 支持按名单类型 / 目标类型 / 风险等级 / 状态 / 关键字过滤。
     *
     * @param listType   名单类型过滤（可空）
     * @param targetType 目标类型过滤（可空）
     * @param riskLevel  风险等级过滤（可空）
     * @param status     状态过滤（可空）
     * @param keyword    目标值/目标名称关键字模糊匹配（可空）
     * @param pageable   分页参数
     * @return 名单分页结果 (按 updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistEntity> listBlacklist(String listType, String targetType, String riskLevel,
                                                    String status, String keyword, Pageable pageable) {
        Specification<ScrmBlacklistEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (listType != null && !listType.isBlank()) {
                predicates.add(cb.equal(root.get("listType"), listType));
            }
            if (targetType != null && !targetType.isBlank()) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (riskLevel != null && !riskLevel.isBlank()) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("targetValue"), like),
                        cb.like(root.get("targetName"), like)));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return blacklistRepository.findAll(spec, pageable);
    }

    /**
     * 检查目标是否在名单中 (完整实现)。
     * <p>查询指定目标的所有 ACTIVE 名单条目, 返回命中结果 Map: hit / matches / listTypes /
     * maxRiskLevel。</p>
     *
     * @param checkDto 检查参数
     * @return 检查结果 Map
     * @throws ScrmException 参数非法
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkBlacklist(ScrmBlacklistCheckDto checkDto) throws ScrmException {
        if (checkDto == null || checkDto.getTargetType() == null || checkDto.getTargetValue() == null) {
            throw ScrmException.badRequest("检查参数不能为空");
        }
        List<ScrmBlacklistEntity> matches;
        if (checkDto.getListType() != null && !checkDto.getListType().isBlank()) {
            matches = blacklistRepository.findByTargetTypeAndTargetValue(
                    checkDto.getTargetType(), checkDto.getTargetValue()).stream()
                    .filter(e -> "ACTIVE".equals(e.getStatus()))
                    .filter(e -> checkDto.getListType().equals(e.getListType()))
                    .collect(Collectors.toList());
        } else {
            matches = blacklistRepository.findByTargetTypeAndTargetValueAndStatus(
                    checkDto.getTargetType(), checkDto.getTargetValue(), "ACTIVE");
        }
        return buildCheckResult(matches);
    }

    /**
     * 批量检查多个目标是否在名单中。
     *
     * @param targets 检查参数列表
     * @return 检查结果列表
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> batchCheck(List<ScrmBlacklistCheckDto> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (ScrmBlacklistCheckDto target : targets) {
            try {
                results.add(checkBlacklist(target));
            } catch (ScrmException e) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("targetType", target != null ? target.getTargetType() : null);
                r.put("targetValue", target != null ? target.getTargetValue() : null);
                r.put("hit", false);
                r.put("error", e.getMessage());
                results.add(r);
            }
        }
        return results;
    }

    /**
     * 按目标查询名单条目 (所有状态)。
     *
     * @param targetType  目标类型
     * @param targetValue 目标值
     * @return 名单条目列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBlacklistEntity> getByTarget(String targetType, String targetValue) {
        return blacklistRepository.findByTargetTypeAndTargetValue(
                 targetType, targetValue);
    }

    /**
     * 查询已过期名单 (状态 ACTIVE 且 expiryDate 早于今天)。
     *
     * @return 已过期名单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBlacklistEntity> getExpiredList() {
        return blacklistRepository.findByStatusAndExpiryDateBefore(
                 "ACTIVE", LocalDate.now());
    }

    /**
     * 查询即将到期名单 (状态 ACTIVE 且 expiryDate 在今天起 days 天内)。
     *
     * @param days 天数
     * @return 即将到期名单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBlacklistEntity> getExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(days);
        return blacklistRepository.findByStatusAndExpiryDateBetween(
                 "ACTIVE", today, end);
    }

    /**
     * 发起申诉 (状态置为 APPEALED, 申诉状态置为 PENDING)。
     *
     * @param appealDto 申诉参数
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @Transactional
    public ScrmBlacklistEntity appeal(ScrmAppealDto appealDto) throws ScrmException {
        if (appealDto == null || appealDto.getBlacklistId() == null) {
            throw ScrmException.badRequest("申诉参数不能为空");
        }
        ScrmBlacklistEntity entity = findBlacklistOrThrow(appealDto.getBlacklistId());
        if (!"ACTIVE".equals(entity.getStatus()) && !"APPEALED".equals(entity.getStatus())) {
            throw ScrmException.badRequest("当前状态不允许申诉: status=" + entity.getStatus());
        }
        entity.setStatus("APPEALED");
        entity.setAppealStatus("PENDING");
        entity.setAppealReason(appealDto.getReason());
        entity.setAppealedAt(LocalDateTime.now());
        entity = blacklistRepository.save(entity);
        log.info("发起申诉: blacklistId={}", appealDto.getBlacklistId());
        return entity;
    }

    /**
     * 审核申诉。
     *
     * @param blacklistId 名单条目 ID
     * @param action      审核动作: APPROVED / REJECTED
     * @param reviewerId  审核人 ID
     * @param result      审核结果说明
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 动作非法
     */
    @Transactional
    public ScrmBlacklistEntity reviewAppeal(Long blacklistId, String action, String reviewerId, String result)
            throws ScrmException {
        if (action == null || !VALID_APPEAL_STATUSES.subList(2, 4).contains(action)) {
            throw ScrmException.badRequest("审核动作非法, 仅支持 APPROVED/REJECTED: " + action);
        }
        ScrmBlacklistEntity entity = findBlacklistOrThrow(blacklistId);
        entity.setAppealStatus(action);
        entity.setAppealReviewedBy(reviewerId != null ? reviewerId : currentOperator());
        entity.setAppealReviewedAt(LocalDateTime.now());
        entity.setAppealResult(result);
        entity.setReviewCount((entity.getReviewCount() != null ? entity.getReviewCount() : 0) + 1);
        entity.setLastReviewedAt(LocalDateTime.now());
        if ("APPROVED".equals(action)) {
            // 申诉通过: 移出名单
            entity.setStatus("REMOVED");
            entity.setRemovedBy(entity.getAppealReviewedBy());
            entity.setRemovedAt(LocalDateTime.now());
            entity.setRemoveReason("申诉通过: " + (result != null ? result : ""));
        } else {
            // 申诉驳回: 恢复 ACTIVE
            entity.setStatus("ACTIVE");
        }
        entity = blacklistRepository.save(entity);
        log.info("审核申诉: blacklistId={}, action={}", blacklistId, action);
        return entity;
    }

    /**
     * 恢复已移除的名单条目 (状态置为 RESTORED → ACTIVE)。
     *
     * @param blacklistId 名单条目 ID
     * @param reason      恢复原因
     * @param restoredBy  恢复人
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 状态非法
     */
    @Transactional
    public ScrmBlacklistEntity restore(Long blacklistId, String reason, String restoredBy) throws ScrmException {
        ScrmBlacklistEntity entity = findBlacklistOrThrow(blacklistId);
        if (!"REMOVED".equals(entity.getStatus())) {
            throw ScrmException.badRequest("仅已移除的名单可恢复: status=" + entity.getStatus());
        }
        entity.setStatus("RESTORED");
        entity.setNotes((entity.getNotes() != null ? entity.getNotes() + " | " : "") + "恢复: " + reason);
        entity = blacklistRepository.save(entity);
        // 恢复后重新激活
        entity.setStatus("ACTIVE");
        entity = blacklistRepository.save(entity);
        log.info("恢复名单: blacklistId={}, restoredBy={}", blacklistId, restoredBy);
        return entity;
    }

    /**
     * 延期名单到期日。
     *
     * @param blacklistId    名单条目 ID
     * @param newExpiryDate  新到期日期
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 日期非法
     */
    @Transactional
    public ScrmBlacklistEntity extendExpiry(Long blacklistId, LocalDate newExpiryDate) throws ScrmException {
        if (newExpiryDate == null) {
            throw ScrmException.badRequest("新到期日期不能为空");
        }
        ScrmBlacklistEntity entity = findBlacklistOrThrow(blacklistId);
        entity.setExpiryDate(newExpiryDate);
        entity.setIsPermanent(false);
        // 若原已过期, 延期后恢复 ACTIVE
        if ("EXPIRED".equals(entity.getStatus())) {
            entity.setStatus("ACTIVE");
        }
        entity = blacklistRepository.save(entity);
        log.info("延期名单: blacklistId={}, newExpiry={}", blacklistId, newExpiryDate);
        return entity;
    }

    /**
     * 更新名单风险评分。
     *
     * @param blacklistId 名单条目 ID
     * @param score       风险评分 0-100
     * @return 更新后的名单条目
     * @throws ScrmException 名单不存在 / 评分越界
     */
    @Transactional
    public ScrmBlacklistEntity updateRiskScore(Long blacklistId, double score) throws ScrmException {
        if (score < MIN_RISK_SCORE || score > MAX_RISK_SCORE) {
            throw ScrmException.badRequest("风险评分必须在 0-100 之间: " + score);
        }
        ScrmBlacklistEntity entity = findBlacklistOrThrow(blacklistId);
        entity.setRiskScore(score);
        entity = blacklistRepository.save(entity);
        log.info("更新名单风险评分: blacklistId={}, score={}", blacklistId, score);
        return entity;
    }

    /**
     * 按客户分页查询名单。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 名单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistEntity> getBlacklistByCustomer(Long customerId, Pageable pageable) {
        return blacklistRepository.findByCustomerId(customerId, pageable);
    }

    /**
     * 按风险等级分页查询名单。
     *
     * @param level    风险等级
     * @param pageable 分页参数
     * @return 名单分页结果
     * @throws ScrmException 风险等级非法
     */
    @Transactional(readOnly = true)
    public Page<ScrmBlacklistEntity> getBlacklistByRiskLevel(
            String level, Pageable pageable) throws ScrmException {
        if (level == null || !VALID_RISK_LEVELS.contains(level)) {
            throw ScrmException.badRequest("风险等级非法: " + level + ", 仅支持 " + VALID_RISK_LEVELS);
        }
        return blacklistRepository.findByRiskLevel(level, pageable);
    }

    /**
     * 批量导入名单。
     *
     * @param items 名单参数列表
     * @return 导入结果 {total, success, failed}
     */
    @Transactional
    public Map<String, Integer> importBlacklist(List<ScrmBlacklistDto> items) {
        Map<String, Integer> result = new LinkedHashMap<>();
        int success = 0;
        int failed = 0;
        if (items == null) {
            result.put("total", 0);
            result.put("success", 0);
            result.put("failed", 0);
            return result;
        }
        for (ScrmBlacklistDto item : items) {
            try {
                addToBlacklist(item);
                success++;
            } catch (Exception e) {
                log.warn("导入名单失败: targetValue={}, error={}",
                        item != null ? item.getTargetValue() : null, e.getMessage());
                failed++;
            }
        }
        result.put("total", items.size());
        result.put("success", success);
        result.put("failed", failed);
        return result;
    }

    /**
     * 导出指定名单类型的所有条目。
     *
     * @param listType 名单类型 (可空, 为空导出全部)
     * @return 名单条目列表
     */
    @Transactional(readOnly = true)
    public List<ScrmBlacklistEntity> exportBlacklist(String listType) {
        if (listType == null || listType.isBlank()) {
            return blacklistRepository.findAll(
                    (root, query, cb) -> cb.and(),
                    Sort.by(Sort.Direction.DESC, "updateTime"));
        }
        return blacklistRepository.findByListType(listType);
    }

    // ============================================================
    // 公共辅助方法 (供兄弟类复用)
    // ============================================================

    /**
     * 比较两个风险等级的高低 (返回正值表示 a 更高)。
     *
     * @param a 等级 A
     * @param b 等级 B
     * @return 分值差
     */
    int compareSeverity(String a, String b) {
        return RISK_LEVEL_SCORE.getOrDefault(a, 0.0).intValue()
                - RISK_LEVEL_SCORE.getOrDefault(b, 0.0).intValue();
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询名单, 不存在抛异常。
     *
     * @param id 名单条目 ID
     * @return 名单条目
     * @throws ScrmException 名单不存在
     */
    private ScrmBlacklistEntity findBlacklistOrThrow(Long id) throws ScrmException {
        ScrmBlacklistEntity entity = blacklistRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "名单条目不存在: id=" + id));
        return entity;
    }

    /**
     * 当前操作人 (优先取 UserContext, 缺省 scrm-system)。
     *
     * @return 当前操作人
     */
    private String currentOperator() {
        String userId = UserContext.getUserId();
        return userId != null ? userId : DEFAULT_OPERATOR;
    }

    /**
     * 校验名单 DTO 枚举字段。
     *
     * @param dto      名单参数
     * @param isUpdate 是否更新场景
     * @throws ScrmException 参数非法
     */
    private void validateBlacklistEnums(ScrmBlacklistDto dto, boolean isUpdate) throws ScrmException {
        if (!isUpdate) {
            if (dto.getListType() != null && !VALID_LIST_TYPES.contains(dto.getListType())) {
                throw ScrmException.badRequest("名单类型非法: " + dto.getListType()
                        + ", 仅支持 " + VALID_LIST_TYPES);
            }
            if (dto.getTargetType() != null && !VALID_TARGET_TYPES.contains(dto.getTargetType())) {
                throw ScrmException.badRequest("目标类型非法: " + dto.getTargetType()
                        + ", 仅支持 " + VALID_TARGET_TYPES);
            }
            if (dto.getSource() != null && !VALID_SOURCES.contains(dto.getSource())) {
                throw ScrmException.badRequest("来源非法: " + dto.getSource() + ", 仅支持 " + VALID_SOURCES);
            }
        }
        if (dto.getRiskLevel() != null && !VALID_RISK_LEVELS.contains(dto.getRiskLevel())) {
            throw ScrmException.badRequest("风险等级非法: " + dto.getRiskLevel()
                    + ", 仅支持 " + VALID_RISK_LEVELS);
        }
        if (dto.getStatus() != null && !VALID_BLACKLIST_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("状态非法: " + dto.getStatus()
                    + ", 仅支持 " + VALID_BLACKLIST_STATUSES);
        }
    }

    /**
     * 构建黑名单检查结果。
     *
     * @param matches 命中的名单条目
     * @return 检查结果 Map
     */
    private Map<String, Object> buildCheckResult(List<ScrmBlacklistEntity> matches) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean hit = matches != null && !matches.isEmpty();
        result.put("hit", hit);
        result.put("matchCount", matches != null ? matches.size() : 0);
        if (hit) {
            result.put("matches", matches);
            Set<String> listTypes = matches.stream()
                    .map(ScrmBlacklistEntity::getListType)
                    .collect(Collectors.toSet());
            result.put("listTypes", listTypes);
            String maxLevel = matches.stream()
                    .map(ScrmBlacklistEntity::getRiskLevel)
                    .max(this::compareSeverity)
                    .orElse("LOW");
            result.put("maxRiskLevel", maxLevel);
            double maxScore = matches.stream()
                    .mapToDouble(b -> b.getRiskScore() != null ? b.getRiskScore() : 0.0)
                    .max().orElse(0.0);
            result.put("maxRiskScore", maxScore);
        } else {
            result.put("matches", List.of());
        }
        return result;
    }
}
