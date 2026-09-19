/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : DailyStatsRepository.java
 * Date : 2026/09/05 14:15:16
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 每日统计查询仓库
 * <p>
 * 通过原生 SQL 按日聚合统计指定数据表的记录数, 供各模块图表查询复用。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Repository
public class DailyStatsRepository implements ApplicationContextAware {

    /** 单例实例 (由 Spring 容器初始化后写入) */
    private static DailyStatsRepository INSTANCE;

    /** JPA 实体管理器 (执行原生统计 SQL) */
    private final EntityManager entityManager;

    /**
     * 构造数据访问对象。
     *
     * @param entityManager JPA 实体管理器, 用于执行原生统计 SQL
     */
    public DailyStatsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 由 Spring 容器回调注入单例实例
     *
     * @param applicationContext Spring 应用上下文
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        DailyStatsRepository.INSTANCE = this;
    }

    /**
     * 获取单例实例
     *
     * @return 全局唯一实例 (容器未初始化时为 null)
     */
    public static DailyStatsRepository getInstance() {
        return DailyStatsRepository.INSTANCE;
    }

    /**
     * 按日聚合统计指定表的记录数
     * <p>
     * whereClause 允许为空: 空串时不生成多余谓词, 避免拼出 {@code WHERE AND ...} 这类非法 SQL。
     * </p>
     *
     * @param table       目标数据表名 (可含别名, 如 {@code scrm.scrm_behavior_track t})
     * @param dateColumn  日期字段名 (与 table 的别名保持一致)
     * @param whereClause 附加过滤条件 (不含 WHERE 关键字, 允许为空串)
     * @param params      附加过滤条件参数键值对 (可为空 Map)
     * @param start       统计起始时间 (含)
     * @param end         统计结束时间 (不含, 可为 null)
     * @return 按日聚合的统计结果列表
     */
    public List<DailyStat> countByDay(String table, String dateColumn, String whereClause,
                                      Map<String, Object> params, LocalDateTime start,
                                      LocalDateTime end) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT to_char(").append(dateColumn).append(", 'YYYY-MM-DD') AS d, COUNT(*) AS c ");
        sql.append("FROM ").append(table);
        List<String> predicates = new ArrayList<>();
        if (whereClause != null && !whereClause.isBlank()) {
            predicates.add(whereClause);
        }
        predicates.add(dateColumn + " >= :start");
        if (end != null) {
            predicates.add(dateColumn + " < :end");
        }
        sql.append(" WHERE ").append(String.join(" AND ", predicates));
        sql.append(" GROUP BY d ORDER BY d");
        Query query = entityManager.createNativeQuery(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            query.setParameter(entry.getKey(), entry.getValue());
        }
        query.setParameter("start", start);
        if (end != null) {
            query.setParameter("end", end);
        }
        List<?> rows = query.getResultList();
        List<DailyStat> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            Object[] values = (Object[]) row;
            result.add(new DailyStat(String.valueOf(values[0]), ((Number) values[1]).longValue()));
        }
        return result;
    }

    /**
     * 将统计结果转换为表格行数据
     *
     * @param stats 每日统计数据列表
     * @return 每行依次为 (日期, 数量) 的对象数组列表
     */
    public static List<Object[]> toRows(List<DailyStat> stats) {
        List<Object[]> rows = new ArrayList<>(stats.size());
        for (DailyStat stat : stats) {
            rows.add(new Object[]{stat.date(), stat.count()});
        }
        return rows;
    }
}
