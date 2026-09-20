/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 数据字典数据访问层。
 * <p>
 * 提供按与字典编码查询、按与父字典查询子字典、按枚举启用字典等便捷方法,
 * 供 {@code ScrmDataDictionaryService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmDataDictionaryRepository extends JpaRepository<ScrmDataDictionaryEntity, Long>,
        JpaSpecificationExecutor<ScrmDataDictionaryEntity> {

    /**
     * 按与字典编码查询字典。
     *
     * @param dictCode 字典编码
     * @return 字典 (可能为空)
     */
    Optional<ScrmDataDictionaryEntity> findByDictCode(String dictCode);

    /**
     * 校验字典编码是否已存在。
     *
     * @param dictCode 字典编码
     * @return 是否存在
     */
    boolean existsByDictCode(String dictCode);

    /**
     * 按与父字典 ID 查询子字典列表。
     *
     * @param parentId 父字典 ID
     * @return 子字典列表
     */
    List<ScrmDataDictionaryEntity> findByParentId(Long parentId);

    /**
     * 按查询所有启用的字典。
     *
     * @return 启用字典列表
     */
    List<ScrmDataDictionaryEntity> findByEnabledTrue();

    /**
     * 按统计字典总数。
     *
     * @return 字典总数
     */
}
