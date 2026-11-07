/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmPayrollController.java
 * Date : 2026/10/25 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.entity.ScrmEmployeeEntity;
import org.hiylo.scrm.entity.ScrmIitRecordEntity;
import org.hiylo.scrm.entity.ScrmSocialSecurityRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.payroll.ScrmPayrollService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 薪酬域控制器：员工档案 + 社保明细 + 个税明细。
 * <p>
 * 代账公司每月固定动作是「增减员 → 社保申报 → 个税申报」，三者共享员工档案，
 * 因此放在同一控制器下，按 {@code /employees}、{@code /social}、{@code /iit} 分组。
 * </p>
 * <p>
 * <b>金额口径</b>：所有金额由 {@code ScrmPayrollCalculator} 精确计算后写入库，
 * 本层不接收前端传来的税额/社保额作为最终值，避免前端篡改账目。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/payroll")
@RequiredArgsConstructor
public class ScrmPayrollController {

    /** 薪酬域服务 */
    private final ScrmPayrollService payrollService;

    // ============================================================
    // 员工档案
    // ============================================================

    /**
     * 分页查询员工档案。
     *
     * @param page      页码（默认 0）
     * @param size      每页大小（默认 20）
     * @param companyId 主体过滤（可空）
     * @param status    在职状态过滤（可空）
     * @param keyword   姓名/岗位/电话关键字（可空）
     * @return 员工分页结果
     */
    @RequirePermission(resource = "scrm_employee", action = "read")
    @GetMapping("/employees")
    public OperationResponse<Page<ScrmEmployeeEntity>> listEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.ASC, "hireDate"));
        return OperationResponse.build(
                payrollService.listEmployees(companyId, status, keyword, pageable));
    }

    /**
     * 查询员工详情。
     *
     * @param id 员工 ID
     * @return 员工
     * @throws ScrmException 不存在
     */
    @RequirePermission(resource = "scrm_employee", action = "read")
    @GetMapping("/employees/{id}")
    public OperationResponse<ScrmEmployeeEntity> getEmployee(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(payrollService.getEmployee(id));
    }

    /**
     * 新增员工。
     *
     * @param entity 员工信息
     * @return 创建后的员工
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @RequirePermission(resource = "scrm_employee", action = "create")
    @PostMapping("/employees")
    public OperationResponse<ScrmEmployeeEntity> createEmployee(
            @RequestBody ScrmEmployeeEntity entity) throws ScrmException {
        return OperationResponse.build(payrollService.createEmployee(entity));
    }

    /**
     * 更新员工（null 字段不覆盖）。
     *
     * @param id     员工 ID
     * @param entity 更新内容
     * @return 更新后的员工
     * @throws ScrmException 员工不存在
     */
    @RequirePermission(resource = "scrm_employee", action = "update")
    @PutMapping("/employees/{id}")
    public OperationResponse<ScrmEmployeeEntity> updateEmployee(
            @PathVariable Long id, @RequestBody ScrmEmployeeEntity entity) throws ScrmException {
        return OperationResponse.build(payrollService.updateEmployee(id, entity));
    }

    /**
     * 删除员工（已有社保/个税明细的不允许删，须改为离职）。
     *
     * @param id 员工 ID
     * @return 空响应
     * @throws ScrmException 员工不存在 / 已有明细
     */
    @RequirePermission(resource = "scrm_employee", action = "delete")
    @DeleteMapping("/employees/{id}")
    public OperationResponse<Void> deleteEmployee(@PathVariable Long id) throws ScrmException {
        payrollService.deleteEmployee(id);
        return OperationResponse.build(null);
    }

    // ============================================================
    // 社保 / 个税批量生成
    // ============================================================

    /**
     * 为某公司某月批量生成社保 + 个税明细。
     * <p>
     * 收入按「employeeId:amount」的逗号分隔串传入，如 {@code 123:8000,456:12000}。
     * 未传入的员工按 0 收入计算个税（仍会生成社保明细）。
     * </p>
     *
     * @param companyId   公司主体 ID
     * @param year        年
     * @param month       月（1-12）
     * @param incomes     各员工税前收入（可空）
     * @return 生成统计
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @RequirePermission(resource = "scrm_social_security_record", action = "create")
    @PostMapping("/generate")
    public OperationResponse<Map<String, Object>> generateMonthly(
            @RequestParam Long companyId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String incomes) throws ScrmException {
        return OperationResponse.build(payrollService.generateMonthly(
                companyId, year, month, parseIncomes(incomes)));
    }

    // ============================================================
    // 社保明细
    // ============================================================

    /**
     * 查某公司某月社保明细。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 明细列表
     */
    @RequirePermission(resource = "scrm_social_security_record", action = "read")
    @GetMapping("/social")
    public OperationResponse<List<ScrmSocialSecurityRecordEntity>> listSocial(
            @RequestParam Long companyId, @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(payrollService.listSocialRecords(companyId, year, month));
    }

    /**
     * 社保汇总（人数 / 个人合计 / 单位合计 / 总计 / 各状态计数）。
     *
     * @param companyId 公司主体 ID（可空 = 全量）
     * @param year      年
     * @param month     月
     * @return 汇总
     */
    @RequirePermission(resource = "scrm_social_security_record", action = "read")
    @GetMapping("/social/summary")
    public OperationResponse<Map<String, Object>> socialSummary(
            @RequestParam(required = false) Long companyId,
            @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(payrollService.socialSummary(companyId, year, month));
    }

    /**
     * 标记社保明细已申报 / 已缴费。
     *
     * @param id 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @RequirePermission(resource = "scrm_social_security_record", action = "update")
    @PostMapping("/social/{id}/declared")
    public OperationResponse<ScrmSocialSecurityRecordEntity> markSocialDeclared(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(payrollService.markSocialStatus(id, false));
    }

    /**
     * 标记社保明细已缴费。
     *
     * @param id 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @RequirePermission(resource = "scrm_social_security_record", action = "update")
    @PostMapping("/social/{id}/paid")
    public OperationResponse<ScrmSocialSecurityRecordEntity> markSocialPaid(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(payrollService.markSocialStatus(id, true));
    }

    // ============================================================
    // 个税明细
    // ============================================================

    /**
     * 查某公司某月个税明细。
     *
     * @param companyId 公司主体 ID
     * @param year      年
     * @param month     月
     * @return 明细列表
     */
    @RequirePermission(resource = "scrm_iit_record", action = "read")
    @GetMapping("/iit")
    public OperationResponse<List<ScrmIitRecordEntity>> listIit(
            @RequestParam Long companyId, @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(payrollService.listIitRecords(companyId, year, month));
    }

    /**
     * 个税汇总（人数 / 收入合计 / 税额合计 / 各状态计数）。
     *
     * @param companyId 公司主体 ID（可空 = 全量）
     * @param year      年
     * @param month     月
     * @return 汇总
     */
    @RequirePermission(resource = "scrm_iit_record", action = "read")
    @GetMapping("/iit/summary")
    public OperationResponse<Map<String, Object>> iitSummary(
            @RequestParam(required = false) Long companyId,
            @RequestParam int year, @RequestParam int month) {
        return OperationResponse.build(payrollService.iitSummary(companyId, year, month));
    }

    /**
     * 标记个税明细已申报。
     *
     * @param id 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @RequirePermission(resource = "scrm_iit_record", action = "update")
    @PostMapping("/iit/{id}/declared")
    public OperationResponse<ScrmIitRecordEntity> markIitDeclared(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(payrollService.markIitStatus(id, false));
    }

    /**
     * 标记个税明细已缴款。
     *
     * @param id 明细 ID
     * @return 更新后的明细
     * @throws ScrmException 明细不存在
     */
    @RequirePermission(resource = "scrm_iit_record", action = "update")
    @PostMapping("/iit/{id}/paid")
    public OperationResponse<ScrmIitRecordEntity> markIitPaid(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(payrollService.markIitStatus(id, true));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 解析「employeeId:amount,employeeId:amount」形式的收入串。
     *
     * @param incomes 收入串（可空）
     * @return 员工 ID → 税前收入
     */
    private Map<Long, BigDecimal> parseIncomes(String incomes) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (incomes == null || incomes.isBlank()) {
            return result;
        }
        for (String part : incomes.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] kv = trimmed.split(":");
            if (kv.length != 2) {
                log.warn("[薪酬] 忽略格式不正确的收入项: {}", trimmed);
                continue;
            }
            try {
                result.put(Long.parseLong(kv[0].trim()), new BigDecimal(kv[1].trim()));
            } catch (NumberFormatException e) {
                log.warn("[薪酬] 忽略无法解析的收入项: {}", trimmed);
            }
        }
        return result;
    }
}
