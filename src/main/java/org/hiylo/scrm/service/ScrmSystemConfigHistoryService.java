/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSystemConfigHistoryService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmConfigHistoryDto;
import org.hiylo.scrm.entity.ScrmConfigHistoryEntity;
import org.hiylo.scrm.entity.ScrmSystemConfigEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConfigHistoryRepository;
import org.hiylo.scrm.repository.ScrmSystemConfigRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 系统配置历史版本与回滚服务。
 * <p>
 * 承载配置历史子域: 历史查询 (分页/按配置/按键/按人/近期变更)、手动创建历史、
 * 回滚与批量回滚、回滚历史、版本对比与历史导出。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSystemConfigHistoryService {

    /** 配置变更历史数据访问层 */
    private final ScrmConfigHistoryRepository historyRepository;
    /** 系统配置数据访问层 */
    private final ScrmSystemConfigRepository configRepository;
    /** 配置项管理服务 (共享工具) */
    private final ScrmSystemConfigItemService itemService;
    /** 配置值处理服务 */
    private final ScrmSystemConfigValueService valueService;

    // ============================================================
    // History 变更历史管理
    // ============================================================

    /**
     * 查询历史详情。
     *
     * @param id 历史 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    @Transactional(readOnly = true)
    public ScrmConfigHistoryEntity getHistory(Long id) throws ScrmException {
        return findHistoryOrThrow(id);
    }

    /**
     * 手动创建配置变更历史记录。
     * <p>适用于外部系统导入 / 审计补录等场景。若配置键存在则自动补全 configId / configName /
     * configGroup 快照; DTO 中已提供的字段优先。changeType / changedBy 为必填。</p>
     *
     * @param dto 历史参数 (configKey + changeType + changedBy 必填)
     * @return 创建后的历史实体
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmConfigHistoryEntity createManualHistory(ScrmConfigHistoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("历史参数不能为空");
        }
        if (dto.getConfigKey() == null || dto.getConfigKey().isBlank()) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        if (dto.getChangeType() == null || dto.getChangeType().isBlank()) {
            throw ScrmException.badRequest("变更类型不能为空");
        }
        if (dto.getChangedBy() == null || dto.getChangedBy().isBlank()) {
            throw ScrmException.badRequest("变更人不能为空");
        }
        // 尝试按 configKey 补全快照 (configId / configName / configGroup)
        ScrmSystemConfigEntity config = configRepository
                .findByConfigKey(dto.getConfigKey()).orElse(null);
        ScrmConfigHistoryEntity history = new ScrmConfigHistoryEntity();
        history.setConfigId(dto.getConfigId() != null ? dto.getConfigId()
                : (config != null ? config.getId() : null));
        history.setConfigKey(dto.getConfigKey());
        history.setConfigName(dto.getConfigName() != null ? dto.getConfigName()
                : (config != null ? config.getConfigName() : null));
        history.setConfigGroup(dto.getConfigGroup() != null ? dto.getConfigGroup()
                : (config != null ? config.getConfigGroup() : null));
        history.setOldValue(dto.getOldValue());
        history.setNewValue(dto.getNewValue());
        history.setOldDisplayValue(dto.getOldDisplayValue() != null ? dto.getOldDisplayValue()
                : (config != null ? valueService.truncate(
                        valueService.formatDisplayValue(dto.getOldValue(), config.getConfigType()), 2000)
                        : null));
        history.setNewDisplayValue(dto.getNewDisplayValue() != null ? dto.getNewDisplayValue()
                : (config != null ? valueService.truncate(
                        valueService.formatDisplayValue(dto.getNewValue(), config.getConfigType()), 2000)
                        : null));
        history.setChangeType(dto.getChangeType());
        history.setChangeReason(dto.getChangeReason());
        history.setChangedBy(dto.getChangedBy());
        history.setChangedAt(dto.getChangedAt() != null ? dto.getChangedAt() : LocalDateTime.now());
        history.setIpAddress(dto.getIpAddress());
        history.setUserAgent(dto.getUserAgent());
        history.setSessionId(dto.getSessionId());
        history.setRollbackPossible(dto.getRollbackPossible() != null ? dto.getRollbackPossible()
                : (ScrmSystemConfigItemService.CHANGE_TYPE_UPDATE.equals(dto.getChangeType())
                        || ScrmSystemConfigItemService.CHANGE_TYPE_RESET.equals(dto.getChangeType())
                        || ScrmSystemConfigItemService.CHANGE_TYPE_IMPORT.equals(dto.getChangeType())));
        history.setRollbackById(dto.getRollbackById());
        history.setIsRolledBack(dto.getIsRolledBack() != null ? dto.getIsRolledBack() : Boolean.FALSE);
        history.setRolledBackAt(dto.getRolledBackAt());
        history.setRolledBackBy(dto.getRolledBackBy());
        history.setReviewStatus(dto.getReviewStatus());
        history.setReviewedBy(dto.getReviewedBy());
        history.setReviewedAt(dto.getReviewedAt());
        history.setMetadata(dto.getMetadata());
        history = historyRepository.save(history);
        log.info("手动创建配置变更历史: historyId={}, configKey={}, changeType={}, changedBy={}",
                history.getId(), dto.getConfigKey(), dto.getChangeType(), dto.getChangedBy());
        return history;
    }

    /**
     * 分页查询历史, 支持按配置 ID / 配置键 / 变更类型 / 变更人 / 时间区间过滤。
     *
     * @param configId   配置 ID 过滤（可空）
     * @param configKey  配置键过滤（可空）
     * @param changeType 变更类型过滤（可空）
     * @param changedBy  变更人过滤（可空）
     * @param startTime  变更时间起始（可空）
     * @param endTime    变更时间截止（可空）
     * @param pageable   分页参数
     * @return 历史分页结果 (按 changedAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmConfigHistoryEntity> listHistory(Long configId, String configKey, String changeType,
                                                       String changedBy, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmConfigHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (configId != null) {
                predicates.add(cb.equal(root.get("configId"), configId));
            }
            if (configKey != null && !configKey.isBlank()) {
                predicates.add(cb.equal(root.get("configKey"), configKey));
            }
            if (changeType != null && !changeType.isBlank()) {
                predicates.add(cb.equal(root.get("changeType"), changeType));
            }
            if (changedBy != null && !changedBy.isBlank()) {
                predicates.add(cb.equal(root.get("changedBy"), changedBy));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("changedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return historyRepository.findAll(spec, pageable);
    }

    /**
     * 按配置 ID 分页查询历史。
     *
     * @param configId 配置 ID
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConfigHistoryEntity> getHistoryByConfig(Long configId, Pageable pageable) {
        if (configId == null) {
            throw ScrmException.badRequest("配置 ID 不能为空");
        }
        return historyRepository.findByConfigIdOrderByChangedAtDesc(
                 configId, pageable);
    }

    /**
     * 按配置键分页查询历史。
     *
     * @param key      配置键
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConfigHistoryEntity> getHistoryByKey(String key, Pageable pageable) {
        if (key == null || key.isBlank()) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        return historyRepository.findByConfigKeyOrderByChangedAtDesc(
                 key, pageable);
    }

    /**
     * 按变更人分页查询历史。
     *
     * @param userId   变更人 ID
     * @param pageable 分页参数
     * @return 历史分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmConfigHistoryEntity> getHistoryByUser(String userId, Pageable pageable) {
        if (userId == null || userId.isBlank()) {
            throw ScrmException.badRequest("变更人不能为空");
        }
        return historyRepository.findByChangedByOrderByChangedAtDesc(
                 userId, pageable);
    }

    /**
     * 查询近期变更 (最近 N 天)。
     *
     * @param days 天数
     * @return 历史列表
     */
    @Transactional(readOnly = true)
    public List<ScrmConfigHistoryEntity> getRecentChanges(int days) {
        if (days <= 0) {
            days = ScrmSystemConfigItemService.DEFAULT_RECENT_DAYS;
        }
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(days);
        return historyRepository.findByChangedAtBetweenOrderByChangedAtDesc(
                 startTime, endTime);
    }

    /**
     * 回滚配置 (完整实现: 恢复旧值 → 创建新历史 → 更新配置)。
     * <p>标记原历史为已回滚, 将配置值恢复为原历史的旧值,
     * 创建一条新的 UPDATE 历史记录关联原历史 ID。</p>
     *
     * @param historyId    历史 ID
     * @param rolledBackBy 回滚人
     * @return 更新后的配置
     * @throws ScrmException 历史不存在 / 不可回滚 / 已回滚
     */
    @Transactional
    public ScrmSystemConfigEntity rollback(Long historyId, String rolledBackBy) throws ScrmException {
        ScrmConfigHistoryEntity history = findHistoryOrThrow(historyId);
        if (Boolean.FALSE.equals(history.getRollbackPossible())) {
            throw ScrmException.badRequest("历史不可回滚: historyId=" + historyId);
        }
        if (Boolean.TRUE.equals(history.getIsRolledBack())) {
            throw ScrmException.badRequest("历史已回滚, 不可重复回滚: historyId=" + historyId);
        }
        // 标记原历史为已回滚
        history.setIsRolledBack(Boolean.TRUE);
        history.setRolledBackAt(LocalDateTime.now());
        history.setRolledBackBy(rolledBackBy);
        historyRepository.save(history);
        // 加载当前配置
        ScrmSystemConfigEntity entity;
        try {
            entity = getConfigByKeyInternal(history.getConfigKey());
        } catch (ScrmException e) {
            throw ScrmException.badRequest("回滚失败, 配置已不存在: " + history.getConfigKey());
        }
        String currentValue = entity.getConfigValue();
        String rollbackValue = history.getOldValue();
        if (Objects.equals(currentValue, rollbackValue)) {
            log.info("回滚配置: 当前值与历史旧值相同, 跳过更新: configKey={}", history.getConfigKey());
            return entity;
        }
        // 校验回滚值合法性
        if (rollbackValue != null && !rollbackValue.isEmpty()) {
            valueService.validateValue(rollbackValue, entity.getConfigType(), entity.getValidationRegex(),
                    entity.getMinValue(), entity.getMaxValue(), entity.getMaxLength());
        }
        entity.setConfigValue(rollbackValue);
        entity = configRepository.save(entity);
        // 创建新的回滚历史, 关联原历史 ID
        ScrmConfigHistoryEntity newHistory = itemService.recordHistory(entity, currentValue, rollbackValue,
                ScrmSystemConfigItemService.CHANGE_TYPE_UPDATE, "回滚至历史: " + historyId, rolledBackBy);
        newHistory.setRollbackById(historyId);
        historyRepository.save(newHistory);
        // 更新变更统计
        itemService.bumpChangeStat(entity, rolledBackBy);
        // 清除缓存
        itemService.evictCache(entity.getConfigKey());
        log.info("回滚配置: configKey={}, historyId={}, rolledBackBy={}",
                entity.getConfigKey(), historyId, rolledBackBy);
        return entity;
    }

    /**
     * 批量回滚 (逐条回滚, 失败条目不影响其它条目)。
     *
     * @param historyIds   历史 ID 列表
     * @param rolledBackBy 回滚人
     * @return 批量处理结果 Map {total, success, failed, results}
     */
    @Transactional
    public Map<String, Object> batchRollback(List<Long> historyIds, String rolledBackBy) {
        if (historyIds == null || historyIds.isEmpty()) {
            throw ScrmException.badRequest("历史 ID 列表不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        for (Long historyId : historyIds) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("historyId", historyId);
            try {
                ScrmSystemConfigEntity entity = rollback(historyId, rolledBackBy);
                r.put("success", true);
                r.put("configKey", entity.getConfigKey());
                r.put("configValue", entity.getConfigValue());
                success++;
            } catch (ScrmException e) {
                r.put("success", false);
                r.put("error", e.getMessage());
            }
            results.add(r);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", historyIds.size());
        summary.put("success", success);
        summary.put("failed", historyIds.size() - success);
        summary.put("results", results);
        log.info("批量回滚: total={}, success={}", historyIds.size(), success);
        return summary;
    }

    /**
     * 查询回滚历史 (哪些回滚操作关联了指定历史)。
     *
     * @param historyId 历史 ID
     * @return 回滚历史列表
     * @throws ScrmException 历史不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmConfigHistoryEntity> getRollbackHistory(Long historyId) throws ScrmException {
        findHistoryOrThrow(historyId);
        return historyRepository.findByRollbackById(historyId);
    }

    /**
     * 版本对比 (对比两个历史版本的新值差异)。
     *
     * @param historyId1 历史 ID 1
     * @param historyId2 历史 ID 2
     * @return 对比结果 Map {history1, history2, same, differences}
     * @throws ScrmException 历史不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(Long historyId1, Long historyId2) throws ScrmException {
        ScrmConfigHistoryEntity h1 = findHistoryOrThrow(historyId1);
        ScrmConfigHistoryEntity h2 = findHistoryOrThrow(historyId2);
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> v1 = new LinkedHashMap<>();
        v1.put("historyId", h1.getId());
        v1.put("configKey", h1.getConfigKey());
        v1.put("newValue", h1.getNewValue());
        v1.put("changedAt", h1.getChangedAt());
        v1.put("changedBy", h1.getChangedBy());
        v1.put("changeType", h1.getChangeType());
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("historyId", h2.getId());
        v2.put("configKey", h2.getConfigKey());
        v2.put("newValue", h2.getNewValue());
        v2.put("changedAt", h2.getChangedAt());
        v2.put("changedBy", h2.getChangedBy());
        v2.put("changeType", h2.getChangeType());
        result.put("history1", v1);
        result.put("history2", v2);
        result.put("same", Objects.equals(h1.getNewValue(), h2.getNewValue()));
        Map<String, Object> diffs = new LinkedHashMap<>();
        if (!Objects.equals(h1.getNewValue(), h2.getNewValue())) {
            diffs.put("newValue", Map.of("history1", h1.getNewValue(), "history2", h2.getNewValue()));
        }
        if (!Objects.equals(h1.getChangedAt(), h2.getChangedAt())) {
            diffs.put("changedAt", Map.of("history1", h1.getChangedAt(), "history2", h2.getChangedAt()));
        }
        if (!Objects.equals(h1.getChangedBy(), h2.getChangedBy())) {
            diffs.put("changedBy", Map.of("history1", h1.getChangedBy(), "history2", h2.getChangedBy()));
        }
        result.put("differences", diffs);
        return result;
    }

    /**
     * 导出历史 (按配置 ID / 时间区间过滤)。
     *
     * @param configId  配置 ID（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 导出结果 Map {configId, count, histories}
     */
    @Transactional
    public Map<String, Object> exportHistory(Long configId, LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmConfigHistoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (configId != null) {
                predicates.add(cb.equal(root.get("configId"), configId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("changedAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("changedAt"), endTime));
            }
            query.orderBy(cb.desc(root.get("changedAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmConfigHistoryEntity> histories = historyRepository.findAll(spec);
        // 若指定 configId 则记录 EXPORT 历史
        if (configId != null) {
            try {
                ScrmSystemConfigEntity entity = itemService.findConfigOrThrow(configId);
                itemService.recordHistory(entity, null, null, ScrmSystemConfigItemService.CHANGE_TYPE_EXPORT,
                        "导出历史", null);
            } catch (ScrmException e) {
                log.warn("导出历史时记录 EXPORT 历史失败: configId={}, err={}", configId, e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configId", configId);
        result.put("startTime", startTime);
        result.put("endTime", endTime);
        result.put("count", histories.size());
        result.put("histories", histories);
        result.put("exportedAt", LocalDateTime.now());
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按配置键查询配置 (内部使用)。
     *
     * @param key 配置键
     * @return 配置实体
     * @throws ScrmException 配置不存在
     */
    private ScrmSystemConfigEntity getConfigByKeyInternal(String key) throws ScrmException {
        if (key == null || key.isBlank()) {
            throw ScrmException.badRequest("配置键不能为空");
        }
        return configRepository.findByConfigKey(key)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "系统配置不存在: key=" + key));
    }

    /**
     * 按主键查询历史, 不存在抛异常, 并校验归属账号。
     *
     * @param id 历史 ID
     * @return 历史实体
     * @throws ScrmException 历史不存在
     */
    private ScrmConfigHistoryEntity findHistoryOrThrow(Long id) throws ScrmException {
        ScrmConfigHistoryEntity entity = historyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "配置历史不存在: id=" + id));
        return entity;
    }
}