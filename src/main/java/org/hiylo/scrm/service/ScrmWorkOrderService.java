/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmWorkOrderService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmWorkOrderAssignDto;
import org.hiylo.scrm.dto.ScrmWorkOrderDto;
import org.hiylo.scrm.dto.ScrmWorkOrderLogDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSearchDto;
import org.hiylo.scrm.dto.ScrmWorkOrderSlaDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 工单管理服务 (门面)。
 * <p>
 * 作为工单模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmWorkOrderCrudService} (订单 CRUD/查询/编号)、{@link ScrmWorkOrderActionService} (分配关闭与动作)、
 * {@link ScrmWorkOrderSlaService} (SLA 策略与检查)、{@link ScrmWorkOrderStatsService} (统计报表) 与
 * {@link ScrmWorkOrderLogService} (日志记录)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmWorkOrderService {

    /** 订单 CRUD/查询/编号子域服务 */
    private final ScrmWorkOrderCrudService crudService;
    /** 分配关闭/动作子域服务 */
    private final ScrmWorkOrderActionService actionService;
    /** SLA 策略与检查子域服务 */
    private final ScrmWorkOrderSlaService slaService;
    /** 统计报表子域服务 */
    private final ScrmWorkOrderStatsService statsService;
    /** 日志记录子域服务 */
    private final ScrmWorkOrderLogService logService;

    // ============================================================
    // 工单 CRUD
    // ============================================================

    /**
     * 创建工单。
     *
     * @param dto 工单参数
     * @return 创建后的工单
     * @throws ScrmException 参数非法
     */
    public ScrmWorkOrderDto createOrder(ScrmWorkOrderDto dto) throws ScrmException {
        return crudService.createOrder(dto);
    }

    /**
     * 更新工单（字段非空才覆盖）。
     *
     * @param id  工单 ID
     * @param dto 工单参数
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法
     */
    public ScrmWorkOrderDto updateOrder(Long id, ScrmWorkOrderDto dto) throws ScrmException {
        return crudService.updateOrder(id, dto);
    }

    /**
     * 删除工单。
     *
     * @param id 工单 ID
     * @throws ScrmException 工单不存在
     */
    public void deleteOrder(Long id) throws ScrmException {
        crudService.deleteOrder(id);
    }

    /**
     * 查询工单详情。
     *
     * @param id 工单 ID
     * @return 工单 DTO
     * @throws ScrmException 工单不存在
     */
    public ScrmWorkOrderDto getOrder(Long id) throws ScrmException {
        return crudService.getOrder(id);
    }

    /**
     * 按工单编号查询工单。
     *
     * @param orderNo 工单编号
     * @return 工单 DTO
     * @throws ScrmException 工单不存在
     */
    public ScrmWorkOrderDto getOrderByNo(String orderNo) throws ScrmException {
        return crudService.getOrderByNo(orderNo);
    }

    /**
     * 分页查询工单。
     *
     * @param pageable 分页参数
     * @return 工单分页结果
     */
    public Page<ScrmWorkOrderDto> listOrders(Pageable pageable) {
        return crudService.listOrders(pageable);
    }

    /**
     * 高级搜索工单。
     *
     * @param searchDto 搜索条件
     * @param pageable  分页参数
     * @return 工单分页结果
     */
    public Page<ScrmWorkOrderDto> searchOrders(ScrmWorkOrderSearchDto searchDto, Pageable pageable) {
        return crudService.searchOrders(searchDto, pageable);
    }

    /**
     * 按客户查询工单。
     *
     * @param customerId 客户 ID
     * @return 工单列表
     */
    public List<ScrmWorkOrderDto> getOrdersByCustomer(Long customerId) {
        return crudService.getOrdersByCustomer(customerId);
    }

    /**
     * 按工单类型查询工单。
     *
     * @param type 工单类型
     * @return 工单列表
     */
    public List<ScrmWorkOrderDto> getOrdersByType(String type) {
        return crudService.getOrdersByType(type);
    }

    /**
     * 按工单状态查询工单。
     *
     * @param status 工单状态
     * @return 工单列表
     */
    public List<ScrmWorkOrderDto> getOrdersByStatus(String status) {
        return crudService.getOrdersByStatus(status);
    }

    /**
     * 按处理人查询工单。
     *
     * @param assigneeId 处理人 ID
     * @return 工单列表
     */
    public List<ScrmWorkOrderDto> getOrdersByAssignee(Long assigneeId) {
        return crudService.getOrdersByAssignee(assigneeId);
    }

    /**
     * 按优先级查询工单。
     *
     * @param priority 优先级
     * @return 工单列表
     */
    public List<ScrmWorkOrderDto> getOrdersByPriority(String priority) {
        return crudService.getOrdersByPriority(priority);
    }

    /**
     * 查询紧急工单。
     *
     * @return 紧急工单列表
     */
    public List<ScrmWorkOrderDto> getUrgentOrders() {
        return crudService.getUrgentOrders();
    }

    /**
     * 查询超期工单。
     *
     * @return 超期工单列表
     */
    public List<ScrmWorkOrderDto> getOverdueOrders() {
        return crudService.getOverdueOrders();
    }

    /**
     * 查询 SLA 违规工单。
     *
     * @return 违规工单列表
     */
    public List<ScrmWorkOrderDto> getSlaBreachedOrders() {
        return crudService.getSlaBreachedOrders();
    }

    // ============================================================
    // 工单动作
    // ============================================================

    /**
     * 分配工单。
     *
     * @param assignDto 分配请求
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 参数非法 / 工单已关闭
     */
    public ScrmWorkOrderDto assignOrder(ScrmWorkOrderAssignDto assignDto) throws ScrmException {
        return actionService.assignOrder(assignDto);
    }

    /**
     * 接受工单。
     *
     * @param id         工单 ID
     * @param acceptorId 接受人 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    public ScrmWorkOrderDto acceptOrder(Long id, Long acceptorId) throws ScrmException {
        return actionService.acceptOrder(id, acceptorId);
    }

    /**
     * 开始处理工单。
     *
     * @param id 工单 ID
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    public ScrmWorkOrderDto startOrder(Long id) throws ScrmException {
        return actionService.startOrder(id);
    }

    /**
     * 解决工单。
     *
     * @param id             工单 ID
     * @param resolution     解决方案
     * @param resolutionCode 解决编码
     * @param rootCause      根本原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    public ScrmWorkOrderDto resolveOrder(Long id, String resolution, String resolutionCode, String rootCause)
            throws ScrmException {
        return actionService.resolveOrder(id, resolution, resolutionCode, rootCause);
    }

    /**
     * 关闭工单。
     *
     * @param id                  工单 ID
     * @param satisfactionScore   满意度评分 1-5
     * @param satisfactionComment 满意度评价
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 评分越界 / 状态非法
     */
    public ScrmWorkOrderDto closeOrder(Long id, Integer satisfactionScore, String satisfactionComment)
            throws ScrmException {
        return actionService.closeOrder(id, satisfactionScore, satisfactionComment);
    }

    /**
     * 取消工单。
     *
     * @param id     工单 ID
     * @param reason 取消原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    public ScrmWorkOrderDto cancelOrder(Long id, String reason) throws ScrmException {
        return actionService.cancelOrder(id, reason);
    }

    /**
     * 重新打开工单。
     *
     * @param id     工单 ID
     * @param reason 重开原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 状态非法
     */
    public ScrmWorkOrderDto reopenOrder(Long id, String reason) throws ScrmException {
        return actionService.reopenOrder(id, reason);
    }

    /**
     * 升级工单。
     *
     * @param id          工单 ID
     * @param escalatedTo 升级到
     * @param reason      升级原因
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 工单已关闭
     */
    public ScrmWorkOrderDto escalateOrder(Long id, String escalatedTo, String reason) throws ScrmException {
        return actionService.escalateOrder(id, escalatedTo, reason);
    }

    /**
     * 添加响应。
     *
     * @param id         工单 ID
     * @param content    响应内容
     * @param isInternal 是否内部响应
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 内容为空
     */
    public ScrmWorkOrderDto addResponse(Long id, String content, Boolean isInternal) throws ScrmException {
        return actionService.addResponse(id, content, isInternal);
    }

    /**
     * 添加附件。
     *
     * @param id            工单 ID
     * @param attachmentUrl 附件 URL
     * @return 更新后的工单
     * @throws ScrmException 工单不存在 / 附件 URL 为空
     */
    public ScrmWorkOrderDto addAttachment(Long id, String attachmentUrl) throws ScrmException {
        return actionService.addAttachment(id, attachmentUrl);
    }

    /**
     * 查询工单时间线。
     *
     * @param id       工单 ID
     * @param pageable 分页参数
     * @return 日志分页结果
     * @throws ScrmException 工单不存在
     */
    public Page<ScrmWorkOrderLogDto> getOrderTimeline(Long id, Pageable pageable) throws ScrmException {
        return logService.getOrderTimeline(id, pageable);
    }

    /**
     * 查询关联工单。
     *
     * @param id 工单 ID
     * @return 关联工单列表
     * @throws ScrmException 工单不存在
     */
    public List<ScrmWorkOrderDto> getRelatedOrders(Long id) throws ScrmException {
        return actionService.getRelatedOrders(id);
    }

    /**
     * 合并工单。
     *
     * @param sourceId 源工单 ID
     * @param targetId 目标工单 ID
     * @return 目标工单
     * @throws ScrmException 工单不存在 / 源与目标相同
     */
    public ScrmWorkOrderDto mergeOrders(Long sourceId, Long targetId) throws ScrmException {
        return actionService.mergeOrders(sourceId, targetId);
    }

    /**
     * 拆分工单。
     *
     * @param id        原工单 ID
     * @param splitData 拆分数据
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    public ScrmWorkOrderDto splitOrder(Long id, Map<String, Object> splitData) throws ScrmException {
        return actionService.splitOrder(id, splitData);
    }

    /**
     * 克隆工单。
     *
     * @param id       原工单 ID
     * @param newTitle 新标题
     * @return 新工单
     * @throws ScrmException 工单不存在
     */
    public ScrmWorkOrderDto cloneOrder(Long id, String newTitle) throws ScrmException {
        return actionService.cloneOrder(id, newTitle);
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
    public int batchAssign(List<Long> orderIds, Long assigneeId, String assigneeName, String department) {
        return actionService.batchAssign(orderIds, assigneeId, assigneeName, department);
    }

    /**
     * 批量关闭工单。
     *
     * @param orderIds            工单 ID 列表
     * @param satisfactionScore   满意度评分
     * @param satisfactionComment 满意度评价
     * @return 关闭成功的工单数
     */
    public int batchClose(List<Long> orderIds, Integer satisfactionScore, String satisfactionComment) {
        return actionService.batchClose(orderIds, satisfactionScore, satisfactionComment);
    }

    /**
     * 导出工单。
     *
     * @param id 工单 ID
     * @return 导出数据
     * @throws ScrmException 工单不存在
     */
    public Map<String, Object> exportOrder(Long id) throws ScrmException {
        return crudService.exportOrder(id);
    }

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
    public ScrmWorkOrderLogDto addLog(ScrmWorkOrderLogDto dto) throws ScrmException {
        return logService.addLog(dto);
    }

    /**
     * 查询日志详情。
     *
     * @param id 日志 ID
     * @return 日志 DTO
     * @throws ScrmException 日志不存在
     */
    public ScrmWorkOrderLogDto getLog(Long id) throws ScrmException {
        return logService.getLog(id);
    }

    /**
     * 按工单查询日志列表。
     *
     * @param orderId 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    public List<ScrmWorkOrderLogDto> getLogsByOrder(Long orderId) throws ScrmException {
        return logService.getLogsByOrder(orderId);
    }

    /**
     * 按日志类型查询日志列表。
     *
     * @param type 日志类型
     * @return 日志列表
     */
    public List<ScrmWorkOrderLogDto> getLogsByType(String type) {
        return logService.getLogsByType(type);
    }

    /**
     * 按操作人查询日志列表。
     *
     * @param operatorId 操作人 ID
     * @return 日志列表
     */
    public List<ScrmWorkOrderLogDto> getLogsByOperator(Long operatorId) {
        return logService.getLogsByOperator(operatorId);
    }

    /**
     * 添加内部备注。
     *
     * @param id           工单 ID
     * @param note         备注内容
     * @param operatorName 操作人名称
     * @return 创建后的日志
     * @throws ScrmException 工单不存在 / 内容为空
     */
    public ScrmWorkOrderLogDto addInternalNote(Long id, String note, String operatorName) throws ScrmException {
        return logService.addInternalNote(id, note, operatorName);
    }

    /**
     * 查询工单完整历史。
     *
     * @param id 工单 ID
     * @return 日志列表
     * @throws ScrmException 工单不存在
     */
    public List<ScrmWorkOrderLogDto> getOrderHistory(Long id) throws ScrmException {
        return logService.getOrderHistory(id);
    }

    // ============================================================
    // SLA 策略管理
    // ============================================================

    /**
     * 创建 SLA 策略。
     *
     * @param dto SLA 策略参数
     * @return 创建后的 SLA 策略
     * @throws ScrmException 参数非法 / 名称或编码重复
     */
    public ScrmWorkOrderSlaDto createSlaPolicy(ScrmWorkOrderSlaDto dto) throws ScrmException {
        return slaService.createSlaPolicy(dto);
    }

    /**
     * 更新 SLA 策略。
     *
     * @param id  SLA 策略 ID
     * @param dto SLA 策略参数
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在 / 参数非法
     */
    public ScrmWorkOrderSlaDto updateSlaPolicy(Long id, ScrmWorkOrderSlaDto dto) throws ScrmException {
        return slaService.updateSlaPolicy(id, dto);
    }

    /**
     * 删除 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @throws ScrmException 策略不存在
     */
    public void deleteSlaPolicy(Long id) throws ScrmException {
        slaService.deleteSlaPolicy(id);
    }

    /**
     * 查询 SLA 策略详情。
     *
     * @param id SLA 策略 ID
     * @return SLA 策略 DTO
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto getSlaPolicy(Long id) throws ScrmException {
        return slaService.getSlaPolicy(id);
    }

    /**
     * 按策略编码查询 SLA 策略。
     *
     * @param code 策略编码
     * @return SLA 策略 DTO
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto getSlaPolicyByCode(String code) throws ScrmException {
        return slaService.getSlaPolicyByCode(code);
    }

    /**
     * 查询 SLA 策略列表。
     *
     * @return SLA 策略列表
     */
    public List<ScrmWorkOrderSlaDto> listSlaPolicies() {
        return slaService.listSlaPolicies();
    }

    /**
     * 启用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto enableSlaPolicy(Long id) throws ScrmException {
        return slaService.enableSlaPolicy(id);
    }

    /**
     * 禁用 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto disableSlaPolicy(Long id) throws ScrmException {
        return slaService.disableSlaPolicy(id);
    }

    /**
     * 设置默认 SLA 策略。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto setDefaultSlaPolicy(Long id) throws ScrmException {
        return slaService.setDefaultSlaPolicy(id);
    }

    /**
     * 应用 SLA 策略到工单。
     *
     * @param orderId  工单 ID
     * @param policyId SLA 策略 ID
     * @return 更新后的工单
     * @throws ScrmException 工单或策略不存在
     */
    public ScrmWorkOrderDto applySlaPolicy(Long orderId, Long policyId) throws ScrmException {
        return slaService.applySlaPolicy(orderId, policyId);
    }

    /**
     * 检查 SLA 违规。
     *
     * @return 标记违规的工单数
     */
    public int checkSlaBreaches() {
        return slaService.checkSlaBreaches();
    }

    /**
     * 发送 SLA 预警。
     *
     * @return 预警的工单数
     */
    public int sendSlaWarnings() {
        return slaService.sendSlaWarnings();
    }

    /**
     * 升级工单 (SLA 触发, 按级别升级)。
     *
     * @param orderId 工单 ID
     * @param level   升级级别 (1/2/3)
     * @return 更新后的工单
     * @throws ScrmException 工单不存在
     */
    public ScrmWorkOrderDto escalateOrder(Long orderId, int level) throws ScrmException {
        return slaService.escalateOrder(orderId, level);
    }

    /**
     * 查询 SLA 达标率。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return SLA 达标率统计
     */
    public Map<String, Object> getSlaCompliance(LocalDateTime startTime, LocalDateTime endTime) {
        return slaService.getSlaCompliance(startTime, endTime);
    }

    /**
     * 查询单个 SLA 策略的统计。
     *
     * @param policyId SLA 策略 ID
     * @return SLA 统计
     * @throws ScrmException 策略不存在
     */
    public Map<String, Object> getSlaStats(Long policyId) throws ScrmException {
        return slaService.getSlaStats(policyId);
    }

    /**
     * 更新 SLA 策略的统计字段。
     *
     * @param id SLA 策略 ID
     * @return 更新后的 SLA 策略
     * @throws ScrmException 策略不存在
     */
    public ScrmWorkOrderSlaDto updateSlaStats(Long id) throws ScrmException {
        return slaService.updateSlaStats(id);
    }

    /**
     * 按 SLA 状态查询工单。
     *
     * @param slaStatus SLA 状态: BREACHED / MET / PENDING
     * @param pageable  分页参数
     * @return 工单分页结果
     */
    public Page<ScrmWorkOrderDto> getOrdersBySla(String slaStatus, Pageable pageable) {
        return slaService.getOrdersBySla(slaStatus, pageable);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 工单统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getWorkOrderStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getWorkOrderStats(startTime, endTime);
    }

    /**
     * SLA 统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return SLA 统计
     */
    public Map<String, Object> getSlaStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSlaStats(startTime, endTime);
    }

    /**
     * 响应时间统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 响应时间统计
     */
    public Map<String, Object> getResponseTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getResponseTimeStats(startTime, endTime);
    }

    /**
     * 解决时间统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 解决时间统计
     */
    public Map<String, Object> getResolutionTimeStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getResolutionTimeStats(startTime, endTime);
    }

    /**
     * 满意度统计。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 满意度统计
     */
    public Map<String, Object> getSatisfactionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getSatisfactionStats(startTime, endTime);
    }

    /**
     * 处理人工作量统计。
     *
     * @param assigneeId 处理人 ID
     * @return 统计结果
     * @throws ScrmException 处理人 ID 为空
     */
    public Map<String, Object> getAssigneeWorkload(Long assigneeId) throws ScrmException {
        return statsService.getAssigneeWorkload(assigneeId);
    }

    /**
     * 工单类型分布。
     *
     * @return 类型分布
     */
    public Map<String, Object> getOrderTypeDistribution() {
        return statsService.getOrderTypeDistribution();
    }

    /**
     * 工单状态分布。
     *
     * @return 状态分布
     */
    public Map<String, Object> getOrderStatusDistribution() {
        return statsService.getOrderStatusDistribution();
    }

    /**
     * 工单趋势。
     *
     * @param months 月数
     * @return 趋势数据
     */
    public Map<String, Object> getOrderTrend(int months) {
        return statsService.getOrderTrend(months);
    }

    /**
     * Top 处理人。
     *
     * @param limit 返回数量
     * @return Top 处理人列表
     */
    public List<Map<String, Object>> getTopAssignees(int limit) {
        return statsService.getTopAssignees(limit);
    }

    /**
     * Top 产品。
     *
     * @param limit 返回数量
     * @return Top 产品列表
     */
    public List<Map<String, Object>> getTopProducts(int limit) {
        return statsService.getTopProducts(limit);
    }

    /**
     * 工单概览。
     *
     * @return 概览数据
     */
    public Map<String, Object> getWorkOrderOverview() {
        return statsService.getWorkOrderOverview();
    }

    // ============================================================
    // 计算与编号
    // ============================================================

    /**
     * 计算 SLA 截止时间。
     *
     * @param orderType 工单类型
     * @param priority  优先级
     * @param createdAt 创建时间
     * @return SLA 截止时间
     */
    public LocalDateTime calculateSlaDeadline(String orderType, String priority, LocalDateTime createdAt) {
        return slaService.calculateSlaDeadline(orderType, priority, createdAt);
    }

    /**
     * 计算响应时间。
     *
     * @param createdAt  创建时间
     * @param acceptedAt 接受时间
     * @return 响应时间分钟
     */
    public int calculateResponseTime(LocalDateTime createdAt, LocalDateTime acceptedAt) {
        return actionService.calculateResponseTime(createdAt, acceptedAt);
    }

    /**
     * 计算解决时间。
     *
     * @param acceptedAt 接受时间
     * @param resolvedAt 解决时间
     * @return 解决时间分钟
     */
    public int calculateResolutionTime(LocalDateTime acceptedAt, LocalDateTime resolvedAt) {
        return actionService.calculateResolutionTime(acceptedAt, resolvedAt);
    }

    /**
     * 生成工单编号。
     *
     * @return 工单编号
     */
    public String generateOrderNo() {
        return crudService.generateOrderNo();
    }
}