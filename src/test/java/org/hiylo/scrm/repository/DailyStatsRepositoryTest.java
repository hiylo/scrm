/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DailyStatsRepositoryTest.java
 * Date : 2026/09/18 10:35:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DailyStatsRepository} 单元测试。
 * <p>
 * 重点覆盖 SQL 拼接正确性: whereClause 为空串时不得生成 {@code WHERE AND ...} 这类非法子句,
 * 且 {@code COUNT(*) AS c} 与 {@code FROM} 之间必须保留空格。该仓库此前无任何测试覆盖,
 * 真实 PostgreSQL 集成冒烟才发现两处拼接缺陷。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DailyStatsRepository 单元测试")
class DailyStatsRepositoryTest {

    /** JPA 实体管理器 Mock */
    @Mock
    private EntityManager entityManager;

    /** 原生查询 Mock, 用于捕获最终拼接的 SQL */
    @Mock
    private Query query;

    /** 被测仓库实例 */
    private DailyStatsRepository repository;

    /** 统计起始时间 */
    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 1, 0, 0);
    /** 统计结束时间 */
    private static final LocalDateTime END = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

    @BeforeEach
    void setUp() {
        repository = new DailyStatsRepository(entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        // 必须用 Collections.singletonList 包装: List.of 的 varargs 会把 Object[] 展开为多个元素
        when(query.getResultList()).thenReturn(
                java.util.Collections.singletonList(new Object[]{"2026-01-01", 3L}));
    }

    /** 捕获本次执行的 SQL 字符串 */
    private String capturedSql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(1)).createNativeQuery(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("countByDay: whereClause 为空串时不生成 WHERE AND 空子句")
    void countByDay_blankWhereClauseProducesValidSql() {
        List<DailyStat> stats =
                repository.countByDay("scrm.scrm_customer", "create_time", "", Map.of(), START, null);

        String sql = capturedSql();
        assertThat(stats).hasSize(1);
        assertThat(sql).doesNotContain("WHERE  AND");
        assertThat(sql).doesNotContain("WHERE AND");
        // 只保留日期下界谓词
        assertThat(sql).contains("WHERE create_time >= :start");
        assertThat(sql).doesNotContain(":end");
        assertThat(sql).contains(" GROUP BY d ORDER BY d");
    }

    @Test
    @DisplayName("countByDay: COUNT(*) AS c 与 FROM 之间保留空格")
    void countByDay_keepsSpaceBeforeFrom() {
        repository.countByDay("scrm.scrm_customer", "create_time", "", Map.of(), START, null);

        String sql = capturedSql();
        assertThat(sql).startsWith("SELECT to_char(create_time, 'YYYY-MM-DD') AS d, COUNT(*) AS c ");
        assertThat(sql).doesNotContain("AS cFROM");
        assertThat(sql).contains("FROM scrm.scrm_customer");
    }

    @Test
    @DisplayName("countByDay: 带附加过滤条件时与日期谓词以 AND 连接")
    void countByDay_appendsExtraPredicate() {
        repository.countByDay("scrm.scrm_behavior_track t", "t.behavior_time",
                "t.event_type = :eventType",
                Map.of("eventType", "PAGE_VIEW"), START, null);

        String sql = capturedSql();
        assertThat(sql).contains("FROM scrm.scrm_behavior_track t");
        assertThat(sql).contains("WHERE t.event_type = :eventType AND t.behavior_time >= :start");
        verify(query).setParameter("eventType", "PAGE_VIEW");
        verify(query).setParameter("start", START);
    }

    @Test
    @DisplayName("countByDay: end 非空时追加结束时间上界")
    void countByDay_appendsEndBound() {
        repository.countByDay("scrm.scrm_audit_log", "operated_at", "", Map.of(), START, END);

        String sql = capturedSql();
        assertThat(sql).contains("WHERE operated_at >= :start AND operated_at < :end");
        verify(query).setParameter("end", END);
    }

    @Test
    @DisplayName("countByDay: 空白 whereClause 视同空串处理")
    void countByDay_treatsWhitespaceOnlyWhereClauseAsBlank() {
        repository.countByDay("scrm.scrm_customer", "create_time", "   ", Map.of(), START, null);

        assertThat(capturedSql()).contains("WHERE create_time >= :start");
    }

    @Test
    @DisplayName("toRows: 将每日统计转换为表格行")
    void toRows_convertsStatsToRows() {
        List<Object[]> rows = DailyStatsRepository.toRows(
                List.of(new DailyStat("2026-01-01", 3L),
                        new DailyStat("2026-01-02", 0L)));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("2026-01-01", 3L);
        assertThat(rows.get(1)).containsExactly("2026-01-02", 0L);
    }

}
