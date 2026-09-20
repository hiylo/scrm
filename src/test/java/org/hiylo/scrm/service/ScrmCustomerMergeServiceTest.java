/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerMergeServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmCustomerMergeRequestDto;
import org.hiylo.scrm.entity.ScrmCustomerDuplicateEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmCustomerMergeRecordEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCustomerDuplicateRepository;
import org.hiylo.scrm.repository.ScrmCustomerMergeRecordRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.vo.CustomerMergeStatsVo;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
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
 * ScrmCustomerMergeService 单元测试
 * <p>
 * 聚焦客户去重检测 (精确/模糊匹配)、重复确认/忽略、客户合并校验与记录创建、
 * 合并回滚与状态恢复、分页查询与统计聚合等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmCustomerMergeService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmCustomerMergeServiceTest {

    /** 客户档案仓库 Mock */
    @Mock
    private ScrmCustomerRepository customerRepository;
    /** 客户重复检测记录仓库 Mock */
    @Mock
    private ScrmCustomerDuplicateRepository duplicateRepository;
    /** 客户合并记录仓库 Mock */
    @Mock
    private ScrmCustomerMergeRecordRepository mergeRecordRepository;

    /** 被测服务实例 */
    private ScrmCustomerMergeService service;

    @BeforeEach
    void setUp() {
        service = new ScrmCustomerMergeService(customerRepository, duplicateRepository, mergeRecordRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造已持久化的客户实体
     */
    private ScrmCustomerEntity buildCustomerEntity(Long id, String platformType, String platformUid, String nickname) {
        ScrmCustomerEntity entity = new ScrmCustomerEntity();
        entity.setId(id);
        entity.setPlatformType(platformType);
        entity.setPlatformCustomerUid(platformUid);
        entity.setNickname(nickname);
        entity.setOwnerAccountId(100L);
        entity.setLifecycle("ACTIVE");
        return entity;
    }

    /**
     * 构造已持久化的重复检测实体
     */
    private ScrmCustomerDuplicateEntity buildDuplicateEntity(Long id, Long customerId, Long duplicateCustomerId, String status) {
        ScrmCustomerDuplicateEntity entity = new ScrmCustomerDuplicateEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setDuplicateCustomerId(duplicateCustomerId);
        entity.setMatchType("EXACT");
        entity.setMatchScore(new BigDecimal("100"));
        entity.setMatchCriteria("platformType+platformCustomerUid");
        entity.setStatus(status);
        entity.setDetectedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 构造已持久化的合并记录实体
     */
    private ScrmCustomerMergeRecordEntity buildMergeRecordEntity(Long id, Long primaryId, String mergedIds, String status) {
        ScrmCustomerMergeRecordEntity entity = new ScrmCustomerMergeRecordEntity();
        entity.setId(id);
        entity.setPrimaryCustomerId(primaryId);
        entity.setMergedCustomerIds(mergedIds);
        entity.setMergeStrategy("MANUAL");
        entity.setMatchCriteria("manual");
        entity.setStatus(status);
        entity.setMergedAt(LocalDateTime.now());
        return entity;
    }

    /**
     * 构造合并请求 DTO
     */
    private ScrmCustomerMergeRequestDto buildMergeRequest(Long primaryId, List<Long> customerIds) {
        ScrmCustomerMergeRequestDto dto = new ScrmCustomerMergeRequestDto();
        dto.setPrimaryCustomerId(primaryId);
        dto.setCustomerIds(customerIds);
        return dto;
    }

    // ==================== 重复检测 ====================

    @Test
    @DisplayName("detectDuplicates: 客户数不足 2 时返回 0 且不创建记录")
    void detectDuplicates_lessThanTwoCustomers() {
        when(customerRepository.findAll())
                .thenReturn(Collections.singletonList(
                        buildCustomerEntity(10L, "wx", "uid1", "Alice")));

        int detected = service.detectDuplicates();

        assertThat(detected).isZero();
        verify(duplicateRepository, never()).save(any());
    }

    @Test
    @DisplayName("detectDuplicates: 精确匹配检测到相同平台 UID 的客户对")
    void detectDuplicates_exactMatch() {
        ScrmCustomerEntity c1 = buildCustomerEntity(10L, "wx", "uid1", "Alice");
        ScrmCustomerEntity c2 = buildCustomerEntity(11L, "wx", "uid1", "Bob");
        when(customerRepository.findAll()).thenReturn(Arrays.asList(c1, c2));
        when(duplicateRepository.findByCustomerIdIn(any()))
                .thenReturn(Collections.emptyList());

        int detected = service.detectDuplicates();

        assertThat(detected).isEqualTo(1);
        ArgumentCaptor<ScrmCustomerDuplicateEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerDuplicateEntity.class);
        verify(duplicateRepository, times(1)).save(captor.capture());
        ScrmCustomerDuplicateEntity saved = captor.getValue();
        assertThat(saved.getMatchType()).isEqualTo("EXACT");
        assertThat(saved.getMatchScore()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getCustomerId()).isIn(10L, 11L);
        assertThat(saved.getDuplicateCustomerId()).isIn(10L, 11L);
    }

    @Test
    @DisplayName("detectDuplicates: 模糊匹配检测到相同昵称的客户对")
    void detectDuplicates_fuzzyMatch() {
        ScrmCustomerEntity c1 = buildCustomerEntity(10L, "wx", "uid1", "Alice");
        ScrmCustomerEntity c2 = buildCustomerEntity(11L, "wx", "uid2", "Alice");
        when(customerRepository.findAll()).thenReturn(Arrays.asList(c1, c2));
        when(duplicateRepository.findByCustomerIdIn(any()))
                .thenReturn(Collections.emptyList());

        int detected = service.detectDuplicates();

        assertThat(detected).isEqualTo(1);
        ArgumentCaptor<ScrmCustomerDuplicateEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerDuplicateEntity.class);
        verify(duplicateRepository, times(1)).save(captor.capture());
        ScrmCustomerDuplicateEntity saved = captor.getValue();
        assertThat(saved.getMatchType()).isEqualTo("FUZZY");
        assertThat(saved.getMatchScore()).isEqualByComparingTo(new BigDecimal("60"));
        assertThat(saved.getMatchCriteria()).isEqualTo("nickname");
    }

    @Test
    @DisplayName("detectDuplicates: 已存在的重复对不重复创建")
    void detectDuplicates_skipsExistingPair() {
        ScrmCustomerEntity c1 = buildCustomerEntity(10L, "wx", "uid1", "Alice");
        ScrmCustomerEntity c2 = buildCustomerEntity(11L, "wx", "uid1", "Bob");
        when(customerRepository.findAll()).thenReturn(Arrays.asList(c1, c2));
        // 正向检查命中已存在 (customerId=10 -> duplicateCustomerId=11)
        when(duplicateRepository.findByCustomerIdIn(any()))
                .thenReturn(Collections.singletonList(
                        buildDuplicateEntity(1L, 10L, 11L, "PENDING")));

        int detected = service.detectDuplicates();

        assertThat(detected).isZero();
        verify(duplicateRepository, never()).save(any());
    }

    // ==================== 重复确认/忽略 ====================

    @Test
    @DisplayName("getDuplicates: 带 status 过滤调用对应方法")
    void getDuplicates_withStatus() {
        Page<ScrmCustomerDuplicateEntity> page = new PageImpl<>(Collections.emptyList());
        when(duplicateRepository.findByStatusOrderByDetectedAtDesc(eq("PENDING"), any(Pageable.class))).thenReturn(page);

        service.getDuplicates("PENDING", 0, 10);

        verify(duplicateRepository, times(1))
                .findByStatusOrderByDetectedAtDesc(eq("PENDING"), any(Pageable.class));
        verify(duplicateRepository, never())
                .findAllByOrderByDetectedAtDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("getDuplicates: 不带 status 调用全量方法")
    void getDuplicates_withoutStatus() {
        Page<ScrmCustomerDuplicateEntity> page = new PageImpl<>(Collections.emptyList());
        when(duplicateRepository.findAllByOrderByDetectedAtDesc(any(Pageable.class)))
                .thenReturn(page);

        service.getDuplicates(null, 0, 10);

        verify(duplicateRepository, times(1))
                .findAllByOrderByDetectedAtDesc(any(Pageable.class));
    }

    @Test
    @DisplayName("confirmDuplicate: PENDING → CONFIRMED 并记录处理人与时间")
    void confirmDuplicate_success() throws ScrmException {
        ScrmCustomerDuplicateEntity entity = buildDuplicateEntity(1L, 10L, 11L, "PENDING");
        when(duplicateRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(duplicateRepository.save(any(ScrmCustomerDuplicateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerDuplicateEntity result = service.confirmDuplicate(1L);

        ArgumentCaptor<ScrmCustomerDuplicateEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerDuplicateEntity.class);
        verify(duplicateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("confirmDuplicate: 记录不存在抛 NOT_FOUND")
    void confirmDuplicate_notFound() {
        when(duplicateRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmDuplicate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("重复检测记录不存在");
        verify(duplicateRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmDuplicate: 非 PENDING 状态抛 BAD_REQUEST")
    void confirmDuplicate_invalidStatus() {
        ScrmCustomerDuplicateEntity entity = buildDuplicateEntity(1L, 10L, 11L, "CONFIRMED");
        when(duplicateRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.confirmDuplicate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅待处理状态可确认");
        verify(duplicateRepository, never()).save(any());
    }

    @Test
    @DisplayName("ignoreDuplicate: 任意状态 → IGNORED 并记录处理人与时间")
    void ignoreDuplicate_success() throws ScrmException {
        ScrmCustomerDuplicateEntity entity = buildDuplicateEntity(1L, 10L, 11L, "PENDING");
        when(duplicateRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(duplicateRepository.save(any(ScrmCustomerDuplicateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerDuplicateEntity result = service.ignoreDuplicate(1L);

        ArgumentCaptor<ScrmCustomerDuplicateEntity> captor =
                ArgumentCaptor.forClass(ScrmCustomerDuplicateEntity.class);
        verify(duplicateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("IGNORED");
        assertThat(captor.getValue().getResolvedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("IGNORED");
    }

    @Test
    @DisplayName("ignoreDuplicate: 记录不存在抛 NOT_FOUND")
    void ignoreDuplicate_notFound() {
        when(duplicateRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ignoreDuplicate(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("重复检测记录不存在");
        verify(duplicateRepository, never()).save(any());
    }

    // ==================== 客户合并 ====================

    @Test
    @DisplayName("mergeCustomers: 请求为空抛 BAD_REQUEST")
    void mergeCustomers_nullRequest() {
        assertThatThrownBy(() -> service.mergeCustomers(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合并请求不能为空");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeCustomers: 主客户 ID 为空抛 BAD_REQUEST")
    void mergeCustomers_nullPrimaryId() {
        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(null, List.of(11L))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("主客户 ID 不能为空");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeCustomers: 待合并列表为空抛 BAD_REQUEST")
    void mergeCustomers_emptyCustomerIds() {
        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(10L, Collections.emptyList())))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("待合并客户 ID 列表不能为空");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeCustomers: 主客户在待合并列表中抛 BAD_REQUEST")
    void mergeCustomers_primaryInList() {
        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(10L, Arrays.asList(10L, 11L))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("主客户不能在待合并列表中");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeCustomers: 待合并列表存在重复 ID 抛 BAD_REQUEST")
    void mergeCustomers_duplicateIds() {
        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(10L, Arrays.asList(11L, 11L))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("待合并客户 ID 列表存在重复");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("mergeCustomers: 主客户不存在抛 NOT_FOUND")
    void mergeCustomers_primaryNotFound() {
        when(customerRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(10L, List.of(11L))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("主客户不存在");
        verify(mergeRecordRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("mergeCustomers: 待合并客户不存在抛 NOT_FOUND")
    void mergeCustomers_mergedCustomerNotFound() {
        ScrmCustomerEntity primary = buildCustomerEntity(10L, "wx", "uid1", "Alice");
        when(customerRepository.findById(10L)).thenReturn(Optional.of(primary));
        when(customerRepository.findAllById(any()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.mergeCustomers(buildMergeRequest(10L, List.of(11L))))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("待合并客户不存在");
        verify(mergeRecordRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("mergeCustomers: 合并成功创建记录, 默认策略 MANUAL, 关联重复检测置 MERGED")
    void mergeCustomers_success() throws ScrmException {
        ScrmCustomerEntity primary = buildCustomerEntity(10L, "wx", "uid1", "Alice");
        ScrmCustomerEntity merged = buildCustomerEntity(11L, "wx", "uid2", "Bob");
        when(customerRepository.findById(10L)).thenReturn(Optional.of(primary));
        when(customerRepository.findAllById(any()))
                .thenReturn(Collections.singletonList(merged));
        when(mergeRecordRepository.save(any(ScrmCustomerMergeRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmCustomerDuplicateEntity dup = buildDuplicateEntity(1L, 11L, 12L, "PENDING");
        when(duplicateRepository.findByCustomerIdIn(any()))
                .thenReturn(Collections.singletonList(dup));
        when(duplicateRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerMergeRecordEntity result = service.mergeCustomers(buildMergeRequest(10L, List.of(11L)));

        ArgumentCaptor<ScrmCustomerMergeRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(ScrmCustomerMergeRecordEntity.class);
        verify(mergeRecordRepository, times(1)).save(recordCaptor.capture());
        ScrmCustomerMergeRecordEntity savedRecord = recordCaptor.getValue();
        assertThat(savedRecord.getPrimaryCustomerId()).isEqualTo(10L);
        assertThat(savedRecord.getMergedCustomerIds()).isEqualTo("11");
        assertThat(savedRecord.getMergeStrategy()).isEqualTo("MANUAL");
        assertThat(savedRecord.getStatus()).isEqualTo("COMPLETED");
        assertThat(savedRecord.getMergedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");

        // 关联的 PENDING 重复检测记录置为 MERGED
        ArgumentCaptor<List<ScrmCustomerDuplicateEntity>> dupCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(duplicateRepository, times(1)).saveAll(dupCaptor.capture());
        assertThat(dupCaptor.getValue()).hasSize(1);
        assertThat(dupCaptor.getValue().get(0).getStatus()).isEqualTo("MERGED");
        assertThat(dupCaptor.getValue().get(0).getResolvedAt()).isNotNull();
    }

    // ==================== 合并回滚 ====================

    @Test
    @DisplayName("revertMerge: 记录不存在抛 NOT_FOUND")
    void revertMerge_notFound() {
        when(mergeRecordRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revertMerge(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("合并记录不存在");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("revertMerge: 非 COMPLETED 状态抛 BAD_REQUEST")
    void revertMerge_invalidStatus() {
        ScrmCustomerMergeRecordEntity record = buildMergeRecordEntity(1L, 10L, "11", "REVERTED");
        when(mergeRecordRepository.findById(1L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.revertMerge(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仅已完成的合并可回滚");
        verify(mergeRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("revertMerge: COMPLETED → REVERTED 并恢复关联重复检测为 CONFIRMED")
    void revertMerge_success() throws ScrmException {
        ScrmCustomerMergeRecordEntity record = buildMergeRecordEntity(1L, 10L, "11,12", "COMPLETED");
        when(mergeRecordRepository.findById(1L)).thenReturn(Optional.of(record));
        when(mergeRecordRepository.save(any(ScrmCustomerMergeRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmCustomerDuplicateEntity dup1 = buildDuplicateEntity(2L, 11L, 13L, "MERGED");
        ScrmCustomerDuplicateEntity dup2 = buildDuplicateEntity(3L, 12L, 14L, "MERGED");
        when(duplicateRepository.findByCustomerIdIn(any()))
                .thenReturn(Arrays.asList(dup1, dup2));
        when(duplicateRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmCustomerMergeRecordEntity result = service.revertMerge(1L);

        ArgumentCaptor<ScrmCustomerMergeRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(ScrmCustomerMergeRecordEntity.class);
        verify(mergeRecordRepository, times(1)).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo("REVERTED");
        assertThat(recordCaptor.getValue().getRevertedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("REVERTED");

        // 两条 MERGED 重复检测记录恢复为 CONFIRMED
        ArgumentCaptor<List<ScrmCustomerDuplicateEntity>> dupCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(duplicateRepository, times(1)).saveAll(dupCaptor.capture());
        assertThat(dupCaptor.getValue()).hasSize(2);
        assertThat(dupCaptor.getValue()).allSatisfy(d ->
                assertThat(d.getStatus()).isEqualTo("CONFIRMED"));
    }

    // ==================== 合并记录查询 ====================

    @Test
    @DisplayName("getMergeRecords: 带 status 过滤调用对应方法")
    void getMergeRecords_withStatus() {
        Page<ScrmCustomerMergeRecordEntity> page = new PageImpl<>(Collections.emptyList());
        when(mergeRecordRepository.findByStatusOrderByMergedAtDesc(eq("COMPLETED"), any(Pageable.class))).thenReturn(page);

        service.getMergeRecords("COMPLETED", 0, 10);

        verify(mergeRecordRepository, times(1))
                .findByStatusOrderByMergedAtDesc(eq("COMPLETED"), any(Pageable.class));
    }

    @Test
    @DisplayName("getMergeRecords: 不带 status 调用全量方法")
    void getMergeRecords_withoutStatus() {
        Page<ScrmCustomerMergeRecordEntity> page = new PageImpl<>(Collections.emptyList());
        when(mergeRecordRepository.findAllByOrderByMergedAtDesc(any(Pageable.class)))
                .thenReturn(page);

        service.getMergeRecords(null, 0, 10);

        verify(mergeRecordRepository, times(1))
                .findAllByOrderByMergedAtDesc(any(Pageable.class));
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("getStats: 聚合重复检测数与合并记录数")
    void getStats_success() {
        when(duplicateRepository.count()).thenReturn(20L);
        when(duplicateRepository.countByStatus("PENDING")).thenReturn(5L);
        when(duplicateRepository.countByStatus("CONFIRMED")).thenReturn(8L);
        when(duplicateRepository.countByStatus("IGNORED")).thenReturn(2L);
        when(duplicateRepository.countByStatus("MERGED")).thenReturn(5L);
        when(mergeRecordRepository.count()).thenReturn(3L);

        CustomerMergeStatsVo vo = service.getStats();

        assertThat(vo.getTotalDuplicates()).isEqualTo(20L);
        assertThat(vo.getPendingCount()).isEqualTo(5L);
        assertThat(vo.getConfirmedCount()).isEqualTo(8L);
        assertThat(vo.getIgnoredCount()).isEqualTo(2L);
        assertThat(vo.getMergedCount()).isEqualTo(5L);
        assertThat(vo.getTotalMergeRecords()).isEqualTo(3L);
        // completedMerges 简化为总数, revertedMerges 简化为 0 (当前实现行为)
        assertThat(vo.getCompletedMerges()).isEqualTo(3L);
        assertThat(vo.getRevertedMerges()).isZero();
    }

    @Test
    @DisplayName("getStats: 无数据时全部为 0")
    void getStats_empty() {
        when(duplicateRepository.count()).thenReturn(0L);
        when(duplicateRepository.countByStatus(any())).thenReturn(0L);
        when(mergeRecordRepository.count()).thenReturn(0L);

        CustomerMergeStatsVo vo = service.getStats();

        assertThat(vo.getTotalDuplicates()).isZero();
        assertThat(vo.getPendingCount()).isZero();
        assertThat(vo.getConfirmedCount()).isZero();
        assertThat(vo.getIgnoredCount()).isZero();
        assertThat(vo.getMergedCount()).isZero();
        assertThat(vo.getTotalMergeRecords()).isZero();
        assertThat(vo.getCompletedMerges()).isZero();
        assertThat(vo.getRevertedMerges()).isZero();
    }
}
