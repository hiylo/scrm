/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommunityService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCommunityBroadcastDto;
import org.hiylo.scrm.dto.ScrmCommunityDto;
import org.hiylo.scrm.dto.ScrmCommunityMemberDto;
import org.hiylo.scrm.dto.ScrmCommunityMessageDto;
import org.hiylo.scrm.dto.ScrmCommunitySopDto;
import org.hiylo.scrm.entity.ScrmCommunityEntity;
import org.hiylo.scrm.entity.ScrmCommunityMemberEntity;
import org.hiylo.scrm.entity.ScrmCommunityMessageEntity;
import org.hiylo.scrm.entity.ScrmCommunitySopEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCommunityMemberRepository;
import org.hiylo.scrm.repository.ScrmCommunityMessageRepository;
import org.hiylo.scrm.repository.ScrmCommunityRepository;
import org.hiylo.scrm.repository.ScrmCommunitySopRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

/**
 * SCRM 社群运营管理服务。
 * <p>
 * 承载社群运营的核心能力: 社群增删改查与启用/停用, 群成员管理 (加入/移除/角色/禁言/不活跃查询),
 * 群 SOP (自动化触发的发消息/欢迎语/提醒/打标签/通知管理员), 群消息归档与多群广播,
 * 以及社群统计 (群统计/总览/活跃度趋势/活跃群排行)。
 * </p>
 * <p>
 * 活跃度评分 (0-100) 综合今日消息数 / 活跃成员占比 / 今日新增占比计算, 由
 * {@link #updateActivityScore} 刷新并写回社群实体。SOP 执行与群广播为模拟实现,
 * 仅记录消息与日志, 不调用真实平台 API。
 * </p>
 * <p>
 * 所有写操作写入归属账号实现数据隔离, 越权访问按不存在处理。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCommunityService {

    // ==================== 状态常量 ====================

    /** 社群状态: 活跃 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 社群状态: 停用 */
    private static final String STATUS_INACTIVE = "INACTIVE";
    /** 社群状态: 已解散 */
    private static final String STATUS_DISSOLVED = "DISSOLVED";

    /** 成员状态: 在群 */
    private static final String MEMBER_STATUS_ACTIVE = "ACTIVE";
    /** 成员状态: 被移除 */
    private static final String MEMBER_STATUS_REMOVED = "REMOVED";
    /** 成员状态: 已退群 */
    private static final String MEMBER_STATUS_LEFT = "LEFT";
    /** 成员状态: 禁言 */
    private static final String MEMBER_STATUS_MUTED = "MUTED";

    /** 群角色: 群主 */
    private static final String ROLE_OWNER = "OWNER";
    /** 群角色: 管理员 */
    private static final String ROLE_ADMIN = "ADMIN";
    /** 群角色: 普通成员 */
    private static final String ROLE_MEMBER = "MEMBER";
    /** 群角色: 访客 */
    private static final String ROLE_GUEST = "GUEST";

    /** 入群方式: 邀请 */
    private static final String JOIN_TYPE_INVITED = "INVITED";

    /** SOP 触发类型: 定时 */
    private static final String TRIGGER_TIME_BASED = "TIME_BASED";
    /** SOP 触发类型: 手动 */
    private static final String TRIGGER_MANUAL = "MANUAL";

    /** 发送者类型: 系统 */
    private static final String SENDER_TYPE_SYSTEM = "SYSTEM";
    /** 消息类型: 文本 */
    private static final String MESSAGE_TYPE_TEXT = "TEXT";

    // ==================== 默认值常量 ====================

    /** 默认最大成员数 */
    private static final int DEFAULT_MAX_MEMBERS = 500;
    /** 活跃度评分上限 */
    private static final double ACTIVITY_SCORE_MAX = 100.0;
    /** 活跃度评分下限 */
    private static final double ACTIVITY_SCORE_MIN = 0.0;
    /** 活跃度评分 - 消息数归一化基准 (每成员每天 5 条视为满分) */
    private static final double MESSAGES_PER_MEMBER_FULL = 5.0;
    /** 活跃群排行默认条数 */
    private static final int DEFAULT_TOP_LIMIT = 10;
    /** 排行榜最大条数 */
    private static final int MAX_TOP_LIMIT = 100;

    // ==================== 数据访问层 ====================

    /** 社群数据访问层 */
    private final ScrmCommunityRepository communityRepository;
    /** 社群成员数据访问层 */
    private final ScrmCommunityMemberRepository memberRepository;
    /** 社群 SOP 数据访问层 */
    private final ScrmCommunitySopRepository sopRepository;
    /** 社群消息数据访问层 */
    private final ScrmCommunityMessageRepository messageRepository;

    // ============================================================
    // 社群管理
    // ============================================================

    /**
     * 创建社群。
     * <p>参数校验: communityName / platformType / communityType / ownerId 必填,
     * 默认 status=ACTIVE, memberCount=0, activityScore=0, maxMembers=500。</p>
     *
     * @param dto 社群参数
     * @return 创建后的社群
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCommunityDto createCommunity(ScrmCommunityDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("社群参数不能为空");
        }
        ScrmCommunityEntity entity = new ScrmCommunityEntity();
        entity.setCommunityName(dto.getCommunityName());
        entity.setPlatformType(dto.getPlatformType());
        entity.setCommunityType(dto.getCommunityType());
        entity.setRoomId(dto.getRoomId());
        entity.setQrCode(dto.getQrCode());
        entity.setDescription(dto.getDescription());
        entity.setOwnerId(dto.getOwnerId());
        entity.setOwnerName(dto.getOwnerName());
        entity.setManagerId(dto.getManagerId());
        entity.setManagerName(dto.getManagerName());
        entity.setMemberCount(0);
        entity.setMaxMembers(dto.getMaxMembers() != null ? dto.getMaxMembers() : DEFAULT_MAX_MEMBERS);
        entity.setActiveMembers(0);
        entity.setTodayNewMembers(0);
        entity.setTodayMessages(0);
        entity.setActivityScore(0.0);
        entity.setTags(dto.getTags());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_ACTIVE);
        entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : LocalDateTime.now());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = communityRepository.save(entity);
        log.info("创建社群: id={}, communityName={}, platformType={}, communityType={}",
                entity.getId(), entity.getCommunityName(), entity.getPlatformType(), entity.getCommunityType());
        return toCommunityDto(entity);
    }

    /**
     * 更新社群信息 (部分更新, 仅非空字段生效)。
     *
     * @param id  社群 ID
     * @param dto 社群参数
     * @return 更新后的社群
     * @throws ScrmException 社群不存在
     */
    @Transactional
    public ScrmCommunityDto updateCommunity(Long id, ScrmCommunityDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("社群参数不能为空");
        }
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        if (dto.getCommunityName() != null) entity.setCommunityName(dto.getCommunityName());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getCommunityType() != null) entity.setCommunityType(dto.getCommunityType());
        if (dto.getRoomId() != null) entity.setRoomId(dto.getRoomId());
        if (dto.getQrCode() != null) entity.setQrCode(dto.getQrCode());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getOwnerId() != null) entity.setOwnerId(dto.getOwnerId());
        if (dto.getOwnerName() != null) entity.setOwnerName(dto.getOwnerName());
        if (dto.getManagerId() != null) entity.setManagerId(dto.getManagerId());
        if (dto.getManagerName() != null) entity.setManagerName(dto.getManagerName());
        if (dto.getMaxMembers() != null) entity.setMaxMembers(dto.getMaxMembers());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getCreatedAt() != null) entity.setCreatedAt(dto.getCreatedAt());
        entity = communityRepository.save(entity);
        log.info("更新社群: id={}, communityName={}", id, entity.getCommunityName());
        return toCommunityDto(entity);
    }

    /**
     * 删除社群。
     * <p>级联清理关联的成员与消息记录, 然后删除社群主记录。已解散的社群不允许再次删除。</p>
     *
     * @param id 社群 ID
     * @throws ScrmException 社群不存在
     */
    @Transactional
    public void deleteCommunity(Long id) throws ScrmException {
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        memberRepository.deleteByCommunityId(id);
        messageRepository.deleteByCommunityId(id);
        communityRepository.delete(entity);
        log.info("删除社群: id={}, communityName={}", id, entity.getCommunityName());
    }

    /**
     * 查询社群详情。
     *
     * @param id 社群 ID
     * @return 社群 DTO
     * @throws ScrmException 社群不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommunityDto getCommunity(Long id) throws ScrmException {
        return toCommunityDto(findCommunityOrThrow(id));
    }

    /**
     * 分页查询社群列表, 支持按平台类型 / 社群类型 / 状态 / 关键词过滤。
     *
     * @param platformType  平台类型过滤 (可空)
     * @param communityType 社群类型过滤 (可空)
     * @param status        状态过滤 (可空)
     * @param keyword       关键词过滤, 匹配社群名称 / 群主名称 (可空)
     * @param pageable      分页参数
     * @return 社群分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommunityDto> listCommunities(String platformType, String communityType,
                                                   String status, String keyword, Pageable pageable) {
        Specification<ScrmCommunityEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(root.get("platformType"), platformType));
            }
            if (communityType != null && !communityType.isBlank()) {
                predicates.add(cb.equal(root.get("communityType"), communityType));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("communityName")), kw),
                        cb.like(cb.lower(root.get("ownerName")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return communityRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toCommunityDto);
    }

    /**
     * 启用社群 (状态置为 ACTIVE)。
     *
     * @param id 社群 ID
     * @return 更新后的社群
     * @throws ScrmException 社群不存在
     */
    @Transactional
    public ScrmCommunityDto activateCommunity(Long id) throws ScrmException {
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        entity.setStatus(STATUS_ACTIVE);
        entity = communityRepository.save(entity);
        log.info("启用社群: id={}", id);
        return toCommunityDto(entity);
    }

    /**
     * 停用社群 (状态置为 INACTIVE)。
     *
     * @param id 社群 ID
     * @return 更新后的社群
     * @throws ScrmException 社群不存在
     */
    @Transactional
    public ScrmCommunityDto deactivateCommunity(Long id) throws ScrmException {
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        entity.setStatus(STATUS_INACTIVE);
        entity = communityRepository.save(entity);
        log.info("停用社群: id={}", id);
        return toCommunityDto(entity);
    }

    /**
     * 更新社群活跃度评分 (基于消息数 / 活跃成员 / 新增成员)。
     * <p>
     * 评分公式: score = 50 * activeRate + 30 * messageScore + 20 * growthScore
     * <ul>
     *   <li>activeRate = activeMembers / memberCount (活跃成员占比)</li>
     *   <li>messageScore = min(1, todayMessages / (memberCount * 5)) (每成员日均 5 条视为满分)</li>
     *   <li>growthScore = memberCount > 0 ? min(1, todayNewMembers / memberCount) : 0</li>
     * </ul>
     * 结果裁剪到 [0, 100] 后写回社群实体。
     * </p>
     *
     * @param id 社群 ID
     * @return 更新后的社群
     * @throws ScrmException 社群不存在
     */
    @Transactional
    public ScrmCommunityDto updateActivityScore(Long id) throws ScrmException {
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        entity.setActivityScore(calculateActivityScore(entity));
        entity = communityRepository.save(entity);
        log.info("更新社群活跃度评分: id={}, activityScore={}", id, entity.getActivityScore());
        return toCommunityDto(entity);
    }

    // ============================================================
    // 成员管理
    // ============================================================

    /**
     * 添加群成员。
     * <p>默认 role=MEMBER, joinType=INVITED, status=ACTIVE, isActive=true, messageCount=0,
     * joinAt 取 dto 值或当前时间。加入后社群成员数 +1, 今日新增 +1。</p>
     *
     * @param dto 成员参数
     * @return 创建后的成员
     * @throws ScrmException 参数非法 / 社群不存在
     */
    @Transactional
    public ScrmCommunityMemberDto addMember(ScrmCommunityMemberDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("成员参数不能为空");
        }
        if (dto.getCommunityId() == null) {
            throw ScrmException.badRequest("社群 ID 不能为空");
        }
        if (dto.getMemberName() == null || dto.getMemberName().isBlank()) {
            throw ScrmException.badRequest("成员名称不能为空");
        }
        ScrmCommunityEntity community = findCommunityOrThrow(dto.getCommunityId());
        ScrmCommunityMemberEntity entity = new ScrmCommunityMemberEntity();
        entity.setCommunityId(dto.getCommunityId());
        entity.setCustomerId(dto.getCustomerId());
        entity.setMemberName(dto.getMemberName());
        entity.setMemberAlias(dto.getMemberAlias());
        entity.setPlatformUid(dto.getPlatformUid());
        entity.setRole(dto.getRole() != null ? dto.getRole() : ROLE_MEMBER);
        entity.setJoinType(dto.getJoinType() != null ? dto.getJoinType() : JOIN_TYPE_INVITED);
        entity.setJoinAt(dto.getJoinAt() != null ? dto.getJoinAt() : LocalDateTime.now());
        entity.setLastActiveAt(dto.getLastActiveAt());
        entity.setMessageCount(0);
        entity.setIsActive(true);
        entity.setInvitedBy(dto.getInvitedBy());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : MEMBER_STATUS_ACTIVE);
        entity = memberRepository.save(entity);
        // 同步社群成员数与今日新增
        community.setMemberCount(safeInt(community.getMemberCount()) + 1);
        community.setTodayNewMembers(safeInt(community.getTodayNewMembers()) + 1);
        communityRepository.save(community);
        log.info("添加群成员: communityId={}, memberId={}, memberName={}",
                dto.getCommunityId(), entity.getId(), entity.getMemberName());
        return toMemberDto(entity);
    }

    /**
     * 移除群成员 (状态置为 REMOVED, 记录退群时间, 社群成员数 -1)。
     *
     * @param id     成员 ID
     * @param reason 移除原因 (可空, 仅记录日志)
     * @return 更新后的成员
     * @throws ScrmException 成员不存在
     */
    @Transactional
    public ScrmCommunityMemberDto removeMember(Long id, String reason) throws ScrmException {
        ScrmCommunityMemberEntity entity = findMemberOrThrow(id);
        entity.setStatus(MEMBER_STATUS_REMOVED);
        entity.setLeftAt(LocalDateTime.now());
        entity.setIsActive(false);
        entity = memberRepository.save(entity);
        // 社群成员数 -1 (下限 0)
        communityRepository.findById(entity.getCommunityId()).ifPresent(c -> {
            c.setMemberCount(Math.max(0, safeInt(c.getMemberCount()) - 1));
            communityRepository.save(c);
        });
        log.info("移除群成员: memberId={}, communityId={}, reason={}", id, entity.getCommunityId(), reason);
        return toMemberDto(entity);
    }

    /**
     * 更新成员群角色。
     *
     * @param id   成员 ID
     * @param role 群角色: OWNER/ADMIN/MEMBER/GUEST
     * @return 更新后的成员
     * @throws ScrmException 成员不存在 / 角色非法
     */
    @Transactional
    public ScrmCommunityMemberDto updateMemberRole(Long id, String role) throws ScrmException {
        if (role == null || (!role.equals(ROLE_OWNER) && !role.equals(ROLE_ADMIN) && !role.equals(ROLE_MEMBER) && !role.equals(ROLE_GUEST))) {
            throw ScrmException.badRequest("非法的群角色: " + role + ", 仅支持 OWNER/ADMIN/MEMBER/GUEST");
        }
        ScrmCommunityMemberEntity entity = findMemberOrThrow(id);
        entity.setRole(role);
        entity = memberRepository.save(entity);
        log.info("更新成员角色: memberId={}, role={}", id, role);
        return toMemberDto(entity);
    }

    /**
     * 查询成员详情。
     *
     * @param id 成员 ID
     * @return 成员 DTO
     * @throws ScrmException 成员不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommunityMemberDto getMember(Long id) throws ScrmException {
        return toMemberDto(findMemberOrThrow(id));
    }

    /**
     * 分页查询社群成员, 支持按角色 / 活跃状态 / 关键词过滤。
     *
     * @param communityId 社群 ID (可空, 不传则忽略)
     * @param role        群角色过滤 (可空)
     * @param isActive    活跃状态过滤 (可空)
     * @param keyword     关键词过滤, 匹配成员名称 / 群昵称 / 平台 UID (可空)
     * @param pageable    分页参数
     * @return 成员分页结果 (按 joinAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommunityMemberDto> listMembers(Long communityId, String role, Boolean isActive,
                                                     String keyword, Pageable pageable) {
        Specification<ScrmCommunityMemberEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (communityId != null) {
                predicates.add(cb.equal(root.get("communityId"), communityId));
            }
            if (role != null && !role.isBlank()) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("memberName")), kw),
                        cb.like(cb.lower(root.get("memberAlias")), kw),
                        cb.like(cb.lower(root.get("platformUid")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("joinAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return memberRepository.findAll(spec, ensureSort(pageable, "joinAt")).map(this::toMemberDto);
    }

    /**
     * 批量添加群成员。
     * <p>逐个调用 {@link #addMember}, 单个失败不影响其他成员, 返回成功数与失败列表。</p>
     *
     * @param communityId 社群 ID
     * @param members     成员参数列表
     * @return 批量操作结果
     */
    @Transactional
    public BatchResult batchAddMembers(Long communityId, List<ScrmCommunityMemberDto> members) {
        if (members == null || members.isEmpty()) {
            return new BatchResult(0, List.of());
        }
        int successCount = 0;
        List<Long> failures = new ArrayList<>();
        for (ScrmCommunityMemberDto dto : members) {
            try {
                dto.setCommunityId(communityId);
                addMember(dto);
                successCount++;
            } catch (ScrmException e) {
                log.warn("批量添加群成员失败: communityId={}, memberName={}, error={}",
                        communityId, dto.getMemberName(), e.getMessage());
                failures.add(dto.getCommunityId());
            }
        }
        log.info("批量添加群成员完成: communityId={}, total={}, success={}, failed={}",
                communityId, members.size(), successCount, failures.size());
        return new BatchResult(successCount, failures);
    }

    /**
     * 查询社群不活跃成员 (最后活跃时间早于指定天数前, 或从未活跃)。
     *
     * @param communityId 社群 ID
     * @param days        不活跃天数阈值 (最后活跃时间早于 now - days 视为不活跃)
     * @return 不活跃成员列表
     * @throws ScrmException 社群不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCommunityMemberDto> getInactiveMembers(Long communityId, int days) throws ScrmException {
        findCommunityOrThrow(communityId);
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(1, days));
        List<ScrmCommunityMemberEntity> inactive = new ArrayList<>(memberRepository
                .findByCommunityIdAndStatusAndLastActiveAtBefore(
                         communityId, MEMBER_STATUS_ACTIVE, threshold));
        inactive.addAll(memberRepository.findByCommunityIdAndStatusAndLastActiveAtIsNull(
                 communityId, MEMBER_STATUS_ACTIVE));
        return inactive.stream().map(this::toMemberDto).toList();
    }

    /**
     * 禁言成员 (状态置为 MUTED)。
     * <p>duration 为禁言分钟数, 仅记录日志 (实体未存储到期时间, 由调用方或定时任务恢复)。</p>
     *
     * @param id       成员 ID
     * @param duration 禁言分钟数
     * @return 更新后的成员
     * @throws ScrmException 成员不存在
     */
    @Transactional
    public ScrmCommunityMemberDto muteMember(Long id, int duration) throws ScrmException {
        ScrmCommunityMemberEntity entity = findMemberOrThrow(id);
        entity.setStatus(MEMBER_STATUS_MUTED);
        entity = memberRepository.save(entity);
        log.info("禁言成员: memberId={}, communityId={}, durationMinutes={}", id, entity.getCommunityId(), duration);
        return toMemberDto(entity);
    }

    // ============================================================
    // SOP 管理
    // ============================================================

    /**
     * 创建社群 SOP。
     * <p>默认 enabled=true, executionCount=0, delayMinutes=0。communityId 为空表示对所有群生效。</p>
     *
     * @param dto SOP 参数
     * @return 创建后的 SOP
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCommunitySopDto createSop(ScrmCommunitySopDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("SOP 参数不能为空");
        }
        ScrmCommunitySopEntity entity = new ScrmCommunitySopEntity();
        entity.setSopName(dto.getSopName());
        entity.setCommunityId(dto.getCommunityId());
        entity.setTriggerType(dto.getTriggerType());
        entity.setTriggerConfig(dto.getTriggerConfig());
        entity.setActionType(dto.getActionType());
        entity.setActionContent(dto.getActionContent());
        entity.setDelayMinutes(dto.getDelayMinutes() != null ? dto.getDelayMinutes() : 0);
        entity.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : Boolean.TRUE);
        entity.setExecutionCount(0);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = sopRepository.save(entity);
        log.info("创建社群 SOP: id={}, sopName={}, triggerType={}, actionType={}",
                entity.getId(), entity.getSopName(), entity.getTriggerType(), entity.getActionType());
        return toSopDto(entity);
    }

    /**
     * 更新 SOP (部分更新, 仅非空字段生效)。
     *
     * @param id  SOP ID
     * @param dto SOP 参数
     * @return 更新后的 SOP
     * @throws ScrmException SOP 不存在
     */
    @Transactional
    public ScrmCommunitySopDto updateSop(Long id, ScrmCommunitySopDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("SOP 参数不能为空");
        }
        ScrmCommunitySopEntity entity = findSopOrThrow(id);
        if (dto.getSopName() != null) entity.setSopName(dto.getSopName());
        if (dto.getCommunityId() != null) entity.setCommunityId(dto.getCommunityId());
        if (dto.getTriggerType() != null) entity.setTriggerType(dto.getTriggerType());
        if (dto.getTriggerConfig() != null) entity.setTriggerConfig(dto.getTriggerConfig());
        if (dto.getActionType() != null) entity.setActionType(dto.getActionType());
        if (dto.getActionContent() != null) entity.setActionContent(dto.getActionContent());
        if (dto.getDelayMinutes() != null) entity.setDelayMinutes(dto.getDelayMinutes());
        if (dto.getEnabled() != null) entity.setEnabled(dto.getEnabled());
        entity = sopRepository.save(entity);
        log.info("更新社群 SOP: id={}, sopName={}", id, entity.getSopName());
        return toSopDto(entity);
    }

    /**
     * 删除 SOP。
     *
     * @param id SOP ID
     * @throws ScrmException SOP 不存在
     */
    @Transactional
    public void deleteSop(Long id) throws ScrmException {
        ScrmCommunitySopEntity entity = findSopOrThrow(id);
        sopRepository.delete(entity);
        log.info("删除社群 SOP: id={}, sopName={}", id, entity.getSopName());
    }

    /**
     * 查询 SOP 详情。
     *
     * @param id SOP ID
     * @return SOP DTO
     * @throws ScrmException SOP 不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommunitySopDto getSop(Long id) throws ScrmException {
        return toSopDto(findSopOrThrow(id));
    }

    /**
     * 分页查询 SOP, 支持按社群 / 触发类型 / 启用状态过滤。
     *
     * @param communityId 社群 ID 过滤 (可空)
     * @param triggerType 触发类型过滤 (可空)
     * @param enabled     启用状态过滤 (可空)
     * @param pageable    分页参数
     * @return SOP 分页结果 (按 createTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommunitySopDto> listSops(Long communityId, String triggerType, Boolean enabled,
                                               Pageable pageable) {
        Specification<ScrmCommunitySopEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (communityId != null) {
                predicates.add(cb.equal(root.get("communityId"), communityId));
            }
            if (triggerType != null && !triggerType.isBlank()) {
                predicates.add(cb.equal(root.get("triggerType"), triggerType));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            query.orderBy(cb.desc(root.get("createTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return sopRepository.findAll(spec, ensureSort(pageable, "createTime")).map(this::toSopDto);
    }

    /**
     * 启用 SOP。
     *
     * @param id SOP ID
     * @return 更新后的 SOP
     * @throws ScrmException SOP 不存在
     */
    @Transactional
    public ScrmCommunitySopDto enableSop(Long id) throws ScrmException {
        ScrmCommunitySopEntity entity = findSopOrThrow(id);
        entity.setEnabled(true);
        entity = sopRepository.save(entity);
        log.info("启用社群 SOP: id={}", id);
        return toSopDto(entity);
    }

    /**
     * 禁用 SOP。
     *
     * @param id SOP ID
     * @return 更新后的 SOP
     * @throws ScrmException SOP 不存在
     */
    @Transactional
    public ScrmCommunitySopDto disableSop(Long id) throws ScrmException {
        ScrmCommunitySopEntity entity = findSopOrThrow(id);
        entity.setEnabled(false);
        entity = sopRepository.save(entity);
        log.info("禁用社群 SOP: id={}", id);
        return toSopDto(entity);
    }

    /**
     * 执行 SOP 动作 (模拟实现)。
     * <p>
     * 校验 SOP 存在且启用后, 按动作类型模拟执行: SEND_MESSAGE / SEND_WELCOME / SEND_REMINDER
     * 记录一条 SYSTEM 消息到目标社群; ADD_TAG / NOTIFY_MANAGER 仅记录日志。最后增量更新执行次数
     * 与最后执行时间。
     * </p>
     *
     * @param sopId       SOP ID
     * @param communityId 目标社群 ID (可空, 为空时取 SOP 绑定的社群)
     * @return 执行结果描述
     * @throws ScrmException SOP 不存在 / 社群不存在 / SOP 未启用
     */
    @Transactional
    public String executeSop(Long sopId, Long communityId) throws ScrmException {
        ScrmCommunitySopEntity sop = findSopOrThrow(sopId);
        if (Boolean.FALSE.equals(sop.getEnabled())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "SOP 未启用, 无法执行: sopId=" + sopId);
        }
        Long targetCommunityId = communityId != null ? communityId : sop.getCommunityId();
        if (targetCommunityId == null) {
            throw ScrmException.badRequest("未指定目标社群, 且 SOP 未绑定社群: sopId=" + sopId);
        }
        findCommunityOrThrow(targetCommunityId);
        // 模拟执行: 发送类动作记录一条 SYSTEM 消息
        String actionType = sop.getActionType();
        switch (actionType) {
            case "SEND_MESSAGE", "SEND_WELCOME", "SEND_REMINDER" -> recordSopMessage(targetCommunityId, sop);
            case "ADD_TAG" -> log.info("SOP 模拟打标签: sopId={}, communityId={}, content={}",
                    sopId, targetCommunityId, sop.getActionContent());
            case "NOTIFY_MANAGER" -> log.info("SOP 模拟通知管理员: sopId={}, communityId={}, content={}",
                    sopId, targetCommunityId, sop.getActionContent());
            default -> log.warn("SOP 未知动作类型, 跳过执行: sopId={}, actionType={}", sopId, actionType);
        }
        // 增量更新执行次数与最后执行时间
        sopRepository.incrementExecutionCount(sopId, LocalDateTime.now());
        log.info("执行社群 SOP: sopId={}, sopName={}, communityId={}, actionType={}",
                sopId, sop.getSopName(), targetCommunityId, actionType);
        return "SOP 执行完成: sopId=" + sopId + ", actionType=" + actionType + ", communityId=" + targetCommunityId;
    }

    /**
     * 处理定时触发 SOP (模拟实现)。
     * <p>加载账号下所有启用的 TIME_BASED SOP, 逐个调用 {@link #executeSop} 执行。
     * 实际生产应由调度器按 triggerConfig 中的 time/days 精确触发。</p>
     *
     * @return 处理的 SOP 数量
     */
    @Transactional
    public int processTimeBasedSops() {
        List<ScrmCommunitySopEntity> sops = sopRepository.findByTriggerTypeAndEnabled(
                 TRIGGER_TIME_BASED, Boolean.TRUE);
        int processed = 0;
        for (ScrmCommunitySopEntity sop : sops) {
            try {
                executeSop(sop.getId(), sop.getCommunityId());
                processed++;
            } catch (ScrmException e) {
                log.warn("处理定时 SOP 失败: sopId={}, error={}", sop.getId(), e.getMessage());
            }
        }
        log.info("处理定时 SOP 完成:, total={}, processed={}", sops.size(), processed);
        return processed;
    }

    // ============================================================
    // 消息管理
    // ============================================================

    /**
     * 记录社群消息。
     * <p>默认 senderType=MEMBER, messageType=TEXT, isReply=false, archived=false。
     * 记录后社群今日消息数 +1; 若发送者为在群成员, 同步其消息数与最后活跃时间。</p>
     *
     * @param dto 消息参数
     * @return 记录后的消息
     * @throws ScrmException 参数非法 / 社群不存在
     */
    @Transactional
    public ScrmCommunityMessageDto recordMessage(ScrmCommunityMessageDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("消息参数不能为空");
        }
        if (dto.getCommunityId() == null) {
            throw ScrmException.badRequest("社群 ID 不能为空");
        }
        if (dto.getMessageType() == null || dto.getMessageType().isBlank()) {
            throw ScrmException.badRequest("消息类型不能为空");
        }
        if (dto.getSentAt() == null) {
            throw ScrmException.badRequest("发送时间不能为空");
        }
        findCommunityOrThrow(dto.getCommunityId());
        ScrmCommunityMessageEntity entity = new ScrmCommunityMessageEntity();
        entity.setCommunityId(dto.getCommunityId());
        entity.setSenderId(dto.getSenderId());
        entity.setSenderName(dto.getSenderName());
        entity.setSenderType(dto.getSenderType() != null ? dto.getSenderType() : "MEMBER");
        entity.setMessageType(dto.getMessageType());
        entity.setContent(dto.getContent());
        entity.setMediaUrl(dto.getMediaUrl());
        entity.setSentAt(dto.getSentAt());
        entity.setIsReply(dto.getIsReply() != null ? dto.getIsReply() : Boolean.FALSE);
        entity.setReplyToMessageId(dto.getReplyToMessageId());
        entity.setSentiment(dto.getSentiment());
        entity.setArchived(Boolean.FALSE);
        entity = messageRepository.save(entity);
        // 社群今日消息数 +1
        communityRepository.findById(dto.getCommunityId()).ifPresent(c -> {
            c.setTodayMessages(safeInt(c.getTodayMessages()) + 1);
            communityRepository.save(c);
        });
        log.info("记录社群消息: messageId={}, communityId={}, messageType={}",
                entity.getId(), dto.getCommunityId(), entity.getMessageType());
        return toMessageDto(entity);
    }

    /**
     * 分页查询社群消息, 支持按发送者类型 / 消息类型 / 时间区间 / 关键词过滤。
     *
     * @param communityId 社群 ID (可空)
     * @param senderType  发送者类型过滤 (可空)
     * @param messageType 消息类型过滤 (可空)
     * @param startTime   发送时间起点 (含, 可空)
     * @param endTime     发送时间终点 (不含, 可空)
     * @param keyword     关键词过滤, 匹配消息内容 / 发送者名称 (可空)
     * @param pageable    分页参数
     * @return 消息分页结果 (按 sentAt DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCommunityMessageDto> listMessages(Long communityId, String senderType, String messageType,
                                                       LocalDateTime startTime, LocalDateTime endTime,
                                                       String keyword, Pageable pageable) {
        Specification<ScrmCommunityMessageEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (communityId != null) {
                predicates.add(cb.equal(root.get("communityId"), communityId));
            }
            if (senderType != null && !senderType.isBlank()) {
                predicates.add(cb.equal(root.get("senderType"), senderType));
            }
            if (messageType != null && !messageType.isBlank()) {
                predicates.add(cb.equal(root.get("messageType"), messageType));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sentAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThan(root.get("sentAt"), endTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("content")), kw),
                        cb.like(cb.lower(root.get("senderName")), kw)
                ));
            }
            query.orderBy(cb.desc(root.get("sentAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return messageRepository.findAll(spec, ensureSort(pageable, "sentAt")).map(this::toMessageDto);
    }

    /**
     * 查询消息详情。
     *
     * @param id 消息 ID
     * @return 消息 DTO
     * @throws ScrmException 消息不存在
     */
    @Transactional(readOnly = true)
    public ScrmCommunityMessageDto getMessage(Long id) throws ScrmException {
        return toMessageDto(findMessageOrThrow(id));
    }

    /**
     * 群广播 (多群发送, 模拟实现)。
     * <p>对每个目标社群记录一条 SYSTEM 类型的消息, 单个社群失败不影响其他社群,
     * 返回成功发送的社群数与失败列表。</p>
     *
     * @param dto 广播参数 (communityIds + messageType + content)
     * @return 批量操作结果
     */
    @Transactional
    public BatchResult broadcast(ScrmCommunityBroadcastDto dto) {
        if (dto == null || dto.getCommunityIds() == null || dto.getCommunityIds().isEmpty()) {
            return new BatchResult(0, List.of());
        }
        int successCount = 0;
        List<Long> failures = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String senderName = dto.getSenderName() != null ? dto.getSenderName() : SENDER_TYPE_SYSTEM;
        for (Long communityId : dto.getCommunityIds()) {
            try {
                findCommunityOrThrow(communityId);
                ScrmCommunityMessageEntity entity = new ScrmCommunityMessageEntity();
                entity.setCommunityId(communityId);
                entity.setSenderId(SENDER_TYPE_SYSTEM);
                entity.setSenderName(senderName);
                entity.setSenderType(SENDER_TYPE_SYSTEM);
                entity.setMessageType(dto.getMessageType());
                entity.setContent(dto.getContent());
                entity.setMediaUrl(dto.getMediaUrl());
                entity.setSentAt(now);
                entity.setIsReply(Boolean.FALSE);
                entity.setArchived(Boolean.FALSE);
                messageRepository.save(entity);
                // 社群今日消息数 +1
                communityRepository.findById(communityId).ifPresent(c -> {
                    c.setTodayMessages(safeInt(c.getTodayMessages()) + 1);
                    communityRepository.save(c);
                });
                successCount++;
            } catch (ScrmException e) {
                log.warn("群广播失败: communityId={}, error={}", communityId, e.getMessage());
                failures.add(communityId);
            }
        }
        log.info("群广播完成: total={}, success={}, failed={}",
                dto.getCommunityIds().size(), successCount, failures.size());
        return new BatchResult(successCount, failures);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 获取社群统计 (成员数 / 活跃度 / 消息趋势 / 活跃率)。
     *
     * @param id 社群 ID
     * @return 社群统计
     * @throws ScrmException 社群不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCommunityStats(Long id) throws ScrmException {
        ScrmCommunityEntity entity = findCommunityOrThrow(id);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("communityId", entity.getId());
        stats.put("communityName", entity.getCommunityName());
        stats.put("status", entity.getStatus());
        stats.put("memberCount", safeInt(entity.getMemberCount()));
        stats.put("activeMembers", safeInt(entity.getActiveMembers()));
        stats.put("todayNewMembers", safeInt(entity.getTodayNewMembers()));
        stats.put("todayMessages", safeInt(entity.getTodayMessages()));
        stats.put("activityScore", entity.getActivityScore());
        // 活跃率 = activeMembers / memberCount
        int memberCount = safeInt(entity.getMemberCount());
        double activeRate = memberCount > 0
                ? (double) safeInt(entity.getActiveMembers()) / memberCount : 0.0;
        stats.put("activeRate", Math.round(activeRate * 10000) / 10000.0);
        // 最近 7 天消息趋势
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(7);
        List<Object[]> dailyStats = messageRepository.dailyMessageStats(id, start, end);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] row : dailyStats) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", row[0] == null ? null : row[0].toString());
            day.put("messageCount", row[1]);
            trend.add(day);
        }
        stats.put("messageTrend", trend);
        return stats;
    }

    /**
     * 获取总体统计 (群数 / 总成员 / 平均活跃度 / 总消息)。
     * <p>统计区间按建群时间 createdAt 过滤 (可空, 为空则统计全部)。</p>
     *
     * @param startTime 建群时间起点 (含, 可空)
     * @param endTime   建群时间终点 (不含, 可空)
     * @return 总体统计
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOverallStats(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 活跃社群数
        stats.put("activeCommunityCount", communityRepository.countByStatus(STATUS_ACTIVE));
        // 总成员数 (活跃社群)
        stats.put("totalMembers", communityRepository.sumMemberCountByStatus(STATUS_ACTIVE));
        // 总活跃成员数 (活跃社群)
        stats.put("totalActiveMembers",
                communityRepository.sumActiveMembersByStatus(STATUS_ACTIVE));
        // 今日总消息数 (活跃社群)
        stats.put("totalTodayMessages",
                communityRepository.sumTodayMessagesByStatus(STATUS_ACTIVE));
        // 区间内消息总数
        LocalDateTime start = startTime != null ? startTime : LocalDate.of(1970, 1, 1).atStartOfDay();
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now().plusYears(100);
        stats.put("totalMessagesInRange", messageRepository.countByTimeRange(start, end));
        // 平均活跃度评分 (基于活跃社群)
        double avgActivityScore = communityRepository
                .findByCreatedAtAfter(LocalDate.of(1970, 1, 1).atStartOfDay())
                .stream()
                .filter(c -> STATUS_ACTIVE.equals(c.getStatus()))
                .mapToDouble(c -> c.getActivityScore() == null ? 0.0 : c.getActivityScore())
                .average()
                .orElse(0.0);
        stats.put("avgActivityScore", Math.round(avgActivityScore * 100) / 100.0);
        return stats;
    }

    /**
     * 获取社群活跃度趋势 (最近 N 天的每日消息数)。
     *
     * @param communityId 社群 ID
     * @param days        天数 (默认 7)
     * @return 活跃度趋势 (每日消息数列表)
     * @throws ScrmException 社群不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getActivityTrend(Long communityId, int days) throws ScrmException {
        findCommunityOrThrow(communityId);
        int safeDays = Math.max(1, days);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(safeDays);
        List<Object[]> dailyStats = messageRepository.dailyMessageStats(communityId, start, end);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] row : dailyStats) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", row[0] == null ? null : row[0].toString());
            day.put("messageCount", row[1]);
            trend.add(day);
        }
        return trend;
    }

    /**
     * 获取活跃群排行 (按 activityScore 倒序)。
     *
     * @param limit 限制条数 (默认 10, 最大 100)
     * @return 活跃群列表
     */
    @Transactional(readOnly = true)
    public List<ScrmCommunityDto> getTopCommunities(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_TOP_LIMIT);
        List<ScrmCommunityEntity> top = communityRepository
                .findTop10ByStatusOrderByActivityScoreDesc(STATUS_ACTIVE);
        return top.stream().limit(safeLimit).map(this::toCommunityDto).toList();
    }

    // ============================================================
    // 私有辅助方法
    // ============================================================

    /**
     * 按主键查询社群并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 社群 ID
     * @return 社群实体
     * @throws ScrmException 社群不存在
     */
    private ScrmCommunityEntity findCommunityOrThrow(Long id) throws ScrmException {
        ScrmCommunityEntity entity = communityRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "社群不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询成员并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 成员 ID
     * @return 成员实体
     * @throws ScrmException 成员不存在
     */
    private ScrmCommunityMemberEntity findMemberOrThrow(Long id) throws ScrmException {
        ScrmCommunityMemberEntity entity = memberRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "社群成员不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询 SOP 并校验账号归属, 不存在或越权抛异常。
     *
     * @param id SOP ID
     * @return SOP 实体
     * @throws ScrmException SOP 不存在
     */
    private ScrmCommunitySopEntity findSopOrThrow(Long id) throws ScrmException {
        ScrmCommunitySopEntity entity = sopRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "社群 SOP 不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询消息并校验账号归属, 不存在或越权抛异常。
     *
     * @param id 消息 ID
     * @return 消息实体
     * @throws ScrmException 消息不存在
     */
    private ScrmCommunityMessageEntity findMessageOrThrow(Long id) throws ScrmException {
        ScrmCommunityMessageEntity entity = messageRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "社群消息不存在: id=" + id));
        return entity;
    }


    /**
     * 确保分页参数带默认排序 (按指定字段倒序)。
     *
     * @param pageable 分页参数
     * @param field    默认排序字段
     * @return 处理后的分页参数
     */
    private Pageable ensureSort(Pageable pageable, String field) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, field));
    }

    /**
     * 计算社群活跃度评分 (0-100)。
     *
     * @param entity 社群实体
     * @return 活跃度评分
     */
    private double calculateActivityScore(ScrmCommunityEntity entity) {
        int memberCount = safeInt(entity.getMemberCount());
        if (memberCount <= 0) {
            return ACTIVITY_SCORE_MIN;
        }
        int activeMembers = safeInt(entity.getActiveMembers());
        int todayMessages = safeInt(entity.getTodayMessages());
        int todayNewMembers = safeInt(entity.getTodayNewMembers());
        // 活跃成员占比 (50%)
        double activeRate = (double) activeMembers / memberCount;
        // 消息得分 (30%): 每成员日均 5 条视为满分
        double messageScore = Math.min(1.0, todayMessages / (memberCount * MESSAGES_PER_MEMBER_FULL));
        // 增长得分 (20%)
        double growthScore = Math.min(1.0, (double) todayNewMembers / memberCount);
        double score = 50.0 * activeRate + 30.0 * messageScore + 20.0 * growthScore;
        return Math.max(ACTIVITY_SCORE_MIN, Math.min(ACTIVITY_SCORE_MAX, Math.round(score * 100) / 100.0));
    }

    /**
     * SOP 模拟发送消息: 在目标社群记录一条 SYSTEM 消息。
     *
     * @param communityId 社群 ID
     * @param sop         SOP 实体
     */
    private void recordSopMessage(Long communityId, ScrmCommunitySopEntity sop) {
        ScrmCommunityMessageEntity entity = new ScrmCommunityMessageEntity();
        entity.setCommunityId(communityId);
        entity.setSenderId(SENDER_TYPE_SYSTEM);
        entity.setSenderName(sop.getSopName());
        entity.setSenderType(SENDER_TYPE_SYSTEM);
        entity.setMessageType(MESSAGE_TYPE_TEXT);
        entity.setContent(sop.getActionContent());
        entity.setSentAt(LocalDateTime.now());
        entity.setIsReply(Boolean.FALSE);
        entity.setArchived(Boolean.FALSE);
        messageRepository.save(entity);
        // 社群今日消息数 +1
        communityRepository.findById(communityId).ifPresent(c -> {
            c.setTodayMessages(safeInt(c.getTodayMessages()) + 1);
            communityRepository.save(c);
        });
    }

    /**
     * 安全将 Integer 转 int (null 视为 0)。
     *
     * @param value Integer 值
     * @return int 值
     */
    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    // ============================================================
    // 实体转 DTO
    // ============================================================

    /**
     * 社群实体转 DTO。
     */
    private ScrmCommunityDto toCommunityDto(ScrmCommunityEntity entity) {
        ScrmCommunityDto dto = new ScrmCommunityDto();
        dto.setId(entity.getId());
        dto.setCommunityName(entity.getCommunityName());
        dto.setPlatformType(entity.getPlatformType());
        dto.setCommunityType(entity.getCommunityType());
        dto.setRoomId(entity.getRoomId());
        dto.setQrCode(entity.getQrCode());
        dto.setDescription(entity.getDescription());
        dto.setOwnerId(entity.getOwnerId());
        dto.setOwnerName(entity.getOwnerName());
        dto.setManagerId(entity.getManagerId());
        dto.setManagerName(entity.getManagerName());
        dto.setMaxMembers(entity.getMaxMembers());
        dto.setTags(entity.getTags());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setMemberCount(entity.getMemberCount());
        dto.setActiveMembers(entity.getActiveMembers());
        dto.setTodayNewMembers(entity.getTodayNewMembers());
        dto.setTodayMessages(entity.getTodayMessages());
        dto.setActivityScore(entity.getActivityScore());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 成员实体转 DTO。
     */
    private ScrmCommunityMemberDto toMemberDto(ScrmCommunityMemberEntity entity) {
        ScrmCommunityMemberDto dto = new ScrmCommunityMemberDto();
        dto.setId(entity.getId());
        dto.setCommunityId(entity.getCommunityId());
        dto.setCustomerId(entity.getCustomerId());
        dto.setMemberName(entity.getMemberName());
        dto.setMemberAlias(entity.getMemberAlias());
        dto.setPlatformUid(entity.getPlatformUid());
        dto.setRole(entity.getRole());
        dto.setJoinType(entity.getJoinType());
        dto.setJoinAt(entity.getJoinAt());
        dto.setLastActiveAt(entity.getLastActiveAt());
        dto.setMessageCount(entity.getMessageCount());
        dto.setIsActive(entity.getIsActive());
        dto.setInvitedBy(entity.getInvitedBy());
        dto.setStatus(entity.getStatus());
        dto.setLeftAt(entity.getLeftAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * SOP 实体转 DTO。
     */
    private ScrmCommunitySopDto toSopDto(ScrmCommunitySopEntity entity) {
        ScrmCommunitySopDto dto = new ScrmCommunitySopDto();
        dto.setId(entity.getId());
        dto.setSopName(entity.getSopName());
        dto.setCommunityId(entity.getCommunityId());
        dto.setTriggerType(entity.getTriggerType());
        dto.setTriggerConfig(entity.getTriggerConfig());
        dto.setActionType(entity.getActionType());
        dto.setActionContent(entity.getActionContent());
        dto.setDelayMinutes(entity.getDelayMinutes());
        dto.setEnabled(entity.getEnabled());
        dto.setExecutionCount(entity.getExecutionCount());
        dto.setLastExecutedAt(entity.getLastExecutedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 消息实体转 DTO。
     */
    private ScrmCommunityMessageDto toMessageDto(ScrmCommunityMessageEntity entity) {
        ScrmCommunityMessageDto dto = new ScrmCommunityMessageDto();
        dto.setId(entity.getId());
        dto.setCommunityId(entity.getCommunityId());
        dto.setSenderId(entity.getSenderId());
        dto.setSenderName(entity.getSenderName());
        dto.setSenderType(entity.getSenderType());
        dto.setMessageType(entity.getMessageType());
        dto.setContent(entity.getContent());
        dto.setMediaUrl(entity.getMediaUrl());
        dto.setSentAt(entity.getSentAt());
        dto.setIsReply(entity.getIsReply());
        dto.setReplyToMessageId(entity.getReplyToMessageId());
        dto.setSentiment(entity.getSentiment());
        dto.setArchived(entity.getArchived());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 批量操作结果 (成功数 + 失败的社群 ID 列表)
 * @since V1.0
     * @author Hsi Chu
     */
    public record BatchResult(int successCount, List<Long> failedCommunityIds) {
    }
}
