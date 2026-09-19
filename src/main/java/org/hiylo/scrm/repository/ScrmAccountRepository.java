/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 平台账号数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAccountRepository extends JpaRepository<ScrmAccountEntity, Long>,
        JpaSpecificationExecutor<ScrmAccountEntity> {

    /**
     * 根据平台类型与平台账号 UID 查询账号。
     *
     * @param platformType       平台类型
     * @param platformAccountUid 平台账号 UID
     * @return 账号（可能为空）
     */
    Optional<ScrmAccountEntity> findByPlatformTypeAndPlatformAccountUid(String platformType, String platformAccountUid);

    /**
     * 根据设备 ID 查询关联账号列表。
     *
     * @param deviceId 设备 ID
     * @return 账号列表
     */
    List<ScrmAccountEntity> findByDeviceId(String deviceId);

    /**
     * 根据人设 ID 查询关联账号列表。
     *
     * @param personaId 人设 ID
     * @return 账号列表
     */
    List<ScrmAccountEntity> findByPersonaId(String personaId);

    /**
     * 根据账号 ID 查询账号列表。
     *
     * @return 账号列表
     */

    /**
     * 统计指定账号下的账号总数。
     *
     * @return 账号总数
     */

    /**
     * 按平台类型聚合账号数（看板用, 避免 N+1）。
     *
     * @return Object[]{platformType, count}
     */
    @Query(
"SELECT e.platformType, COUNT(e.id) FROM ScrmAccountEntity e GROUP BY e.platformType")
    List<Object[]> countByPlatformType();

    /**
     * 按登录态聚合账号数（看板用, 避免 N+1）。
     *
     * @return Object[]{loginState, count}
     */
    @Query(
"SELECT e.loginState, COUNT(e.id) FROM ScrmAccountEntity e GROUP BY e.loginState")
    List<Object[]> countByLoginState();

    /**
     * 统计指定账号下 LOGIN 状态的账号数。
     *
     * @param loginState 登录态
     * @return 账号数
     */
    long countByLoginState(String loginState);

    /**
     * 根据账号 ID 和平台类型查询账号列表。
     *
     * @param platformType 平台类型
     * @return 账号列表
     */
    List<ScrmAccountEntity> findByPlatformType(String platformType);
}
