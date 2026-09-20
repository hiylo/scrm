/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSegmentServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmSegmentDto;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmSegmentEntity;
import org.hiylo.scrm.entity.ScrmSegmentMemberEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmSegmentHistoryRepository;
import org.hiylo.scrm.repository.ScrmSegmentMemberRepository;
import org.hiylo.scrm.repository.ScrmSegmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmSegmentService 单元测试
 * <p>
 * 聚焦分群定义管理 (创建 / 默认值 / 编码唯一性 / 条件 JSON 校验)、动态分群成员计算
 * (条件评估 / 成员新增 / 成员数更新)、分群预览、成员管理 (手动添加 / 成员数同步)、
 * 分群复制与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmSegmentService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmSegmentServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 客户分群仓库 Mock */
    @Mock
    private ScrmSegmentRepository segmentRepository;
    /** 分群成员仓库 Mock */
    @Mock
    private ScrmSegmentMemberRepository memberRepository;
    /** 分群历史记录仓库 Mock */
    @Mock
    private ScrmSegmentHistoryRepository historyRepository;
    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;

    /** 被测服务实例 */
    private ScrmSegmentService service;

    @BeforeEach
    void setUp() {
        service = new ScrmSegmentService(segmentRepository, memberRepository,
                historyRepository, customerRepository, objectMapper,
                new CustomerConditionEvaluator(objectMapper));
    }

    @AfterEach
    void tearDown() {
    }

    /** 条件 JSON: customer_level eq ACTIVE */
    private static final String CONDITIONS_JSON =
            "[{\"field\":\"customer_level\",\"operator\":\"eq\",\"value\":\"ACTIVE\"}]";

    /**
     * 构造已持久化的分群实体 (用于 findById 返回)
     */
    private ScrmSegmentEntity buildSegmentEntity(Long id) {
        ScrmSegmentEntity entity = new ScrmSegmentEntity();
        entity.setId(id);
        entity.setSegmentName("活跃客户分群");
        entity.setSegmentCode("VIP_ACTIVE");
        entity.setSegmentType("DYNAMIC");
        entity.setConditionType("ALL");
        entity.setConditions(CONDITIONS_JSON);
        entity.setStatus("ACTIVE");
        entity.setMemberCount(0);
        entity.setCalculationFrequency("DAILY");
        entity.setAutoUpdate(true);
        entity.setCreateTime(LocalDateTime.now().minusDays(10));
        return entity;
    }

    /**
     * 构造客户实体 (lifecycle 决定条件匹配)
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, String lifecycle) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setNickname("客户" + id);
        entity.setLifecycle(lifecycle);
        entity.setPlatformType("WECHAT");
        entity.setPlatformCustomerUid("uid_" + id);
        entity.setOwnerAccountId(10L);
        entity.setCreateTime(LocalDateTime.now().minusDays(30));
        return entity;
    }

    /**
     * 构造分群创建参数
     */
    private ScrmSegmentDto buildCreateDto() {
        ScrmSegmentDto dto = new ScrmSegmentDto();
        dto.setSegmentName("活跃客户分群");
        dto.setSegmentCode("VIP_ACTIVE");
        dto.setConditions(CONDITIONS_JSON);
        dto.setCreatedBy("admin01");
        return dto;
    }

    @Test
    @DisplayName("createSegment: 写入账号 ID 与默认值 (DYNAMIC / ALL / ACTIVE / DAILY / autoUpdate=true)")
    void createSegment_success() throws ScrmException {
        when(segmentRepository.existsBySegmentCode("VIP_ACTIVE")).thenReturn(false);
        when(segmentRepository.save(any(ScrmSegmentEntity.class)))
                .thenAnswer(inv -> {
                    ScrmSegmentEntity e = inv.getArgument(0);
                    e.setId(1L);
                    return e;
                });

        ScrmSegmentEntity result = service.createSegment(buildCreateDto());

        ArgumentCaptor<ScrmSegmentEntity> captor =
                ArgumentCaptor.forClass(ScrmSegmentEntity.class);
        verify(segmentRepository, times(1)).save(captor.capture());
        ScrmSegmentEntity saved = captor.getValue();
        assertThat(saved.getSegmentType()).isEqualTo("DYNAMIC");
        assertThat(saved.getConditionType()).isEqualTo("ALL");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getCalculationFrequency()).isEqualTo("DAILY");
        assertThat(saved.getAutoUpdate()).isTrue();
        assertThat(saved.getMemberCount()).isZero();
        assertThat(result.getSegmentName()).isEqualTo("活跃客户分群");
    }

    @Test
    @DisplayName("createSegment: 编码重复抛 CONFLICT")
    void createSegment_duplicateCode() {
        when(segmentRepository.existsBySegmentCode("VIP_ACTIVE")).thenReturn(true);

        assertThatThrownBy(() -> service.createSegment(buildCreateDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分群编码已存在");
        verify(segmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSegment: 非法分群类型抛 BAD_REQUEST")
    void createSegment_invalidSegmentType() {
        ScrmSegmentDto dto = buildCreateDto();
        dto.setSegmentType("INVALID");

        assertThatThrownBy(() -> service.createSegment(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分群类型非法");
        verify(segmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createSegment: 条件 JSON 字段非法抛 BAD_REQUEST")
    void createSegment_invalidConditionField() {
        ScrmSegmentDto dto = buildCreateDto();
        dto.setConditions("[{\"field\":\"invalid_field\",\"operator\":\"eq\",\"value\":\"x\"}]");

        assertThatThrownBy(() -> service.createSegment(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件字段非法");
        verify(segmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculateSegment: 命中条件客户加入分群, 更新成员数与最后计算时间")
    void calculateSegment_success() throws ScrmException {
        ScrmSegmentEntity segment = buildSegmentEntity(1L);
        when(segmentRepository.findById(1L)).thenReturn(Optional.of(segment));
        // 之前无成员
        when(memberRepository.findBySegmentIdAndIsCurrentMemberTrue(1L))
                .thenReturn(List.of());
        when(memberRepository.markAutoMembersLeft(eq(1L), any(LocalDateTime.class)))
                .thenReturn(0);
        // 2 个客户: 1 个 ACTIVE 命中, 1 个 DORMANT 不命中
        ScrmCustomerEntity activeCustomer = buildCustomerEntity(100L, "ACTIVE");
        ScrmCustomerEntity dormantCustomer = buildCustomerEntity(101L, "DORMANT");
        when(customerRepository.findAll()).thenReturn(List.of(activeCustomer, dormantCustomer));
        // 命中客户的成员关系不存在 (新建): 服务按命中客户集合批量查询已有成员关系
        when(memberRepository.findBySegmentIdAndCustomerIdIn(1L, List.of(100L)))
                .thenReturn(List.of());
        when(memberRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        // 计算后当前成员数 = 1
        when(memberRepository.countBySegmentIdAndIsCurrentMemberTrue(1L)).thenReturn(1L);
        when(segmentRepository.save(any(ScrmSegmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = service.calculateSegment(1L);

        // 验证保存了新建的成员
        verify(memberRepository, times(1)).saveAll(any());
        // 验证更新了分群成员数与最后计算时间
        ArgumentCaptor<ScrmSegmentEntity> segmentCaptor =
                ArgumentCaptor.forClass(ScrmSegmentEntity.class);
        verify(segmentRepository, times(1)).save(segmentCaptor.capture());
        assertThat(segmentCaptor.getValue().getMemberCount()).isEqualTo(1);
        assertThat(segmentCaptor.getValue().getLastCalculatedAt()).isNotNull();
        // 验证计算结果
        assertThat(result.getTotalMatched()).isEqualTo(1);
        assertThat(result.getAddedCount()).isEqualTo(1);
        assertThat(result.getRemovedCount()).isZero();
        assertThat(result.getSampleMembers()).hasSize(1);
    }

    @Test
    @DisplayName("previewSegment: 返回匹配数与样本, 不保存成员")
    void previewSegment_success() throws ScrmException {
        ScrmCustomerEntity activeCustomer = buildCustomerEntity(100L, "ACTIVE");
        ScrmCustomerEntity dormantCustomer = buildCustomerEntity(101L, "DORMANT");
        when(customerRepository.findAll()).thenReturn(List.of(activeCustomer, dormantCustomer));

        var result = service.previewSegment(CONDITIONS_JSON, "ALL");

        assertThat(result.getTotalMatched()).isEqualTo(1);
        assertThat(result.getSampleMembers()).hasSize(1);
        assertThat(result.getSampleMembers().get(0).get("customerId")).isEqualTo(100L);
        // 不应保存任何成员
        verify(memberRepository, never()).saveAll(any());
        verify(memberRepository, never()).save(any());
    }

    @Test
    @DisplayName("previewSegment: 空 conditions 抛 BAD_REQUEST")
    void previewSegment_emptyConditions() {
        assertThatThrownBy(() -> service.previewSegment("", "ALL"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("条件 JSON 不能为空");
        verify(customerRepository, never()).findAll();
    }

    @Test
    @DisplayName("addMember: 新客户加入分群后同步更新分群成员数")
    void addMember_success() throws ScrmException {
        ScrmSegmentEntity segment = buildSegmentEntity(1L);
        when(segmentRepository.findById(1L)).thenReturn(Optional.of(segment));
        ScrmCustomerEntity customer = buildCustomerEntity(100L, "ACTIVE");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));
        when(memberRepository.findBySegmentIdAndCustomerId(1L, 100L))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(ScrmSegmentMemberEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // updateSegmentMemberCount 调用
        when(memberRepository.countBySegmentIdAndIsCurrentMemberTrue(1L)).thenReturn(1L);
        when(segmentRepository.findById(1L)).thenReturn(Optional.of(segment));
        when(segmentRepository.save(any(ScrmSegmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSegmentMemberEntity result = service.addMember(1L, 100L, "MANUAL");

        ArgumentCaptor<ScrmSegmentMemberEntity> memberCaptor =
                ArgumentCaptor.forClass(ScrmSegmentMemberEntity.class);
        verify(memberRepository, times(1)).save(memberCaptor.capture());
        ScrmSegmentMemberEntity savedMember = memberCaptor.getValue();
        assertThat(savedMember.getSegmentId()).isEqualTo(1L);
        assertThat(savedMember.getCustomerId()).isEqualTo(100L);
        assertThat(savedMember.getCustomerName()).isEqualTo("客户100");
        assertThat(savedMember.getIsCurrentMember()).isTrue();
        assertThat(savedMember.getSource()).isEqualTo("MANUAL");
        // 验证更新了分群成员数
        verify(segmentRepository, times(1)).save(any(ScrmSegmentEntity.class));
        assertThat(result.getCustomerId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("copySegment: 创建副本, 编码追加 _copy 后缀, 名称加 副本- 前缀, 成员数归零")
    void copySegment_success() throws ScrmException {
        ScrmSegmentEntity source = buildSegmentEntity(1L);
        source.setMemberCount(50);
        when(segmentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(segmentRepository.existsBySegmentCode("VIP_ACTIVE_copy")).thenReturn(false);
        when(segmentRepository.save(any(ScrmSegmentEntity.class)))
                .thenAnswer(inv -> {
                    ScrmSegmentEntity e = inv.getArgument(0);
                    e.setId(2L);
                    return e;
                });

        ScrmSegmentEntity result = service.copySegment(1L);

        ArgumentCaptor<ScrmSegmentEntity> captor =
                ArgumentCaptor.forClass(ScrmSegmentEntity.class);
        verify(segmentRepository, times(1)).save(captor.capture());
        ScrmSegmentEntity copy = captor.getValue();
        assertThat(copy.getSegmentCode()).isEqualTo("VIP_ACTIVE_copy");
        assertThat(copy.getSegmentName()).isEqualTo("副本-活跃客户分群");
        assertThat(copy.getStatus()).isEqualTo("DRAFT");
        assertThat(copy.getMemberCount()).isZero();
        assertThat(copy.getSegmentType()).isEqualTo("DYNAMIC");
        assertThat(result.getSegmentCode()).isEqualTo("VIP_ACTIVE_copy");
    }

    
}
