/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserAccountRepository.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmUserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * SCRM 用户-账号关联数据访问层。
 * <p>
 * 支持按用户 ID、部门 ID 查询关联账号，用于数据隔离时计算可访问账号 ID 集合。
 * </p>
 *
 * @author Hsi Chu
 */
@Repository
public interface ScrmUserAccountRepository extends JpaRepository<ScrmUserAccountEntity, Long> {

    /**
     * 根据用户 ID 查询关联账号列表。
     *
     * @param userId 用户 ID
     * @return 用户-账号关联列表
     */
    List<ScrmUserAccountEntity> findByUserId(String userId);

    /**
     * 根据部门 ID 查询关联账号列表（用于 MANAGER 角色聚合本部门账号）。
     *
     * @param departmentId 部门 ID
     * @return 用户-账号关联列表
     */
    List<ScrmUserAccountEntity> findByDepartmentId(String departmentId);

    /**
     * 根据用户 ID 或部门 ID 查询关联账号列表。
     * <p>
     * 用于需要同时获取"直接绑定"与"部门授权"账号的场景。
     * </p>
     *
     * @param userId       用户 ID
     * @param departmentId 部门 ID
     * @return 用户-账号关联列表
     */
    List<ScrmUserAccountEntity> findByUserIdOrDepartmentId(String userId, String departmentId);
}
