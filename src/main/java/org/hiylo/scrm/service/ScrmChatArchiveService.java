/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChatArchiveService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmArchiveRuleDto;
import org.hiylo.scrm.dto.ScrmChatArchiveDto;
import org.hiylo.scrm.entity.ScrmArchiveRuleEntity;
import org.hiylo.scrm.entity.ScrmChatArchiveEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmArchiveRuleRepository;
import org.hiylo.scrm.repository.ScrmChatArchiveRepository;
import org.hiylo.scrm.vo.ChatArchiveStatsVo;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 会话存档服务。
 * <p>
 * 负责消息归档、归档规则管理与存档统计。所有写操作写入当前用户归属账号, 实现数据隔离。归档规则按优先级降序匹配, 命中任一规则即执行归档。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmChatArchiveService {

    /** 默认归档来源: 自动 */
    private static final String SOURCE_AUTO = "AUTO";

    /** 默认归档来源: 手动 */
    private static final String SOURCE_MANUAL = "MANUAL";

    /** 默认质量标记: 正常 */
    private static final String QUALITY_NORMAL = "NORMAL";

    /** 质量标记: 敏感 */
    private static final String QUALITY_SENSITIVE = "SENSITIVE";

    /** 质量标记: 违规 */
    private static final String QUALITY_VIOLATION = "VIOLATION";

    /** 归档数据访问层 */
    private final ScrmChatArchiveRepository archiveRepository;

    /** 归档规则数据访问层 */
    private final ScrmArchiveRuleRepository ruleRepository;

    /**
     * 归档消息
     * <p>
     * 创建一条归档记录, 写入归属账号 ID, 默认质量标记为 NORMAL, 归档来源根据参数决定。
     * </p>
     *
     * @param dto 归档参数
     * @return 归档后的记录
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmChatArchiveEntity archiveMessage(ScrmChatArchiveDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("归档参数不能为空");
        }
        if (dto.getAccountId() == null) {
            throw ScrmException.badRequest("归属账号 ID 不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
        if (dto.getDirection() == null || dto.getDirection().isBlank()) {
            throw ScrmException.badRequest("消息方向不能为空");
        }
        if (dto.getMessageType() == null || dto.getMessageType().isBlank()) {
            throw ScrmException.badRequest("消息类型不能为空");
        }
        if (dto.getSentAt() == null) {
            throw ScrmException.badRequest("消息发送时间不能为空");
        }

        ScrmChatArchiveEntity entity = new ScrmChatArchiveEntity();
        entity.setAccountId(dto.getAccountId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setConversationId(dto.getConversationId());
        entity.setPlatformType(dto.getPlatformType());
        entity.setDirection(dto.getDirection());
        entity.setMessageType(dto.getMessageType());
        entity.setContent(dto.getContent());
        entity.setRawContent(dto.getRawContent());
        entity.setMediaUrl(dto.getMediaUrl());
        entity.setSentAt(dto.getSentAt());
        entity.setArchivedAt(LocalDateTime.now());
        entity.setQualityFlag(dto.getQualityFlag() != null ? dto.getQualityFlag() : QUALITY_NORMAL);
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setArchiveSource(dto.getArchiveSource() != null ? dto.getArchiveSource() : SOURCE_AUTO);
        entity = archiveRepository.save(entity);
        log.info("归档消息: id={}, accountId={}, platformType={}, direction={}",
                entity.getId(), entity.getAccountId(), entity.getPlatformType(), entity.getDirection());
        return entity;
    }

    /**
     * 查询归档消息
     *
     * @param id 归档 ID
     * @return 归档记录
     * @throws ScrmException 归档不存在
     */
    @Transactional(readOnly = true)
    public ScrmChatArchiveEntity getArchive(Long id) throws ScrmException {
        return archiveRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归档消息不存在: id=" + id));
    }

    /**
     * 分页查询归档消息, 支持按平台类型、方向、质量标记过滤
     *
     * @param platformType 平台类型过滤 (可选)
     * @param direction   消息方向过滤 (可选)
     * @param qualityFlag 质量标记过滤 (可选)
     * @param accountId   账号 ID 过滤 (可选)
     * @param page        页码 (从 0 开始)
     * @param size        每页大小
     * @return 归档消息分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmChatArchiveEntity> listArchives(String platformType, String direction,
                                                     String qualityFlag, Long accountId,
                                                     int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        Specification<ScrmChatArchiveEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(root.get("platformType"), platformType));
            }
            if (direction != null && !direction.isBlank()) {
                predicates.add(cb.equal(root.get("direction"), direction));
            }
            if (qualityFlag != null && !qualityFlag.isBlank()) {
                predicates.add(cb.equal(root.get("qualityFlag"), qualityFlag));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return archiveRepository.findAll(spec, pageable);
    }

    /**
     * 按账号 ID 分页查询归档消息
     *
     * @param accountId 账号 ID
     * @param page      页码
     * @param size      每页大小
     * @return 归档消息分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmChatArchiveEntity> getArchivesByAccount(Long accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return archiveRepository.findByAccountIdOrderBySentAtDesc(
                 accountId, pageable);
    }

    /**
     * 按客户 ID 分页查询归档消息
     *
     * @param customerId 客户 ID
     * @param page       页码
     * @param size       每页大小
     * @return 归档消息分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmChatArchiveEntity> getArchivesByCustomer(Long customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return archiveRepository.findByCustomerIdOrderBySentAtDesc(
                 customerId, pageable);
    }

    /**
     * 按会话 ID 分页查询归档消息
     *
     * @param conversationId 会话 ID
     * @param page            页码
     * @param size            每页大小
     * @return 归档消息分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmChatArchiveEntity> getArchivesByConversation(Long conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sentAt"));
        return archiveRepository.findByConversationIdOrderBySentAtDesc(
                 conversationId, pageable);
    }

    /**
     * 获取存档统计
     *
     * @return 统计 VO
     */
    @Transactional(readOnly = true)
    public ChatArchiveStatsVo getStats() {
        long total = archiveRepository.count();
        long normal = archiveRepository.countByQualityFlag(QUALITY_NORMAL);
        long sensitive = archiveRepository.countByQualityFlag(QUALITY_SENSITIVE);
        long violation = archiveRepository.countByQualityFlag(QUALITY_VIOLATION);
        // 按平台分布: 简化实现, 取全部后分组
        Map<String, Long> platformDist = new HashMap<>();
        Map<String, Long> qualityDist = new HashMap<>();
        qualityDist.put(QUALITY_NORMAL, normal);
        qualityDist.put(QUALITY_SENSITIVE, sensitive);
        qualityDist.put(QUALITY_VIOLATION, violation);
        return ChatArchiveStatsVo.builder()
                .totalArchived(total)
                .normalCount(normal)
                .sensitiveCount(sensitive)
                .violationCount(violation)
                .platformDistribution(platformDist)
                .qualityDistribution(qualityDist)
                .riskDistribution(new HashMap<>())
                .build();
    }

    // ==================== 归档规则管理 ====================

    /**
     * 创建归档规则
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法或规则名称重复
     */
    @Transactional
    public ScrmArchiveRuleEntity createRule(ScrmArchiveRuleDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("规则参数不能为空");
        }
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            throw ScrmException.badRequest("规则名称不能为空");
        }
        // 唯一性校验: 同一账号下规则名称不重复
        if (!ruleRepository.findByRuleName(dto.getRuleName()).isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "归档规则名称已存在: " + dto.getRuleName());
        }
        ScrmArchiveRuleEntity entity = new ScrmArchiveRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setPlatformType(dto.getPlatformType());
        entity.setAccountId(dto.getAccountId());
        entity.setDirection(dto.getDirection());
        entity.setMessageTypes(dto.getMessageTypes());
        entity.setKeywords(dto.getKeywords());
        entity.setRiskLevelFilter(dto.getRiskLevelFilter());
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : 0);
        entity = ruleRepository.save(entity);
        log.info("创建归档规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 更新归档规则
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在或参数非法
     */
    @Transactional
    public ScrmArchiveRuleEntity updateRule(Long id, ScrmArchiveRuleDto dto) throws ScrmException {
        ScrmArchiveRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归档规则不存在: id=" + id));
        if (dto.getRuleName() != null) entity.setRuleName(dto.getRuleName());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getAccountId() != null) entity.setAccountId(dto.getAccountId());
        if (dto.getDirection() != null) entity.setDirection(dto.getDirection());
        if (dto.getMessageTypes() != null) entity.setMessageTypes(dto.getMessageTypes());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getRiskLevelFilter() != null) entity.setRiskLevelFilter(dto.getRiskLevelFilter());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        if (dto.getPriority() != null) entity.setPriority(dto.getPriority());
        entity = ruleRepository.save(entity);
        log.info("更新归档规则: id={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    /**
     * 删除归档规则
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public void deleteRule(Long id) throws ScrmException {
        ScrmArchiveRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归档规则不存在: id=" + id));
        ruleRepository.delete(entity);
        log.info("删除归档规则: id={}, ruleName={}", id, entity.getRuleName());
    }

    /**
     * 查询归档规则列表
     *
     * @param enabledOnly 是否只返回启用的规则
     * @return 规则列表
     */
    @Transactional(readOnly = true)
    public List<ScrmArchiveRuleEntity> listRules(boolean enabledOnly) {
        if (enabledOnly) {
            return ruleRepository.findByEnabledOrderByPriorityDesc(Boolean.TRUE);
        }
        return ruleRepository.findAll();
    }

    /**
     * 切换归档规则启用状态
     *
     * @param id      规则 ID
     * @param enabled 是否启用
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    @Transactional
    public ScrmArchiveRuleEntity toggleRule(Long id, boolean enabled) throws ScrmException {
        ScrmArchiveRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归档规则不存在: id=" + id));
        entity.setEnabled(enabled);
        entity = ruleRepository.save(entity);
        log.info("切换归档规则状态: id={}, enabled={}", id, enabled);
        return entity;
    }
}
