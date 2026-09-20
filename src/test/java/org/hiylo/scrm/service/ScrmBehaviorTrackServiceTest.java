/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmBehaviorTrackServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmBehaviorRecordDto;
import org.hiylo.scrm.entity.ScrmBehaviorPathEntity;
import org.hiylo.scrm.entity.ScrmBehaviorTrackEntity;
import org.hiylo.scrm.entity.ScrmTouchpointEntity;
import org.hiylo.scrm.dto.ScrmTouchpointDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmBehaviorPathRepository;
import org.hiylo.scrm.repository.ScrmBehaviorTrackRepository;
import org.hiylo.scrm.repository.ScrmTouchpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
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
 * ScrmBehaviorTrackService 单元测试
 * <p>
 * 聚焦行为记录 (参数校验 / 默认值填充 / 增量触点统计)、触点管理 (CRUD / 编码唯一 /
 * 启用禁用 / 越权访问)、行为路径构建 (序列聚合 / 入口出口 / 转化点 / 已存在更新) 与
 * 私有校验方法等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmBehaviorTrackService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmBehaviorTrackServiceTest {

    /** 行为追踪数据仓库 Mock 桩 */
    @Mock
    private ScrmBehaviorTrackRepository trackRepository;
    /** 行为触点数据仓库 Mock 桩 */
    @Mock
    private ScrmTouchpointRepository touchpointRepository;
    /** 行为路径数据仓库 Mock 桩 */
    @Mock
    private ScrmBehaviorPathRepository pathRepository;

    /** 被测服务实例 */
    private ScrmBehaviorTrackService service;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ScrmBehaviorTrackService(trackRepository, touchpointRepository, pathRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 行为记录 ====================

    @Test
    @DisplayName("recordBehavior: 成功记录并写入归属账号, behaviorTime 缺省当前时间")
    void recordBehavior_success() throws Exception {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        when(trackRepository.save(any(ScrmBehaviorTrackEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 500L));

        ScrmBehaviorTrackEntity result = service.recordBehavior(dto);

        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getCustomerId()).isEqualTo(100L);
        assertThat(result.getBehaviorType()).isEqualTo("PAGE_VIEW");
        assertThat(result.getTouchpoint()).isEqualTo("WEBSITE");
        assertThat(result.getBehaviorTime()).isNotNull();
        assertThat(result.getIsConversion()).isFalse();
        assertThat(result.getConversionValue()).isEqualTo(0.0);
        ArgumentCaptor<ScrmBehaviorTrackEntity> captor = ArgumentCaptor.forClass(ScrmBehaviorTrackEntity.class);
        verify(trackRepository).save(captor.capture());
    }

    @Test
    @DisplayName("recordBehavior: isConversion=true 时 conversionValue 使用传入值")
    void recordBehavior_conversionValue() throws Exception {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setIsConversion(true);
        dto.setConversionValue(99.9);
        when(trackRepository.save(any(ScrmBehaviorTrackEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 501L));

        ScrmBehaviorTrackEntity result = service.recordBehavior(dto);

        assertThat(result.getIsConversion()).isTrue();
        assertThat(result.getConversionValue()).isEqualTo(99.9);
    }

    @Test
    @DisplayName("recordBehavior: dto 为 null 抛 BAD_REQUEST")
    void recordBehavior_nullDto() {
        assertThatThrownBy(() -> service.recordBehavior(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为记录参数不能为空");
    }

    @Test
    @DisplayName("recordBehavior: customerId 为 null 抛 BAD_REQUEST")
    void recordBehavior_nullCustomerId() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setCustomerId(null);
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("recordBehavior: behaviorType 为空抛 BAD_REQUEST")
    void recordBehavior_blankBehaviorType() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setBehaviorType("");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为类型不能为空");
    }

    @Test
    @DisplayName("recordBehavior: behaviorType 非法抛 BAD_REQUEST")
    void recordBehavior_invalidBehaviorType() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setBehaviorType("INVALID_TYPE");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为类型非法");
    }

    @Test
    @DisplayName("recordBehavior: touchpoint 为空抛 BAD_REQUEST")
    void recordBehavior_blankTouchpoint() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setTouchpoint("");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点不能为空");
    }

    @Test
    @DisplayName("recordBehavior: touchpoint 非法抛 BAD_REQUEST")
    void recordBehavior_invalidTouchpoint() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setTouchpoint("INVALID_TP");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点非法");
    }

    @Test
    @DisplayName("recordBehavior: deviceType 非法抛 BAD_REQUEST")
    void recordBehavior_invalidDeviceType() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setDeviceType("WRONG_DEVICE");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("设备类型非法");
    }

    @Test
    @DisplayName("recordBehavior: funnelStage 非法抛 BAD_REQUEST")
    void recordBehavior_invalidFunnelStage() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setFunnelStage("WRONG_STAGE");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("漏斗阶段非法");
    }

    @Test
    @DisplayName("recordBehavior: metadata 非法 JSON 抛 BAD_REQUEST")
    void recordBehavior_invalidMetadataJson() {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        dto.setMetadata("{invalid");
        assertThatThrownBy(() -> service.recordBehavior(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("附加数据 metadata JSON 解析失败");
    }

    @Test
    @DisplayName("recordBehavior: behaviorTime 传入时使用传入值 (补录)")
    void recordBehavior_customBehaviorTime() throws Exception {
        ScrmBehaviorRecordDto dto = buildRecordDto();
        LocalDateTime customTime = LocalDateTime.of(2025, 1, 1, 10, 0);
        dto.setBehaviorTime(customTime);
        when(trackRepository.save(any(ScrmBehaviorTrackEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 502L));

        ScrmBehaviorTrackEntity result = service.recordBehavior(dto);

        assertThat(result.getBehaviorTime()).isEqualTo(customTime);
    }

    @Test
    @DisplayName("batchRecord: 空列表抛 BAD_REQUEST")
    void batchRecord_emptyList() {
        assertThatThrownBy(() -> service.batchRecord(Collections.emptyList()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为记录列表不能为空");
    }

    @Test
    @DisplayName("batchRecord: 多条记录逐一调用 recordBehavior")
    void batchRecord_success() throws Exception {
        ScrmBehaviorRecordDto dto1 = buildRecordDto();
        ScrmBehaviorRecordDto dto2 = buildRecordDto();
        dto2.setBehaviorType("CLICK");
        when(trackRepository.save(any(ScrmBehaviorTrackEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 600L));

        List<ScrmBehaviorTrackEntity> result = service.batchRecord(List.of(dto1, dto2));

        assertThat(result).hasSize(2);
        verify(trackRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("getTrack: 不存在抛 NOT_FOUND")
    void getTrack_notFound() {
        when(trackRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTrack(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为事件不存在");
    }

    
    // ==================== 触点管理 ====================

    @Test
    @DisplayName("createTouchpoint: 成功创建, isActive 缺省 true, 统计字段初始化 0")
    void createTouchpoint_success() throws Exception {
        ScrmTouchpointDto dto = buildTouchpointDto();
        when(touchpointRepository.existsByTouchpointCode("TP_001")).thenReturn(false);
        when(touchpointRepository.save(any(ScrmTouchpointEntity.class)))
                .thenAnswer(inv -> assignTouchpointId(inv.getArgument(0), 700L));

        ScrmTouchpointEntity result = service.createTouchpoint(dto);

        assertThat(result.getId()).isEqualTo(700L);
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getTotalEvents()).isEqualTo(0);
        assertThat(result.getUniqueVisitors()).isEqualTo(0);
        assertThat(result.getConversionCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("createTouchpoint: 触点编码重复抛 CONFLICT")
    void createTouchpoint_duplicateCode() {
        ScrmTouchpointDto dto = buildTouchpointDto();
        when(touchpointRepository.existsByTouchpointCode("TP_001")).thenReturn(true);
        assertThatThrownBy(() -> service.createTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点编码已存在");
    }

    @Test
    @DisplayName("createTouchpoint: touchpointName 为空抛 BAD_REQUEST")
    void createTouchpoint_blankName() {
        ScrmTouchpointDto dto = buildTouchpointDto();
        dto.setTouchpointName("");
        assertThatThrownBy(() -> service.createTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点名称不能为空");
    }

    @Test
    @DisplayName("createTouchpoint: touchpointType 非法抛 BAD_REQUEST")
    void createTouchpoint_invalidType() {
        ScrmTouchpointDto dto = buildTouchpointDto();
        dto.setTouchpointType("INVALID_TP_TYPE");
        assertThatThrownBy(() -> service.createTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点类型非法");
    }

    @Test
    @DisplayName("createTouchpoint: config 非法 JSON 抛 BAD_REQUEST")
    void createTouchpoint_invalidConfigJson() {
        ScrmTouchpointDto dto = buildTouchpointDto();
        dto.setConfig("{invalid");
        assertThatThrownBy(() -> service.createTouchpoint(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点配置 config JSON 解析失败");
    }

    @Test
    @DisplayName("updateTouchpoint: 字段非空才覆盖")
    void updateTouchpoint_partialUpdate() throws Exception {
        ScrmTouchpointEntity existing = buildTouchpointEntity(1L);
        when(touchpointRepository.findById(800L)).thenReturn(Optional.of(existing));
        when(touchpointRepository.save(any(ScrmTouchpointEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmTouchpointDto dto = new ScrmTouchpointDto();
        dto.setTouchpointName("更新后名称");
        dto.setIsActive(false);
        ScrmTouchpointEntity result = service.updateTouchpoint(800L, dto);

        assertThat(result.getTouchpointName()).isEqualTo("更新后名称");
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getTouchpointCode()).isEqualTo("TP_001");
    }

    @Test
    @DisplayName("updateTouchpoint: 不存在抛 NOT_FOUND")
    void updateTouchpoint_notFound() {
        when(touchpointRepository.findById(999L)).thenReturn(Optional.empty());
        ScrmTouchpointDto dto = new ScrmTouchpointDto();
        assertThatThrownBy(() -> service.updateTouchpoint(999L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点不存在");
    }

    @Test
    @DisplayName("deleteTouchpoint: 成功删除")
    void deleteTouchpoint_success() throws Exception {
        ScrmTouchpointEntity existing = buildTouchpointEntity(1L);
        when(touchpointRepository.findById(801L)).thenReturn(Optional.of(existing));
        service.deleteTouchpoint(801L);
        verify(touchpointRepository).delete(existing);
    }

    
    @Test
    @DisplayName("getTouchpointByCode: 存在则返回")
    void getTouchpointByCode_success() throws Exception {
        ScrmTouchpointEntity existing = buildTouchpointEntity(1L);
        when(touchpointRepository.findByTouchpointCode("TP_001"))
                .thenReturn(Optional.of(existing));
        ScrmTouchpointEntity result = service.getTouchpointByCode("TP_001");
        assertThat(result.getTouchpointCode()).isEqualTo("TP_001");
    }

    @Test
    @DisplayName("getTouchpointByCode: 不存在抛 NOT_FOUND")
    void getTouchpointByCode_notFound() {
        when(touchpointRepository.findByTouchpointCode("MISSING"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTouchpointByCode("MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("触点不存在");
    }

    @Test
    @DisplayName("activateTouchpoint: 成功启用")
    void activateTouchpoint_success() throws Exception {
        ScrmTouchpointEntity existing = buildTouchpointEntity(1L);
        existing.setIsActive(false);
        when(touchpointRepository.findById(803L)).thenReturn(Optional.of(existing));
        when(touchpointRepository.save(any(ScrmTouchpointEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmTouchpointEntity result = service.activateTouchpoint(803L);
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("deactivateTouchpoint: 成功禁用")
    void deactivateTouchpoint_success() throws Exception {
        ScrmTouchpointEntity existing = buildTouchpointEntity(1L);
        existing.setIsActive(true);
        when(touchpointRepository.findById(804L)).thenReturn(Optional.of(existing));
        when(touchpointRepository.save(any(ScrmTouchpointEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmTouchpointEntity result = service.deactivateTouchpoint(804L);
        assertThat(result.getIsActive()).isFalse();
    }

    // ==================== 行为路径 ====================

    @Test
    @DisplayName("buildPath: customerId 为 null 抛 BAD_REQUEST")
    void buildPath_nullCustomerId() {
        assertThatThrownBy(() -> service.buildPath(null, "session_1"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("客户 ID 不能为空");
    }

    @Test
    @DisplayName("buildPath: sessionId 为空抛 BAD_REQUEST")
    void buildPath_blankSessionId() {
        assertThatThrownBy(() -> service.buildPath(100L, ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话 ID 不能为空");
    }

    @Test
    @DisplayName("buildPath: 无行为事件抛 BAD_REQUEST")
    void buildPath_noEvents() {
        when(trackRepository.findByCustomerIdAndSessionIdOrderByBehaviorTimeAsc(100L, "session_empty"))
                .thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> service.buildPath(100L, "session_empty"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("会话内无行为事件");
    }

    @Test
    @DisplayName("buildPath: 新建路径, 验证序列聚合 / 入口出口 / 总行为数 / 转化点")
    void buildPath_createNew() throws Exception {
        ScrmBehaviorTrackEntity e1 = buildTrackEntity(1L, 100L, "PAGE_VIEW", "WEBSITE", false);
        e1.setBehaviorTime(LocalDateTime.of(2025, 1, 1, 10, 0));
        e1.setDurationSeconds(30);
        ScrmBehaviorTrackEntity e2 = buildTrackEntity(2L, 100L, "PURCHASE", "APP", true);
        e2.setBehaviorTime(LocalDateTime.of(2025, 1, 1, 10, 5));
        e2.setDurationSeconds(60);
        when(trackRepository.findByCustomerIdAndSessionIdOrderByBehaviorTimeAsc(100L, "session_1"))
                .thenReturn(List.of(e1, e2));
        when(pathRepository.findByCustomerIdAndSessionId(100L, "session_1"))
                .thenReturn(Optional.empty());
        when(pathRepository.save(any(ScrmBehaviorPathEntity.class)))
                .thenAnswer(inv -> assignPathId(inv.getArgument(0), 900L));

        ScrmBehaviorPathEntity result = service.buildPath(100L, "session_1");

        assertThat(result.getCustomerId()).isEqualTo(100L);
        assertThat(result.getTotalBehaviors()).isEqualTo(2);
        assertThat(result.getTotalDurationSeconds()).isEqualTo(90);
        assertThat(result.getTouchpointCount()).isEqualTo(2);
        assertThat(result.getTouchpoints()).isEqualTo("WEBSITE,APP");
        assertThat(result.getEntryTouchpoint()).isEqualTo("WEBSITE");
        assertThat(result.getExitTouchpoint()).isEqualTo("APP");
        assertThat(result.getHasConversion()).isTrue();
        assertThat(result.getConversionPoint()).isEqualTo("PURCHASE@APP");
        assertThat(result.getSessionStartTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 0));
        assertThat(result.getSessionEndTime()).isEqualTo(LocalDateTime.of(2025, 1, 1, 10, 5));
    }

    @Test
    @DisplayName("buildPath: 已存在路径则更新而非新建")
    void buildPath_updateExisting() throws Exception {
        ScrmBehaviorTrackEntity e1 = buildTrackEntity(1L, 100L, "CLICK", "WEBSITE", false);
        e1.setBehaviorTime(LocalDateTime.now());
        ScrmBehaviorPathEntity existing = new ScrmBehaviorPathEntity();
        existing.setId(901L);
        when(trackRepository.findByCustomerIdAndSessionIdOrderByBehaviorTimeAsc(100L, "session_2"))
                .thenReturn(List.of(e1));
        when(pathRepository.findByCustomerIdAndSessionId(100L, "session_2"))
                .thenReturn(Optional.of(existing));
        when(pathRepository.save(any(ScrmBehaviorPathEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmBehaviorPathEntity result = service.buildPath(100L, "session_2");

        assertThat(result.getId()).isEqualTo(901L);
        verify(pathRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("getPath: 不存在抛 NOT_FOUND")
    void getPath_notFound() {
        when(pathRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPath(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("行为路径不存在");
    }

    // ==================== 辅助方法 ====================

    private ScrmBehaviorRecordDto buildRecordDto() {
        ScrmBehaviorRecordDto dto = new ScrmBehaviorRecordDto();
        dto.setCustomerId(100L);
        dto.setBehaviorType("PAGE_VIEW");
        dto.setTouchpoint("WEBSITE");
        return dto;
    }

    private ScrmTouchpointDto buildTouchpointDto() {
        ScrmTouchpointDto dto = new ScrmTouchpointDto();
        dto.setTouchpointName("官网");
        dto.setTouchpointCode("TP_001");
        dto.setTouchpointType("WEBSITE");
        return dto;
    }

    private ScrmTouchpointEntity buildTouchpointEntity(Long id) {
        ScrmTouchpointEntity entity = new ScrmTouchpointEntity();
        entity.setId(id);
        entity.setTouchpointName("官网");
        entity.setTouchpointCode("TP_001");
        entity.setTouchpointType("WEBSITE");
        entity.setIsActive(true);
        return entity;
    }

    private ScrmBehaviorTrackEntity buildTrackEntity(Long id, Long customerId, String behaviorType, String touchpoint, boolean conversion) {
        ScrmBehaviorTrackEntity entity = new ScrmBehaviorTrackEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setBehaviorType(behaviorType);
        entity.setTouchpoint(touchpoint);
        entity.setIsConversion(conversion);
        return entity;
    }

    private ScrmBehaviorTrackEntity assignId(ScrmBehaviorTrackEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmTouchpointEntity assignTouchpointId(ScrmTouchpointEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmBehaviorPathEntity assignPathId(ScrmBehaviorPathEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
