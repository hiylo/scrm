/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmMessageTrackingServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hiylo.scrm.dto.ScrmMessageReadReportDto;
import org.hiylo.scrm.dto.ScrmMessageRecallActionDto;
import org.hiylo.scrm.dto.ScrmMessageTrackingDto;
import org.hiylo.scrm.entity.ScrmMessageReadLogEntity;
import org.hiylo.scrm.entity.ScrmMessageRecallEntity;
import org.hiylo.scrm.entity.ScrmMessageTrackingEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmMessageReadLogRepository;
import org.hiylo.scrm.repository.ScrmMessageRecallRepository;
import org.hiylo.scrm.repository.ScrmMessageTrackingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmMessageTrackingService 单元测试
 * <p>
 * 聚焦消息跟踪记录创建 (默认值填充 / messageId 唯一性 / 枚举校验)、发送状态流转
 * (SENT 自动填充 sentAt / FAILED 填充 errorMessage / CANCELLED 终态不可变更)、
 * 阅读记录 (首次阅读序号 1 / 重复阅读序号递增 / 撤回消息不可阅读 / 互动评分计算)、
 * 撤回管理 (撤回窗口检查 / 已撤回不可重复 / 撤回记录创建 / processRecall 状态机)、
 * 越权隔离与统计分析 (getReadStats / getTrackingStats) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmMessageTrackingService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmMessageTrackingServiceTest {

    /** 消息跟踪记录仓库 Mock */
    @Mock
    private ScrmMessageTrackingRepository trackingRepository;
    /** 消息阅读记录仓库 Mock */
    @Mock
    private ScrmMessageReadLogRepository readLogRepository;
    /** 消息撤回记录仓库 Mock */
    @Mock
    private ScrmMessageRecallRepository recallRepository;

    /** 被测服务实例 */
    private ScrmMessageTrackingService service;
    /** JSON 序列化工具 (注册 JavaTimeModule 以支持时间类型序列化) */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        service = new ScrmMessageTrackingService(trackingRepository, readLogRepository,
                recallRepository, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 创建跟踪记录 ====================

    @Test
    @DisplayName("createTracking: 成功创建, sendStatus/contentType/recipientType 缺省填充默认值")
    void createTracking_success_defaultsFilled() throws Exception {
        ScrmMessageTrackingDto dto = buildTrackingDto();
        when(trackingRepository.findByMessageId("MSG_001")).thenReturn(Optional.empty());
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> assignId(inv.getArgument(0), 100L));

        ScrmMessageTrackingEntity result = service.createTracking(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmMessageTrackingEntity> captor = ArgumentCaptor.forClass(ScrmMessageTrackingEntity.class);
        verify(trackingRepository).save(captor.capture());
        ScrmMessageTrackingEntity saved = captor.getValue();
        assertThat(saved.getMessageId()).isEqualTo("MSG_001");
        assertThat(saved.getSenderId()).isEqualTo("S001");
        assertThat(saved.getRecipientId()).isEqualTo("R001");
        assertThat(saved.getChannel()).isEqualTo("WECHAT");
        assertThat(saved.getSendStatus()).isEqualTo("PENDING");
        assertThat(saved.getContentType()).isEqualTo("TEXT");
        assertThat(saved.getRecipientType()).isEqualTo("CUSTOMER");
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("createTracking: messageId 重复抛 CONFLICT")
    void createTracking_duplicateMessageId() {
        ScrmMessageTrackingDto dto = buildTrackingDto();
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(new ScrmMessageTrackingEntity()));
        assertThatThrownBy(() -> service.createTracking(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息 ID 已存在");
    }

    @Test
    @DisplayName("createTracking: dto 为 null 抛 BAD_REQUEST")
    void createTracking_nullDto() {
        assertThatThrownBy(() -> service.createTracking(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("跟踪参数不能为空");
    }

    @Test
    @DisplayName("createTracking: messageId 为空抛 BAD_REQUEST")
    void createTracking_blankMessageId() {
        ScrmMessageTrackingDto dto = buildTrackingDto();
        dto.setMessageId("");
        assertThatThrownBy(() -> service.createTracking(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息 ID 不能为空");
    }

    @Test
    @DisplayName("createTracking: 渠道非法抛 BAD_REQUEST")
    void createTracking_invalidChannel() {
        ScrmMessageTrackingDto dto = buildTrackingDto();
        dto.setChannel("TELEGRAM");
        assertThatThrownBy(() -> service.createTracking(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("渠道非法");
    }

    @Test
    @DisplayName("createTracking: metadata 非法 JSON 抛 BAD_REQUEST")
    void createTracking_invalidMetadataJson() {
        ScrmMessageTrackingDto dto = buildTrackingDto();
        dto.setMetadata("{invalid}");
        assertThatThrownBy(() -> service.createTracking(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("附加数据 metadata JSON 解析失败");
    }

    // ==================== 主键查询与数据隔离 ====================

    @Test
    @DisplayName("getTracking: 存在且同账号返回实体")
    void getTracking_success() throws Exception {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        when(trackingRepository.findById(100L)).thenReturn(Optional.of(entity));
        ScrmMessageTrackingEntity result = service.getTracking(100L);
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getTracking: 不存在抛 NOT_FOUND")
    void getTracking_notFound() {
        when(trackingRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTracking(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息跟踪记录不存在");
    }

    
    @Test
    @DisplayName("getTrackingByMessageId: messageId 为空抛 BAD_REQUEST")
    void getTrackingByMessageId_blank() {
        assertThatThrownBy(() -> service.getTrackingByMessageId(""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息 ID 不能为空");
    }

    @Test
    @DisplayName("getTrackingByMessageId: 不存在抛 NOT_FOUND")
    void getTrackingByMessageId_notFound() {
        when(trackingRepository.findByMessageId("MISSING"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getTrackingByMessageId("MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息跟踪记录不存在");
    }

    // ==================== 发送状态流转 ====================

    @Test
    @DisplayName("updateSendStatus: SENT 状态自动填充 sentAt")
    void updateSendStatus_sent_setsSentAt() throws Exception {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        entity.setSentAt(null);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(entity));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTrackingEntity result = service.updateSendStatus("MSG_001", "SENT");

        assertThat(result.getSendStatus()).isEqualTo("SENT");
        assertThat(result.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("updateSendStatus: FAILED 状态自动填充 errorMessage")
    void updateSendStatus_failed_setsErrorMessage() throws Exception {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        entity.setErrorMessage(null);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(entity));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTrackingEntity result = service.updateSendStatus("MSG_001", "FAILED");

        assertThat(result.getSendStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).isEqualTo("发送失败");
    }

    @Test
    @DisplayName("updateSendStatus: 状态非法抛 BAD_REQUEST")
    void updateSendStatus_invalidStatus() {
        assertThatThrownBy(() -> service.updateSendStatus("MSG_001", "UNKNOWN"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("发送状态非法");
    }

    @Test
    @DisplayName("updateSendStatus: CANCELLED 终态不允许变更抛 BAD_REQUEST")
    void updateSendStatus_cancelledCannotChange() {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        entity.setSendStatus("CANCELLED");
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(entity));
        assertThatThrownBy(() -> service.updateSendStatus("MSG_001", "SENT"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息已取消, 不允许变更发送状态");
        verify(trackingRepository, never()).save(any(ScrmMessageTrackingEntity.class));
    }

    @Test
    @DisplayName("markDelivered: 更新状态为 DELIVERED 并填充 deliveredAt")
    void markDelivered_success() throws Exception {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(entity));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTrackingEntity result = service.markDelivered("MSG_001");

        assertThat(result.getSendStatus()).isEqualTo("DELIVERED");
        assertThat(result.getDeliveredAt()).isNotNull();
    }

    // ==================== 阅读管理 ====================

    @Test
    @DisplayName("recordRead: 首次阅读 sequence=1, isRepeatedRead=false, 跟踪记录 isRead=true")
    void recordRead_firstRead() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(readLogRepository.findFirstByMessageTrackingIdAndReaderIdOrderBySequenceDesc(100L, "U001"))
                .thenReturn(Optional.empty());
        when(readLogRepository.save(any(ScrmMessageReadLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageReadReportDto report = buildReadReportDto();
        report.setReadDuration(30);
        ScrmMessageReadLogEntity result = service.recordRead(report);

        ArgumentCaptor<ScrmMessageReadLogEntity> logCaptor = ArgumentCaptor.forClass(ScrmMessageReadLogEntity.class);
        verify(readLogRepository).save(logCaptor.capture());
        ScrmMessageReadLogEntity savedLog = logCaptor.getValue();
        assertThat(savedLog.getMessageTrackingId()).isEqualTo(100L);
        assertThat(savedLog.getReaderId()).isEqualTo("U001");
        assertThat(savedLog.getSequence()).isEqualTo(1);
        assertThat(savedLog.getIsRepeatedRead()).isFalse();
        assertThat(savedLog.getReadDurationSeconds()).isEqualTo(30);
        assertThat(savedLog.getReaderType()).isEqualTo("CUSTOMER");
        assertThat(result).isNotNull();
        // 跟踪记录更新
        assertThat(tracking.getIsRead()).isTrue();
        assertThat(tracking.getReadCount()).isEqualTo(1);
        assertThat(tracking.getFirstReadAt()).isNotNull();
        assertThat(tracking.getLastReadAt()).isNotNull();
        assertThat(tracking.getReadDurationSeconds()).isEqualTo(30);
        // 互动评分: readCount(1)*1.0 + readDuration(30)*0.01 = 1.3
        assertThat(tracking.getEngagementScore()).isEqualTo(1.3);
    }

    @Test
    @DisplayName("recordRead: 重复阅读 sequence 递增, isRepeatedRead=true")
    void recordRead_repeatedRead() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setReadCount(1);
        tracking.setIsRead(true);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        ScrmMessageReadLogEntity lastLog = new ScrmMessageReadLogEntity();
        lastLog.setSequence(1);
        when(readLogRepository.findFirstByMessageTrackingIdAndReaderIdOrderBySequenceDesc(100L, "U001"))
                .thenReturn(Optional.of(lastLog));
        when(readLogRepository.save(any(ScrmMessageReadLogEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.recordRead(buildReadReportDto());

        ArgumentCaptor<ScrmMessageReadLogEntity> logCaptor = ArgumentCaptor.forClass(ScrmMessageReadLogEntity.class);
        verify(readLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getSequence()).isEqualTo(2);
        assertThat(logCaptor.getValue().getIsRepeatedRead()).isTrue();
        // 跟踪记录 readCount 累加
        assertThat(tracking.getReadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("recordRead: 消息已撤回抛 BAD_REQUEST")
    void recordRead_recalled_throws() {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setIsRecalled(true);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        assertThatThrownBy(() -> service.recordRead(buildReadReportDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息已撤回, 不允许记录阅读");
        verify(readLogRepository, never()).save(any(ScrmMessageReadLogEntity.class));
    }

    @Test
    @DisplayName("recordRead: readerId 为空抛 BAD_REQUEST")
    void recordRead_blankReaderId() {
        ScrmMessageReadReportDto report = buildReadReportDto();
        report.setReaderId("");
        assertThatThrownBy(() -> service.recordRead(report))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("阅读者 ID 不能为空");
    }

    // ==================== 撤回管理 ====================

    @Test
    @DisplayName("recallMessage: 撤回窗口内成功撤回, 创建撤回记录 SUCCESS")
    void recallMessage_withinWindow_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setSentAt(LocalDateTime.now());
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(readLogRepository.findReadersByTrackingId(100L))
                .thenReturn(Collections.emptyList());
        when(recallRepository.save(any(ScrmMessageRecallEntity.class)))
                .thenAnswer(inv -> assignRecallId(inv.getArgument(0), 200L));

        ScrmMessageRecallActionDto action = new ScrmMessageRecallActionDto();
        action.setMessageId("MSG_001");
        action.setReason("误发");
        ScrmMessageRecallEntity result = service.recallMessage(action);

        assertThat(result.getId()).isEqualTo(200L);
        assertThat(result.getRecallStatus()).isEqualTo("SUCCESS");
        assertThat(result.getIsWithinWindow()).isTrue();
        assertThat(result.getRecallWindowMinutes()).isEqualTo(2);
        assertThat(tracking.getIsRecalled()).isTrue();
        assertThat(tracking.getRecalledAt()).isNotNull();
        assertThat(tracking.getRecallReason()).isEqualTo("误发");
    }

    @Test
    @DisplayName("recallMessage: 已撤回抛 CONFLICT")
    void recallMessage_alreadyRecalled_throws() {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setIsRecalled(true);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        ScrmMessageRecallActionDto action = new ScrmMessageRecallActionDto();
        action.setMessageId("MSG_001");
        assertThatThrownBy(() -> service.recallMessage(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息已撤回, 不允许重复撤回");
        verify(recallRepository, never()).save(any(ScrmMessageRecallEntity.class));
    }

    @Test
    @DisplayName("recallMessage: 超出窗口仍允许撤回, 但 isWithinWindow=false")
    void recallMessage_beyondWindow() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        // 设置发送时间为 1 小时前, 超出 2 分钟撤回窗口
        tracking.setSentAt(LocalDateTime.now().minusHours(1));
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(readLogRepository.findReadersByTrackingId(100L))
                .thenReturn(Collections.emptyList());
        when(recallRepository.save(any(ScrmMessageRecallEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageRecallActionDto action = new ScrmMessageRecallActionDto();
        action.setMessageId("MSG_001");
        ScrmMessageRecallEntity result = service.recallMessage(action);

        assertThat(result.getIsWithinWindow()).isFalse();
        assertThat(result.getRecallStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("recallMessage: 已阅读者列表写入 affectedReaders JSON")
    void recallMessage_withAffectedReaders() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setSentAt(LocalDateTime.now());
        ScrmMessageReadLogEntity reader = new ScrmMessageReadLogEntity();
        reader.setReaderId("U001");
        reader.setReaderName("张三");
        reader.setReadAt(LocalDateTime.now());
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(readLogRepository.findReadersByTrackingId(100L))
                .thenReturn(List.of(reader));
        when(recallRepository.save(any(ScrmMessageRecallEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageRecallActionDto action = new ScrmMessageRecallActionDto();
        action.setMessageId("MSG_001");
        ScrmMessageRecallEntity result = service.recallMessage(action);

        assertThat(result.getAffectedReaders()).contains("U001").contains("张三");
    }

    @Test
    @DisplayName("recallMessage: messageId 为空抛 BAD_REQUEST")
    void recallMessage_blankMessageId() {
        ScrmMessageRecallActionDto action = new ScrmMessageRecallActionDto();
        action.setMessageId("");
        assertThatThrownBy(() -> service.recallMessage(action))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息 ID 不能为空");
    }

    @Test
    @DisplayName("processRecall: PENDING → SUCCESS, 填充 successfulRecalls")
    void processRecall_success() throws Exception {
        ScrmMessageRecallEntity recall = buildRecallEntity(200L);
        recall.setRecallStatus("PENDING");
        recall.setTotalRecipients(5);
        when(recallRepository.findById(200L)).thenReturn(Optional.of(recall));
        when(recallRepository.save(any(ScrmMessageRecallEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageRecallEntity result = service.processRecall(200L);

        assertThat(result.getRecallStatus()).isEqualTo("SUCCESS");
        assertThat(result.getSuccessfulRecalls()).isEqualTo(5);
        assertThat(result.getFailedRecalls()).isZero();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("processRecall: 非 PENDING 状态抛 BAD_REQUEST")
    void processRecall_invalidStatus() {
        ScrmMessageRecallEntity recall = buildRecallEntity(200L);
        recall.setRecallStatus("SUCCESS");
        when(recallRepository.findById(200L)).thenReturn(Optional.of(recall));
        assertThatThrownBy(() -> service.processRecall(200L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("撤回记录状态不允许执行");
    }

    
    @Test
    @DisplayName("checkRecallWindow: 窗口内返回 withinWindow=true")
    void checkRecallWindow_withinWindow() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setSentAt(LocalDateTime.now());
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));

        Map<String, Object> result = service.checkRecallWindow("MSG_001");

        assertThat(result.get("withinWindow")).isEqualTo(true);
        assertThat(result.get("recallWindowMinutes")).isEqualTo(2);
        assertThat(result.get("messageId")).isEqualTo("MSG_001");
    }

    // ==================== 分页查询 ====================

    @Test
    @DisplayName("listTrackings: 返回分页结果")
    void listTrackings_success() {
        ScrmMessageTrackingEntity entity = buildTrackingEntity(100L);
        Page<ScrmMessageTrackingEntity> page = new PageImpl<>(
                List.of(entity), PageRequest.of(0, 10), 1);
        when(trackingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmMessageTrackingEntity> result = service.listTrackings(null, null, null,
                "WECHAT", null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
    }

    // ==================== 阅读统计 ====================

    @Test
    @DisplayName("getReadStats: 返回阅读次数 / 独立阅读者 / 重复阅读数 / 平均时长")
    void getReadStats_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findById(100L)).thenReturn(Optional.of(tracking));
        when(readLogRepository.countByMessageTrackingId(100L)).thenReturn(5L);
        when(readLogRepository.countDistinctReader(100L)).thenReturn(3L);
        when(readLogRepository.countRepeatedRead(100L)).thenReturn(2L);
        when(readLogRepository.avgReadDuration(100L)).thenReturn(15.5);

        Map<String, Object> stats = service.getReadStats(100L);

        assertThat(stats.get("messageTrackingId")).isEqualTo(100L);
        assertThat(stats.get("readCount")).isEqualTo(5L);
        assertThat(stats.get("uniqueReaders")).isEqualTo(3L);
        assertThat(stats.get("repeatedReads")).isEqualTo(2L);
        assertThat(stats.get("avgReadDurationSeconds")).isEqualTo(15.5);
    }

    @Test
    @DisplayName("getReadStats: 跟踪记录不存在抛 NOT_FOUND")
    void getReadStats_notFound() {
        when(trackingRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReadStats(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息跟踪记录不存在");
    }

    @Test
    @DisplayName("getReadLogs: 跟踪记录不存在抛 NOT_FOUND")
    void getReadLogs_notFound() {
        when(trackingRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getReadLogs(999L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("消息跟踪记录不存在");
    }

    @Test
    @DisplayName("getReadLogs: 存在时返回阅读日志列表")
    void getReadLogs_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findById(100L)).thenReturn(Optional.of(tracking));
        ScrmMessageReadLogEntity log = new ScrmMessageReadLogEntity();
        log.setId(500L);
        when(readLogRepository.findByMessageTrackingIdOrderByReadAtAsc(100L))
                .thenReturn(List.of(log));

        List<ScrmMessageReadLogEntity> result = service.getReadLogs(100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(500L);
    }

    // ==================== 转发追踪 ====================

    @Test
    @DisplayName("recordForward: 写入 forwardCount / firstForwardedAt / metadata forwardChain")
    void recordForward_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTrackingEntity result = service.recordForward("MSG_001", "F001");

        assertThat(result.getIsForwarded()).isTrue();
        assertThat(result.getForwardCount()).isEqualTo(1);
        assertThat(result.getFirstForwardedAt()).isNotNull();
        assertThat(result.getMetadata()).contains("forwardChain").contains("F001");
        // 互动评分: forwardCount(1)*2.0 = 2.0
        assertThat(result.getEngagementScore()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("recordForward: forwarderId 为空抛 BAD_REQUEST")
    void recordForward_blankForwarderId() {
        assertThatThrownBy(() -> service.recordForward("MSG_001", ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("转发者 ID 不能为空");
    }

    @Test
    @DisplayName("getForwardChain: 解析 metadata forwardChain 返回事件列表")
    void getForwardChain_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        tracking.setMetadata("{\"forwardChain\":[{\"forwarderId\":\"F001\",\"sequence\":1}]}");
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));

        List<Map<String, Object>> chain = service.getForwardChain("MSG_001");

        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).get("forwarderId")).isEqualTo("F001");
    }

    @Test
    @DisplayName("getForwardChain: metadata 无 forwardChain 返回空列表")
    void getForwardChain_empty() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));

        List<Map<String, Object>> chain = service.getForwardChain("MSG_001");

        assertThat(chain).isEmpty();
    }

    // ==================== 回复追踪 ====================

    @Test
    @DisplayName("recordReply: 写入 isReplied / repliedAt / replyContent / metadata replyHistory")
    void recordReply_success() throws Exception {
        ScrmMessageTrackingEntity tracking = buildTrackingEntity(100L);
        when(trackingRepository.findByMessageId("MSG_001"))
                .thenReturn(Optional.of(tracking));
        when(trackingRepository.save(any(ScrmMessageTrackingEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMessageTrackingEntity result = service.recordReply("MSG_001", "好的, 收到", "R001");

        assertThat(result.getIsReplied()).isTrue();
        assertThat(result.getRepliedAt()).isNotNull();
        assertThat(result.getReplyContent()).isEqualTo("好的, 收到");
        assertThat(result.getMetadata()).contains("replyHistory").contains("R001");
        // 互动评分: isReplied ? +5.0 = 5.0
        assertThat(result.getEngagementScore()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("recordReply: replyContent 为空抛 BAD_REQUEST")
    void recordReply_blankContent() {
        assertThatThrownBy(() -> service.recordReply("MSG_001", "", "R001"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("回复内容不能为空");
    }

    @Test
    @DisplayName("recordReply: replierId 为空抛 BAD_REQUEST")
    void recordReply_blankReplierId() {
        assertThatThrownBy(() -> service.recordReply("MSG_001", "好的", ""))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("回复者 ID 不能为空");
    }

    // ==================== 统计分析 ====================

    @Test
    @DisplayName("getTrackingStats: 返回发送 / 送达 / 已读 / 撤回 / 转发 / 回复统计")
    void getTrackingStats_success() {
        when(trackingRepository.countSentInRange(any(), any())).thenReturn(10L);
        when(trackingRepository.countDeliveredInRange(any(), any())).thenReturn(8L);
        when(trackingRepository.countReadInRange(any(), any())).thenReturn(5L);
        when(trackingRepository.countRecalledInRange(any(), any())).thenReturn(1L);
        when(trackingRepository.countForwardedInRange(any(), any())).thenReturn(2L);
        when(trackingRepository.countRepliedInRange(any(), any())).thenReturn(3L);

        Map<String, Object> stats = service.getTrackingStats(null, null);

        assertThat(stats.get("totalSent")).isEqualTo(10L);
        assertThat(stats.get("deliveredCount")).isEqualTo(8L);
        assertThat(stats.get("readCount")).isEqualTo(5L);
        assertThat(stats.get("recalledCount")).isEqualTo(1L);
        assertThat(stats.get("forwardedCount")).isEqualTo(2L);
        assertThat(stats.get("repliedCount")).isEqualTo(3L);
        assertThat((Double) stats.get("readRate")).isEqualTo(0.5);
        assertThat((Double) stats.get("deliveryRate")).isEqualTo(0.8);
    }

    @Test
    @DisplayName("getTrackingStats: 无数据时比率字段为 0.0, 不抛除零异常")
    void getTrackingStats_empty() {
        when(trackingRepository.countSentInRange(any(), any())).thenReturn(0L);
        when(trackingRepository.countDeliveredInRange(any(), any())).thenReturn(0L);
        when(trackingRepository.countReadInRange(any(), any())).thenReturn(0L);
        when(trackingRepository.countRecalledInRange(any(), any())).thenReturn(0L);
        when(trackingRepository.countForwardedInRange(any(), any())).thenReturn(0L);
        when(trackingRepository.countRepliedInRange(any(), any())).thenReturn(0L);

        Map<String, Object> stats = service.getTrackingStats(null, null);

        assertThat((Double) stats.get("readRate")).isEqualTo(0.0);
        assertThat((Double) stats.get("deliveryRate")).isEqualTo(0.0);
    }

    // ==================== 辅助方法 ====================

    private ScrmMessageTrackingDto buildTrackingDto() {
        ScrmMessageTrackingDto dto = new ScrmMessageTrackingDto();
        dto.setMessageId("MSG_001");
        dto.setSenderId("S001");
        dto.setSenderName("发送者");
        dto.setRecipientId("R001");
        dto.setRecipientName("接收者");
        dto.setChannel("WECHAT");
        dto.setMessageContent("Hello");
        return dto;
    }

    private ScrmMessageTrackingEntity buildTrackingEntity(Long id) {
        ScrmMessageTrackingEntity entity = new ScrmMessageTrackingEntity();
        entity.setId(id);
        entity.setMessageId("MSG_001");
        entity.setSenderId("S001");
        entity.setSenderName("发送者");
        entity.setRecipientId("R001");
        entity.setRecipientName("接收者");
        entity.setRecipientType("CUSTOMER");
        entity.setChannel("WECHAT");
        entity.setContentType("TEXT");
        entity.setSendStatus("PENDING");
        entity.setSentAt(LocalDateTime.now());
        entity.setReadCount(0);
        entity.setIsRead(false);
        entity.setIsRecalled(false);
        entity.setForwardCount(0);
        entity.setIsForwarded(false);
        entity.setIsReplied(false);
        entity.setEngagementScore(0.0);
        entity.setReadDurationSeconds(0);
        return entity;
    }

    private ScrmMessageRecallEntity buildRecallEntity(Long id) {
        ScrmMessageRecallEntity recall = new ScrmMessageRecallEntity();
        recall.setId(id);
        recall.setMessageTrackingId(100L);
        recall.setMessageId("MSG_001");
        recall.setSenderId("S001");
        recall.setSenderName("发送者");
        recall.setRecallType("MANUAL");
        recall.setRecallStatus("PENDING");
        recall.setTotalRecipients(1);
        recall.setSuccessfulRecalls(0);
        recall.setFailedRecalls(0);
        recall.setRecalledAt(LocalDateTime.now());
        recall.setRecallWindowMinutes(2);
        recall.setIsWithinWindow(true);
        return recall;
    }

    private ScrmMessageReadReportDto buildReadReportDto() {
        ScrmMessageReadReportDto report = new ScrmMessageReadReportDto();
        report.setMessageId("MSG_001");
        report.setReaderId("U001");
        report.setReaderName("张三");
        report.setReadDuration(30);
        report.setSource("APP");
        return report;
    }

    private ScrmMessageTrackingEntity assignId(ScrmMessageTrackingEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmMessageRecallEntity assignRecallId(ScrmMessageRecallEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
