/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompanyServiceTest.java
 * Date : 2026/10/23 17:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCompanyDto;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLicenseOcrTaskRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmCompanyService} 单元测试。
 * <p>
 * 锁定公司主体域的核心语义：
 * <ul>
 *   <li>信用代码在同一归属内唯一（重复创建/更新到别人的代码 → 冲突）</li>
 *   <li>联系人只属一家公司；跨归属挂靠被拒绝；非法角色被拒绝</li>
 *   <li>删除主体前必须无联系人挂靠（避免联系人变孤儿）</li>
 *   <li>证照 OCR 结果可反哺建主体，且已存在时幂等复用</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("公司主体服务 单元测试")
class ScrmCompanyServiceTest {

    /** 归属账号 ID（与 UserContext 中设置的一致） */
    private static final long OWNER_ID = 1001L;

    @Mock
    private ScrmCompanyRepository companyRepository;

    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmLicenseOcrTaskRepository licenseOcrTaskRepository;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private org.hiylo.scrm.repository.ScrmUserRepository userRepository;
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;


    private ScrmCompanyService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCompanyService(companyRepository, customerRepository,
                licenseOcrTaskRepository, new ObjectMapper(), dataScopeService, financeDataScope, userRepository);
        UserContext.setUserId(String.valueOf(OWNER_ID));
        UserContext.setUsername("tester");
        when(dataScopeService.getAccessibleAccountIds(any(), any(), any()))
                .thenReturn(List.of(OWNER_ID));
        // 财税数据范围：默认不限制（null），且详情越权校验放行
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(null);
        when(financeDataScope.canAccessCompany(any())).thenReturn(true);
        when(dataScopeService.getCurrentUserId()).thenReturn(String.valueOf(OWNER_ID));
        when(dataScopeService.getCurrentRole()).thenReturn("ADMIN");
        when(dataScopeService.getCurrentDepartmentId()).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== 创建 ====================

    @Test
    @DisplayName("创建主体：正常写入并回填归属")
    void create_success() throws ScrmException {
        when(companyRepository.existsByOwnerAccountIdAndCreditCode(anyLong(), anyString()))
                .thenReturn(false);
        when(companyRepository.save(any(ScrmCompanyEntity.class))).thenAnswer(invocation -> {
            ScrmCompanyEntity e = invocation.getArgument(0);
            e.setId(9001L);
            return e;
        });

        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setCompanyName("上海云溪财税服务有限公司");
        dto.setCreditCode("91310115ma1k3wxy7x");
        dto.setLegalRepresentative("张明远");
        dto.setOwnerAccountId(OWNER_ID);

        ScrmCompanyDto created = service.create(dto);

        assertThat(created.getId()).isEqualTo(9001L);
        assertThat(created.getCompanyName()).isEqualTo("上海云溪财税服务有限公司");
        // 信用代码归一化：去空格 + 转大写
        assertThat(created.getCreditCode()).isEqualTo("91310115MA1K3WXY7X");
        assertThat(created.getOwnerAccountId()).isEqualTo(OWNER_ID);
        assertThat(created.getCompanyType()).isEqualTo(ScrmCompanyEntity.TYPE_ENTERPRISE);
        assertThat(created.getBusinessStatus()).isEqualTo(ScrmCompanyEntity.BUSINESS_ACTIVE);
    }

    @Test
    @DisplayName("创建主体：信用代码重复被拒绝（同归属内唯一）")
    void create_duplicateCreditCode_conflict() {
        when(companyRepository.existsByOwnerAccountIdAndCreditCode(anyLong(), anyString()))
                .thenReturn(true);

        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setCompanyName("重复公司");
        dto.setCreditCode("91310115MA1K3WXY7X");
        dto.setOwnerAccountId(OWNER_ID);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("主体已存在");
    }

    @Test
    @DisplayName("创建主体：企业名称为空被拒绝")
    void create_blankName_rejected() {
        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setCompanyName("  ");
        dto.setOwnerAccountId(OWNER_ID);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("企业名称不能为空");
    }

    @Test
    @DisplayName("创建主体：登录上下文缺失归属账号时 fail-closed")
    void create_noOwner_rejected() {
        UserContext.clear();
        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setCompanyName("无归属公司");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("归属账号 ID 不能为空");
    }

    @Test
    @DisplayName("创建主体：非法主体类型被拒绝")
    void create_invalidType_rejected() {
        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setCompanyName("某公司");
        dto.setCompanyType("LIMITED");
        dto.setOwnerAccountId(OWNER_ID);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("非法的主体类型");
    }

    // ==================== 更新 ====================

    @Test
    @DisplayName("更新主体：工商状态变化时刷新变更时间")
    void update_businessStatusChange_refreshesTime() throws ScrmException {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        entity.setBusinessStatus(ScrmCompanyEntity.BUSINESS_ACTIVE);
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(companyRepository.save(any(ScrmCompanyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmCompanyDto patch = new ScrmCompanyDto();
        patch.setBusinessStatus(ScrmCompanyEntity.BUSINESS_ABNORMAL);

        ScrmCompanyDto updated = service.update(9001L, patch);

        assertThat(updated.getBusinessStatus()).isEqualTo(ScrmCompanyEntity.BUSINESS_ABNORMAL);
        assertThat(updated.getBusinessStatusChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("更新主体：改成别人已占用的信用代码被拒绝")
    void update_creditCodeTaken_conflict() {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(companyRepository.existsByOwnerAccountIdAndCreditCode(anyLong(), anyString()))
                .thenReturn(true);

        ScrmCompanyDto patch = new ScrmCompanyDto();
        patch.setCreditCode("91310115MA1K3WXY7N");

        assertThatThrownBy(() -> service.update(9001L, patch))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("信用代码已被其他主体占用");
    }

    // ==================== 联系人归属 ====================

    @Test
    @DisplayName("联系人挂靠：设置 companyId 与公司内角色")
    void bindContact_success() throws ScrmException {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        ScrmCustomerEntity customer = customer(5001L, OWNER_ID);
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(customerRepository.findById(5001L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.bindContact(9001L, 5001L, "FINANCE");

        assertThat(result.getCompanyId()).isEqualTo(9001L);
        assertThat(result.getContactRole()).isEqualTo("FINANCE");
    }

    @Test
    @DisplayName("联系人挂靠：跨归属账号被拒绝")
    void bindContact_crossOwner_forbidden() {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        ScrmCustomerEntity customer = customer(5001L, 9999L);
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(customerRepository.findById(5001L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.bindContact(9001L, 5001L, "BOSS"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不属于同一归属账号");
    }

    @Test
    @DisplayName("联系人挂靠：非法角色被拒绝")
    void bindContact_invalidRole_rejected() {
        assertThatThrownBy(() -> service.bindContact(9001L, 5001L, "CEO"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("非法的公司内角色");
    }

    @Test
    @DisplayName("解除挂靠：清空 companyId 与角色")
    void unbindContact_clearsFields() throws ScrmException {
        ScrmCustomerEntity customer = customer(5001L, OWNER_ID);
        customer.setCompanyId(9001L);
        customer.setContactRole("BOSS");
        when(customerRepository.findById(5001L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.unbindContact(5001L);

        assertThat(result.getCompanyId()).isNull();
        assertThat(result.getContactRole()).isNull();
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除主体：仍有联系人挂靠时拒绝")
    void delete_withContacts_rejected() {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(customerRepository.countByCompanyId(9001L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(9001L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 2 个联系人");
    }

    @Test
    @DisplayName("删除主体：无联系人时成功")
    void delete_withoutContacts_success() throws ScrmException {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        when(customerRepository.countByCompanyId(9001L)).thenReturn(0L);

        service.delete(9001L);

        org.mockito.Mockito.verify(companyRepository).delete(entity);
    }

    // ==================== 证照反哺 ====================

    @Test
    @DisplayName("证照反哺：营业执照识别结果创建主体并挂联系人")
    void createFromLicenseTask_success() throws ScrmException {
        ScrmLicenseOcrTaskEntity task = new ScrmLicenseOcrTaskEntity();
        task.setId(7001L);
        task.setLicenseType("license");
        task.setOwnerAccountId(OWNER_ID);
        task.setCustomerId(5001L);
        task.setOverallOk(true);
        task.setFieldsJson("[{\"key\":\"enterprise_name\",\"name\":\"企业名称\",\"value\":\"上海云溪财税服务有限公司\"},"
                + "{\"key\":\"credit_code\",\"name\":\"统一社会信用代码\",\"value\":\"91310115MA1K3WXY7X\"},"
                + "{\"key\":\"legal_representative\",\"name\":\"法定代表人\",\"value\":\"张明远\"},"
                + "{\"key\":\"establish_date\",\"name\":\"成立日期\",\"value\":\"2019年03月15日\"}]");
        when(licenseOcrTaskRepository.findById(7001L)).thenReturn(Optional.of(task));
        ScrmCustomerEntity taskCustomer = customer(5001L, OWNER_ID);
        when(customerRepository.findById(5001L)).thenReturn(Optional.of(taskCustomer));
        when(companyRepository.findByOwnerAccountIdAndCreditCode(anyLong(), anyString()))
                .thenReturn(Optional.empty());
        when(companyRepository.save(any(ScrmCompanyEntity.class))).thenAnswer(invocation -> {
            ScrmCompanyEntity e = invocation.getArgument(0);
            e.setId(9002L);
            return e;
        });
        ScrmCustomerEntity customer = customer(5001L, OWNER_ID);
        when(customerRepository.findById(5001L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmCompanyDto created = service.createFromLicenseTask(7001L);

        assertThat(created.getId()).isEqualTo(9002L);
        assertThat(created.getCompanyName()).isEqualTo("上海云溪财税服务有限公司");
        assertThat(created.getCreditCode()).isEqualTo("91310115MA1K3WXY7X");
        assertThat(created.getLegalRepresentative()).isEqualTo("张明远");
        assertThat(created.getEstablishDate()).isEqualTo(java.time.LocalDate.of(2019, 3, 15));
        assertThat(created.getSource()).isEqualTo(ScrmCompanyEntity.SOURCE_OCR);
        assertThat(created.getVerified()).isTrue();
        // 任务回填 companyId
        assertThat(task.getCompanyId()).isEqualTo(9002L);
        // 联系人自动挂靠（默认 BOSS）
        assertThat(customer.getCompanyId()).isEqualTo(9002L);
        assertThat(customer.getContactRole()).isEqualTo("BOSS");
    }

    @Test
    @DisplayName("证照反哺：信用代码已存在时幂等复用，不重复建主体")
    void createFromLicenseTask_existing_idempotent() throws ScrmException {
        ScrmLicenseOcrTaskEntity task = new ScrmLicenseOcrTaskEntity();
        task.setId(7002L);
        task.setLicenseType("license");
        task.setOwnerAccountId(OWNER_ID);
        task.setCustomerId(5002L);
        task.setFieldsJson("[{\"key\":\"enterprise_name\",\"name\":\"企业名称\",\"value\":\"上海云溪财税服务有限公司\"},"
                + "{\"key\":\"credit_code\",\"name\":\"统一社会信用代码\",\"value\":\"91310115MA1K3WXY7X\"}]");
        when(licenseOcrTaskRepository.findById(7002L)).thenReturn(Optional.of(task));
        ScrmCustomerEntity taskCustomer = customer(5002L, OWNER_ID);
        when(customerRepository.findById(5002L)).thenReturn(Optional.of(taskCustomer));
        ScrmCompanyEntity existing = company(9003L, "91310115MA1K3WXY7X");
        when(companyRepository.findByOwnerAccountIdAndCreditCode(anyLong(), anyString()))
                .thenReturn(Optional.of(existing));
        when(licenseOcrTaskRepository.save(any(ScrmLicenseOcrTaskEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.save(any(ScrmCustomerEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScrmCompanyDto result = service.createFromLicenseTask(7002L);

        assertThat(result.getId()).isEqualTo(9003L);
        assertThat(task.getCompanyId()).isEqualTo(9003L);
        org.mockito.Mockito.verify(companyRepository, org.mockito.Mockito.never())
                .save(any(ScrmCompanyEntity.class));
    }

    @Test
    @DisplayName("证照反哺：非营业执照任务被拒绝")
    void createFromLicenseTask_wrongType_rejected() {
        ScrmLicenseOcrTaskEntity task = new ScrmLicenseOcrTaskEntity();
        task.setId(7003L);
        task.setLicenseType("idcard");
        when(licenseOcrTaskRepository.findById(7003L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.createFromLicenseTask(7003L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅营业执照识别任务");
    }

    @Test
    @DisplayName("证照反哺：缺少企业名称时拒绝（不建空主体）")
    void createFromLicenseTask_missingName_rejected() {
        ScrmLicenseOcrTaskEntity task = new ScrmLicenseOcrTaskEntity();
        task.setId(7004L);
        task.setLicenseType("license");
        task.setOwnerAccountId(OWNER_ID);
        task.setFieldsJson("[{\"key\":\"credit_code\",\"name\":\"统一社会信用代码\",\"value\":\"91310115MA1K3WXY7X\"}]");
        when(licenseOcrTaskRepository.findById(7004L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.createFromLicenseTask(7004L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("缺少企业名称");
    }

    // ==================== 详情聚合 ====================

    @Test
    @DisplayName("详情：返回联系人列表与数量")
    void get_withContacts() throws ScrmException {
        ScrmCompanyEntity entity = company(9001L, "91310115MA1K3WXY7X");
        when(companyRepository.findById(9001L)).thenReturn(Optional.of(entity));
        ScrmCustomerEntity boss = customer(5001L, OWNER_ID);
        boss.setCompanyId(9001L);
        boss.setContactRole("BOSS");
        ScrmCustomerEntity finance = customer(5002L, OWNER_ID);
        finance.setCompanyId(9001L);
        finance.setContactRole("FINANCE");
        when(customerRepository.findByOwnerAccountIdAndCompanyId(OWNER_ID, 9001L))
                .thenReturn(List.of(boss, finance));

        ScrmCompanyDto detail = service.get(9001L);

        assertThat(detail.getContacts()).hasSize(2);
        assertThat(detail.getContactCount()).isEqualTo(2L);
        assertThat(detail.getContacts()).extracting("contactRole")
                .containsExactly("BOSS", "FINANCE");
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造公司主体实体。
     *
     * @param id         主键
     * @param creditCode 信用代码
     * @return 实体
     */
    private ScrmCompanyEntity company(Long id, String creditCode) {
        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setId(id);
        entity.setOwnerAccountId(OWNER_ID);
        entity.setCompanyName("上海云溪财税服务有限公司");
        entity.setCreditCode(creditCode);
        entity.setCompanyType(ScrmCompanyEntity.TYPE_ENTERPRISE);
        entity.setBusinessStatus(ScrmCompanyEntity.BUSINESS_ACTIVE);
        entity.setTaxStatus(ScrmCompanyEntity.TAX_NORMAL);
        entity.setServiceStatus(ScrmCompanyEntity.SERVICE_PENDING);
        return entity;
    }

    /**
     * 构造联系人实体。
     *
     * @param id             主键
     * @param ownerAccountId 归属账号
     * @return 实体
     */
    private ScrmCustomerEntity customer(Long id, Long ownerAccountId) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setOwnerAccountId(ownerAccountId);
        entity.setNickname("陈静雯");
        entity.setLifecycle("NEW");
        return entity;
    }

    @Test
    @DisplayName("详情：会计访问非自己负责的主体被拒绝（403 越权防护）")
    void get_byAccountantOnForeignCompany_forbidden() throws ScrmException {
        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setId(9001L);
        entity.setCompanyName("他人主体");
        when(companyRepository.findById(9001L)).thenReturn(java.util.Optional.of(entity));
        // 会计的数据范围只含 100L，不含 9001L
        when(financeDataScope.canAccessCompany(9001L)).thenReturn(false);

        assertThatThrownBy(() -> service.get(9001L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该主体");
    }

    @Test
    @DisplayName("列表：会计范围为空时不回退全量（fail-closed）")
    void list_byAccountantWithNoCompanies_returnsEmptySpec() {
        // 会计未绑定任何主体 → 空集合，绝不能变成「不过滤」
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(java.util.List.of());
        when(companyRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        org.springframework.data.domain.Page<ScrmCompanyDto> page =
                service.list(null, null, null, 0, 20);

        assertThat(page.getTotalElements()).isZero();
        verify(companyRepository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }
}
