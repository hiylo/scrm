/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderActionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWorkOrderAssignDto;
import org.hiylo.scrm.dto.ScrmWorkOrderDto;
import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWorkOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SCRM 工单动作服务。
 * <p>
 * 承载工单动作子域: 分配 / 接受 / 开始 / 解决 / 关闭 / 取消 / 重开 / 升级 / 响应 / 附件、
 * 关联工单查询、合并 / 拆分 / 克隆、批量分配 / 批量关闭, 以及响应与解决时长计算。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkOrderActionService {

    /** 工单数据访问层 */
    private final ScrmWorkOrderRepository orderRepository;
    /** 工单订单管理服务 (共享工具) */
    private final ScrmWorkOrderCrudService crudService;
    /** 工单日志服务 (日志写入口) */
    private final ScrmWorkOrderLogService logService;

    // ============================================================
    // 工单动作
    // ============================================================

    /**
     * 分配工单 (更新处理人 → 记录日志 → 更新 SLA)。
     * <p>assigneeId 与 department 至少传其一, 工单状态置 ASSIGNED (若原为 OPEN),
     * 设置分配时间, 记录 ASSIGNMENT 日志, 并按新优先级重算 SLA 截止。</p>
     *
     * @param assignDto 分配请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    @Transactional
    public ScrmWorkOrderDto assignOrder(ScrmWorkOrderAssignDto assignDto) throws ScrmException {
        if (assignDto == null || assignDto.getOrderId() == null) {
            throw ScrmException.badRequest("工单 ID 不能为空");
        }
        boolean noAssignee = assignDto.getAssigneeId() == null;
        boolean noDept = assignDto.getDepartment() == null || assignDto.getDepartment().isBlank();
        if (noAssignee && noDept) {
            throw ScrmException.badRequest("处理人 ID 与处理部门至少传其一");
        }
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(assignDto.getOrderId());
        ensureNotClosed(entity);
        String fromAssignee = entity.getAssignedTo();
        String fromDept = entity.getAssignedDepartment();
        LocalDateTime now = LocalDateTime.now();
        if (assignDto.getAssigneeId() != null) {
            entity.setAssignedToId(assignDto.getAssigneeId());
            entity.setAssignedTo(assignDto.getAssigneeName());
        }
        if (assignDto.getDepartment() != null && !assignDto.getDepartment().isBlank()) {
            entity.setAssignedDepartment(assignDto.getDepartment());
        }
        entity.setAssignedAt(now);
        // OPEN 状态分配后进入已分配
        if (ScrmWorkOrderCrudService.STATUS_OPEN.equals(entity.getOrderStatus())) {
            entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_ASSIGNED);
        }
        // 重算 SLA 截止 (以当前时间为基准)
        crudService.applyDefaultSla(entity, entity.getOrderType(), entity.getPriority(), now);
        entity = orderRepository.save(entity);
        // 记录分配日志
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_ASSIGNMENT, "分配工单",
                fromAssignee != null ? fromAssignee : fromDept,
                !noAssignee ? String.valueOf(assignDto.getAssigneeId()) : assignDto.getDepartment(),
                now, "分配工单: assigneeId=" + assignDto.getAssigneeId()
                        + ", department=" + assignDto.getDepartment(),
                false, true));
        log.info("分配工单: id={}, assigneeId={}, department={}",
                entity.getId(), assignDto.getAssigneeId(), assignDto.getDepartment());
        return crudService.toOrderDto(entity);
    }

    /**
     * 接受工单。
     * <p>状态置 IN_PROGRESS, 设置接受时间, 记录日志。若未设置响应时间则同步设置。</p>
     *
     * @param id         工单 ID
     * @param acceptorId 接受人 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkOrderDto acceptOrder(Long id, Long acceptorId) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        ensureNotClosed(entity);
        String fromStatus = entity.getOrderStatus();
        if (!ScrmWorkOrderCrudService.STATUS_OPEN.equals(fromStatus)
                && !ScrmWorkOrderCrudService.STATUS_ASSIGNED.equals(fromStatus)
                && !ScrmWorkOrderCrudService.STATUS_REOPENED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "仅 OPEN / ASSIGNED / REOPENED 状态可接受: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_IN_PROGRESS);
        entity.setAcceptedAt(now);
        if (acceptorId != null) {
            entity.setAssignedToId(acceptorId);
        }
        // 计算响应时间 (从创建到接受)
        if (entity.getResponseTimeMinutes() == null && entity.getCreateTime() != null) {
            entity.setResponseTimeMinutes(calculateResponseTime(entity.getCreateTime(), now));
        }
        if (entity.getLastResponseAt() == null) {
            entity.setLastResponseAt(now);
        }
        // 校验 SLA 响应达标
        if (entity.getSlaResponseDue() != null && entity.getSlaResponseMet() == null) {
            entity.setSlaResponseMet(!now.isAfter(entity.getSlaResponseDue()));
        }
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_STATUS_CHANGE, "接受工单",
                fromStatus, ScrmWorkOrderCrudService.STATUS_IN_PROGRESS, now,
                "接受工单: acceptorId=" + acceptorId, false, true));
        log.info("接受工单: id={}, acceptorId={}", id, acceptorId);
        return crudService.toOrderDto(entity);
    }

    /**
     * 开始处理工单。
     *
     * @param id 工单 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @Transactional
    public ScrmWorkOrderDto startOrder(Long id) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        ensureNotClosed(entity);
        String fromStatus = entity.getOrderStatus();
        LocalDateTime now = LocalDateTime.now();
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_IN_PROGRESS);
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(now);
        }
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_STATUS_CHANGE, "开始处理",
                fromStatus, ScrmWorkOrderCrudService.STATUS_IN_PROGRESS, now, "开始处理工单", false, true));
        log.info("开始处理工单: id={}", id);
        return crudService.toOrderDto(entity);
    }

    /**
     * 解决工单 (记录解决方案 → 计算时长 → 更新 SLA)。
     * <p>状态置 RESOLVED, 设置解决时间, 计算解决时长, 校验 SLA 解决达标,
     * 若全部 SLA 达标则清除违规标记, 记录 RESOLUTION 日志。</p>
     *
     * @param id             工单 ID
     * @param resolution     解决方案
     * @param resolutionCode 解决编码
     * @param rootCause      根本原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkOrderDto resolveOrder(Long id, String resolution, String resolutionCode, String rootCause)
            throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        String fromStatus = entity.getOrderStatus();
        if (ScrmWorkOrderCrudService.STATUS_CLOSED.equals(fromStatus)
                || ScrmWorkOrderCrudService.STATUS_CANCELLED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许解决: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_RESOLVED);
        entity.setResolvedAt(now);
        if (resolution != null) {
            entity.setResolution(resolution);
        }
        if (resolutionCode != null) {
            entity.setResolutionCode(resolutionCode);
        }
        if (rootCause != null) {
            entity.setRootCause(rootCause);
        }
        entity.setActualResolutionDate(now.toLocalDate());
        // 计算解决时长 (从接受到解决, 无接受时间则从创建算起)
        LocalDateTime base = entity.getAcceptedAt() != null ? entity.getAcceptedAt() : entity.getCreateTime();
        if (base != null) {
            entity.setResolutionTimeMinutes(calculateResolutionTime(base, now));
        }
        // 校验 SLA 解决达标
        if (entity.getSlaResolutionDue() != null) {
            boolean met = !now.isAfter(entity.getSlaResolutionDue());
            entity.setSlaResolutionMet(met);
            // 若解决达标, 清除违规标记
            if (met && (entity.getSlaResponseMet() == null || entity.getSlaResponseMet())) {
                entity.setSlaBreached(false);
            }
        }
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_RESOLUTION, "解决工单",
                fromStatus, ScrmWorkOrderCrudService.STATUS_RESOLVED, now,
                "解决工单: resolutionCode=" + resolutionCode + ", rootCause=" + rootCause,
                false, true));
        log.info("解决工单: id={}, resolutionCode={}", id, resolutionCode);
        return crudService.toOrderDto(entity);
    }

    /**
     * 关闭工单 (记录满意度 → 关闭)。
     * <p>状态置 CLOSED, 设置关闭时间, 记录满意度评价, 记录 STATUS_CHANGE 日志。</p>
     *
     * @param id                  工单 ID
     * @param satisfactionScore   满意度评分 1-5
     * @param satisfactionComment 满意度评价
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 评分越界 / 状态非法
     */
    @Transactional
    public ScrmWorkOrderDto closeOrder(Long id, Integer satisfactionScore, String satisfactionComment)
            throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        String fromStatus = entity.getOrderStatus();
        if (ScrmWorkOrderCrudService.STATUS_CLOSED.equals(fromStatus)
                || ScrmWorkOrderCrudService.STATUS_CANCELLED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许再次关闭: currentStatus=" + fromStatus);
        }
        if (satisfactionScore != null && (satisfactionScore < 1 || satisfactionScore > 5)) {
            throw ScrmException.badRequest("满意度评分需为 1-5");
        }
        LocalDateTime now = LocalDateTime.now();
        // 若未解决, 同步补齐解决信息
        if (entity.getResolvedAt() == null) {
            entity.setResolvedAt(now);
            LocalDateTime base = entity.getAcceptedAt() != null ? entity.getAcceptedAt() : entity.getCreateTime();
            if (base != null) {
                entity.setResolutionTimeMinutes(calculateResolutionTime(base, now));
            }
            entity.setActualResolutionDate(now.toLocalDate());
            if (entity.getSlaResolutionDue() != null) {
                entity.setSlaResolutionMet(!now.isAfter(entity.getSlaResolutionDue()));
            }
        }
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_CLOSED);
        entity.setClosedAt(now);
        if (satisfactionScore != null) {
            entity.setSatisfactionScore(satisfactionScore);
        }
        if (satisfactionComment != null) {
            entity.setSatisfactionComment(satisfactionComment);
        }
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_STATUS_CHANGE, "关闭工单",
                fromStatus, ScrmWorkOrderCrudService.STATUS_CLOSED, now,
                "关闭工单: satisfactionScore=" + satisfactionScore, false, true));
        log.info("关闭工单: id={}, score={}", id, satisfactionScore);
        return crudService.toOrderDto(entity);
    }

    /**
     * 取消工单。
     *
     * @param id     工单 ID
     * @param reason 取消原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkOrderDto cancelOrder(Long id, String reason) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        String fromStatus = entity.getOrderStatus();
        if (ScrmWorkOrderCrudService.STATUS_CLOSED.equals(fromStatus)
                || ScrmWorkOrderCrudService.STATUS_CANCELLED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许再次取消: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_CANCELLED);
        entity.setClosedAt(now);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_STATUS_CHANGE, "取消工单",
                fromStatus, ScrmWorkOrderCrudService.STATUS_CANCELLED, now, "取消工单: reason=" + reason, false, true));
        log.info("取消工单: id={}, reason={}", id, reason);
        return crudService.toOrderDto(entity);
    }

    /**
     * 重新打开工单。
     * <p>仅 RESOLVED / CLOSED 状态可重开, 状态置 REOPENED, 清理解决/关闭/满意度字段,
     * 重算 SLA 截止, 记录日志。</p>
     *
     * @param id     工单 ID
     * @param reason 重开原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    @Transactional
    public ScrmWorkOrderDto reopenOrder(Long id, String reason) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        String fromStatus = entity.getOrderStatus();
        if (!ScrmWorkOrderCrudService.STATUS_RESOLVED.equals(fromStatus)
                && !ScrmWorkOrderCrudService.STATUS_CLOSED.equals(fromStatus)) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "仅 RESOLVED / CLOSED 状态可重新打开: currentStatus=" + fromStatus);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_REOPENED);
        entity.setResolvedAt(null);
        entity.setClosedAt(null);
        entity.setResolutionTimeMinutes(null);
        entity.setAcceptedAt(null);
        entity.setStartedAt(null);
        entity.setSatisfactionScore(null);
        entity.setSatisfactionComment(null);
        entity.setActualResolutionDate(null);
        // 重算 SLA 截止
        crudService.applyDefaultSla(entity, entity.getOrderType(), entity.getPriority(), now);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_STATUS_CHANGE, "重新打开工单",
                fromStatus, ScrmWorkOrderCrudService.STATUS_REOPENED, now, "重新打开: reason=" + reason, false, true));
        log.info("重新打开工单: id={}, reason={}", id, reason);
        return crudService.toOrderDto(entity);
    }

    /**
     * 升级工单。
     * <p>标记 escalated=true, 设置升级时间与升级目标, 紧急工单自动标记,
     * 记录 ESCALATION 日志。</p>
     *
     * @param id          工单 ID
     * @param escalatedTo 升级到
     * @param reason      升级原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    @Transactional
    public ScrmWorkOrderDto escalateOrder(Long id, String escalatedTo, String reason) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        ensureNotClosed(entity);
        LocalDateTime now = LocalDateTime.now();
        entity.setEscalated(true);
        entity.setEscalatedTo(escalatedTo);
        entity.setEscalatedAt(now);
        entity.setEscalationReason(reason);
        // 升级后自动标记紧急
        entity.setIsUrgent(true);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_ESCALATION, "升级工单",
                null, escalatedTo, now, "升级工单: escalatedTo=" + escalatedTo
                        + ", reason=" + reason, false, true));
        log.info("升级工单: id={}, escalatedTo={}", id, escalatedTo);
        return crudService.toOrderDto(entity);
    }

    /**
     * 添加响应 (记录日志)。
     * <p>更新最后响应时间, 记录 COMMENT / INTERNAL 日志, 客户响应单独记录 CUSTOMER_RESPONSE。</p>
     *
     * @param id         工单 ID
     * @param content    响应内容
     * @param isInternal 是否内部响应
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 内容为空
     */
    @Transactional
    public ScrmWorkOrderDto addResponse(Long id, String content, Boolean isInternal) throws ScrmException {
        if (content == null || content.isBlank()) {
            throw ScrmException.badRequest("响应内容不能为空");
        }
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        ensureNotClosed(entity);
        LocalDateTime now = LocalDateTime.now();
        entity.setLastResponseAt(now);
        // 首次响应计算响应时间
        if (entity.getResponseTimeMinutes() == null && entity.getCreateTime() != null) {
            entity.setResponseTimeMinutes(calculateResponseTime(entity.getCreateTime(), now));
        }
        if (entity.getAcceptedAt() == null) {
            entity.setAcceptedAt(now);
        }
        if (entity.getSlaResponseDue() != null && entity.getSlaResponseMet() == null) {
            entity.setSlaResponseMet(!now.isAfter(entity.getSlaResponseDue()));
        }
        entity = orderRepository.save(entity);
        boolean internal = Boolean.TRUE.equals(isInternal);
        String logType = internal ? ScrmWorkOrderCrudService.LOG_INTERNAL : ScrmWorkOrderCrudService.LOG_COMMENT;
        logService.addLog(logService.buildLog(entity, logType, internal ? "内部响应" : "添加响应",
                null, null, now, content, internal, !internal));
        log.info("添加响应: id={}, isInternal={}", id, internal);
        return crudService.toOrderDto(entity);
    }

    /**
     * 添加附件。
     * <p>追加附件到 attachments 字段 (JSON 数组), 记录 ATTACHMENT 日志。</p>
     *
     * @param id            工单 ID
     * @param attachmentUrl 附件 URL
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 附件 URL 为空
     */
    @Transactional
    public ScrmWorkOrderDto addAttachment(Long id, String attachmentUrl) throws ScrmException {
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw ScrmException.badRequest("附件 URL 不能为空");
        }
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        ensureNotClosed(entity);
        String existing = entity.getAttachments();
        String newAttachments;
        if (existing == null || existing.isBlank()) {
            newAttachments = "[\"" + attachmentUrl.replace("\"", "\\\"") + "\"]";
        } else {
            // 简单追加: 在 ] 前插入
            String trimmed = existing.trim();
            if (trimmed.endsWith("]")) {
                String inner = trimmed.substring(0, trimmed.length() - 1).trim();
                if (inner.endsWith("]")) {
                    newAttachments = inner.substring(0, inner.length() - 1) + ",\"" + attachmentUrl + "\"]";
                } else {
                    newAttachments = "[\"" + attachmentUrl + "\"]";
                }
            } else {
                newAttachments = "[\"" + attachmentUrl + "\"]";
            }
        }
        entity.setAttachments(newAttachments);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_ATTACHMENT, "添加附件",
                null, attachmentUrl, LocalDateTime.now(), "添加附件: " + attachmentUrl,
                false, true));
        log.info("添加附件: id={}, url={}", id, attachmentUrl);
        return crudService.toOrderDto(entity);
    }

    /**
     * 查询关联工单 (relatedOrderIds 中保存的工单编号列表)。
     *
     * @param id 工单 ID
     * @return 关联工单列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getRelatedOrders(Long id) throws ScrmException {
        ScrmWorkOrderEntity entity = crudService.findOrderOrThrow(id);
        if (entity.getRelatedOrderIds() == null || entity.getRelatedOrderIds().isBlank()) {
            return new ArrayList<>();
        }
        List<ScrmWorkOrderDto> result = new ArrayList<>();
        for (String orderNo : entity.getRelatedOrderIds().split(",")) {
            String trimmed = orderNo.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            orderRepository.findByOrderNo(trimmed).ifPresent(o -> {
                result.add(crudService.toOrderDto(o));
            });
        }
        return result;
    }

    /**
     * 合并工单 (将源工单标记为重复, 关联到目标工单)。
     *
     * @param sourceId 源工单 ID
     * @param targetId 目标工单 ID
     * @return 目标工单
     * @throws ScrmException 工单不存在 / 源与目标相同
     */
    @Transactional
    public ScrmWorkOrderDto mergeOrders(Long sourceId, Long targetId) throws ScrmException {
        if (Objects.equals(sourceId, targetId)) {
            throw ScrmException.badRequest("源工单与目标工单不能相同");
        }
        ScrmWorkOrderEntity source = crudService.findOrderOrThrow(sourceId);
        ScrmWorkOrderEntity target = crudService.findOrderOrThrow(targetId);
        LocalDateTime now = LocalDateTime.now();
        // 源工单标记为重复
        source.setIsRepeated(true);
        source.setOrderStatus(ScrmWorkOrderCrudService.STATUS_CANCELLED);
        source.setClosedAt(now);
        // 关联编号追加
        String existing = target.getRelatedOrderIds();
        String merged = existing == null || existing.isBlank()
                ? source.getOrderNo()
                : existing + "," + source.getOrderNo();
        target.setRelatedOrderIds(merged);
        orderRepository.save(source);
        target = orderRepository.save(target);
        logService.addLog(logService.buildLog(target, ScrmWorkOrderCrudService.LOG_INTERNAL, "合并工单",
                source.getOrderNo(), target.getOrderNo(), now,
                "合并工单: source=" + source.getOrderNo() + " → target=" + target.getOrderNo(),
                true, false));
        log.info("合并工单: sourceId={}, targetId={}", sourceId, targetId);
        return crudService.toOrderDto(target);
    }

    /**
     * 拆分工单 (基于原工单创建新工单, 仅复制部分字段)。
     *
     * @param id        原工单 ID
     * @param splitData 拆分数据 (title, description, orderType 等)
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    @Transactional
    public ScrmWorkOrderDto splitOrder(Long id, Map<String, Object> splitData) throws ScrmException {
        ScrmWorkOrderEntity source = crudService.findOrderOrThrow(id);
        if (splitData == null) {
            throw ScrmException.badRequest("拆分数据不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ScrmWorkOrderEntity entity = new ScrmWorkOrderEntity();
        entity.setOrderNo(crudService.generateOrderNo());
        entity.setTitle(crudService.asString(splitData.get("title"), source.getTitle()));
        entity.setDescription(crudService.asString(splitData.get("description"), null));
        entity.setOrderType(crudService.asString(splitData.get("orderType"), source.getOrderType()));
        entity.setOrderCategory(crudService.asString(splitData.get("orderCategory"), source.getOrderCategory()));
        entity.setPriority(crudService.asString(splitData.get("priority"), source.getPriority()));
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_OPEN);
        entity.setCustomerId(source.getCustomerId());
        entity.setCustomerName(source.getCustomerName());
        entity.setCustomerPhone(source.getCustomerPhone());
        entity.setCustomerEmail(source.getCustomerEmail());
        entity.setProductId(source.getProductId());
        entity.setProductName(source.getProductName());
        entity.setProductCategory(source.getProductCategory());
        entity.setSerialNumber(source.getSerialNumber());
        entity.setContractId(source.getContractId());
        entity.setContractNo(source.getContractNo());
        entity.setSource(source.getSource());
        entity.setChannel(source.getChannel());
        entity.setRelatedOrderIds(source.getOrderNo());
        entity.setCreatedBy(crudService.asString(splitData.get("createdBy"), source.getCreatedBy()));
        entity.setSlaBreached(false);
        entity.setIsRepeated(false);
        entity.setEscalated(false);
        entity.setIsUrgent(false);
        entity.setIsVipCustomer(source.getIsVipCustomer());
        entity.setFollowUpRequired(false);
        crudService.applyDefaultSla(entity, entity.getOrderType(), entity.getPriority(), now);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_INTERNAL, "拆分工单",
                source.getOrderNo(), entity.getOrderNo(), now,
                "拆分工单: source=" + source.getOrderNo() + " → new=" + entity.getOrderNo(),
                true, false));
        log.info("拆分工单: sourceId={}, newId={}", id, entity.getId());
        return crudService.toOrderDto(entity);
    }

    /**
     * 克隆工单 (复制全部业务字段, 生成新编号, 状态置 OPEN)。
     *
     * @param id       原工单 ID
     * @param newTitle 新标题 (可空, 缺省追加 -副本 后缀)
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    @Transactional
    public ScrmWorkOrderDto cloneOrder(Long id, String newTitle) throws ScrmException {
        ScrmWorkOrderEntity source = crudService.findOrderOrThrow(id);
        LocalDateTime now = LocalDateTime.now();
        ScrmWorkOrderEntity entity = new ScrmWorkOrderEntity();
        entity.setOrderNo(crudService.generateOrderNo());
        entity.setTitle(newTitle != null && !newTitle.isBlank() ? newTitle : source.getTitle() + "-副本");
        entity.setDescription(source.getDescription());
        entity.setOrderType(source.getOrderType());
        entity.setOrderCategory(source.getOrderCategory());
        entity.setPriority(source.getPriority());
        entity.setOrderStatus(ScrmWorkOrderCrudService.STATUS_OPEN);
        entity.setCustomerId(source.getCustomerId());
        entity.setCustomerName(source.getCustomerName());
        entity.setCustomerPhone(source.getCustomerPhone());
        entity.setCustomerEmail(source.getCustomerEmail());
        entity.setContactPerson(source.getContactPerson());
        entity.setContactPhone(source.getContactPhone());
        entity.setContactEmail(source.getContactEmail());
        entity.setProductId(source.getProductId());
        entity.setProductName(source.getProductName());
        entity.setProductCategory(source.getProductCategory());
        entity.setSerialNumber(source.getSerialNumber());
        entity.setContractId(source.getContractId());
        entity.setContractNo(source.getContractNo());
        entity.setCampaignId(source.getCampaignId());
        entity.setSource(source.getSource());
        entity.setChannel(source.getChannel());
        entity.setTags(source.getTags());
        entity.setExpectedResolutionDate(source.getExpectedResolutionDate());
        entity.setResolutionDeadline(source.getResolutionDeadline());
        entity.setCreatedBy(source.getCreatedBy());
        entity.setSlaBreached(false);
        entity.setIsRepeated(false);
        entity.setEscalated(false);
        entity.setIsUrgent(source.getIsUrgent());
        entity.setIsVipCustomer(source.getIsVipCustomer());
        entity.setFollowUpRequired(false);
        crudService.applyDefaultSla(entity, entity.getOrderType(), entity.getPriority(), now);
        entity = orderRepository.save(entity);
        logService.addLog(logService.buildLog(entity, ScrmWorkOrderCrudService.LOG_INTERNAL, "克隆工单",
                source.getOrderNo(), entity.getOrderNo(), now,
                "克隆工单: source=" + source.getOrderNo() + " → new=" + entity.getOrderNo(),
                true, false));
        log.info("克隆工单: sourceId={}, newId={}", id, entity.getId());
        return crudService.toOrderDto(entity);
    }

    /**
     * 批量分配工单。
     *
     * @param orderIds     工单 ID 列表
     * @param assigneeId   处理人 ID
     * @param assigneeName 处理人名称
     * @param department   处理部门
     * @return 分配成功的工单数
     */
    @Transactional
    public int batchAssign(List<Long> orderIds, Long assigneeId, String assigneeName, String department) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : orderIds) {
            try {
                ScrmWorkOrderAssignDto dto = new ScrmWorkOrderAssignDto();
                dto.setOrderId(id);
                dto.setAssigneeId(assigneeId);
                dto.setAssigneeName(assigneeName);
                dto.setDepartment(department);
                assignOrder(dto);
                count++;
            } catch (ScrmException e) {
                log.warn("批量分配工单失败: id={}, error={}", id, e.getMessage());
            }
        }
        log.info("批量分配工单: total={}, success={}", orderIds.size(), count);
        return count;
    }

    /**
     * 批量关闭工单。
     *
     * @param orderIds            工单 ID 列表
     * @param satisfactionScore   满意度评分
     * @param satisfactionComment 满意度评价
     * @return 关闭成功的工单数
     */
    @Transactional
    public int batchClose(List<Long> orderIds, Integer satisfactionScore, String satisfactionComment) {
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : orderIds) {
            try {
                closeOrder(id, satisfactionScore, satisfactionComment);
                count++;
            } catch (ScrmException e) {
                log.warn("批量关闭工单失败: id={}, error={}", id, e.getMessage());
            }
        }
        log.info("批量关闭工单: total={}, success={}", orderIds.size(), count);
        return count;
    }

    // ============================================================
    // 时长计算
    // ============================================================

    /**
     * 计算响应时间 (从创建到接受, 分钟)。
     *
     * @param createdAt  创建时间
     * @param acceptedAt 接受时间
     * @return 响应时间分钟 (不可为负)
     */
    public int calculateResponseTime(LocalDateTime createdAt, LocalDateTime acceptedAt) {
        if (createdAt == null || acceptedAt == null) {
            return 0;
        }
        long minutes = Duration.between(createdAt, acceptedAt).toMinutes();
        return (int) Math.max(0, minutes);
    }

    /**
     * 计算解决时间 (从接受到解决, 分钟)。
     *
     * @param acceptedAt 接受时间
     * @param resolvedAt 解决时间
     * @return 解决时间分钟 (不可为负)
     */
    public int calculateResolutionTime(LocalDateTime acceptedAt, LocalDateTime resolvedAt) {
        if (acceptedAt == null || resolvedAt == null) {
            return 0;
        }
        long minutes = Duration.between(acceptedAt, resolvedAt).toMinutes();
        return (int) Math.max(0, minutes);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 确保工单未关闭/取消 (用于限制编辑类操作)。
     */
    private void ensureNotClosed(ScrmWorkOrderEntity entity) throws ScrmException {
        if (ScrmWorkOrderCrudService.STATUS_CLOSED.equals(entity.getOrderStatus())
                || ScrmWorkOrderCrudService.STATUS_CANCELLED.equals(entity.getOrderStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "工单已关闭或取消, 不允许当前操作: status=" + entity.getOrderStatus());
        }
    }
}