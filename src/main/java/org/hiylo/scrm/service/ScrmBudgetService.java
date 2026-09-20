/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBudgetService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

import org.hiylo.scrm.dto.ScrmBudgetAllocationDto;
import org.hiylo.scrm.dto.ScrmBudgetExpenseApproveDto;
import org.hiylo.scrm.dto.ScrmBudgetExpenseDto;
import org.hiylo.scrm.dto.ScrmBudgetPlanDto;
import org.hiylo.scrm.dto.ScrmBudgetRoiDto;
import org.hiylo.scrm.dto.ScrmBudgetTransferDto;
import org.hiylo.scrm.exception.ScrmException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销预算管理服务 (门面)。
 * <p>
 * 统一对外暴露营销预算与 ROI 追踪的全部能力, 具体实现按子域下沉到兄弟服务:
 * <ul>
 *   <li>{@link ScrmBudgetPlanService} 预算计划 (方案增删改查/激活/暂停/关闭/审批/统计/预警)</li>
 *   <li>{@link ScrmBudgetAllocationService} 预算分配 (分配增删改查/调拨/消耗/预警/利用率分析)</li>
 *   <li>{@link ScrmBudgetExpenseService} 费用管理 (支出增删改查/审批/付款/批量/统计/趋势)</li>
 *   <li>{@link ScrmBudgetRoiService} ROI 统计 (ROI 计算/趋势/对比/渠道/活动/概览)</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmBudgetService {

    /** 预算计划管理服务 */
    private final ScrmBudgetPlanService planService;

    /** 预算分配管理服务 */
    private final ScrmBudgetAllocationService allocationService;

    /** 费用管理服务 */
    private final ScrmBudgetExpenseService expenseService;

    /** ROI 统计服务 */
    private final ScrmBudgetRoiService roiService;

    /**
     * 创建预算方案。
     * <p>校验 planCode 唯一性与周期合法性后写入归属账号 ID 持久化, 缺省字段填默认值。</p>
     *
     * @param dto 方案参数
     * @return 创建后的方案
     * @throws ScrmException 参数非法 / planCode 重复
     */
    public ScrmBudgetPlanDto createPlan(ScrmBudgetPlanDto dto) throws ScrmException {
        return planService.createPlan(dto);
    }

    /**
     * 更新预算方案（字段非空才覆盖）。
     *
     * @param id  方案 ID
     * @param dto 方案参数
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 参数非法 / planCode 重复
     */
    public ScrmBudgetPlanDto updatePlan(Long id, ScrmBudgetPlanDto dto) throws ScrmException {
        return planService.updatePlan(id, dto);
    }

    /**
     * 删除预算方案。
     * <p>同时清理该方案下的分配、支出与 ROI 记录。</p>
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
    public ScrmBudgetPlanDto getPlan(Long id) throws ScrmException {
        return planService.getPlan(id);
    }

    /**
     * 按方案编码查询方案。
     *
     * @param code 方案编码
     * @return 方案 DTO
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto getPlanByCode(String code) throws ScrmException {
        return planService.getPlanByCode(code);
    }

    /**
     * 分页查询预算方案, 支持按财年/预算类型/状态/关键字过滤。
     *
     * @param fiscalYear 财年 (可空)
     * @param budgetType 预算类型 (可空)
     * @param status     状态 (可空)
     * @param keyword    关键字 (匹配方案名称/编码, 可空)
     * @param pageable   分页参数
     * @return 方案分页结果
     */
    public Page<ScrmBudgetPlanDto> listPlans(Integer fiscalYear, String budgetType, String status,
                                             String keyword, Pageable pageable) {
        return planService.listPlans(fiscalYear, budgetType, status, keyword, pageable);
    }

    /**
     * 激活方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto activatePlan(Long id) throws ScrmException {
        return planService.activatePlan(id);
    }

    /**
     * 暂停方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto pausePlan(Long id) throws ScrmException {
        return planService.pausePlan(id);
    }

    /**
     * 关闭方案。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto closePlan(Long id) throws ScrmException {
        return planService.closePlan(id);
    }

    /**
     * 审批方案 (记录审批人、审批金额与审批时间)。
     *
     * @param id         方案 ID
     * @param approverId 审批人 ID
     * @param amount     审批金额
     * @return 更新后的方案
     * @throws ScrmException 方案不存在 / 金额非法
     */
    public ScrmBudgetPlanDto approvePlan(Long id, String approverId, Double amount) throws ScrmException {
        return planService.approvePlan(id, approverId, amount);
    }

    /**
     * 更新方案统计 (已分配/已消耗/剩余/分配率/消耗率)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto updatePlanStats(Long id) throws ScrmException {
        return planService.updatePlanStats(id);
    }

    /**
     * 检查方案预警 (消耗率超过阈值则触发预警)。
     *
     * @param id 方案 ID
     * @return 更新后的方案
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetPlanDto checkAlert(Long id) throws ScrmException {
        return planService.checkAlert(id);
    }

    /**
     * 查询即将到期的方案 (状态为 ACTIVE 且周期结束日在 days 天内)。
     *
     * @param days 天数 (默认 7)
     * @return 方案列表
     */
    public List<ScrmBudgetPlanDto> getExpiringPlans(Integer days) {
        return planService.getExpiringPlans(days);
    }

    /**
     * 创建预算分配。
     *
     * @param dto 分配参数
     * @return 创建后的分配
     * @throws ScrmException 参数非法 / 方案不存在
     */
    public ScrmBudgetAllocationDto createAllocation(ScrmBudgetAllocationDto dto) throws ScrmException {
        return allocationService.createAllocation(dto);
    }

    /**
     * 更新预算分配（字段非空才覆盖）。
     *
     * @param id  分配 ID
     * @param dto 分配参数
     * @return 更新后的分配
     * @throws ScrmException 分配不存在 / 参数非法
     */
    public ScrmBudgetAllocationDto updateAllocation(Long id, ScrmBudgetAllocationDto dto) throws ScrmException {
        return allocationService.updateAllocation(id, dto);
    }

    /**
     * 删除预算分配。
     *
     * @param id 分配 ID
     * @throws ScrmException 分配不存在
     */
    public void deleteAllocation(Long id) throws ScrmException {
        allocationService.deleteAllocation(id);
    }

    /**
     * 查询分配详情。
     *
     * @param id 分配 ID
     * @return 分配 DTO
     * @throws ScrmException 分配不存在
     */
    public ScrmBudgetAllocationDto getAllocation(Long id) throws ScrmException {
        return allocationService.getAllocation(id);
    }

    /**
     * 分页查询预算分配, 支持按方案 ID/分配类型/状态过滤。
     *
     * @param planId         方案 ID (可空)
     * @param allocationType 分配类型 (可空)
     * @param status         状态 (可空)
     * @param pageable       分页参数
     * @return 分配分页结果
     */
    public Page<ScrmBudgetAllocationDto> listAllocations(Long planId, String allocationType, String status,
                                                          Pageable pageable) {
        return allocationService.listAllocations(planId, allocationType, status, pageable);
    }

    /**
     * 预算调拨 (从源分配调拨金额到目标分配)。
     * <p>校验源分配剩余金额充足, 同步增减两端分配金额与剩余, 重算消耗率与方案统计。</p>
     *
     * @param transferDto 调拨参数
     * @return 调拨结果汇总
     * @throws ScrmException 分配不存在 / 金额非法 / 源分配余额不足
     */
    public Map<String, Object> transferBudget(ScrmBudgetTransferDto transferDto) throws ScrmException {
        return allocationService.transferBudget(transferDto);
    }

    /**
     * 更新分配消耗统计 (按已审批/已付款支出汇总已消耗金额, 重算剩余与消耗率)。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    public ScrmBudgetAllocationDto updateAllocationStats(Long id) throws ScrmException {
        return allocationService.updateAllocationStats(id);
    }

    /**
     * 检查分配预警 (消耗率超过阈值则触发预警)。
     *
     * @param id 分配 ID
     * @return 更新后的分配
     * @throws ScrmException 分配不存在
     */
    public ScrmBudgetAllocationDto checkAllocationAlert(Long id) throws ScrmException {
        return allocationService.checkAllocationAlert(id);
    }

    /**
     * 查询已耗尽分配 (状态为 EXHAUSTED 或剩余金额 <= 0)。
     *
     * @return 分配列表
     */
    public List<ScrmBudgetAllocationDto> getExhaustedAllocations() {
        return allocationService.getExhaustedAllocations();
    }

    /**
     * 分配汇总 (按方案 ID 汇总分配数/已分配/已消耗/剩余/各类型分布)。
     *
     * @param planId 方案 ID
     * @return 分配汇总
     * @throws ScrmException 方案不存在
     */
    public Map<String, Object> getAllocationSummary(Long planId) throws ScrmException {
        return allocationService.getAllocationSummary(planId);
    }

    /**
     * 创建支出。
     * <p>自动生成支出编号 (EXP+年月日+序号), 关联方案与分配名称冗余便于展示。</p>
     *
     * @param dto 支出参数
     * @return 创建后的支出
     * @throws ScrmException 参数非法
     */
    public ScrmBudgetExpenseDto createExpense(ScrmBudgetExpenseDto dto) throws ScrmException {
        return expenseService.createExpense(dto);
    }

    /**
     * 更新支出（字段非空才覆盖, 仅 PENDING 状态可更新）。
     *
     * @param id  支出 ID
     * @param dto 支出参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 参数非法 / 状态不允许
     */
    public ScrmBudgetExpenseDto updateExpense(Long id, ScrmBudgetExpenseDto dto) throws ScrmException {
        return expenseService.updateExpense(id, dto);
    }

    /**
     * 删除支出。
     *
     * @param id 支出 ID
     * @throws ScrmException 支出不存在
     */
    public void deleteExpense(Long id) throws ScrmException {
        expenseService.deleteExpense(id);
    }

    /**
     * 查询支出详情。
     *
     * @param id 支出 ID
     * @return 支出 DTO
     * @throws ScrmException 支出不存在
     */
    public ScrmBudgetExpenseDto getExpense(Long id) throws ScrmException {
        return expenseService.getExpense(id);
    }

    /**
     * 按支出编号查询支出。
     *
     * @param expenseNo 支出编号
     * @return 支出 DTO
     * @throws ScrmException 支出不存在
     */
    public ScrmBudgetExpenseDto getExpenseByNo(String expenseNo) throws ScrmException {
        return expenseService.getExpenseByNo(expenseNo);
    }

    /**
     * 分页查询支出, 支持按方案/分配/活动/类型/状态/时间范围过滤。
     *
     * @param planId       方案 ID (可空)
     * @param allocationId 分配 ID (可空)
     * @param campaignId   营销活动 ID (可空)
     * @param expenseType  支出类型 (可空)
     * @param status       状态 (可空)
     * @param startTime    支出日期下限 (可空)
     * @param endTime      支出日期上限 (可空)
     * @param pageable     分页参数
     * @return 支出分页结果
     */
    public Page<ScrmBudgetExpenseDto> listExpenses(Long planId, Long allocationId, Long campaignId,
                                                    String expenseType, String status,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    Pageable pageable) {
        return expenseService.listExpenses(planId, allocationId, campaignId, expenseType, status,
                startTime, endTime, pageable);
    }

    /**
     * 审批支出 (通过 → 更新预算消耗与 ROI; 驳回 → 仅更新状态)。
     *
     * @param approveDto 审批参数
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 动作非法 / 状态不允许
     */
    public ScrmBudgetExpenseDto approveExpense(ScrmBudgetExpenseApproveDto approveDto) throws ScrmException {
        return expenseService.approveExpense(approveDto);
    }

    /**
     * 驳回支出。
     *
     * @param id     支出 ID
     * @param reason 驳回原因
     * @return 更新后的支出
     * @throws ScrmException 支出不存在
     */
    public ScrmBudgetExpenseDto rejectExpense(Long id, String reason) throws ScrmException {
        return expenseService.rejectExpense(id, reason);
    }

    /**
     * 标记支出已付款。
     *
     * @param id 支出 ID
     * @return 更新后的支出
     * @throws ScrmException 支出不存在 / 状态不允许
     */
    public ScrmBudgetExpenseDto markAsPaid(Long id) throws ScrmException {
        return expenseService.markAsPaid(id);
    }

    /**
     * 分页查询待审批支出。
     *
     * @param pageable 分页参数
     * @return 支出分页结果
     */
    public Page<ScrmBudgetExpenseDto> getPendingExpenses(Pageable pageable) {
        return expenseService.getPendingExpenses(pageable);
    }

    /**
     * 按方案分页查询支出。
     *
     * @param planId   方案 ID
     * @param pageable 分页参数
     * @return 支出分页结果
     */
    public Page<ScrmBudgetExpenseDto> getExpensesByPlan(Long planId, Pageable pageable) {
        return expenseService.getExpensesByPlan(planId, pageable);
    }

    /**
     * 按营销活动分页查询支出。
     *
     * @param campaignId 营销活动 ID
     * @param pageable   分页参数
     * @return 支出分页结果
     */
    public Page<ScrmBudgetExpenseDto> getExpensesByCampaign(Long campaignId, Pageable pageable) {
        return expenseService.getExpensesByCampaign(campaignId, pageable);
    }

    /**
     * 批量审批支出。
     *
     * @param expenseIds 支出 ID 列表
     * @param action     审批动作: APPROVE/REJECT
     * @param comment    审批备注 (可空)
     * @return 已审批的记录数
     * @throws ScrmException 列表为空 / 动作非法
     */
    public int batchApprove(List<Long> expenseIds, String action, String comment) throws ScrmException {
        return expenseService.batchApprove(expenseIds, action, comment);
    }

    /**
     * 计算方案在指定周期的 ROI。
     * <p>完整实现: 汇总周期内已审批/已付款支出得到总支出与广告支出, 按公式计算
     * 收入 ROI / 利润 ROI / ROAS / CPA / CPC / CPM / CAC / LTV / LTV-CAC 比率 /
     * 转化率 / 点击率, 并按活动维度生成 breakdown 拆分。已存在同周期记录则更新。</p>
     *
     * @param planId 方案 ID
     * @param period 周期 (yyyy-MM, 可空默认当月)
     * @return ROI DTO
     * @throws ScrmException 方案不存在
     */
    public ScrmBudgetRoiDto calculateRoi(Long planId, String period) throws ScrmException {
        return roiService.calculateRoi(planId, period);
    }

    /**
     * 查询 ROI 详情。
     *
     * @param id ROI ID
     * @return ROI DTO
     * @throws ScrmException ROI 不存在
     */
    public ScrmBudgetRoiDto getRoi(Long id) throws ScrmException {
        return roiService.getRoi(id);
    }

    /**
     * 分页查询 ROI, 支持按方案/活动/周期过滤。
     *
     * @param planId     方案 ID (可空)
     * @param campaignId 营销活动 ID (可空)
     * @param period     周期 (可空)
     * @param pageable   分页参数
     * @return ROI 分页结果
     */
    public Page<ScrmBudgetRoiDto> listRoi(Long planId, Long campaignId, String period, Pageable pageable) {
        return roiService.listRoi(planId, campaignId, period, pageable);
    }

    /**
     * 活动维度 ROI (按活动汇总支出与 ROI)。
     *
     * @param campaignId 营销活动 ID
     * @param startTime  起始时间 (可空)
     * @param endTime    结束时间 (可空)
     * @return 活动 ROI 汇总
     */
    public Map<String, Object> getCampaignRoi(Long campaignId, LocalDateTime startTime, LocalDateTime endTime) {
        return roiService.getCampaignRoi(campaignId, startTime, endTime);
    }

    /**
     * 渠道维度 ROI (按方案的 breakdown 聚合各渠道支出与 ROI)。
     *
     * @param planId    方案 ID (可空)
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 渠道 ROI 列表
     */
    public List<Map<String, Object>> getChannelRoi(Long planId, LocalDateTime startTime, LocalDateTime endTime) {
        return roiService.getChannelRoi(planId, startTime, endTime);
    }

    /**
     * ROI 趋势 (最近 months 个月方案的 ROI 变化)。
     *
     * @param planId 方案 ID
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     * @throws ScrmException 方案不存在
     */
    public List<Map<String, Object>> getRoiTrend(Long planId, Integer months) throws ScrmException {
        return roiService.getRoiTrend(planId, months);
    }

    /**
     * ROI 对比 (多方案在同一周期的 ROI 对比)。
     *
     * @param planIds 方案 ID 列表
     * @param period  周期 (可空默认当月)
     * @return 对比结果列表
     */
    public List<Map<String, Object>> getRoiComparison(List<Long> planIds, String period) {
        return roiService.getRoiComparison(planIds, period);
    }

    /**
     * 更新活动 ROI (按活动的支出重新计算并落库一条活动维度 ROI)。
     *
     * @param campaignId 营销活动 ID
     * @return 活动 ROI DTO
     */
    public ScrmBudgetRoiDto updateCampaignRoi(Long campaignId) {
        return roiService.updateCampaignRoi(campaignId);
    }

    /**
     * 预算统计 (按财年汇总总额/已分配/已消耗/剩余/消耗率)。
     *
     * @param fiscalYear 财年 (可空)
     * @return 预算统计
     */
    public Map<String, Object> getBudgetStats(Integer fiscalYear) {
        return planService.getBudgetStats(fiscalYear);
    }

    /**
     * 支出统计 (按类型/部门/活动汇总)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return 支出统计
     */
    public Map<String, Object> getSpendStats(LocalDateTime startTime, LocalDateTime endTime) {
        return expenseService.getSpendStats(startTime, endTime);
    }

    /**
     * ROI 概览 (按时间范围汇总总支出/总收入/ROI/ROAS)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   结束时间 (可空)
     * @return ROI 概览
     */
    public Map<String, Object> getRoiOverview(LocalDateTime startTime, LocalDateTime endTime) {
        return roiService.getRoiOverview(startTime, endTime);
    }

    /**
     * 预算利用率 (方案已分配/已消耗/剩余/分配率/消耗率/各分配明细)。
     *
     * @param planId 方案 ID
     * @return 预算利用率
     * @throws ScrmException 方案不存在
     */
    public Map<String, Object> getBudgetUtilization(Long planId) throws ScrmException {
        return allocationService.getBudgetUtilization(planId);
    }

    /**
     * 预算趋势 (最近 months 个月每月支出总额)。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据列表
     */
    public List<Map<String, Object>> getBudgetTrend(Integer months) {
        return expenseService.getBudgetTrend(months);
    }

    /**
     * 支出最高的活动 (按活动汇总支出金额倒序)。
     *
     * @param limit 返回条数 (默认 10)
     * @return 活动支出排行列表
     */
    public List<Map<String, Object>> getTopSpendingCampaigns(Integer limit) {
        return expenseService.getTopSpendingCampaigns(limit);
    }

    /**
     * 预算差异分析 (实际 vs 计划: 总预算/已分配/已消耗/剩余 vs 差异率)。
     *
     * @param planId 方案 ID
     * @return 预算差异分析
     * @throws ScrmException 方案不存在
     */
    public Map<String, Object> getBudgetVariance(Long planId) throws ScrmException {
        return allocationService.getBudgetVariance(planId);
    }

    /**
     * 生成支出编号: EXP + yyyyMMdd + 6 位毫秒时间戳后缀。
     *
     * @return 支出编号
     */
    public String generateExpenseNo() {
        return expenseService.generateExpenseNo();
    }

}