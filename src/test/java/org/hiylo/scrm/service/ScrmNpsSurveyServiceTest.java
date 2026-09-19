/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmNpsSurveyServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmSurveyDto;
import org.hiylo.scrm.entity.ScrmSurveyEntity;
import org.hiylo.scrm.entity.ScrmSurveyResponseEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmNpsBenchmarkRepository;
import org.hiylo.scrm.repository.ScrmSurveyInvitationRepository;
import org.hiylo.scrm.repository.ScrmSurveyRepository;
import org.hiylo.scrm.repository.ScrmSurveyResponseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmNpsSurveyService 单元测试
 * <p>
 * 聚焦调查问卷管理 (创建 / 默认值填充 / 参数校验)、问卷状态机
 * (激活 / 归档)、NPS 分数计算与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmNpsSurveyService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmNpsSurveyServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 调查问卷仓库 Mock */
    @Mock
    private ScrmSurveyRepository surveyRepository;
    /** 问卷邀请仓库 Mock */
    @Mock
    private ScrmSurveyInvitationRepository invitationRepository;
    /** 问卷作答记录仓库 Mock */
    @Mock
    private ScrmSurveyResponseRepository responseRepository;
    /** NPS 行业基准仓库 Mock */
    @Mock
    private ScrmNpsBenchmarkRepository benchmarkRepository;

    /** 被测服务实例 */
    private ScrmNpsSurveyService service;

    @BeforeEach
    void setUp() {
        ScrmNpsSurveyManageService manageService = new ScrmNpsSurveyManageService(
                surveyRepository, invitationRepository, objectMapper);
        ScrmNpsSurveyInvitationService invitationService = new ScrmNpsSurveyInvitationService(
                invitationRepository, manageService);
        ScrmNpsSurveyResponseService responseService = new ScrmNpsSurveyResponseService(
                responseRepository, invitationRepository, surveyRepository, objectMapper, manageService);
        ScrmNpsSurveyAnalyticsService analyticsService = new ScrmNpsSurveyAnalyticsService(
                benchmarkRepository, responseRepository, invitationRepository, surveyRepository, manageService);
        service = new ScrmNpsSurveyService(manageService, invitationService, responseService, analyticsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的调查问卷实体 (用于 findById 返回)
     */
    private ScrmSurveyEntity buildSurveyEntity(Long id, String status) {
        ScrmSurveyEntity entity = new ScrmSurveyEntity();
        entity.setId(id);
        entity.setSurveyName("NPS 满意度调查");
        entity.setSurveyType("NPS");
        entity.setScaleType("NPS_0_10");
        entity.setStatus(status);
        entity.setResponseCount(0);
        entity.setCompletionRate(0.0);
        return entity;
    }

    /**
     * 构造调查回复实体 (用于 NPS 计算)
     */
    private ScrmSurveyResponseEntity buildResponseEntity(Integer npsScore) {
        ScrmSurveyResponseEntity entity = new ScrmSurveyResponseEntity();
        entity.setNpsScore(npsScore);
        return entity;
    }

    @Test
    @DisplayName("createSurvey: 写入账号 ID 与默认值后持久化")
    void createSurvey_success() throws ScrmException {
        when(surveyRepository.save(any(ScrmSurveyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSurveyDto dto = new ScrmSurveyDto();
        dto.setSurveyName("NPS 满意度调查");
        dto.setSurveyType("NPS");
        dto.setScaleType("NPS_0_10");
        dto.setTitle("您有多大意愿推荐我们?");
        dto.setQuestions("[{\"id\":\"q1\",\"scale\":\"NPS_0_10\"}]");
        dto.setCreatedBy("admin01");

        ScrmSurveyEntity result = service.createSurvey(dto);

        ArgumentCaptor<ScrmSurveyEntity> captor =
                ArgumentCaptor.forClass(ScrmSurveyEntity.class);
        verify(surveyRepository, times(1)).save(captor.capture());
        ScrmSurveyEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getResponseCount()).isZero();
        assertThat(saved.getCompletionRate()).isEqualTo(0.0);
        assertThat(saved.getTriggerDelayHours()).isZero();
        assertThat(saved.getEstimatedTimeMinutes()).isEqualTo(2);
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getSurveyName()).isEqualTo("NPS 满意度调查");
    }

    @Test
    @DisplayName("createSurvey: 调查类型非法时抛 BAD_REQUEST")
    void createSurvey_invalidSurveyType() {
        ScrmSurveyDto dto = new ScrmSurveyDto();
        dto.setSurveyName("非法问卷");
        dto.setSurveyType("INVALID_TYPE");
        dto.setTitle("标题");
        dto.setQuestions("[]");

        assertThatThrownBy(() -> service.createSurvey(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("调查类型非法");
        verify(surveyRepository, never()).save(any());
    }

    @Test
    @DisplayName("activateSurvey: DRAFT 问卷激活后状态置 ACTIVE")
    void activateSurvey_success() throws ScrmException {
        ScrmSurveyEntity entity = buildSurveyEntity(10L, "DRAFT");
        when(surveyRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(surveyRepository.save(any(ScrmSurveyEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.activateSurvey(10L);

        ArgumentCaptor<ScrmSurveyEntity> captor =
                ArgumentCaptor.forClass(ScrmSurveyEntity.class);
        verify(surveyRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("archiveSurvey: 已归档问卷再次归档抛 CONFLICT")
    void archiveSurvey_alreadyArchived() {
        ScrmSurveyEntity entity = buildSurveyEntity(10L, "ARCHIVED");
        when(surveyRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.archiveSurvey(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("问卷已归档");
        verify(surveyRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculateNps: 推荐者3贬损者2时NPS得分为20")
    void calculateNps_success() {
        // 推荐者 (9-10): 3 个, 贬损者 (0-6): 2 个, 总计 5 个
        // NPS = (3-2)/5*100 = 20
        List<ScrmSurveyResponseEntity> responses = List.of(
                buildResponseEntity(10),
                buildResponseEntity(10),
                buildResponseEntity(10),
                buildResponseEntity(0),
                buildResponseEntity(6)
        );

        int nps = service.calculateNps(responses);

        assertThat(nps).isEqualTo(20);
    }

    
}
