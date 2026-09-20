/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmDataDictionaryServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmDataDictionaryDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryItemDto;
import org.hiylo.scrm.dto.ScrmDataDictionaryUsageDto;
import org.hiylo.scrm.dto.ScrmDictBatchImportDto;
import org.hiylo.scrm.entity.ScrmDataDictionaryEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryItemEntity;
import org.hiylo.scrm.entity.ScrmDataDictionaryUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmDataDictionaryItemRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryRepository;
import org.hiylo.scrm.repository.ScrmDataDictionaryUsageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmDataDictionaryService 单元测试
 * <p>
 * 聚焦字典与字典项的 CRUD、默认值填充、字典编码账号内唯一校验、系统内置字典保护、
 * 越权访问、字典复制与合并、缓存管理 (清除 / 统计)、字典项默认项唯一性维护、
 * 使用记录幂等累加与统计、批量导入去重、重排序校验、字典统计与健康度等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmDataDictionaryService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmDataDictionaryServiceTest {

    /** 数据字典仓库 Mock */
    @Mock
    private ScrmDataDictionaryRepository dictionaryRepository;
    /** 数据字典项仓库 Mock */
    @Mock
    private ScrmDataDictionaryItemRepository itemRepository;
    /** 数据字典使用记录仓库 Mock */
    @Mock
    private ScrmDataDictionaryUsageRepository usageRepository;

    /** 被测服务实例 */
    private ScrmDataDictionaryService service;

    /** 用于模拟数据库生成主键的自增计数器 */
    private final AtomicLong idSeq = new AtomicLong(1000L);

    @BeforeEach
    void setUp() {
        ScrmDataDictionaryCacheService cacheService =
                new ScrmDataDictionaryCacheService(dictionaryRepository, itemRepository, usageRepository);
        ScrmDataDictionaryItemService itemService =
                new ScrmDataDictionaryItemService(itemRepository, dictionaryRepository, cacheService);
        ScrmDataDictionaryManageService manageService = new ScrmDataDictionaryManageService(
                dictionaryRepository, itemRepository, usageRepository, itemService, cacheService);
        ScrmDataDictionaryStatsService statsService =
                new ScrmDataDictionaryStatsService(dictionaryRepository, itemRepository);
        service = new ScrmDataDictionaryService(manageService, itemService, cacheService, statsService);
        idSeq.set(1000L);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 构造工厂方法 ====================

    private ScrmDataDictionaryDto buildDictDto() {
        ScrmDataDictionaryDto dto = new ScrmDataDictionaryDto();
        dto.setDictName("客户等级");
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setDescription("客户分级字典");
        dto.setCreatedBy("tester");
        return dto;
    }

    private ScrmDataDictionaryEntity buildDictEntity(Long id) {
        ScrmDataDictionaryEntity entity = new ScrmDataDictionaryEntity();
        entity.setId(id);
        entity.setDictName("客户等级");
        entity.setDictCode("CUSTOMER_LEVEL");
        entity.setDictType("LIST");
        entity.setItemCount(0);
        entity.setIsSystem(false);
        entity.setIsCacheable(true);
        entity.setCacheTtlSeconds(3600);
        entity.setSortOrder(0);
        entity.setEnabled(true);
        entity.setUsageCount(0);
        entity.setCreatedBy("tester");
        return entity;
    }

    private ScrmDataDictionaryItemEntity buildItemEntity(Long id, Long dictId, String dictCode) {
        ScrmDataDictionaryItemEntity entity = new ScrmDataDictionaryItemEntity();
        entity.setId(id);
        entity.setDictId(dictId);
        entity.setDictCode(dictCode);
        entity.setItemLabel("普通客户");
        entity.setItemValue("NORMAL");
        entity.setItemCode("NORMAL");
        entity.setItemLevel(1);
        entity.setItemPath(id + "/");
        entity.setSortOrder(0);
        entity.setIsDefault(false);
        entity.setIsDisabled(false);
        entity.setIsVisible(true);
        entity.setUsageCount(0);
        entity.setEnabled(true);
        return entity;
    }

    /** 模拟 JPA save: 返回带生成 ID 的实体 */
    private void stubDictSaveWithGeneratedId() {
        when(dictionaryRepository.save(any(ScrmDataDictionaryEntity.class)))
                .thenAnswer(inv -> {
                    ScrmDataDictionaryEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(idSeq.incrementAndGet());
                    }
                    return e;
                });
    }

    private void stubItemSaveWithGeneratedId() {
        when(itemRepository.save(any(ScrmDataDictionaryItemEntity.class)))
                .thenAnswer(inv -> {
                    ScrmDataDictionaryItemEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(idSeq.incrementAndGet());
                    }
                    return e;
                });
    }

    // ============================================================
    // 字典 CRUD
    // ============================================================

    @Test
    @DisplayName("createDictionary: 写入归属账号与默认值 (dictType=LIST, isCacheable=true, enabled=true)")
    void createDictionary_success() throws ScrmException {
        ScrmDataDictionaryDto dto = buildDictDto();
        when(dictionaryRepository.existsByDictCode("CUSTOMER_LEVEL")).thenReturn(false);
        stubDictSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.createDictionary(dto);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        ScrmDataDictionaryEntity saved = captor.getValue();
        assertThat(saved.getDictName()).isEqualTo("客户等级");
        assertThat(saved.getDictCode()).isEqualTo("CUSTOMER_LEVEL");
        assertThat(saved.getDictType()).isEqualTo("LIST");
        assertThat(saved.getItemCount()).isZero();
        assertThat(saved.getIsSystem()).isFalse();
        assertThat(saved.getIsCacheable()).isTrue();
        assertThat(saved.getCacheTtlSeconds()).isEqualTo(3600);
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getUsageCount()).isZero();
        assertThat(result.getDictCode()).isEqualTo("CUSTOMER_LEVEL");
    }

    @Test
    @DisplayName("createDictionary: dictCode 已存在抛 CONFLICT")
    void createDictionary_duplicateCode() {
        ScrmDataDictionaryDto dto = buildDictDto();
        when(dictionaryRepository.existsByDictCode("CUSTOMER_LEVEL")).thenReturn(true);

        assertThatThrownBy(() -> service.createDictionary(dto))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.CONFLICT);
                });
        verify(dictionaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDictionary: dictName 为空抛 BAD_REQUEST")
    void createDictionary_blankName() {
        ScrmDataDictionaryDto dto = buildDictDto();
        dto.setDictName("  ");

        assertThatThrownBy(() -> service.createDictionary(dto))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(dictionaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDictionary: 非法 dictType 抛 BAD_REQUEST")
    void createDictionary_invalidType() {
        ScrmDataDictionaryDto dto = buildDictDto();
        dto.setDictType("INVALID");

        assertThatThrownBy(() -> service.createDictionary(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典类型非法");
        verify(dictionaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createDictionary: 指定 parentId 时校验父字典存在性")
    void createDictionary_withParent() throws ScrmException {
        ScrmDataDictionaryDto dto = buildDictDto();
        dto.setParentId(10L);
        when(dictionaryRepository.existsByDictCode("CUSTOMER_LEVEL")).thenReturn(false);
        when(dictionaryRepository.findById(10L))
                .thenReturn(Optional.of(buildDictEntity(10L)));
        stubDictSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.createDictionary(dto);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(10L);
        assertThat(result.getParentId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createDictionary: dto 为空抛 BAD_REQUEST")
    void createDictionary_nullDto() {
        assertThatThrownBy(() -> service.createDictionary(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典参数不能为空");
        verify(dictionaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateDictionary: 字段非空才覆盖, 保存后清除缓存")
    void updateDictionary_success() throws ScrmException {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryDto dto = new ScrmDataDictionaryDto();
        dto.setDictName("更新名称");
        dto.setSortOrder(5);
        dto.setEnabled(false);

        ScrmDataDictionaryDto result = service.updateDictionary(10L, dto);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        ScrmDataDictionaryEntity saved = captor.getValue();
        assertThat(saved.getDictName()).isEqualTo("更新名称");
        assertThat(saved.getSortOrder()).isEqualTo(5);
        assertThat(saved.getEnabled()).isFalse();
        // 未设置的字段保留原值
        assertThat(saved.getDictCode()).isEqualTo("CUSTOMER_LEVEL");
        assertThat(result.getDictName()).isEqualTo("更新名称");
    }

    @Test
    @DisplayName("updateDictionary: parentId 等于自身 ID 时跳过 (允许但不更新)")
    void updateDictionary_parentEqualsSelf() throws ScrmException {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryDto dto = new ScrmDataDictionaryDto();
        dto.setParentId(10L);

        service.updateDictionary(10L, dto);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        // parentId 为自身 ID 时不调用 findDictionaryOrThrow, 保留原值 null
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    @DisplayName("deleteDictionary: 系统内置字典禁止删除抛 BAD_REQUEST")
    void deleteDictionary_systemDict() {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        entity.setIsSystem(true);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.deleteDictionary(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("系统内置字典不允许删除");
        verify(dictionaryRepository, never()).delete(any(ScrmDataDictionaryEntity.class));
    }

    @Test
    @DisplayName("deleteDictionary: 非系统字典级联清理字典项与使用记录后删除")
    void deleteDictionary_success() throws ScrmException {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));

        service.deleteDictionary(10L);

        verify(itemRepository, times(1)).deleteByDictId(10L);
        verify(usageRepository, times(1)).deleteByDictId(10L);
        verify(dictionaryRepository, times(1)).delete(entity);
    }

    
    @Test
    @DisplayName("getDictionaryByCode: 编码为空抛 BAD_REQUEST")
    void getDictionaryByCode_blankCode() {
        assertThatThrownBy(() -> service.getDictionaryByCode("  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典编码不能为空");
    }

    @Test
    @DisplayName("disableDictionary: 系统内置字典禁止禁用抛 BAD_REQUEST")
    void disableDictionary_systemDict() {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        entity.setIsSystem(true);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.disableDictionary(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("系统内置字典不允许禁用");
        verify(dictionaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("enableDictionary: 启用字典并清除缓存")
    void enableDictionary_success() throws ScrmException {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        entity.setEnabled(false);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));
        stubDictSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.enableDictionary(10L);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
        assertThat(result.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("copyDictionary: 复制字典与字典项, 新编码冲突抛 CONFLICT")
    void copyDictionary_duplicateCode() {
        ScrmDataDictionaryEntity source = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(source));
        when(dictionaryRepository.existsByDictCode("DUP_CODE")).thenReturn(true);

        assertThatThrownBy(() -> service.copyDictionary(10L, "DUP_CODE"))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.CONFLICT);
                });
    }

    @Test
    @DisplayName("copyDictionary: 新字典编码为空抛 BAD_REQUEST")
    void copyDictionary_blankCode() {
        assertThatThrownBy(() -> service.copyDictionary(10L, "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("新字典编码不能为空");
    }

    @Test
    @DisplayName("copyDictionary: 复制成功时 isSystem 强制为 false 且 itemCount 为字典项数量")
    void copyDictionary_success() throws ScrmException {
        ScrmDataDictionaryEntity source = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(source));
        when(dictionaryRepository.existsByDictCode("LEVEL_COPY")).thenReturn(false);
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryItemEntity srcItem = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findByDictIdOrderBySortOrderAsc(10L))
                .thenReturn(Collections.singletonList(srcItem));
        stubItemSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.copyDictionary(10L, "LEVEL_COPY");

        ArgumentCaptor<ScrmDataDictionaryEntity> dictCaptor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(2)).save(dictCaptor.capture());
        ScrmDataDictionaryEntity target = dictCaptor.getAllValues().get(0);
        assertThat(target.getDictCode()).isEqualTo("LEVEL_COPY");
        assertThat(target.getDictName()).isEqualTo("客户等级_副本");
        assertThat(target.getIsSystem()).isFalse();
        // 字典项已复制
        verify(itemRepository, times(2)).save(any(ScrmDataDictionaryItemEntity.class));
        assertThat(result.getDictCode()).isEqualTo("LEVEL_COPY");
    }

    @Test
    @DisplayName("mergeDictionaries: 源与目标相同抛 BAD_REQUEST")
    void mergeDictionaries_sameId() {
        assertThatThrownBy(() -> service.mergeDictionaries(10L, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("源字典与目标字典不能相同");
    }

    @Test
    @DisplayName("mergeDictionaries: ID 为空抛 BAD_REQUEST")
    void mergeDictionaries_nullIds() {
        assertThatThrownBy(() -> service.mergeDictionaries(null, 10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("源字典 ID 与目标字典 ID 不能为空");
    }

    @Test
    @DisplayName("mergeDictionaries: 重复项跳过, 非重复项迁移")
    void mergeDictionaries_skipDuplicate() throws ScrmException {
        ScrmDataDictionaryEntity source = buildDictEntity(10L);
        ScrmDataDictionaryEntity target = buildDictEntity(11L);
        target.setDictCode("TARGET_DICT");
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(source));
        when(dictionaryRepository.findById(11L)).thenReturn(Optional.of(target));
        // 源字典项 itemCode=NORMAL 与目标重复, itemCode=VIP 不重复
        ScrmDataDictionaryItemEntity src1 = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        ScrmDataDictionaryItemEntity src2 = buildItemEntity(21L, 10L, "CUSTOMER_LEVEL");
        src2.setItemCode("VIP");
        src2.setItemValue("VIP");
        when(itemRepository.findByDictIdOrderBySortOrderAsc(10L))
                .thenReturn(Arrays.asList(src1, src2));
        ScrmDataDictionaryItemEntity tgt1 = buildItemEntity(30L, 11L, "TARGET_DICT");
        when(itemRepository.findByDictIdOrderBySortOrderAsc(11L))
                .thenReturn(Collections.singletonList(tgt1));
        stubItemSaveWithGeneratedId();
        stubDictSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.mergeDictionaries(10L, 11L);

        // 仅 src2 被迁移 (1 次 save 创建 + 1 次 save 路径)
        verify(itemRepository, times(2)).save(any(ScrmDataDictionaryItemEntity.class));
        ArgumentCaptor<ScrmDataDictionaryEntity> dictCaptor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(dictCaptor.capture());
        assertThat(dictCaptor.getValue().getItemCount()).isEqualTo(2); // 1 已有 + 1 合并
        assertThat(result.getDictCode()).isEqualTo("TARGET_DICT");
    }

    @Test
    @DisplayName("updateDictionaryStats: 重新计算 itemCount 与 usageCount")
    void updateDictionaryStats_success() throws ScrmException {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        entity.setItemCount(null);
        entity.setUsageCount(null);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(itemRepository.countByDictId(10L)).thenReturn(5L);
        ScrmDataDictionaryUsageEntity u1 = new ScrmDataDictionaryUsageEntity();
        u1.setUsageCount(3);
        u1.setLastUsedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        ScrmDataDictionaryUsageEntity u2 = new ScrmDataDictionaryUsageEntity();
        u2.setUsageCount(7);
        u2.setLastUsedAt(LocalDateTime.of(2026, 8, 3, 10, 0));
        when(usageRepository.findByDictId(10L)).thenReturn(Arrays.asList(u1, u2));
        stubDictSaveWithGeneratedId();

        ScrmDataDictionaryDto result = service.updateDictionaryStats(10L);

        ArgumentCaptor<ScrmDataDictionaryEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getItemCount()).isEqualTo(5);
        assertThat(captor.getValue().getUsageCount()).isEqualTo(10);
        assertThat(captor.getValue().getLastUsedAt()).isEqualTo(LocalDateTime.of(2026, 8, 3, 10, 0));
        assertThat(result.getItemCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getDictionaryTree: 按分类分组, 无分类归入 UNGROUPED")
    void getDictionaryTree_success() {
        ScrmDataDictionaryEntity d1 = buildDictEntity(10L);
        d1.setCategory("BUSINESS");
        ScrmDataDictionaryEntity d2 = buildDictEntity(11L);
        d2.setCategory(null);
        when(dictionaryRepository.findByEnabledTrue())
                .thenReturn(Arrays.asList(d1, d2));

        Map<String, List<ScrmDataDictionaryDto>> tree = service.getDictionaryTree();

        assertThat(tree).containsKey("BUSINESS").containsKey("UNGROUPED");
        assertThat(tree.get("BUSINESS")).hasSize(1);
        assertThat(tree.get("UNGROUPED")).hasSize(1);
    }

    @Test
    @DisplayName("listDictionaries: 调用 findAll(spec, pageable) 返回分页结果")
    void listDictionaries_success() {
        ScrmDataDictionaryEntity entity = buildDictEntity(10L);
        PageImpl<ScrmDataDictionaryEntity> page =
                new PageImpl<>(Collections.singletonList(entity), PageRequest.of(0, 10), 1L);
        when(dictionaryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listDictionaries(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(dictionaryRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
    }

    // ============================================================
    // 字典项 CRUD
    // ============================================================

    @Test
    @DisplayName("createItem: 根项写入 level=1 与路径, 默认值补全")
    void createItem_rootItem() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        stubItemSaveWithGeneratedId();
        when(itemRepository.countByDictId(10L)).thenReturn(1L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("VIP");
        dto.setItemValue("VIP");

        ScrmDataDictionaryItemDto result = service.createItem(dto);

        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, atLeastOnce()).save(captor.capture());
        ScrmDataDictionaryItemEntity saved = captor.getValue();
        assertThat(saved.getDictId()).isEqualTo(10L);
        assertThat(saved.getItemLevel()).isEqualTo(1);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getIsDefault()).isFalse();
        assertThat(saved.getIsVisible()).isTrue();
        assertThat(result.getItemLabel()).isEqualTo("VIP");
    }

    @Test
    @DisplayName("createItem: dictId 与 dictCode 不匹配抛 BAD_REQUEST")
    void createItem_dictIdMismatch() {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(99L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("VIP");
        dto.setItemValue("VIP");

        assertThatThrownBy(() -> service.createItem(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典 ID 与字典编码不匹配");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("createItem: itemLabel 为空抛 BAD_REQUEST")
    void createItem_blankLabel() {
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("  ");
        dto.setItemValue("VIP");

        assertThatThrownBy(() -> service.createItem(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典项标签不能为空");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("createItem: 非法 itemStyle 抛 BAD_REQUEST")
    void createItem_invalidStyle() {
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("VIP");
        dto.setItemValue("VIP");
        dto.setItemStyle("INVALID");

        assertThatThrownBy(() -> service.createItem(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典项样式非法");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("createItem: 指定父项时自动维护 level=parent.level+1 与 path")
    void createItem_withParent() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        ScrmDataDictionaryItemEntity parent = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        parent.setItemLevel(1);
        parent.setItemPath("20/");
        when(itemRepository.findById(20L)).thenReturn(Optional.of(parent));
        stubItemSaveWithGeneratedId();
        when(itemRepository.countByDictId(10L)).thenReturn(2L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("子项");
        dto.setItemValue("CHILD");
        dto.setParentId(20L);

        service.createItem(dto);

        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, atLeastOnce()).save(captor.capture());
        ScrmDataDictionaryItemEntity saved = captor.getValue();
        assertThat(saved.getParentId()).isEqualTo(20L);
        assertThat(saved.getItemLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("createItem: 父项不属于同一字典抛 BAD_REQUEST")
    void createItem_parentDifferentDict() {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        ScrmDataDictionaryItemEntity parent = buildItemEntity(20L, 99L, "OTHER_DICT");
        when(itemRepository.findById(20L)).thenReturn(Optional.of(parent));
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemLabel("子项");
        dto.setItemValue("CHILD");
        dto.setParentId(20L);

        assertThatThrownBy(() -> service.createItem(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("父项不属于同一字典");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateItem: parentId 等于自身 ID 抛 BAD_REQUEST")
    void updateItem_selfParent() {
        ScrmDataDictionaryItemEntity entity = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findById(20L)).thenReturn(Optional.of(entity));
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setParentId(20L);
        dto.setItemLabel("更新");
        dto.setItemValue("UPD");

        assertThatThrownBy(() -> service.updateItem(20L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典项的父项不能为自身");
        verify(itemRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("getItemByValue: 编码或值为空抛 BAD_REQUEST")
    void getItemByValue_blankParams() {
        assertThatThrownBy(() -> service.getItemByValue("  ", "V1"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典编码与字典项值不能为空");
    }

    @Test
    @DisplayName("getItemByValue: 不存在抛 NOT_FOUND")
    void getItemByValue_notFound() {
        when(itemRepository.findByDictCodeAndItemValue("CUSTOMER_LEVEL", "MISSING"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getItemByValue("CUSTOMER_LEVEL", "MISSING"))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("getItemByCode: 编码或项编码为空抛 BAD_REQUEST")
    void getItemByCode_blankParams() {
        assertThatThrownBy(() -> service.getItemByCode("CUSTOMER_LEVEL", "  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典编码与字典项编码不能为空");
    }

    @Test
    @DisplayName("setDefault: 清除同字典其他默认项后设为默认")
    void setDefault_success() throws ScrmException {
        ScrmDataDictionaryItemEntity entity = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        entity.setIsDefault(false);
        when(itemRepository.findById(20L)).thenReturn(Optional.of(entity));
        stubItemSaveWithGeneratedId();

        ScrmDataDictionaryItemDto result = service.setDefault(20L);

        verify(itemRepository, times(1)).clearDefaultByDict(10L);
        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsDefault()).isTrue();
        assertThat(result.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("moveItem: parentId 等于自身 ID 抛 BAD_REQUEST")
    void moveItem_selfParent() {
        ScrmDataDictionaryItemEntity entity = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findById(20L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.moveItem(20L, 20L, 1))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典项的父项不能为自身");
        verify(itemRepository, never()).save(any());
    }

    @Test
    @DisplayName("moveItem: 移到根级时 level=1, path=id/")
    void moveItem_toRoot() throws ScrmException {
        ScrmDataDictionaryItemEntity entity = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        entity.setParentId(19L);
        entity.setItemLevel(2);
        when(itemRepository.findById(20L)).thenReturn(Optional.of(entity));
        stubItemSaveWithGeneratedId();

        ScrmDataDictionaryItemDto result = service.moveItem(20L, null, 5);

        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, times(1)).save(captor.capture());
        ScrmDataDictionaryItemEntity saved = captor.getValue();
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getItemLevel()).isEqualTo(1);
        assertThat(saved.getItemPath()).isEqualTo("20/");
        assertThat(saved.getSortOrder()).isEqualTo(5);
        assertThat(result.getSortOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("enableItem: 启用字典项并清除缓存")
    void enableItem_success() throws ScrmException {
        ScrmDataDictionaryItemEntity entity = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        entity.setEnabled(false);
        when(itemRepository.findById(20L)).thenReturn(Optional.of(entity));
        stubItemSaveWithGeneratedId();

        ScrmDataDictionaryItemDto result = service.enableItem(20L);

        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
        assertThat(result.getEnabled()).isTrue();
    }

    // ============================================================
    // 批量操作
    // ============================================================

    @Test
    @DisplayName("batchImport: 参数为空抛 BAD_REQUEST")
    void batchImport_nullDto() {
        assertThatThrownBy(() -> service.batchImport(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量导入参数与字典项列表不能为空");
    }

    @Test
    @DisplayName("batchImport: 字典项列表为空抛 BAD_REQUEST")
    void batchImport_emptyItems() {
        ScrmDictBatchImportDto dto = new ScrmDictBatchImportDto();
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItems(Collections.emptyList());

        assertThatThrownBy(() -> service.batchImport(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量导入参数与字典项列表不能为空");
    }

    @Test
    @DisplayName("batchImport: 不存在项新增, 存在项默认跳过 (overwrite=false)")
    void batchImport_insertAndSkip() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        ScrmDataDictionaryItemEntity existing = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findByDictCodeOrderBySortOrderAsc("CUSTOMER_LEVEL"))
                .thenReturn(Collections.singletonList(existing));
        stubItemSaveWithGeneratedId();
        when(itemRepository.countByDictId(10L)).thenReturn(2L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        stubDictSaveWithGeneratedId();

        ScrmDictBatchImportDto dto = new ScrmDictBatchImportDto();
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setOverwrite(false);
        ScrmDataDictionaryItemDto newItem = new ScrmDataDictionaryItemDto();
        newItem.setItemLabel("VIP");
        newItem.setItemValue("VIP");
        ScrmDataDictionaryItemDto dupItem = new ScrmDataDictionaryItemDto();
        dupItem.setItemLabel("普通");
        dupItem.setItemValue("NORMAL");
        dto.setItems(Arrays.asList(newItem, dupItem));

        Map<String, Object> result = service.batchImport(dto);

        assertThat(result).containsEntry("inserted", 1).containsEntry("skipped", 1);
    }

    @Test
    @DisplayName("reorderItems: 字典 ID 为空抛 BAD_REQUEST")
    void reorderItems_nullDictId() {
        assertThatThrownBy(() -> service.reorderItems(null, Collections.singletonList(1L)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典 ID 不能为空");
    }

    @Test
    @DisplayName("reorderItems: 顺序列表为空抛 BAD_REQUEST")
    void reorderItems_emptyList() {
        assertThatThrownBy(() -> service.reorderItems(10L, Collections.emptyList()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典项顺序列表不能为空");
    }

    
    @Test
    @DisplayName("reorderItems: 按 ID 顺序依次设置 sortOrder")
    void reorderItems_success() throws ScrmException {
        ScrmDataDictionaryItemEntity item1 = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        ScrmDataDictionaryItemEntity item2 = buildItemEntity(21L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findById(20L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(21L)).thenReturn(Optional.of(item2));
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        stubItemSaveWithGeneratedId();

        Map<String, Object> result = service.reorderItems(10L, Arrays.asList(21L, 20L));

        ArgumentCaptor<ScrmDataDictionaryItemEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryItemEntity.class);
        verify(itemRepository, times(2)).save(captor.capture());
        List<ScrmDataDictionaryItemEntity> saved = captor.getAllValues();
        assertThat(saved.get(0).getId()).isEqualTo(21L);
        assertThat(saved.get(0).getSortOrder()).isZero();
        assertThat(saved.get(1).getId()).isEqualTo(20L);
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
        assertThat(result).containsEntry("reordered", 2);
    }

    @Test
    @DisplayName("batchUpdateItems: 列表为空抛 BAD_REQUEST")
    void batchUpdateItems_emptyList() {
        assertThatThrownBy(() -> service.batchUpdateItems(Collections.emptyList()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量更新列表不能为空");
    }

    @Test
    @DisplayName("batchUpdateItems: ID 为空抛 BAD_REQUEST")
    void batchUpdateItems_nullId() {
        ScrmDataDictionaryItemDto dto = new ScrmDataDictionaryItemDto();
        dto.setItemLabel("更新");
        dto.setItemValue("UPD");

        assertThatThrownBy(() -> service.batchUpdateItems(Collections.singletonList(dto)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("批量更新字典项 ID 不能为空");
    }

    // ============================================================
    // 使用记录与统计
    // ============================================================

    @Test
    @DisplayName("recordUsage: dictId / dictCode / usageModule 为空抛 BAD_REQUEST")
    void recordUsage_missingRequiredFields() {
        ScrmDataDictionaryUsageDto dto = new ScrmDataDictionaryUsageDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");

        assertThatThrownBy(() -> service.recordUsage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典 ID / 字典编码 / 使用模块不能为空");
    }

    @Test
    @DisplayName("recordUsage: dictId 与 dictCode 不匹配抛 BAD_REQUEST")
    void recordUsage_dictIdMismatch() {
        ScrmDataDictionaryUsageDto dto = new ScrmDataDictionaryUsageDto();
        dto.setDictId(99L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setUsageModule("customer");
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));

        assertThatThrownBy(() -> service.recordUsage(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典 ID 与字典编码不匹配");
    }

    @Test
    @DisplayName("recordUsage: 不存在记录时新建, increment=true 按 delta 累加")
    void recordUsage_createNew() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        when(usageRepository.findByDictIdAndItemIdAndUsageModule(10L, 20L, "customer"))
                .thenReturn(Optional.empty());
        when(usageRepository.save(any(ScrmDataDictionaryUsageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryUsageDto dto = new ScrmDataDictionaryUsageDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemId(20L);
        dto.setUsageModule("customer");
        dto.setUsageCount(5);
        dto.setIncrement(true);

        ScrmDataDictionaryUsageDto result = service.recordUsage(dto);

        ArgumentCaptor<ScrmDataDictionaryUsageEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryUsageEntity.class);
        verify(usageRepository, times(1)).save(captor.capture());
        ScrmDataDictionaryUsageEntity saved = captor.getValue();
        assertThat(saved.getUsageCount()).isEqualTo(5);
        assertThat(saved.getFirstUsedAt()).isNotNull();
        assertThat(saved.getLastUsedAt()).isNotNull();
        // 字典 usageCount 累加 delta
        ArgumentCaptor<ScrmDataDictionaryEntity> dictCaptor =
                ArgumentCaptor.forClass(ScrmDataDictionaryEntity.class);
        verify(dictionaryRepository, times(1)).save(dictCaptor.capture());
        assertThat(dictCaptor.getValue().getUsageCount()).isEqualTo(5);
        assertThat(result.getUsageCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("recordUsage: 已存在记录累加, increment=false 时覆盖")
    void recordUsage_overwriteExisting() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        dict.setUsageCount(10);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        ScrmDataDictionaryUsageEntity existing = new ScrmDataDictionaryUsageEntity();
        existing.setUsageCount(3);
        when(usageRepository.findByDictIdAndItemIdAndUsageModule(10L, 20L, "customer"))
                .thenReturn(Optional.of(existing));
        when(usageRepository.save(any(ScrmDataDictionaryUsageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDictSaveWithGeneratedId();
        ScrmDataDictionaryUsageDto dto = new ScrmDataDictionaryUsageDto();
        dto.setDictId(10L);
        dto.setDictCode("CUSTOMER_LEVEL");
        dto.setItemId(20L);
        dto.setUsageModule("customer");
        dto.setUsageCount(99);
        dto.setIncrement(false);

        service.recordUsage(dto);

        ArgumentCaptor<ScrmDataDictionaryUsageEntity> captor =
                ArgumentCaptor.forClass(ScrmDataDictionaryUsageEntity.class);
        verify(usageRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsageCount()).isEqualTo(99);
    }

    @Test
    @DisplayName("getUsageStats: 按时间范围过滤并聚合 byItem / byModule")
    void getUsageStats_success() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 5, 10, 0);
        ScrmDataDictionaryUsageEntity u1 = new ScrmDataDictionaryUsageEntity();
        u1.setItemId(20L);
        u1.setUsageModule("customer");
        u1.setUsageCount(5);
        u1.setLastUsedAt(t1);
        ScrmDataDictionaryUsageEntity u2 = new ScrmDataDictionaryUsageEntity();
        u2.setItemId(21L);
        u2.setUsageModule("order");
        u2.setUsageCount(3);
        u2.setLastUsedAt(t2);
        when(usageRepository.findByDictId(10L)).thenReturn(Arrays.asList(u1, u2));

        Map<String, Object> stats = service.getUsageStats(10L,
                LocalDateTime.of(2026, 8, 3, 0, 0), null);

        assertThat(stats.get("total")).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        Map<String, Integer> byModule = (Map<String, Integer>) stats.get("byModule");
        assertThat(byModule).containsEntry("order", 3);
    }

    @Test
    @DisplayName("getUsageByModule: 按模块聚合统计")
    void getUsageByModule_success() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        ScrmDataDictionaryUsageEntity u1 = new ScrmDataDictionaryUsageEntity();
        u1.setUsageModule("customer");
        u1.setUsageCount(4);
        ScrmDataDictionaryUsageEntity u2 = new ScrmDataDictionaryUsageEntity();
        u2.setUsageModule("customer");
        u2.setUsageCount(6);
        when(usageRepository.findByDictId(10L)).thenReturn(Arrays.asList(u1, u2));

        Map<String, Object> result = service.getUsageByModule(10L);

        assertThat(result.get("total")).isEqualTo(10L);
        @SuppressWarnings("unchecked")
        Map<String, Integer> byModule = (Map<String, Integer>) result.get("byModule");
        assertThat(byModule).containsEntry("customer", 10);
    }

    @Test
    @DisplayName("cleanupUsage: 返回清理记录数")
    void cleanupUsage_success() {
        when(usageRepository.deleteExpired(any(LocalDateTime.class))).thenReturn(7);

        Map<String, Object> result = service.cleanupUsage(30);

        assertThat(result).containsEntry("deleted", 7);
        assertThat(result.get("threshold")).isInstanceOf(LocalDateTime.class);
    }

    @Test
    @DisplayName("getPopularItems: 返回热门字典项列表")
    void getPopularItems_success() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        ScrmDataDictionaryUsageEntity u1 = new ScrmDataDictionaryUsageEntity();
        u1.setItemId(20L);
        u1.setItemValue("VIP");
        u1.setUsageModule("customer");
        u1.setUsageCount(10);
        u1.setLastUsedAt(LocalDateTime.now());
        when(usageRepository.findPopularByDict(10L))
                .thenReturn(Collections.singletonList(u1));

        List<Map<String, Object>> result = service.getPopularItems(10L, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("itemId", 20L).containsEntry("usageCount", 10);
    }

    @Test
    @DisplayName("getUnusedItems: 返回未使用字典项")
    void getUnusedItems_success() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findById(10L)).thenReturn(Optional.of(dict));
        ScrmDataDictionaryItemEntity item1 = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        ScrmDataDictionaryItemEntity item2 = buildItemEntity(21L, 10L, "CUSTOMER_LEVEL");
        when(itemRepository.findByDictIdOrderBySortOrderAsc(10L))
                .thenReturn(Arrays.asList(item1, item2));
        // 仅 item1 有使用记录, item2 未使用
        ScrmDataDictionaryUsageEntity u = new ScrmDataDictionaryUsageEntity();
        u.setItemId(20L);
        u.setLastUsedAt(LocalDateTime.now());
        when(usageRepository.findByDictId(10L))
                .thenReturn(Collections.singletonList(u));

        List<ScrmDataDictionaryItemDto> result = service.getUnusedItems(10L, 30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(21L);
    }

    // ============================================================
    // 缓存管理
    // ============================================================

    @Test
    @DisplayName("clearAllCache: 返回清除的缓存条目数")
    void clearAllCache_success() {
        // 先填充缓存
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        dict.setIsCacheable(true);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        when(itemRepository.findByDictCodeAndEnabledTrueOrderBySortOrderAsc("CUSTOMER_LEVEL"))
                .thenReturn(Collections.emptyList());
        service.getCachedItems("CUSTOMER_LEVEL");

        Map<String, Object> result = service.clearAllCache();

        assertThat(result).containsEntry("cleared", 1);
    }

    @Test
    @DisplayName("clearCache: 指定字典编码为空时不操作")
    void clearCache_blankCode() {
        service.clearCache("  ");
        // 仅验证不抛异常
    }

    @Test
    @DisplayName("getCacheStats: 返回缓存大小 / keys / expired")
    void getCacheStats_success() {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        dict.setIsCacheable(true);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        when(itemRepository.findByDictCodeAndEnabledTrueOrderBySortOrderAsc("CUSTOMER_LEVEL"))
                .thenReturn(Collections.emptyList());
        service.getCachedItems("CUSTOMER_LEVEL");

        Map<String, Object> stats = service.getCacheStats();

        assertThat(stats.get("size")).isEqualTo(1);
        assertThat(stats.get("expired")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) stats.get("keys");
        assertThat(keys).hasSize(1).first().asString().contains("CUSTOMER_LEVEL");
    }

    @Test
    @DisplayName("getCachedItems: 不可缓存字典直接查库不缓存")
    void getCachedItems_nonCacheable() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        dict.setIsCacheable(false);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        when(itemRepository.findByDictCodeAndEnabledTrueOrderBySortOrderAsc("CUSTOMER_LEVEL"))
                .thenReturn(Collections.emptyList());

        List<ScrmDataDictionaryItemDto> items = service.getCachedItems("CUSTOMER_LEVEL");

        assertThat(items).isEmpty();
        // 缓存统计为空 (未缓存)
        assertThat(service.getCacheStats().get("size")).isEqualTo(0);
    }

    @Test
    @DisplayName("getCachedItems: 字典编码为空抛 BAD_REQUEST")
    void getCachedItems_blankCode() {
        assertThatThrownBy(() -> service.getCachedItems("  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典编码不能为空");
    }

    // ============================================================
    // 统计与健康度
    // ============================================================

    @Test
    @DisplayName("getDictionaryStats: 聚合总数 / 类型 / 分类 / 系统数")
    void getDictionaryStats_success() {
        ScrmDataDictionaryEntity d1 = buildDictEntity(10L);
        d1.setDictType("LIST");
        d1.setCategory("BUSINESS");
        d1.setIsSystem(true);
        d1.setEnabled(true);
        ScrmDataDictionaryEntity d2 = buildDictEntity(11L);
        d2.setDictType("TREE");
        d2.setCategory("CUSTOM");
        d2.setIsSystem(false);
        d2.setEnabled(false);
        when(dictionaryRepository.findAll(any(Specification.class)))
                .thenReturn(Arrays.asList(d1, d2));

        Map<String, Object> stats = service.getDictionaryStats();

        assertThat(stats.get("total")).isEqualTo(2L);
        assertThat(stats.get("systemCount")).isEqualTo(1L);
        assertThat(stats.get("customCount")).isEqualTo(1L);
        assertThat(stats.get("enabledCount")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byType = (Map<String, Long>) stats.get("byType");
        assertThat(byType).containsEntry("LIST", 1L).containsEntry("TREE", 1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byCategory = (Map<String, Long>) stats.get("byCategory");
        assertThat(byCategory).containsEntry("BUSINESS", 1L).containsEntry("CUSTOM", 1L);
    }

    @Test
    @DisplayName("getModuleStats: 按模块分组的字典数与字典项数")
    void getModuleStats_success() {
        ScrmDataDictionaryEntity d1 = buildDictEntity(10L);
        d1.setModule("customer");
        d1.setItemCount(5);
        ScrmDataDictionaryEntity d2 = buildDictEntity(11L);
        d2.setModule(null);
        d2.setItemCount(3);
        when(dictionaryRepository.findAll(any(Specification.class)))
                .thenReturn(Arrays.asList(d1, d2));

        Map<String, Object> stats = service.getModuleStats();

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Long>> byModule =
                (Map<String, Map<String, Long>>) stats.get("byModule");
        assertThat(byModule).containsKey("customer").containsKey("UNGROUPED");
        assertThat(byModule.get("customer")).containsEntry("dictCount", 1L).containsEntry("itemCount", 5L);
    }

    @Test
    @DisplayName("getDictionaryHealth: 标记空字典与未使用字典")
    void getDictionaryHealth_success() {
        ScrmDataDictionaryEntity d1 = buildDictEntity(10L);
        d1.setItemCount(0);
        d1.setUsageCount(0);
        d1.setEnabled(true);
        ScrmDataDictionaryEntity d2 = buildDictEntity(11L);
        d2.setItemCount(5);
        d2.setUsageCount(10);
        d2.setEnabled(true);
        when(dictionaryRepository.findAll(any(Specification.class)))
                .thenReturn(Arrays.asList(d1, d2));
        when(itemRepository.findByDictIdOrderBySortOrderAsc(10L))
                .thenReturn(Collections.emptyList());
        when(itemRepository.findByDictIdOrderBySortOrderAsc(11L))
                .thenReturn(Collections.emptyList());

        Map<String, Object> health = service.getDictionaryHealth();

        assertThat(health.get("totalDicts")).isEqualTo(2L);
        assertThat(health.get("enabledDicts")).isEqualTo(2L);
        assertThat(health.get("emptyDictCount")).isEqualTo(1L);
        assertThat(health.get("unusedDictCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("getItemStats: 字典项统计 (总数 / 启用 / 禁用 / 默认)")
    void getItemStats_success() {
        ScrmDataDictionaryItemEntity i1 = buildItemEntity(20L, 10L, "CUSTOMER_LEVEL");
        i1.setEnabled(true);
        i1.setIsDefault(true);
        i1.setUsageCount(5);
        ScrmDataDictionaryItemEntity i2 = buildItemEntity(21L, 10L, "CUSTOMER_LEVEL");
        i2.setEnabled(false);
        i2.setIsDefault(false);
        i2.setUsageCount(3);
        when(itemRepository.findAll(any(Specification.class)))
                .thenReturn(Arrays.asList(i1, i2));

        Map<String, Object> stats = service.getItemStats();

        assertThat(stats.get("total")).isEqualTo(2L);
        assertThat(stats.get("enabled")).isEqualTo(1L);
        assertThat(stats.get("disabled")).isEqualTo(1L);
        assertThat(stats.get("defaultCount")).isEqualTo(1L);
        assertThat(stats.get("totalUsageCount")).isEqualTo(8L);
    }

    // ============================================================
    // 导入导出
    // ============================================================

    @Test
    @DisplayName("exportDictionary: 字典编码为空抛 BAD_REQUEST")
    void exportDictionary_blankCode() {
        assertThatThrownBy(() -> service.exportDictionary("  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("字典编码不能为空");
    }

    @Test
    @DisplayName("exportDictionary: 返回字典元信息与字典项列表")
    void exportDictionary_success() throws ScrmException {
        ScrmDataDictionaryEntity dict = buildDictEntity(10L);
        when(dictionaryRepository.findByDictCode("CUSTOMER_LEVEL"))
                .thenReturn(Optional.of(dict));
        when(itemRepository.findByDictCodeOrderBySortOrderAsc("CUSTOMER_LEVEL"))
                .thenReturn(Collections.singletonList(buildItemEntity(20L, 10L, "CUSTOMER_LEVEL")));

        Map<String, Object> result = service.exportDictionary("CUSTOMER_LEVEL");

        assertThat(result).containsKey("dictionary").containsKey("items").containsKey("exportedAt");
        @SuppressWarnings("unchecked")
        List<ScrmDataDictionaryItemDto> items = (List<ScrmDataDictionaryItemDto>) result.get("items");
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("getExportTemplate: 返回模板数据")
    void getExportTemplate_success() {
        Map<String, Object> template = service.getExportTemplate();

        assertThat(template).containsKey("dictionary").containsKey("items").containsKey("overwrite");
        @SuppressWarnings("unchecked")
        Map<String, Object> dict = (Map<String, Object>) template.get("dictionary");
        assertThat(dict).containsKey("dictCode").containsKey("dictType");
    }

    @Test
    @DisplayName("importDictionary: 数据为空抛 BAD_REQUEST")
    void importDictionary_nullData() {
        assertThatThrownBy(() -> service.importDictionary(null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("导入数据不能为空");
    }

    @Test
    @DisplayName("importDictionary: 缺少 dictionary 字段抛 BAD_REQUEST")
    void importDictionary_missingDictionary() {
        assertThatThrownBy(() -> service.importDictionary(Collections.emptyMap()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("导入数据不能为空");
    }

    @Test
    @DisplayName("importDictionary: 字典编码已存在且 overwrite=false 时跳过")
    void importDictionary_skipExisting() throws ScrmException {
        Map<String, Object> importData = new java.util.HashMap<>();
        Map<String, Object> dictMap = new java.util.HashMap<>();
        dictMap.put("dictName", "客户等级");
        dictMap.put("dictCode", "CUSTOMER_LEVEL");
        dictMap.put("dictType", "LIST");
        importData.put("dictionary", dictMap);
        importData.put("overwrite", false);
        when(dictionaryRepository.existsByDictCode("CUSTOMER_LEVEL")).thenReturn(true);

        Map<String, Object> result = service.importDictionary(importData);

        assertThat(result).containsEntry("action", "skipped");
    }

}
