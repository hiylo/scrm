/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerIdentityRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmCustomerIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * SCRM 客户身份数据访问层。
 * <p>
 * 提供按客户 / 身份类型 / 身份值查询, 主身份获取, 跨客户身份查找 (用于重复检测),
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmCustomerIdentityRepository extends JpaRepository<ScrmCustomerIdentityEntity, Long>,
        JpaSpecificationExecutor<ScrmCustomerIdentityEntity> {

    /**
     * 按客户查询全部身份 (按 isPrimary DESC, createTime DESC)。
     *
     * @param customerId 客户 ID
     * @return 身份列表
     */
    List<ScrmCustomerIdentityEntity> findByCustomerIdOrderByIsPrimaryDescCreateTimeDesc(Long customerId);

    /**
     * 按客户查询活跃身份。
     *
     * @param customerId 客户 ID
     * @param isActive   是否活跃
     * @return 身份列表
     */
    List<ScrmCustomerIdentityEntity> findByCustomerIdAndIsActive(Long customerId, Boolean isActive);

    /**
     * 按客户 ID 列表批量查询活跃身份 (批量预加载用, 避免逐客户查询造成 N+1)。
     *
     * @param customerIds 客户 ID 列表
     * @param isActive    是否活跃
     * @return 身份列表
     */
    List<ScrmCustomerIdentityEntity> findByCustomerIdInAndIsActive(Collection<Long> customerIds, Boolean isActive);

    /**
     *
     * @param customerId   客户 ID
     * @param identityType 身份类型
     * @return 身份 (可能为空)
     */
    Optional<ScrmCustomerIdentityEntity> findByCustomerIdAndIdentityType(Long customerId, String identityType);

    /**
     *
     * @param identityType  身份类型
     * @param identityValue 身份值
     * @return 身份 (可能为空)
     */
    Optional<ScrmCustomerIdentityEntity> findByIdentityTypeAndIdentityValue(String identityType, String identityValue);

    /**
     * 按客户查询主身份。
     *
     * @param customerId 客户 ID
     * @param isPrimary  是否主身份
     * @return 主身份 (可能为空)
     */
    Optional<ScrmCustomerIdentityEntity> findByCustomerIdAndIsPrimary(Long customerId, Boolean isPrimary);

    /**
     *
     * @param identityType 身份类型
     * @param identityValue 身份值
     * @return 身份列表
     */
    List<ScrmCustomerIdentityEntity> findAllByIdentityTypeAndIdentityValue(String identityType, String identityValue);

    /**
     *
     * @param identityType  身份类型
     * @param identityValues 身份值列表
     * @return 身份列表
     */
    List<ScrmCustomerIdentityEntity> findByIdentityTypeAndIdentityValueIn(String identityType, List<String> identityValues);

    /**
     *
     * @param identityType  身份类型 (可空)
     * @param keyword       关键词前缀
     * @return 身份列表
     */
    @Query(value = "SELECT i FROM ScrmCustomerIdentityEntity i WHERE (:identityType IS NULL OR i.identityType ="
                          + ":identityType) AND LOWER(i.identityValue) LIKE LOWER(CONCAT(:keyword, '%')) ORDER BY "
                          + "i.createTime DESC")
    List<ScrmCustomerIdentityEntity> searchByKeyword(
                                                      @Param("identityType") String identityType,
                                                      @Param("keyword") String keyword);

    /**
     *
     * @return Object[]{identityType, count}
     */
    @Query("SELECT i.identityType, COUNT(i.id) FROM ScrmCustomerIdentityEntity i GROUP BY i.identityType")
    List<Object[]> countByIdentityType();

    /**
     *
     * @return Object[]{platform, count}
     */
    @Query(value = "SELECT COALESCE(i.platform, 'UNKNOWN'), COUNT(i.id) FROM ScrmCustomerIdentityEntity i GROUP BY "
                          + "COALESCE(i.platform, 'UNKNOWN')")
    List<Object[]> countByPlatform();

    /**
     * 统计指定账号的已验证身份数 (验证率统计用)。
     *
     * @param isVerified 是否验证
     * @return 身份数
     */
    long countByIsVerified(Boolean isVerified);

    /**
     * 统计指定账号的身份总数。
     *
     * @return 身份总数
     */
}
