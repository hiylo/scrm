/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmApiKeyRepository.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmApiKeyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * SCRM 开放API 密钥数据访问层。
 * <p>
 * 提供按与 apiKey 查询密钥, 按应用分页查询密钥, 以及使用记录增量更新能力,
 * 支撑 {@code ScrmOpenApiService} 的密钥管理与调用统计接口。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmApiKeyRepository extends JpaRepository<ScrmApiKeyEntity, Long>,
        JpaSpecificationExecutor<ScrmApiKeyEntity> {

    /**
     * 按与 apiKey 查询密钥 (网关侧鉴权用)。
     *
     * @param apiKey   API 密钥
     * @return 密钥实体
     */
    Optional<ScrmApiKeyEntity> findByApiKey(String apiKey);

    /**
     * 按与应用分页查询密钥, 按创建时间倒序返回。
     *
     * @param appId    应用 ID
     * @param pageable 分页参数
     * @return 密钥分页结果
     */
    Page<ScrmApiKeyEntity> findByAppIdOrderByCreateTimeDesc(Long appId, Pageable pageable);

    /**
     *
     * @param appId    应用 ID
     * @param status   密钥状态
     * @param pageable 分页参数
     * @return 密钥分页结果
     */
    Page<ScrmApiKeyEntity> findByAppIdAndStatusOrderByCreateTimeDesc(Long appId,
                                                                                String status, Pageable pageable);

    /**
     * 增量更新密钥使用记录: 使用次数 +1, 刷新最后使用时间与 IP。
     *
     * @param id         密钥 ID
     * @param usedAt     使用时间
     * @param usedIp     使用 IP
     */
    @Modifying
    @Query(value = "UPDATE ScrmApiKeyEntity k SET k.usageCount = COALESCE(k.usageCount, 0) + 1, k.lastUsedAt ="
                          + ":usedAt, k.lastUsedIp = :usedIp WHERE k.id = :id")
    int incrementUsage(@Param("id") Long id,
                       @Param("usedAt") LocalDateTime usedAt,
                       @Param("usedIp") String usedIp);
}
