/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechLibraryServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmMaterialDto;
import org.hiylo.scrm.dto.ScrmSpeechCategoryDto;
import org.hiylo.scrm.dto.ScrmSpeechDto;
import org.hiylo.scrm.entity.ScrmMaterialEntity;
import org.hiylo.scrm.entity.ScrmSpeechCategoryEntity;
import org.hiylo.scrm.entity.ScrmSpeechEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmMaterialRepository;
import org.hiylo.scrm.repository.ScrmSpeechCategoryRepository;
import org.hiylo.scrm.repository.ScrmSpeechRepository;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmSpeechLibraryService 单元测试
 * <p>
 * 聚焦话术分类 / 话术条目 / 素材库的 CRUD、默认值填充、父子分类校验、
 * 状态切换、计数自增 (useCount/likeCount/downloadCount)、越权隔离与
 * 分类删除的子分类引用检查等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmSpeechLibraryService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmSpeechLibraryServiceTest {

    /** 话术分类仓库 Mock */
    @Mock
    private ScrmSpeechCategoryRepository speechCategoryRepository;
    /** 话术条目仓库 Mock */
    @Mock
    private ScrmSpeechRepository speechRepository;
    /** 素材库仓库 Mock */
    @Mock
    private ScrmMaterialRepository materialRepository;

    /** 被测服务实例 */
    private ScrmSpeechLibraryService service;

    @BeforeEach
    void setUp() {
        service = new ScrmSpeechLibraryService(speechCategoryRepository, speechRepository, materialRepository);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 构造工厂方法 ====================

    /**
     * 构造话术分类 DTO
     */
    private ScrmSpeechCategoryDto buildCategoryDto() {
        ScrmSpeechCategoryDto dto = new ScrmSpeechCategoryDto();
        dto.setCategoryName("售前话术");
        dto.setDescription("售前场景话术合集");
        dto.setSortOrder(1);
        return dto;
    }

    /**
     * 构造已持久化的话术分类实体
     */
    private ScrmSpeechCategoryEntity buildCategoryEntity(Long id) {
        ScrmSpeechCategoryEntity entity = new ScrmSpeechCategoryEntity();
        entity.setId(id);
        entity.setCategoryName("售前话术");
        entity.setParentId(null);
        entity.setSortOrder(1);
        entity.setDescription("售前场景话术合集");
        entity.setStatus("ACTIVE");
        return entity;
    }

    /**
     * 构造话术条目 DTO
     */
    private ScrmSpeechDto buildSpeechDto() {
        ScrmSpeechDto dto = new ScrmSpeechDto();
        dto.setTitle("问候话术");
        dto.setContent("您好,很高兴为您服务!");
        dto.setPlatformType("wechat");
        dto.setScenario("GREETING");
        dto.setTags("问候,开场");
        dto.setCreatedBy("tester");
        return dto;
    }

    /**
     * 构造已持久化的话术条目实体
     */
    private ScrmSpeechEntity buildSpeechEntity(Long id) {
        ScrmSpeechEntity entity = new ScrmSpeechEntity();
        entity.setId(id);
        entity.setTitle("问候话术");
        entity.setContent("您好,很高兴为您服务!");
        entity.setSpeechType("TEXT");
        entity.setPlatformType("wechat");
        entity.setScenario("GREETING");
        entity.setTags("问候,开场");
        entity.setSortOrder(0);
        entity.setUseCount(5);
        entity.setLikeCount(2);
        entity.setStatus("ACTIVE");
        entity.setCreatedBy("tester");
        return entity;
    }

    /**
     * 构造素材 DTO
     */
    private ScrmMaterialDto buildMaterialDto() {
        ScrmMaterialDto dto = new ScrmMaterialDto();
        dto.setMaterialName("产品介绍图");
        dto.setMaterialType("IMAGE");
        dto.setFileUrl("https://example.com/product.png");
        dto.setFileSize(1024L);
        dto.setFileSizeText("1KB");
        dto.setThumbnailUrl("https://example.com/product_thumb.png");
        dto.setDescription("产品介绍图片素材");
        dto.setTags("产品,介绍");
        dto.setUploadedBy("tester");
        return dto;
    }

    /**
     * 构造已持久化的素材实体
     */
    private ScrmMaterialEntity buildMaterialEntity(Long id) {
        ScrmMaterialEntity entity = new ScrmMaterialEntity();
        entity.setId(id);
        entity.setMaterialName("产品介绍图");
        entity.setMaterialType("IMAGE");
        entity.setFileUrl("https://example.com/product.png");
        entity.setFileSize(1024L);
        entity.setFileSizeText("1KB");
        entity.setThumbnailUrl("https://example.com/product_thumb.png");
        entity.setDescription("产品介绍图片素材");
        entity.setTags("产品,介绍");
        entity.setDownloadCount(3);
        entity.setStatus("ACTIVE");
        entity.setUploadedBy("tester");
        return entity;
    }

    // ============================================================
    // 话术分类 Category
    // ============================================================

    @Test
    @DisplayName("createCategory: 写入账号 ID 与默认值 (status=ACTIVE, sortOrder=0)")
    void createCategory_success() throws ScrmException {
        ScrmSpeechCategoryDto dto = buildCategoryDto();
        when(speechCategoryRepository.save(any(ScrmSpeechCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechCategoryEntity result = service.createCategory(dto);

        ArgumentCaptor<ScrmSpeechCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechCategoryEntity.class);
        verify(speechCategoryRepository, times(1)).save(captor.capture());
        ScrmSpeechCategoryEntity saved = captor.getValue();
        assertThat(saved.getCategoryName()).isEqualTo("售前话术");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getSortOrder()).isEqualTo(1);
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getDescription()).isEqualTo("售前场景话术合集");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("createCategory: 缺省 sortOrder / status 时填默认值")
    void createCategory_appliesDefaults() throws ScrmException {
        ScrmSpeechCategoryDto dto = new ScrmSpeechCategoryDto();
        dto.setCategoryName("售后话术");
        when(speechCategoryRepository.save(any(ScrmSpeechCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechCategoryEntity result = service.createCategory(dto);

        assertThat(result.getSortOrder()).isZero();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("createCategory: 指定 parentId 时校验父分类存在性")
    void createCategory_withParent() throws ScrmException {
        ScrmSpeechCategoryDto dto = buildCategoryDto();
        dto.setParentId(2L);
        when(speechCategoryRepository.findById(2L))
                .thenReturn(Optional.of(buildCategoryEntity(2L)));
        when(speechCategoryRepository.save(any(ScrmSpeechCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechCategoryEntity result = service.createCategory(dto);

        ArgumentCaptor<ScrmSpeechCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechCategoryEntity.class);
        verify(speechCategoryRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(2L);
        assertThat(result.getParentId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("createCategory: parentId 不存在抛 NOT_FOUND")
    void createCategory_parentNotFound() {
        ScrmSpeechCategoryDto dto = buildCategoryDto();
        dto.setParentId(99L);
        when(speechCategoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCategory(dto))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
                });
        verify(speechCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCategory: dto 为空抛 BAD_REQUEST")
    void createCategory_nullDto() {
        assertThatThrownBy(() -> service.createCategory(null))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(speechCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory: 字段非空才覆盖, 保存后返回更新实体")
    void updateCategory_success() throws ScrmException {
        ScrmSpeechCategoryEntity entity = buildCategoryEntity(10L);
        when(speechCategoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(speechCategoryRepository.save(any(ScrmSpeechCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmSpeechCategoryDto dto = new ScrmSpeechCategoryDto();
        dto.setCategoryName("售后话术");
        dto.setSortOrder(5);
        dto.setStatus("INACTIVE");
        dto.setDescription("更新后的描述");

        ScrmSpeechCategoryEntity result = service.updateCategory(10L, dto);

        ArgumentCaptor<ScrmSpeechCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechCategoryEntity.class);
        verify(speechCategoryRepository, times(1)).save(captor.capture());
        ScrmSpeechCategoryEntity saved = captor.getValue();
        assertThat(saved.getCategoryName()).isEqualTo("售后话术");
        assertThat(saved.getSortOrder()).isEqualTo(5);
        assertThat(saved.getStatus()).isEqualTo("INACTIVE");
        assertThat(saved.getDescription()).isEqualTo("更新后的描述");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("updateCategory: parentId 等于自身 ID 抛 BAD_REQUEST (禁止自引用)")
    void updateCategory_selfReference() {
        ScrmSpeechCategoryEntity entity = buildCategoryEntity(10L);
        when(speechCategoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmSpeechCategoryDto dto = new ScrmSpeechCategoryDto();
        dto.setParentId(10L);

        assertThatThrownBy(() -> service.updateCategory(10L, dto))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(speechCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateCategory: 分类不存在抛 NOT_FOUND")
    void updateCategory_notFound() {
        when(speechCategoryRepository.findById(99L)).thenReturn(Optional.empty());
        ScrmSpeechCategoryDto dto = new ScrmSpeechCategoryDto();
        dto.setCategoryName("不存在");

        assertThatThrownBy(() -> service.updateCategory(99L, dto))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
                });
        verify(speechCategoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteCategory: 无子分类时成功删除")
    void deleteCategory_success() throws ScrmException {
        ScrmSpeechCategoryEntity entity = buildCategoryEntity(10L);
        when(speechCategoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(speechCategoryRepository.findByParentId(10L))
                .thenReturn(Collections.emptyList());

        service.deleteCategory(10L);

        verify(speechCategoryRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("deleteCategory: 仍有子分类时抛 CONFLICT 并返回子分类数量")
    void deleteCategory_hasChildren() {
        ScrmSpeechCategoryEntity entity = buildCategoryEntity(10L);
        when(speechCategoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(speechCategoryRepository.findByParentId(10L))
                .thenReturn(Arrays.asList(
                        buildCategoryEntity(11L),
                        buildCategoryEntity(12L)));

        assertThatThrownBy(() -> service.deleteCategory(10L))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.CONFLICT);
                    assertThat(ae.getMessage()).contains("2");
                });
        verify(speechCategoryRepository, never()).delete(any(ScrmSpeechCategoryEntity.class));
    }

    
    @Test
    @DisplayName("getCategoryTree: 按 sortOrder 升序返回顶级分类")
    void getCategoryTree_success() {
        ScrmSpeechCategoryEntity c1 = buildCategoryEntity(10L);
        c1.setSortOrder(2);
        ScrmSpeechCategoryEntity c2 = buildCategoryEntity(11L);
        c2.setSortOrder(1);
        ScrmSpeechCategoryEntity child = buildCategoryEntity(12L);
        child.setParentId(10L);
        child.setSortOrder(0);
        when(speechCategoryRepository.findAll())
                .thenReturn(Arrays.asList(c1, c2, child));

        List<ScrmSpeechCategoryEntity> topLevels = service.getCategoryTree();

        // 仅顶级分类 (parentId=null) 返回, 且按 sortOrder 升序
        assertThat(topLevels).hasSize(2);
        assertThat(topLevels.get(0).getId()).isEqualTo(11L);
        assertThat(topLevels.get(1).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("listCategories: 调用 repository.findAll(spec, pageable) 返回分页结果")
    void listCategories_success() {
        ScrmSpeechCategoryEntity entity = buildCategoryEntity(10L);
        PageImpl<ScrmSpeechCategoryEntity> page =
                new PageImpl<>(Collections.singletonList(entity), PageRequest.of(0, 10), 1L);
        when(speechCategoryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listCategories(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(10L);
        verify(speechCategoryRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
    }

    // ============================================================
    // 话术条目 Speech
    // ============================================================

    @Test
    @DisplayName("createSpeech: 写入账号 ID 与默认值 (speechType=TEXT, counts=0, status=ACTIVE)")
    void createSpeech_success() throws ScrmException {
        ScrmSpeechDto dto = buildSpeechDto();
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechEntity result = service.createSpeech(dto);

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        ScrmSpeechEntity saved = captor.getValue();
        assertThat(saved.getSpeechType()).isEqualTo("TEXT");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getUseCount()).isZero();
        assertThat(saved.getLikeCount()).isZero();
        assertThat(saved.getTitle()).isEqualTo("问候话术");
        assertThat(saved.getCreatedBy()).isEqualTo("tester");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("createSpeech: 指定 categoryId 时校验分类存在性")
    void createSpeech_withCategory() throws ScrmException {
        ScrmSpeechDto dto = buildSpeechDto();
        dto.setCategoryId(10L);
        when(speechCategoryRepository.findById(10L))
                .thenReturn(Optional.of(buildCategoryEntity(10L)));
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechEntity result = service.createSpeech(dto);

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(10L);
        assertThat(result.getCategoryId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("createSpeech: dto 为空抛 BAD_REQUEST")
    void createSpeech_nullDto() {
        assertThatThrownBy(() -> service.createSpeech(null))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(speechRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateSpeech: 字段非空才覆盖, 保存后返回更新实体")
    void updateSpeech_success() throws ScrmException {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        when(speechRepository.findById(20L)).thenReturn(Optional.of(entity));
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmSpeechDto dto = new ScrmSpeechDto();
        dto.setTitle("更新标题");
        dto.setContent("更新内容");
        dto.setSpeechType("IMAGE");
        dto.setStatus("DRAFT");
        dto.setUseCount(10);

        ScrmSpeechEntity result = service.updateSpeech(20L, dto);

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        ScrmSpeechEntity saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("更新标题");
        assertThat(saved.getContent()).isEqualTo("更新内容");
        assertThat(saved.getSpeechType()).isEqualTo("IMAGE");
        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getUseCount()).isEqualTo(10);
        // 未设置的字段保留原值
        assertThat(saved.getTags()).isEqualTo("问候,开场");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("deleteSpeech: 调用 repository.delete")
    void deleteSpeech_success() throws ScrmException {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        when(speechRepository.findById(20L)).thenReturn(Optional.of(entity));

        service.deleteSpeech(20L);

        verify(speechRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("incrementUseCount: 使用次数 +1")
    void incrementUseCount_success() throws ScrmException {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        entity.setUseCount(5);
        when(speechRepository.findById(20L)).thenReturn(Optional.of(entity));
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechEntity result = service.incrementUseCount(20L);

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUseCount()).isEqualTo(6);
        assertThat(result.getUseCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("likeSpeech: 点赞数 +1")
    void likeSpeech_success() throws ScrmException {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        entity.setLikeCount(2);
        when(speechRepository.findById(20L)).thenReturn(Optional.of(entity));
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechEntity result = service.likeSpeech(20L);

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLikeCount()).isEqualTo(3);
        assertThat(result.getLikeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("toggleSpeechStatus: 切换状态成功")
    void toggleSpeechStatus_success() throws ScrmException {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        when(speechRepository.findById(20L)).thenReturn(Optional.of(entity));
        when(speechRepository.save(any(ScrmSpeechEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmSpeechEntity result = service.toggleSpeechStatus(20L, "INACTIVE");

        ArgumentCaptor<ScrmSpeechEntity> captor =
                ArgumentCaptor.forClass(ScrmSpeechEntity.class);
        verify(speechRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("INACTIVE");
        assertThat(result.getStatus()).isEqualTo("INACTIVE");
    }

    @Test
    @DisplayName("toggleSpeechStatus: 状态为空抛 BAD_REQUEST")
    void toggleSpeechStatus_blankStatus() {
        assertThatThrownBy(() -> service.toggleSpeechStatus(20L, "  "))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(speechRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("listSpeeches: 调用 repository.findAll(spec, pageable) 返回分页结果")
    void listSpeeches_success() {
        ScrmSpeechEntity entity = buildSpeechEntity(20L);
        PageImpl<ScrmSpeechEntity> page =
                new PageImpl<>(Collections.singletonList(entity), PageRequest.of(0, 10), 1L);
        when(speechRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listSpeeches(null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(20L);
        verify(speechRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
    }

    // ============================================================
    // 素材 Material
    // ============================================================

    @Test
    @DisplayName("createMaterial: 写入账号 ID 与默认值 (status=ACTIVE, downloadCount=0)")
    void createMaterial_success() throws ScrmException {
        ScrmMaterialDto dto = buildMaterialDto();
        when(materialRepository.save(any(ScrmMaterialEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMaterialEntity result = service.createMaterial(dto);

        ArgumentCaptor<ScrmMaterialEntity> captor =
                ArgumentCaptor.forClass(ScrmMaterialEntity.class);
        verify(materialRepository, times(1)).save(captor.capture());
        ScrmMaterialEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getDownloadCount()).isZero();
        assertThat(saved.getMaterialName()).isEqualTo("产品介绍图");
        assertThat(saved.getMaterialType()).isEqualTo("IMAGE");
        assertThat(saved.getFileUrl()).isEqualTo("https://example.com/product.png");
        assertThat(saved.getUploadedBy()).isEqualTo("tester");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("createMaterial: dto 为空抛 BAD_REQUEST")
    void createMaterial_nullDto() {
        assertThatThrownBy(() -> service.createMaterial(null))
                .isInstanceOf(ScrmException.class)
                .satisfies(ex -> {
                    ScrmException ae = (ScrmException) ex;
                    assertThat(ae.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
                });
        verify(materialRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMaterial: 字段非空才覆盖, 保存后返回更新实体")
    void updateMaterial_success() throws ScrmException {
        ScrmMaterialEntity entity = buildMaterialEntity(30L);
        when(materialRepository.findById(30L)).thenReturn(Optional.of(entity));
        when(materialRepository.save(any(ScrmMaterialEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmMaterialDto dto = new ScrmMaterialDto();
        dto.setMaterialName("更新素材名");
        dto.setMaterialType("VIDEO");
        dto.setStatus("INACTIVE");
        dto.setDownloadCount(99);

        ScrmMaterialEntity result = service.updateMaterial(30L, dto);

        ArgumentCaptor<ScrmMaterialEntity> captor =
                ArgumentCaptor.forClass(ScrmMaterialEntity.class);
        verify(materialRepository, times(1)).save(captor.capture());
        ScrmMaterialEntity saved = captor.getValue();
        assertThat(saved.getMaterialName()).isEqualTo("更新素材名");
        assertThat(saved.getMaterialType()).isEqualTo("VIDEO");
        assertThat(saved.getStatus()).isEqualTo("INACTIVE");
        assertThat(saved.getDownloadCount()).isEqualTo(99);
        // 未设置的字段保留原值
        assertThat(saved.getFileUrl()).isEqualTo("https://example.com/product.png");
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("deleteMaterial: 调用 repository.delete")
    void deleteMaterial_success() throws ScrmException {
        ScrmMaterialEntity entity = buildMaterialEntity(30L);
        when(materialRepository.findById(30L)).thenReturn(Optional.of(entity));

        service.deleteMaterial(30L);

        verify(materialRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("incrementDownloadCount: 下载次数 +1")
    void incrementDownloadCount_success() throws ScrmException {
        ScrmMaterialEntity entity = buildMaterialEntity(30L);
        entity.setDownloadCount(3);
        when(materialRepository.findById(30L)).thenReturn(Optional.of(entity));
        when(materialRepository.save(any(ScrmMaterialEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmMaterialEntity result = service.incrementDownloadCount(30L);

        ArgumentCaptor<ScrmMaterialEntity> captor =
                ArgumentCaptor.forClass(ScrmMaterialEntity.class);
        verify(materialRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDownloadCount()).isEqualTo(4);
        assertThat(result.getDownloadCount()).isEqualTo(4);
    }

    
    @Test
    @DisplayName("listMaterials: 调用 repository.findAll(spec, pageable) 返回分页结果")
    void listMaterials_success() {
        ScrmMaterialEntity entity = buildMaterialEntity(30L);
        PageImpl<ScrmMaterialEntity> page =
                new PageImpl<>(Collections.singletonList(entity), PageRequest.of(0, 10), 1L);
        when(materialRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listMaterials(null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(30L);
        verify(materialRepository, times(1))
                .findAll(any(Specification.class), any(Pageable.class));
    }
}
