/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmComplianceController.java
 * Date : 2026/10/25 10:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.entity.ScrmAnnualReportEntity;
import org.hiylo.scrm.entity.ScrmQualificationEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.service.DataScopeService;
import org.hiylo.scrm.service.finance.compliance.ScrmComplianceService;
import org.hiylo.scrm.service.finance.document.ScrmCustomerDocumentService;
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

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规域控制器：工商年报 + 资质许可。
 * <p>
 * 两块业务同属「代账公司的法定提醒责任」，共用一套「生成 → 推进 → 提醒 → 回填」节奏：
 * </p>
 * <ul>
 *   <li><b>工商年报</b>：每年 1/1–6/30 报送上一年度报告，逾期进经营异常名录；</li>
 *   <li><b>资质许可</b>：食品经营、劳务派遣等证照到期前须提醒客户续办。</li>
 * </ul>
 * <p>
 * 所有端点都支持 {@code mine=true}：只看当前登录会计负责的主体，
 * 由后端解析会话用户得出，避免前端传参越权。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/compliance")
@RequiredArgsConstructor
public class ScrmComplianceController {

    /** 合规域服务 */
    private final ScrmComplianceService complianceService;

    /** 数据范围服务（解析当前登录用户） */
    private final DataScopeService dataScopeService;

    /** 资料归档服务（资料自身到期推进与合规事项同属「到期」语义） */
    private final ScrmCustomerDocumentService documentService;

    // ============================================================
    // 工商年报
    // ============================================================

    /**
     * 分页查询工商年报。
     *
     * @param page        页码（默认 0）
     * @param size        每页大小（默认 20）
     * @param status      状态过滤（可空）
     * @param year        报送年度过滤（可空）
     * @param companyId   主体过滤（可空）
     * @param mine        只看我负责的主体
     * @return 年报分页结果
     */
    @RequirePermission(resource = "scrm_annual_report", action = "read")
    @GetMapping("/annual-reports")
    public OperationResponse<Page<ScrmAnnualReportEntity>> listAnnualReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long companyId,
            @RequestParam(defaultValue = "false") boolean mine) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.ASC, "dueDate"));
        return OperationResponse.build(complianceService.listAnnualReports(
                status, year, companyId, resolveAccountantId(mine), pageable));
    }

    /**
     * 年报概览统计（前端顶部卡片）。
     *
     * @param year 年度过滤（可空 = 全部年度）
     * @return 各状态计数
     */
    @RequirePermission(resource = "scrm_annual_report", action = "read")
    @GetMapping("/annual-reports/summary")
    public OperationResponse<Map<String, Object>> annualSummary(
            @RequestParam(required = false) Integer year) {
        return OperationResponse.build(complianceService.annualSummary(year));
    }

    /**
     * 手动生成指定年度的年报任务。
     * <p>
     * 常规生成由调度每年 1 月 1 日执行；此端点用于系统上线首年补历史、
     * 或新签企业需要补当年任务时手动触发。幂等（一户一年一条）。
     * </p>
     *
     * @param year 报送年度（不传默认上一年度）
     * @return 本次生成条数
     */
    @RequirePermission(resource = "scrm_annual_report", action = "create")
    @PostMapping("/annual-reports/generate")
    public OperationResponse<Integer> generateAnnualReports(
            @RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear() - 1;
        return OperationResponse.build(complianceService.generateAnnualReports(targetYear));
    }

    /**
     * 查询年报详情。
     *
     * @param id 年报 ID
     * @return 年报记录
     * @throws ScrmException 记录不存在
     */
    @RequirePermission(resource = "scrm_annual_report", action = "read")
    @GetMapping("/annual-reports/{id}")
    public OperationResponse<ScrmAnnualReportEntity> getAnnualReport(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(complianceService.getAnnualReport(id));
    }

    /**
     * 标记年报已报送。
     *
     * @param id        年报 ID
     * @param receiptNo 公示系统回执编号（可空）
     * @param remark    备注（可空）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 状态不允许
     */
    @RequirePermission(resource = "scrm_annual_report", action = "update")
    @PostMapping("/annual-reports/{id}/submit")
    public OperationResponse<ScrmAnnualReportEntity> submitAnnualReport(
            @PathVariable Long id,
            @RequestParam(required = false) String receiptNo,
            @RequestParam(required = false) String remark) throws ScrmException {
        return OperationResponse.build(
                complianceService.markAnnualSubmitted(id, receiptNo, remark));
    }

    /**
     * 标记年报免报（当年注销/未开展经营），免报不计入逾期。
     *
     * @param id     年报 ID
     * @param remark 免报原因（必填）
     * @return 更新后的记录
     * @throws ScrmException 记录不存在 / 原因为空
     */
    @RequirePermission(resource = "scrm_annual_report", action = "update")
    @PostMapping("/annual-reports/{id}/exempt")
    public OperationResponse<ScrmAnnualReportEntity> exemptAnnualReport(
            @PathVariable Long id, @RequestParam String remark) throws ScrmException {
        return OperationResponse.build(complianceService.markAnnualExempt(id, remark));
    }

    // ============================================================
    // 资质许可
    // ============================================================

    /**
     * 分页查询资质许可。
     *
     * @param page      页码（默认 0）
     * @param size      每页大小（默认 20）
     * @param status    状态过滤（可空）
     * @param companyId 主体过滤（可空）
     * @param keyword   证照名/证号/企业名关键字（可空）
     * @param mine      只看我负责的主体
     * @return 证照分页结果
     */
    @RequirePermission(resource = "scrm_qualification", action = "read")
    @GetMapping("/qualifications")
    public OperationResponse<Page<ScrmQualificationEntity>> listQualifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean mine) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.ASC, "expireDate"));
        return OperationResponse.build(complianceService.listQualifications(
                status, companyId, resolveAccountantId(mine), keyword, pageable));
    }

    /**
     * 资质概览统计（前端顶部卡片）。
     *
     * @return 各状态计数 + 90 天内到期数
     */
    @RequirePermission(resource = "scrm_qualification", action = "read")
    @GetMapping("/qualifications/summary")
    public OperationResponse<Map<String, Object>> qualificationSummary() {
        return OperationResponse.build(complianceService.qualificationSummary());
    }

    /**
     * 新增资质许可。
     *
     * @param entity 证照信息
     * @return 创建后的证照
     * @throws ScrmException 参数非法 / 主体不存在
     */
    @RequirePermission(resource = "scrm_qualification", action = "create")
    @PostMapping("/qualifications")
    public OperationResponse<ScrmQualificationEntity> createQualification(
            @RequestBody ScrmQualificationEntity entity) throws ScrmException {
        return OperationResponse.build(complianceService.createQualification(entity));
    }

    /**
     * 更新资质许可（null 字段不覆盖）。
     *
     * @param id     证照 ID
     * @param entity 更新内容
     * @return 更新后的证照
     * @throws ScrmException 证照不存在
     */
    @RequirePermission(resource = "scrm_qualification", action = "update")
    @PutMapping("/qualifications/{id}")
    public OperationResponse<ScrmQualificationEntity> updateQualification(
            @PathVariable Long id, @RequestBody ScrmQualificationEntity entity)
            throws ScrmException {
        return OperationResponse.build(complianceService.updateQualification(id, entity));
    }

    /**
     * 删除资质许可。
     *
     * @param id 证照 ID
     * @return 空响应
     * @throws ScrmException 证照不存在
     */
    @RequirePermission(resource = "scrm_qualification", action = "delete")
    @DeleteMapping("/qualifications/{id}")
    public OperationResponse<Void> deleteQualification(@PathVariable Long id) throws ScrmException {
        complianceService.deleteQualification(id);
        return OperationResponse.build(null);
    }

    /**
     * 标记证照已续期（更新到期日后回到有效）。
     *
     * @param id        证照 ID
     * @param newExpire 新到期日
     * @param remark    备注（可空）
     * @return 更新后的证照
     * @throws ScrmException 证照不存在 / 新到期日为空
     */
    @RequirePermission(resource = "scrm_qualification", action = "update")
    @PostMapping("/qualifications/{id}/renew")
    public OperationResponse<ScrmQualificationEntity> renewQualification(
            @PathVariable Long id,
            @RequestParam java.time.LocalDate newExpire,
            @RequestParam(required = false) String remark) throws ScrmException {
        return OperationResponse.build(
                complianceService.renewQualification(id, newExpire, remark));
    }

    // ============================================================
    // 手动触发（调度补充，便于验证与即时纠偏）
    // ============================================================

    /**
     * 立即推进合规状态（年报逾期判定 + 证照到期状态推进）。
     * <p>
     * 常规由调度每日 01:00 执行。此端点供两种场景：
     * ① 刚录入证照后想立刻看到它是否已进入「即将到期」；
     * ② 修了数据后需要立即纠偏，不想等次日调度。
     * </p>
     *
     * @return 推进结果：年报转逾期数 + 证照状态变更数
     */
    @RequirePermission(resource = "scrm_annual_report", action = "execute")
    @PostMapping("/advance")
    public OperationResponse<Map<String, Object>> advanceNow() {
        int annualOverdue = complianceService.advanceAnnualReports();
        int qualChanged = complianceService.advanceQualifications();
        // 资料自身的到期（身份证/合同过期）也必须一起推进，否则手动纠偏时会漏掉
        int documentExpired = documentService.advanceExpired();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("annualOverdue", annualOverdue);
        result.put("qualificationChanged", qualChanged);
        result.put("documentExpired", documentExpired);
        return OperationResponse.build(result);
    }

    /**
     * 立即推送到期提醒（年报 + 资质）。
     * <p>
     * 常规由调度每日 09:30 执行。此端点用于「想让会计立刻收到提醒」的场景。
     * 幂等：同一事项同一到期节点只推一次。
     * </p>
     *
     * @return 推送结果：年报推送数 + 资质推送数
     */
    @RequirePermission(resource = "scrm_annual_report", action = "execute")
    @PostMapping("/remind")
    public OperationResponse<Map<String, Object>> remindNow() {
        int annual = complianceService.pushAnnualReminders();
        int qualification = complianceService.pushQualificationReminders();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("annualPushed", annual);
        result.put("qualificationPushed", qualification);
        return OperationResponse.build(result);
    }

    /**
     * 按主体查询合规数据（年报 + 证照），供公司 360 钻取。
     *
     * @param companyId 公司主体 ID
     * @return 年报列表 + 证照列表
     */
    @RequirePermission(resource = "scrm_annual_report", action = "read")
    @GetMapping("/companies/{companyId}")
    public OperationResponse<Map<String, Object>> companyCompliance(@PathVariable Long companyId) {
        return OperationResponse.build(complianceService.companyCompliance(companyId));
    }

    /**
     * 解析「只看我的」过滤条件：mine=true 时取当前登录用户 ID。
     *
     * @param mine 是否只查自己负责的
     * @return 会计用户 ID；mine=false 或解析失败返回 null（不过滤）
     */
    private Long resolveAccountantId(boolean mine) {
        if (!mine) {
            return null;
        }
        String userId = dataScopeService.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            log.warn("[合规] 当前用户 ID 非法, 忽略 mine 过滤: {}", userId);
            return null;
        }
    }
}
