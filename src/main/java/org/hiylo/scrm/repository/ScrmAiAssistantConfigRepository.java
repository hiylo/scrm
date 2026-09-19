/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAiAssistantConfigRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiAssistantConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM AI 助手配置数据访问层。
 * <p>
 * 提供按加载默认/启用配置, 以及增量更新请求次数与最近使用时间等能力,
 * 供 {@code ScrmAiAssistantService} 配置管理与对话调用使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAiAssistantConfigRepository extends JpaRepository<ScrmAiAssistantConfigEntity, Long>,
        JpaSpecificationExecutor<ScrmAiAssistantConfigEntity> {

    /**
     * 按查询默认启用配置。
     *
     * @return 默认启用配置 (不存在返回 empty)
     */
    Optional<ScrmAiAssistantConfigEntity> findByIsDefaultTrueAndEnabledTrue();

    /**
     * 增量更新配置请求次数与最近使用时间 (避免乐观锁冲突, 直接 SQL 更新)。
     *
     * @param configId   配置 ID
     * @param usedAt     使用时间
     * @return 受影响行数 (1 表示更新成功)
     */
    @Modifying
    @Query(value = "UPDATE ScrmAiAssistantConfigEntity c SET c.requestCount = c.requestCount + 1, c.lastUsedAt ="
                          + ":usedAt WHERE c.id = :configId")
    int incrementRequestCount(@Param("configId") Long configId, @Param("usedAt") LocalDateTime usedAt);

    /**
     * 清除所有默认配置标记 (用于设置新默认配置前清场)。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE ScrmAiAssistantConfigEntity c SET c.isDefault = false")
    int clearDefault();
}
