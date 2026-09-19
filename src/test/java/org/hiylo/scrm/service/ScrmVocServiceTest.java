/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmVocServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmVocResponseDto;
import org.hiylo.scrm.dto.ScrmVocVoiceDto;
import org.hiylo.scrm.entity.ScrmVocVoiceEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmVocInsightRepository;
import org.hiylo.scrm.repository.ScrmVocTopicRepository;
import org.hiylo.scrm.repository.ScrmVocVoiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmVocService 单元测试
 * <p>
 * 聚焦客户声音管理 (创建 / 默认值填充 / 枚举校验 / 声音编号生成)、
 * 声音回复 (回复计数递增 / 状态流转) 与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmVocService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmVocServiceTest {

    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 客户声音仓库 Mock */
    @Mock
    private ScrmVocVoiceRepository voiceRepository;
    /** 声音主题仓库 Mock */
    @Mock
    private ScrmVocTopicRepository topicRepository;
    /** 声音洞察仓库 Mock */
    @Mock
    private ScrmVocInsightRepository insightRepository;

    /** 被测服务实例 */
    private ScrmVocService service;

    @BeforeEach
    void setUp() {
        ScrmVocTopicService topicService = new ScrmVocTopicService(topicRepository, voiceRepository);
        ScrmVocVoiceService voiceService =
                new ScrmVocVoiceService(voiceRepository, topicRepository, topicService);
        ScrmVocInsightService insightService = new ScrmVocInsightService(insightRepository, topicService);
        ScrmVocStatsService statsService =
                new ScrmVocStatsService(voiceRepository, topicRepository, insightRepository,
                        voiceService, topicService);
        service = new ScrmVocService(voiceService, topicService, insightService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的客户声音实体 (用于 findById 返回)
     */
    private ScrmVocVoiceEntity buildVoiceEntity(Long id, String status, Integer responseCount) {
        ScrmVocVoiceEntity entity = new ScrmVocVoiceEntity();
        entity.setId(id);
        entity.setVoiceNo("VOC202608050001");
        entity.setCustomerId(100L);
        entity.setCustomerName("张三");
        entity.setSource("SURVEY");
        entity.setVoiceType("COMPLAINT");
        entity.setTitle("物流延迟投诉");
        entity.setContent("下单三天未发货");
        entity.setStatus(status);
        entity.setResponseCount(responseCount);
        entity.setPriority("HIGH");
        return entity;
    }

    @Test
    @DisplayName("createVoice: 写入账号 ID 与默认值后持久化")
    void createVoice_success() throws ScrmException {
        when(voiceRepository.countByVoiceNoStartingWith(any(String.class)))
                .thenReturn(0L);
        when(voiceRepository.save(any(ScrmVocVoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmVocVoiceDto dto = new ScrmVocVoiceDto();
        dto.setCustomerId(100L);
        dto.setCustomerName("张三");
        dto.setSource("SURVEY");
        dto.setVoiceType("COMPLAINT");
        dto.setTitle("物流延迟投诉");
        dto.setContent("下单三天未发货");
        dto.setCreatedBy("admin01");

        ScrmVocVoiceEntity result = service.createVoice(dto);

        ArgumentCaptor<ScrmVocVoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmVocVoiceEntity.class);
        verify(voiceRepository, times(1)).save(captor.capture());
        ScrmVocVoiceEntity saved = captor.getValue();
        assertThat(saved.getLanguage()).isEqualTo("zh-CN");
        assertThat(saved.getSentiment()).isEqualTo("NEUTRAL");
        assertThat(saved.getSentimentScore()).isEqualTo(0.0);
        assertThat(saved.getPriority()).isEqualTo("MEDIUM");
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getResponseCount()).isZero();
        assertThat(saved.getLikeCount()).isZero();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getShareCount()).isZero();
        assertThat(saved.getResolutionTimeHours()).isZero();
        assertThat(saved.getIsPublic()).isFalse();
        assertThat(saved.getIsVerified()).isFalse();
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(saved.getVoiceNo()).startsWith("VOC");
        assertThat(result.getTitle()).isEqualTo("物流延迟投诉");
    }

    @Test
    @DisplayName("createVoice: 来源渠道非法时抛 BAD_REQUEST")
    void createVoice_invalidSource() {
        ScrmVocVoiceDto dto = new ScrmVocVoiceDto();
        dto.setSource("INVALID_SOURCE");
        dto.setVoiceType("COMPLAINT");

        assertThatThrownBy(() -> service.createVoice(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("来源渠道非法");
        verify(voiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("createVoice: 优先级非法时抛 BAD_REQUEST")
    void createVoice_invalidPriority() {
        ScrmVocVoiceDto dto = new ScrmVocVoiceDto();
        dto.setSource("SURVEY");
        dto.setVoiceType("COMPLAINT");
        dto.setPriority("INVALID_PRIORITY");

        assertThatThrownBy(() -> service.createVoice(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("优先级非法");
        verify(voiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("respondToVoice: 回复后回复计数递增且 NEW 状态流转为 IN_PROGRESS")
    void respondToVoice_success() throws ScrmException {
        ScrmVocVoiceEntity voice = buildVoiceEntity(50L, "NEW", 0);
        when(voiceRepository.findById(50L)).thenReturn(Optional.of(voice));
        when(voiceRepository.save(any(ScrmVocVoiceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmVocResponseDto dto = new ScrmVocResponseDto();
        dto.setVoiceId(50L);
        dto.setResponse("已联系物流加急处理");
        dto.setResponderId("agent01");

        ScrmVocVoiceEntity result = service.respondToVoice(dto);

        ArgumentCaptor<ScrmVocVoiceEntity> captor =
                ArgumentCaptor.forClass(ScrmVocVoiceEntity.class);
        verify(voiceRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getResponseCount()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.getResponseCount()).isEqualTo(1);
    }

    
    @Test
    @DisplayName("createVoice: 参数为空时抛 BAD_REQUEST")
    void createVoice_nullDto() {
        assertThatThrownBy(() -> service.createVoice(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("声音参数不能为空");
        verify(voiceRepository, never()).save(any());
    }
}
