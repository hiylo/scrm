/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesTargetServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmSalesAchievementDto;
import org.hiylo.scrm.dto.ScrmSalesForecastDto;
import org.hiylo.scrm.dto.ScrmSalesTargetDto;
import org.hiylo.scrm.entity.ScrmSalesAchievementEntity;
import org.hiylo.scrm.entity.ScrmSalesTargetEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmSalesAchievementRepository;
import org.hiylo.scrm.repository.ScrmSalesRankingRepository;
import org.hiylo.scrm.repository.ScrmSalesTargetRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * ScrmSalesTargetService 单元测试
 * <p>
 * 聚焦销售目标管理 (创建 / 更新 / 归档 / 校验)、达成记录 (录入 / 批量 / 手动调整)、
 * 排名计算 (按达成值降序)、业绩预测 (线性回归)、达成率分布统计、
 * 达成率计算与越权隔离等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmSalesTargetService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmSalesTargetServiceTest {

    /** 销售目标仓库 Mock */
    @Mock
    private ScrmSalesTargetRepository targetRepository;
    /** 销售达成记录仓库 Mock */
    @Mock
    private ScrmSalesAchievementRepository achievementRepository;
    /** 销售排名仓库 Mock */
    @Mock
    private ScrmSalesRankingRepository rankingRepository;

    /** 被测服务实例 */
    private ScrmSalesTargetService service;

    @BeforeEach
    void setUp() {
        service = new ScrmSalesTargetService(targetRepository, achievementRepository, rankingRepository);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造创建目标参数 DTO
     */
    private ScrmSalesTargetDto buildCreateDto() {
        ScrmSalesTargetDto dto = new ScrmSalesTargetDto();
        dto.setTargetName("Q4 销售目标");
        dto.setTargetType("INDIVIDUAL");
        dto.setTargetId("user_001");
        dto.setTargetNameRef("张三");
        dto.setPeriodType("QUARTERLY");
        dto.setPeriodStart(LocalDate.of(2026, 10, 1));
        dto.setPeriodEnd(LocalDate.of(2026, 12, 31));
        dto.setMetricType("AMOUNT");
        dto.setTargetValue(100000d);
        return dto;
    }

    /**
     * 构造已持久化的销售目标实体 (用于 findById 返回)
     */
    private ScrmSalesTargetEntity buildTargetEntity(Long id) {
        ScrmSalesTargetEntity entity = new ScrmSalesTargetEntity();
        entity.setId(id);
        entity.setTargetName("Q4 销售目标");
        entity.setTargetType("INDIVIDUAL");
        entity.setTargetId("user_001");
        entity.setTargetNameRef("张三");
        entity.setPeriodType("QUARTERLY");
        entity.setPeriodStart(LocalDate.of(2026, 10, 1));
        entity.setPeriodEnd(LocalDate.of(2026, 12, 31));
        entity.setMetricType("AMOUNT");
        entity.setTargetValue(100000d);
        entity.setActualValue(0d);
        entity.setAchievementRate(0d);
        entity.setStatus("ACTIVE");
        return entity;
    }

    /**
     * 构造已持久化的达成记录实体 (用于查询返回)
     */
    private ScrmSalesAchievementEntity buildAchievementEntity(Long id, Double value, LocalDate date) {
        ScrmSalesAchievementEntity entity = new ScrmSalesAchievementEntity();
        entity.setId(id);
        entity.setTargetId(10L);
        entity.setAchievedValue(value);
        entity.setAchievementDate(date);
        entity.setSourceType("MANUAL_ADJUST");
        return entity;
    }

    @Test
    @DisplayName("createTarget: 写入账号 ID 与默认值后持久化")
    void createTarget_success() throws ScrmException {
        ScrmSalesTargetDto dto = buildCreateDto();
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSalesTargetDto result = service.createTarget(dto);

        ArgumentCaptor<ScrmSalesTargetEntity> captor =
                ArgumentCaptor.forClass(ScrmSalesTargetEntity.class);
        verify(targetRepository, times(1)).save(captor.capture());
        ScrmSalesTargetEntity saved = captor.getValue();
        // actualValue 初值为 0
        assertThat(saved.getActualValue()).isEqualTo(0.0);
        // achievementRate 初值为 0
        assertThat(saved.getAchievementRate()).isEqualTo(0.0);
        // status 缺省时填 ACTIVE
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        // createdBy 缺省时填 scrm-system
        assertThat(saved.getCreatedBy()).isEqualTo("scrm-system");
        assertThat(result.getTargetName()).isEqualTo("Q4 销售目标");
    }

    @Test
    @DisplayName("createTarget: 周期结束日期早于开始日期抛 BAD_REQUEST")
    void createTarget_invalidPeriod() {
        ScrmSalesTargetDto dto = buildCreateDto();
        dto.setPeriodStart(LocalDate.of(2026, 12, 1));
        dto.setPeriodEnd(LocalDate.of(2026, 10, 1));

        assertThatThrownBy(() -> service.createTarget(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("周期结束日期不能早于开始日期");
        verify(targetRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTarget: 目标值 ≤ 0 抛 BAD_REQUEST")
    void createTarget_nonPositiveValue() {
        ScrmSalesTargetDto dto = buildCreateDto();
        dto.setTargetValue(0d);

        assertThatThrownBy(() -> service.createTarget(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("目标值必须大于 0");
        verify(targetRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("updateTarget: 目标值变更后重新计算达成率")
    void updateTarget_recalculateRate() throws ScrmException {
        ScrmSalesTargetEntity entity = buildTargetEntity(10L);
        entity.setActualValue(50000d);
        entity.setAchievementRate(50d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSalesTargetDto dto = new ScrmSalesTargetDto();
        dto.setTargetValue(50000d); // 目标值改为 50000, 实际值 50000 → 达成率 100%
        service.updateTarget(10L, dto);

        ArgumentCaptor<ScrmSalesTargetEntity> captor =
                ArgumentCaptor.forClass(ScrmSalesTargetEntity.class);
        verify(targetRepository, times(1)).save(captor.capture());
        // 达成率 = 50000/50000*100 = 100
        assertThat(captor.getValue().getAchievementRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("archiveTarget: 设置状态为 ARCHIVED")
    void archiveTarget_success() throws ScrmException {
        ScrmSalesTargetEntity entity = buildTargetEntity(10L);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.archiveTarget(10L);

        ArgumentCaptor<ScrmSalesTargetEntity> captor =
                ArgumentCaptor.forClass(ScrmSalesTargetEntity.class);
        verify(targetRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("recordAchievement: 写入记录并累加目标实际值, 重算达成率")
    void recordAchievement_success() throws ScrmException {
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        target.setActualValue(30000d);
        target.setAchievementRate(30d);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(achievementRepository.save(any(ScrmSalesAchievementEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // recalculateTarget 调用: 历史记录累加后 actualValue=80000
        when(achievementRepository.findByTargetIdOrderByAchievementDateAsc(10L))
                .thenReturn(List.of(buildAchievementEntity(1L, 30000d, LocalDate.of(2026, 10, 1)),
                        buildAchievementEntity(2L, 50000d, LocalDate.of(2026, 10, 2))));
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSalesAchievementDto dto = new ScrmSalesAchievementDto();
        dto.setTargetId(10L);
        dto.setAchievedValue(50000d);
        dto.setAchievementDate(LocalDate.of(2026, 10, 15));
        ScrmSalesAchievementDto result = service.recordAchievement(dto);

        // 验证达成记录写入账号 ID
        ArgumentCaptor<ScrmSalesAchievementEntity> achCaptor =
                ArgumentCaptor.forClass(ScrmSalesAchievementEntity.class);
        verify(achievementRepository, times(1)).save(achCaptor.capture());
        // 验证目标重算: actualValue = 30000 + 50000 = 80000, rate = 80
        ArgumentCaptor<ScrmSalesTargetEntity> targetCaptor =
                ArgumentCaptor.forClass(ScrmSalesTargetEntity.class);
        verify(targetRepository, times(1)).save(targetCaptor.capture());
        assertThat(targetCaptor.getValue().getActualValue()).isEqualTo(80000.0);
        assertThat(targetCaptor.getValue().getAchievementRate()).isEqualTo(80.0);
        assertThat(result.getAchievedValue()).isEqualTo(50000d);
    }

    @Test
    @DisplayName("recordAchievement: 达成率 ≥100% 时自动置为 COMPLETED")
    void recordAchievement_autoComplete() throws ScrmException {
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        target.setStatus("ACTIVE");
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(achievementRepository.save(any(ScrmSalesAchievementEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // 历史累加值 = 100000, 达成率 100% → 自动 COMPLETED
        when(achievementRepository.findByTargetIdOrderByAchievementDateAsc(10L))
                .thenReturn(List.of(buildAchievementEntity(1L, 100000d, LocalDate.of(2026, 10, 1))));
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSalesAchievementDto dto = new ScrmSalesAchievementDto();
        dto.setTargetId(10L);
        dto.setAchievedValue(100000d);
        dto.setAchievementDate(LocalDate.of(2026, 10, 15));
        service.recordAchievement(dto);

        ArgumentCaptor<ScrmSalesTargetEntity> captor =
                ArgumentCaptor.forClass(ScrmSalesTargetEntity.class);
        verify(targetRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("batchRecordAchievements: 部分目标不存在时跳过, 返回成功记录")
    void batchRecordAchievements_partialSuccess() throws ScrmException {
        // 第一条目标存在, 第二条目标不存在
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        when(targetRepository.findById(11L)).thenReturn(Optional.empty());
        when(achievementRepository.save(any(ScrmSalesAchievementEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(achievementRepository.findByTargetIdOrderByAchievementDateAsc(10L))
                .thenReturn(Collections.emptyList());
        when(targetRepository.save(any(ScrmSalesTargetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSalesAchievementDto dto1 = new ScrmSalesAchievementDto();
        dto1.setTargetId(10L);
        dto1.setAchievedValue(1000d);
        dto1.setAchievementDate(LocalDate.of(2026, 10, 15));
        ScrmSalesAchievementDto dto2 = new ScrmSalesAchievementDto();
        dto2.setTargetId(11L);
        dto2.setAchievedValue(2000d);
        dto2.setAchievementDate(LocalDate.of(2026, 10, 15));
        List<ScrmSalesAchievementDto> results = service.batchRecordAchievements(List.of(dto1, dto2));

        // 仅第一条成功
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTargetId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("adjustAchievement: 调整值为 0 抛 BAD_REQUEST")
    void adjustAchievement_zeroValue() {
        assertThatThrownBy(() -> service.adjustAchievement(10L, 0d, "原因"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("调整值不能为 0");
        verify(achievementRepository, never()).save(any());
    }

    @Test
    @DisplayName("adjustAchievement: 调整值为空抛 BAD_REQUEST")
    void adjustAchievement_nullValue() {
        assertThatThrownBy(() -> service.adjustAchievement(10L, null, "原因"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("调整值不能为空");
        verify(achievementRepository, never()).save(any());
    }

    @Test
    @DisplayName("calculateRanking: 按达成值降序排名, 清理旧排名后写入新排名")
    void calculateRanking_success() throws ScrmException {
        ScrmSalesTargetEntity t1 = buildTargetEntity(10L);
        t1.setActualValue(80000d);
        t1.setAchievementRate(80d);
        t1.setTargetId("user_001");
        t1.setTargetNameRef("张三");
        ScrmSalesTargetEntity t2 = buildTargetEntity(11L);
        t2.setActualValue(120000d);
        t2.setAchievementRate(120d);
        t2.setTargetId("user_002");
        t2.setTargetNameRef("李四");
        when(targetRepository.findAll(any(Specification.class))).thenReturn(new ArrayList<>(List.of(t1, t2)));
        when(rankingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<org.hiylo.scrm.dto.ScrmSalesRankingDto> results = service.calculateRanking(
                "QUARTERLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                "AMOUNT", "INDIVIDUAL");

        // 验证清理旧排名
        verify(rankingRepository
            , times(1)).deleteByPeriodTypeAndPeriodStartAndPeriodEndAndMetricTypeAndTargetType("QUARTERLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31),
                "AMOUNT", "INDIVIDUAL");
        // 按达成值降序: 李四 (120000) 第一, 张三 (80000) 第二
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getRank()).isEqualTo(1);
        assertThat(results.get(0).getTargetId()).isEqualTo("user_002");
        assertThat(results.get(0).getAchievedValue()).isEqualTo(120000d);
        assertThat(results.get(1).getRank()).isEqualTo(2);
        assertThat(results.get(1).getTargetId()).isEqualTo("user_001");
    }

    @Test
    @DisplayName("calculateRanking: 周期类型为空抛 BAD_REQUEST")
    void calculateRanking_blankPeriodType() {
        assertThatThrownBy(() -> service.calculateRanking("", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31), "AMOUNT", "INDIVIDUAL"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("周期类型不能为空");
        verify(rankingRepository, never()).save(any());
    }

    @Test
    @DisplayName("getTargetOverview: 聚合总目标值/实际值/平均达成率/完成数")
    void getTargetOverview_success() {
        ScrmSalesTargetEntity t1 = buildTargetEntity(10L);
        t1.setTargetValue(100000d);
        t1.setActualValue(100000d);
        t1.setAchievementRate(100d);
        ScrmSalesTargetEntity t2 = buildTargetEntity(11L);
        t2.setTargetValue(100000d);
        t2.setActualValue(50000d);
        t2.setAchievementRate(50d);
        when(targetRepository.findAll(any(Specification.class))).thenReturn(List.of(t1, t2));

        Map<String, Object> result = service.getTargetOverview("QUARTERLY",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

        assertThat(result.get("totalTargets")).isEqualTo(2);
        // 完成数: 仅 t1 达成率 ≥100
        assertThat(result.get("completedTargets")).isEqualTo(1L);
        // 总目标值 = 200000
        assertThat(result.get("totalTargetValue")).isEqualTo(200000.0);
        // 总实际值 = 150000
        assertThat(result.get("totalActualValue")).isEqualTo(150000.0);
        // 平均达成率 = (100 + 50) / 2 = 75
        assertThat(result.get("avgAchievementRate")).isEqualTo(75.0);
        // 总体达成率 = 150000 / 200000 * 100 = 75
        assertThat(result.get("overallAchievementRate")).isEqualTo(75.0);
    }

    @Test
    @DisplayName("getAchievementRateDistribution: 五档分桶统计")
    void getAchievementRateDistribution_success() {
        ScrmSalesTargetEntity excellent = buildTargetEntity(10L);
        excellent.setAchievementRate(120d);
        excellent.setPeriodStart(LocalDate.of(2026, 10, 1));
        excellent.setPeriodEnd(LocalDate.of(2026, 12, 31));
        ScrmSalesTargetEntity good = buildTargetEntity(11L);
        good.setAchievementRate(85d);
        good.setPeriodStart(LocalDate.of(2026, 10, 1));
        good.setPeriodEnd(LocalDate.of(2026, 12, 31));
        ScrmSalesTargetEntity poor = buildTargetEntity(12L);
        poor.setAchievementRate(20d);
        poor.setPeriodStart(LocalDate.of(2026, 10, 1));
        poor.setPeriodEnd(LocalDate.of(2026, 12, 31));
        when(targetRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(excellent, good, poor));

        List<Map<String, Object>> result = service.getAchievementRateDistribution(
                "QUARTERLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31));

        // total=3, EXCELLENT=1 (120≥100), GOOD=1 (85≥80), POOR=1 (20<30)
        Map<String, Long> bucketCount = new java.util.HashMap<>();
        for (Map<String, Object> item : result) {
            bucketCount.put((String) item.get("bucket"), ((Number) item.get("count")).longValue());
        }
        assertThat(bucketCount.get("EXCELLENT")).isEqualTo(1L);
        assertThat(bucketCount.get("GOOD")).isEqualTo(1L);
        assertThat(bucketCount.get("POOR")).isEqualTo(1L);
        assertThat(bucketCount.get("NORMAL")).isZero();
        assertThat(bucketCount.get("BELOW")).isZero();
    }

    @Test
    @DisplayName("getTopPerformers: 按达成率降序返回前 N 名")
    void getTopPerformers_success() {
        ScrmSalesTargetEntity t1 = buildTargetEntity(10L);
        t1.setAchievementRate(60d);
        t1.setActualValue(60000d);
        ScrmSalesTargetEntity t2 = buildTargetEntity(11L);
        t2.setAchievementRate(120d);
        t2.setActualValue(120000d);
        ScrmSalesTargetEntity t3 = buildTargetEntity(12L);
        t3.setAchievementRate(80d);
        t3.setActualValue(80000d);
        when(targetRepository.findAll(any(Specification.class)))
                .thenReturn(new ArrayList<>(List.of(t1, t2, t3)));

        List<Map<String, Object>> result = service.getTopPerformers("QUARTERLY",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), 2);

        // 按达成率降序: t2 (120) > t3 (80) > t1 (60), 取前 2
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("rank")).isEqualTo(1);
        assertThat(result.get(0).get("achievementRate")).isEqualTo(120d);
        assertThat(result.get(1).get("rank")).isEqualTo(2);
        assertThat(result.get(1).get("achievementRate")).isEqualTo(80d);
    }

    @Test
    @DisplayName("getAchievementTrend: 返回每日累计达成值")
    void getAchievementTrend_success() throws ScrmException {
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        LocalDate today = LocalDate.now();
        // 两条达成记录: 昨天 1000, 今天 2000
        when(achievementRepository.findByTargetIdOrderByAchievementDateAsc(10L))
                .thenReturn(List.of(
                        buildAchievementEntity(1L, 1000d, today.minusDays(1)),
                        buildAchievementEntity(2L, 2000d, today)));

        List<Map<String, Object>> result = service.getAchievementTrend(10L, 2);

        // 2 天趋势: 昨天 累计 1000, 今天 累计 3000
        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("dailyValue")).isEqualTo(1000.0);
        assertThat(result.get(0).get("cumulativeValue")).isEqualTo(1000.0);
        assertThat(result.get(1).get("dailyValue")).isEqualTo(2000.0);
        assertThat(result.get(1).get("cumulativeValue")).isEqualTo(3000.0);
    }

    @Test
    @DisplayName("forecastAchievement: 历史数据不足时按日均线性外推")
    void forecastAchievement_insufficientData() throws ScrmException {
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        target.setActualValue(30000d);
        target.setAchievementRate(30d);
        target.setPeriodStart(LocalDate.now().minusDays(30));
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));
        // 无达成记录, 走日均外推
        when(achievementRepository.findByTargetIdOrderByAchievementDateAsc(10L))
                .thenReturn(Collections.emptyList());

        ScrmSalesForecastDto dto = new ScrmSalesForecastDto();
        dto.setTargetId(10L);
        dto.setForecastDays(30);
        Map<String, Object> result = service.forecastAchievement(dto);

        assertThat(result.get("targetId")).isEqualTo(10L);
        assertThat(result.get("currentActualValue")).isEqualTo(30000d);
        // 预测后值应大于当前实际值 (日均 * 30 天增量)
        assertThat((double) result.get("forecastValue")).isGreaterThan(30000d);
        // willAchieve 取决于预测值是否达到目标值
        assertThat(result.get("willAchieve")).isNotNull();
    }

    @Test
    @DisplayName("forecastAchievement: 预测天数 ≤0 抛 BAD_REQUEST")
    void forecastAchievement_invalidDays() {
        ScrmSalesTargetEntity target = buildTargetEntity(10L);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(target));

        ScrmSalesForecastDto dto = new ScrmSalesForecastDto();
        dto.setTargetId(10L);
        dto.setForecastDays(0);
        assertThatThrownBy(() -> service.forecastAchievement(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("预测天数必须大于 0");
    }

    @Test
    @DisplayName("calculateAchievementRate: 目标值为 0 返回 0 避免除零")
    void calculateAchievementRate_zeroTarget() {
        Double rate = ReflectionTestUtils.invokeMethod(service, "calculateAchievementRate", 1000.0, 0.0);
        assertThat(rate).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculateAchievementRate: 实际值 / 目标值 * 100")
    void calculateAchievementRate_normalCase() {
        // 50000 / 100000 * 100 = 50
        Double rate = ReflectionTestUtils.invokeMethod(service, "calculateAchievementRate", 50000.0, 100000.0);
        assertThat(rate).isEqualTo(50.0);
    }

    @Test
    @DisplayName("listTargets: 通过 Specification 分页查询")
    void listTargets_pagination() {
        ScrmSalesTargetEntity entity = buildTargetEntity(10L);
        when(targetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<ScrmSalesTargetDto> result = service.listTargets("INDIVIDUAL", "AMOUNT",
                "QUARTERLY", "ACTIVE", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetType()).isEqualTo("INDIVIDUAL");
        verify(targetRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("deleteTarget: 级联清理达成记录后删除目标")
    void deleteTarget_success() throws ScrmException {
        ScrmSalesTargetEntity entity = buildTargetEntity(10L);
        when(targetRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(achievementRepository.deleteByTargetId(10L)).thenReturn(2L);

        service.deleteTarget(10L);

        verify(achievementRepository, times(1)).deleteByTargetId(10L);
        verify(targetRepository, times(1)).delete(entity);
    }
}
