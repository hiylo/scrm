/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : CompanyResolverTest.java
 * Date : 2026/10/23 20:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link CompanyResolver} 单元测试。
 * <p>
 * 锁定「业务数据自动挂公司」的三条语义：
 * <ul>
 *   <li>联系人已归属公司 → 解析出 companyId</li>
 *   <li>联系人未归属 / 入参为空 → 返回 null（不阻断业务创建）</li>
 *   <li>解析抛异常 → 降级返回 null（业务数据仍可落库，待归并后回填）</li>
 *   <li>显式传入 companyId 时以其为准，不被自动推导覆盖</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("公司归属解析 单元测试")
class CompanyResolverTest {

    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmCompanyRepository companyRepository;

    private CompanyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CompanyResolver(customerRepository, companyRepository);
    }

    @Test
    @DisplayName("联系人已归属公司：解析出 companyId")
    void resolveByCustomerId_linked() {
        when(customerRepository.findCompanyIdByCustomerId(5001L)).thenReturn(9001L);

        assertThat(resolver.resolveByCustomerId(5001L)).isEqualTo(9001L);
    }

    @Test
    @DisplayName("联系人未归属公司：返回 null")
    void resolveByCustomerId_notLinked() {
        when(customerRepository.findCompanyIdByCustomerId(5002L)).thenReturn(null);

        assertThat(resolver.resolveByCustomerId(5002L)).isNull();
    }

    @Test
    @DisplayName("入参为空：直接返回 null，不查库")
    void resolveByCustomerId_nullInput() {
        assertThat(resolver.resolveByCustomerId(null)).isNull();
    }

    @Test
    @DisplayName("解析异常：降级返回 null，不阻断业务创建")
    void resolveByCustomerId_error_degradesToNull() {
        when(customerRepository.findCompanyIdByCustomerId(anyLong()))
                .thenThrow(new RuntimeException("db down"));

        assertThat(resolver.resolveByCustomerId(5003L)).isNull();
    }

    @Test
    @DisplayName("显式 companyId 优先于自动推导")
    void resolve_explicitWins() {
        when(customerRepository.findCompanyIdByCustomerId(5001L)).thenReturn(9001L);

        assertThat(resolver.resolve(8888L, 5001L)).isEqualTo(8888L);
    }

    @Test
    @DisplayName("显式为空时回落到联系人所属公司")
    void resolve_fallbackToCustomer() {
        when(customerRepository.findCompanyIdByCustomerId(5001L)).thenReturn(9001L);

        assertThat(resolver.resolve(null, 5001L)).isEqualTo(9001L);
    }
}
