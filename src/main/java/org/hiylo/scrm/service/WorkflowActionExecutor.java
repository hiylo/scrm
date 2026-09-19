/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : WorkflowActionExecutor.java
 * Date : 2026/09/19 10:12:40
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.dto.ScrmFollowUpTaskDto;
import org.hiylo.scrm.dto.ScrmLifecycleTransitionRequestDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmTagCustomerDto;
import org.hiylo.scrm.dto.ScrmTicketDto;
import org.hiylo.scrm.dto.ScrmWebhookEventDto;
import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.hiylo.scrm.entity.ScrmWorkflowInstanceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流动作节点执行器。
 * <p>
 * 将工作流 ACTION 节点分派到仓库内已存在的真实业务服务, 产生真实副作用:
 * </p>
 * <ul>
 *   <li>ADD_TAG / REMOVE_TAG: {@link ScrmCustomerTagService} 客户打标 / 去标
 *       (config: {@code tagId} 或 {@code tagCode}, 可选 {@code tagValue})</li>
 *   <li>ADD_TO_SEGMENT / REMOVE_FROM_SEGMENT: {@link ScrmSegmentService} 分群成员增删
 *       (config: {@code segmentId} 或 {@code segmentCode})</li>
 *   <li>UPDATE_LIFECYCLE: {@link ScrmCustomerLifecycleService} 生命周期流转
 *       (config: {@code toStage}, 可选 {@code trigger} / {@code notes})</li>
 *   <li>NOTIFY: {@link ScrmNotificationCenterService} 通知中心发送
 *       (config: {@code templateCode} + {@code recipients}, 可选 {@code variables} / {@code priority})</li>
 *   <li>WEBHOOK: {@link ScrmWebhookService} 发布事件并按订阅配置真实 HTTP POST 推送
 *       (config: {@code eventType})</li>
 *   <li>CREATE_TASK: {@link ScrmFollowUpService} 创建跟进任务
 *       (config: {@code taskType} + {@code title} + {@code assigneeId}, 可选 {@code plannedInHours})</li>
 *   <li>CREATE_TICKET: {@link ScrmTicketService} 创建工单
 *       (config: {@code title} + {@code category}, 可选 {@code description} / {@code priority})</li>
 *   <li>UPDATE_FIELD: {@link ScrmCustomerService} 更新客户字段
 *       (config: {@code field} ∈ nickname / remark / lifecycle / personaId / avatarUrl / nextFollowUpAt
 *       + {@code value})</li>
 * </ul>
 * <p>
 * 无内部真实通道的动作 (SEND_MESSAGE / SEND_EMAIL / SEND_SMS / CALL_API / ASSIGN_OWNER) 返回
 * {@code simulated=true} 并在 {@code reason} 中说明原因, 不伪造成功。
 * 动作类型未知、必填配置缺失或下游服务失败时抛出 {@link ScrmException}, 由调用方将节点与实例置为失败。
 * </p>
 * <p>
 * 配置中的 {@code tagCode} / {@code segmentCode} 等标识按字符串或数字书写均可。
 * </p>
 *
 * @author Hsi Chu
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowActionExecutor {

    /** 支持真实执行与显式降级的全部动作类型 */
    public static final List<String> SUPPORTED_ACTION_TYPES = List.of(
            "ADD_TAG", "REMOVE_TAG", "ADD_TO_SEGMENT", "REMOVE_FROM_SEGMENT", "UPDATE_LIFECYCLE",
            "NOTIFY", "WEBHOOK", "CREATE_TASK", "CREATE_TICKET", "UPDATE_FIELD",
            "SEND_MESSAGE", "SEND_EMAIL", "SEND_SMS", "CALL_API", "ASSIGN_OWNER");

    /** UPDATE_FIELD 支持的客户字段 */
    private static final List<String> UPDATABLE_CUSTOMER_FIELDS = List.of(
            "nickname", "remark", "lifecycle", "personaId", "avatarUrl", "nextFollowUpAt");

    /** 动作来源标识 (打标来源 / 分群成员来源 / 操作人) */
    private static final String ACTION_SOURCE = "WORKFLOW";

    /** 通知记录失败状态, 与通知中心 STATUS_FAILED 一致: 用于区分"已创建"与"已送达" */
    private static final String NOTIFICATION_FAILED = "FAILED";

    /** 创建跟进任务默认计划时长 (小时) */
    private static final int DEFAULT_PLANNED_IN_HOURS = 24;

    /** JSON 解析器 */
    private final ObjectMapper objectMapper;

    /** 客户标签服务 */
    private final ScrmCustomerTagService customerTagService;

    /** 客户分群服务 */
    private final ScrmSegmentService segmentService;

    /** 客户生命周期服务 */
    private final ScrmCustomerLifecycleService lifecycleService;

    /** 通知中心服务 */
    private final ScrmNotificationCenterService notificationCenterService;

    /** Webhook 服务 */
    private final ScrmWebhookService webhookService;

    /** 跟进任务服务 */
    private final ScrmFollowUpService followUpService;

    /** 工单服务 */
    private final ScrmTicketService ticketService;

    /** 客户档案服务 */
    private final ScrmCustomerService customerService;

    /**
     * 执行工作流动作节点。
     *
     * @param actionType 动作类型 (非空, 必须在 {@link #SUPPORTED_ACTION_TYPES} 内)
     * @param config     动作配置 JSON (可空)
     * @param instance   工作流实例 (提供 customerId / customerName / variables)
     * @return 动作执行结果 (actionType / executed / simulated / message, 真实动作附带业务主键)
     * @throws ScrmException 动作类型非法 / 必填配置缺失 / 下游服务失败
     */
    @Transactional
    public Map<String, Object> execute(String actionType, String config,
                                       ScrmWorkflowInstanceEntity instance) throws ScrmException {
        if (actionType == null || actionType.isBlank()) {
            throw ScrmException.badRequest("ACTION 节点未配置 actionType: instanceId="
                    + (instance != null ? instance.getId() : null));
        }
        if (!SUPPORTED_ACTION_TYPES.contains(actionType)) {
            throw ScrmException.badRequest(
                    "动作类型不支持: " + actionType + ", 仅支持 " + SUPPORTED_ACTION_TYPES);
        }
        if (instance == null || instance.getCustomerId() == null) {
            throw ScrmException.badRequest("动作执行缺少客户上下文: actionType=" + actionType);
        }
        Map<String, Object> cfg = parseConfig(config);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("actionType", actionType);
        switch (actionType) {
            case "ADD_TAG":
                return addTag(cfg, instance, result);
            case "REMOVE_TAG":
                return removeTag(cfg, instance, result);
            case "ADD_TO_SEGMENT":
                return addToSegment(cfg, instance, result);
            case "REMOVE_FROM_SEGMENT":
                return removeFromSegment(cfg, instance, result);
            case "UPDATE_LIFECYCLE":
                return updateLifecycle(cfg, instance, result);
            case "NOTIFY":
                return notify(cfg, instance, result);
            case "WEBHOOK":
                return publishWebhook(cfg, instance, result);
            case "CREATE_TASK":
                return createFollowUpTask(cfg, instance, result);
            case "CREATE_TICKET":
                return createTicket(cfg, instance, result);
            case "UPDATE_FIELD":
                return updateCustomerField(cfg, instance, result);
            default:
                return degrade(actionType, result);
        }
    }

    /**
     * 打标: 支持 tagId 直接指定或 tagCode 反查标签 ID。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 标签标识缺失 / 标签不存在
     */
    private Map<String, Object> addTag(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                       Map<String, Object> result) throws ScrmException {
        Long tagId = resolveTagId(cfg);
        ScrmTagCustomerDto dto = new ScrmTagCustomerDto();
        dto.setCustomerId(instance.getCustomerId());
        dto.setTagId(tagId);
        dto.setTagValue(text(cfg, "tagValue"));
        dto.setTagSource(ACTION_SOURCE);
        dto.setAssignedBy(ACTION_SOURCE);
        dto.setNote("工作流实例 " + instance.getId() + " 打标");
        customerTagService.assignTag(dto);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("tagId", tagId);
        result.put("message", "已为客户 " + instance.getCustomerId() + " 打标签 " + tagId);
        return result;
    }

    /**
     * 去标。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 标签标识缺失 / 关联不存在
     */
    private Map<String, Object> removeTag(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                          Map<String, Object> result) throws ScrmException {
        Long tagId = resolveTagId(cfg);
        customerTagService.removeTag(instance.getCustomerId(), tagId);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("tagId", tagId);
        result.put("message", "已移除客户 " + instance.getCustomerId() + " 的标签 " + tagId);
        return result;
    }

    /**
     * 加入客群 (来源标记为 AUTO, 与分群自动计算来源一致, 便于后续重算收敛)。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 客群标识缺失 / 客群不存在
     */
    private Map<String, Object> addToSegment(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                             Map<String, Object> result) throws ScrmException {
        Long segmentId = resolveSegmentId(cfg);
        segmentService.addMember(segmentId, instance.getCustomerId(), ACTION_SOURCE);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("segmentId", segmentId);
        result.put("message", "已将客户 " + instance.getCustomerId() + " 加入客群 " + segmentId);
        return result;
    }

    /**
     * 移出客群。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 客群标识缺失 / 实例不存在
     */
    private Map<String, Object> removeFromSegment(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                                  Map<String, Object> result) throws ScrmException {
        Long segmentId = resolveSegmentId(cfg);
        segmentService.removeMember(segmentId, instance.getCustomerId());
        result.put("executed", true);
        result.put("simulated", false);
        result.put("segmentId", segmentId);
        result.put("message", "已将客户 " + instance.getCustomerId() + " 移出客群 " + segmentId);
        return result;
    }

    /**
     * 生命周期流转。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 目标阶段缺失 / 流转规则不允许
     */
    private Map<String, Object> updateLifecycle(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                                Map<String, Object> result) throws ScrmException {
        String toStage = requireText(cfg, "toStage");
        ScrmLifecycleTransitionRequestDto request = new ScrmLifecycleTransitionRequestDto();
        request.setCustomerId(instance.getCustomerId());
        request.setToStage(toStage);
        request.setTrigger(cfg.get("trigger") != null ? text(cfg, "trigger") : ACTION_SOURCE);
        request.setNotes(text(cfg, "notes"));
        request.setOperatorId(ACTION_SOURCE);
        request.setOperatorName("工作流 " + instance.getWorkflowName());
        lifecycleService.transitionCustomer(request);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("toStage", toStage);
        result.put("message", "客户 " + instance.getCustomerId() + " 已流转至阶段 " + toStage);
        return result;
    }

    /**
     * 通知中心发送 (渠道由模板决定, 送达语义见通知中心 {@code deliver})。
     * <p>结果同时给出创建 / 送达 / 失败三档计数: 通知中心对未接入网关的渠道 (EMAIL / SMS)
     * 与未知渠道会落 FAILED, 只报"已发送 N 条"会把失败也算进去。</p>
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 模板编码或接收者缺失
     */
    private Map<String, Object> notify(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                       Map<String, Object> result) throws ScrmException {
        ScrmNotificationSendDto dto = new ScrmNotificationSendDto();
        dto.setTemplateCode(requireText(cfg, "templateCode"));
        List<String> recipients = stringList(cfg.get("recipients"));
        if (recipients.isEmpty()) {
            throw ScrmException.badRequest("NOTIFY 动作缺少接收者: config.recipients");
        }
        dto.setRecipients(recipients);
        dto.setRecipientType(text(cfg, "recipientType"));
        dto.setVariables(stringMap(cfg.get("variables")));
        dto.setPriority(number(cfg.get("priority")) != null ? number(cfg.get("priority")).intValue() : null);
        dto.setSenderId(ACTION_SOURCE);
        dto.setSenderName(instance.getWorkflowName());
        dto.setRelatedType("WORKFLOW_INSTANCE");
        dto.setRelatedId(String.valueOf(instance.getId()));
        var sent = notificationCenterService.sendNotification(dto);
        long failed = sent.stream().filter(n -> NOTIFICATION_FAILED.equals(n.getStatus())).count();
        result.put("executed", true);
        result.put("simulated", false);
        result.put("notificationCount", sent.size());
        result.put("deliveredCount", sent.size() - failed);
        result.put("failedCount", failed);
        result.put("message", "通知节点执行完成: 创建 " + sent.size() + " 条 (模板 " + dto.getTemplateCode()
                + "), 送达 " + (sent.size() - failed) + " 条, 失败 " + failed + " 条");
        return result;
    }

    /**
     * 发布 Webhook 事件, 由订阅配置异步真实 POST。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 事件类型缺失
     */
    private Map<String, Object> publishWebhook(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                               Map<String, Object> result) throws ScrmException {
        ScrmWebhookEventDto eventDto = new ScrmWebhookEventDto();
        eventDto.setEventType(requireText(cfg, "eventType"));
        eventDto.setEntityId(instance.getCustomerId());
        eventDto.setEventData(instance.getVariables() != null
                ? instance.getVariables() : toJson(Map.of("customerId", instance.getCustomerId())));
        var logs = webhookService.publishEvent(eventDto);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("webhookLogCount", logs.size());
        result.put("message", logs.isEmpty()
                ? "事件已发布, 但无匹配的活跃 Webhook 订阅: eventType=" + eventDto.getEventType()
                : "事件已发布并投递 " + logs.size() + " 个 Webhook: eventType=" + eventDto.getEventType());
        return result;
    }

    /**
     * 创建跟进任务。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 必填字段缺失
     */
    private Map<String, Object> createFollowUpTask(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                                   Map<String, Object> result) throws ScrmException {
        ScrmFollowUpTaskDto dto = new ScrmFollowUpTaskDto();
        dto.setCustomerId(instance.getCustomerId());
        dto.setCustomerName(instance.getCustomerName());
        dto.setTaskType(requireText(cfg, "taskType"));
        dto.setTitle(requireText(cfg, "title"));
        dto.setContent(text(cfg, "content"));
        dto.setAssigneeId(requireText(cfg, "assigneeId"));
        dto.setAssigneeName(text(cfg, "assigneeName"));
        Integer plannedInHours = number(cfg.get("plannedInHours")) != null
                ? number(cfg.get("plannedInHours")).intValue() : DEFAULT_PLANNED_IN_HOURS;
        dto.setPlannedAt(LocalDateTime.now().plusHours(plannedInHours));
        dto.setCreatedBy(ACTION_SOURCE);
        var task = followUpService.createTask(dto);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("taskId", task.getId());
        result.put("message", "已创建跟进任务: taskId=" + task.getId());
        return result;
    }

    /**
     * 创建工单。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 必填字段缺失 / 类别非法
     */
    private Map<String, Object> createTicket(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                             Map<String, Object> result) throws ScrmException {
        ScrmTicketDto dto = new ScrmTicketDto();
        dto.setTitle(requireText(cfg, "title"));
        dto.setCategory(requireText(cfg, "category"));
        dto.setDescription(text(cfg, "description"));
        dto.setPriority(text(cfg, "priority"));
        dto.setCustomerId(instance.getCustomerId());
        dto.setCustomerName(instance.getCustomerName());
        dto.setCreatedBy(ACTION_SOURCE);
        var ticket = ticketService.createTicket(dto);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("ticketId", ticket.getId());
        result.put("ticketNo", ticket.getTicketNo());
        result.put("message", "已创建工单: ticketNo=" + ticket.getTicketNo());
        return result;
    }

    /**
     * 更新客户字段 (仅开放 {@link #UPDATABLE_CUSTOMER_FIELDS} 白名单)。
     *
     * @param cfg      动作配置
     * @param instance 实例
     * @param result   结果容器
     * @return 执行结果
     * @throws ScrmException 字段不在白名单 / 取值缺失或格式非法
     */
    private Map<String, Object> updateCustomerField(Map<String, Object> cfg, ScrmWorkflowInstanceEntity instance,
                                                    Map<String, Object> result) throws ScrmException {
        String field = requireText(cfg, "field");
        if (!UPDATABLE_CUSTOMER_FIELDS.contains(field)) {
            throw ScrmException.badRequest(
                    "UPDATE_FIELD 不支持的字段: " + field + ", 仅支持 " + UPDATABLE_CUSTOMER_FIELDS);
        }
        Object rawValue = cfg.get("value");
        if (rawValue == null) {
            throw ScrmException.badRequest("UPDATE_FIELD 动作缺少 value: field=" + field);
        }
        ScrmCustomerDto dto = new ScrmCustomerDto();
        switch (field) {
            case "nickname" -> dto.setNickname(String.valueOf(rawValue));
            case "remark" -> dto.setRemark(String.valueOf(rawValue));
            case "lifecycle" -> dto.setLifecycle(String.valueOf(rawValue));
            case "personaId" -> dto.setPersonaId(String.valueOf(rawValue));
            case "avatarUrl" -> dto.setAvatarUrl(String.valueOf(rawValue));
            default -> {
                try {
                    dto.setNextFollowUpAt(LocalDateTime.parse(String.valueOf(rawValue)));
                } catch (Exception e) {
                    throw ScrmException.badRequest(
                            "nextFollowUpAt 取值需为 ISO-8601 日期时间: " + rawValue);
                }
            }
        }
        customerService.updateCustomer(instance.getCustomerId(), dto);
        result.put("executed", true);
        result.put("simulated", false);
        result.put("field", field);
        result.put("message", "客户 " + instance.getCustomerId() + " 字段 " + field + " 已更新");
        return result;
    }

    /**
     * 无内部真实通道的动作: 显式降级, 标记 simulated 并说明原因, 不伪造成功。
     *
     * @param actionType 动作类型
     * @param result     结果容器
     * @return 执行结果
     */
    private Map<String, Object> degrade(String actionType, Map<String, Object> result) {
        result.put("executed", false);
        result.put("simulated", true);
        result.put("reason", switch (actionType) {
            case "SEND_MESSAGE" -> "仓库内无面向单个客户的同步消息发送通道 (群发任务需设备侧执行引擎派发)";
            case "SEND_EMAIL", "SEND_SMS" -> "仓库内无邮件 / 短信网关实现, 需接入外部服务商后启用";
            case "CALL_API" -> "无受控的通用外部 API 调用通道, 直接调用配置中的 URL 存在 SSRF 风险, 请改用 WEBHOOK 动作";
            default -> "ASSIGN_OWNER 需归属变更专用服务与操作人语义, 当前无对应内部实现";
        });
        result.put("message", "动作 " + actionType + " 未真实执行: " + result.get("reason"));
        log.warn("工作流动作未真实执行 (显式降级): actionType={}, reason={}", actionType, result.get("reason"));
        return result;
    }

    /**
     * 解析标签 ID: 优先 tagId, 其次按 tagCode 反查。
     *
     * @param cfg 动作配置
     * @return 标签 ID
     * @throws ScrmException 标识缺失或标签不存在
     */
    private Long resolveTagId(Map<String, Object> cfg) throws ScrmException {
        Long tagId = number(cfg.get("tagId")) != null ? number(cfg.get("tagId")).longValue() : null;
        if (tagId != null) {
            return tagId;
        }
        String tagCode = text(cfg, "tagCode");
        if (tagCode == null) {
            throw ScrmException.badRequest("标签动作缺少 tagId 或 tagCode");
        }
        return customerTagService.getTagByCode(tagCode).getId();
    }

    /**
     * 解析客群 ID: 优先 segmentId, 其次按 segmentCode 反查。
     *
     * @param cfg 动作配置
     * @return 客群 ID
     * @throws ScrmException 标识缺失或客群不存在
     */
    private Long resolveSegmentId(Map<String, Object> cfg) throws ScrmException {
        Long segmentId = number(cfg.get("segmentId")) != null ? number(cfg.get("segmentId")).longValue() : null;
        if (segmentId != null) {
            return segmentId;
        }
        String segmentCode = text(cfg, "segmentCode");
        if (segmentCode == null) {
            throw ScrmException.badRequest("客群动作缺少 segmentId 或 segmentCode");
        }
        ScrmSegmentEntity segment = segmentService.getSegmentByCode(segmentCode);
        return segment.getId();
    }

    /**
     * 解析动作配置 JSON。
     *
     * @param config 配置 JSON (可空)
     * @return 配置 Map, 空配置返回空 Map
     * @throws ScrmException JSON 解析失败或不是对象
     */
    private Map<String, Object> parseConfig(String config) throws ScrmException {
        if (config == null || config.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw ScrmException.badRequest("动作配置 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 读取必填字符串配置。
     *
     * @param cfg 配置 Map
     * @param key 键
     * @return 字符串值
     * @throws ScrmException 缺失或为空白
     */
    private String requireText(Map<String, Object> cfg, String key) throws ScrmException {
        String value = text(cfg, key);
        if (value == null) {
            throw ScrmException.badRequest("动作配置缺少必填项: " + key);
        }
        return value;
    }

    /**
     * 读取字符串配置。
     *
     * @param cfg 配置 Map
     * @param key 键
     * @return 字符串值, 缺失返回 null
     */
    private String text(Map<String, Object> cfg, String key) {
        Object value = cfg.get(key);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value);
        return str.isBlank() ? null : str;
    }

    /**
     * 读取数值配置 (数字或数字字符串)。
     *
     * @param value 原始值
     * @return Number, 不可解析返回 null
     */
    private Number number(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 读取字符串列表配置 (数组元素可为字符串或数字)。
     *
     * @param value 原始值
     * @return 字符串列表, 非列表返回空列表
     */
    private List<String> stringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> col) {
            for (Object item : col) {
                if (item != null) {
                    list.add(String.valueOf(item));
                }
            }
        } else if (value instanceof String s && !s.isBlank()) {
            list.add(s);
        }
        return list;
    }

    /**
     * 读取字符串键值配置 (通知模板变量)。
     *
     * @param value 原始值
     * @return 字符串 Map, 非对象返回空 Map
     */
    private Map<String, String> stringMap(Object value) {
        Map<String, String> map = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> obj) {
            obj.forEach((k, v) -> {
                if (k != null) {
                    map.put(String.valueOf(k), v == null ? null : String.valueOf(v));
                }
            });
        }
        return map;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 失败返回 null
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return null;
        }
    }
}
