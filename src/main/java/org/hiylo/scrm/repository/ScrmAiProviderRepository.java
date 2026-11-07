/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderRepository.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * AI Provider 注册表数据访问层。
 *
 * @author Hsi Chu
 * @since V1.0
 */
public interface ScrmAiProviderRepository extends JpaRepository<ScrmAiProviderEntity, Long> {

    /** 按名称精确查询（唯一约束） */
    Optional<ScrmAiProviderEntity> findByProviderName(String providerName);

    /** 全部启用中的 provider（按优先级升序，数值小优先） */
    List<ScrmAiProviderEntity> findByEnabledTrueOrderByPriorityAsc();

    /** 全部记录（管理端列表） */
    List<ScrmAiProviderEntity> findAllByOrderByPriorityAsc();

    /** 当前默认文本 provider */
    Optional<ScrmAiProviderEntity> findByDefaultTextTrue();

    /** 当前默认多模态 provider */
    Optional<ScrmAiProviderEntity> findByDefaultImageTrue();
}