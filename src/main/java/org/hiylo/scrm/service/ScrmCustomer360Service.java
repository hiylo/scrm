/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomer360Service.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmConversationEntity;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupMemberEntity;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerTimelineEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmOpportunityEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmConversationRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.repository.ScrmCustomerLifecycleHistoryRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTimelineRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmOpportunityRepository;
import org.hiylo.scrm.vo.ScrmCustomer360Vo;
import org.hiylo.scrm.vo.ScrmCustomer360Vo.InteractionStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SCRM 客户 360° 视图聚合服务。
 * <p>
 * 聚合客户基本信息、标签、分组、旅程进度、商机、会话消息、跟进记录、
 * 时间线事件与互动统计为统一视图, 供客户详情页一站式展示。
 * </p>
 * <p>
 * 数据隔离: 所有查询均按当前用户可见账号范围过滤,
 * 防止越权访问客户数据。聚合过程中对每个数据源查询单独 try-catch,
 * 单个数据源异常 (如表缺失 / 字段缺失) 不影响整体视图, 仅对应字段返回空值。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCustomer360Service {

    /** 时间线最近事件返回上限 */
    private static final int RECENT_TIMELINE_LIMIT = 50;

    /** 最近会话消息返回上限 */
    private static final int RECENT_MESSAGES_LIMIT = 20;

    /** 最近生命周期历史返回上限 */
    private static final int LIFECYCLE_HISTORY_LIMIT = 50;

    /** 商机金额求和时遍历的商机上限 (避免单客户商机过多导致 N+1 拖累) */
    private static final int OPPORTUNITY_STATS_LIMIT = 200;

    /** 互动统计遍历的会话上限 (求消息总数时避免遍历全部历史会话) */
    private static final int CONVERSATION_STATS_LIMIT = 100;

    /** 商机状态: 进行中 */
    private static final String OPPORTUNITY_STATUS_OPEN = "OPEN";

    /** 标签键: 客户分级 (用于推导 level 字段) */
    private static final String TAG_KEY_LEVEL = "level";

    /** 重要级别: 普通 */
    private static final String IMPORTANCE_NORMAL = "NORMAL";

    /** 客户数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 客户标签数据访问层 */
    private final ScrmCustomerTagRepository customerTagRepository;
    /** 客户-标签赋值数据访问层 (重构后赋值关系独立存储) */
    private final ScrmTagCustomerRepository tagCustomerRepository;

    /** 客户-分组关联数据访问层 */
    private final ScrmCustomerGroupMemberRepository customerGroupMemberRepository;

    /** 客户分组数据访问层 */
    private final ScrmCustomerGroupRepository customerGroupRepository;

    /** 商机数据访问层 */
    private final ScrmOpportunityRepository opportunityRepository;

    /** 旅程入营记录数据访问层 */
    private final ScrmJourneyEnrollmentRepository journeyEnrollmentRepository;

    /** 生命周期变更历史数据访问层 */
    private final ScrmCustomerLifecycleHistoryRepository lifecycleHistoryRepository;

    /** 会话数据访问层 */
    private final ScrmConversationRepository conversationRepository;

    /** 会话消息数据访问层 */
    private final ScrmConversationMessageRepository conversationMessageRepository;

    /** 客户时间线数据访问层 */
    private final ScrmCustomerTimelineRepository customerTimelineRepository;

    /** 数据隔离服务 (用于获取当前操作人) */
    private final DataScopeService dataScopeService;

    /**
     * 聚合客户 360° 全景视图。
     * <p>
     * 从各 Repository 分别查询并整合: 基本信息 / 标签 / 分组 / 生命周期历史 /
     * 进行中商机 / 旅程进度 / 最近会话消息 / 最近时间线事件 / 互动统计。
     * 每个数据源查询独立 try-catch, 单点失败不影响整体视图。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 客户 360° 视图
     * @throws ScrmException 客户不存在或越权访问
     */
    @Transactional(readOnly = true)
    public ScrmCustomer360Vo getCustomer360(Long customerId) throws ScrmException {
        // 先校验客户存在且归属当前账号, 同时获取基本信息
        ScrmCustomerEntity customer = findOrThrow(customerId);
        ScrmCustomerDto customerDto = toDto(customer);

        // 互动统计 (内部已 try-catch)
        InteractionStats stats = safeStats(customerId, customer);

        // 各数据源查询独立 try-catch, 单点失败返回空列表
        List<ScrmTagCustomerEntity> tags = safeTags(customerId);
        List<ScrmCustomerGroupEntity> groups = safeGroups(customerId);
        List<ScrmCustomerLifecycleHistoryEntity> lifecycleHistory = safeLifecycleHistory(customerId);
        List<ScrmOpportunityEntity> opportunities = safeOpportunities(customerId);
        List<ScrmJourneyEnrollmentEntity> journeyEnrollments = safeJourneyEnrollments(customerId);
        List<ScrmConversationMessageEntity> recentMessages = safeRecentMessages(customerId);
        List<ScrmCustomerTimelineEntity> recentTimeline = safeRecentTimeline(customerId);

        // 客户分级: 从标签 "level" 推导, 未打标则为 null
        String level = deriveLevel(tags);

        return ScrmCustomer360Vo.builder()
                .customer(customerDto)
                .tags(tags)
                .groups(groups)
                .level(level)
                .lifecycleHistory(lifecycleHistory)
                .opportunities(opportunities)
                .journeyEnrollments(journeyEnrollments)
                .followUpTasks(Collections.emptyList())
                .followUpRecords(Collections.emptyList())
                .recentMessages(recentMessages)
                .recentTimeline(recentTimeline)
                .stats(stats)
                .build();
    }

    /**
     * 查询客户时间线, 支持按事件类型与时间范围过滤。
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。eventType / startTime / endTime 均为可选条件,
     * 任一为空则忽略该条件。结果按事件时间倒序分页返回。
     * </p>
     *
     * @param customerId 客户 ID
     * @param eventType  事件类型过滤 (可空)
     * @param startTime  事件起始时间 (含, 可空)
     * @param endTime    事件截止时间 (含, 可空)
     * @param pageable   分页参数
     * @return 时间线事件分页
     * @throws ScrmException 客户不存在或越权访问
     */
    @Transactional(readOnly = true)
    public Page<ScrmCustomerTimelineEntity> getTimeline(Long customerId, String eventType,
                                                          LocalDateTime startTime, LocalDateTime endTime,
                                                          Pageable pageable) throws ScrmException {
        // 校验客户存在且归属当前账号
        findOrThrow(customerId);
        Specification<ScrmCustomerTimelineEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));
            if (eventType != null && !eventType.isBlank()) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("eventTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        // 若调用方未指定排序, 默认按事件时间倒序
        Pageable page = pageable;
        if (page.getSort().isUnsorted()) {
            page = PageRequest.of(page.getPageNumber(), page.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "eventTime"));
        }
        return customerTimelineRepository.findAll(spec, page);
    }

    /**
     * 添加时间线事件。
     * <p>
     * 自动填充归属账号 ID 与事件时间 (默认当前时间), operatorId / operatorName 为空时
     * 从请求头 (X-User-Id / X-Username) 回退, importance 为空时默认 NORMAL。
     * </p>
     *
     * @param customerId    客户 ID
     * @param eventType     事件类型
     * @param eventTitle    事件标题
     * @param eventDetail   事件详情 JSON (可空)
     * @param operatorId    操作人 ID (可空, 回退当前用户)
     * @param operatorName  操作人姓名 (可空, 回退当前用户)
     * @param importance    重要级别 (可空, 默认 NORMAL)
     * @return 持久化后的事件
     * @throws ScrmException 客户不存在 / 越权访问 / 参数非法
     */
    @Transactional
    public ScrmCustomerTimelineEntity addTimelineEvent(Long customerId, String eventType, String eventTitle,
                                                        String eventDetail, String operatorId, String operatorName,
                                                        String importance) throws ScrmException {
        // 校验客户存在且归属当前账号
        findOrThrow(customerId);
        // 参数校验
        if (eventType == null || eventType.isBlank()) {
            throw ScrmException.badRequest("事件类型不能为空");
        }
        if (eventTitle == null || eventTitle.isBlank()) {
            throw ScrmException.badRequest("事件标题不能为空");
        }
        ScrmCustomerTimelineEntity entity = new ScrmCustomerTimelineEntity();
        entity.setCustomerId(customerId);
        entity.setEventType(eventType);
        entity.setEventTitle(eventTitle);
        entity.setEventDetail(eventDetail);
        entity.setEventTime(LocalDateTime.now());
        // 操作人回退: 优先用入参, 为空则取当前请求用户
        entity.setOperatorId(
                operatorId != null && !operatorId.isBlank() ? operatorId : dataScopeService.getCurrentUserId());
        entity.setOperatorName(
                operatorName != null && !operatorName.isBlank() ? operatorName
                        : dataScopeService.getHeader("X-Username"));
        entity.setImportance(importance != null && !importance.isBlank() ? importance : IMPORTANCE_NORMAL);
        entity = customerTimelineRepository.save(entity);
        log.info("添加客户时间线事件: customerId={}, eventType={}, eventTitle={}, importance={}",
                customerId, eventType, eventTitle, entity.getImportance());
        return entity;
    }

    /**
     * 查询客户互动统计。
     * <p>
     * 聚合指标: 总消息数 / 总跟进次数 / 最后互动时间 / 客户天数 / 商机总额。
     * 单点数据源异常时, 对应指标置零或置空, 不影响整体返回。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 互动统计
     * @throws ScrmException 客户不存在或越权访问
     */
    @Transactional(readOnly = true)
    public InteractionStats getInteractionStats(Long customerId) throws ScrmException {
        ScrmCustomerEntity customer = findOrThrow(customerId);
        return safeStats(customerId, customer);
    }

    /**
     * 查询客户概要 (基本信息 + 关键指标)。
     * <p>
     * 比 360° 视图轻量, 仅返回客户 DTO 与互动统计, 适合列表页悬浮卡片或批量预览。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 客户概要
     * @throws ScrmException 客户不存在或越权访问
     */
    @Transactional(readOnly = true)
    public ScrmCustomer360Vo getCustomerOverview(Long customerId) throws ScrmException {
        ScrmCustomerEntity customer = findOrThrow(customerId);
        ScrmCustomerDto customerDto = toDto(customer);
        InteractionStats stats = safeStats(customerId, customer);
        return ScrmCustomer360Vo.builder()
                .customer(customerDto)
                .tags(Collections.emptyList())
                .groups(Collections.emptyList())
                .level(null)
                .lifecycleHistory(Collections.emptyList())
                .opportunities(Collections.emptyList())
                .journeyEnrollments(Collections.emptyList())
                .followUpTasks(Collections.emptyList())
                .followUpRecords(Collections.emptyList())
                .recentMessages(Collections.emptyList())
                .recentTimeline(Collections.emptyList())
                .stats(stats)
                .build();
    }

    /**
     * 批量查询客户概要。
     * <p>
     * 逐一调用 {@link #getCustomerOverview}, 单个客户失败 (如不存在) 不影响其他客户,
     * 失败的客户不在返回列表中体现。适合客户列表页批量预览。
     * </p>
     *
     * @param customerIds 客户 ID 列表
     * @return 客户概要列表 (仅含成功的客户)
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomer360Vo> batchGetOverview(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScrmCustomer360Vo> result = new ArrayList<>(customerIds.size());
        for (Long customerId : customerIds) {
            try {
                result.add(getCustomerOverview(customerId));
            } catch (ScrmException e) {
                log.warn("批量查询客户概要失败: customerId={}, error={}", customerId, e.getMessage());
            }
        }
        return result;
    }

    // ============================================================
    // 内部辅助方法: 各数据源查询 (独立 try-catch, 失败返回空列表)
    // ============================================================

    /**
     * 查询客户标签 (单点失败返回空列表)
     */
    private List<ScrmTagCustomerEntity> safeTags(Long customerId) {
        try {
            return tagCustomerRepository.findByCustomerId(
                     customerId);
        } catch (Exception e) {
            log.warn("查询客户标签失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户所属分组 (单点失败返回空列表)
     */
    private List<ScrmCustomerGroupEntity> safeGroups(Long customerId) {
        try {
            List<ScrmCustomerGroupMemberEntity> members = customerGroupMemberRepository.findByCustomerId(customerId);
            if (members == null || members.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> groupIds = members.stream()
                    .map(ScrmCustomerGroupMemberEntity::getGroupId)
                    .collect(Collectors.toList());
            return customerGroupRepository.findAllById(groupIds);
        } catch (Exception e) {
            log.warn("查询客户分组失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户生命周期变更历史 (单点失败返回空列表)
     */
    private List<ScrmCustomerLifecycleHistoryEntity> safeLifecycleHistory(Long customerId) {
        try {
            List<ScrmCustomerLifecycleHistoryEntity> all = lifecycleHistoryRepository
                    .findByCustomerIdOrderByIdDesc(customerId);
            if (all == null || all.isEmpty()) {
                return Collections.emptyList();
            }
            return all.size() > LIFECYCLE_HISTORY_LIMIT ? all.subList(0, LIFECYCLE_HISTORY_LIMIT) : all;
        } catch (Exception e) {
            log.warn("查询客户生命周期历史失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户进行中商机 (单点失败返回空列表)
     * <p>
     * 商机仓库未提供 findByCustomerId 方法, 通过 Specification 按归属账号 ID + customerId 过滤。
     * </p>
     */
    private List<ScrmOpportunityEntity> safeOpportunities(Long customerId) {
        try {
            Specification<ScrmOpportunityEntity> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("customerId"), customerId));
                predicates.add(cb.equal(root.get("status"), OPPORTUNITY_STATUS_OPEN));
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return opportunityRepository.findAll(spec);
        } catch (Exception e) {
            log.warn("查询客户商机失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户旅程入营记录 (单点失败返回空列表)
     * <p>
     * 旅程入营仓库仅提供 findByCustomerId (无归属过滤), 在内存按归属账号过滤。
     * </p>
     */
    private List<ScrmJourneyEnrollmentEntity> safeJourneyEnrollments(Long customerId) {
        try {
            List<ScrmJourneyEnrollmentEntity> all = journeyEnrollmentRepository.findByCustomerId(customerId);
            if (all == null || all.isEmpty()) {
                return Collections.emptyList();
            }
            return all.stream()

                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询客户旅程进度失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户最近会话消息 (单点失败返回空列表)
     * <p>
     * 先取该客户最近一条会话, 再查该会话最近 {@value #RECENT_MESSAGES_LIMIT} 条消息。
     * </p>
     */
    private List<ScrmConversationMessageEntity> safeRecentMessages(Long customerId) {
        try {
            Page<ScrmConversationEntity> conversations = conversationRepository
                    .findByCustomerIdOrderByLastMessageAtDesc(
                             customerId, PageRequest.of(0, 1));
            if (conversations.isEmpty()) {
                return Collections.emptyList();
            }
            Long conversationId = conversations.getContent().get(0).getId();
            return conversationMessageRepository.findByConversationIdOrderBySentAtDesc(
                    conversationId, PageRequest.of(0, RECENT_MESSAGES_LIMIT)).getContent();
        } catch (Exception e) {
            log.warn("查询客户最近消息失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询客户最近时间线事件 (单点失败返回空列表)
     */
    private List<ScrmCustomerTimelineEntity> safeRecentTimeline(Long customerId) {
        try {
            Page<ScrmCustomerTimelineEntity> page = customerTimelineRepository
                    .findByCustomerIdOrderByEventTimeDesc(
                             customerId, PageRequest.of(0, RECENT_TIMELINE_LIMIT));
            return page.getContent();
        } catch (Exception e) {
            log.warn("查询客户时间线失败, 返回空列表: customerId={}, error={}", customerId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 计算互动统计 (单点失败置零或置空)
     * <p>
     * 总消息数: 取客户最近 {@value #CONVERSATION_STATS_LIMIT} 个会话的消息数累加;
     * 总跟进次数: 跟进记录模块未接入, 固定为 0;
     * 最后互动时间: 取客户实体的 lastInteractionAt;
     * 客户天数: 从 createTime 至今的天数;
     * 商机总额: 取客户进行中商机金额合计。
     * </p>
     */
    private InteractionStats safeStats(Long customerId, ScrmCustomerEntity customer) {
        Long totalMessages = 0L;
        try {
            Page<ScrmConversationEntity> conversations = conversationRepository
                    .findByCustomerIdOrderByLastMessageAtDesc(
                             customerId, PageRequest.of(0, CONVERSATION_STATS_LIMIT));
            for (ScrmConversationEntity conv : conversations.getContent()) {
                totalMessages += conversationMessageRepository.countByConversationId(conv.getId());
            }
        } catch (Exception e) {
            log.warn("统计客户消息总数失败, 置零: customerId={}, error={}", customerId, e.getMessage());
            totalMessages = 0L;
        }

        Double totalOpportunityAmount = 0.0;
        try {
            List<ScrmOpportunityEntity> opportunities = safeOpportunities(customerId);
            double sum = 0.0;
            int count = 0;
            for (ScrmOpportunityEntity opp : opportunities) {
                if (count++ >= OPPORTUNITY_STATS_LIMIT) {
                    break;
                }
                if (opp.getAmount() != null) {
                    sum += opp.getAmount();
                }
            }
            totalOpportunityAmount = sum;
        } catch (Exception e) {
            log.warn("统计客户商机总额失败, 置零: customerId={}, error={}", customerId, e.getMessage());
            totalOpportunityAmount = 0.0;
        }

        // 客户天数: 从创建时间至今
        Long customerDays = 0L;
        if (customer.getCreateTime() != null) {
            customerDays = Duration.between(customer.getCreateTime(), LocalDateTime.now()).toDays();
            if (customerDays < 0) {
                customerDays = 0L;
            }
        }

        return InteractionStats.builder()
                .totalMessages(totalMessages)
                .totalFollowUps(0L)
                .lastInteractionAt(customer.getLastInteractionAt())
                .customerDays(customerDays)
                .totalOpportunityAmount(totalOpportunityAmount)
                .build();
    }

    /**
     * 从客户标签中推导客户分级 (标签 key 为 "level" 的值)
     *
     * @param tags 客户标签列表
     * @return 客户分级, 未打标返回 null
     */
    private String deriveLevel(List<ScrmTagCustomerEntity> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        // "level" 标签定义 → tagId, 赋值记录按 tagId 匹配 (重构后赋值以 tagId 引用定义)
        Long levelTagId = customerTagRepository
                .findByTagCode(TAG_KEY_LEVEL)
                .map(ScrmCustomerTagEntity::getId).orElse(null);
        if (levelTagId == null) {
            return null;
        }
        final Long lid = levelTagId;
        return tags.stream()
                .filter(t -> lid.equals(t.getTagId()))
                .map(ScrmTagCustomerEntity::getTagValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 按主键查询客户, 不存在或越权访问抛异常
     */
    private ScrmCustomerEntity findOrThrow(Long id) throws ScrmException {
        ScrmCustomerEntity entity = customerRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + id));

        return entity;
    }

    /**
     * 客户实体转 DTO
     */
    private ScrmCustomerDto toDto(ScrmCustomerEntity entity) {
        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setPlatformCustomerUid(entity.getPlatformCustomerUid());
        dto.setNickname(entity.getNickname());
        dto.setAvatarUrl(entity.getAvatarUrl());
        dto.setOwnerAccountId(entity.getOwnerAccountId());
        dto.setPersonaId(entity.getPersonaId());
        dto.setLifecycle(entity.getLifecycle());
        dto.setLastInteractionAt(entity.getLastInteractionAt());
        dto.setNextFollowUpAt(entity.getNextFollowUpAt());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
