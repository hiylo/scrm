/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmUserRepository.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmUserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * SCRM 平台登录用户数据访问层。
 * <p>
 * 支撑自建 JWT 认证: 按用户名查询账号完成登录校验, 用户名唯一性校验防止重复注册。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Repository
public interface ScrmUserRepository extends JpaRepository<ScrmUserEntity, Long> {

    /**
     * 按用户名查询用户。
     *
     * @param username 登录用户名
     * @return 用户实体, 不存在时返回空
     */
    Optional<ScrmUserEntity> findByUsername(String username);

    /**
     * 判断用户名是否已存在。
     *
     * @param username 登录用户名
     * @return true=用户名已被占用
     */
    boolean existsByUsername(String username);

    /**
     * 按用户名 / 昵称模糊分页查询用户 (管理端用户列表关键词搜索)。
     *
     * @param username 用户名关键词
     * @param displayName 昵称关键词
     * @param pageable    分页参数
     * @return 用户分页结果
     */
    Page<ScrmUserEntity> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String username, String displayName, Pageable pageable);
}
