/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderCrudService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWorkOrderDto;
import org.hiylo.scrm.dto.ScrmWorkOrderLogDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSearchDto;
import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.hiylo.scrm.entity.ScrmWorkOrderLogEntity;
import org.hiylo.scrm.entity.ScrmWorkOrderSlaEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWorkOrderLogRepository;
import org.hiylo.scrm.repository.ScrmWorkOrderRepository;
import org.hiylo.scrm.repository.ScrmWorkOrderSlaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SCRM 工单订单管理服务。
 * <p>
 * 承载工单订单子域: 工单增删改查与按编号/客户/类型/状态/处理人/优先级查询、紧急/超期/违规工单扫描、
 * 工单导出与编号生成。同时托管工单共享常量与辅助方法 (状态/优先级/日志类型/合法集合、实体转 DTO、
 * 按主键查找、默认 SLA 应用、搜索与时间范围条件构建、参数校验), 供动作 / SLA / 统计 / 日志
 * 兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkOrderCrudService {

    // ==================== 优先级 (共享) ====================
    /** 优先级: 紧急 */
    static final String PRIORITY_URGENT = "URGENT";
    /** 优先级: 高 */
    static final String PRIORITY_HIGH = "HIGH";
    /** 优先级: 普通 */
    static final String PRIORITY_NORMAL = "NORMAL";
    /** 优先级: 低 */
    static final String PRIORITY_LOW = "LOW";

    // ==================== 状态 (共享) ====================
    /** 状态: 待处理 */
    static final String STATUS_OPEN = "OPEN";
    /** 状态: 已分配 */
    static final String STATUS_ASSIGNED = "ASSIGNED";
    /** 状态: 处理中 */
    static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 等待客户 */
    static final String STATUS_PENDING_CUSTOMER = "PENDING_CUSTOMER";
    /** 状态: 已解决 */
    static final String STATUS_RESOLVED = "RESOLVED";
    /** 状态: 已关闭 */
    static final String STATUS_CLOSED = "CLOSED";
    /** 状态: 已取消 */
    static final String STATUS_CANCELLED = "CANCELLED";
    /** 状态: 已重开 */
    static final String STATUS_REOPENED = "REOPENED";

    /** 日志类型: STATUS_CHANGE 状态变更 */
    static final String LOG_STATUS_CHANGE = "STATUS_CHANGE";
    /** 日志类型: ASSIGNMENT 工单分配 */
    static final String LOG_ASSIGNMENT = "ASSIGNMENT";
    /** 日志类型: COMMENT 评论 */
    static final String LOG_COMMENT = "COMMENT";
    /** 日志类型: ESCALATION 工单升级 */
    static final String LOG_ESCALATION = "ESCALATION";
    /** 日志类型: RESOLUTION 工单解决 */
    static final String LOG_RESOLUTION = "RESOLUTION";
    /** 日志类型: SLA_BREACH SLA 违约 */
    static final String LOG_SLA_BREACH = "SLA_BREACH";
    /** 日志类型: NOTE 备注 */
    static final String LOG_NOTE = "NOTE";
    /** 日志类型: ATTACHMENT 附件 */
    static final String LOG_ATTACHMENT = "ATTACHMENT";
    /** 日志类型: CUSTOMER_RESPONSE 客户回复 */
    static final String LOG_CUSTOMER_RESPONSE = "CUSTOMER_RESPONSE";
    /** 日志类型: INTERNAL 内部记录 */
    static final String LOG_INTERNAL = "INTERNAL";

    /** 操作者类型: AGENT 坐席 */
    static final String OPERATOR_AGENT = "AGENT";
    /** 操作者类型: SYSTEM 系统 */
    static final String OPERATOR_SYSTEM = "SYSTEM";

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    static final String DEFAULT_OPERATOR = "scrm-system";

    // ==================== 工单编号 (共享) ====================
    /** 工单编号前缀 */
    static final String ORDER_NO_PREFIX = "WO";
    /** 工单编号日期格式 */
    static final DateTimeFormatter ORDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 工单序号格式 (4 位, 前补零) */
    static final String ORDER_NO_SEQ_FORMAT = "%04d";
    /** 工单序号上限 */
    static final int ORDER_NO_SEQ_BOUND = 10000;

    /** 默认 SLA 策略编码 */
    static final String DEFAULT_SLA_POLICY_CODE = "DEFAULT_SLA";
    /** 默认 SLA 策略名称 */
    static final String DEFAULT_SLA_POLICY_NAME = "默认 SLA 策略";

    /** 未关闭状态集合 (SLA 超期扫描与工作量统计用) */
    static final List<String> OPEN_STATUSES = List.of(
            STATUS_OPEN, STATUS_ASSIGNED, STATUS_IN_PROGRESS,
            STATUS_PENDING_CUSTOMER, STATUS_REOPENED);
    /** 可评价状态集合 (RESOLVED / CLOSED / REOPENED) */
    static final Set<String> SATISFACTION_ALLOWED_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_RESOLVED, STATUS_CLOSED, STATUS_REOPENED));

    /** 合法优先级集合 */
    static final Set<String> VALID_PRIORITIES = new HashSet<>(Arrays.asList(
            PRIORITY_URGENT, PRIORITY_HIGH, PRIORITY_NORMAL, PRIORITY_LOW));
    /** 合法状态集合 */
    static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList(
            STATUS_OPEN, STATUS_ASSIGNED, STATUS_IN_PROGRESS, STATUS_PENDING_CUSTOMER,
            STATUS_RESOLVED, STATUS_CLOSED, STATUS_CANCELLED, STATUS_REOPENED));
    /** 合法工单类型集合 */
    static final Set<String> VALID_ORDER_TYPES = new HashSet<>(Arrays.asList(
            "COMPLAINT", "CONSULTATION", "MAINTENANCE", "INSTALLATION", "REPAIR",
            "SERVICE_REQUEST", "TECH_SUPPORT", "BILLING", "RETURN", "EXCHANGE",
            "FEEDBACK", "OTHER"));
    /** 合法来源集合 */
    static final Set<String> VALID_SOURCES = new HashSet<>(Arrays.asList(
            "PHONE", "EMAIL", "WEB", "APP", "WECHAT", "WALK_IN", "REFERRAL",
            "SYSTEM", "OTHER"));

    /** 工单数据访问层 */
    private final ScrmWorkOrderRepository orderRepository;
    /** 工单日志数据访问层 */
    private final ScrmWorkOrderLogRepository logRepository;
    /** SLA 策略数据访问层 */
    private final ScrmWorkOrderSlaRepository slaRepository;
    /** 工单日志服务 (日志写入口) */
    private final ScrmWorkOrderLogService logService;

    // ============================================================
    // 工单 CRUD
    // ============================================================

    /**
     * 创建工单。
     * <p>校验参数合法性后生成唯一工单编号 (WO + 年月日 + 4 位序号), 写入归属账号 ID 持久化,
     * 优先级缺省 NORMAL, 状态缺省 OPEN, 来源缺省 OTHER, 各布尔字段缺省 false,
     * 按优先级计算 SLA 截止时间。同时记录创建日志。</p>
     *
     * @param dto 工单参数
     * @return 创建后的工单
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmWorkOrderDto createOrder(ScrmWorkOrderDto dto) throws ScrmException {
        validateOrderDto(dto, false);
        LocalDateTime now = LocalDateTime.now();
        String priority = dto.getPriority() != null ? dto.getPriority() : PRIORITY_NORMAL;
        validatePriority(priority);
        String orderType = dto.getOrderType();
        validateOrderType(orderType);
        String source = dto.getSource() != null ? dto.getSource() : "OTHER";
        validateSource(source);
        ScrmWorkOrderEntity entity = new ScrmWorkOrderEntity();
        entity.setOrderNo(generateOrderNo());
        entity.setTitle(dto.getTitle());
        entity.setDescription(dto.getDescription());
        entity.setOrderType(orderType);
        entity.setOrderCategory(dto.getOrderCategory());
        entity.setPriority(priority);
        entity.setOrderStatus(dto.getOrderStatus() != null ? dto.getOrderStatus() : STATUS_OPEN);
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setCustomerPhone(dto.getCustomerPhone());
        entity.setCustomerEmail(dto.getCustomerEmail());
        entity.setContactPerson(dto.getContactPerson());
        entity.setContactPhone(dto.getContactPhone());
        entity.setContactEmail(dto.getContactEmail());
        entity.setProductId(dto.getProductId());
        entity.setProductName(dto.getProductName());
        entity.setProductCategory(dto.getProductCategory());
        entity.setSerialNumber(dto.getSerialNumber());
        entity.setContractId(dto.getContractId());
        entity.setContractNo(dto.getContractNo());
        entity.setCampaignId(dto.getCampaignId());
        entity.setSource(source);
        entity.setChannel(dto.getChannel());
        entity.setTags(dto.getTags());
        entity.setAttachments(dto.getAttachments());
        entity.setInternalNotes(dto.getInternalNotes());
        entity.setExpectedResolutionDate(dto.getExpectedResolutionDate());
        entity.setResolutionDeadline(dto.getResolutionDeadline());
        entity.setCreatedBy(dto.getCreatedBy());
        // 布尔字段缺省 false
        entity.setSlaBreached(false);
        entity.setIsRepeated(dto.getIsRepeated() != null ? dto.getIsRepeated() : false);
        entity.setEscalated(false);
        entity.setIsUrgent(dto.getIsUrgent() != null ? dto.getIsUrgent() : false);
        entity.setIsVipCustomer(dto.getIsVipCustomer() != null ? dto.getIsVipCustomer() : false);
        entity.setFollowUpRequired(dto.getFollowUpRequired() != null ? dto.getFollowUpRequired() : false);
        // 应用默认 SLA 策略
        applyDefaultSla(entity, orderType, priority, now);
        entity = orderRepository.save(entity);
        // 记录创建日志
        logService.addLog(logService.buildLog(entity, LOG_STATUS_CHANGE, "创建工单", null, entity.getOrderStatus(),
                now, "工单已创建: " + entity.getOrderNo(), false, true));
        log.info("创建工单: id={}, orderNo={}, type={}, priority={}",
                entity.getId(), entity.getOrderNo(), entity.getOrderType(), entity.getPriority());
        return toOrderDto(entity);
    }

    /**
     * 更新工单（字段非空才覆盖）。
     * <p>状态、优先级、处理人、SLA/响应/解决/关闭时间等通过专用接口维护, 此处不直接修改。
     * 工单类型变更时记录日志并重算 SLA。</p>
     *
     * @param id  工单 ID
     * @param dto 工单参数
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @Transactional
    public ScrmWorkOrderDto updateOrder(Long id, ScrmWorkOrderDto dto) throws ScrmException {
        ScrmWorkOrderEntity entity = findOrderOrThrow(id);
        validateOrderDto(dto, true);
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getOrderCategory() != null) entity.setOrderCategory(dto.getOrderCategory());
        if (dto.getCustomerName() != null) entity.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerPhone() != null) entity.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getCustomerEmail() != null) entity.setCustomerEmail(dto.getCustomerEmail());
        if (dto.getContactPerson() != null) entity.setContactPerson(dto.getContactPerson());
        if (dto.getContactPhone() != null) entity.setContactPhone(dto.getContactPhone());
        if (dto.getContactEmail() != null) entity.setContactEmail(dto.getContactEmail());
        if (dto.getProductName() != null) entity.setProductName(dto.getProductName());
        if (dto.getProductCategory() != null) entity.setProductCategory(dto.getProductCategory());
        if (dto.getSerialNumber() != null) entity.setSerialNumber(dto.getSerialNumber());
        if (dto.getContractNo() != null) entity.setContractNo(dto.getContractNo());
        if (dto.getChannel() != null) entity.setChannel(dto.getChannel());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getInternalNotes() != null) entity.setInternalNotes(dto.getInternalNotes());
        if (dto.getExpectedResolutionDate() != null) entity.setExpectedResolutionDate(dto.getExpectedResolutionDate());
        if (dto.getResolutionDeadline() != null) entity.setResolutionDeadline(dto.getResolutionDeadline());
        if (dto.getFollowUpDate() != null) entity.setFollowUpDate(dto.getFollowUpDate());
        if (dto.getFollowUpBy() != null) entity.setFollowUpBy(dto.getFollowUpBy());
        if (dto.getIsUrgent() != null) entity.setIsUrgent(dto.getIsUrgent());
        if (dto.getIsVipCustomer() != null) entity.setIsVipCustomer(dto.getIsVipCustomer());
        if (dto.getFollowUpRequired() != null) entity.setFollowUpRequired(dto.getFollowUpRequired());
        // 工单类型变更: 记录日志并重算 SLA
        if (dto.getOrderType() != null && !dto.getOrderType().equals(entity.getOrderType())) {
            validateOrderType(dto.getOrderType());
            String fromType = entity.getOrderType();
            entity.setOrderType(dto.getOrderType());
            applyDefaultSla(entity, dto.getOrderType(), entity.getPriority(), LocalDateTime.now());
            logService.addLog(logService.buildLog(entity, LOG_STATUS_CHANGE, "变更工单类型", fromType, dto.getOrderType(),
                    LocalDateTime.now(), "变更工单类型", false, true));
        }
        entity = orderRepository.save(entity);
        log.info("更新工单: id={}", id);
        return toOrderDto(entity);
    }

    /**
     * 删除工单。
     * <p>级联清理工单的日志记录。</p>
     *
     * @param id 工单 ID
     * @throws ScrmException 工单不存在
     */
    @Transactional
    public void deleteOrder(Long id) throws ScrmException {
        ScrmWorkOrderEntity entity = findOrderOrThrow(id);
        List<ScrmWorkOrderLogEntity> logs = logRepository
                .findByOrderIdOrderByCreateTimeAsc(id);
        if (!logs.isEmpty()) {
            logRepository.deleteAll(logs);
        }
        orderRepository.delete(entity);
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
    public ScrmWorkOrderDto getOrder(Long id) throws ScrmException {
        return toOrderDto(findOrderOrThrow(id));
    }

    /**
     * 按工单编号查询工单。
     *
     * @param orderNo 工单编号
     * @return 工单 DTO
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkOrderDto getOrderByNo(String orderNo) throws ScrmException {
        if (orderNo == null || orderNo.isBlank()) {
            throw ScrmException.badRequest("工单编号不能为空");
        }
        ScrmWorkOrderEntity entity = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工单不存在: orderNo=" + orderNo));

        return toOrderDto(entity);
    }

    /**
     * 分页查询工单。
     *
     * @param pageable 分页参数
     * @return 工单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkOrderDto> listOrders(Pageable pageable) {
        Specification<ScrmWorkOrderEntity> spec = (root, query, cb) ->
                cb.and();
        return orderRepository.findAll(spec, pageable).map(this::toOrderDto);
    }

    /**
     * 高级搜索工单, 支持按编号、标题、类型、优先级、状态、客户、处理人、来源、SLA 违规、
     * 时间范围与关键词组合过滤, 并支持自定义排序字段。
     *
     * @param searchDto 搜索条件
     * @param pageable  分页参数
     * @return 工单分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkOrderDto> searchOrders(ScrmWorkOrderSearchDto searchDto, Pageable pageable) {
        Specification<ScrmWorkOrderEntity> spec = buildSearchSpec(searchDto);
        return orderRepository.findAll(spec, pageable).map(this::toOrderDto);
    }

    /**
     * 按客户查询工单。
     *
     * @param customerId 客户 ID
     * @return 工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOrdersByCustomer(Long customerId) {
        if (customerId == null) {
            return new ArrayList<>();
        }
        return orderRepository.findByCustomerId(customerId)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 按工单类型查询工单。
     *
     * @param type 工单类型
     * @return 工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOrdersByType(String type) {
        if (type == null || type.isBlank()) {
            return new ArrayList<>();
        }
        return orderRepository.findByOrderType(type)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 按工单状态查询工单。
     *
     * @param status 工单状态
     * @return 工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOrdersByStatus(String status) {
        if (status == null || status.isBlank()) {
            return new ArrayList<>();
        }
        return orderRepository.findByOrderStatus(status)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 按处理人查询工单。
     *
     * @param assigneeId 处理人 ID
     * @return 工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOrdersByAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return new ArrayList<>();
        }
        return orderRepository.findByAssignedToId(assigneeId)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 按优先级查询工单。
     *
     * @param priority 优先级
     * @return 工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOrdersByPriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return new ArrayList<>();
        }
        return orderRepository.findByPriority(priority)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 查询紧急工单。
     *
     * @return 紧急工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getUrgentOrders() {
        return orderRepository.findByIsUrgentTrue()
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 查询超期工单 (SLA 解决截止早于当前时间且状态未关闭)。
     *
     * @return 超期工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getOverdueOrders() {
        return orderRepository.findBySlaResolutionDueBeforeAndOrderStatusIn(
                         LocalDateTime.now(), OPEN_STATUSES)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 查询 SLA 违规工单。
     *
     * @return 违规工单列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderDto> getSlaBreachedOrders() {
        return orderRepository.findBySlaBreachedTrue()
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    /**
     * 导出工单 (返回完整工单详情与时间线)。
     *
     * @param id 工单 ID
     * @return 导出数据 (工单详情 + 日志时间线)
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportOrder(Long id) throws ScrmException {
        ScrmWorkOrderDto order = getOrder(id);
        List<ScrmWorkOrderLogDto> timeline = logRepository
                .findByOrderIdOrderByCreateTimeAsc(id)
                .stream().map(logService::toLogDto).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", order);
        result.put("timeline", timeline);
        result.put("exportedAt", LocalDateTime.now());
        return result;
    }

    // ============================================================
    // 工单编号生成
    // ============================================================

    /**
     * 生成工单编号 (WO + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成工单数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 工单编号
     */
    public String generateOrderNo() {
        String datePart = LocalDateTime.now().format(ORDER_NO_DATE_FORMAT);
        String prefix = ORDER_NO_PREFIX + datePart;
        long count = orderRepository.countByOrderNoStartingWith(prefix);
        long seq = count + 1;
        String seqPart = seq < ORDER_NO_SEQ_BOUND
                ? String.format(ORDER_NO_SEQ_FORMAT, seq)
                : String.valueOf(seq);
        return prefix + seqPart;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 应用默认 SLA 策略到工单 (查找账号默认策略, 计算截止时间)。
     *
     * @param entity    工单实体
     * @param orderType 工单类型
     * @param priority  优先级
     * @param base      基准时间
     */
    void applyDefaultSla(ScrmWorkOrderEntity entity, String orderType, String priority, LocalDateTime base) {
        ScrmWorkOrderSlaEntity policy = resolveSlaPolicyByMatch(orderType, priority);
        if (policy == null) {
            // 兜底固定规则
            entity.setSlaPolicy(null);
            int responseMinutes;
            int resolutionMinutes;
            switch (priority != null ? priority : PRIORITY_NORMAL) {
                case PRIORITY_URGENT:
                    responseMinutes = 30;
                    resolutionMinutes = 240;
                    break;
                case PRIORITY_HIGH:
                    responseMinutes = 60;
                    resolutionMinutes = 480;
                    break;
                case PRIORITY_LOW:
                    responseMinutes = 480;
                    resolutionMinutes = 2880;
                    break;
                case PRIORITY_NORMAL:
                default:
                    responseMinutes = 120;
                    resolutionMinutes = 1440;
                    break;
            }
            entity.setSlaResponseDue(base.plusMinutes(responseMinutes));
            entity.setSlaResolutionDue(base.plusMinutes(resolutionMinutes));
        } else {
            entity.setSlaPolicy(policy.getPolicyName());
            entity.setSlaResponseDue(base.plusMinutes(policy.getResponseTimeMinutes()));
            entity.setSlaResolutionDue(base.plusMinutes(policy.getResolutionTimeMinutes()));
        }
    }

    /**
     * 按工单类型与优先级匹配 SLA 策略。
     *
     * @param orderType 工单类型
     * @param priority  优先级
     * @return 匹配的 SLA 策略 (可能为空)
     */
    ScrmWorkOrderSlaEntity resolveSlaPolicyByMatch(String orderType, String priority) {
        // 1) 工单类型 + 优先级匹配
        for (ScrmWorkOrderSlaEntity p : slaRepository.findByEnabledTrue()) {
            if (Objects.equals(p.getOrderType(), orderType) && Objects.equals(p.getPriority(), priority)) {
                return p;
            }
        }
        // 2) 仅工单类型匹配
        for (ScrmWorkOrderSlaEntity p : slaRepository.findByEnabledTrue()) {
            if (Objects.equals(p.getOrderType(), orderType) && (p.getPriority() == null || p.getPriority().isBlank())) {
                return p;
            }
        }
        // 3) 仅优先级匹配
        for (ScrmWorkOrderSlaEntity p : slaRepository.findByEnabledTrue()) {
            if ((p.getOrderType() == null || p.getOrderType().isBlank()) && Objects.equals(p.getPriority(), priority)) {
                return p;
            }
        }
        // 4) 默认策略
        return slaRepository.findByIsDefaultTrue().orElse(null);
    }

    /**
     * 校验工单参数。
     *
     * @param dto     工单参数
     * @param partial 是否为部分更新场景
     * @throws ScrmException 参数非法
     */
    void validateOrderDto(ScrmWorkOrderDto dto, boolean partial) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("工单参数不能为空");
        }
        if (!partial) {
            if (dto.getTitle() == null || dto.getTitle().isBlank()) {
                throw ScrmException.badRequest("工单标题不能为空");
            }
            if (dto.getOrderType() == null || dto.getOrderType().isBlank()) {
                throw ScrmException.badRequest("工单类型不能为空");
            }
            validateOrderType(dto.getOrderType());
            if (dto.getCustomerId() == null) {
                throw ScrmException.badRequest("客户 ID 不能为空");
            }
            if (dto.getPriority() != null) {
                validatePriority(dto.getPriority());
            }
            if (dto.getOrderStatus() != null) {
                validateStatus(dto.getOrderStatus());
            }
            if (dto.getSource() != null) {
                validateSource(dto.getSource());
            }
        } else {
            if (dto.getOrderType() != null) {
                validateOrderType(dto.getOrderType());
            }
            if (dto.getPriority() != null) {
                validatePriority(dto.getPriority());
            }
            if (dto.getOrderStatus() != null) {
                validateStatus(dto.getOrderStatus());
            }
            if (dto.getSource() != null) {
                validateSource(dto.getSource());
            }
        }
    }

    /**
     * 校验优先级合法性。
     */
    void validatePriority(String priority) throws ScrmException {
        if (!VALID_PRIORITIES.contains(priority)) {
            throw ScrmException.badRequest("优先级非法: " + priority
                    + ", 合法值: URGENT / HIGH / NORMAL / LOW");
        }
    }

    /**
     * 校验状态合法性。
     */
    void validateStatus(String status) throws ScrmException {
        if (!VALID_STATUSES.contains(status)) {
            throw ScrmException.badRequest("状态非法: " + status
 +", 合法值: OPEN / ASSIGNED / IN_PROGRESS / PENDING_CUSTOMER / RESOLVED / CLOSED / CANCELLED / REOPENED");
        }
    }

    /**
     * 校验工单类型合法性。
     */
    void validateOrderType(String orderType) throws ScrmException {
        if (!VALID_ORDER_TYPES.contains(orderType)) {
            throw ScrmException.badRequest("工单类型非法: " + orderType);
        }
    }

    /**
     * 校验来源合法性。
     */
    void validateSource(String source) throws ScrmException {
        if (!VALID_SOURCES.contains(source)) {
            throw ScrmException.badRequest("来源非法: " + source);
        }
    }

    /**
     * 构建工单搜索条件 Specification。
     */
    Specification<ScrmWorkOrderEntity> buildSearchSpec(ScrmWorkOrderSearchDto search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (search.getOrderNo() != null && !search.getOrderNo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("orderNo")),
                        "%" + search.getOrderNo().toLowerCase() + "%"));
            }
            if (search.getTitle() != null && !search.getTitle().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + search.getTitle().toLowerCase() + "%"));
            }
            if (search.getOrderType() != null && !search.getOrderType().isBlank()) {
                predicates.add(cb.equal(root.get("orderType"), search.getOrderType()));
            }
            if (search.getPriority() != null && !search.getPriority().isBlank()) {
                predicates.add(cb.equal(root.get("priority"), search.getPriority()));
            }
            if (search.getOrderStatus() != null && !search.getOrderStatus().isBlank()) {
                predicates.add(cb.equal(root.get("orderStatus"), search.getOrderStatus()));
            }
            if (search.getCustomerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), search.getCustomerId()));
            }
            if (search.getAssignedTo() != null && !search.getAssignedTo().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("assignedTo")),
                        "%" + search.getAssignedTo().toLowerCase() + "%"));
            }
            if (search.getSource() != null && !search.getSource().isBlank()) {
                predicates.add(cb.equal(root.get("source"), search.getSource()));
            }
            if (search.getSlaBreached() != null) {
                if (Boolean.TRUE.equals(search.getSlaBreached())) {
                    predicates.add(cb.isTrue(root.get("slaBreached")));
                } else {
                    predicates.add(cb.isFalse(root.get("slaBreached")));
                }
            }
            if (search.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), search.getStartDate()));
            }
            if (search.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), search.getEndDate()));
            }
            if (search.getKeyword() != null && !search.getKeyword().isBlank()) {
                String like = "%" + search.getKeyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderNo")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建时间范围查询条件 Specification (按创建时间过滤)。
     */
    Specification<ScrmWorkOrderEntity> buildTimeRangeSpec(LocalDateTime startTime, LocalDateTime endTime) {
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
    ScrmWorkOrderEntity findOrderOrThrow(Long id) throws ScrmException {
        ScrmWorkOrderEntity entity = orderRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工单不存在: id=" + id));

        return entity;
    }

    /**
     * Map 值转 String, 为空返回默认值。
     */
    String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? defaultValue : s;
    }

    /**
     * 工单实体转 DTO
     */
    ScrmWorkOrderDto toOrderDto(ScrmWorkOrderEntity entity) {
        ScrmWorkOrderDto dto = new ScrmWorkOrderDto();
        dto.setId(entity.getId());
        dto.setOrderNo(entity.getOrderNo());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setOrderType(entity.getOrderType());
        dto.setOrderCategory(entity.getOrderCategory());
        dto.setPriority(entity.getPriority());
        dto.setOrderStatus(entity.getOrderStatus());
        dto.setCustomerId(entity.getCustomerId());
        dto.setCustomerName(entity.getCustomerName());
        dto.setCustomerPhone(entity.getCustomerPhone());
        dto.setCustomerEmail(entity.getCustomerEmail());
        dto.setContactPerson(entity.getContactPerson());
        dto.setContactPhone(entity.getContactPhone());
        dto.setContactEmail(entity.getContactEmail());
        dto.setProductId(entity.getProductId());
        dto.setProductName(entity.getProductName());
        dto.setProductCategory(entity.getProductCategory());
        dto.setSerialNumber(entity.getSerialNumber());
        dto.setContractId(entity.getContractId());
        dto.setContractNo(entity.getContractNo());
        dto.setCampaignId(entity.getCampaignId());
        dto.setSource(entity.getSource());
        dto.setChannel(entity.getChannel());
        dto.setAssignedTo(entity.getAssignedTo());
        dto.setAssignedToId(entity.getAssignedToId());
        dto.setAssignedDepartment(entity.getAssignedDepartment());
        dto.setAssignedAt(entity.getAssignedAt());
        dto.setAcceptedAt(entity.getAcceptedAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setResolvedAt(entity.getResolvedAt());
        dto.setClosedAt(entity.getClosedAt());
        dto.setLastResponseAt(entity.getLastResponseAt());
        dto.setResponseTimeMinutes(entity.getResponseTimeMinutes());
        dto.setResolutionTimeMinutes(entity.getResolutionTimeMinutes());
        dto.setSlaPolicy(entity.getSlaPolicy());
        dto.setSlaResponseDue(entity.getSlaResponseDue());
        dto.setSlaResolutionDue(entity.getSlaResolutionDue());
        dto.setSlaResponseMet(entity.getSlaResponseMet());
        dto.setSlaResolutionMet(entity.getSlaResolutionMet());
        dto.setSlaBreached(entity.getSlaBreached());
        dto.setSatisfactionScore(entity.getSatisfactionScore());
        dto.setSatisfactionComment(entity.getSatisfactionComment());
        dto.setResolution(entity.getResolution());
        dto.setResolutionCode(entity.getResolutionCode());
        dto.setRootCause(entity.getRootCause());
        dto.setIsRepeated(entity.getIsRepeated());
        dto.setRelatedOrderIds(entity.getRelatedOrderIds());
        dto.setEscalated(entity.getEscalated());
        dto.setEscalatedTo(entity.getEscalatedTo());
        dto.setEscalatedAt(entity.getEscalatedAt());
        dto.setEscalationReason(entity.getEscalationReason());
        dto.setTags(entity.getTags());
        dto.setAttachments(entity.getAttachments());
        dto.setInternalNotes(entity.getInternalNotes());
        dto.setExpectedResolutionDate(entity.getExpectedResolutionDate());
        dto.setActualResolutionDate(entity.getActualResolutionDate());
        dto.setResolutionDeadline(entity.getResolutionDeadline());
        dto.setIsUrgent(entity.getIsUrgent());
        dto.setIsVipCustomer(entity.getIsVipCustomer());
        dto.setFollowUpRequired(entity.getFollowUpRequired());
        dto.setFollowUpDate(entity.getFollowUpDate());
        dto.setFollowUpBy(entity.getFollowUpBy());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}