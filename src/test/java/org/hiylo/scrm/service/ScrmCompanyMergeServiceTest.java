/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCompanyMergeServiceTest.java
 * Date : 2026/10/23 22:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmCompanyMergeService} 单元测试。
 * <p>
 * 锁定存量归并的三条语义：
 * <ul>
 *   <li>待归并清单附带信号摘要（有手机号 / 昵称含企业特征词 / 无信号）</li>
 *   <li>候选匹配保守：命中才给候选并带理由，无命中返回空（不编造）</li>
 *   <li>批量挂靠：跳过不存在/跨归属/已归属，其余正常挂靠</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("存量联系人归并 单元测试")
class ScrmCompanyMergeServiceTest {

    /** 归属账号 ID */
    private static final long OWNER_ID = 1001L;

    @Mock
    private ScrmCompanyRepository companyRepository;

    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private DataScopeService dataScopeService;

    private ScrmCompanyMergeService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCompanyMergeService(companyRepository, customerRepository, dataScopeService);
        when(dataScopeService.getAccessibleAccountIds(any(), any(), any())).thenReturn(List.of(OWNER_ID));
        when(dataScopeService.getCurrentUserId()).thenReturn(String.valueOf(OWNER_ID));
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.getCurrentDepartmentId()).thenReturn(null);
        UserContext.setUserId(String.valueOf(OWNER_ID));
        UserContext.setUsername("tester");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== 待归并清单 ====================

    @Test
    @DisplayName("待归并清单：附带信号摘要（手机号 / 企业特征词 / 无信号）")
    void listUnlinked_withSignals() {
        ScrmCustomerEntity withPhone = customer(1L, "王老板", "13800138000");
        ScrmCustomerEntity withHint = customer(2L, "A定昌电子-罗贤东（物联网主板）", null);
        ScrmCustomerEntity nothing = customer(3L, "明天", null);
        Page<ScrmCustomerEntity> page = new PageImpl<>(List.of(withPhone, withHint, nothing));
        when(customerRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(page);

        Page<Map<String, Object>> result = service.listUnlinked(null, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(3);
        List<Map<String, Object>> items = result.getContent();
        assertThat(signals(items.get(0))).contains("有手机号");
        assertThat(signals(items.get(1))).anyMatch(s -> s.contains("昵称含企业特征词"));
        assertThat(signals(items.get(2))).anyMatch(s -> s.contains("无可用于判断归属的信号"));
    }

    // ==================== 候选建议 ====================

    @Test
    @DisplayName("为公司推荐联系人：昵称命中公司名关键词")
    void suggestContacts_hit() {
        ScrmCompanyEntity company = company(9001L, "上海启明财务咨询有限公司");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company));
        ScrmCustomerEntity hit = customer(1L, "启明财务-王会计", null);
        when(customerRepository.findByOwnerAccountIdAndNicknameLike(anyLong(), anyString()))
                .thenReturn(List.of(hit));

        List<Map<String, Object>> result = service.suggestContacts(9001L, 20);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).get("customerId")).isEqualTo(1L);
        assertThat(String.valueOf(result.get(0).get("reason"))).contains("昵称包含公司名关键词");
    }

    @Test
    @DisplayName("为公司推荐联系人：剥掉城市前缀后能命中存量昵称（上海定昌电子 → A定昌电子-罗贤东）")
    void suggestContacts_stripCityPrefix_hitRealNickname() {
        ScrmCompanyEntity company = company(9001L, "上海定昌电子有限公司");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company));
        ScrmCustomerEntity hit = customer(366599420319502336L, "A定昌电子-罗贤东（物联网主板）", null);
        when(customerRepository.findByOwnerAccountIdAndNicknameLike(anyLong(), anyString()))
                .thenAnswer(inv -> {
                    String pattern = ((String) inv.getArgument(1)).replace("%", "");
                    return hit.getNickname().contains(pattern) ? List.of(hit) : List.of();
                });

        List<Map<String, Object>> result = service.suggestContacts(9001L, 20);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).get("customerId")).isEqualTo(366599420319502336L);
    }

    @Test
    @DisplayName("为公司推荐联系人：公司名全是通用词时不给候选（不编造）")
    void suggestContacts_noUsableKeyword_returnsEmpty() {
        ScrmCompanyEntity company = company(9001L, "上海有限公司");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company));

        assertThat(service.suggestContacts(9001L, 20)).isEmpty();
    }

    @Test
    @DisplayName("公司不存在：推荐联系人抛 404")
    void suggestContacts_companyNotFound() {
        when(companyRepository.findById(9001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suggestContacts(9001L, 20))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公司主体不存在");
    }

    @Test
    @DisplayName("为联系人推荐公司：昵称关键词命中公司名")
    void suggestCompanies_hit() {
        ScrmCustomerEntity customer = customer(1L, "启明财务-王会计", null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(companyRepository.findByOwnerAccountIdAndCompanyNameContainingIgnoreCase(
                anyLong(), anyString())).thenReturn(List.of(company(9001L, "上海启明财务咨询有限公司")));

        List<Map<String, Object>> result = service.suggestCompanies(1L, 20);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).get("companyId")).isEqualTo(9001L);
        assertThat(String.valueOf(result.get(0).get("reason"))).contains("公司名包含");
    }

    @Test
    @DisplayName("为联系人推荐公司：剥掉微信字母前缀后能命中（A定昌电子-罗贤东 → 上海定昌电子有限公司）")
    void suggestCompanies_stripLatinPrefix_hit() {
        ScrmCompanyEntity company = company(9001L, "上海定昌电子有限公司");
        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer(1L, "A定昌电子-罗贤东（物联网主板）", null)));
        when(companyRepository.findByOwnerAccountIdAndCompanyNameContainingIgnoreCase(
                anyLong(), anyString())).thenAnswer(inv -> {
                    String kw = (String) inv.getArgument(1);
                    return company.getCompanyName().contains(kw) ? List.of(company) : List.of();
                });

        List<Map<String, Object>> result = service.suggestCompanies(1L, 20);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).get("companyId")).isEqualTo(9001L);
    }

    @Test
    @DisplayName("为联系人推荐公司：昵称为空时返回空")
    void suggestCompanies_blankNickname_returnsEmpty() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, null, null)));

        assertThat(service.suggestCompanies(1L, 20)).isEmpty();
    }

    // ==================== 批量挂靠 ====================

    @Test
    @DisplayName("批量挂靠：正常挂靠 + 跳过不存在/跨归属/已归属")
    void bulkBind_partialSuccess() throws ScrmException {
        ScrmCompanyEntity company = company(9001L, "上海启明财务咨询有限公司");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company));

        ScrmCustomerEntity ok1 = customer(1L, "甲", null);
        ScrmCustomerEntity ok2 = customer(2L, "乙", null);
        ScrmCustomerEntity crossOwner = customer(3L, "丙", null);
        crossOwner.setOwnerAccountId(9999L);
        ScrmCustomerEntity already = customer(4L, "丁", null);
        already.setCompanyId(9001L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(ok1));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(ok2));
        when(customerRepository.findById(3L)).thenReturn(Optional.of(crossOwner));
        when(customerRepository.findById(4L)).thenReturn(Optional.of(already));
        when(customerRepository.findById(5L)).thenReturn(Optional.empty());
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.bulkBind(9001L, List.of(1L, 2L, 3L, 4L, 5L), "BOSS");

        assertThat(result.get("requested")).isEqualTo(5);
        assertThat(result.get("bound")).isEqualTo(2);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(skipped).hasSize(3);
        assertThat(skipped).extracting(m -> m.get("reason"))
                .containsExactly("归属账号不一致", "已归属该公司", "联系人不存在");
        // 成功挂靠的带上了角色
        assertThat(ok1.getCompanyId()).isEqualTo(9001L);
        assertThat(ok1.getContactRole()).isEqualTo("BOSS");
    }

    @Test
    @DisplayName("批量挂靠：空列表被拒绝")
    void bulkBind_emptyList_rejected() {
        assertThatThrownBy(() -> service.bulkBind(9001L, List.of(), "BOSS"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("联系人 ID 列表不能为空");
    }

    @Test
    @DisplayName("批量挂靠：公司不存在抛 404")
    void bulkBind_companyNotFound() {
        when(companyRepository.findById(9001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bulkBind(9001L, List.of(1L), "BOSS"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公司主体不存在");
    }

    @Test
    @DisplayName("批量挂靠：不传角色时保留联系人原角色")
    void bulkBind_noRole_keepsExistingRole() throws ScrmException {
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company(9001L, "某公司")));
        ScrmCustomerEntity customer = customer(1L, "甲", null);
        customer.setContactRole("FINANCE");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.bulkBind(9001L, List.of(1L), null);

        assertThat(customer.getContactRole()).isEqualTo("FINANCE");
    }

    // ==================== 测试夹具 ====================

    /**
     * 取信号列表。
     *
     * @param item 清单项
     * @return 信号列表
     */
    @SuppressWarnings("unchecked")
    private List<String> signals(Map<String, Object> item) {
        return (List<String>) item.get("signals");
    }

    /**
     * 构造公司实体。
     *
     * @param id   主键
     * @param name 企业名称
     * @return 实体
     */
    private ScrmCompanyEntity company(Long id, String name) {
        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setId(id);
        entity.setOwnerAccountId(OWNER_ID);
        entity.setCompanyName(name);
        entity.setCompanyType(ScrmCompanyEntity.TYPE_ENTERPRISE);
        entity.setServiceStatus(ScrmCompanyEntity.SERVICE_SERVING);
        return entity;
    }

    /**
     * 构造联系人实体。
     *
     * @param id       主键
     * @param nickname 昵称
     * @param phone    手机号
     * @return 实体
     */
    private ScrmCustomerEntity customer(Long id, String nickname, String phone) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setOwnerAccountId(OWNER_ID);
        entity.setNickname(nickname);
        entity.setPhone(phone);
        entity.setLifecycle("NEW");
        return entity;
    }
}
