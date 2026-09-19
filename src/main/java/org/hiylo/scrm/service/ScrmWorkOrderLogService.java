/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderLogService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmWorkOrderLogDto;
import org.hiylo.scrm.entity.ScrmWorkOrderEntity;
import org.hiylo.scrm.entity.ScrmWorkOrderLogEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmWorkOrderLogRepository;
import org.hiylo.scrm.repository.ScrmWorkOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SCRM 工单日志服务。
 * <p>
 * 承载工单日志子域: 工单日志的增删查、内部备注、工单完整历史与时间线查询,
 * 同时托管工单日志写入口 (内部使用, 供订单 / 动作 / SLA 兄弟类跨类复用)。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmWorkOrderLogService {

    /** 工单日志数据访问层 */
    private final ScrmWorkOrderLogRepository logRepository;
    /** 工单数据访问层 (日志校验工单存在与补全编号用) */
    private final ScrmWorkOrderRepository orderRepository;

    // ============================================================
    // 工单日志
    // ============================================================

    /**
     * 添加工单日志。
     *
     * @param dto 日志参数
     * @return 创建后的日志
     * @throws ScrmException 工单不存在 / 参数非法
     */
    @Transactional
    public ScrmWorkOrderLogDto addLog(ScrmWorkOrderLogDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("日志参数不能为空");
        }
        if (dto.getOrderId() == null) {
            throw ScrmException.badRequest("工单 ID 不能为空");
        }
        if (dto.getLogType() == null || dto.getLogType().isBlank()) {
            throw ScrmException.badRequest("日志类型不能为空");
        }
        ScrmWorkOrderEntity order = findOrderOrThrow(dto.getOrderId());
        ScrmWorkOrderLogEntity entity = toLogEntity(dto);
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        if (entity.getIsInternal() == null) {
            entity.setIsInternal(false);
        }
        if (entity.getIsCustomerVisible() == null) {
            entity.setIsCustomerVisible(true);
        }
        if (entity.getOperatorType() == null) {
            entity.setOperatorType(ScrmWorkOrderCrudService.OPERATOR_AGENT);
        }
        if (entity.getTimeSpentMinutes() == null) {
            entity.setTimeSpentMinutes(0);
        }
        if (entity.getBillableTime() == null) {
            entity.setBillableTime(0);
        }
        entity = logRepository.save(entity);
        return toLogDto(entity);
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志 DTO
     * @throws ScrmException 日志不存在
     */
    @Transactional(readOnly = true)
    public ScrmWorkOrderLogDto getLog(Long id) throws ScrmException {
        ScrmWorkOrderLogEntity entity = logRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "工单日志不存在: id=" + id));

        return toLogDto(entity);
    }

    /**
     * 按工单查询日志列表。
     *
     * @param orderId 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderLogDto> getLogsByOrder(Long orderId) throws ScrmException {
        findOrderOrThrow(orderId);
        return logRepository.findByOrderIdOrderByCreateTimeAsc(
                         orderId)
                .stream().map(this::toLogDto).collect(Collectors.toList());
    }

    /**
     * 按日志类型查询日志列表。
     *
     * @param type 日志类型
     * @return 日志列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderLogDto> getLogsByType(String type) {
        if (type == null || type.isBlank()) {
            return new ArrayList<>();
        }
        return logRepository.findByLogTypeOrderByCreateTimeAsc(
                         type)
                .stream().map(this::toLogDto).collect(Collectors.toList());
    }

    /**
     * 按操作人查询日志列表。
     *
     * @param operatorId 操作人 ID
     * @return 日志列表
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderLogDto> getLogsByOperator(Long operatorId) {
        if (operatorId == null) {
            return new ArrayList<>();
        }
        return logRepository.findByOperatorIdOrderByCreateTimeAsc(
                         operatorId)
                .stream().map(this::toLogDto).collect(Collectors.toList());
    }

    /**
     * 添加内部备注 (日志类型 NOTE, isInternal=true)。
     *
     * @param id           工单 ID
     * @param note         备注内容
     * @param operatorName 操作人名称
     * @return 创建后的日志
     * @throws ScrmException 工单不存在 / 内容为空
     */
    @Transactional
    public ScrmWorkOrderLogDto addInternalNote(Long id, String note, String operatorName) throws ScrmException {
        if (note == null || note.isBlank()) {
            throw ScrmException.badRequest("备注内容不能为空");
        }
        ScrmWorkOrderEntity order = findOrderOrThrow(id);
        ScrmWorkOrderLogEntity entity = new ScrmWorkOrderLogEntity();
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setLogType(ScrmWorkOrderCrudService.LOG_NOTE);
        entity.setLogTitle("内部备注");
        entity.setLogContent(note);
        entity.setIsInternal(true);
        entity.setIsCustomerVisible(false);
        entity.setOperatorName(operatorName);
        entity.setOperatorType(ScrmWorkOrderCrudService.OPERATOR_AGENT);
        entity.setTimeSpentMinutes(0);
        entity.setBillableTime(0);
        entity = logRepository.save(entity);
        log.info("添加内部备注: orderId={}, logId={}", id, entity.getId());
        return toLogDto(entity);
    }

    /**
     * 查询工单完整历史 (全部日志, 按时间升序)。
     *
     * @param id 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmWorkOrderLogDto> getOrderHistory(Long id) throws ScrmException {
        return getLogsByOrder(id);
    }

    /**
     * 查询工单时间线 (日志按时间升序分页)。
     *
     * @param id       工单 ID
     * @param pageable 分页参数
     * @return 日志分页结果
     * @throws ScrmException 工单不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmWorkOrderLogDto> getOrderTimeline(Long id, Pageable pageable) throws ScrmException {
        findOrderOrThrow(id);
        return logRepository.findByOrderIdOrderByCreateTimeAsc(
                         id, pageable)
                .map(this::toLogDto);
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

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
     * 添加工单日志 (内部构建, 不校验)。
     *
     * @param entity 日志实体
     * @return 创建后的日志 DTO
     */
    ScrmWorkOrderLogDto addLog(ScrmWorkOrderLogEntity entity) {
        entity = logRepository.save(entity);
        return toLogDto(entity);
    }

    /**
     * 构建工单日志实体 (设置默认操作人与时间)。
     *
     * @param order             工单实体
     * @param logType           日志类型
     * @param logTitle          日志标题
     * @param fromStatus        变更前状态
     * @param toStatus          变更后状态
     * @param actionTime        动作时间
     * @param content           日志内容
     * @param isInternal        是否内部
     * @param isCustomerVisible 是否客户可见
     * @return 日志实体
     */
    ScrmWorkOrderLogEntity buildLog(ScrmWorkOrderEntity order, String logType, String logTitle,
                                    String fromStatus, String toStatus, LocalDateTime actionTime,
                                    String content, boolean isInternal, boolean isCustomerVisible) {
        ScrmWorkOrderLogEntity entity = new ScrmWorkOrderLogEntity();
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setLogType(logType);
        entity.setLogTitle(logTitle);
        entity.setLogContent(content);
        entity.setFromStatus(fromStatus);
        entity.setToStatus(toStatus);
        entity.setIsInternal(isInternal);
        entity.setIsCustomerVisible(isCustomerVisible);
        entity.setOperatorName(ScrmWorkOrderCrudService.DEFAULT_OPERATOR);
        entity.setOperatorType(ScrmWorkOrderCrudService.OPERATOR_SYSTEM);
        entity.setTimeSpentMinutes(0);
        entity.setBillableTime(0);
        return entity;
    }

    /**
     * 日志实体转 DTO
     */
    ScrmWorkOrderLogDto toLogDto(ScrmWorkOrderLogEntity entity) {
        ScrmWorkOrderLogDto dto = new ScrmWorkOrderLogDto();
        dto.setId(entity.getId());
        dto.setOrderId(entity.getOrderId());
        dto.setOrderNo(entity.getOrderNo());
        dto.setLogType(entity.getLogType());
        dto.setLogTitle(entity.getLogTitle());
        dto.setLogContent(entity.getLogContent());
        dto.setFromStatus(entity.getFromStatus());
        dto.setToStatus(entity.getToStatus());
        dto.setFromAssignee(entity.getFromAssignee());
        dto.setToAssignee(entity.getToAssignee());
        dto.setFromDepartment(entity.getFromDepartment());
        dto.setToDepartment(entity.getToDepartment());
        dto.setFieldChanges(entity.getFieldChanges());
        dto.setIsInternal(entity.getIsInternal());
        dto.setIsCustomerVisible(entity.getIsCustomerVisible());
        dto.setOperatorId(entity.getOperatorId());
        dto.setOperatorName(entity.getOperatorName());
        dto.setOperatorType(entity.getOperatorType());
        dto.setAttachments(entity.getAttachments());
        dto.setMentionedUsers(entity.getMentionedUsers());
        dto.setMentionedDepartments(entity.getMentionedDepartments());
        dto.setTimeSpentMinutes(entity.getTimeSpentMinutes());
        dto.setBillableTime(entity.getBillableTime());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 日志 DTO 转实体 (不含归属账号/orderId/orderNo, 由调用方补全)
     */
    private ScrmWorkOrderLogEntity toLogEntity(ScrmWorkOrderLogDto dto) {
        ScrmWorkOrderLogEntity entity = new ScrmWorkOrderLogEntity();
        entity.setLogType(dto.getLogType());
        entity.setLogTitle(dto.getLogTitle());
        entity.setLogContent(dto.getLogContent());
        entity.setFromStatus(dto.getFromStatus());
        entity.setToStatus(dto.getToStatus());
        entity.setFromAssignee(dto.getFromAssignee());
        entity.setToAssignee(dto.getToAssignee());
        entity.setFromDepartment(dto.getFromDepartment());
        entity.setToDepartment(dto.getToDepartment());
        entity.setFieldChanges(dto.getFieldChanges());
        entity.setIsInternal(dto.getIsInternal());
        entity.setIsCustomerVisible(dto.getIsCustomerVisible());
        entity.setOperatorId(dto.getOperatorId());
        entity.setOperatorName(dto.getOperatorName());
        entity.setOperatorType(dto.getOperatorType());
        entity.setAttachments(dto.getAttachments());
        entity.setMentionedUsers(dto.getMentionedUsers());
        entity.setMentionedDepartments(dto.getMentionedDepartments());
        entity.setTimeSpentMinutes(dto.getTimeSpentMinutes());
        entity.setBillableTime(dto.getBillableTime());
        entity.setIpAddress(dto.getIpAddress());
        entity.setUserAgent(dto.getUserAgent());
        entity.setCreatedBy(dto.getCreatedBy());
        return entity;
    }
}