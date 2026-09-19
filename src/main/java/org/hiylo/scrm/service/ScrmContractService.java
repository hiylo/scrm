/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmContractApproveDto;
import org.hiylo.scrm.dto.ScrmContractCreateDto;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractReminderDto;
import org.hiylo.scrm.dto.ScrmContractRenewDto;
import org.hiylo.scrm.dto.ScrmContractSignDto;
import org.hiylo.scrm.dto.ScrmContractTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户合同管理服务 (门面)。
 * <p>
 * 作为合同模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmContractTemplateService} (合同模板)、{@link ScrmContractManageService} (合同管理)、
 * {@link ScrmContractReminderService} (提醒管理) 与 {@link ScrmContractStatService} (合同统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmContractService {

    /** 合同模板管理子域服务 */
    private final ScrmContractTemplateService templateService;

    /** 合同管理子域服务 */
    private final ScrmContractManageService manageService;

    /** 合同提醒管理子域服务 */
    private final ScrmContractReminderService reminderService;

    /** 合同统计子域服务 */
    private final ScrmContractStatService statsService;

    // ============================================================
    // 模板管理
    // ============================================================

    /**
     * 创建合同模板。
     * <p>校验参数合法性后写入归属账号 ID 持久化, 状态缺省 ACTIVE, 版本与使用次数缺省 0。</p>
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    public ScrmContractTemplateDto createTemplate(ScrmContractTemplateDto dto) throws ScrmException {
        return templateService.createTemplate(dto);
    }

    /**
     * 更新合同模板 (字段非空才覆盖)。
     * <p>更新模板内容时自动递增业务版本号。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 模板编码重复
     */
    public ScrmContractTemplateDto updateTemplate(Long id, ScrmContractTemplateDto dto) throws ScrmException {
        return templateService.updateTemplate(id, dto);
    }

    /**
     * 删除合同模板。
     * <p>已被合同引用的模板不允许删除 (避免合同悬空引用), 请先停用模板。</p>
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在 / 已被合同引用不允许删除
     */
    public void deleteTemplate(Long id) throws ScrmException {
        templateService.deleteTemplate(id);
    }

    /**
     * 查询合同模板详情。
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    public ScrmContractTemplateDto getTemplate(Long id) throws ScrmException {
        return templateService.getTemplate(id);
    }

    /**
     * 按模板编码查询合同模板。
     *
     * @param code 模板编码
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    public ScrmContractTemplateDto getTemplateByCode(String code) throws ScrmException {
        return templateService.getTemplateByCode(code);
    }

    /**
     * 分页查询合同模板, 支持按合同类型、状态与关键词过滤。
     *
     * @param contractType 合同类型过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param keyword      关键词过滤, 匹配模板名称或编码 (可空)
     * @param pageable     分页参数
     * @return 模板分页结果
     */
    public Page<ScrmContractTemplateDto> listTemplates(String contractType, String status, String keyword,
                                                        Pageable pageable) {
        return templateService.listTemplates(contractType, status, keyword, pageable);
    }

    /**
     * 启用合同模板 (状态置 ACTIVE)。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmContractTemplateDto activateTemplate(Long id) throws ScrmException {
        return templateService.activateTemplate(id);
    }

    /**
     * 停用合同模板 (状态置 INACTIVE), 已创建合同不受影响。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    public ScrmContractTemplateDto deactivateTemplate(Long id) throws ScrmException {
        return templateService.deactivateTemplate(id);
    }

    /**
     * 渲染模板: 将模板内容中的 {{variableName}} 占位符替换为 variables 中的值。
     * <p>variables 为 JSON 字符串 (键值对), 未提供的变量保留占位符原样。
     * 渲染后递增模板使用次数并刷新最后使用时间。</p>
     *
     * @param templateId 模板 ID
     * @param variables  变量值 JSON 字符串 (可空)
     * @return 渲染后的合同内容
     * @throws ScrmException 模板不存在
     */
    public String renderTemplate(Long templateId, String variables) throws ScrmException {
        return templateService.renderTemplate(templateId, variables);
    }

    /**
     * 复制合同模板 (基于已有模板创建新模板, 新模板编码由参数指定)。
     * <p>复制后的模板状态为 DRAFT, 使用次数为 0, 业务版本号重置为 1。</p>
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 新模板
     * @throws ScrmException 模板不存在 / 新编码重复
     */
    public ScrmContractTemplateDto copyTemplate(Long id, String newCode) throws ScrmException {
        return templateService.copyTemplate(id, newCode);
    }

    /**
     * 递增模板使用次数并刷新最后使用时间。
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    public void incrementUsage(Long id) throws ScrmException {
        templateService.incrementUsage(id);
    }

    /**
     * 获取模板变量定义 (解析 variables JSON 并返回变量列表)。
     *
     * @param id 模板 ID
     * @return 变量定义列表
     * @throws ScrmException 模板不存在
     */
    public List<Map<String, Object>> getTemplateVariables(Long id) throws ScrmException {
        return templateService.getTemplateVariables(id);
    }

    // ============================================================
    // 合同管理
    // ============================================================

    /**
     * 创建合同 (基于模板渲染)。
     * <p>选择模板 → 渲染模板内容 (变量替换) → 创建合同记录 → 递增模板使用次数 → 生成提醒。</p>
     *
     * @param createDto 创建请求 (模板 ID + 客户 ID + 变量 + 自定义字段)
     * @return 创建后的合同
     * @throws ScrmException 模板不存在 / 模板未启用 / 客户不存在
     */
    public ScrmContractDto createContract(ScrmContractCreateDto createDto) throws ScrmException {
        return manageService.createContract(createDto);
    }

    /**
     * 更新合同 (字段非空才覆盖)。
     * <p>仅 DRAFT 状态合同允许更新核心字段 (金额/日期/内容)。</p>
     *
     * @param id  合同 ID
     * @param dto 合同参数
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 参数非法 / 状态不允许更新
     */
    public ScrmContractDto updateContract(Long id, ScrmContractDto dto) throws ScrmException {
        return manageService.updateContract(id, dto);
    }

    /**
     * 删除合同。
     * <p>仅 DRAFT / CANCELLED 状态合同允许删除, 已生效合同请先终止或归档。</p>
     *
     * @param id 合同 ID
     * @throws ScrmException 合同不存在 / 状态不允许删除
     */
    public void deleteContract(Long id) throws ScrmException {
        manageService.deleteContract(id);
    }

    /**
     * 查询合同详情。
     *
     * @param id 合同 ID
     * @return 合同 DTO
     * @throws ScrmException 合同不存在
     */
    public ScrmContractDto getContract(Long id) throws ScrmException {
        return manageService.getContract(id);
    }

    /**
     * 按合同编号查询合同。
     *
     * @param contractNo 合同编号
     * @return 合同 DTO
     * @throws ScrmException 合同不存在
     */
    public ScrmContractDto getContractByNo(String contractNo) throws ScrmException {
        return manageService.getContractByNo(contractNo);
    }

    /**
     * 分页查询合同, 支持按合同类型、客户、状态、销售人员、日期范围与关键词过滤。
     *
     * @param contractType 合同类型过滤 (可空)
     * @param customerId   客户 ID 过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param salesPersonId 销售人员 ID 过滤 (可空)
     * @param startDate    开始日期下限 (按合同开始日期, 可空)
     * @param endDate      开始日期上限 (按合同开始日期, 可空)
     * @param keyword      关键词过滤, 匹配合同名称或编号 (可空)
     * @param pageable     分页参数
     * @return 合同分页结果
     */
    public Page<ScrmContractDto> listContracts(String contractType, Long customerId, String status,
                                                String salesPersonId, LocalDate startDate, LocalDate endDate,
                                                String keyword, Pageable pageable) {
        return manageService.listContracts(contractType, customerId, status, salesPersonId, startDate, endDate,
                keyword, pageable);
    }

    /**
     * 分页查询客户合同列表。
     *
     * @param customerId 客户 ID
     * @param pageable   分页参数
     * @return 合同分页结果
     */
    public Page<ScrmContractDto> getContractsByCustomer(Long customerId, Pageable pageable) {
        return manageService.getContractsByCustomer(customerId, pageable);
    }

    /**
     * 分页查询销售人员合同列表。
     *
     * @param salesPersonId 销售人员 ID
     * @param pageable      分页参数
     * @return 合同分页结果
     */
    public Page<ScrmContractDto> getContractsBySalesPerson(String salesPersonId, Pageable pageable) {
        return manageService.getContractsBySalesPerson(salesPersonId, pageable);
    }

    /**
     * 提交审批: 合同状态由 DRAFT 流转至 PENDING_REVIEW。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto submitForApproval(Long id) throws ScrmException {
        return manageService.submitForApproval(id);
    }

    /**
     * 审批合同: 通过则流转至 PENDING_SIGNATURE, 驳回则回退至 DRAFT。
     *
     * @param approveDto 审批请求 (合同 ID + 动作 + 意见)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto approve(ScrmContractApproveDto approveDto) throws ScrmException {
        return manageService.approve(approveDto);
    }

    /**
     * 驳回合同 (状态回退至 DRAFT 并记录原因)。
     *
     * @param id     合同 ID
     * @param reason 驳回原因
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto reject(Long id, String reason) throws ScrmException {
        return manageService.reject(id, reason);
    }

    /**
     * 签署合同: 状态由 PENDING_SIGNATURE 流转至 SIGNED, 记录签署人与签署方式。
     * <p>签署日期缺省为当天, 生效日期缺省为签署当天。</p>
     *
     * @param signDto 签署请求 (合同 ID + 签署人 + 签署方式 + 签署文件 URL)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto sign(ScrmContractSignDto signDto) throws ScrmException {
        return manageService.sign(signDto);
    }

    /**
     * 激活合同: 状态由 SIGNED 流转至 ACTIVE。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto activate(Long id) throws ScrmException {
        return manageService.activate(id);
    }

    /**
     * 终止合同: 状态流转至 TERMINATED, 记录失效日期。
     *
     * @param id              合同 ID
     * @param reason          终止原因
     * @param terminationDate 终止日期 (可空, 缺省为当天)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto terminate(Long id, String reason, LocalDate terminationDate) throws ScrmException {
        return manageService.terminate(id, reason, terminationDate);
    }

    /**
     * 取消合同: 状态流转至 CANCELLED。
     *
     * @param id     合同 ID
     * @param reason 取消原因
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto cancel(Long id, String reason) throws ScrmException {
        return manageService.cancel(id, reason);
    }

    /**
     * 归档合同: 状态流转至 ARCHIVED。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    public ScrmContractDto archive(Long id) throws ScrmException {
        return manageService.archive(id);
    }

    /**
     * 续约合同: 基于原合同创建新合同, 关联原合同并生成提醒。
     * <p>新合同继承原合同的核心字段 (客户/类型/模板/内容), 覆盖结束日期/金额/自动续约配置,
     * 状态置为 DRAFT。原合同的 renewedToId 指向新合同。</p>
     *
     * @param renewDto 续约请求 (原合同 ID + 新结束日期 + 自动续约 + 新金额)
     * @return 新合同
     * @throws ScrmException 原合同不存在 / 状态非法
     */
    public ScrmContractDto renew(ScrmContractRenewDto renewDto) throws ScrmException {
        return manageService.renew(renewDto);
    }

    /**
     * 复制合同 (基于已有合同创建新合同, 新合同编号由参数指定)。
     * <p>复制后的合同状态为 DRAFT, 不继承审批/签署信息。</p>
     *
     * @param id    源合同 ID
     * @param newNo 新合同编号
     * @return 新合同
     * @throws ScrmException 源合同不存在 / 新编号重复
     */
    public ScrmContractDto duplicate(Long id, String newNo) throws ScrmException {
        return manageService.duplicate(id, newNo);
    }

    /**
     * 查询即将到期的合同 (未来 N 天内到期, 状态为 ACTIVE/SIGNED)。
     *
     * @param days 天数
     * @return 合同列表
     */
    public List<ScrmContractDto> getExpiringContracts(int days) {
        return manageService.getExpiringContracts(days);
    }

    /**
     * 查询已到期合同 (结束日期早于今天, 状态为 ACTIVE/SIGNED)。
     *
     * @return 合同列表
     */
    public List<ScrmContractDto> getExpiredContracts() {
        return manageService.getExpiredContracts();
    }

    /**
     * 为合同生成提醒 (到期/续约/付款)。
     * <p>根据合同 endDate 与 reminderDaysBefore 生成到期提醒 (EXPIRY),
     * 自动续约合同生成续约提醒 (RENEWAL), 有付款条款的合同生成付款提醒 (PAYMENT)。</p>
     *
     * @param contractId 合同 ID
     * @return 生成的提醒列表
     * @throws ScrmException 合同不存在
     */
    public List<ScrmContractReminderDto> generateRemindersForContract(Long contractId) throws ScrmException {
        return manageService.generateRemindersForContract(contractId);
    }

    /**
     * 生成合同编号 (HT + 年月日 + 4 位序号)。
     * <p>序号 = 当日已生成合同数 + 1, 超过 9999 则扩展为 5 位。</p>
     *
     * @return 合同编号
     */
    public String generateContractNo() {
        return manageService.generateContractNo();
    }

    // ============================================================
    // 提醒管理
    // ============================================================

    /**
     * 创建合同提醒。
     *
     * @param dto 提醒参数
     * @return 创建后的提醒
     * @throws ScrmException 参数非法 / 合同不存在
     */
    public ScrmContractReminderDto createReminder(ScrmContractReminderDto dto) throws ScrmException {
        return reminderService.createReminder(dto);
    }

    /**
     * 更新合同提醒 (字段非空才覆盖)。
     *
     * @param id  提醒 ID
     * @param dto 提醒参数
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 参数非法
     */
    public ScrmContractReminderDto updateReminder(Long id, ScrmContractReminderDto dto) throws ScrmException {
        return reminderService.updateReminder(id, dto);
    }

    /**
     * 删除合同提醒。
     *
     * @param id 提醒 ID
     * @throws ScrmException 提醒不存在
     */
    public void deleteReminder(Long id) throws ScrmException {
        reminderService.deleteReminder(id);
    }

    /**
     * 查询合同提醒详情。
     *
     * @param id 提醒 ID
     * @return 提醒 DTO
     * @throws ScrmException 提醒不存在
     */
    public ScrmContractReminderDto getReminder(Long id) throws ScrmException {
        return reminderService.getReminder(id);
    }

    /**
     * 分页查询合同提醒, 支持按合同 ID、提醒类型、状态与日期范围过滤。
     *
     * @param contractId   合同 ID 过滤 (可空)
     * @param reminderType 提醒类型过滤 (可空)
     * @param status       状态过滤 (可空)
     * @param startDate    起始日期 (按提醒日期, 可空)
     * @param endDate      截止日期 (按提醒日期, 可空)
     * @param pageable     分页参数
     * @return 提醒分页结果
     */
    public Page<ScrmContractReminderDto> listReminders(Long contractId, String reminderType, String status,
                                                        LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return reminderService.listReminders(contractId, reminderType, status, startDate, endDate, pageable);
    }

    /**
     * 发送提醒 (模拟): 状态置 SENT, 累加发送次数, 刷新合同最后提醒时间。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    public ScrmContractReminderDto sendReminder(Long id) throws ScrmException {
        return reminderService.sendReminder(id);
    }

    /**
     * 批量发送提醒 (定时任务): 扫描当前账号下 reminderDate 早于今天的 PENDING 提醒并发送。
     *
     * @return 发送的提醒数量
     */
    public int batchSendReminders() {
        return reminderService.batchSendReminders();
    }

    /**
     * 取消提醒 (状态置 CANCELLED)。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    public ScrmContractReminderDto cancelReminder(Long id) throws ScrmException {
        return reminderService.cancelReminder(id);
    }

    /**
     * 查询待发送提醒 (状态为 PENDING, 按提醒日期升序)。
     *
     * @return 待发送提醒列表
     */
    public List<ScrmContractReminderDto> getPendingReminders() {
        return reminderService.getPendingReminders();
    }

    /**
     * 检查并生成提醒 (定时任务): 扫描即将到期的合同并生成提醒。
     * <p>扫描当前账号下状态为 ACTIVE/SIGNED 且 endDate 在未来 N 天内的合同,
     * 为尚未生成到期提醒的合同生成提醒。</p>
     *
     * @return 生成提醒的合同数量
     */
    public int checkAndGenerateReminders() {
        return reminderService.checkAndGenerateReminders();
    }

    /**
     * 标记提醒已处理。
     *
     * @param id        提醒 ID
     * @param actionBy 处理人
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在
     */
    public ScrmContractReminderDto markActionTaken(Long id, String actionBy) throws ScrmException {
        return reminderService.markActionTaken(id, actionBy);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 合同统计: 总数 / 各类型 / 各状态 / 总金额 / 平均期限。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getContractStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getContractStats(startTime, endTime);
    }

    /**
     * 到期统计: 未来 N 个月内到期的合同数量与金额。
     *
     * @param months 月数
     * @return 统计结果
     */
    public Map<String, Object> getExpiryStats(int months) {
        return statsService.getExpiryStats(months);
    }

    /**
     * 续约统计: 续约率 / 平均续约金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getRenewalStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getRenewalStats(startTime, endTime);
    }

    /**
     * 销售人员合同统计: 合同数 / 总金额 / 各状态分布。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param salesPersonId 销售人员 ID
     * @param startTime     起始时间 (可空)
     * @param endTime       截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getSalesPersonContractStats(String salesPersonId, LocalDateTime startTime,
                                                            LocalDateTime endTime) {
        return statsService.getSalesPersonContractStats(salesPersonId, startTime, endTime);
    }

    /**
     * 金额统计: 总金额 / 平均金额 / 各类型金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getValueStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getValueStats(startTime, endTime);
    }

    /**
     * 合同趋势: 过去 N 个月每月新增合同数与金额。
     *
     * @param months 月数
     * @return 趋势数据
     */
    public Map<String, Object> getContractTrend(int months) {
        return statsService.getContractTrend(months);
    }

    /**
     * 提醒统计: 总数 / 各类型 / 各状态 / 已处理率。
     * <p>时间范围按提醒创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getReminderStats(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getReminderStats(startTime, endTime);
    }
}