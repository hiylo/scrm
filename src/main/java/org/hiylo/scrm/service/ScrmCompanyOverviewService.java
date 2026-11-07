/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCompanyOverviewService.java
 * Date : 2026/10/23 21:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmContractRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmInspectionTaskRepository;
import org.hiylo.scrm.repository.ScrmLicenseOcrTaskRepository;
import org.hiylo.scrm.repository.ScrmRenewalTaskRepository;
import org.hiylo.scrm.repository.ScrmTaxDeclarationProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公司 360 聚合服务（以公司为主体的业务全景）。
 * <p>
 * 财税业务的天然主体是公司，但联系人、合同、报税、证照、巡检、续约分散在不同域。
 * 本服务按 {@code companyId} 聚合各域数据，输出「一家公司现在什么状态」：
 * </p>
 * <ul>
 *   <li><b>主体档案</b>：名称、信用代码、工商/税务状态、服务状态与到期日</li>
 *   <li><b>联系人</b>：总数 + 按角色分布（老板/财务/出纳/行政/经办/其他）</li>
 *   <li><b>业务数据</b>：合同、报税档案、证照识别、巡检任务、续费任务各自数量</li>
 *   <li><b>风险提示</b>：工商异常/注销、税务非正常、服务临期（30 天内）</li>
 * </ul>
 * <p>
 * 只读聚合，不做写操作；各域通过 {@code companyId} 关联（业务创建时由
 * {@link CompanyResolver} 自动写入）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmCompanyOverviewService {

    /** 服务临期提醒窗口（天） */
    private static final int EXPIRING_WINDOW_DAYS = 30;

    /** 公司主体数据访问层 */
    private final ScrmCompanyRepository companyRepository;

    /** 联系人数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 合同数据访问层 */
    private final ScrmContractRepository contractRepository;

    /** 报税档案数据访问层 */
    private final ScrmTaxDeclarationProfileRepository taxProfileRepository;

    /** 证照识别任务数据访问层 */
    private final ScrmLicenseOcrTaskRepository licenseOcrTaskRepository;

    /** 巡检任务数据访问层 */
    private final ScrmInspectionTaskRepository inspectionTaskRepository;

    /** 续费任务数据访问层 */
    private final ScrmRenewalTaskRepository renewalTaskRepository;

    /**
     * 聚合公司 360 概览。
     *
     * @param companyId 公司主体 ID
     * @return 概览数据（主体 + 联系人分布 + 业务计数 + 风险提示）
     * @throws ScrmException 公司主体不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long companyId) throws ScrmException {
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", company.getId());
        result.put("companyName", company.getCompanyName());
        result.put("creditCode", company.getCreditCode());
        result.put("companyType", company.getCompanyType());
        result.put("legalRepresentative", company.getLegalRepresentative());
        result.put("businessStatus", company.getBusinessStatus());
        result.put("taxStatus", company.getTaxStatus());
        result.put("serviceType", company.getServiceType());
        result.put("serviceStatus", company.getServiceStatus());
        result.put("serviceEndDate", company.getServiceEndDate());
        result.put("verified", company.getVerified());

        // 联系人：总数 + 按角色分布
        long contactCount = customerRepository.countByCompanyId(companyId);
        Map<String, Object> contacts = new LinkedHashMap<>();
        contacts.put("total", contactCount);
        contacts.put("byRole", countContactsByRole(companyId));
        result.put("contacts", contacts);

        // 业务数据计数
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("contracts", contractRepository.countByCompanyId(companyId));
        business.put("taxProfiles", taxProfileRepository.countByCompanyId(companyId));
        business.put("licenseOcrTasks", licenseOcrTaskRepository.countByCompanyId(companyId));
        business.put("inspectionTasks", inspectionTaskRepository.countByCompanyId(companyId));
        business.put("renewalTasks", renewalTaskRepository.countByCompanyId(companyId));
        result.put("business", business);

        result.put("risks", collectRisks(company));
        return result;
    }

    /**
     * 统计公司下联系人的角色分布。
     *
     * @param companyId 公司主体 ID
     * @return 角色 → 数量（未设置角色的归入 UNKNOWN）
     */
    private Map<String, Long> countContactsByRole(Long companyId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        customerRepository.findByCompanyIdOrderByRole(companyId).forEach(row -> {
            Object role = row[0];
            Object count = row[1];
            counts.put(role == null ? "UNKNOWN" : String.valueOf(role),
                    count == null ? 0L : ((Number) count).longValue());
        });
        return counts;
    }

    /**
     * 汇总主体风险提示（工商异常/注销、税务非正常、服务临期）。
     *
     * @param company 主体
     * @return 风险描述列表（无风险时为空列表）
     */
    private java.util.List<String> collectRisks(ScrmCompanyEntity company) {
        java.util.List<String> risks = new java.util.ArrayList<>();
        String businessStatus = company.getBusinessStatus();
        if (ScrmCompanyEntity.BUSINESS_ABNORMAL.equals(businessStatus)) {
            risks.add("工商状态异常（经营异常名录）");
        } else if (ScrmCompanyEntity.BUSINESS_CANCELLED.equals(businessStatus)) {
            risks.add("企业已注销");
        } else if (ScrmCompanyEntity.BUSINESS_REVOKED.equals(businessStatus)) {
            risks.add("企业已被吊销");
        }
        String taxStatus = company.getTaxStatus();
        if (ScrmCompanyEntity.TAX_ABNORMAL.equals(taxStatus)) {
            risks.add("税务非正常户");
        } else if (ScrmCompanyEntity.TAX_ARREARS.equals(taxStatus)) {
            risks.add("存在欠税");
        }
        java.time.LocalDate endDate = company.getServiceEndDate();
        if (endDate != null) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (endDate.isBefore(today)) {
                risks.add("服务已到期（" + endDate + "），需立即续费");
            } else if (endDate.minusDays(EXPIRING_WINDOW_DAYS).isBefore(today)) {
                risks.add("服务将于 " + endDate + " 到期（" + EXPIRING_WINDOW_DAYS + " 天内）");
            }
        }
        if (Boolean.FALSE.equals(company.getVerified())) {
            risks.add("主体信息尚未核验");
        }
        return risks;
    }
}
