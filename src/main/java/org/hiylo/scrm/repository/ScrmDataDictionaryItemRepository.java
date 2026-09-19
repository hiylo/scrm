/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryItemRepository.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.repository;

import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * SCRM 数据字典项数据访问层。
 * <p>
 * 提供按字典 ID / 字典编码查询字典项、按字典编码 + 值 / 编码定位单项、
 * 按父项查询子项、批量重置默认项等便捷方法, 供 {@code ScrmDataDictionaryService} 使用。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public interface ScrmDataDictionaryItemRepository extends JpaRepository<ScrmDataDictionaryItemEntity, Long>,
        JpaSpecificationExecutor<ScrmDataDictionaryItemEntity> {

    /**
     * 按与字典 ID 查询全部字典项 (按排序值升序)。
     *
     * @param dictId   字典 ID
     * @return 字典项列表
     */
    List<ScrmDataDictionaryItemEntity> findByDictIdOrderBySortOrderAsc(Long dictId);

    /**
     * 按与字典编码查询全部字典项 (按排序值升序)。
     *
     * @param dictCode 字典编码
     * @return 字典项列表
     */
    List<ScrmDataDictionaryItemEntity> findByDictCodeOrderBySortOrderAsc(String dictCode);

    /**
     * 按与字典编码查询启用的字典项 (按排序值升序)。
     *
     * @param dictCode 字典编码
     * @return 启用字典项列表
     */
    List<ScrmDataDictionaryItemEntity> findByDictCodeAndEnabledTrueOrderBySortOrderAsc(String dictCode);

    /**
     *
     * @param dictCode  字典编码
     * @param itemValue 字典项值
     * @return 字典项 (可能为空)
     */
    Optional<ScrmDataDictionaryItemEntity> findByDictCodeAndItemValue(String dictCode, String itemValue);

    /**
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     * @return 字典项 (可能为空)
     */
    Optional<ScrmDataDictionaryItemEntity> findByDictCodeAndItemCode(String dictCode, String itemCode);

    /**
     * 按与父项 ID 查询子项列表 (按排序值升序)。
     *
     * @param parentId 父项 ID
     * @return 子项列表
     */
    List<ScrmDataDictionaryItemEntity> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     *
     * @param dictCode 字典编码
     * @param pathPrefix 路径前缀 (如 '1/5/')
     * @return 字典项列表
     */
    List<ScrmDataDictionaryItemEntity> findByDictCodeAndItemPathStartingWithOrderBySortOrderAsc(String dictCode, String pathPrefix);

    /**
     *
     * @param dictCode  字典编码
     * @param isDefault 是否默认
     * @return 默认项列表
     */
    List<ScrmDataDictionaryItemEntity> findByDictCodeAndIsDefault(String dictCode, Boolean isDefault);

    /**
     * 按与字典 ID 统计字典项数量。
     *
     * @param dictId   字典 ID
     * @return 字典项数量
     */
    long countByDictId(Long dictId);

    /**
     * 按与字典 ID 清除所有默认标志 (设置默认项前调用)。
     *
     * @param dictId   字典 ID
     * @return 受影响行数
     */
    @Modifying
    @Query(value = "UPDATE ScrmDataDictionaryItemEntity i SET i.isDefault = false WHERE i.dictId = :dictId AND "
                          + "i.isDefault = true")
    int clearDefaultByDict(@Param("dictId") Long dictId);

    /**
     * 按与字典 ID 删除全部字典项。
     *
     * @param dictId   字典 ID
     * @return 受影响行数
     */
    long deleteByDictId(Long dictId);
}
