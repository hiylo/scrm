/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFeedbackManagementService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmFeedbackDto;
import org.hiylo.scrm.dto.ScrmFeedbackProcessDto;
import org.hiylo.scrm.dto.ScrmFeedbackQueryDto;
import org.hiylo.scrm.entity.ScrmFeedbackCategoryEntity;
import org.hiylo.scrm.entity.ScrmFeedbackCommentEntity;
import org.hiylo.scrm.entity.ScrmFeedbackEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmFeedbackCategoryRepository;
import org.hiylo.scrm.repository.ScrmFeedbackCommentRepository;
import org.hiylo.scrm.repository.ScrmFeedbackRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户反馈管理服务: 反馈增删改查与按编号查询、分配、状态/优先级变更、解决/关闭/驳回/
 * 标记重复/合并、反馈编号生成 (FB + 年月日 + 4 位序号), 以及创建反馈时配套的情感分析、
 * 自动分类与标签提取的调度。
 * <p>
 * 所有写操作写入当前用户归属账号, 实现数据隔离, 读操作通过
 * JPA Specification 始终按当前用户可见账号范围过滤。校验失败抛出 {@link ScrmException} 携带
 * 通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmFeedbackManagementService {

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
    /** 状态: 新建 */
    private static final String STATUS_NEW = "NEW";
    /** 状态: 审核中 */
    private static final String STATUS_IN_REVIEW = "IN_REVIEW";
    /** 状态: 处理中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 已解决 */
    private static final String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已关闭 */
    private static final String STATUS_CLOSED = "CLOSED";
    /** 状态: 已驳回 */
    private static final String STATUS_REJECTED = "REJECTED";
    /** 状态: 重复 */
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    /** 反馈来源: CUSTOMER 客户 */
    private static final String SOURCE_CUSTOMER = "CUSTOMER";

    /** 评论类型: 系统 */
    private static final String COMMENT_TYPE_SYSTEM = "SYSTEM";

    /** 反馈类型: SUGGESTION 建议 */
    private static final String TYPE_SUGGESTION = "SUGGESTION";
    /** 反馈类型: COMPLAINT 投诉 */
    private static final String TYPE_COMPLAINT = "COMPLAINT";
    /** 反馈类型: COMPLIMENT 表扬 */
    private static final String TYPE_COMPLIMENT = "COMPLIMENT";
    /** 反馈类型: BUG_REPORT 缺陷报告 */
    private static final String TYPE_BUG_REPORT = "BUG_REPORT";
    /** 反馈类型: FEATURE_REQUEST 功能需求 */
    private static final String TYPE_FEATURE_REQUEST = "FEATURE_REQUEST";
    /** 反馈类型: SERVICE_ISSUE 服务问题 */
    private static final String TYPE_SERVICE_ISSUE = "SERVICE_ISSUE";
    /** 反馈类型: PRODUCT_ISSUE 产品问题 */
    private static final String TYPE_PRODUCT_ISSUE = "PRODUCT_ISSUE";
    /** 反馈类型: OTHER 其他 */
    private static final String TYPE_OTHER = "OTHER";

    /** 反馈编号前缀 */
    private static final String FEEDBACK_NO_PREFIX = "FB";
    /** 反馈编号日期格式 */
    private static final DateTimeFormatter FEEDBACK_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 反馈序号格式 (4 位, 前补零) */
    private static final String FEEDBACK_NO_SEQ_FORMAT = "%04d";
    /** 反馈序号上限 (超过则进位到 5 位) */
    private static final int FEEDBACK_NO_SEQ_BOUND = 10000;

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 合法优先级集合 */
    private static final Set<String> VALID_PRIORITIES = new HashSet<>(Arrays.asList(
            PRIORITY_URGENT, PRIORITY_HIGH, PRIORITY_MEDIUM, PRIORITY_LOW));
    /** 合法状态集合 */
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_NEW, STATUS_IN_REVIEW, STATUS_IN_PROGRESS, STATUS_RESOLVED,
            STATUS_CLOSED, STATUS_REJECTED, STATUS_DUPLICATE));
    /** 合法反馈类型集合 */
    private static final Set<String> VALID_FEEDBACK_TYPES = new HashSet<>(Arrays.asList(
            TYPE_SUGGESTION, TYPE_COMPLAINT, TYPE_COMPLIMENT, TYPE_BUG_REPORT,
            TYPE_FEATURE_REQUEST, TYPE_SERVICE_ISSUE, TYPE_PRODUCT_ISSUE, TYPE_OTHER));
    /** 合法来源集合 */
    private static final Set<String> VALID_SOURCES = new HashSet<>(Arrays.asList(
            SOURCE_CUSTOMER, "AGENT", "SYSTEM", "SURVEY", "SOCIAL", "EMAIL", "PHONE", "CHAT"));
    /** 终态状态集合 (不允许再编辑) */
    private static final Set<String> TERMINAL_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_CLOSED, STATUS_REJECTED, STATUS_DUPLICATE));

    /** 负面情感关键词 */
    private static final List<String> NEGATIVE_KEYWORDS = Arrays.asList(
            "差", "烂", "垃圾", "投诉", "不满", "失望", "生气", "愤怒", "糟糕", "坏", "慢", "等待",
            "无法", "不能", "问题", "错误", "故障", "崩溃", "卡", "退款", "赔偿", "骗", "坑",
            "bad", "terrible", "awful", "hate", "disappointed", "angry", "broken", "bug",
            "error", "fail", "slow", "useless", "waste", "worst", "horrible");
    /** 正面情感关键词 */
    private static final List<String> POSITIVE_KEYWORDS = Arrays.asList(
            "好", "棒", "优秀", "赞", "喜欢", "满意", "感谢", "谢谢", "推荐", "不错", "优质", "完美",
            "给力", "贴心", "专业", "great", "good", "excellent", "love", "like", "awesome",
            "perfect", "amazing", "wonderful", "thanks", "recommend", "happy", "satisfied", "best");

    /** 反馈数据访问层 */
    private final ScrmFeedbackRepository feedbackRepository;
    /** 反馈评论数据访问层 */
    private final ScrmFeedbackCommentRepository commentRepository;
    /** 反馈分类数据访问层 */
    private final ScrmFeedbackCategoryRepository categoryRepository;
    /** 反馈分析与统计服务 (情感分析/自动分类/标签提取) */
    private final ScrmFeedbackAnalysisService analysisService;

    // ============================================================
    // 反馈 CRUD
    // ============================================================

    /**
     * 创建反馈。
     * <p>校验参数合法性后生成唯一反馈编号 (FB + 年月日 + 4 位序号), 写入账号 ID 持久化,
     * 优先级缺省 MEDIUM, 状态缺省 NEW, 来源缺省 CUSTOMER, isPublic/isAnonymous 缺省 FALSE,
     * 计数缺省 0。同时执行情感分析、自动分类 (未指定 category 时) 与标签提取,
     * 若匹配到启用的反馈分类则应用其默认优先级/处理人/团队/SLA/自动标签。</p>
     *
     * @param dto 反馈参数
     * @return 创建后的反馈
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmFeedbackDto createFeedback(ScrmFeedbackDto dto) throws ScrmException {
        validateFeedbackDto(dto, false);
        validateFeedbackType(dto.getFeedbackType());
        if (dto.getPriority() != null) {
            validatePriority(dto.getPriority());
        }
        if (dto.getSource() != null) {
            validateSource(dto.getSource());
        }
        ScrmFeedbackEntity entity = new ScrmFeedbackEntity();
        entity.setFeedbackNo(generateFeedbackNo());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setFeedbackType(dto.getFeedbackType());
        entity.setPriority(dto.getPriority() != null ? dto.getPriority() : PRIORITY_MEDIUM);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_NEW);
        entity.setSource(dto.getSource() != null ? dto.getSource() : SOURCE_CUSTOMER);
        // 自动分类: 未指定 category 时按内容关键词匹配
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            entity.setCategory(dto.getCategory());
        } else {
            entity.setCategory(analysisService.autoCategorize(dto.getContent() + " " + dto.getTitle()));
        }
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setCustomerPhone(dto.getCustomerPhone());
        entity.setCustomerEmail(dto.getCustomerEmail());
        entity.setCustomerLevel(dto.getCustomerLevel());
        entity.setOrderId(dto.getOrderId());
        entity.setProductId(dto.getProductId());
        entity.setTicketId(dto.getTicketId());
        entity.setRating(dto.getRating());
        entity.setAttachments(dto.getAttachments());
        // 情感分析
        String sentiment = analysisService.analyzeSentiment(dto.getContent() + " " + dto.getTitle());
        entity.setSentiment(sentiment);
        entity.setSentimentScore(calcSentimentScore(dto.getContent() + " " + dto.getTitle()));
        // 标签提取 (与入参 tags 合并)
        String extractedTags = analysisService.extractTags(dto.getContent() + " " + dto.getTitle());
        String tags = mergeTags(dto.getTags(), extractedTags);
        entity.setTags(tags);
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setIsPublic(dto.getIsPublic() != null ? dto.getIsPublic() : Boolean.FALSE);
        entity.setIsAnonymous(dto.getIsAnonymous() != null ? dto.getIsAnonymous() : Boolean.FALSE);
        entity.setViewCount(0);
        entity.setUpvoteCount(0);
        entity.setCommentCount(0);
        // 匹配反馈分类: 应用默认配置
        applyCategoryDefaults(entity);
        entity = feedbackRepository.save(entity);
        log.info("创建反馈: id={}, feedbackNo={}, type={}, sentiment={}",
                entity.getId(), entity.getFeedbackNo(), entity.getFeedbackType(), entity.getSentiment());
        return toFeedbackDto(entity);
    }

    /**
     * 更新反馈（字段非空才覆盖）。
     * <p>状态、优先级、分配人、SLA/响应/解决/关闭时间等通过专用接口维护, 此处不直接修改。
     * 内容变更时重算情感与标签。</p>
     *
     * @param id  反馈 ID
     * @param dto 反馈参数
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法
     */
    @Transactional
    public ScrmFeedbackDto updateFeedback(Long id, ScrmFeedbackDto dto) throws ScrmException {
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        ensureNotTerminal(entity);
        validateFeedbackDto(dto, true);
        if (dto.getFeedbackType() != null) {
            validateFeedbackType(dto.getFeedbackType());
            entity.setFeedbackType(dto.getFeedbackType());
        }
        if (dto.getTitle() != null) {
            entity.setTitle(dto.getTitle());
        }
        boolean contentChanged = dto.getContent() != null && !dto.getContent().equals(entity.getContent());
        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
        }
        if (dto.getCategory() != null) {
            entity.setCategory(dto.getCategory());
        }
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerPhone() != null) entity.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getCustomerEmail() != null) entity.setCustomerEmail(dto.getCustomerEmail());
        if (dto.getCustomerLevel() != null) entity.setCustomerLevel(dto.getCustomerLevel());
        if (dto.getOrderId() != null) entity.setOrderId(dto.getOrderId());
        if (dto.getProductId() != null) entity.setProductId(dto.getProductId());
        if (dto.getTicketId() != null) entity.setTicketId(dto.getTicketId());
        if (dto.getRating() != null) entity.setRating(dto.getRating());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getAttachments() != null) entity.setAttachments(dto.getAttachments());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getIsAnonymous() != null) entity.setIsAnonymous(dto.getIsAnonymous());
        // 内容变更时重算情感与标签
        if (contentChanged) {
            String text = entity.getContent() + " " + entity.getTitle();
            entity.setSentiment(analysisService.analyzeSentiment(text));
            entity.setSentimentScore(calcSentimentScore(text));
        }
        entity = feedbackRepository.save(entity);
        log.info("更新反馈: id={}", id);
        return toFeedbackDto(entity);
    }

    /**
     * 删除反馈。
     * <p>级联清理反馈的评论。</p>
     *
     * @param id 反馈 ID
     * @throws ScrmException 反馈不存在
     */
    @Transactional
    public void deleteFeedback(Long id) throws ScrmException {
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        List<ScrmFeedbackCommentEntity> comments = commentRepository
                .findByFeedbackIdOrderByCreatedAtAsc(id);
        if (!comments.isEmpty()) {
            commentRepository.deleteAll(comments);
        }
        feedbackRepository.delete(entity);
        log.info("删除反馈: id={}", id);
    }

    /**
     * 查询反馈详情。
     *
     * @param id 反馈 ID
     * @return 反馈 DTO
     * @throws ScrmException 反馈不存在
     */
    @Transactional(readOnly = true)
    public ScrmFeedbackDto getFeedback(Long id) throws ScrmException {
        return toFeedbackDto(findFeedbackOrThrow(id));
    }

    /**
     * 按反馈编号查询反馈。
     *
     * @param feedbackNo 反馈编号
     * @return 反馈 DTO
     * @throws ScrmException 反馈不存在
     */
    @Transactional(readOnly = true)
    public ScrmFeedbackDto getFeedbackByNo(String feedbackNo) throws ScrmException {
        if (feedbackNo == null || feedbackNo.isBlank()) {
            throw ScrmException.badRequest("反馈编号不能为空");
        }
        ScrmFeedbackEntity entity = feedbackRepository.findByFeedbackNo(feedbackNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "反馈不存在: feedbackNo=" + feedbackNo));

        return toFeedbackDto(entity);
    }

    /**
     * 分页查询反馈, 支持按反馈类型、分类、状态、优先级、情感、客户、时间范围与关键词过滤。
     *
     * @param queryDto 查询条件
     * @param pageable 分页参数
     * @return 反馈分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmFeedbackDto> listFeedbacks(ScrmFeedbackQueryDto queryDto, Pageable pageable) {
        Specification<ScrmFeedbackEntity> spec = buildFeedbackSpec(queryDto);
        return feedbackRepository.findAll(spec, pageable).map(this::toFeedbackDto);
    }

    // ============================================================
    // 反馈动作
    // ============================================================

    /**
     * 分配反馈。
     * <p>assigneeId 与 teamId 至少传其一, 首次分配时记录 assignedAt 与首次响应时间,
     * 状态 NEW / IN_REVIEW 的反馈分配后置 IN_PROGRESS。</p>
     *
     * @param id         反馈 ID
     * @param assigneeId 处理人 ID (可空)
     * @param teamId     处理团队 ID (可空)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 参数非法 / 反馈已关闭
     */
    @Transactional
    public ScrmFeedbackDto assignFeedback(Long id, String assigneeId, String teamId) throws ScrmException {
        boolean noAssignee = assigneeId == null || assigneeId.isBlank();
        boolean noTeam = teamId == null || teamId.isBlank();
        if (noAssignee && noTeam) {
            throw ScrmException.badRequest("处理人 ID 与处理团队 ID 至少传其一");
        }
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        ensureNotTerminal(entity);
        LocalDateTime now = LocalDateTime.now();
        if (!noAssignee) {
            entity.setAssigneeId(assigneeId);
        }
        if (!noTeam) {
            entity.setTeamId(teamId);
        }
        if (entity.getAssignedAt() == null) {
            entity.setAssignedAt(now);
        }
        if (entity.getFirstResponseAt() == null) {
            entity.setFirstResponseAt(now);
            if (entity.getCreateTime() != null) {
                long hours = Duration.between(entity.getCreateTime(), now).toHours();
                entity.setResponseTimeHours((int) Math.max(0, hours));
            }
        }
        // NEW / IN_REVIEW 状态分配后进入处理中
        if (STATUS_NEW.equals(entity.getStatus()) || STATUS_IN_REVIEW.equals(entity.getStatus())) {
            entity.setStatus(STATUS_IN_PROGRESS);
        }
        entity = feedbackRepository.save(entity);
        log.info("分配反馈: id={}, assigneeId={}, teamId={}", id, assigneeId, teamId);
        return toFeedbackDto(entity);
    }

    /**
     * 变更反馈状态。
     * <p>校验目标状态合法, 记录时间戳: RESOLVED 设置 resolvedAt 与解决时长, CLOSED 设置 closedAt,
     * REJECTED/DUPLICATE 设置 closedAt。processDto.resolution 非空时写入解决方案,
     * assigneeId 非空时同步处理人。</p>
     *
     * @param processDto 处理请求 (feedbackId + status + resolution + assigneeId)
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法 / 状态流转非法
     */
    @Transactional
    public ScrmFeedbackDto changeStatus(ScrmFeedbackProcessDto processDto) throws ScrmException {
        if (processDto == null || processDto.getFeedbackId() == null) {
            throw ScrmException.badRequest("反馈 ID 不能为空");
        }
        if (processDto.getStatus() == null || processDto.getStatus().isBlank()) {
            throw ScrmException.badRequest("目标状态不能为空");
        }
        String status = processDto.getStatus();
        validateStatus(status);
        ScrmFeedbackEntity entity = findFeedbackOrThrow(processDto.getFeedbackId());
        String fromStatus = entity.getStatus();
        if (fromStatus.equals(status)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈状态未变更: status=" + status);
        }
        // 终态状态不允许变更
        if (TERMINAL_STATUSES.contains(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已处于终态, 不允许变更状态: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(status);
        if (processDto.getResolution() != null && !processDto.getResolution().isBlank()) {
            entity.setResolution(processDto.getResolution());
        }
        if (processDto.getAssigneeId() != null && !processDto.getAssigneeId().isBlank()) {
            entity.setAssigneeId(processDto.getAssigneeId());
        }
        // 状态副作用
        switch (status) {
            case STATUS_IN_PROGRESS:
            case STATUS_IN_REVIEW:
                if (entity.getFirstResponseAt() == null) {
                    entity.setFirstResponseAt(now);
                    if (entity.getCreateTime() != null) {
                        long hours = Duration.between(entity.getCreateTime(), now).toHours();
                        entity.setResponseTimeHours((int) Math.max(0, hours));
                    }
                }
                break;
            case STATUS_RESOLVED:
                entity.setResolvedAt(now);
                if (entity.getCreateTime() != null) {
                    long hours = Duration.between(entity.getCreateTime(), now).toHours();
                    entity.setResolutionTimeHours((int) Math.max(0, hours));
                }
                break;
            case STATUS_CLOSED:
                if (entity.getResolvedAt() == null) {
                    entity.setResolvedAt(now);
                    if (entity.getCreateTime() != null) {
                        long hours = Duration.between(entity.getCreateTime(), now).toHours();
                        entity.setResolutionTimeHours((int) Math.max(0, hours));
                    }
                }
                entity.setClosedAt(now);
                break;
            case STATUS_REJECTED:
            case STATUS_DUPLICATE:
                entity.setClosedAt(now);
                break;
            default:
                break;
        }
        entity = feedbackRepository.save(entity);
        log.info("变更反馈状态: id={}, from={}, to={}", processDto.getFeedbackId(), fromStatus, status);
        return toFeedbackDto(entity);
    }

    /**
     * 变更反馈优先级。
     *
     * @param id       反馈 ID
     * @param priority 目标优先级
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 优先级非法 / 反馈已关闭
     */
    @Transactional
    public ScrmFeedbackDto changePriority(Long id, String priority) throws ScrmException {
        if (priority == null || priority.isBlank()) {
            throw ScrmException.badRequest("目标优先级不能为空");
        }
        validatePriority(priority);
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        ensureNotTerminal(entity);
        String fromPriority = entity.getPriority();
        if (fromPriority.equals(priority)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈优先级未变更: priority=" + priority);
        }
        entity.setPriority(priority);
        entity = feedbackRepository.save(entity);
        log.info("变更反馈优先级: id={}, from={}, to={}", id, fromPriority, priority);
        return toFeedbackDto(entity);
    }

    /**
     * 解决反馈。
     * <p>状态置 RESOLVED, 设置 resolvedAt 与解决时长, 写入解决方案。</p>
     *
     * @param id         反馈 ID
     * @param resolution 解决方案
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @Transactional
    public ScrmFeedbackDto resolveFeedback(Long id, String resolution) throws ScrmException {
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        if (TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已处于终态, 不允许解决: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_RESOLVED);
        entity.setResolvedAt(now);
        if (entity.getCreateTime() != null) {
            long hours = Duration.between(entity.getCreateTime(), now).toHours();
            entity.setResolutionTimeHours((int) Math.max(0, hours));
        }
        if (resolution != null && !resolution.isBlank()) {
            entity.setResolution(resolution);
        }
        entity = feedbackRepository.save(entity);
        log.info("解决反馈: id={}", id);
        return toFeedbackDto(entity);
    }

    /**
     * 关闭反馈。
     * <p>状态置 CLOSED, 设置 closedAt 与 resolvedAt (若未设置), 计算解决时长。</p>
     *
     * @param id 反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @Transactional
    public ScrmFeedbackDto closeFeedback(Long id) throws ScrmException {
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        if (STATUS_CLOSED.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已关闭, 不允许再次关闭: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getResolvedAt() == null) {
            entity.setResolvedAt(now);
            if (entity.getCreateTime() != null) {
                long hours = Duration.between(entity.getCreateTime(), now).toHours();
                entity.setResolutionTimeHours((int) Math.max(0, hours));
            }
        }
        entity.setStatus(STATUS_CLOSED);
        entity.setClosedAt(now);
        entity = feedbackRepository.save(entity);
        log.info("关闭反馈: id={}", id);
        return toFeedbackDto(entity);
    }

    /**
     * 驳回反馈。
     * <p>状态置 REJECTED, 设置 closedAt, 写入驳回原因到解决方案字段。</p>
     *
     * @param id     反馈 ID
     * @param reason 驳回原因
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 状态非法
     */
    @Transactional
    public ScrmFeedbackDto rejectFeedback(Long id, String reason) throws ScrmException {
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        if (TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已处于终态, 不允许驳回: currentStatus=" + entity.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_REJECTED);
        entity.setClosedAt(now);
        if (reason != null && !reason.isBlank()) {
            entity.setResolution("驳回原因: " + reason);
        }
        entity = feedbackRepository.save(entity);
        log.info("驳回反馈: id={}, reason={}", id, reason);
        return toFeedbackDto(entity);
    }

    /**
     * 标记反馈为重复。
     * <p>状态置 DUPLICATE, 设置 closedAt, 解决方案记录原始反馈编号。originalFeedbackId 必须存在
     * 且不能等于当前反馈 ID。</p>
     *
     * @param id                 反馈 ID
     * @param originalFeedbackId 原始反馈 ID
     * @return 更新后的反馈
     * @throws ScrmException 反馈不存在 / 原始反馈不存在 / 参数非法
     */
    @Transactional
    public ScrmFeedbackDto markDuplicate(Long id, Long originalFeedbackId) throws ScrmException {
        if (originalFeedbackId == null) {
            throw ScrmException.badRequest("原始反馈 ID 不能为空");
        }
        if (originalFeedbackId.equals(id)) {
            throw ScrmException.badRequest("原始反馈 ID 不能与当前反馈 ID 相同");
        }
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        if (TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已处于终态, 不允许标记重复: currentStatus=" + entity.getStatus());
        }
        ScrmFeedbackEntity original = findFeedbackOrThrow(originalFeedbackId);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_DUPLICATE);
        entity.setClosedAt(now);
        entity.setResolution("重复反馈, 原始反馈编号: " + original.getFeedbackNo());
        entity = feedbackRepository.save(entity);
        log.info("标记重复反馈: id={}, originalFeedbackId={}", id, originalFeedbackId);
        return toFeedbackDto(entity);
    }

    /**
     * 合并反馈。
     * <p>将当前反馈 (id) 合并到目标反馈 (targetId): 当前反馈标记为 DUPLICATE 并指向目标反馈,
     * 当前反馈的评论保留但追加系统评论说明合并去向。targetId 不能等于 id。</p>
     *
     * @param id       当前反馈 ID
     * @param targetId 目标反馈 ID
     * @return 更新后的当前反馈
     * @throws ScrmException 反馈不存在 / 目标反馈不存在 / 参数非法
     */
    @Transactional
    public ScrmFeedbackDto mergeFeedback(Long id, Long targetId) throws ScrmException {
        if (targetId == null) {
            throw ScrmException.badRequest("目标反馈 ID 不能为空");
        }
        if (targetId.equals(id)) {
            throw ScrmException.badRequest("目标反馈 ID 不能与当前反馈 ID 相同");
        }
        ScrmFeedbackEntity entity = findFeedbackOrThrow(id);
        ScrmFeedbackEntity target = findFeedbackOrThrow(targetId);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(STATUS_DUPLICATE);
        entity.setClosedAt(now);
        entity.setResolution("已合并到反馈: " + target.getFeedbackNo());
        entity = feedbackRepository.save(entity);
        // 追加系统评论说明合并去向
        addSystemComment(entity.getId(), "反馈已合并到: " + target.getFeedbackNo());
        log.info("合并反馈: id={}, targetId={}", id, targetId);
        return toFeedbackDto(entity);
    }

    // ============================================================
    // 反馈编号生成
    // ============================================================

    /**
     * 生成反馈编号 (FB + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成反馈数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 反馈编号
     */
    public String generateFeedbackNo() {
        String datePart = LocalDateTime.now().format(FEEDBACK_NO_DATE_FORMAT);
        String prefix = FEEDBACK_NO_PREFIX + datePart;
        long count = feedbackRepository.countByFeedbackNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < FEEDBACK_NO_SEQ_BOUND
                ? String.format(FEEDBACK_NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 计算情感得分 (-1.0 ~ 1.0)。
     * <p>正面关键词数 - 负面关键词数, 归一化到 [-1, 1] 区间。</p>
     *
     * @param text 待分析文本
     * @return 情感得分
     */
    private Double calcSentimentScore(String text) {
        if (text == null || text.isBlank()) {
            return 0d;
        }
        String lower = text.toLowerCase();
        int positive = 0;
        int negative = 0;
        for (String kw : POSITIVE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                positive++;
            }
        }
        for (String kw : NEGATIVE_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                negative++;
            }
        }
        int total = positive + negative;
        if (total == 0) {
            return 0d;
        }
        return Math.round((double) (positive - negative) / total * 100d) / 100d;
    }

    /**
     * 合并入参标签与提取标签 (去重)。
     *
     * @param inputTags    入参标签 (逗号分隔, 可空)
     * @param extractedTags 提取标签 (逗号分隔, 可空)
     * @return 合并后的标签字符串 (可空)
     */
    private String mergeTags(String inputTags, String extractedTags) {
        Set<String> tags = new java.util.LinkedHashSet<>();
        if (inputTags != null && !inputTags.isBlank()) {
            for (String t : inputTags.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        if (extractedTags != null && !extractedTags.isBlank()) {
            for (String t : extractedTags.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    tags.add(trimmed);
                }
            }
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    /**
     * 应用反馈分类的默认配置 (优先级/处理人/团队/自动标签)。
     * <p>按反馈 category 字段匹配启用的分类, 命中则应用其 defaultPriority (若反馈未指定优先级)、
     * defaultAssigneeId / defaultTeamId (若反馈未指定) 与 autoTag (合并到 tags)。</p>
     *
     * @param entity 反馈实体
     */
    private void applyCategoryDefaults(ScrmFeedbackEntity entity) {
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            return;
        }
        ScrmFeedbackCategoryEntity category = categoryRepository.findByCategoryCode(entity.getCategory())
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()))

                .orElse(null);
        if (category == null) {
            return;
        }
        if (entity.getPriority() == null) {
            entity.setPriority(category.getDefaultPriority());
        }
        if (entity.getAssigneeId() == null && category.getDefaultAssigneeId() != null) {
            entity.setAssigneeId(category.getDefaultAssigneeId());
        }
        if (entity.getTeamId() == null && category.getDefaultTeamId() != null) {
            entity.setTeamId(category.getDefaultTeamId());
        }
        if (category.getAutoTag() != null && !category.getAutoTag().isBlank()) {
            entity.setTags(mergeTags(entity.getTags(), category.getAutoTag()));
        }
    }

    /**
     * 添加系统评论 (不刷新计数, 避免循环)。
     *
     * @param feedbackId 反馈 ID
     * @param content    评论内容
     */
    private void addSystemComment(Long feedbackId, String content) {
        ScrmFeedbackCommentEntity entity = new ScrmFeedbackCommentEntity();
        entity.setFeedbackId(feedbackId);
        entity.setCommentType(COMMENT_TYPE_SYSTEM);
        entity.setAuthorId(DEFAULT_OPERATOR);
        entity.setAuthorName(DEFAULT_OPERATOR);
        entity.setContent(content);
        entity.setIsInternal(false);
        entity.setUpvoteCount(0);
        entity.setCreatedAt(LocalDateTime.now());
        commentRepository.save(entity);
    }

    /**
     * 校验反馈参数。
     *
     * @param dto     反馈参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    private void validateFeedbackDto(ScrmFeedbackDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("反馈参数不能为空");
        }
        if (!partial) {
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("反馈标题不能为空");
            }
            if (dto.getContent() == null || dto.getContent().isBlank()) {
                throw ScrmException.badRequest("反馈内容不能为空");
            }
            if (dto.getFeedbackType() == null || dto.getFeedbackType().isBlank()) {
                throw ScrmException.badRequest("反馈类型不能为空");
            }
        }
    }

    /**
     * 校验优先级合法性。
     *
     * @param priority 优先级
     * @throws ScrmException 优先级非法
     */
    void validatePriority(String priority) throws ScrmException {
        if (!VALID_PRIORITIES.contains(priority)) {
            throw ScrmException.badRequest("优先级非法: " + priority
                    + ", 合法值: URGENT / HIGH / MEDIUM / LOW");
        }
    }

    /**
     * 校验状态合法性。
     *
     * @param status 状态
     * @throws ScrmException 状态非法
     */
    private void validateStatus(String status) throws ScrmException {
        if (!VALID_STATUSES.contains(status)) {
            throw ScrmException.badRequest("状态非法: " + status
                    + ", 合法值: NEW / IN_REVIEW / IN_PROGRESS / RESOLVED / CLOSED / REJECTED / DUPLICATE");
        }
    }

    /**
     * 校验反馈类型合法性。
     *
     * @param feedbackType 反馈类型
     * @throws ScrmException 反馈类型非法
     */
    private void validateFeedbackType(String feedbackType) throws ScrmException {
        if (!VALID_FEEDBACK_TYPES.contains(feedbackType)) {
            throw ScrmException.badRequest("反馈类型非法: " + feedbackType);
        }
    }

    /**
     * 校验来源合法性。
     *
     * @param source 来源
     * @throws ScrmException 来源非法
     */
    private void validateSource(String source) throws ScrmException {
        if (!VALID_SOURCES.contains(source)) {
            throw ScrmException.badRequest("来源非法: " + source);
        }
    }

    /**
     * 确保反馈未处于终态 (CLOSED / REJECTED / DUPLICATE)。
     *
     * @param entity 反馈实体
     * @throws ScrmException 反馈已处于终态
     */
    void ensureNotTerminal(ScrmFeedbackEntity entity) throws ScrmException {
        if (TERMINAL_STATUSES.contains(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "反馈已处于终态, 不允许当前操作: status=" + entity.getStatus());
        }
    }

    /**
     * 构建反馈查询条件 Specification。
     *
     * @param queryDto 查询条件
     * @return Specification
     */
    private Specification<ScrmFeedbackEntity> buildFeedbackSpec(ScrmFeedbackQueryDto queryDto) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (queryDto != null) {
                if (queryDto.getFeedbackType() != null && !queryDto.getFeedbackType().isBlank()) {
                    predicates.add(cb.equal(root.get("feedbackType"), queryDto.getFeedbackType()));
                }
                if (queryDto.getCategory() != null && !queryDto.getCategory().isBlank()) {
                    predicates.add(cb.equal(root.get("category"), queryDto.getCategory()));
                }
                if (queryDto.getStatus() != null && !queryDto.getStatus().isBlank()) {
                    predicates.add(cb.equal(root.get("status"), queryDto.getStatus()));
                }
                if (queryDto.getPriority() != null && !queryDto.getPriority().isBlank()) {
                    predicates.add(cb.equal(root.get("priority"), queryDto.getPriority()));
                }
                if (queryDto.getSentiment() != null && !queryDto.getSentiment().isBlank()) {
                    predicates.add(cb.equal(root.get("sentiment"), queryDto.getSentiment()));
                }
                if (queryDto.getCustomerId() != null) {
                    predicates.add(cb.equal(root.get("customerId"), queryDto.getCustomerId()));
                }
                if (queryDto.getStartTime() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), queryDto.getStartTime()));
                }
                if (queryDto.getEndTime() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), queryDto.getEndTime()));
                }
                if (queryDto.getKeyword() != null && !queryDto.getKeyword().isBlank()) {
                    String like = "%" + queryDto.getKeyword().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("feedbackNo")), like),
                            cb.like(cb.lower(root.get("title")), like),
                            cb.like(cb.lower(root.get("content")), like)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按主键查询反馈, 不存在或越权抛异常
     *
     * @param id 反馈 ID
     * @return 反馈实体
     * @throws ScrmException 反馈不存在
     */
    ScrmFeedbackEntity findFeedbackOrThrow(Long id) throws ScrmException {
        ScrmFeedbackEntity entity = feedbackRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "反馈不存在: id=" + id));

        return entity;
    }

    /**
     * 反馈实体转 DTO
     *
     * @param entity 反馈实体
     * @return 反馈 DTO
     */
    private ScrmFeedbackDto toFeedbackDto(ScrmFeedbackEntity entity) {
        ScrmFeedbackDto dto = new ScrmFeedbackDto();
        dto.setId(entity.getId());
        dto.setFeedbackNo(entity.getFeedbackNo());
        dto.setTitle(entity.getTitle());
        dto.setContent(entity.getContent());
        dto.setFeedbackType(entity.getFeedbackType());
        dto.setCategory(entity.getCategory());
        dto.setPriority(entity.getPriority());
        dto.setStatus(entity.getStatus());
        dto.setSource(entity.getSource());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCustomerPhone(entity.getCustomerPhone());
        dto.setCustomerEmail(entity.getCustomerEmail());
        dto.setCustomerLevel(entity.getCustomerLevel());
        dto.setOrderId(entity.getOrderId());
        dto.setProductId(entity.getProductId());
        dto.setTicketId(entity.getTicketId());
        dto.setSentiment(entity.getSentiment());
        dto.setSentimentScore(entity.getSentimentScore());
        dto.setRating(entity.getRating());
        dto.setTags(entity.getTags());
        dto.setAttachments(entity.getAttachments());
        dto.setAssigneeId(entity.getAssigneeId());
        dto.setAssigneeName(entity.getAssigneeName());
        dto.setTeamId(entity.getTeamId());
        dto.setAssignedAt(entity.getAssignedAt());
        dto.setFirstResponseAt(entity.getFirstResponseAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setClosedAt(entity.getClosedAt());
        dto.setResponseTimeHours(entity.getResponseTimeHours());
        dto.setResolutionTimeHours(entity.getResolutionTimeHours());
        dto.setResolution(entity.getResolution());
        dto.setSatisfactionScore(entity.getSatisfactionScore());
        dto.setIsPublic(entity.getIsPublic());
        dto.setIsAnonymous(entity.getIsAnonymous());
        dto.setViewCount(entity.getViewCount());
        dto.setUpvoteCount(entity.getUpvoteCount());
        dto.setCommentCount(entity.getCommentCount());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}
