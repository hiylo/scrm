/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm-finance
 * File : ScrmPolicyPushTaskRepository.java
 * Date : 2026/10/23 12:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmPolicyPushTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 政策推送任务数据访问层（政策匹配推送域）。
 * <p>
 * 提供按任务编号查询（唯一校验）、按政策条目查询（防止重复建任务）、按状态加载
 * （发布/补算调度用）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmPolicyPushTaskRepository
        extends JpaRepository<ScrmPolicyPushTaskEntity, Long>,
        JpaSpecificationExecutor<ScrmPolicyPushTaskEntity> {

    /**
     * 按任务编号查询（唯一校验 / 检索）。
     *
     * @param taskNo 任务编号
     * @return 任务（可能为空）
     */
    Optional<ScrmPolicyPushTaskEntity> findByTaskNo(String taskNo);

    /**
     * 按政策条目 ID 查询任务（同一政策不重复建任务）。
     *
     * @param policyArticleId 政策条目 ID
     * @return 任务列表
     */
    List<ScrmPolicyPushTaskEntity> findByPolicyArticleId(Long policyArticleId);

    /**
     * 按状态加载任务（调度补算 / 发布重试）。
     *
     * @param status 任务状态
     * @return 任务列表
     */
    List<ScrmPolicyPushTaskEntity> findByStatus(String status);
}
