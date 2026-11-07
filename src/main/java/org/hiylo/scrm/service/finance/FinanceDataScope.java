/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : FinanceDataScope.java
 * Date : 2026/10/26 14:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.constant.FinanceRoles;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.service.DataScopeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 财税域数据范围解析。
 * <p>
 * 代账公司的数据隔离口径与 SCRM 的「账号归属」模型不同：财税业务以
 * <b>公司主体</b>为唯一维度，隔离依据是 {@code scrm_company.accountant_id}
 * （负责会计的用户 ID），而非 {@code owner_account_id}（企微账号 ID）。
 * </p>
 * <p>
 * 角色与可见范围：
 * <ul>
 *   <li><b>ADMIN / SUPERADMIN / MANAGER / SUPERVISOR</b> → 全部（管理人员视角）；</li>
 *   <li><b>ACCOUNTANT</b> → 仅 {@code accountant_id = 当前用户} 的主体；</li>
 *   <li><b>SALES / OPERATOR / VIEWER</b> → 沿用原有 SCRM 账号归属逻辑（不受本类影响）。</li>
 * </ul>
 * </p>
 * <p>
 * <b>fail-closed</b>：会计角色但用户 ID 缺失/非法、或未绑定任何主体时返回空列表，
 * 绝不回退成「全部可见」——「看不到自己的客户」比「误看到别人的客户」安全。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceDataScope {

    /** 可见全部数据的角色（管理人员） */
    private static final List<String> MANAGEMENT_ROLES =
            List.of(DataScopeService.ROLE_ADMIN, "SUPERADMIN", "MANAGER", FinanceRoles.SUPERVISOR);

    /** 数据隔离依赖的公司主体仓储 */
    private final ScrmCompanyRepository companyRepository;

    /** 身份与角色来源 */
    private final DataScopeService dataScopeService;

    /**
     * 判断当前登录用户是否管理人员（可查看全部财税数据）。
     *
     * @param rolesCsv 逗号分隔的角色串（来自 JWT，可信）
     * @return true = 管理人员
     */
    public boolean isManagement(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) {
            return false;
        }
        for (String role : rolesCsv.split(",")) {
            String normalized = role.trim().toUpperCase();
            if (MANAGEMENT_ROLES.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前登录用户是否为会计（数据受限角色）。
     *
     * @param rolesCsv 逗号分隔的角色串
     * @return true = 会计角色
     */
    public boolean isAccountant(String rolesCsv) {
        return FinanceRoles.contains(rolesCsv, FinanceRoles.ACCOUNTANT);
    }

    /**
     * 判断本类是否需要接管该角色的数据范围。
     * <p>
     * 只有「会计」角色由本类接管；管理人员返回不限；其余历史角色
     * （SALES / OPERATOR / VIEWER）保持原有 SCRM 账号归属逻辑不变，
     * 避免改动会话、客户等既有域的行为。
     * </p>
     *
     * @param rolesCsv 逗号分隔的角色串
     * @return true = 由财税数据范围接管
     */
    public boolean appliesTo(String rolesCsv) {
        return isAccountant(rolesCsv) && !isManagement(rolesCsv);
    }

    /**
     * 解析当前用户可访问的公司主体 ID 集合。
     * <p>
     * 返回值语义：
     * <ul>
     *   <li>{@code null} → 不限制（管理人员，或不由本类接管的角色）；</li>
     *   <li>空集合 → 无任何可访问主体（会计未绑定主体 / 身份异常），调用方必须返回空结果；</li>
     *   <li>非空集合 → 仅这些主体可见。</li>
     * </ul>
     * </p>
     *
     * @return 可访问主体 ID 集合，null 表示不限制
     */
    public List<Long> resolveAccessibleCompanyIds() {
        String rolesCsv = dataScopeService.getCurrentRole();
        if (!appliesTo(rolesCsv)) {
            return null;
        }
        Long accountantId = currentAccountantId();
        if (accountantId == null) {
            // 会计身份异常时 fail-closed，绝不放行全量
            return List.of();
        }
        List<Long> companyIds = companyRepository.findIdsByAccountantId(accountantId);
        log.debug("[财税数据范围] 会计 userId={} 可访问主体 {} 个", accountantId, companyIds.size());
        return companyIds == null ? List.of() : companyIds;
    }

    /**
     * 获取当前登录用户 ID（会计隔离依据）。
     *
     * @return 用户 ID，未认证或格式非法时返回 null
     */
    public Long currentAccountantId() {
        String userId = dataScopeService.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            log.warn("[财税数据范围] 当前用户 ID 非法: {}", userId);
            return null;
        }
    }

    /**
     * 校验当前用户是否有权访问指定主体。
     * <p>
     * 用于详情类端点，避免会计通过直接拼 URL 越权读取他人主体数据。
     * </p>
     *
     * @param companyId 主体 ID
     * @return true = 有权访问
     */
    public boolean canAccessCompany(Long companyId) {
        if (companyId == null) {
            return false;
        }
        List<Long> accessible = resolveAccessibleCompanyIds();
        return accessible == null || accessible.contains(companyId);
    }
}
