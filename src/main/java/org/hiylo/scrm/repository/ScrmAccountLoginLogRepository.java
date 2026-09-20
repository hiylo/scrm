/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAccountLoginLogRepository.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAccountLoginLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * SCRM 账号登录态日志数据访问层。
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmAccountLoginLogRepository extends JpaRepository<ScrmAccountLoginLogEntity, Long> {

    /**
     * 按账号 ID 查询登录日志，按操作时间倒序返回。
     *
     * @param accountId 账号 ID
     * @return 登录日志列表（最新在前）
     */
    List<ScrmAccountLoginLogEntity> findByAccountIdOrderByOperateAtDesc(Long accountId);
}
