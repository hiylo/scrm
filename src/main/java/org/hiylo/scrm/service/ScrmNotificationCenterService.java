/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNotificationCenterService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmBatchSendDto;
import org.hiylo.scrm.dto.ScrmNotificationBatchDto;
import org.hiylo.scrm.dto.ScrmNotificationDto;
import org.hiylo.scrm.dto.ScrmNotificationPreferenceDto;
import org.hiylo.scrm.dto.ScrmNotificationSendDto;
import org.hiylo.scrm.dto.ScrmNotificationTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 通知中心服务。
 * <p>
 * 承载通知中心的核心能力: 通知模板管理 (增删改查 / 启用禁用 / 渲染), 通知发送 (单条 / 批量 /
 * 定时 / 取消 / 重试), 消息已读管理 (单条 / 批量 / 全部 / 未读数), 批次管理 (进度 / 取消 /
 * 执行), 通知偏好管理 (查询 / 更新 / 检查), 以及通知统计 (总览 / 渠道 / 分类 / 送达率)。
 * </p>
 * <p>
 * 本类为门面, 具体实现按子域拆分到兄弟服务:
 * {@link ScrmNotificationCenterTemplateService} (模板管理), {@link ScrmNotificationCenterSendService}
 * (发送与调度 / 批次管理), {@link ScrmNotificationCenterQueryService} (查询与已读管理),
 * {@link ScrmNotificationCenterPreferenceService} (偏好与统计)。所有 public 方法签名保持不变,
 * 仅做一行委托。
 * </p>
 * <p>
 * 发送执行 (sendNotification / sendBatch / retryNotification 链路): IN_APP 持久化即送达,
 * WEBHOOK 真实 HTTP POST 分发 (含 SSRF 校验), PUSH 经 {@link PushNotificationService} 下发到
 * 用户设备 (个推凭证未就绪时按 {@code GETUI_ENABLED} 开关自动落到 {@link NoOpPushNotificationService},
 * 与项目内既有推送通道用法一致); EMAIL / SMS 仓库内无网关实现, 显式置 FAILED 并在
 * {@code errorMessage} 标明通道未接入, 不伪造发送成功。偏好检查综合启用状态 / 最低优先级 /
 * 免打扰时段判断, 实现数据隔离, 越权访问按不存在处理。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmNotificationCenterService {

    /** 通知模板管理服务 */
    private final ScrmNotificationCenterTemplateService templateService;
    /** 通知发送与调度服务 (含批次管理) */
    private final ScrmNotificationCenterSendService sendService;
    /** 通知查询与已读管理服务 */
    private final ScrmNotificationCenterQueryService queryService;
    /** 通知偏好与统计服务 */
    private final ScrmNotificationCenterPreferenceService preferenceService;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建通知模板。
     * <p>参数校验: templateName / templateCode / category / channel / title / content 必填,
     * templateCode 唯一; 默认 isHtml=false, enabled=true, usageCount=0。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    public ScrmNotificationTemplateDto createTemplate(ScrmNotificationTemplateDto dto) throws ScrmException {
        return templateService.createTemplate(dto);
    }

    /**
     * 更新模板 (部分更新, 仅非空字段生效)。
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 模板编码重复
     */
    public ScrmNotificationTemplateDto updateTemplate(
            Long id, ScrmNotificationTemplateDto dto) throws ScrmException {
        return templateService.updateTemplate(id, dto);
    }

    /**
     * 删除模板。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void deleteTemplate(Long id) throws ScrmException {
        templateService.deleteTemplate(id);
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    public ScrmNotificationTemplateDto getTemplate(Long id) throws ScrmException {
        return templateService.getTemplate(id);
    }

    /**
     * 按模板编码查询模板。
     *
     * @param code 模板编码
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    public ScrmNotificationTemplateDto getTemplateByCode(String code) throws ScrmException {
        return templateService.getTemplateByCode(code);
    }

    /**
     * 分页查询模板, 支持按渠道 / 分类 / 启用状态 / 关键词过滤。
     *
     * @param channel  渠道过滤 (可空)
     * @param category 分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配模板名称 / 模板编码 (可空)
     * @param pageable 分页参数
     * @return 模板分页结果 (按 createTime DESC)
     */
    public Page<ScrmNotificationTemplateDto> listTemplates(String channel, String category, Boolean enabled,
                                                            String keyword, Pageable pageable) {
        return templateService.listTemplates(channel, category, enabled, keyword, pageable);
    }

    /**
     * 启用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmNotificationTemplateDto enableTemplate(Long id) throws ScrmException {
        return templateService.enableTemplate(id);
    }

    /**
     * 禁用模板。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmNotificationTemplateDto disableTemplate(Long id) throws ScrmException {
        return templateService.disableTemplate(id);
    }

    /**
     * 渲染模板 (变量替换)。
     * <p>按模板编码加载模板并替换标题与内容中的 {varName} 占位符, 返回渲染结果。</p>
     *
     * @param code      模板编码
     * @param variables 变量映射 (可空)
     * @return 渲染结果 (title / content)
     * @throws ScrmException 模板不存在
     */
    public Map<String, String> renderTemplate(String code, Map<String, String> variables) throws ScrmException {
        return templateService.renderTemplate(code, variables);
    }

    // ============================================================
    // 通知发送
    // ============================================================

    /**
     * 发送单条通知 (模板渲染 → 偏好检查 → 发送 → 记录)。
     * <p>按 templateCode 加载模板并渲染, 对每个接收者检查偏好后构建通知记录并按渠道投递
     * (详见发送服务 {@link ScrmNotificationCenterSendService})。计划发送时间 scheduledAt 非空时
     * 仅创建 PENDING 记录, 不立即发送; 否则立即发送并刷新状态。发送成功后模板使用次数 +1。</p>
     *
     * @param dto 发送参数 (templateCode + recipients + variables)
     * @return 创建的通知列表 (EMAIL / SMS 等未接入渠道的记录状态为 FAILED, 不会伪造成功)
     * @throws ScrmException 模板不存在 / 模板已禁用 / 参数非法
     */
    public List<ScrmNotificationDto> sendNotification(ScrmNotificationSendDto dto) throws ScrmException {
        return sendService.sendNotification(dto);
    }

    /**
     * 批量发送通知 (创建批次并逐条发送)。
     * <p>创建一个批次, 对每个接收者渲染模板并按渠道投递, 增量更新批次计数。
     * 发送完成后批次状态置为 COMPLETED; 全部失败置 FAILED。</p>
     *
     * @param batchDto 批量发送参数 (batchName + templateCode + channel + recipientIds)
     * @return 更新后的批次
     * @throws ScrmException 模板不存在 / 参数非法
     */
    public ScrmNotificationBatchDto sendBatch(ScrmBatchSendDto batchDto) throws ScrmException {
        return sendService.sendBatch(batchDto);
    }

    /**
     * 定时发送 (设置计划发送时间, 仅对 PENDING 状态生效)。
     *
     * @param id          通知 ID
     * @param scheduledAt 计划发送时间
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    public ScrmNotificationDto scheduleNotification(Long id, LocalDateTime scheduledAt) throws ScrmException {
        return sendService.scheduleNotification(id, scheduledAt);
    }

    /**
     * 取消发送 (仅对 PENDING 状态生效, 状态置为 CANCELLED)。
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在 / 状态非法
     */
    public ScrmNotificationDto cancelNotification(Long id) throws ScrmException {
        return sendService.cancelNotification(id);
    }

    /**
     * 重试失败的通知 (仅对 FAILED 状态生效, 重试次数 +1 并按渠道重新投递)。
     *
     * @param id 通知 ID
     * @return 更新后的通知 (未接入渠道重试后仍为 FAILED)
     * @throws ScrmException 通知不存在 / 状态非法 / 重试次数已达上限
     */
    public ScrmNotificationDto retryNotification(Long id) throws ScrmException {
        return sendService.retryNotification(id);
    }

    /**
     * 查询通知详情。
     *
     * @param id 通知 ID
     * @return 通知 DTO
     * @throws ScrmException 通知不存在
     */
    public ScrmNotificationDto getNotification(Long id) throws ScrmException {
        return queryService.getNotification(id);
    }

    /**
     * 分页查询通知, 支持按渠道 / 分类 / 状态 / 接收者 / 时间区间过滤。
     *
     * @param channel     渠道过滤 (可空)
     * @param category    分类过滤 (可空)
     * @param status      状态过滤 (可空)
     * @param recipientId 接收者 ID 过滤 (可空)
     * @param startTime   发送时间起点 (含, 可空)
     * @param endTime     发送时间终点 (不含, 可空)
     * @param pageable    分页参数
     * @return 通知分页结果 (按 createTime DESC)
     */
    public Page<ScrmNotificationDto> listNotifications(String channel, String category, String status,
                                                       String recipientId, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        return queryService.listNotifications(channel, category, status, recipientId, startTime, endTime, pageable);
    }

    /**
     * 标记通知为已读 (状态置为 READ, 记录已读时间)。
     *
     * @param id 通知 ID
     * @return 更新后的通知
     * @throws ScrmException 通知不存在
     */
    public ScrmNotificationDto markAsRead(Long id) throws ScrmException {
        return queryService.markAsRead(id);
    }

    /**
     * 批量标记通知为已读。
     *
     * @param ids 通知 ID 列表
     * @return 已标记的通知列表
     */
    public List<ScrmNotificationDto> batchMarkAsRead(List<Long> ids) {
        return queryService.batchMarkAsRead(ids);
    }

    /**
     * 将用户的全部未读站内信标记为已读。
     *
     * @param userId 用户 ID
     * @return 影响行数
     */
    public int markAllAsRead(String userId) {
        return queryService.markAllAsRead(userId);
    }

    /**
     * 获取用户未读站内信数。
     *
     * @param userId 用户 ID
     * @return 未读数
     */
    public long getUnreadCount(String userId) {
        return queryService.getUnreadCount(userId);
    }

    // ============================================================
    // 批次管理
    // ============================================================

    /**
     * 查询批次详情。
     *
     * @param id 批次 ID
     * @return 批次 DTO
     * @throws ScrmException 批次不存在
     */
    public ScrmNotificationBatchDto getBatch(Long id) throws ScrmException {
        return sendService.getBatch(id);
    }

    /**
     * 分页查询批次, 支持按状态 / 时间区间过滤。
     *
     * @param status    状态过滤 (可空)
     * @param startTime 创建时间起点 (含, 可空)
     * @param endTime   创建时间终点 (不含, 可空)
     * @param pageable  分页参数
     * @return 批次分页结果 (按 createTime DESC)
     */
    public Page<ScrmNotificationBatchDto> listBatches(String status, LocalDateTime startTime,
                                                       LocalDateTime endTime, Pageable pageable) {
        return sendService.listBatches(status, startTime, endTime, pageable);
    }

    /**
     * 取消批次 (仅对 PENDING / SENDING 状态生效, 状态置为 CANCELLED, 记录结束时间)。
     *
     * @param id 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 状态非法
     */
    public ScrmNotificationBatchDto cancelBatch(Long id) throws ScrmException {
        return sendService.cancelBatch(id);
    }

    /**
     * 执行批次发送 (模拟实现)。
     * <p>对于 PENDING 状态的批次, 标记为 SENDING → COMPLETED 并记录起止时间 (模拟调度执行);
     * 对于已结束的批次, 仅刷新进度。实际接收者发送在 sendBatch 创建时已完成。</p>
     *
     * @param batchId 批次 ID
     * @return 更新后的批次
     * @throws ScrmException 批次不存在 / 状态非法
     */
    public ScrmNotificationBatchDto processBatch(Long batchId) throws ScrmException {
        return sendService.processBatch(batchId);
    }

    /**
     * 获取批次进度。
     *
     * @param batchId 批次 ID
     * @return 进度信息 (总数 / 已发送 / 成功 / 失败 / 已读 / 进度百分比 / 状态)
     * @throws ScrmException 批次不存在
     */
    public Map<String, Object> getBatchProgress(Long batchId) throws ScrmException {
        return sendService.getBatchProgress(batchId);
    }

    // ============================================================
    // 通知偏好
    // ============================================================

    /**
     * 查询用户通知偏好列表。
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    public List<ScrmNotificationPreferenceDto> getPreference(String userId) {
        return preferenceService.getPreference(userId);
    }

    /**
     * 更新 (或创建) 用户通知偏好。
     * <p>按 + 用户 + 渠道 + 分类定位偏好, 不存在则新建。仅更新非空字段。</p>
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param dto      偏好参数
     * @return 更新后的偏好
     * @throws ScrmException 参数非法
     */
    public ScrmNotificationPreferenceDto updatePreference(String userId, String channel, String category,
                                                           ScrmNotificationPreferenceDto dto)
                                                               throws ScrmException {
        return preferenceService.updatePreference(userId, channel, category, dto);
    }

    /**
     * 查询用户通知偏好列表 (与 getPreference 等价, 显式列表入口)。
     *
     * @param userId 用户 ID
     * @return 偏好列表
     */
    public List<ScrmNotificationPreferenceDto> listPreferences(String userId) {
        return preferenceService.listPreferences(userId);
    }

    /**
     * 检查偏好 (是否允许发送)。
     * <p>无偏好记录时默认允许; 偏好禁用 / 优先级低于阈值 / 当前时间处于免打扰时段则拒绝。</p>
     *
     * @param userId   用户 ID
     * @param channel  通知渠道
     * @param category 分类
     * @param priority 通知优先级
     * @return true 允许发送, false 拒绝
     */
    public boolean checkPreference(String userId, String channel, String category, int priority) {
        return preferenceService.checkPreference(userId, channel, category, priority);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 获取通知总览统计 (发送数 / 成功率 / 已读率 / 各渠道分布)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 总览统计
     */
    public Map<String, Object> getNotificationStats(LocalDateTime startTime, LocalDateTime endTime) {
        return preferenceService.getNotificationStats(startTime, endTime);
    }

    /**
     * 获取渠道统计 (每渠道发送数)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 渠道统计 (channel → count)
     */
    public Map<String, Long> getChannelStats(LocalDateTime startTime, LocalDateTime endTime) {
        return preferenceService.getChannelStats(startTime, endTime);
    }

    /**
     * 获取分类统计 (每分类发送数)。
     *
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 分类统计 (category → count)
     */
    public Map<String, Long> getCategoryStats(LocalDateTime startTime, LocalDateTime endTime) {
        return preferenceService.getCategoryStats(startTime, endTime);
    }

    /**
     * 获取送达率 (已送达 / 已发送)。
     * <p>已送达 = DELIVERED + READ, 已发送 = SENT + DELIVERED + READ。</p>
     *
     * @param channel   渠道 (可空, 为空则统计全部渠道)
     * @param startTime 发送时间起点 (含, 可空)
     * @param endTime   发送时间终点 (不含, 可空)
     * @return 送达率 (百分比, 保留两位小数)
     */
    public double getDeliveryRate(String channel, LocalDateTime startTime, LocalDateTime endTime) {
        return preferenceService.getDeliveryRate(channel, startTime, endTime);
    }
}