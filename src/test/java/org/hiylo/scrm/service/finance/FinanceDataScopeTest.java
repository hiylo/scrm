/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : FinanceDataScopeTest.java
 * Description : 财税数据范围的角色映射与 fail-closed 行为测试
 */

package org.hiylo.scrm.service.finance;

import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.service.DataScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 财税数据范围单元测试。
 * <p>
 * 覆盖角色映射（管理全量 / 会计受限 / 其他角色不接管）与 fail-closed
 * （会计身份异常时返回空而非全量）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("财税数据范围：角色映射与 fail-closed")
class FinanceDataScopeTest {

    /** 会计用户 ID */
    private static final long ACCOUNTANT_ID = 5001L;

    @Mock
    private ScrmCompanyRepository companyRepository;

    @Mock
    private DataScopeService dataScopeService;

    private FinanceDataScope scope;

    @BeforeEach
    void setUp() {
        scope = new FinanceDataScope(companyRepository, dataScopeService);
        when(dataScopeService.getCurrentUserId()).thenReturn(String.valueOf(ACCOUNTANT_ID));
    }

    @Test
    @DisplayName("ADMIN / MANAGER / SUPERVISOR 均视为管理，返回 null 表示不限制")
    void managementRolesSeeAll() {
        for (String role : List.of("ADMIN", "MANAGER", "SUPERVISOR", "SUPERADMIN")) {
            when(dataScopeService.getCurrentRole()).thenReturn(role);
            assertThat(scope.resolveAccessibleCompanyIds())
                    .as("角色 %s 应为管理视角", role)
                    .isNull();
            assertThat(scope.isManagement(role)).isTrue();
        }
        // 管理视角不应触达公司仓储查询
        verify(companyRepository, never()).findIdsByAccountantId(anyLong());
    }

    @Test
    @DisplayName("ACCOUNTANT 只返回自己负责的主体 ID")
    void accountantSeesOwnCompanies() {
        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(companyRepository.findIdsByAccountantId(ACCOUNTANT_ID))
                .thenReturn(List.of(10L, 20L));

        assertThat(scope.resolveAccessibleCompanyIds()).containsExactly(10L, 20L);
        assertThat(scope.appliesTo("ACCOUNTANT")).isTrue();
    }

    @Test
    @DisplayName("ACCOUNTANT 未绑定任何主体时返回空集合，绝不回退全量")
    void accountantWithoutCompaniesGetsEmpty() {
        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(companyRepository.findIdsByAccountantId(ACCOUNTANT_ID)).thenReturn(List.of());

        assertThat(scope.resolveAccessibleCompanyIds()).isEmpty();
    }

    @Test
    @DisplayName("会计角色但用户 ID 非法时 fail-closed 返回空集合")
    void invalidUserIdFailsClosed() {
        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(dataScopeService.getCurrentUserId()).thenReturn("not-a-number");

        assertThat(scope.resolveAccessibleCompanyIds()).isEmpty();
        verify(companyRepository, never()).findIdsByAccountantId(anyLong());
    }

    @Test
    @DisplayName("会计角色但无登录上下文时 fail-closed 返回空集合")
    void missingUserIdFailsClosed() {
        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(dataScopeService.getCurrentUserId()).thenReturn(null);

        assertThat(scope.resolveAccessibleCompanyIds()).isEmpty();
    }

    @Test
    @DisplayName("SALES / OPERATOR / VIEWER 不由本类接管，返回 null 保持原逻辑")
    void legacyRolesAreNotOwnedByThisScope() {
        for (String role : List.of("SALES", "OPERATOR", "VIEWER")) {
            when(dataScopeService.getCurrentRole()).thenReturn(role);
            assertThat(scope.resolveAccessibleCompanyIds())
                    .as("角色 %s 应保持原有 SCRM 数据范围", role)
                    .isNull();
            assertThat(scope.appliesTo(role)).isFalse();
        }
    }

    @Test
    @DisplayName("会计 + 管理双角色时以管理优先（不降级为受限）")
    void managementWinsOverAccountant() {
        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT,MANAGER");

        assertThat(scope.isManagement("ACCOUNTANT,MANAGER")).isTrue();
        assertThat(scope.appliesTo("ACCOUNTANT,MANAGER")).isFalse();
        assertThat(scope.resolveAccessibleCompanyIds()).isNull();
    }

    @Test
    @DisplayName("canAccessCompany：管理放行任意主体，会计仅在白名单内放行")
    void canAccessCompanyRespectsScope() {
        when(dataScopeService.getCurrentRole()).thenReturn("MANAGER");
        assertThat(scope.canAccessCompany(999L)).isTrue();

        when(dataScopeService.getCurrentRole()).thenReturn("ACCOUNTANT");
        when(companyRepository.findIdsByAccountantId(ACCOUNTANT_ID)).thenReturn(List.of(10L));
        assertThat(scope.canAccessCompany(10L)).isTrue();
        assertThat(scope.canAccessCompany(999L)).isFalse();
        assertThat(scope.canAccessCompany(null)).isFalse();
    }
}
