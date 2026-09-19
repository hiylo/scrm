/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmCommissionApproveDto;
import org.hiylo.scrm.dto.ScrmCommissionCalculateDto;
import org.hiylo.scrm.dto.ScrmCommissionPayoutDto;
import org.hiylo.scrm.dto.ScrmCommissionPlanDto;
import org.hiylo.scrm.dto.ScrmCommissionRecordDto;
import org.hiylo.scrm.dto.ScrmCommissionRuleDto;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 销售佣金管理服务 (门面)。
 * <p>
 * 作为佣金模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmCommissionPlanService} (方案管理)、{@link ScrmCommissionRuleService} (规则管理)、
 * {@link ScrmCommissionCalculateService} (佣金计算) 与 {@link ScrmCommissionRecordService}
 * (记录查询 / 审批 / 发放 / 统计)。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmCommissionService {

    /** 佣金方案管理子域服务 */
    private final ScrmCommissionPlanService planService;

    /** 佣金规则管理子域服务 */
    private final ScrmCommissionRuleService ruleService;

    /** 佣金计算子域服务 */
    private final ScrmCommissionCalculateService calculateService;

    /** 佣金记录与提现统计子域服务 */
    private final ScrmCommissionRecordService recordService;

    // ============================================================
    // 方案管理
    // ============================================================

    /**
     * 创建佣金方案。
     * <p>校验 planCode 唯一性后写入归属账号 ID 持久化, 缺省字段填默认值。若设为默认, 清理旧默认。</p>
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    public ScrmCommissionPlanDto createPlan(ScrmCommissionPlanDto dto) throws ScrmException {
        return planService.createPlan(dto);
    }

    /**
     * 更新佣金方案（字段非空才覆盖）。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    public ScrmCommissionPlanDto updatePlan(Long id, ScrmCommissionPlanDto dto) throws ScrmException {
        return planService.updatePlan(id, dto);
    }

    /**
     * 删除佣金方案。
     * <p>同时清理该方案下的规则。</p>
     *
     * @param id 方案 ID
     * @throws ScrmException 方案不存在
     */
    public void deletePlan(Long id) throws ScrmException {
        planService.deletePlan(id);
    }

    /**
     * 查询方案详情。
     *
     * @param id 方案 ID
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto getPlan(Long id) throws ScrmException {
        return planService.getPlan(id);
    }

    /**
     * 按方案编码查询方案。
     *
     * @param code 方案编码
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto getPlanByCode(String code) throws ScrmException {
        return planService.getPlanByCode(code);
    }

    /**
     * 分页查询佣金方案, 支持按方案类型/状态/关键字过滤。
     *
     * @param planType 方案类型 (可空)
     * @param status   状态 (可空)
     * @param keyword  关键字 (匹配方案名称/编码, 可空)
     * @param pageable 分页参数
     * @return 方案分页结果
     */
    public Page<ScrmCommissionPlanDto> listPlans(String planType, String status, String keyword, Pageable pageable) {
        return planService.listPlans(planType, status, keyword, pageable);
    }

    /**
     * 激活方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto activatePlan(Long id) throws ScrmException {
        return planService.activatePlan(id);
    }

    /**
     * 暂停方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto pausePlan(Long id) throws ScrmException {
        return planService.pausePlan(id);
    }

    /**
     * 使方案过期。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto expirePlan(Long id) throws ScrmException {
        return planService.expirePlan(id);
    }

    /**
     * 设为默认方案 (清理旧默认)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto setDefault(Long id) throws ScrmException {
        return planService.setDefault(id);
    }

    /**
     * 更新方案统计 (累计已发放佣金 / 销售总额 / 订单数)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmCommissionPlanDto updatePlanStats(Long id) throws ScrmException {
        return planService.updatePlanStats(id);
    }

    // ============================================================
    // 规则管理
    // ============================================================

    /**
     * 创建佣金规则。
     *
     * @param dto 规则参数
     * @return 创建后的规则
     * @throws ScrmException 参数非法 / 方案不存在
     */
    public ScrmCommissionRuleDto createRule(ScrmCommissionRuleDto dto) throws ScrmException {
        return ruleService.createRule(dto);
    }

    /**
     * 更新佣金规则（字段非空才覆盖）。
     *
     * @param id  规则 ID
     * @param dto 规则参数
     * @return 更新后的规则
     * @throws ScrmException 规则不存在 / 参数非法
     */
    public ScrmCommissionRuleDto updateRule(Long id, ScrmCommissionRuleDto dto) throws ScrmException {
        return ruleService.updateRule(id, dto);
    }

    /**
     * 删除佣金规则。
     *
     * @param id 规则 ID
     * @throws ScrmException 规则不存在
     */
    public void deleteRule(Long id) throws ScrmException {
        ruleService.deleteRule(id);
    }

    /**
     * 查询规则详情。
     *
     * @param id 规则 ID
     * @return 规则 DTO
     * @throws ScrmException 规则不存在
     */
    public ScrmCommissionRuleDto getRule(Long id) throws ScrmException {
        return ruleService.getRule(id);
    }

    /**
     * 分页查询规则, 支持按方案 ID/规则类型/启用状态过滤。
     *
     * @param planId   方案 ID (可空)
     * @param ruleType 规则类型 (可空)
     * @param enabled  启用状态 (可空)
     * @param pageable 分页参数
     * @return 规则分页结果
     */
    public Page<ScrmCommissionRuleDto> listRules(Long planId, String ruleType, Boolean enabled, Pageable pageable) {
        return ruleService.listRules(planId, ruleType, enabled, pageable);
    }

    /**
     * 启用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmCommissionRuleDto enableRule(Long id) throws ScrmException {
        return ruleService.enableRule(id);
    }

    /**
     * 禁用规则。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmCommissionRuleDto disableRule(Long id) throws ScrmException {
        return ruleService.disableRule(id);
    }

    /**
     * 更新规则统计 (累计匹配次数与累计计算佣金)。
     *
     * @param id 规则 ID
     * @return 更新后的规则
     * @throws ScrmException 规则不存在
     */
    public ScrmCommissionRuleDto updateRuleStats(Long id) throws ScrmException {
        return ruleService.updateRuleStats(id);
    }

    /**
     * 获取匹配规则的规则列表。
     * <p>按 conditions JSON 过滤方案下启用的规则, 优先级倒序排列。conditions 为空时返回全部启用规则。</p>
     *
     * @param planId     方案 ID
     * @param conditions 匹配条件 Map (field → value)
     * @return 匹配规则列表
     * @throws ScrmException 方案不存在
     */
    public List<ScrmCommissionRuleEntity> getMatchingRules(Long planId, Map<String, Object> conditions)
            throws ScrmException {
        return ruleService.getMatchingRules(planId, conditions);
    }

    // ============================================================
    // 佣金计算
    // ============================================================

    /**
     * 计算佣金 (匹配方案 → 匹配规则 → 应用计算 → 创建记录)。
     *
     * @param calculateDto 计算参数
     * @return 创建的佣金记录
     * @throws ScrmException 方案不存在 / 方案非激活 / 订单金额非法
     */
    public ScrmCommissionRecordDto calculateCommission(
            ScrmCommissionCalculateDto calculateDto) throws ScrmException {
        return calculateService.calculateCommission(calculateDto);
    }

    /**
     * 批量计算佣金。
     *
     * @param calculateDtos 计算参数列表
     * @return 创建的佣金记录列表
     * @throws ScrmException 部分失败时跳过
     */
    public List<ScrmCommissionRecordDto> batchCalculate(List<ScrmCommissionCalculateDto> calculateDtos)
            throws ScrmException {
        return calculateService.batchCalculate(calculateDtos);
    }

    /**
     * 为订单计算佣金 (简化入参)。
     *
     * @param orderId        订单 ID
     * @param salesPersonId  销售人员 ID
     * @param orderAmount    订单金额
     * @return 创建的佣金记录 (使用账号默认方案)
     * @throws ScrmException 默认方案不存在
     */
    public ScrmCommissionRecordDto calculateForOrder(String orderId, String salesPersonId, Double orderAmount)
            throws ScrmException {
        return calculateService.calculateForOrder(orderId, salesPersonId, orderAmount);
    }

    /**
     * 计算周期佣金 (按方案 + 周期 + 销售人员查询已有记录汇总, 不重新计算)。
     *
     * @param planId        方案 ID
     * @param period        所属周期 (yyyy-MM)
     * @param salesPersonId 销售人员 ID (可空, 为空表示该周期所有销售人员)
     * @return 周期佣金汇总
     * @throws ScrmException 方案不存在
     */
    public Map<String, Object> calculateForPeriod(Long planId, String period, String salesPersonId)
            throws ScrmException {
        return calculateService.calculateForPeriod(planId, period, salesPersonId);
    }

    /**
     * 固定比例计算。
     * <p>commission = basis * rate; cap > 0 时取 min(commission, cap)。</p>
     *
     * @param basis 计算基础
     * @param rate  佣金比例 (0-1)
     * @param cap   每单最大佣金 (0 表示无限)
     * @return 佣金金额
     */
    public double applyFlatRate(double basis, double rate, double cap) {
        return calculateService.applyFlatRate(basis, rate, cap);
    }

    /**
     * 阶梯比例计算。
     * <p>解析 tierConfig JSON [{minValue,maxValue,rate,bonusAmount}], 找到 basis 所在区间,
     * commission = basis * rate + bonusAmount。未匹配则返回 0。</p>
     *
     * @param basis     计算基础
     * @param tierConfig 阶梯配置 JSON
     * @return double[] {佣金金额, 适用比例}
     */
    public double[] applyTieredRate(double basis, String tierConfig) {
        return calculateService.applyTieredRate(basis, tierConfig);
    }

    /**
     * 奖金计算。
     * <p>满足条件时返回固定奖金金额。</p>
     *
     * @param basis       计算基础 (未使用, 保留接口)
     * @param bonusAmount 奖金金额
     * @param conditions  条件 JSON (可空, 当前仅返回固定奖金)
     * @return 奖金金额
     */
    public double applyBonus(double basis, double bonusAmount, String conditions) {
        return calculateService.applyBonus(basis, bonusAmount, conditions);
    }

    /**
     * 倍数计算。
     * <p>将当前佣金乘以倍数。</p>
     *
     * @param commission 当前佣金
     * @param multiplier 倍数
     * @return 调整后佣金
     */
    public double applyMultiplier(double commission, double multiplier) {
        return calculateService.applyMultiplier(commission, multiplier);
    }

    /**
     * 重新计算佣金记录 (基于已有记录的订单信息重新匹配规则)。
     *
     * @param recordId 记录 ID
     * @return 重新计算后的记录
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto recalculate(Long recordId) throws ScrmException {
        return calculateService.recalculate(recordId);
    }

    // ============================================================
    // 记录查询
    // ============================================================

    /**
     * 查询佣金记录详情。
     *
     * @param id 记录 ID
     * @return 记录 DTO
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto getRecord(Long id) throws ScrmException {
        return recordService.getRecord(id);
    }

    /**
     * 按佣金编号查询记录。
     *
     * @param recordNo 佣金编号
     * @return 记录 DTO
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto getRecordByNo(String recordNo) throws ScrmException {
        return recordService.getRecordByNo(recordNo);
    }

    /**
     * 分页查询佣金记录, 支持按方案/销售人员/状态/周期/时间范围过滤。
     *
     * @param planId        方案 ID (可空)
     * @param salesPersonId 销售人员 ID (可空)
     * @param status        状态 (可空)
     * @param period        所属周期 (可空)
     * @param startTime     计算时间下限 (可空)
     * @param endTime       计算时间上限 (可空)
     * @param pageable      分页参数
     * @return 记录分页结果
     */
    public Page<ScrmCommissionRecordDto> listRecords(Long planId, String salesPersonId, String status,
                                                      String period, LocalDateTime startTime, LocalDateTime endTime,
                                                      Pageable pageable) {
        return recordService.listRecords(planId, salesPersonId, status, period, startTime, endTime, pageable);
    }

    /**
     * 按销售人员查询记录。
     *
     * @param salesPersonId 销售人员 ID
     * @param period        所属周期 (可空)
     * @param pageable      分页参数
     * @return 记录分页结果
     */
    public Page<ScrmCommissionRecordDto> getRecordsBySalesPerson(String salesPersonId, String period,
                                                                   Pageable pageable) {
        return recordService.getRecordsBySalesPerson(salesPersonId, period, pageable);
    }

    /**
     * 按订单查询记录。
     *
     * @param orderId 订单 ID
     * @return 记录列表
     */
    public List<ScrmCommissionRecordDto> getRecordsByOrder(String orderId) {
        return recordService.getRecordsByOrder(orderId);
    }

    // ============================================================
    // 审批管理
    // ============================================================

    /**
     * 提交审批 (将记录状态从 CALCULATED 改为 PENDING_APPROVAL)。
     *
     * @param recordIds 记录 ID 列表
     * @return 已提交的记录数
     * @throws ScrmException 记录不存在
     */
    public int submitForApproval(List<Long> recordIds) throws ScrmException {
        return recordService.submitForApproval(recordIds);
    }

    /**
     * 审批 (通过/驳回)。
     *
     * @param approveDto 审批参数
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在
     */
    public int approve(ScrmCommissionApproveDto approveDto) throws ScrmException {
        return recordService.approve(approveDto);
    }

    /**
     * 批量审批。
     *
     * @param recordIds 记录 ID 列表
     * @param action    审批动作: APPROVE/REJECT
     * @param note      审批备注 (可空)
     * @return 已审批的记录数
     * @throws ScrmException 记录不存在 / 动作非法
     */
    public int batchApprove(List<Long> recordIds, String action, String note) throws ScrmException {
        return recordService.batchApprove(recordIds, action, note);
    }

    /**
     * 驳回单条记录。
     *
     * @param recordId 记录 ID
     * @param reason   驳回原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto reject(Long recordId, String reason) throws ScrmException {
        return recordService.reject(recordId, reason);
    }

    /**
     * 调整佣金金额。
     *
     * @param recordId  记录 ID
     * @param newAmount 新佣金金额
     * @param reason    调整原因
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 金额非法
     */
    public ScrmCommissionRecordDto adjustCommission(Long recordId, Double newAmount, String reason)
            throws ScrmException {
        return recordService.adjustCommission(recordId, newAmount, reason);
    }

    // ============================================================
    // 发放管理
    // ============================================================

    /**
     * 发放佣金 (查询已审批 → 计算税额 → 创建发放记录 → 更新状态)。
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> payout(ScrmCommissionPayoutDto payoutDto) throws ScrmException {
        return recordService.payout(payoutDto);
    }

    /**
     * 批量发放 (等价于 payout, 支持多个销售人员)。
     *
     * @param payoutDto 发放参数
     * @return 发放汇总
     * @throws ScrmException 参数非法
     */
    public Map<String, Object> batchPayout(ScrmCommissionPayoutDto payoutDto) throws ScrmException {
        return recordService.batchPayout(payoutDto);
    }

    /**
     * 发放汇总 (按周期统计已发放记录)。
     *
     * @param period 所属周期
     * @return 发放汇总
     */
    public Map<String, Object> getPayoutSummary(String period) {
        return recordService.getPayoutSummary(period);
    }

    /**
     * 退款追回 (将已发放记录置为 CLAWBACK, 记录追回金额与原因)。
     *
     * @param recordId 记录 ID
     * @param reason   追回原因
     * @param amount   追回金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto processClawback(Long recordId, String reason, Double amount)
            throws ScrmException {
        return recordService.processClawback(recordId, reason, amount);
    }

    /**
     * 标记已发放。
     *
     * @param recordId   记录 ID
     * @param paidAmount 实发金额
     * @return 更新后的记录
     * @throws ScrmException 记录不存在
     */
    public ScrmCommissionRecordDto markAsPaid(Long recordId, Double paidAmount) throws ScrmException {
        return recordService.markAsPaid(recordId, paidAmount);
    }

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 佣金统计 (总佣金/已发放/待审批/各方案/各团队)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 统计结果
     */
    public Map<String, Object> getCommissionStats(LocalDateTime startTime, LocalDateTime endTime) {
        return recordService.getCommissionStats(startTime, endTime);
    }

    /**
     * 销售人员佣金排行。
     *
     * @param period 所属周期 (可空)
     * @param limit  返回条数 (默认 10)
     * @return 排行列表
     */
    public List<Map<String, Object>> getSalesPersonRanking(String period, Integer limit) {
        return recordService.getSalesPersonRanking(period, limit);
    }

    /**
     * 团队统计。
     *
     * @param period 所属周期 (可空)
     * @return 团队统计列表
     */
    public List<Map<String, Object>> getTeamStats(String period) {
        return recordService.getTeamStats(period);
    }

    /**
     * 方案效果统计。
     *
     * @param planId    方案 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 方案效果
     * @throws ScrmException 方案不存在
     */
    public Map<String, Object> getPlanPerformance(Long planId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return recordService.getPlanPerformance(planId, startTime, endTime);
    }

    /**
     * 佣金趋势 (最近 days 天每日佣金总额)。
     *
     * @param days 天数 (默认 7)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getCommissionTrend(Integer days) {
        return recordService.getCommissionTrend(days);
    }

    /**
     * 发放报告 (按周期 + 销售人员汇总发放金额)。
     *
     * @param period 所属周期
     * @return 发放报告
     */
    public Map<String, Object> getPayoutReport(String period) {
        return recordService.getPayoutReport(period);
    }

    // ============================================================
    // 编号生成
    // ============================================================

    /**
     * 生成佣金编号: COMM + yyyyMM + 6 位毫秒时间戳后缀。
     *
     * @return 佣金编号
     */
    public String generateRecordNo() {
        return calculateService.generateRecordNo();
    }
}