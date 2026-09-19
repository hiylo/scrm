/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCommissionCalculateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCommissionCalculateDto;
import org.hiylo.scrm.dto.ScrmCommissionRecordDto;
import org.hiylo.scrm.entity.ScrmCommissionPlanEntity;
import org.hiylo.scrm.entity.ScrmCommissionRecordEntity;
import org.hiylo.scrm.entity.ScrmCommissionRuleEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCommissionPlanRepository;
import org.hiylo.scrm.repository.ScrmCommissionRecordRepository;
import org.hiylo.scrm.repository.ScrmCommissionRuleRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 佣金计算服务 (计算子域)。
 * <p>
 * 承载单笔 / 批量 / 按订单 / 按周期佣金计算, 完整实现固定比例 / 阶梯 / 奖金 / 倍数 / 扣减
 * 规则计算, 以及记录重算、佣金编号生成与佣金计算基础解析。规则匹配委托给
 * {@link ScrmCommissionRuleService}, 记录转换与金额舍入委托给 {@link ScrmCommissionRecordService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCommissionCalculateService {

    // ==================== 计算基础常量 ====================

    /** 计算基础: 订单金额 */
    private static final String BASIS_ORDER_AMOUNT = "ORDER_AMOUNT";
    /** 计算基础: 订单利润 */
    private static final String BASIS_ORDER_PROFIT = "ORDER_PROFIT";
    /** 计算基础: 订单数量 */
    private static final String BASIS_ORDER_COUNT = "ORDER_COUNT";
    /** 计算基础: 营收目标 */
    private static final String BASIS_REVENUE_TARGET = "REVENUE_TARGET";
    /** 计算基础: 售出单位 */
    private static final String BASIS_UNITS_SOLD = "UNITS_SOLD";

    /** 佣金编号前缀 */
    private static final String RECORD_NO_PREFIX = "COMM";

    /** 佣金方案数据访问层 */
    private final ScrmCommissionPlanRepository planRepository;

    /** 佣金规则数据访问层 (计算时累加规则统计) */
    private final ScrmCommissionRuleRepository ruleRepository;

    /** 佣金记录数据访问层 */
    private final ScrmCommissionRecordRepository recordRepository;

    /** JSON 解析器 (解析 tierConfig / 序列化计算明细) */
    private final ObjectMapper objectMapper;

    /** 佣金方案管理服务 (校验方案存在) */
    private final ScrmCommissionPlanService planService;

    /** 佣金规则管理服务 (匹配规则) */
    private final ScrmCommissionRuleService ruleService;

    /** 佣金记录服务 (记录查询 / 转换 / 金额舍入) */
    private final ScrmCommissionRecordService recordService;

    // ============================================================
    // 佣金计算
    // ============================================================

    /**
     * 计算佣金 (匹配方案 → 匹配规则 → 应用计算 → 创建记录)。
     * <p>完整实现: 解析方案计算基础 → 按条件过滤启用规则 → 按规则类型 (FLAT_RATE/TIERED_RATE/BONUS/
     * MULTIPLIER/DEDUCTION) 计算佣金 → 应用每单最大佣金与方案佣金上下限 → 创建佣金记录并更新方案/规则统计。</p>
     *
     * @param calculateDto 计算参数
     * @return 创建的佣金记录
     * @throws ScrmException 方案不存在 / 方案非激活 / 订单金额非法
     */
    @Transactional
    public ScrmCommissionRecordDto calculateCommission(
            ScrmCommissionCalculateDto calculateDto) throws ScrmException {
        if (calculateDto == null || calculateDto.getPlanId() == null) {
            throw ScrmException.badRequest("计算参数与方案 ID 不能为空");
        }
        ScrmCommissionPlanEntity plan = planService.findPlanOrThrow(calculateDto.getPlanId());
        if (!ScrmCommissionPlanService.STATUS_ACTIVE.equals(plan.getStatus())) {
            throw ScrmException.badRequest("佣金方案非激活状态, 当前状态: " + plan.getStatus());
        }
        if (calculateDto.getOrderAmount() == null || calculateDto.getOrderAmount() <= 0) {
            throw ScrmException.badRequest("订单金额必须大于 0");
        }
        // 计算佣金计算基础
        double basis = resolveBasis(plan, calculateDto);
        // 构造匹配条件 Map
        Map<String, Object> conditions = new LinkedHashMap<>();
        if (calculateDto.getProductCategory() != null) {
            conditions.put("product_category", calculateDto.getProductCategory());
        }
        if (calculateDto.getCustomerType() != null) {
            conditions.put("customer_type", calculateDto.getCustomerType());
        }
        if (calculateDto.getTeamId() != null) {
            conditions.put("team", calculateDto.getTeamId());
        }
        conditions.put("order_amount", calculateDto.getOrderAmount());
        // 加载匹配规则
        List<ScrmCommissionRuleEntity> matchedRules = ruleService.getMatchingRules(plan.getId(), conditions);
        // 应用规则计算
        List<Map<String, Object>> calculationDetails = new ArrayList<>();
        double commission = 0d;
        double bonusTotal = 0d;
        double deductionTotal = 0d;
        double appliedRate = 0d;
        ScrmCommissionRuleEntity appliedRule = null;
        for (ScrmCommissionRuleEntity rule : matchedRules) {
            // 校验最低订单金额
            if (rule.getMinOrderAmount() != null && rule.getMinOrderAmount() > 0 && calculateDto.getOrderAmount() < rule.getMinOrderAmount()) {
                continue;
            }
            double ruleCommission = 0d;
            switch (rule.getRuleType()) {
                case ScrmCommissionRuleService.RULE_FLAT_RATE:
                    ruleCommission = applyFlatRate(basis,
                            rule.getCommissionRate() != null ? rule.getCommissionRate() : 0d,
                            rule.getMaxCommissionPerOrder() != null ? rule.getMaxCommissionPerOrder() : 0d);
                    appliedRate = rule.getCommissionRate() != null ? rule.getCommissionRate() : 0d;
                    break;
                case ScrmCommissionRuleService.RULE_TIERED_RATE:
                    double[] tierResult = applyTieredRate(basis, rule.getTierConfig());
                    ruleCommission = tierResult[0];
                    if (tierResult.length > 1) {
                        appliedRate = tierResult[1];
                    }
                    break;
                case ScrmCommissionRuleService.RULE_BONUS:
                    ruleCommission = applyBonus(basis,
                            rule.getBonusAmount() != null ? rule.getBonusAmount() : 0d, rule.getConditions());
                    bonusTotal += ruleCommission;
                    break;
                case ScrmCommissionRuleService.RULE_MULTIPLIER:
                    commission = applyMultiplier(commission,
                            rule.getMultiplier() != null ? rule.getMultiplier() : 1.0d);
                    ruleCommission = 0d;
                    break;
                case ScrmCommissionRuleService.RULE_DEDUCTION:
                    double deduction = rule.getDeductionAmount() != null ? rule.getDeductionAmount() : 0d;
                    ruleCommission = -deduction;
                    deductionTotal += deduction;
                    break;
                default:
                    continue;
            }
            // 应用每单最大佣金 (FLAT_RATE/TIERED_RATE)
            if ((ScrmCommissionRuleService.RULE_FLAT_RATE.equals(rule.getRuleType()) || ScrmCommissionRuleService.RULE_TIERED_RATE.equals(rule.getRuleType())) && rule.getMaxCommissionPerOrder() != null && rule.getMaxCommissionPerOrder() > 0 && ruleCommission > rule.getMaxCommissionPerOrder()) {
                ruleCommission = rule.getMaxCommissionPerOrder();
            }
            if (ScrmCommissionRuleService.RULE_FLAT_RATE.equals(rule.getRuleType()) || ScrmCommissionRuleService.RULE_TIERED_RATE.equals(rule.getRuleType())) {
                commission += ruleCommission;
                if (appliedRule == null) {
                    appliedRule = rule;
                }
            } else if (ScrmCommissionRuleService.RULE_BONUS.equals(rule.getRuleType()) || ScrmCommissionRuleService.RULE_DEDUCTION.equals(rule.getRuleType())) {
                commission += ruleCommission;
            }
            // 记录计算详情
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("ruleId", rule.getId());
            detail.put("ruleName", rule.getRuleName());
            detail.put("ruleType", rule.getRuleType());
            detail.put("condition", rule.getConditions());
            detail.put("rate", rule.getCommissionRate());
            detail.put("amount", recordService.round2(ruleCommission));
            calculationDetails.add(detail);
            // 更新规则统计 (匹配次数 +1, 累计计算佣金)
            rule.setMatchCount((rule.getMatchCount() != null ? rule.getMatchCount() : 0) + 1);
            rule.setTotalCommissionCalculated(recordService.round2((rule.getTotalCommissionCalculated() != null
                    ? rule.getTotalCommissionCalculated() : 0d) + ruleCommission));
            ruleRepository.save(rule);
        }
        // 应用方案佣金上限与下限
        double finalCommission = commission;
        if (plan.getCapAmount() != null && plan.getCapAmount() > 0 && finalCommission > plan.getCapAmount()) {
            finalCommission = plan.getCapAmount();
        }
        if (plan.getMinAmount() != null && plan.getMinAmount() > 0 && finalCommission < plan.getMinAmount()) {
            finalCommission = plan.getMinAmount();
        }
        if (finalCommission < 0) {
            finalCommission = 0d;
        }
        // 创建佣金记录
        ScrmCommissionRecordEntity record = new ScrmCommissionRecordEntity();
        record.setRecordNo(generateRecordNo());
        record.setPlanId(plan.getId());
        record.setPlanName(plan.getPlanName());
        if (appliedRule != null) {
            record.setRuleId(appliedRule.getId());
            record.setRuleName(appliedRule.getRuleName());
        } else if (!matchedRules.isEmpty()) {
            record.setRuleId(matchedRules.get(0).getId());
            record.setRuleName(matchedRules.get(0).getRuleName());
        }
        record.setSalesPersonId(calculateDto.getSalesPersonId());
        record.setSalesPersonName(calculateDto.getSalesPersonName());
        record.setTeamId(calculateDto.getTeamId());
        record.setTeamName(calculateDto.getTeamName());
        record.setOrderId(calculateDto.getOrderId());
        record.setOrderAmount(calculateDto.getOrderAmount());
        record.setOrderProfit(calculateDto.getOrderProfit());
        record.setOrderDate(LocalDate.now());
        record.setProductCategory(calculateDto.getProductCategory());
        record.setCustomerType(calculateDto.getCustomerType());
        record.setCommissionBasis(recordService.round2(basis));
        record.setCommissionRate(recordService.round2(appliedRate));
        record.setCommissionAmount(recordService.round2(commission));
        record.setBonusAmount(recordService.round2(bonusTotal));
        record.setDeductionAmount(recordService.round2(deductionTotal));
        record.setFinalCommission(recordService.round2(finalCommission));
        record.setCalculationDetails(toJson(calculationDetails));
        record.setStatus(ScrmCommissionPlanService.RECORD_CALCULATED);
        record.setPeriod(YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM")));
        record.setCalculatedAt(LocalDateTime.now());
        record.setCreatedBy(ScrmCommissionPlanService.DEFAULT_OPERATOR);
        record = recordRepository.save(record);
        // 增量更新方案销售总额与订单数
        plan.setTotalSalesAmount(recordService.round2((plan.getTotalSalesAmount() != null ? plan.getTotalSalesAmount() : 0d)
                + calculateDto.getOrderAmount()));
        plan.setTotalOrders((plan.getTotalOrders() != null ? plan.getTotalOrders() : 0) + 1);
        planRepository.save(plan);
        log.info("计算佣金: recordNo={}, planId={}, salesPersonId={}, orderAmount={}, basis={}, finalCommission={}",
                record.getRecordNo(), plan.getId(), calculateDto.getSalesPersonId(),
                calculateDto.getOrderAmount(), basis, finalCommission);
        return recordService.toRecordDto(record);
    }

    /**
     * 批量计算佣金。
     *
     * @param calculateDtos 计算参数列表
     * @return 创建的佣金记录列表
     * @throws ScrmException 部分失败时跳过
     */
    @Transactional
    public List<ScrmCommissionRecordDto> batchCalculate(List<ScrmCommissionCalculateDto> calculateDtos)
            throws ScrmException {
        if (calculateDtos == null || calculateDtos.isEmpty()) {
            throw ScrmException.badRequest("计算参数列表不能为空");
        }
        List<ScrmCommissionRecordDto> results = new ArrayList<>();
        for (ScrmCommissionCalculateDto dto : calculateDtos) {
            try {
                results.add(calculateCommission(dto));
            } catch (ScrmException e) {
                log.warn("批量计算佣金跳过: planId={}, salesPersonId={}, err={}",
                        dto.getPlanId(), dto.getSalesPersonId(), e.getMessage());
            }
        }
        log.info("批量计算佣金: total={}, success={}", calculateDtos.size(), results.size());
        return results;
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
    @Transactional
    public ScrmCommissionRecordDto calculateForOrder(String orderId, String salesPersonId, Double orderAmount)
            throws ScrmException {
        if (orderAmount == null || orderAmount <= 0) {
            throw ScrmException.badRequest("订单金额必须大于 0");
        }
        ScrmCommissionPlanEntity plan = planRepository.findByIsDefaultTrue()
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "账号下未配置默认佣金方案"));
        ScrmCommissionCalculateDto dto = new ScrmCommissionCalculateDto();
        dto.setPlanId(plan.getId());
        dto.setSalesPersonId(salesPersonId);
        dto.setOrderId(orderId);
        dto.setOrderAmount(orderAmount);
        return calculateCommission(dto);
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
    @Transactional(readOnly = true)
    public Map<String, Object> calculateForPeriod(Long planId, String period, String salesPersonId)
            throws ScrmException {
        planService.findPlanOrThrow(planId);
        Specification<ScrmCommissionRecordEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("planId"), planId));
            if (period != null && !period.isBlank()) {
                predicates.add(cb.equal(root.get("period"), period));
            }
            if (salesPersonId != null && !salesPersonId.isBlank()) {
                predicates.add(cb.equal(root.get("salesPersonId"), salesPersonId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ScrmCommissionRecordEntity> records = recordRepository.findAll(spec);
        double totalCommission = records.stream()
                .mapToDouble(r -> r.getFinalCommission() != null ? r.getFinalCommission() : 0d).sum();
        double totalPaid = records.stream()
                .filter(r -> ScrmCommissionPlanService.RECORD_PAID.equals(r.getStatus()))
                .mapToDouble(r -> r.getPaidAmount() != null ? r.getPaidAmount() : 0d).sum();
        double totalOrderAmount = records.stream()
                .mapToDouble(r -> r.getOrderAmount() != null ? r.getOrderAmount() : 0d).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", planId);
        result.put("period", period);
        result.put("salesPersonId", salesPersonId);
        result.put("recordCount", records.size());
        result.put("totalCommission", recordService.round2(totalCommission));
        result.put("totalPaid", recordService.round2(totalPaid));
        result.put("totalOrderAmount", recordService.round2(totalOrderAmount));
        result.put("records", records.stream().map(recordService::toRecordDto).collect(Collectors.toList()));
        return result;
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
        double commission = basis * rate;
        if (cap > 0 && commission > cap) {
            commission = cap;
        }
        return recordService.round2(commission);
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
        if (tierConfig == null || tierConfig.isBlank()) {
            return new double[]{0d, 0d};
        }
        List<Map<String, Object>> tiers;
        try {
            tiers = objectMapper.readValue(tierConfig, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("阶梯配置 JSON 解析失败: {}", e.getMessage());
            return new double[]{0d, 0d};
        }
        double commission = 0d;
        double appliedRate = 0d;
        for (Map<String, Object> tier : tiers) {
            double min = ruleService.toDouble(tier.get("minValue"));
            double max = ruleService.toDouble(tier.get("maxValue"));
            double rate = ruleService.toDouble(tier.get("rate"));
            double bonus = ruleService.toDouble(tier.get("bonusAmount"));
            boolean inRange = basis >= min && (max <= 0 || basis <= max);
            if (inRange) {
                commission = basis * rate + bonus;
                appliedRate = rate;
                break;
            }
        }
        return new double[]{recordService.round2(commission), appliedRate};
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
        return recordService.round2(bonusAmount);
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
        return recordService.round2(commission * multiplier);
    }

    /**
     * 重新计算佣金记录 (基于已有记录的订单信息重新匹配规则)。
     *
     * @param recordId 记录 ID
     * @return 重新计算后的记录
     * @throws ScrmException 记录不存在
     */
    @Transactional
    public ScrmCommissionRecordDto recalculate(Long recordId) throws ScrmException {
        ScrmCommissionRecordEntity record = recordService.findRecordOrThrow(recordId);
        ScrmCommissionCalculateDto dto = new ScrmCommissionCalculateDto();
        dto.setPlanId(record.getPlanId());
        dto.setSalesPersonId(record.getSalesPersonId());
        dto.setSalesPersonName(record.getSalesPersonName());
        dto.setTeamId(record.getTeamId());
        dto.setTeamName(record.getTeamName());
        dto.setOrderId(record.getOrderId());
        dto.setOrderAmount(record.getOrderAmount());
        dto.setOrderProfit(record.getOrderProfit());
        dto.setProductCategory(record.getProductCategory());
        dto.setCustomerType(record.getCustomerType());
        ScrmCommissionRecordDto newRecord = calculateCommission(dto);
        // 标记原记录为已调整
        record.setStatus(ScrmCommissionPlanService.RECORD_ADJUSTED);
        record.setNotes("重新计算, 新记录编号: " + newRecord.getRecordNo());
        recordRepository.save(record);
        return newRecord;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 生成佣金编号: COMM + yyyyMM + 6 位毫秒时间戳后缀。
     *
     * @return 佣金编号
     */
    public String generateRecordNo() {
        String ym = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String suffix = String.format("%06d", System.currentTimeMillis() % 1000000L);
        return RECORD_NO_PREFIX + ym + suffix;
    }

    /**
     * 解析计算基础。
     *
     * @param plan         方案
     * @param calculateDto 计算参数
     * @return 计算基础值
     */
    private double resolveBasis(ScrmCommissionPlanEntity plan, ScrmCommissionCalculateDto calculateDto) {
        switch (plan.getCalculationBasis()) {
            case BASIS_ORDER_AMOUNT:
                return calculateDto.getOrderAmount() != null ? calculateDto.getOrderAmount() : 0d;
            case BASIS_ORDER_PROFIT:
                return calculateDto.getOrderProfit() != null ? calculateDto.getOrderProfit() : 0d;
            case BASIS_ORDER_COUNT:
                return 1d;
            case BASIS_REVENUE_TARGET:
                return plan.getTargetAmount() != null ? plan.getTargetAmount() : 0d;
            case BASIS_UNITS_SOLD:
                return 1d;
            default:
                return calculateDto.getOrderAmount() != null ? calculateDto.getOrderAmount() : 0d;
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }
}