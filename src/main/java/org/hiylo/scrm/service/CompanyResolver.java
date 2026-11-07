/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : CompanyResolver.java
 * Date : 2026/10/23 20:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.stereotype.Service;

/**
 * 公司主体归属解析器（业务数据自动挂公司的统一入口）。
 * <p>
 * 财税业务的天然主体是「公司」，但既有业务数据（合同、报税档案、证照任务、巡检、
 * 续约）都是按 {@code customerId}（联系人）创建的。为避免各服务各自查库、语义漂移，
 * 统一由本组件从联系人反查其所属公司：
 * </p>
 * <ul>
 *   <li>联系人已归属公司 → 返回该 {@code companyId}</li>
 *   <li>联系人未归属公司 / 联系人不存在 / 入参为空 → 返回 {@code null}
 *       （业务数据仍可创建，只是暂时不挂主体，待联系人归并后回填）</li>
 * </ul>
 * <p>
 * <b>设计约束</b>：解析失败绝不抛异常——业务数据创建不能因为「没挂公司」而失败，
 * 否则会阻断现有流程；未挂主体的数据由后续归并任务补齐。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyResolver {

    /** 联系人数据访问层（只取 company_id，避免加载整实体） */
    private final ScrmCustomerRepository customerRepository;

    /** 公司主体数据访问层（解析负责会计用） */
    private final org.hiylo.scrm.repository.ScrmCompanyRepository companyRepository;

    /**
     * 按联系人解析所属公司主体 ID。
     *
     * @param customerId 联系人 ID（可空）
     * @return 公司主体 ID；未归属或无法解析时返回 {@code null}
     */
    public Long resolveByCustomerId(Long customerId) {
        if (customerId == null) {
            return null;
        }
        try {
            return customerRepository.findCompanyIdByCustomerId(customerId);
        } catch (Exception e) {
            // 解析失败不阻断业务创建（数据落库后再由归并任务补齐）
            log.warn("[公司归属] 解析失败，业务数据将不挂主体: customerId={}, err={}",
                    customerId, e.getMessage());
            return null;
        }
    }

    /**
     * 解析并返回「最终应写入的公司主体 ID」。
     * <p>调用方已显式指定 companyId 时以显式值为准（允许覆盖自动推导）。</p>
     *
     * @param explicitCompanyId 调用方显式传入的公司 ID（可空）
     * @param customerId        联系人 ID（可空）
     * @return 生效的公司主体 ID（可能为空）
     */
    public Long resolve(Long explicitCompanyId, Long customerId) {
        return explicitCompanyId != null ? explicitCompanyId : resolveByCustomerId(customerId);
    }

    /**
     * 解析主体负责会计的用户 ID（提醒路由用）。
     * <p>
     * 代账公司的提醒应发给「负责这家公司的会计」，而不是全公司一个固定收件人。
     * 以下任一情况返回 {@code null}，由调用方回落到配置的默认收件人：
     * </p>
     * <ul>
     *   <li>主体 ID 为空（业务数据未挂公司）</li>
     *   <li>主体不存在</li>
     *   <li>主体未指派负责会计</li>
     * </ul>
     * <p>
     * 与 {@link #resolveByCustomerId} 一致：解析失败不抛异常，绝不阻断提醒主流程。
     * </p>
     *
     * @param companyId 公司主体 ID（可空）
     * @return 负责会计的用户 ID；无法解析时返回 {@code null}
     */
    public Long resolveAccountantId(Long companyId) {
        if (companyId == null) {
            return null;
        }
        try {
            return companyRepository.findById(companyId)
                    .map(org.hiylo.scrm.entity.ScrmCompanyEntity::getAccountantId)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[公司归属] 负责会计解析失败, 提醒将回落默认收件人: companyId={}, err={}",
                    companyId, e.getMessage());
            return null;
        }
    }
}
