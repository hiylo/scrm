/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AuthService.java
 * Date : 2026/09/17 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.auth;

import org.hiylo.scrm.dto.auth.LoginRequestDto;
import org.hiylo.scrm.dto.auth.LoginResponseDto;
import org.hiylo.scrm.dto.auth.RegisterRequestDto;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.exception.ScrmException;

/**
 * 认证服务。
 * <p>
 * 承载 SCRM 独立单体形态下的自建登录能力: 注册 (用户名唯一校验 + 密码强度校验 +
 * BCrypt 加密)、登录 (凭证校验 + 令牌签发 + 最后登录时间回写)、按用户 ID 查询当前用户。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface AuthService {

    /**
     * 注册新用户。
     * <p>
     * 校验用户名唯一与密码强度 (不少于 8 位且同时含字母与数字), 口令经 BCrypt 加密落库,
     * 默认角色 {@code OPERATOR}。
     * </p>
     *
     * @param request 注册请求
     * @return 创建后的用户实体
     * @throws ScrmException 用户名重复 (409) 或密码强度不足 (400)
     */
    ScrmUserEntity register(RegisterRequestDto request);

    /**
     * 登录并签发访问令牌。
     * <p>
     * 校验用户名存在、口令匹配与账号启用状态, 成功后回写 lastLoginAt 并签发 JWT。
     * </p>
     *
     * @param request 登录请求
     * @return 登录响应 (含访问令牌与调用方身份)
     * @throws ScrmException 凭证错误 / 账号禁用 (401)
     */
    LoginResponseDto login(LoginRequestDto request);

    /**
     * 按用户 ID 查询当前登录用户。
     *
     * @param userId 用户 ID 字符串形式 (JWT 声明 {@code uid})
     * @return 用户实体
     * @throws ScrmException 用户不存在或 ID 格式非法 (401)
     */
    ScrmUserEntity getCurrentUser(String userId);
}
