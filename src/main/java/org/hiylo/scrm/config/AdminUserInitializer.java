/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : AdminUserInitializer.java
 * Date : 2026/09/22 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmUserEntity;
import org.hiylo.scrm.repository.ScrmUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 默认系统管理员引导。
 * <p>
 * 关闭公开注册后, 系统需要一个初始 ADMIN 账号才能进行首次用户管理。
 * 本组件在应用启动时幂等执行: 若不存在 {@code username} 对应的用户则创建 ADMIN
 * (口令来自配置 {@code scrm.security.bootstrap-admin.password}, BCrypt 加密落库);
 * 已存在则跳过。生产环境必须通过 {@code SCRM_ADMIN_PASSWORD} 注入强口令。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    /** 引导开关, 默认开启 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 默认管理员用户名 */
    private static final String DEFAULT_USERNAME = "admin";

    /** 管理员角色 */
    private static final String ROLE_ADMIN = "ADMIN";

    /** 账号状态: 启用 */
    private static final int STATUS_ENABLED = 1;

    /** 系统用户数据访问层 */
    private final ScrmUserRepository userRepository;

    /** 口令加密器 (BCrypt) */
    private final PasswordEncoder passwordEncoder;

    /** 引导开关 (配置项 {@code scrm.security.bootstrap-admin.enabled}) */
    @Value("${scrm.security.bootstrap-admin.enabled:true}")
    private boolean enabled;

    /** 默认管理员用户名 (配置项 {@code scrm.security.bootstrap-admin.username}) */
    @Value("${scrm.security.bootstrap-admin.username:admin}")
    private String adminUsername;

    /** 默认管理员初始口令 (配置项 {@code scrm.security.bootstrap-admin.password}) */
    @Value("${scrm.security.bootstrap-admin.password:admin123456}")
    private String adminPassword;

    /**
     * 应用启动后幂等创建默认管理员。
     *
     * @param args 启动参数 (未使用)
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("管理员引导已关闭 (scrm.security.bootstrap-admin.enabled=false), 跳过");
            return;
        }
        String username = adminUsername == null || adminUsername.isBlank() ? DEFAULT_USERNAME : adminUsername.trim();
        if (userRepository.existsByUsername(username)) {
            log.info("管理员用户已存在, 跳过引导: username={}", username);
            return;
        }
        ScrmUserEntity admin = new ScrmUserEntity();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setDisplayName("系统管理员");
        admin.setRoles(ROLE_ADMIN);
        admin.setStatus(STATUS_ENABLED);
        userRepository.save(admin);
        log.warn("已创建默认系统管理员: username={}, 请立即通过用户管理修改初始口令", username);
    }
}