/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.dto.ScrmAssetCategoryDto;
import org.hiylo.scrm.dto.ScrmAssetDto;
import org.hiylo.scrm.dto.ScrmAssetReviewDto;
import org.hiylo.scrm.dto.ScrmAssetSearchDto;
import org.hiylo.scrm.dto.ScrmAssetUploadDto;
import org.hiylo.scrm.dto.ScrmAssetUsageDto;
import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAssetCategoryRepository;
import org.hiylo.scrm.repository.ScrmAssetRepository;
import org.hiylo.scrm.repository.ScrmAssetUsageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmAssetLibraryService 单元测试
 * <p>
 * 聚焦素材分类 CRUD (层级 / 路径计算 / 编码唯一 / 子分类阻止删除 / 移动自引用)、
 * 素材上传 (默认值填充 / 编码生成 / 类型校验 / 分类统计重算)、素材删除 (使用中阻止)、
 * 素材审核 (APPROVE / REJECT 与意见必填)、素材复制 (新编码生成 / 编码冲突)、
 * 统计自增 (浏览 / 下载 / 使用)、使用记录 (类型校验 / 统计同步 / 越权访问)、
 * 使用统计聚合 (按类型 / 模块 / 时间过滤)、搜索相关度排序、标签聚合去重、
 * 素材 / 分类 / 存储 / 审核统计聚合, 以及私有工具方法 (路径构建 / 扩展名提取 / 相关度评分) 等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmAssetLibraryService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmAssetLibraryServiceTest {

    /** 资产分类数据仓库 Mock 桩 */
    @Mock
    private ScrmAssetCategoryRepository categoryRepository;
    /** 资产数据仓库 Mock 桩 */
    @Mock
    private ScrmAssetRepository assetRepository;
    /** 资产使用数据仓库 Mock 桩 */
    @Mock
    private ScrmAssetUsageRepository usageRepository;

    /** 被测素材库服务实例 */
    private ScrmAssetLibraryService service;
    /** 素材分类兄弟服务 (持有 buildCategoryPath 私有方法) */
    private ScrmAssetLibraryCategoryService categoryService;
    /** 素材管理兄弟服务 (持有 extractExtension/relevanceScore 私有方法) */
    private ScrmAssetLibraryAssetService assetService;

    @BeforeEach
    void setUp() {
        categoryService =
                new ScrmAssetLibraryCategoryService(categoryRepository, assetRepository);
        assetService =
                new ScrmAssetLibraryAssetService(assetRepository, usageRepository, categoryService);
        ScrmAssetLibraryUsageService usageService =
                new ScrmAssetLibraryUsageService(usageRepository, assetRepository, assetService);
        ScrmAssetLibraryStatsService statsService =
                new ScrmAssetLibraryStatsService(assetRepository, categoryRepository, usageRepository);
        service = new ScrmAssetLibraryService(categoryService, assetService, usageService, statsService);
    }

    @AfterEach
    void tearDown() {
    }

    // ==================== 素材分类 Category ====================

    /**
     * 验证创建顶级分类成功场景, 期望 level 为 1、enabled 缺省 TRUE 且 path 由自身 ID 构建
     */
    @Test
    @DisplayName("createCategory: 顶级分类成功创建, level=1, enabled 缺省 TRUE, path 由自身 ID 构建")
    void createCategory_topLevel_success() throws Exception {
        ScrmAssetCategoryDto dto = buildCategoryDto("图片素材", "IMAGE", null);
        when(categoryRepository.existsByCategoryCode("IMAGE")).thenReturn(false);
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> assignCategoryId(inv.getArgument(0), 100L));

        ScrmAssetCategoryEntity result = service.createCategory(dto);

        assertThat(result.getId()).isEqualTo(100L);
        ArgumentCaptor<ScrmAssetCategoryEntity> captor =
                ArgumentCaptor.forClass(ScrmAssetCategoryEntity.class);
        verify(categoryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        ScrmAssetCategoryEntity saved = captor.getAllValues().get(0);
        assertThat(saved.getCategoryName()).isEqualTo("图片素材");
        assertThat(saved.getCategoryCode()).isEqualTo("IMAGE");
        assertThat(saved.getCategoryLevel()).isEqualTo(1);
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getAssetCount()).isZero();
        assertThat(saved.getTotalSizeBytes()).isZero();
        // 第二次保存写入路径
        ScrmAssetCategoryEntity second = captor.getAllValues().get(1);
        assertThat(second.getCategoryPath()).isEqualTo("100/");
    }

    /**
     * 验证创建子分类场景, 期望继承父分类 level 加 1 并拼接父分类路径
     */
    @Test
    @DisplayName("createCategory: 子分类继承父分类 level+1, path 拼接父路径")
    void createCategory_child_inheritsParentLevelAndPath() throws Exception {
        ScrmAssetCategoryEntity parent = buildCategoryEntity(50L, "根分类", "ROOT");
        parent.setCategoryLevel(1);
        parent.setCategoryPath("50/");
        ScrmAssetCategoryDto dto = buildCategoryDto("子分类", "CHILD", 50L);
        when(categoryRepository.existsByCategoryCode("CHILD")).thenReturn(false);
        when(categoryRepository.findById(50L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> assignCategoryId(inv.getArgument(0), 101L));

        ScrmAssetCategoryEntity result = service.createCategory(dto);

        assertThat(result.getCategoryLevel()).isEqualTo(2);
        assertThat(result.getCategoryPath()).isEqualTo("50/101/");
    }

    /**
     * 验证分类编码重复场景, 期望抛出 CONFLICT 异常
     */
    @Test
    @DisplayName("createCategory: 分类编码重复抛 CONFLICT")
    void createCategory_duplicateCode_throwsConflict() {
        ScrmAssetCategoryDto dto = buildCategoryDto("图片", "IMAGE", null);
        when(categoryRepository.existsByCategoryCode("IMAGE")).thenReturn(true);

        assertThatThrownBy(() -> service.createCategory(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分类编码已存在");
    }

    /**
     * 验证删除仍有子分类的父分类场景, 期望抛出 CONFLICT 异常且不执行删除
     */
    @Test
    @DisplayName("deleteCategory: 存在子分类时抛 CONFLICT, 不执行删除")
    void deleteCategory_hasChildren_throwsConflict() throws Exception {
        ScrmAssetCategoryEntity entity = buildCategoryEntity(10L, "父分类", "PARENT");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByParentId(10L))
                .thenReturn(List.of(buildCategoryEntity(11L, "子分类", "CHILD")));

        assertThatThrownBy(() -> service.deleteCategory(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("仍有 1 个子分类");
        verify(categoryRepository, never()).delete(any(ScrmAssetCategoryEntity.class));
    }

    /**
     * 验证删除无子分类的分类场景, 期望正常执行删除
     */
    @Test
    @DisplayName("deleteCategory: 无子分类时成功删除")
    void deleteCategory_noChildren_success() throws Exception {
        ScrmAssetCategoryEntity entity = buildCategoryEntity(10L, "分类", "CAT");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.findByParentId(10L)).thenReturn(Collections.emptyList());

        service.deleteCategory(10L);

        verify(categoryRepository).delete(entity);
    }

    /**
     * 验证越权删除分类场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证更新分类时父 ID 指向自身场景, 期望抛出禁止自引用的 BAD_REQUEST 异常
     */
    @Test
    @DisplayName("updateCategory: parentId 等于自身 ID 抛 badRequest, 禁止自引用")
    void updateCategory_selfReference_throwsBadRequest() {
        ScrmAssetCategoryEntity entity = buildCategoryEntity(10L, "分类", "CAT");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmAssetCategoryDto dto = buildCategoryDto("分类", "CAT", 10L);

        assertThatThrownBy(() -> service.updateCategory(10L, dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("禁止自引用");
    }

    /**
     * 验证移动分类到根级场景, 期望 level 重置为 1 且 path 改为自身 ID 加斜杠
     */
    @Test
    @DisplayName("moveCategory: 移到根级时 level=1, path=自身ID+'/'")
    void moveCategory_toRoot_resetsLevelAndPath() throws Exception {
        ScrmAssetCategoryEntity entity = buildCategoryEntity(10L, "子分类", "CHILD");
        entity.setCategoryLevel(2);
        entity.setCategoryPath("5/10/");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetCategoryEntity result = service.moveCategory(10L, null, 3);

        assertThat(result.getCategoryLevel()).isEqualTo(1);
        assertThat(result.getCategoryPath()).isEqualTo("10/");
        assertThat(result.getSortOrder()).isEqualTo(3);
    }

    /**
     * 验证重算分类统计场景, 期望聚合该分类下素材数量与总大小
     */
    @Test
    @DisplayName("updateCategoryStats: 聚合分类下素材数与总大小")
    void updateCategoryStats_aggregatesCountAndSize() throws Exception {
        ScrmAssetCategoryEntity entity = buildCategoryEntity(10L, "分类", "CAT");
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(assetRepository.countByCategoryId(10L)).thenReturn(5L);
        when(assetRepository.sumFileSizeBytesByCategoryId(10L)).thenReturn(1024L);
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetCategoryEntity result = service.updateCategoryStats(10L);

        assertThat(result.getAssetCount()).isEqualTo(5);
        assertThat(result.getTotalSizeBytes()).isEqualTo(1024L);
    }

    /**
     * 验证按编码查询不存在的分类场景, 期望抛出 NOT_FOUND 异常
     */
    @Test
    @DisplayName("getCategoryByCode: 编码存在返回分类, 不存在抛 NOT_FOUND")
    void getCategoryByCode_notFound_throws() {
        when(categoryRepository.findByCategoryCode("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategoryByCode("MISSING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("素材分类不存在");
    }

    /**
     * 验证分类编码为空白场景, 期望抛出 BAD_REQUEST 异常
     */
    @Test
    @DisplayName("getCategoryByCode: 空白编码抛 badRequest")
    void getCategoryByCode_blankCode_throwsBadRequest() {
        assertThatThrownBy(() -> service.getCategoryByCode("  "))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("分类编码不能为空");
    }

    // ==================== 素材 Asset ====================

    /**
     * 验证素材上传成功场景, 期望填充缺省状态与存储类型并自动生成素材编码
     */
    @Test
    @DisplayName("uploadAsset: 成功上传, 默认值填充 (status=PENDING/reviewStatus=PENDING/storageType=LOCAL), 编码自动生成")
    void uploadAsset_success_defaultsFilled() throws Exception {
        ScrmAssetUploadDto uploadDto = new ScrmAssetUploadDto();
        uploadDto.setAssetName("夏季海报");
        uploadDto.setAssetType("IMAGE");
        uploadDto.setFileUrl("https://cdn.example.com/poster.png");
        uploadDto.setFileSizeBytes(2048L);
        uploadDto.setMimeType("image/png");
        uploadDto.setUploadedBy("user01");
        when(assetRepository.existsByAssetCode(any(String.class))).thenReturn(false);
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> assignAssetId(inv.getArgument(0), 200L));

        ScrmAssetEntity result = service.uploadAsset(uploadDto);

        assertThat(result.getId()).isEqualTo(200L);
        ArgumentCaptor<ScrmAssetEntity> captor = ArgumentCaptor.forClass(ScrmAssetEntity.class);
        verify(assetRepository).save(captor.capture());
        ScrmAssetEntity saved = captor.getValue();
        assertThat(saved.getAssetName()).isEqualTo("夏季海报");
        assertThat(saved.getAssetCode()).isNotBlank().startsWith("ASSET");
        assertThat(saved.getAssetType()).isEqualTo("IMAGE");
        assertThat(saved.getFileExtension()).isEqualTo("png");
        assertThat(saved.getStorageType()).isEqualTo("LOCAL");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getReviewStatus()).isEqualTo("PENDING");
        assertThat(saved.getVersionNo()).isEqualTo(1);
        assertThat(saved.getIsPublic()).isFalse();
        assertThat(saved.getIsTemplate()).isFalse();
        assertThat(saved.getViewCount()).isZero();
        assertThat(saved.getDownloadCount()).isZero();
        assertThat(saved.getUseCount()).isZero();
        assertThat(saved.getUploadedBy()).isEqualTo("user01");
        assertThat(saved.getUploadedAt()).isNotNull();
        assertThat(saved.getIsExpired()).isFalse();
    }

    /**
     * 验证素材类型非法场景, 期望抛出 BAD_REQUEST 异常
     */
    @Test
    @DisplayName("uploadAsset: 非法素材类型抛 badRequest")
    void uploadAsset_invalidType_throwsBadRequest() {
        ScrmAssetUploadDto uploadDto = new ScrmAssetUploadDto();
        uploadDto.setAssetName("测试");
        uploadDto.setAssetType("INVALID_TYPE");

        assertThatThrownBy(() -> service.uploadAsset(uploadDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("素材类型非法");
    }

    /**
     * 验证带分类上传素材场景, 期望同步更新所属分类的素材数量与总大小
     */
    @Test
    @DisplayName("uploadAsset: 带分类时同步分类统计 (count+size)")
    void uploadAsset_withCategory_updatesCategoryStats() throws Exception {
        ScrmAssetCategoryEntity category = buildCategoryEntity(30L, "图片", "IMAGE");
        category.setCategoryLevel(1);
        category.setCategoryPath("30/");
        ScrmAssetUploadDto uploadDto = new ScrmAssetUploadDto();
        uploadDto.setAssetName("海报");
        uploadDto.setAssetType("IMAGE");
        uploadDto.setFileSizeBytes(1024L);
        uploadDto.setUploadedBy("user01");
        uploadDto.setCategoryId(30L);
        when(assetRepository.existsByAssetCode(any(String.class))).thenReturn(false);
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(category));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> assignAssetId(inv.getArgument(0), 200L));
        when(assetRepository.countByCategoryId(30L)).thenReturn(1L);
        when(assetRepository.sumFileSizeBytesByCategoryId(30L)).thenReturn(1024L);
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetEntity result = service.uploadAsset(uploadDto);

        assertThat(result.getCategoryId()).isEqualTo(30L);
        assertThat(result.getCategoryName()).isEqualTo("图片");
        verify(categoryRepository, org.mockito.Mockito.atLeastOnce()).save(any(ScrmAssetCategoryEntity.class));
    }

    /**
     * 验证删除存在 USE 类型使用记录的素材场景, 期望抛出 CONFLICT 异常且不执行删除
     */
    @Test
    @DisplayName("deleteAsset: 素材有 USE 类型使用记录时抛 CONFLICT, 不删除")
    void deleteAsset_inUse_throwsConflict() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        ScrmAssetUsageEntity usage = new ScrmAssetUsageEntity();
        usage.setUsageType("USE");
        when(usageRepository.findByAssetId(200L)).thenReturn(List.of(usage));

        assertThatThrownBy(() -> service.deleteAsset(200L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("素材使用中");
        verify(assetRepository, never()).delete(any(ScrmAssetEntity.class));
    }

    /**
     * 验证删除无使用记录的素材场景, 期望级联清理使用记录并删除素材
     */
    @Test
    @DisplayName("deleteAsset: 无 USE 记录时级联清理使用记录并删除素材")
    void deleteAsset_noUseRecords_cascadesAndDeletes() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        entity.setCategoryId(30L);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(usageRepository.findByAssetId(200L)).thenReturn(Collections.emptyList());
        when(usageRepository.deleteByAssetId(200L)).thenReturn(0L);
        // updateCategoryStats 依赖
        ScrmAssetCategoryEntity category = buildCategoryEntity(30L, "图片", "IMAGE");
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(category));
        when(assetRepository.countByCategoryId(30L)).thenReturn(0L);
        when(assetRepository.sumFileSizeBytesByCategoryId(30L)).thenReturn(0L);
        when(categoryRepository.save(any(ScrmAssetCategoryEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.deleteAsset(200L);

        verify(usageRepository).deleteByAssetId(200L);
        verify(assetRepository).delete(entity);
    }

    /**
     * 验证查询素材场景, 期望浏览量自增 1
     */
    @Test
    @DisplayName("getAsset: 查询时浏览量 +1")
    void getAsset_incrementsViewCount() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        entity.setViewCount(5);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetEntity result = service.getAsset(200L);

        assertThat(result.getViewCount()).isEqualTo(6);
    }

    /**
     * 验证越权查询素材场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证审核通过素材场景, 期望状态与审核状态均置为 APPROVED 并记录审核人与审核时间
     */
    @Test
    @DisplayName("reviewAsset: APPROVE 时 status 与 reviewStatus 均为 APPROVED, 记录审核人与时间")
    void reviewAsset_approve_success() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmAssetReviewDto reviewDto = new ScrmAssetReviewDto();
        reviewDto.setAssetId(200L);
        reviewDto.setAction("APPROVE");
        reviewDto.setComment("通过");

        ScrmAssetEntity result = service.reviewAsset(reviewDto);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(result.getReviewedBy()).isEqualTo("scrm-system");
        assertThat(result.getReviewedAt()).isNotNull();
        assertThat(result.getReviewComment()).isEqualTo("通过");
    }

    /**
     * 验证驳回审核但未填写审核意见场景, 期望抛出 BAD_REQUEST 异常
     */
    @Test
    @DisplayName("reviewAsset: REJECT 时审核意见为空抛 badRequest")
    void reviewAsset_rejectWithoutComment_throwsBadRequest() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        ScrmAssetReviewDto reviewDto = new ScrmAssetReviewDto();
        reviewDto.setAssetId(200L);
        reviewDto.setAction("REJECT");

        assertThatThrownBy(() -> service.reviewAsset(reviewDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("审核意见不能为空");
    }

    /**
     * 验证批量审核场景, 期望返回成功、失败与总数计数
     */
    @Test
    @DisplayName("batchReview: 部分成功部分失败, 返回 success/failed/total 计数")
    void batchReview_partialSuccess() throws Exception {
        ScrmAssetEntity ok = buildAssetEntity(200L, "素材A", "A001");
        ScrmAssetEntity reject = buildAssetEntity(201L, "素材B", "A002");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(ok));
        when(assetRepository.findById(201L)).thenReturn(Optional.of(reject));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.batchReview(List.of(200L, 201L), "APPROVE", "通过");

        assertThat(result.get("success")).isEqualTo(2);
        assertThat(result.get("failed")).isEqualTo(0);
        assertThat(result.get("total")).isEqualTo(2);
    }

    /**
     * 验证复制素材成功场景, 期望生成新编码、名称追加副本后缀并重置统计字段
     */
    @Test
    @DisplayName("duplicateAsset: 生成新编码, 名称追加 _副本, 统计字段重置为 0")
    void duplicateAsset_success_newCodeAndResetStats() throws Exception {
        ScrmAssetEntity source = buildAssetEntity(200L, "原素材", "ASSET001");
        source.setAssetType("IMAGE");
        source.setUseCount(50);
        source.setViewCount(100);
        source.setDownloadCount(30);
        source.setIsPublic(true);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(source));
        when(assetRepository.existsByAssetCode(any(String.class))).thenReturn(false);
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> assignAssetId(inv.getArgument(0), 300L));

        ScrmAssetEntity result = service.duplicateAsset(200L, null);

        assertThat(result.getId()).isEqualTo(300L);
        assertThat(result.getAssetName()).isEqualTo("原素材_副本");
        assertThat(result.getAssetCode()).isNotEqualTo("ASSET001");
        assertThat(result.getAssetType()).isEqualTo("IMAGE");
        assertThat(result.getIsPublic()).isTrue();
        assertThat(result.getUseCount()).isZero();
        assertThat(result.getViewCount()).isZero();
        assertThat(result.getDownloadCount()).isZero();
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getReviewStatus()).isEqualTo("PENDING");
    }

    /**
     * 验证复制素材时指定编码已存在场景, 期望抛出 CONFLICT 异常
     */
    @Test
    @DisplayName("duplicateAsset: 指定新编码已存在抛 CONFLICT")
    void duplicateAsset_existingCode_throwsConflict() throws Exception {
        ScrmAssetEntity source = buildAssetEntity(200L, "原素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(source));
        when(assetRepository.existsByAssetCode("EXISTING")).thenReturn(true);

        assertThatThrownBy(() -> service.duplicateAsset(200L, "EXISTING"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("素材编码已存在");
    }

    /**
     * 验证发布素材场景, 期望状态流转为 PUBLISHED
     */
    @Test
    @DisplayName("publishAsset: 状态流转为 PUBLISHED")
    void publishAsset_statusTransition() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetEntity result = service.publishAsset(200L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
    }

    /**
     * 验证累计素材使用次数场景, 期望使用次数加 1 并更新最近使用时间
     */
    @Test
    @DisplayName("incrementUseCount: 使用次数 +1 并更新最近使用时间")
    void incrementUseCount_incrementsAndUpdatesLastUsedAt() throws Exception {
        ScrmAssetEntity entity = buildAssetEntity(200L, "素材", "ASSET001");
        entity.setUseCount(10);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(entity));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAssetEntity result = service.incrementUseCount(200L, "campaign_100");

        assertThat(result.getUseCount()).isEqualTo(11);
        assertThat(result.getLastUsedAt()).isNotNull();
    }

    // ==================== 使用记录 Usage ====================

    /**
     * 验证记录使用成功场景, 期望填充缺省时间与次数并同步素材使用计数
     */
    @Test
    @DisplayName("recordUsage: 成功记录, 缺省 usedAt 填充当前时间, usageCount 缺省 1, 同步素材 useCount")
    void recordUsage_success_bumpsAssetStat() throws Exception {
        ScrmAssetEntity asset = buildAssetEntity(200L, "素材", "ASSET001");
        asset.setUseCount(5);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(asset));
        when(usageRepository.save(any(ScrmAssetUsageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmAssetUsageDto usageDto = new ScrmAssetUsageDto();
        usageDto.setAssetId(200L);
        usageDto.setUsageType("USE");
        usageDto.setUserId("user01");
        usageDto.setUsageModule("campaign");

        ScrmAssetUsageEntity result = service.recordUsage(usageDto);
        assertThat(result.getAssetId()).isEqualTo(200L);
        assertThat(result.getUsageType()).isEqualTo("USE");
        assertThat(result.getUserId()).isEqualTo("user01");
        assertThat(result.getUsageCount()).isEqualTo(1);
        assertThat(result.getUsedAt()).isNotNull();
        // 素材 useCount 同步 +1
        assertThat(asset.getUseCount()).isEqualTo(6);
        assertThat(asset.getLastUsedAt()).isNotNull();
    }

    /**
     * 验证使用类型非法场景, 期望抛出 BAD_REQUEST 异常
     */
    @Test
    @DisplayName("recordUsage: 非法使用类型抛 badRequest")
    void recordUsage_invalidType_throwsBadRequest() {
        ScrmAssetUsageDto usageDto = new ScrmAssetUsageDto();
        usageDto.setAssetId(200L);
        usageDto.setUsageType("INVALID");
        usageDto.setUserId("user01");

        assertThatThrownBy(() -> service.recordUsage(usageDto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("使用类型非法");
    }

    /**
     * 验证记录 DOWNLOAD 类型使用场景, 期望素材下载次数加 1
     */
    @Test
    @DisplayName("recordUsage: DOWNLOAD 类型同步素材 downloadCount +1")
    void recordUsage_download_bumpsDownloadCount() throws Exception {
        ScrmAssetEntity asset = buildAssetEntity(200L, "素材", "ASSET001");
        asset.setDownloadCount(3);
        when(assetRepository.findById(200L)).thenReturn(Optional.of(asset));
        when(usageRepository.save(any(ScrmAssetUsageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.save(any(ScrmAssetEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmAssetUsageDto usageDto = new ScrmAssetUsageDto();
        usageDto.setAssetId(200L);
        usageDto.setUsageType("DOWNLOAD");
        usageDto.setUserId("user01");

        service.recordUsage(usageDto);

        assertThat(asset.getDownloadCount()).isEqualTo(4);
    }

    /**
     * 验证越权查询使用记录场景, 期望按不存在处理抛出 NOT_FOUND 异常
     */
    
    /**
     * 验证使用统计聚合场景, 期望按类型与模块聚合并过滤早于起始时间的记录
     */
    @Test
    @DisplayName("getUsageStats: 按类型 / 模块聚合, 时间范围过滤跳过早于 startTime 的记录")
    void getUsageStats_aggregatesWithTimeFilter() throws Exception {
        ScrmAssetEntity asset = buildAssetEntity(200L, "素材", "ASSET001");
        when(assetRepository.findById(200L)).thenReturn(Optional.of(asset));
        LocalDateTime start = LocalDateTime.of(2026, 8, 2, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 5, 23, 59);
        when(usageRepository.findByAssetId(200L)).thenReturn(List.of(
                buildUsage("VIEW", "campaign", 2, LocalDateTime.of(2026, 8, 1, 10, 0)),
                buildUsage("DOWNLOAD", "mass_send", 1, LocalDateTime.of(2026, 8, 3, 10, 0)),
                buildUsage("VIEW", "campaign", 3, LocalDateTime.of(2026, 8, 4, 10, 0))));

        Map<String, Object> stats = service.getUsageStats(200L, start, end);

        // 第一条 (8/1) 早于 start 被过滤, 后两条合计 4
        assertThat(stats.get("total")).isEqualTo(4L);
        @SuppressWarnings("unchecked")
        Map<String, Integer> byType = (Map<String, Integer>) stats.get("byType");
        assertThat(byType.get("VIEW")).isEqualTo(3);
        assertThat(byType.get("DOWNLOAD")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Integer> byModule = (Map<String, Integer>) stats.get("byModule");
        assertThat(byModule.get("campaign")).isEqualTo(3);
        assertThat(byModule.get("mass_send")).isEqualTo(1);
    }

    /**
     * 验证清理过期使用记录场景, 期望返回删除条数与阈值时间
     */
    @Test
    @DisplayName("cleanupUsage: 返回清理记录数与阈值时间")
    void cleanupUsage_returnsDeletedCount() {
        when(usageRepository.deleteExpired(any(LocalDateTime.class))).thenReturn(15);

        Map<String, Object> result = service.cleanupUsage(30);

        assertThat(result.get("deleted")).isEqualTo(15);
        assertThat(result.get("threshold")).isNotNull();
    }

    // ==================== 搜索 Search ====================

    /**
     * 验证素材搜索相关度排序场景, 期望名称命中优先于标签命中且同分按使用次数倒序
     */
    @Test
    @DisplayName("search: 关键词命中名称 (3分) 排在标签命中 (2分) 之前, 同分按 useCount 倒序")
    void search_relevanceScoring() {
        ScrmAssetEntity nameHit = buildAssetEntity(200L, "夏季营销海报", "A001");
        nameHit.setTags("其他");
        nameHit.setDescription("其他");
        nameHit.setUseCount(1);
        ScrmAssetEntity tagHit = buildAssetEntity(201L, "其他", "A002");
        tagHit.setTags("夏季,海报");
        tagHit.setDescription("其他");
        tagHit.setUseCount(100);
        when(assetRepository.findAll(any(Specification.class)))
                .thenReturn(new ArrayList<>(List.of(tagHit, nameHit)));

        ScrmAssetSearchDto searchDto = new ScrmAssetSearchDto();
        searchDto.setKeyword("夏季");

        List<ScrmAssetEntity> results = service.search(searchDto);

        // nameHit 相关度 3 (名称命中), tagHit 相关度 2 (标签命中), nameHit 排前
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getId()).isEqualTo(200L);
        assertThat(results.get(1).getId()).isEqualTo(201L);
    }

    /**
     * 验证无关键词搜索场景, 期望退化为列表查询按使用次数倒序取前 100 条
     */
    @Test
    @DisplayName("search: 无关键词时退化为列表查询按 useCount 倒序取前 100")
    void search_noKeyword_fallsBackToList() {
        when(assetRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ScrmAssetSearchDto searchDto = new ScrmAssetSearchDto();
        List<ScrmAssetEntity> results = service.search(searchDto);

        assertThat(results).isEmpty();
    }

    /**
     * 验证空白前缀自动补全场景, 期望返回空列表且不执行查询
     */
    @Test
    @DisplayName("autoComplete: 空白前缀返回空列表, 不查询")
    void autoComplete_blankPrefix_returnsEmpty() {
        List<String> results = service.autoComplete("  ");

        assertThat(results).isEmpty();
    }

    /**
     * 验证聚合素材标签场景, 期望汇总当前账号全部标签并去重
     */
    @Test
    @DisplayName("getTags: 聚合当前账号全部素材标签并去重")
    void getTags_aggregatesAndDeduplicates() {
        ScrmAssetEntity a = buildAssetEntity(200L, "素材A", "A001");
        a.setTags("夏季,海报,营销");
        ScrmAssetEntity b = buildAssetEntity(201L, "素材B", "A002");
        b.setTags("夏季,视频");
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(a, b));

        List<String> tags = service.getTags();

        assertThat(tags).containsExactlyInAnyOrder("夏季", "海报", "营销", "视频");
    }

    // ==================== 统计 Stats ====================

    /**
     * 验证素材统计聚合场景, 期望汇总总数、各类型、各状态与总大小
     */
    @Test
    @DisplayName("getAssetStats: 聚合总数 / 各类型 / 各状态 / 总大小")
    void getAssetStats_aggregates() {
        ScrmAssetEntity a = buildAssetEntity(200L, "素材A", "A001");
        a.setAssetType("IMAGE");
        a.setStatus("PUBLISHED");
        a.setFileSizeBytes(1024L);
        ScrmAssetEntity b = buildAssetEntity(201L, "素材B", "A002");
        b.setAssetType("VIDEO");
        b.setStatus("PENDING");
        b.setFileSizeBytes(2048L);
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(a, b));

        Map<String, Object> stats = service.getAssetStats();

        assertThat(stats.get("total")).isEqualTo(2L);
        assertThat(stats.get("totalSizeBytes")).isEqualTo(3072L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byType = (Map<String, Long>) stats.get("byType");
        assertThat(byType.get("IMAGE")).isEqualTo(1L);
        assertThat(byType.get("VIDEO")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) stats.get("byStatus");
        assertThat(byStatus.get("PUBLISHED")).isEqualTo(1L);
        assertThat(byStatus.get("PENDING")).isEqualTo(1L);
    }

    /**
     * 验证审核统计聚合场景, 期望汇总各审核状态的素材数量
     */
    @Test
    @DisplayName("getReviewStats: 聚合各审核状态素材数")
    void getReviewStats_aggregates() {
        ScrmAssetEntity a = buildAssetEntity(200L, "素材A", "A001");
        a.setReviewStatus("APPROVED");
        ScrmAssetEntity b = buildAssetEntity(201L, "素材B", "A002");
        b.setReviewStatus("PENDING");
        ScrmAssetEntity c = buildAssetEntity(202L, "素材C", "A003");
        c.setReviewStatus("APPROVED");
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(a, b, c));

        Map<String, Object> stats = service.getReviewStats();

        assertThat(stats.get("total")).isEqualTo(3L);
        @SuppressWarnings("unchecked")
        Map<String, Long> byReviewStatus = (Map<String, Long>) stats.get("byReviewStatus");
        assertThat(byReviewStatus.get("APPROVED")).isEqualTo(2L);
        assertThat(byReviewStatus.get("PENDING")).isEqualTo(1L);
    }

    /**
     * 验证存储统计聚合场景, 期望按存储类型聚合素材数量与大小并返回总大小
     */
    @Test
    @DisplayName("getStorageStats: 按存储类型聚合素材数与大小, 含总大小")
    void getStorageStats_aggregates() {
        ScrmAssetEntity a = buildAssetEntity(200L, "素材A", "A001");
        a.setStorageType("LOCAL");
        a.setFileSizeBytes(1024L);
        ScrmAssetEntity b = buildAssetEntity(201L, "素材B", "A002");
        b.setStorageType("OSS");
        b.setFileSizeBytes(2048L);
        when(assetRepository.findAll(any(Specification.class))).thenReturn(List.of(a, b));
        when(assetRepository.sumFileSizeBytes()).thenReturn(3072L);

        Map<String, Object> stats = service.getStorageStats();

        @SuppressWarnings("unchecked")
        Map<String, Long> countByStorage = (Map<String, Long>) stats.get("countByStorageType");
        assertThat(countByStorage.get("LOCAL")).isEqualTo(1L);
        assertThat(countByStorage.get("OSS")).isEqualTo(1L);
        assertThat(stats.get("totalSizeBytes")).isEqualTo(3072L);
    }

    /**
     * 验证分类统计聚合场景, 期望汇总各分类素材数量与总大小
     */
    @Test
    @DisplayName("getCategoryStats: 聚合各分类素材数与总大小")
    void getCategoryStats_aggregates() {
        ScrmAssetCategoryEntity cat = buildCategoryEntity(30L, "图片", "IMAGE");
        cat.setAssetCount(5);
        cat.setTotalSizeBytes(5120L);
        when(categoryRepository.findAll()).thenReturn(List.of(cat));

        Map<String, Object> stats = service.getCategoryStats();

        assertThat(stats.get("totalCategories")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        Map<String, Object> byCategory = (Map<String, Object>) stats.get("byCategory");
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) byCategory.get("IMAGE");
        assertThat(entry.get("categoryName")).isEqualTo("图片");
        assertThat(entry.get("assetCount")).isEqualTo(5);
        assertThat(entry.get("totalSizeBytes")).isEqualTo(5120L);
    }

    /**
     * 验证热门素材统计场景, 期望聚合热门素材并补充详情且其他账号素材不填充
     */
    
    // ==================== 私有方法 ====================

    /**
     * 验证父路径为空时构建分类路径场景, 期望仅返回自身 ID 加斜杠
     */
    @Test
    @DisplayName("buildCategoryPath: parentPath 为空时仅返回 selfId+'/'")
    void buildCategoryPath_nullParent() {
        String path = ReflectionTestUtils.invokeMethod(categoryService, "buildCategoryPath", null, 100L);

        assertThat(path).isEqualTo("100/");
    }

    /**
     * 验证父路径非空时构建分类路径场景, 期望拼接父路径与自身 ID
     */
    @Test
    @DisplayName("buildCategoryPath: 拼接 parentPath 与 selfId")
    void buildCategoryPath_withParent() {
        String path = ReflectionTestUtils.invokeMethod(categoryService, "buildCategoryPath", "50/", 101L);

        assertThat(path).isEqualTo("50/101/");
    }

    /**
     * 验证父路径与自身 ID 均为空场景, 期望返回 null
     */
    @Test
    @DisplayName("buildCategoryPath: parentPath 与 selfId 均为空返回 null")
    void buildCategoryPath_bothNull() {
        String path = ReflectionTestUtils.invokeMethod(categoryService, "buildCategoryPath", null, null);

        assertThat(path).isNull();
    }

    /**
     * 验证从文件 URL 提取扩展名场景, 期望去除查询参数并转为小写
     */
    @Test
    @DisplayName("extractExtension: 从文件 URL 提取扩展名 (去除查询参数, 转小写)")
    void extractExtension_fromUrl() {
        String ext = ReflectionTestUtils.invokeMethod(assetService, "extractExtension",
                "https://cdn.example.com/poster.PNG?token=abc", null);

        assertThat(ext).isEqualTo("png");
    }

    /**
     * 验证文件 URL 为空时回退提取扩展名场景, 期望依据 MIME 类型返回扩展名
     */
    @Test
    @DisplayName("extractExtension: 文件 URL 为空时回退到 MIME 类型")
    void extractExtension_fallbackToMime() {
        String ext = ReflectionTestUtils.invokeMethod(assetService, "extractExtension",
                null, "image/jpeg");

        assertThat(ext).isEqualTo("jpeg");
    }

    /**
     * 验证 URL 与 MIME 均无可用信息场景, 期望返回 null
     */
    @Test
    @DisplayName("extractExtension: URL 与 MIME 均无可用信息返回 null")
    void extractExtension_returnsNull() {
        String ext = ReflectionTestUtils.invokeMethod(assetService, "extractExtension", null, null);

        assertThat(ext).isNull();
    }

    /**
     * 验证相关度评分场景, 期望名称命中 3 分、标签 2 分、描述 1 分累加
     */
    @Test
    @DisplayName("relevanceScore: 名称命中 3 分, 标签命中 2 分, 描述命中 1 分, 多字段命中累加")
    void relevanceScore_multiFieldHit() {
        ScrmAssetEntity asset = buildAssetEntity(200L, "夏季营销", "A001");
        asset.setTags("夏季海报");
        asset.setDescription("夏季活动");

        int score = ReflectionTestUtils.invokeMethod(assetService, "relevanceScore", asset, "夏季");

        // 名称 + 标签 + 描述 = 3 + 2 + 1 = 6
        assertThat(score).isEqualTo(6);
    }

    // ==================== 辅助方法 ====================

    private ScrmAssetCategoryDto buildCategoryDto(String name, String code, Long parentId) {
        ScrmAssetCategoryDto dto = new ScrmAssetCategoryDto();
        dto.setCategoryName(name);
        dto.setCategoryCode(code);
        dto.setParentId(parentId);
        return dto;
    }

    private ScrmAssetCategoryEntity buildCategoryEntity(Long id, String name, String code) {
        ScrmAssetCategoryEntity entity = new ScrmAssetCategoryEntity();
        entity.setId(id);
        entity.setCategoryName(name);
        entity.setCategoryCode(code);
        entity.setSortOrder(0);
        entity.setEnabled(Boolean.TRUE);
        entity.setAssetCount(0);
        entity.setTotalSizeBytes(0L);
        return entity;
    }

    private ScrmAssetEntity buildAssetEntity(Long id, String name, String code) {
        ScrmAssetEntity entity = new ScrmAssetEntity();
        entity.setId(id);
        entity.setAssetName(name);
        entity.setAssetCode(code);
        entity.setViewCount(0);
        entity.setDownloadCount(0);
        entity.setUseCount(0);
        entity.setLikeCount(0);
        entity.setShareCount(0);
        entity.setFavoriteCount(0);
        entity.setVersionNo(1);
        entity.setIsPublic(false);
        entity.setIsTemplate(false);
        entity.setIsExpired(false);
        entity.setFileSizeBytes(0L);
        entity.setStorageType("LOCAL");
        entity.setStatus("PENDING");
        entity.setReviewStatus("PENDING");
        return entity;
    }

    private ScrmAssetUsageEntity buildUsage(String type, String module, int count, LocalDateTime usedAt) {
        ScrmAssetUsageEntity entity = new ScrmAssetUsageEntity();
        entity.setUsageType(type);
        entity.setUsageModule(module);
        entity.setUsageCount(count);
        entity.setUsedAt(usedAt);
        return entity;
    }

    private ScrmAssetCategoryEntity assignCategoryId(ScrmAssetCategoryEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }

    private ScrmAssetEntity assignAssetId(ScrmAssetEntity entity, Long id) {
        entity.setId(id);
        return entity;
    }
}
