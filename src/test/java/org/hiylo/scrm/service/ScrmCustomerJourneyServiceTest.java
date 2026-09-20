/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerJourneyServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.entity.ScrmConversationMessageEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmJourneyEnrollmentEntity;
import org.hiylo.scrm.entity.ScrmJourneyStepEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.repository.ScrmConversationMessageRepository;
import org.hiylo.scrm.repository.ScrmCustomerJourneyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmCustomerTagRepository;
import org.hiylo.scrm.repository.ScrmJourneyEnrollmentRepository;
import org.hiylo.scrm.repository.ScrmJourneyProgressLogRepository;
import org.hiylo.scrm.repository.ScrmJourneyStepRepository;
import org.hiylo.scrm.repository.ScrmTagCustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmCustomerJourneyService 单元测试
 * <p>
 * 聚焦步骤动作执行 (SEND_MESSAGE / ADD_TAG / SET_LIFECYCLE) 与客户字段提取
 * (extractCustomerFieldValue) 的真实执行逻辑, 通过反射调用 private 方法验证。
 * ObjectMapper 使用真实实例以解析步骤 config JSON。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerJourneyService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerJourneyServiceTest {

    /** 客户旅程数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerJourneyRepository journeyRepository;
    /** 旅程步骤数据仓库 Mock 桩 */
    @Mock
    private ScrmJourneyStepRepository stepRepository;
    /** 旅程加入数据仓库 Mock 桩 */
    @Mock
    private ScrmJourneyEnrollmentRepository enrollmentRepository;
    /** 旅程进度日志数据仓库 Mock 桩 */
    @Mock
    private ScrmJourneyProgressLogRepository progressLogRepository;
    /** 客户数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户标签关联数据仓库 Mock 桩 */
    @Mock
    private ScrmTagCustomerRepository tagCustomerRepository;
    /** 客户标签数据仓库 Mock 桩 */
    @Mock
    private ScrmCustomerTagRepository customerTagRepository;
    /** 会话消息数据仓库 Mock 桩 */
    @Mock
    private ScrmConversationMessageRepository conversationMessageRepository;

    /** 步骤 config JSON 解析需真实 ObjectMapper, 故不使用 Mock */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测步骤执行子域服务实例 */
    private ScrmJourneyStepExecutionService stepService;

    @BeforeEach
    void setUp() {
        stepService = new ScrmJourneyStepExecutionService(journeyRepository, stepRepository, enrollmentRepository,
                progressLogRepository, customerRepository, tagCustomerRepository, customerTagRepository,
                conversationMessageRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造入营记录
     */
    private ScrmJourneyEnrollmentEntity buildEnrollment(Long customerId) {
        ScrmJourneyEnrollmentEntity enrollment = new ScrmJourneyEnrollmentEntity();
        enrollment.setId(10L);
        enrollment.setJourneyId(20L);
        enrollment.setCustomerId(customerId);
        enrollment.setStatus("ACTIVE");
        return enrollment;
    }

    /**
     * 构造步骤实体
     */
    private ScrmJourneyStepEntity buildStep(String stepType, String config) {
        ScrmJourneyStepEntity step = new ScrmJourneyStepEntity();
        step.setId(30L);
        step.setJourneyId(20L);
        step.setStepName("test-step");
        step.setStepType(stepType);
        step.setStepOrder(1);
        step.setConfig(config);
        return step;
    }

    @Test
    @DisplayName("executeSendMessageAction: 持久化出站会话消息, direction=OUTBOUND")
    void executeSendMessageAction_success() {
        ScrmJourneyEnrollmentEntity enrollment = buildEnrollment(100L);
        ScrmJourneyStepEntity step = buildStep("SEND_MESSAGE",
                "{\"conversationId\":200,\"content\":\"你好\",\"messageType\":\"TEXT\"}");

        ReflectionTestUtils.invokeMethod(stepService, "executeSendMessageAction", enrollment, step);

        ArgumentCaptor<ScrmConversationMessageEntity> captor =
                ArgumentCaptor.forClass(ScrmConversationMessageEntity.class);
        verify(conversationMessageRepository, times(1)).save(captor.capture());
        ScrmConversationMessageEntity saved = captor.getValue();
        assertThat(saved.getDirection()).isEqualTo("OUTBOUND");
        assertThat(saved.getConversationId()).isEqualTo(200L);
        assertThat(saved.getContent()).isEqualTo("你好");
        assertThat(saved.getMessageType()).isEqualTo("TEXT");
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("executeAddTagAction: 标签不存在时创建赋值记录, save 被调用")
    void executeAddTagAction_success() {
        ScrmJourneyEnrollmentEntity enrollment = buildEnrollment(100L);
        ScrmJourneyStepEntity step = buildStep("ADD_TAG", "{\"tagIds\":\"100,200\"}");
        // 标签赋值不存在 → 应新增
        when(tagCustomerRepository.findByCustomerIdAndTagId(eq(100L), eq(100L)))
                .thenReturn(Optional.empty());
        when(tagCustomerRepository.findByCustomerIdAndTagId(eq(100L), eq(200L)))
                .thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(stepService, "executeAddTagAction", enrollment, step);

        ArgumentCaptor<ScrmTagCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmTagCustomerEntity.class);
        verify(tagCustomerRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ScrmTagCustomerEntity::getTagId)
                .containsExactlyInAnyOrder(100L, 200L);
        captor.getAllValues().forEach(tc -> {
            assertThat(tc.getCustomerId()).isEqualTo(100L);
            assertThat(tc.getTagSource()).isEqualTo("JOURNEY");
            assertThat(tc.getIsAuto()).isTrue();
        });
    }

    @Test
    @DisplayName("executeSetLifecycleAction: 更新客户 lifecycle 字段并持久化")
    void executeSetLifecycleAction_success() {
        ScrmJourneyEnrollmentEntity enrollment = buildEnrollment(100L);
        ScrmJourneyStepEntity step = buildStep("SET_LIFECYCLE", "{\"lifecycle\":\"ACTIVE\"}");
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setLifecycle("NEW");
        when(customerRepository.findById(100L)).thenReturn(Optional.of(customer));

        ReflectionTestUtils.invokeMethod(stepService, "executeSetLifecycleAction", enrollment, step);

        ArgumentCaptor<ScrmCustomerEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerEntity.class);
        verify(customerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLifecycle()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("extractCustomerFieldValue_tag: 通过 tag.{tagCode} 查询标签值")
    void extractCustomerFieldValue_tag() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        // 标签定义: tagCode=vipLevel → tagId=500
        ScrmCustomerTagEntity tagDef = new ScrmCustomerTagEntity();
        tagDef.setId(500L);
        tagDef.setTagCode("vipLevel");
        when(customerTagRepository.findByTagCode("vipLevel"))
                .thenReturn(Optional.of(tagDef));
        // 客户-标签赋值: tagValue=GOLD
        ScrmTagCustomerEntity tagCustomer = new ScrmTagCustomerEntity();
        tagCustomer.setTagValue("GOLD");
        when(tagCustomerRepository.findByCustomerIdAndTagId(100L, 500L))
                .thenReturn(Optional.of(tagCustomer));

        String value = ReflectionTestUtils.invokeMethod(stepService, "extractCustomerFieldValue", customer, "tag.vipLevel");

        assertThat(value).isEqualTo("GOLD");
        verify(customerTagRepository, times(1)).findByTagCode("vipLevel");
        verify(tagCustomerRepository, times(1)).findByCustomerIdAndTagId(100L, 500L);
    }

    @Test
    @DisplayName("extractCustomerFieldValue_lifecycle: 直接读取客户 lifecycle 字段")
    void extractCustomerFieldValue_lifecycle() {
        ScrmCustomerEntity customer = new ScrmCustomerEntity();
        customer.setId(100L);
        customer.setLifecycle("ACTIVE");

        String value = ReflectionTestUtils.invokeMethod(stepService, "extractCustomerFieldValue", customer, "lifecycle");

        assertThat(value).isEqualTo("ACTIVE");
        verify(customerTagRepository, never()).findByTagCode(any());
    }
}
