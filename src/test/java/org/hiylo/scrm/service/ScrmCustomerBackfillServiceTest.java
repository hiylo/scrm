/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCustomerBackfillServiceTest.java
 * Date : 2026/10/24 08:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmCustomerBackfillService} 单元测试。
 * <p>
 * 锁定存量资料补录的三条语义：
 * <ul>
 *   <li>手机号归一化：空格/连字符/括号/+86/0086 前缀统一剥离</li>
 *   <li>批量补录：格式非法、撞车、已存在同值 分别走「跳过/跳过/未变化」，不静默覆盖</li>
 *   <li>按手机号挂靠：按号码精确定位联系人，补录空缺手机号并挂公司，已归属该公司不重复挂</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("存量资料补录 单元测试")
class ScrmCustomerBackfillServiceTest {

    /** 归属账号 ID */
    private static final long OWNER_ID = 1001L;

    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmCompanyRepository companyRepository;

    private ScrmCustomerBackfillService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerBackfillService(customerRepository, companyRepository);
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("手机号归一化：空格/连字符/括号/+86/0086 统一为 11 位")
    void normalizePhone_variants() {
        assertThat(ScrmCustomerBackfillService.normalizePhone("138 0013 8000")).isEqualTo("13800138000");
        assertThat(ScrmCustomerBackfillService.normalizePhone("138-0013-8000")).isEqualTo("13800138000");
        assertThat(ScrmCustomerBackfillService.normalizePhone("+86 13800138000")).isEqualTo("13800138000");
        assertThat(ScrmCustomerBackfillService.normalizePhone("008613800138000")).isEqualTo("13800138000");
        assertThat(ScrmCustomerBackfillService.normalizePhone("（138）00138000")).isEqualTo("13800138000");
        assertThat(ScrmCustomerBackfillService.normalizePhone("  ")).isNull();
        assertThat(ScrmCustomerBackfillService.normalizePhone(null)).isNull();
    }

    @Test
    @DisplayName("批量补录：正常写入 + 跳过不存在/非法/撞车 + 同值计未变化")
    void backfillPhones_partialSuccess() throws ScrmException {
        ScrmCustomerEntity ok = customer(1L, null);
        ScrmCustomerEntity same = customer(2L, "13800138000");
        ScrmCustomerEntity occupiedByOther = customer(4L, null);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(ok));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(same));
        when(customerRepository.findById(3L)).thenReturn(Optional.empty());
        when(customerRepository.findById(4L)).thenReturn(Optional.of(occupiedByOther));
        ScrmCustomerEntity conflictTarget = customer(5L, null);
        when(customerRepository.findById(5L)).thenReturn(Optional.of(conflictTarget));
        // 3 号手机号已被 99 号占用
        when(customerRepository.findByOwnerAccountIdAndPhone(OWNER_ID, "13900139000"))
                .thenReturn(List.of(customer(99L, "13900139000")));

        List<ScrmCustomerBackfillService.PhoneBackfillItem> items = List.of(
                new ScrmCustomerBackfillService.PhoneBackfillItem(1L, "138 0013 8000"),
                new ScrmCustomerBackfillService.PhoneBackfillItem(2L, "13800138000"),
                new ScrmCustomerBackfillService.PhoneBackfillItem(3L, "13700137000"),
                new ScrmCustomerBackfillService.PhoneBackfillItem(4L, "12345"),
                new ScrmCustomerBackfillService.PhoneBackfillItem(5L, "13900139000"));

        Map<String, Object> result = service.backfillPhones(items);

        assertThat(result.get("requested")).isEqualTo(5);
        assertThat(result.get("updated")).isEqualTo(1);
        assertThat(result.get("unchanged")).isEqualTo(1);
        assertThat(ok.getPhone()).isEqualTo("13800138000");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(skipped).extracting(m -> m.get("reason"))
                .anyMatch(r -> String.valueOf(r).contains("联系人不存在"))
                .anyMatch(r -> String.valueOf(r).contains("手机号格式非法"))
                .anyMatch(r -> String.valueOf(r).contains("已被同账号下其他联系人占用"));
    }

    @Test
    @DisplayName("批量补录：空列表被拒绝")
    void backfillPhones_empty_rejected() {
        assertThatThrownBy(() -> service.backfillPhones(List.of()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("补录项不能为空");
    }

    @Test
    @DisplayName("按手机号挂靠：定位联系人 + 补录空缺手机号 + 挂公司")
    void bindByPhones_bindsAndFillsPhone() throws ScrmException {
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company(9001L)));
        ScrmCustomerEntity target = customer(1L, null);
        when(customerRepository.findByOwnerAccountIdAndPhone(OWNER_ID, "13800138000"))
                .thenReturn(List.of(target));

        Map<String, Object> result = service.bindByPhones(9001L,
                List.of(new ScrmCustomerBackfillService.PhoneBackfillItem(null, "138-0013-8000")), "BOSS");

        assertThat(result.get("bound")).isEqualTo(1);
        assertThat(result.get("phoneFilled")).isEqualTo(1);
        assertThat(target.getCompanyId()).isEqualTo(9001L);
        assertThat(target.getContactRole()).isEqualTo("BOSS");
        assertThat(target.getPhone()).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("按手机号挂靠：号码找不到联系人时跳过并说明")
    void bindByPhones_notFound() throws ScrmException {
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company(9001L)));
        when(customerRepository.findByOwnerAccountIdAndPhone(anyLong(), any()))
                .thenReturn(List.of());

        Map<String, Object> result = service.bindByPhones(9001L,
                List.of(new ScrmCustomerBackfillService.PhoneBackfillItem(null, "13800138000")), null);

        assertThat(result.get("bound")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(String.valueOf(skipped.get(0).get("reason"))).contains("找不到联系人");
    }

    @Test
    @DisplayName("按手机号挂靠：已归属该公司不重复计数")
    void bindByPhones_alreadyLinked() throws ScrmException {
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(company(9001L)));
        ScrmCustomerEntity target = customer(1L, "13800138000");
        target.setCompanyId(9001L);
        when(customerRepository.findByOwnerAccountIdAndPhone(OWNER_ID, "13800138000"))
                .thenReturn(List.of(target));

        Map<String, Object> result = service.bindByPhones(9001L,
                List.of(new ScrmCustomerBackfillService.PhoneBackfillItem(null, "13800138000")), null);

        assertThat(result.get("bound")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
        assertThat(String.valueOf(skipped.get(0).get("reason"))).contains("已归属该公司");
    }

    @Test
    @DisplayName("按手机号挂靠：公司不存在抛 404")
    void bindByPhones_companyNotFound() {
        when(companyRepository.findById(9001L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bindByPhones(9001L,
                List.of(new ScrmCustomerBackfillService.PhoneBackfillItem(1L, "13800138000")), null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公司主体不存在");
    }

    @Test
    @DisplayName("补录完成度统计：总数/有手机号/已归属/覆盖率")
    void backfillStatus_statistics() {
        ScrmCustomerEntity a = customer(1L, "13800138000");
        a.setCompanyId(9001L);
        ScrmCustomerEntity b = customer(2L, null);
        ScrmCustomerEntity c = customer(3L, "");
        when(customerRepository.findAll()).thenReturn(List.of(a, b, c));

        Map<String, Object> status = service.backfillStatus();

        assertThat(status.get("total")).isEqualTo(3L);
        assertThat(status.get("withPhone")).isEqualTo(1L);
        assertThat(status.get("phoneMissing")).isEqualTo(2L);
        assertThat(status.get("linked")).isEqualTo(1L);
        assertThat(status.get("unlinked")).isEqualTo(2L);
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造联系人实体。
     *
     * @param id    主键
     * @param phone 手机号
     * @return 实体
     */
    private ScrmCustomerEntity customer(Long id, String phone) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setOwnerAccountId(OWNER_ID);
        entity.setPhone(phone);
        entity.setLifecycle("NEW");
        return entity;
    }

    /**
     * 构造公司实体。
     *
     * @param id 主键
     * @return 实体
     */
    private ScrmCompanyEntity company(Long id) {
        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setId(id);
        entity.setOwnerAccountId(OWNER_ID);
        entity.setCompanyName("测试公司");
        entity.setCompanyType(ScrmCompanyEntity.TYPE_ENTERPRISE);
        entity.setServiceStatus(ScrmCompanyEntity.SERVICE_SERVING);
        return entity;
    }
}
