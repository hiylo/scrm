/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmOpportunityRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmOpportunityEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * SCRM 商机数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmOpportunityRepository extends JpaRepository<ScrmOpportunityEntity, Long>,
        JpaSpecificationExecutor<ScrmOpportunityEntity> {

    /**
     * 按负责人查询商机列表 (销售预测用)。
     *
     * @param ownerUserId 负责人用户 ID
     * @param pageable    分页参数
     * @return 商机分页
     */
    Page<ScrmOpportunityEntity> findByOwnerUserId(String ownerUserId, Pageable pageable);

    /**
     * 按漏斗 ID 与阶段 ID 聚合商机数与金额 (漏斗分析用, native query)。
     *
     * @param funnelId 漏斗 ID
     * @return Object[]{stageId, count, sumAmount}
     */
    @Query(value = "SELECT current_stage_id, COUNT(*), COALESCE(SUM(amount), 0) FROM scrm.scrm_opportunity WHERE "
                          + "funnel_id = :funnelId AND status = 'OPEN' GROUP BY current_stage_id",
            nativeQuery = true)
    List<Object[]> aggregateByStage(
                                    @Param("funnelId") Long funnelId);

    /**
     * 按负责人统计 OPEN 状态商机的总金额与加权金额 (销售预测汇总用)。
     *
     * <p>返回 **List&lt;Object[]&gt; 而不是裸 Object[]**：原生多列聚合查询由 Hibernate
     * 按「行列表」返回，若方法声明成裸 {@code Object[]}，Spring Data 会把整个结果集
     * 塞进一个 Object[]，于是 {@code summary[0]} 拿到的是整行 {@code Object[]} 而非
     * 首列数值，强转 Number 即抛 ClassCastException（2026-10-28 线上 forecast 500
     * 的根因）。改成 List 后由调用方取第一行。</p>
     *
     * @param ownerUserId 负责人用户 ID (可空, 为空时统计全部)
     * @return 单行结果列表，元素为 Object[]{totalCount, totalAmount, weightedAmount}；
     *         聚合查询恒返回一行，但列表形状更符合 JPA 语义
     */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(amount), 0), COALESCE(SUM(amount * probability / 100.0), 0) FROM "
                          + "scrm.scrm_opportunity WHERE status = 'OPEN' AND (:ownerUserId IS NULL OR owner_user_id ="
                          + ":ownerUserId)",
            nativeQuery = true)
    List<Object[]> forecastSummary(
                             @Param("ownerUserId") String ownerUserId);

    /**
     * 按漏斗 ID 查询商机列表 (用于漏斗删除前校验是否仍有商机)。
     *
     * @param funnelId 漏斗 ID
     * @return 商机列表
     */
    List<ScrmOpportunityEntity> findByFunnelId(Long funnelId);
}
