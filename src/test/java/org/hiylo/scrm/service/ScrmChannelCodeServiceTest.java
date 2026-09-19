/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmChannelCodeDto;
import org.hiylo.scrm.dto.ScrmChannelCodeScanDto;
import org.hiylo.scrm.entity.ScrmChannelCodeEntity;
import org.hiylo.scrm.entity.ScrmChannelCodeScanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmChannelCodeRepository;
import org.hiylo.scrm.repository.ScrmChannelCodeScanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmChannelCodeService 单元测试
 * <p>
 * 聚焦渠道活码管理 (创建 / 默认值填充 / SINGLE 类型校验)、扫码记录
 * (账号分配 / 扫码计数 / 状态校验)、活码激活与越权访问校验等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmChannelCodeService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmChannelCodeServiceTest {

    /** 渠道码数据仓库 Mock 桩 */
    @Mock
    private ScrmChannelCodeRepository channelCodeRepository;
    /** 渠道码扫码数据仓库 Mock 桩 */
    @Mock
    private ScrmChannelCodeScanRepository scanRepository;

    /** 被测服务实例 */
    private ScrmChannelCodeService service;

    @BeforeEach
    void setUp() {
        service = new ScrmChannelCodeService(channelCodeRepository, scanRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的渠道活码实体 (用于 findById 返回)
     */
    private ScrmChannelCodeEntity buildCodeEntity(Long id, String status, String codeType) {
        ScrmChannelCodeEntity entity = new ScrmChannelCodeEntity();
        entity.setId(id);
        entity.setCodeName("抖音引流活码");
        entity.setCodeType(codeType);
        entity.setPlatformType("douyin");
        entity.setQrCodeUrl("https://qr.example.com/code/" + id);
        entity.setRedirectAccountId(1001L);
        entity.setStatus(status);
        entity.setScanCount(0);
        entity.setAddCount(0);
        return entity;
    }

    @Test
    @DisplayName("createCode: 写入归属账号与默认值后持久化")
    void createCode_success() throws ScrmException {
        when(channelCodeRepository.save(any(ScrmChannelCodeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChannelCodeDto dto = new ScrmChannelCodeDto();
        dto.setCodeName("抖音引流活码");
        dto.setCodeType("MULTI");
        dto.setPlatformType("douyin");
        dto.setAssignRule("1001,1002,1003");
        dto.setCreatedBy("admin01");

        ScrmChannelCodeDto result = service.createCode(dto);

        ArgumentCaptor<ScrmChannelCodeEntity> captor =
                ArgumentCaptor.forClass(ScrmChannelCodeEntity.class);
        verify(channelCodeRepository, times(1)).save(captor.capture());
        ScrmChannelCodeEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("INACTIVE");
        assertThat(saved.getScanCount()).isZero();
        assertThat(saved.getAddCount()).isZero();
        assertThat(saved.getCreatedBy()).isEqualTo("admin01");
        assertThat(result.getCodeName()).isEqualTo("抖音引流活码");
    }

    @Test
    @DisplayName("createCode: SINGLE 类型未指定重定向账号时抛 BAD_REQUEST")
    void createCode_singleTypeWithoutRedirect() {
        ScrmChannelCodeDto dto = new ScrmChannelCodeDto();
        dto.setCodeName("单账号活码");
        dto.setCodeType("SINGLE");
        dto.setPlatformType("wework");
        dto.setRedirectAccountId(null);

        assertThatThrownBy(() -> service.createCode(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("SINGLE 类型活码必须指定重定向账号");
        verify(channelCodeRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordScan: SINGLE 活码扫码后分配重定向账号并递增扫码数")
    void recordScan_success() throws ScrmException {
        ScrmChannelCodeEntity entity = buildCodeEntity(10L, "ACTIVE", "SINGLE");
        when(channelCodeRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(channelCodeRepository.save(any(ScrmChannelCodeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(scanRepository.save(any(ScrmChannelCodeScanEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmChannelCodeScanDto result = service.recordScan(10L, "scanner01", "扫码者",
                "127.0.0.1", "Mozilla/5.0");

        ArgumentCaptor<ScrmChannelCodeEntity> codeCaptor =
                ArgumentCaptor.forClass(ScrmChannelCodeEntity.class);
        verify(channelCodeRepository, times(1)).save(codeCaptor.capture());
        assertThat(codeCaptor.getValue().getScanCount()).isEqualTo(1);
        ArgumentCaptor<ScrmChannelCodeScanEntity> scanCaptor =
                ArgumentCaptor.forClass(ScrmChannelCodeScanEntity.class);
        verify(scanRepository, times(1)).save(scanCaptor.capture());
        ScrmChannelCodeScanEntity savedScan = scanCaptor.getValue();
        assertThat(savedScan.getChannelCodeId()).isEqualTo(10L);
        assertThat(savedScan.getAssignedAccountId()).isEqualTo(1001L);
        assertThat(savedScan.getAdded()).isEqualTo("PENDING");
        assertThat(savedScan.getScannerUid()).isEqualTo("scanner01");
        assertThat(result.getAssignedAccountId()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("recordScan: 活码已停用时抛 BAD_REQUEST")
    void recordScan_inactiveCode() {
        ScrmChannelCodeEntity entity = buildCodeEntity(10L, "INACTIVE", "SINGLE");
        when(channelCodeRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.recordScan(10L, "scanner01", "扫码者",
                "127.0.0.1", "Mozilla/5.0"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道活码已停用");
        verify(channelCodeRepository, never()).save(any());
        verify(scanRepository, never()).save(any());
    }

    @Test
    @DisplayName("activateCode: 激活活码后状态置 ACTIVE")
    void activateCode_success() throws ScrmException {
        ScrmChannelCodeEntity entity = buildCodeEntity(10L, "INACTIVE", "MULTI");
        when(channelCodeRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(channelCodeRepository.save(any(ScrmChannelCodeEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.activateCode(10L);

        ArgumentCaptor<ScrmChannelCodeEntity> captor =
                ArgumentCaptor.forClass(ScrmChannelCodeEntity.class);
        verify(channelCodeRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    
}
