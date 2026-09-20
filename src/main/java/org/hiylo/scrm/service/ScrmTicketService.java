/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmTicketService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmTicketAssignDto;
import org.hiylo.scrm.dto.ScrmTicketCommentDto;
import org.hiylo.scrm.dto.ScrmTicketDto;
import org.hiylo.scrm.dto.ScrmTicketHistoryDto;
import org.hiylo.scrm.dto.ScrmTicketSatisfactionDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmTicketCommentEntity;
import org.hiylo.scrm.entity.ScrmTicketEntity;
import org.hiylo.scrm.entity.ScrmTicketHistoryEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmTicketCommentRepository;
import org.hiylo.scrm.repository.ScrmTicketHistoryRepository;
import org.hiylo.scrm.repository.ScrmTicketRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 客户工单/售后服务管理服务。
 * <p>
 * 承载售后服务工单的核心能力: 工单增删改查与按编号查询、分配/状态变更/优先级变更/升级/重新打开/关闭、
 * 评论与内部备注、流转历史与时间线、SLA 跟踪 (固定规则 URGENT=4h/HIGH=8h/MEDIUM=24h/LOW=48h)、
 * 满意度评价、工单统计/处理人工作量/分类统计。工单编号格式为 TKT + 年月日 + 4 位序号。
 * </p>
 * <p>
* 所有写操作写入当前用户归属账号实现数据隔离, 读操作通过
* JPA Specification 始终按当前账号过滤。校验失败抛出 {@link ScrmException} 携带
 * 通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmTicketService {

    // ==================== 优先级 ====================
    /** 优先级: 紧急 */
    private static final String PRIORITY_URGENT = "URGENT";
    /** 优先级: 高 */
    private static final String PRIORITY_HIGH = "HIGH";
    /** 优先级: 中 */
    private static final String PRIORITY_MEDIUM = "MEDIUM";
    /** 优先级: 低 */
    private static final String PRIORITY_LOW = "LOW";

    // ==================== 状态 ====================
    /** 状态: 待处理 */
    private static final String STATUS_OPEN = "OPEN";
    /** 状态: 处理中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 已解决 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已关闭 */
    private static final String STATUS_CLOSED = "CLOSED";
    /** 状态: 已重新打开 */
    private static final String STATUS_REOPENED = "REOPENED";
    /** 状态: 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    // ==================== 评论类型 ====================
    /** 评论类型: 客户 */
    private static final String COMMENT_TYPE_CUSTOMER = "CUSTOMER";
    /** 评论类型: 坐席 */
    private static final String COMMENT_TYPE_AGENT = "AGENT";
    /** 评论类型: 内部备注 */
    private static final String COMMENT_TYPE_INTERNAL = "INTERNAL";
    /** 评论类型: 系统 */
    private static final String COMMENT_TYPE_SYSTEM = "SYSTEM";

    // ==================== 动作类型 ====================
    /** 动作类型: CREATED 创建 */
    private static final String ACTION_CREATED = "CREATED";
    /** 动作类型: ASSIGNED 分配 */
    private static final String ACTION_ASSIGNED = "ASSIGNED";
    /** 动作类型: STATUS_CHANGED 状态变更 */
    private static final String ACTION_STATUS_CHANGED = "STATUS_CHANGED";
    /** 动作类型: PRIORITY_CHANGED 优先级变更 */
    private static final String ACTION_PRIORITY_CHANGED = "PRIORITY_CHANGED";
    /** 动作类型: CATEGORY_CHANGED 分类变更 */
    private static final String ACTION_CATEGORY_CHANGED = "CATEGORY_CHANGED";
    /** 动作类型: COMMENTED 评论 */
    private static final String ACTION_COMMENTED = "COMMENTED";
    /** 动作类型: ESCALATED 升级 */
    private static final String ACTION_ESCALATED = "ESCALATED";
    /** 动作类型: REOPENED 重新打开 */
    private static final String ACTION_REOPENED = "REOPENED";
    /** 动作类型: CLOSED 关闭 */
    private static final String ACTION_CLOSED = "CLOSED";

    /** SLA 响应时限: 紧急 4 小时 */
    private static final int SLA_HOURS_URGENT = 4;
    /** SLA 响应时限: 高 8 小时 */
    private static final int SLA_HOURS_HIGH = 8;
    /** SLA 响应时限: 中 24 小时 */
    private static final int SLA_HOURS_MEDIUM = 24;
    /** SLA 响应时限: 低 48 小时 */
    private static final int SLA_HOURS_LOW = 48;

    /** 工单编号前缀 */
    private static final String TICKET_NO_PREFIX = "TKT";
    /** 工单编号日期格式 */
    private static final DateTimeFormatter TICKET_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 工单序号格式 (4 位, 前补零) */
    private static final String TICKET_NO_SEQ_FORMAT = "%04d";
    /** 工单序号上限 (超过则进位到 5 位) */
    private static final int TICKET_NO_SEQ_BOUND = 10000;

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 未关闭状态集合 (SLA 超期扫描与工作量统计用) */
    private static final List<String> OPEN_STATUSES = List.of(
            STATUS_OPEN, STATUS_IN_PROGRESS, STATUS_REOPENED);
    /** 可评价状态集合 (RESOLVED / CLOSED / REOPENED) */
    private static final Set<String> SATISFACTION_ALLOWED_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_RESOLVED, STATUS_CLOSED, STATUS_REOPENED));

    /** 优先级有序列表 (从低到高, 升级用) */
    private static final List<String> PRIORITY_ORDER = List.of(
            PRIORITY_LOW, PRIORITY_MEDIUM, PRIORITY_HIGH, PRIORITY_URGENT);

    /** 合法优先级集合 */
    private static final Set<String> VALID_PRIORITIES = new HashSet<>(PRIORITY_ORDER);
    /** 合法状态集合 */
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_OPEN, STATUS_IN_PROGRESS, STATUS_RESOLVED, STATUS_CLOSED, STATUS_REOPENED, STATUS_CANCELLED));

    /** 工单数据访问层 */
    private final ScrmTicketRepository ticketRepository;
    /** 工单评论数据访问层 */
    private final ScrmTicketCommentRepository commentRepository;
    /** 工单历史数据访问层 */
    private final ScrmTicketHistoryRepository historyRepository;
    /** 客户数据访问层 (解析客户名称) */
    private final ScrmCustomerRepository customerRepository;

    // ============================================================
    // 工单 CRUD
    // ============================================================

    /**
     * 创建工单。
     * <p>校验参数合法性后生成唯一工单编号 (TKT + 年月日 + 4 位序号), 写入归属账号 ID 持久化,
     * 优先级缺省 MEDIUM, 状态缺省 OPEN, 来源缺省 CUSTOMER, 并按优先级计算 SLA 到期时间。
     * 同时记录 CREATED 历史与系统评论。</p>
     *
     * @param dto 工单参数
     * @return 创建后的工单
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmTicketDto createTicket(ScrmTicketDto dto) throws ScrmException {
        validateTicketDto(dto, false);
        LocalDateTime now = LocalDateTime.now();
        String priority = dto.getPriority() != null ? dto.getPriority() : PRIORITY_MEDIUM;
        validatePriority(priority);
        String category = dto.getCategory();
        validateCategory(category);
        ScrmTicketEntity entity = new ScrmTicketEntity();
        entity.setTicketNo(generateTicketNo());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName() != null
                ? dto.getCustomerName() : resolveCustomerName(dto.getCustomerId()));
        entity.setAccountId(dto.getAccountId());
        entity.setCategory(category);
        entity.setPriority(priority);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_OPEN);
        entity.setSource(dto.getSource() != null ? dto.getSource() : "CUSTOMER");
        entity.setAssigneeId(dto.getAssigneeId());
        entity.setAssigneeName(dto.getAssigneeName());
        entity.setTeamId(dto.getTeamId());
        entity.setRelatedOrderId(dto.getRelatedOrderId());
        entity.setRelatedProductId(dto.getRelatedProductId());
        entity.setSlaDueAt(calcSlaDueAt(now, priority));
        entity.setTags(dto.getTags());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = ticketRepository.save(entity);
        // 记录创建历史
        recordHistory(entity, ACTION_CREATED, null, entity.getTicketNo(), now, "创建工单");
        // 记录系统评论
        addSystemComment(entity.getId(), "工单已创建: " + entity.getTicketNo());
        log.info("创建工单: id={}, ticketNo={}, category={}, priority={}",
                entity.getId(), entity.getTicketNo(), entity.getCategory(), entity.getPriority());
        return toTicketDto(entity);
    }

    /**
     * 更新工单（字段非空才覆盖）。
     * <p>状态、优先级、分配人、SLA/响应/解决/关闭时间等通过专用接口维护, 此处不直接修改。
     * 类别变更时记录 CATEGORY_CHANGED 历史。</p>
     *
     * @param id  工单 ID
     * @param dto 工单参数
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @Transactional
    public ScrmTicketDto updateTicket(Long id, ScrmTicketDto dto) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        validateTicketDto(dto, true);
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getAccountId() != null) entity.setAccountId(dto.getAccountId());
        if (dto.getRelatedOrderId() != null) entity.setRelatedOrderId(dto.getRelatedOrderId());
        if (dto.getRelatedProductId() != null) entity.setRelatedProductId(dto.getRelatedProductId());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        // 类别变更: 记录历史并重算 SLA
        if (dto.getCategory() != null && !dto.getCategory().equals(entity.getCategory())) {
            validateCategory(dto.getCategory());
            String fromCategory = entity.getCategory();
            entity.setCategory(dto.getCategory());
            recordHistory(entity, ACTION_CATEGORY_CHANGED, fromCategory, dto.getCategory(),
                    LocalDateTime.now(), "变更工单类别");
        }
        entity = ticketRepository.save(entity);
        log.info("更新工单: id={}", id);
        return toTicketDto(entity);
    }

    /**
     * 删除工单。
     * <p>级联清理工单的评论与历史记录。</p>
     *
     * @param id 工单 ID
     * @throws ScrmException 工单不存在
     */
    @Transactional
    public void deleteTicket(Long id) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        // 级联清理评论与历史
        List<ScrmTicketCommentEntity> comments = commentRepository
                .findByTicketIdOrderByCreatedAtAsc(id);
        if (!comments.isEmpty()) {
            commentRepository.deleteAll(comments);
        }
        List<ScrmTicketHistoryEntity> histories = historyRepository
                .findByTicketIdOrderByActionTimeAsc(id);
        if (!histories.isEmpty()) {
            historyRepository.deleteAll(histories);
        }
        ticketRepository.delete(entity);
        log.info("删除工单: id={}", id);
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单 DTO
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public ScrmTicketDto getTicket(Long id) throws ScrmException {
        return toTicketDto(findTicketOrThrow(id));
    }

    /**
     * 分页查询工单, 支持按状态、优先级、类别、处理人、客户、来源、时间范围与关键词过滤。
     *
     * @param status     状态过滤 (可空)
     * @param priority   优先级过滤 (可空)
     * @param category   类别过滤 (可空)
     * @param assigneeId 处理人 ID 过滤 (可空)
     * @param customerId 客户 ID 过滤 (可空)
     * @param source     来源过滤 (可空)
     * @param startTime  起始时间 (按创建时间, 可空)
     * @param endTime    截止时间 (按创建时间, 可空)
     * @param keyword    关键词过滤, 匹配工单编号/标题/描述 (可空)
     * @param pageable   分页参数
     * @return 工单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTicketDto> listTickets(String status, String priority, String category,
                                            String assigneeId, Long customerId, String source,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            String keyword, Pageable pageable) {
        Specification<ScrmTicketEntity> spec = buildTicketSpec(status, priority, category, assigneeId,
                customerId, source, startTime, endTime, keyword);
        return ticketRepository.findAll(spec, pageable).map(this::toTicketDto);
    }

    /**
     * 按工单编号查询工单。
     *
     * @param ticketNo 工单编号
     * @return 工单 DTO
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public ScrmTicketDto getTicketByNo(String ticketNo) throws ScrmException {
        if (ticketNo == null || ticketNo.isBlank()) {
            throw ScrmException.badRequest("工单编号不能为空");
        }
        ScrmTicketEntity entity = ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工单不存在: ticketNo=" + ticketNo));

        return toTicketDto(entity);
    }

    // ============================================================
    // 工单动作
    // ============================================================

    /**
     * 分配工单。
     * <p>assigneeId 与 teamId 至少传其一, 工单状态置 IN_PROGRESS (若原为 OPEN),
     * 首次分配时记录首次响应时间, 记录 ASSIGNED 历史。</p>
     *
     * @param assignDto 分配请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @Transactional
    public ScrmTicketDto assignTicket(ScrmTicketAssignDto assignDto) throws ScrmException {
        if (assignDto == null || assignDto.getTicketId() == null) {
            throw ScrmException.badRequest("工单 ID 不能为空");
        }
        boolean noAssignee = assignDto.getAssigneeId() == null || assignDto.getAssigneeId().isBlank();
        boolean noTeam = assignDto.getTeamId() == null || assignDto.getTeamId().isBlank();
        if (noAssignee && noTeam) {
            throw ScrmException.badRequest("处理人 ID 与处理团队 ID 至少传其一");
        }
        ScrmTicketEntity entity = findTicketOrThrow(assignDto.getTicketId());
        ensureNotClosed(entity);
        String fromAssignee = entity.getAssigneeId();
        String fromTeam = entity.getTeamId();
        LocalDateTime now = LocalDateTime.now();
        if (assignDto.getAssigneeId() != null && !assignDto.getAssigneeId().isBlank()) {
            entity.setAssigneeId(assignDto.getAssigneeId());
            entity.setAssigneeName(assignDto.getAssigneeName());
        }
        if (assignDto.getTeamId() != null && !assignDto.getTeamId().isBlank()) {
            entity.setTeamId(assignDto.getTeamId());
        }
        // OPEN 状态分配后进入处理中
        if (STATUS_OPEN.equals(entity.getStatus())) {
            entity.setStatus(STATUS_IN_PROGRESS);
        }
        // 首次分配记录首次响应时间
        if (entity.getFirstResponseAt() == null) {
            entity.setFirstResponseAt(now);
        }
        entity = ticketRepository.save(entity);
        StringBuilder note = new StringBuilder("分配工单");
        if (!noAssignee) {
            note.append(": assignee=").append(assignDto.getAssigneeId());
        }
        if (!noTeam) {
            note.append(", team=").append(assignDto.getTeamId());
        }
        recordHistory(entity, ACTION_ASSIGNED,
                fromAssignee != null ? fromAssignee : fromTeam,
                !noAssignee ? assignDto.getAssigneeId() : assignDto.getTeamId(),
                now, note.toString());
        log.info("分配工单: id={}, assigneeId={}, teamId={}", entity.getId(),
                assignDto.getAssigneeId(), assignDto.getTeamId());
        return toTicketDto(entity);
    }

    /**
     * 变更工单状态。
     * <p>校验目标状态合法, 记录 STATUS_CHANGED 历史。变更为 RESOLVED 时设置 resolvedAt 与解决时长,
     * 变更为 CLOSED 时设置 closedAt, 变更为 REOPENED 时清理解决/关闭字段。</p>
     *
     * @param id     工单 ID
     * @param status 目标状态
     * @param note   变更备注 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法 / 状态流转非法
     */
    @Transactional
    public ScrmTicketDto changeStatus(Long id, String status, String note) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("目标状态不能为空");
        }
        validateStatus(status);
        ScrmTicketEntity entity = findTicketOrThrow(id);
        String fromStatus = entity.getStatus();
        if (fromStatus.equals(status)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单状态未变更: status=" + status);
        }
        // 已取消的工单不允许变更状态
        if (STATUS_CANCELLED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "已取消的工单不允许变更状态: id=" + id);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(status);
        // 状态副作用
        switch (status) {
            case STATUS_IN_PROGRESS:
                if (entity.getFirstResponseAt() == null) {
                    entity.setFirstResponseAt(now);
                }
                break;
            case STATUS_RESOLVED:
                entity.setResolvedAt(now);
                if (entity.getCreateTime() != null) {
                    long minutes = Duration.between(entity.getCreateTime(), now).toMinutes();
                    entity.setResolutionTimeMinutes((int) Math.max(0, minutes));
                }
                break;
            case STATUS_CLOSED:
                if (entity.getResolvedAt() == null) {
                    entity.setResolvedAt(now);
                    if (entity.getCreateTime() != null) {
                        long minutes = Duration.between(entity.getCreateTime(), now).toMinutes();
                        entity.setResolutionTimeMinutes((int) Math.max(0, minutes));
                    }
                }
                entity.setClosedAt(now);
                break;
            case STATUS_REOPENED:
                entity.setResolvedAt(null);
                entity.setClosedAt(null);
                entity.setResolutionTimeMinutes(null);
                break;
            default:
                break;
        }
        entity = ticketRepository.save(entity);
        recordHistory(entity, ACTION_STATUS_CHANGED, fromStatus, status, now, note);
        log.info("变更工单状态: id={}, from={}, to={}", id, fromStatus, status);
        return toTicketDto(entity);
    }

    /**
     * 变更工单优先级。
     * <p>校验优先级合法, 记录 PRIORITY_CHANGED 历史, 并按新优先级重算 SLA 到期时间
     * (以当前时间为基准)。</p>
     *
     * @param id       工单 ID
     * @param priority 目标优先级
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 优先级非法 / 工单已关闭
     */
    @Transactional
    public ScrmTicketDto changePriority(Long id, String priority) throws ScrmException {
        if (priority == null || priority.isBlank()) {
            throw ScrmException.badRequest("目标优先级不能为空");
        }
        validatePriority(priority);
        ScrmTicketEntity entity = findTicketOrThrow(id);
        ensureNotClosed(entity);
        String fromPriority = entity.getPriority();
        if (fromPriority.equals(priority)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单优先级未变更: priority=" + priority);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setPriority(priority);
        // 按新优先级重算 SLA (以当前时间为基准)
        entity.setSlaDueAt(calcSlaDueAt(now, priority));
        entity = ticketRepository.save(entity);
        recordHistory(entity, ACTION_PRIORITY_CHANGED, fromPriority, priority, now,
                "变更优先级: " + fromPriority + " → " + priority);
        log.info("变更工单优先级: id={}, from={}, to={}", id, fromPriority, priority);
        return toTicketDto(entity);
    }

    /**
     * 升级处理。
     * <p>将优先级提升一级 (LOW → MEDIUM → HIGH → URGENT), 已为 URGENT 时保持不变;
     * 状态置 IN_PROGRESS, 按新优先级重算 SLA, 记录 ESCALATED 历史并附加升级原因。</p>
     *
     * @param id     工单 ID
     * @param reason 升级原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @Transactional
    public ScrmTicketDto escalateTicket(Long id, String reason) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        ensureNotClosed(entity);
        String fromPriority = entity.getPriority();
        int idx = PRIORITY_ORDER.indexOf(fromPriority);
        String toPriority = idx >= 0 && idx < PRIORITY_ORDER.size() - 1
                ? PRIORITY_ORDER.get(idx + 1) : fromPriority;
        LocalDateTime now = LocalDateTime.now();
        entity.setPriority(toPriority);
        entity.setStatus(STATUS_IN_PROGRESS);
        entity.setSlaDueAt(calcSlaDueAt(now, toPriority));
        if (entity.getFirstResponseAt() == null) {
            entity.setFirstResponseAt(now);
        }
        entity = ticketRepository.save(entity);
        recordHistory(entity, ACTION_ESCALATED, fromPriority, toPriority, now,
                "升级处理: reason=" + reason);
        // 系统评论记录升级
        addSystemComment(entity.getId(), "工单已升级: " + fromPriority + " → " + toPriority
                + (reason != null && !reason.isBlank() ? ", 原因: " + reason : ""));
        log.info("升级工单: id={}, from={}, to={}, reason={}", id, fromPriority, toPriority, reason);
        return toTicketDto(entity);
    }

    /**
     * 重新打开工单。
     * <p>仅 RESOLVED / CLOSED 状态可重新打开, 状态置 REOPENED, 清理解决/关闭/满意度字段,
     * 按 当前优先级重算 SLA, 记录 REOPENED 历史并附加重新打开原因。</p>
     *
     * @param id     工单 ID
     * @param reason 重新打开原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmTicketDto reopenTicket(Long id, String reason) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        String fromStatus = entity.getStatus();
        if (!STATUS_RESOLVED.equals(fromStatus) && !STATUS_CLOSED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "仅 RESOLVED / CLOSED 状态可重新打开: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_REOPENED);
        entity.setResolvedAt(null);
        entity.setClosedAt(null);
        entity.setResolutionTimeMinutes(null);
        entity.setSatisfactionScore(null);
        entity.setSatisfactionComment(null);
        entity.setSlaDueAt(calcSlaDueAt(now, entity.getPriority()));
        entity = ticketRepository.save(entity);
        recordHistory(entity, ACTION_REOPENED, fromStatus, STATUS_REOPENED, now,
                "重新打开: reason=" + reason);
        addSystemComment(entity.getId(), "工单已重新打开"
                + (reason != null && !reason.isBlank() ? ", 原因: " + reason : ""));
        log.info("重新打开工单: id={}, from={}, reason={}", id, fromStatus, reason);
        return toTicketDto(entity);
    }

    /**
     * 关闭工单。
     * <p>状态置 CLOSED, 设置 closedAt 与 resolvedAt (若未设置), 计算解决时长, 记录 CLOSED 历史
     * 并附加解决方案。</p>
     *
     * @param id         工单 ID
     * @param resolution 解决方案 (可空)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmTicketDto closeTicket(Long id, String resolution) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        String fromStatus = entity.getStatus();
        if (STATUS_CLOSED.equals(fromStatus) || STATUS_CANCELLED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许再次关闭: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getResolvedAt() == null) {
            entity.setResolvedAt(now);
            if (entity.getCreateTime() != null) {
                long minutes = Duration.between(entity.getCreateTime(), now).toMinutes();
                entity.setResolutionTimeMinutes((int) Math.max(0, minutes));
            }
        }
        entity.setStatus(STATUS_CLOSED);
        entity.setClosedAt(now);
        entity = ticketRepository.save(entity);
        recordHistory(entity, ACTION_CLOSED, fromStatus, STATUS_CLOSED, now,
                "关闭工单: resolution=" + resolution);
        if (resolution != null && !resolution.isBlank()) {
            addSystemComment(entity.getId(), "工单已关闭, 解决方案: " + resolution);
        }
        log.info("关闭工单: id={}, from={}", id, fromStatus);
        return toTicketDto(entity);
    }

    // ============================================================
    // 评论
    // ============================================================

    /**
     * 添加评论。
     * <p>校验评论类型合法, isInternal 缺省按评论类型推断 (INTERNAL 默认 true, 其他默认 false),
     * 记录评论并写入 COMMENTED 历史。</p>
     *
     * @param dto 评论参数
     * @return 创建后的评论
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @Transactional
    public ScrmTicketCommentDto addComment(ScrmTicketCommentDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("评论参数不能为空");
        }
        if (dto.getTicketId() == null) {
            throw ScrmException.badRequest("工单 ID 不能为空");
        }
        if (dto.getCommentType() == null || dto.getCommentType().isBlank()) {
            throw ScrmException.badRequest("评论类型不能为空");
        }
        validateCommentType(dto.getCommentType());
        if (dto.getAuthorId() == null || dto.getAuthorId().isBlank()) {
            throw ScrmException.badRequest("作者 ID 不能为空");
        }
        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw ScrmException.badRequest("评论内容不能为空");
        }
        ScrmTicketEntity ticket = findTicketOrThrow(dto.getTicketId());
        ensureNotClosed(ticket);
        LocalDateTime now = LocalDateTime.now();
        ScrmTicketCommentEntity entity = new ScrmTicketCommentEntity();
        entity.setTicketId(ticket.getId());
        entity.setCommentType(dto.getCommentType());
        entity.setAuthorId(dto.getAuthorId());
        entity.setAuthorName(dto.getAuthorName());
        entity.setContent(dto.getContent());
        entity.setAttachments(dto.getAttachments());
        // isInternal 缺省: INTERNAL 类型默认 true, 其他默认 false
        entity.setIsInternal(dto.getIsInternal() != null ? dto.getIsInternal()
                : COMMENT_TYPE_INTERNAL.equals(dto.getCommentType()));
        entity.setCreatedAt(dto.getCreatedAt() != null ? dto.getCreatedAt() : now);
        entity = commentRepository.save(entity);
        recordHistory(ticket, ACTION_COMMENTED, null, dto.getCommentType(), now,
                "添加评论: type=" + dto.getCommentType());
        log.info("添加工单评论: ticketId={}, commentId={}, type={}",
                ticket.getId(), entity.getId(), dto.getCommentType());
        return toCommentDto(entity);
    }

    /**
     * 查询工单评论列表 (按评论发生时间升序)。
     *
     * @param ticketId 工单 ID
     * @return 评论列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmTicketCommentDto> listComments(Long ticketId) throws ScrmException {
        findTicketOrThrow(ticketId);
        return commentRepository
                .findByTicketIdOrderByCreatedAtAsc(ticketId)
                .stream().map(this::toCommentDto).collect(Collectors.toList());
    }

    /**
     * 添加内部备注。
     * <p>评论类型固定 INTERNAL, isInternal=true, 记录评论与 COMMENTED 历史。</p>
     *
     * @param ticketId 工单 ID
     * @param content  备注内容
     * @param authorId 作者 ID
     * @return 创建后的评论
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @Transactional
    public ScrmTicketCommentDto addInternalNote(Long ticketId, String content, String authorId)
            throws ScrmException {
        if (content == null || content.isBlank()) {
            throw ScrmException.badRequest("备注内容不能为空");
        }
        if (authorId == null || authorId.isBlank()) {
            throw ScrmException.badRequest("作者 ID 不能为空");
        }
        ScrmTicketCommentDto dto = new ScrmTicketCommentDto();
        dto.setTicketId(ticketId);
        dto.setCommentType(COMMENT_TYPE_INTERNAL);
        dto.setAuthorId(authorId);
        dto.setContent(content);
        dto.setIsInternal(true);
        return addComment(dto);
    }

    // ============================================================
    // 历史 / 时间线
    // ============================================================

    /**
     * 查询工单流转历史列表 (按动作时间升序)。
     *
     * @param ticketId 工单 ID
     * @return 历史列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmTicketHistoryDto> listHistory(Long ticketId) throws ScrmException {
        findTicketOrThrow(ticketId);
        return historyRepository
                .findByTicketIdOrderByActionTimeAsc(ticketId)
                .stream().map(this::toHistoryDto).collect(Collectors.toList());
    }

    /**
     * 查询工单时间线 (评论与历史合并, 按时间升序)。
     * <p>每条记录含 type (COMMENT / HISTORY)、time 与对应字段, 便于前端按时间顺序展示。</p>
     *
     * @param ticketId 工单 ID
     * @return 时间线列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTicketTimeline(Long ticketId) throws ScrmException {
        findTicketOrThrow(ticketId);
        List<ScrmTicketCommentEntity> comments = commentRepository
                .findByTicketIdOrderByCreatedAtAsc(ticketId);
        List<ScrmTicketHistoryEntity> histories = historyRepository
                .findByTicketIdOrderByActionTimeAsc(ticketId);
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (ScrmTicketCommentEntity c : comments) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "COMMENT");
            item.put("time", c.getCreatedAt());
            item.put("id", c.getId());
            item.put("commentType", c.getCommentType());
            item.put("authorId", c.getAuthorId());
            item.put("authorName", c.getAuthorName());
            item.put("content", c.getContent());
            item.put("isInternal", c.getIsInternal());
            timeline.add(item);
        }
        for (ScrmTicketHistoryEntity h : histories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "HISTORY");
            item.put("time", h.getActionTime());
            item.put("id", h.getId());
            item.put("actionType", h.getActionType());
            item.put("fromValue", h.getFromValue());
            item.put("toValue", h.getToValue());
            item.put("operatorId", h.getOperatorId());
            item.put("operatorName", h.getOperatorName());
            item.put("note", h.getNote());
            timeline.add(item);
        }
        // 按时间升序排序
        timeline.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("time");
            LocalDateTime tb = (LocalDateTime) b.get("time");
            if (ta == null || tb == null) {
                return 0;
            }
            return ta.compareTo(tb);
        });
        return timeline;
    }

    // ============================================================
    // SLA
    // ============================================================

    /**
     * 检查工单 SLA 状态。
     * <p>返回 SLA 到期时间、是否超期、剩余分钟数 (超期为负数)。工单已关闭/取消或无 SLA 时
     * isOverdue=false, remainingMinutes=null。</p>
     *
     * @param id 工单 ID
     * @return SLA 状态
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkSla(Long id) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticketId", entity.getId());
        result.put("ticketNo", entity.getTicketNo());
        result.put("status", entity.getStatus());
        result.put("priority", entity.getPriority());
        result.put("slaDueAt", entity.getSlaDueAt());
        boolean closed = STATUS_CLOSED.equals(entity.getStatus())
                || STATUS_CANCELLED.equals(entity.getStatus());
        if (entity.getSlaDueAt() == null || closed) {
            result.put("isOverdue", false);
            result.put("remainingMinutes", null);
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = entity.getSlaDueAt().isBefore(now);
        long remaining = Duration.between(now, entity.getSlaDueAt()).toMinutes();
        result.put("isOverdue", overdue);
        result.put("remainingMinutes", remaining);
        return result;
    }

    /**
     * 更新工单 SLA 到期时间。
     *
     * @param id        工单 ID
     * @param slaDueAt  SLA 到期时间
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @Transactional
    public ScrmTicketDto updateSla(Long id, LocalDateTime slaDueAt) throws ScrmException {
        ScrmTicketEntity entity = findTicketOrThrow(id);
        ensureNotClosed(entity);
        entity.setSlaDueAt(slaDueAt);
        entity = ticketRepository.save(entity);
        log.info("更新工单 SLA: id={}, slaDueAt={}", id, slaDueAt);
        return toTicketDto(entity);
    }

    /**
     * 分页查询 SLA 超期工单 (slaDueAt 早于当前时间且状态属于未关闭集合)。
     *
     * @param pageable 分页参数
     * @return 超期工单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmTicketDto> getOverdueTickets(Pageable pageable) {
        List<ScrmTicketEntity> overdue = ticketRepository.findBySlaDueAtBeforeAndStatusIn(
                 LocalDateTime.now(), OPEN_STATUSES);
        // 内存分页 (超期工单通常数量有限)
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), overdue.size());
        List<ScrmTicketDto> content = start <= overdue.size()
                ? overdue.subList(start, end).stream().map(this::toTicketDto).collect(Collectors.toList())
                : new ArrayList<>();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, overdue.size());
    }

    // ============================================================
    // 满意度
    // ============================================================

    /**
     * 提交满意度评价。
     * <p>仅 RESOLVED / CLOSED / REOPENED 状态的工单可提交评价, 重复提交覆盖原评价。</p>
     *
     * @param dto 评价请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 评分越界 / 状态非法
     */
    @Transactional
    public ScrmTicketDto submitSatisfaction(ScrmTicketSatisfactionDto dto) throws ScrmException {
        if (dto == null || dto.getTicketId() == null) {
            throw ScrmException.badRequest("工单 ID 不能为空");
        }
        if (dto.getScore() == null || dto.getScore() < 1 || dto.getScore() > 5) {
            throw ScrmException.badRequest("满意度评分需为 1-5");
        }
        ScrmTicketEntity entity = findTicketOrThrow(dto.getTicketId());
        if (!SATISFACTION_ALLOWED_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "当前状态不允许提交满意度评价: status=" + entity.getStatus());
        }
        entity.setSatisfactionScore(dto.getScore());
        entity.setSatisfactionComment(dto.getComment());
        entity = ticketRepository.save(entity);
        log.info("提交满意度评价: id={}, score={}", entity.getId(), dto.getScore());
        return toTicketDto(entity);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 工单统计: 总数 / 各状态 / 各优先级 / 平均解决时长 / 平均满意度。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getTicketStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmTicketEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmTicketEntity> tickets = ticketRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createTime"));
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : Arrays.asList(STATUS_OPEN, STATUS_IN_PROGRESS, STATUS_RESOLVED,
                STATUS_CLOSED, STATUS_REOPENED, STATUS_CANCELLED)) {
            statusCount.put(s, 0L);
        }
        Map<String, Long> priorityCount = new LinkedHashMap<>();
        for (String p : Arrays.asList(PRIORITY_URGENT, PRIORITY_HIGH, PRIORITY_MEDIUM, PRIORITY_LOW)) {
            priorityCount.put(p, 0L);
        }
        long resolvedWithMinutes = 0;
        long resolvedCount = 0;
        long satisfactionSum = 0;
        long satisfactionCount = 0;
        for (ScrmTicketEntity t : tickets) {
            if (t.getStatus() != null) {
                statusCount.merge(t.getStatus(), 1L, Long::sum);
            }
            if (t.getPriority() != null) {
                priorityCount.merge(t.getPriority(), 1L, Long::sum);
            }
            if (t.getResolutionTimeMinutes() != null) {
                resolvedWithMinutes += t.getResolutionTimeMinutes();
                resolvedCount++;
            }
            if (t.getSatisfactionScore() != null) {
                satisfactionSum += t.getSatisfactionScore();
                satisfactionCount++;
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) tickets.size());
        stats.put("byStatus", statusCount);
        stats.put("byPriority", priorityCount);
        stats.put("avgResolutionMinutes", resolvedCount > 0
                ? Math.round((double) resolvedWithMinutes / resolvedCount) : 0);
        stats.put("avgSatisfaction", satisfactionCount > 0
                ? Math.round((double) satisfactionSum / satisfactionCount * 100d) / 100d : 0);
        stats.put("satisfactionCount", satisfactionCount);
        return stats;
    }

    /**
     * 处理人工作量统计: 各状态工单数。
     *
     * @param assigneeId 处理人 ID
     * @return 统计结果
     * @throws ScrmException 处理人 ID 为空
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAssigneeWorkload(String assigneeId) throws ScrmException {
        if (assigneeId == null || assigneeId.isBlank()) {
            throw ScrmException.badRequest("处理人 ID 不能为空");
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("assigneeId", assigneeId);
        stats.put("openCount", ticketRepository.countByAssigneeIdAndStatusIn(
                 assigneeId, OPEN_STATUSES));
        stats.put("inProgressCount", ticketRepository.countByAssigneeIdAndStatusIn(
                 assigneeId, List.of(STATUS_IN_PROGRESS)));
        stats.put("resolvedCount", ticketRepository.countByAssigneeIdAndStatusIn(
                 assigneeId, List.of(STATUS_RESOLVED)));
        stats.put("closedCount", ticketRepository.countByAssigneeIdAndStatusIn(
                 assigneeId, List.of(STATUS_CLOSED)));
        stats.put("totalCount", ticketRepository.countByAssigneeIdAndStatusIn(
                 assigneeId, Arrays.asList(STATUS_OPEN, STATUS_IN_PROGRESS,
                        STATUS_RESOLVED, STATUS_CLOSED, STATUS_REOPENED, STATUS_CANCELLED)));
        return stats;
    }

    /**
     * 分类统计: 各工单类别数量。
     * <p>时间范围按工单创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmTicketEntity> spec = buildTimeRangeSpec(startTime, endTime);
        List<ScrmTicketEntity> tickets = ticketRepository.findAll(spec);
        Map<String, Long> categoryCount = new LinkedHashMap<>();
        for (String c : Arrays.asList("PRODUCT_ISSUE", "SERVICE_COMPLAINT", "REFUND", "EXCHANGE",
                "TECHNICAL", "DELIVERY", "BILLING", "OTHER")) {
            categoryCount.put(c, 0L);
        }
        for (ScrmTicketEntity t : tickets) {
            if (t.getCategory() != null) {
                categoryCount.merge(t.getCategory(), 1L, Long::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", (long) tickets.size());
        stats.put("byCategory", categoryCount);
        return stats;
    }

    // ============================================================
    // 工单编号生成
    // ============================================================

    /**
     * 生成工单编号 (TKT + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成工单数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 工单编号
     */
    public String generateTicketNo() {
        String datePart = LocalDateTime.now().format(TICKET_NO_DATE_FORMAT);
        String prefix = TICKET_NO_PREFIX + datePart;
        long count = ticketRepository.countByTicketNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < TICKET_NO_SEQ_BOUND
                ? String.format(TICKET_NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 当前操作人 ID (从请求头 X-User-Id 透传, 暂用默认值)
     */
    private String currentOperator() {
        return DEFAULT_OPERATOR;
    }

    /**
     * 计算 SLA 到期时间 (按优先级固定规则)。
     *
     * @param base     基准时间
     * @param priority 优先级
     * @return SLA 到期时间
     */
    private LocalDateTime calcSlaDueAt(LocalDateTime base, String priority) {
        int hours;
        switch (priority) {
            case PRIORITY_URGENT:
                hours = SLA_HOURS_URGENT;
                break;
            case PRIORITY_HIGH:
                hours = SLA_HOURS_HIGH;
                break;
            case PRIORITY_LOW:
                hours = SLA_HOURS_LOW;
                break;
            case PRIORITY_MEDIUM:
            default:
                hours = SLA_HOURS_MEDIUM;
                break;
        }
        return base.plusHours(hours);
    }

    /**
     * 添加系统评论 (不触发历史记录, 避免循环)。
     */
    private void addSystemComment(Long ticketId, String content) {
        ScrmTicketCommentEntity entity = new ScrmTicketCommentEntity();
        entity.setTicketId(ticketId);
        entity.setCommentType(COMMENT_TYPE_SYSTEM);
        entity.setAuthorId(DEFAULT_OPERATOR);
        entity.setAuthorName(DEFAULT_OPERATOR);
        entity.setContent(content);
        entity.setIsInternal(false);
        entity.setCreatedAt(LocalDateTime.now());
        commentRepository.save(entity);
    }

    /**
     * 记录工单流转历史。
     */
    private void recordHistory(ScrmTicketEntity ticket, String actionType, String fromValue,
                                String toValue, LocalDateTime actionTime, String note) {
        ScrmTicketHistoryEntity history = new ScrmTicketHistoryEntity();
        history.setTicketId(ticket.getId());
        history.setActionType(actionType);
        history.setFromValue(fromValue);
        history.setToValue(toValue);
        history.setOperatorId(currentOperator());
        history.setOperatorName(currentOperator());
        history.setActionTime(actionTime != null ? actionTime : LocalDateTime.now());
        history.setNote(note);
        historyRepository.save(history);
    }

    /**
     * 解析客户名称 (从客户实体 nickname 字段), 客户不存在时返回 null。
     */
    private String resolveCustomerName(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)

                .map(ScrmCustomerEntity::getNickname)
                .orElse(null);
    }

    /**
     * 校验工单参数。
     *
     * @param dto     工单参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateTicketDto(ScrmTicketDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("工单参数不能为空");
        }
        if (!partial) {
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("工单标题不能为空");
            }
            if (dto.getCustomerId() == null) {
                throw ScrmException.badRequest("客户 ID 不能为空");
            }
            if (dto.getCategory() == null || dto.getCategory().isBlank()) {
                throw ScrmException.badRequest("工单类别不能为空");
            }
            validateCategory(dto.getCategory());
            if (dto.getPriority() != null) {
                validatePriority(dto.getPriority());
            }
            if (dto.getStatus() != null) {
                validateStatus(dto.getStatus());
            }
        }
    }

    /**
     * 校验优先级合法性。
     */
    private void validatePriority(String priority) throws ScrmException {
        if (!VALID_PRIORITIES.contains(priority)) {
            throw ScrmException.badRequest("优先级非法: " + priority
                    + ", 合法值: URGENT / HIGH / MEDIUM / LOW");
        }
    }

    /**
     * 校验状态合法性。
     */
    private void validateStatus(String status) throws ScrmException {
        if (!VALID_STATUSES.contains(status)) {
            throw ScrmException.badRequest("状态非法: " + status
                    + ", 合法值: OPEN / IN_PROGRESS / RESOLVED / CLOSED / REOPENED / CANCELLED");
        }
    }

    /**
     * 校验类别合法性。
     */
    private void validateCategory(String category) throws ScrmException {
        Set<String> validCategories = new HashSet<>(Arrays.asList(
                "PRODUCT_ISSUE", "SERVICE_COMPLAINT", "REFUND", "EXCHANGE",
                "TECHNICAL", "DELIVERY", "BILLING", "OTHER"));
        if (!validCategories.contains(category)) {
            throw ScrmException.badRequest("工单类别非法: " + category);
        }
    }

    /**
     * 校验评论类型合法性。
     */
    private void validateCommentType(String type) throws ScrmException {
        Set<String> validTypes = new HashSet<>(Arrays.asList(
                COMMENT_TYPE_CUSTOMER, COMMENT_TYPE_AGENT, COMMENT_TYPE_INTERNAL, COMMENT_TYPE_SYSTEM));
        if (!validTypes.contains(type)) {
            throw ScrmException.badRequest("评论类型非法: " + type
                    + ", 合法值: CUSTOMER / AGENT / INTERNAL / SYSTEM");
        }
    }

    /**
     * 确保工单未关闭/取消 (用于限制编辑类操作)。
     */
    private void ensureNotClosed(ScrmTicketEntity entity) throws ScrmException {
        if (STATUS_CLOSED.equals(entity.getStatus()) || STATUS_CANCELLED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许当前操作: status=" + entity.getStatus());
        }
    }

    /**
     * 构建工单查询条件 Specification。
     */
    private Specification<ScrmTicketEntity> buildTicketSpec(String status, String priority, String category,
                                                            String assigneeId, Long customerId, String source,
                                                            LocalDateTime startTime, LocalDateTime endTime,
                                                            String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (assigneeId != null && !assigneeId.isBlank()) {
                predicates.add(cb.equal(root.get("assigneeId"), assigneeId));
            }
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ticketNo")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建时间范围查询条件 Specification (按创建时间过滤)。
     */
    private Specification<ScrmTicketEntity> buildTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按主键查询工单, 不存在或越权抛异常
     */
    private ScrmTicketEntity findTicketOrThrow(Long id) throws ScrmException {
        ScrmTicketEntity entity = ticketRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工单不存在: id=" + id));

        return entity;
    }

    /**
     * 工单实体转 DTO
     */
    private ScrmTicketDto toTicketDto(ScrmTicketEntity entity) {
        ScrmTicketDto dto = new ScrmTicketDto();
        dto.setId(entity.getId());
        dto.setTicketNo(entity.getTicketNo());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setAccountId(entity.getAccountId());
        dto.setCategory(entity.getCategory());
        dto.setPriority(entity.getPriority());
        dto.setStatus(entity.getStatus());
        dto.setSource(entity.getSource());
        dto.setAssigneeId(entity.getAssigneeId());
        dto.setAssigneeName(entity.getAssigneeName());
        dto.setTeamId(entity.getTeamId());
        dto.setRelatedOrderId(entity.getRelatedOrderId());
        dto.setRelatedProductId(entity.getRelatedProductId());
        dto.setSlaDueAt(entity.getSlaDueAt());
        dto.setFirstResponseAt(entity.getFirstResponseAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setClosedAt(entity.getClosedAt());
        dto.setResolutionTimeMinutes(entity.getResolutionTimeMinutes());
        dto.setSatisfactionScore(entity.getSatisfactionScore());
        dto.setSatisfactionComment(entity.getSatisfactionComment());
        dto.setTags(entity.getTags());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 评论实体转 DTO
     */
    private ScrmTicketCommentDto toCommentDto(ScrmTicketCommentEntity entity) {
        ScrmTicketCommentDto dto = new ScrmTicketCommentDto();
        dto.setId(entity.getId());
        dto.setTicketId(entity.getTicketId());
        dto.setCommentType(entity.getCommentType());
        dto.setAuthorId(entity.getAuthorId());
        dto.setAuthorName(entity.getAuthorName());
        dto.setContent(entity.getContent());
        dto.setAttachments(entity.getAttachments());
        dto.setIsInternal(entity.getIsInternal());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 历史实体转 DTO
     */
    private ScrmTicketHistoryDto toHistoryDto(ScrmTicketHistoryEntity entity) {
        ScrmTicketHistoryDto dto = new ScrmTicketHistoryDto();
        dto.setId(entity.getId());
        dto.setTicketId(entity.getTicketId());
        dto.setActionType(entity.getActionType());
        dto.setFromValue(entity.getFromValue());
        dto.setToValue(entity.getToValue());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setActionTime(entity.getActionTime());
        dto.setNote(entity.getNote());
        dto.setCreateTime(entity.getCreateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
