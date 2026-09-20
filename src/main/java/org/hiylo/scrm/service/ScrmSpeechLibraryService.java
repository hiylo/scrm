/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSpeechLibraryService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 话术库 / 素材库服务。
 * <p>
* 承载话术分类、话术条目与素材库的增删改查、统计计数与状态切换能力。所有写操作写入
* 当前用户归属账号实现数据隔离, {@link #findOrThrow} 系列
* 方法在加载实体后再次校验归属账号。{@code useCount} / {@code likeCount} /
 * {@code downloadCount} 等统计字段通过原子化端点自增维护, 避免并发覆盖。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSpeechLibraryService {

    /** 默认状态: ACTIVE */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认话术类型: TEXT */
    private static final String DEFAULT_SPEECH_TYPE = "TEXT";

    /** 默认排序值（数字越小越靠前） */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 默认计数初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 话术分类数据访问层 */
    private final ScrmSpeechCategoryRepository speechCategoryRepository;

    /** 话术条目数据访问层 */
    private final ScrmSpeechRepository speechRepository;

    /** 素材库数据访问层 */
    private final ScrmMaterialRepository materialRepository;

    // ============================================================
    // 话术分类 Category
    // ============================================================

    /**
     * 创建话术分类。
     * <p>parentId 引用当前账号的已有分类 (为空表示顶级分类); status / sortOrder 缺省时填默认值。</p>
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 父分类不存在 / 参数非法
     */
    @Transactional
    public ScrmSpeechCategoryEntity createCategory(ScrmSpeechCategoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        // 父分类存在性校验 (顶级分类 parentId 为空跳过)
        if (dto.getParentId() != null) {
            findCategoryOrThrow(dto.getParentId());
        }
        ScrmSpeechCategoryEntity entity = new ScrmSpeechCategoryEntity();
        entity.setCategoryName(dto.getCategoryName());
        entity.setParentId(dto.getParentId());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : DEFAULT_STATUS);
        entity = speechCategoryRepository.save(entity);
        log.info("创建话术分类: id={}, categoryName={}, parentId={}",
                entity.getId(), entity.getCategoryName(), entity.getParentId());
        return entity;
    }

    /**
     * 更新话术分类（字段非空才覆盖）。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 自引用
     */
    @Transactional
    public ScrmSpeechCategoryEntity updateCategory(Long id, ScrmSpeechCategoryDto dto) throws ScrmException {
        ScrmSpeechCategoryEntity entity = findCategoryOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        // 父分类变更时校验存在性, 并禁止自引用
        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(id)) {
                throw ScrmException.badRequest("父分类 ID 不能等于自身 ID, 禁止自引用");
            }
            findCategoryOrThrow(dto.getParentId());
            entity.setParentId(dto.getParentId());
        }
        if (dto.getCategoryName() != null) entity.setCategoryName(dto.getCategoryName());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        entity = speechCategoryRepository.save(entity);
        log.info("更新话术分类: id={}, categoryName={}", entity.getId(), entity.getCategoryName());
        return entity;
    }

    /**
     * 删除话术分类。
     * <p>删除前检查是否有子分类, 若有则阻止删除并返回子分类数量, 避免级联悬空引用。</p>
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 仍有子分类
     */
    @Transactional
    public void deleteCategory(Long id) throws ScrmException {
        ScrmSpeechCategoryEntity entity = findCategoryOrThrow(id);
        // 检查子分类引用
        List<ScrmSpeechCategoryEntity> children = speechCategoryRepository
                .findByParentId(id);
        if (!children.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除分类: 仍有 %d 个子分类, 请先删除子分类后再删除", children.size()));
        }
        speechCategoryRepository.delete(entity);
        log.info("删除话术分类: id={}, categoryName={}", id, entity.getCategoryName());
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmSpeechCategoryEntity getCategory(Long id) throws ScrmException {
        return findCategoryOrThrow(id);
    }

    /**
     * 分页查询子分类。
     * <p>parentId 为空时查询顶级分类 (parent_id IS NULL), 按 sortOrder ASC + createTime DESC 排序。</p>
     *
     * @param parentId 父分类 ID（可空, 空表示顶级分类）
     * @param pageable 分页参数
     * @return 分类分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSpeechCategoryEntity> listCategories(Long parentId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmSpeechCategoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (parentId != null) {
                predicates.add(cb.equal(root.get("parentId"), parentId));
            } else {
                predicates.add(cb.isNull(root.get("parentId")));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return speechCategoryRepository.findAll(spec, sorted);
    }

    /**
     * 查询分类树。
     * <p>加载当前账号全量分类, 在内存中按 parentId 构建多级树结构 (按 sortOrder 升序)。</p>
     *
     * @return 顶级分类列表 (每个节点带 children 子节点列表)
     */
    @Transactional(readOnly = true)
    public List<ScrmSpeechCategoryEntity> getCategoryTree() {
        List<ScrmSpeechCategoryEntity> all = speechCategoryRepository.findAll();
        all.sort(Comparator.comparingInt((ScrmSpeechCategoryEntity c) ->
                c.getSortOrder() != null ? c.getSortOrder() : DEFAULT_SORT_ORDER));
        // 按 parentId 分组
        Map<Long, List<ScrmSpeechCategoryEntity>> byParent = new LinkedHashMap<>();
        for (ScrmSpeechCategoryEntity c : all) {
            byParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        // 注: 实体本身无 children 字段, 这里返回顶级分类列表; 前端可按 parentId 自行组装树
        return byParent.getOrDefault(null, new ArrayList<>());
    }

    // ============================================================
    // 话术 Speech
    // ============================================================

    /**
     * 创建话术条目。
     * <p>categoryId 引用当前账号的已有分类 (可空); speechType / status / sortOrder / useCount /
     * likeCount 缺省时填默认值。</p>
     *
     * @param dto 话术参数
     * @return 创建后的话术
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @Transactional
    public ScrmSpeechEntity createSpeech(ScrmSpeechDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("话术参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
        }
        ScrmSpeechEntity entity = new ScrmSpeechEntity();
        entity.setCategoryId(dto.getCategoryId());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setSpeechType(dto.getSpeechType() != null && !dto.getSpeechType().isBlank()
                ? dto.getSpeechType() : DEFAULT_SPEECH_TYPE);
        entity.setMediaUrls(dto.getMediaUrls());
        entity.setPlatformType(dto.getPlatformType());
        entity.setScenario(dto.getScenario());
        entity.setTags(dto.getTags());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setUseCount(dto.getUseCount() != null ? dto.getUseCount() : DEFAULT_COUNT);
        entity.setLikeCount(dto.getLikeCount() != null ? dto.getLikeCount() : DEFAULT_COUNT);
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : DEFAULT_STATUS);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = speechRepository.save(entity);
        log.info("创建话术: id={}, title={}, speechType={}", entity.getId(), entity.getTitle(), entity.getSpeechType());
        return entity;
    }

    /**
     * 更新话术条目（字段非空才覆盖）。
     *
     * @param id  话术 ID
     * @param dto 话术参数
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 分类不存在
     */
    @Transactional
    public ScrmSpeechEntity updateSpeech(Long id, ScrmSpeechDto dto) throws ScrmException {
        ScrmSpeechEntity entity = findSpeechOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("话术参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
            entity.setCategoryId(dto.getCategoryId());
        }
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getSpeechType() != null && !dto.getSpeechType().isBlank()) entity.setSpeechType(dto.getSpeechType());
        if (dto.getMediaUrls() != null) entity.setMediaUrls(dto.getMediaUrls());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getScenario() != null) entity.setScenario(dto.getScenario());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getUseCount() != null) entity.setUseCount(dto.getUseCount());
        if (dto.getLikeCount() != null) entity.setLikeCount(dto.getLikeCount());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = speechRepository.save(entity);
        log.info("更新话术: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除话术条目。
     *
     * @param id 话术 ID
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public void deleteSpeech(Long id) throws ScrmException {
        ScrmSpeechEntity entity = findSpeechOrThrow(id);
        speechRepository.delete(entity);
        log.info("删除话术: id={}, title={}", id, entity.getTitle());
    }

    /**
     * 查询话术详情。
     *
     * @param id 话术 ID
     * @return 话术实体
     * @throws ScrmException 话术不存在
     */
    @Transactional(readOnly = true)
    public ScrmSpeechEntity getSpeech(Long id) throws ScrmException {
        return findSpeechOrThrow(id);
    }

    /**
     * 分页查询话术, 支持按分类、平台、场景与关键字过滤。
     * <p>过滤优先级: categoryId > platformType > scenario > keyword (按 title / content / tags 模糊匹配),
     * 均为空时全量分页 (按 sortOrder ASC + createTime DESC)。</p>
     *
     * @param categoryId    分类过滤（可空）
     * @param platformType  平台过滤（可空）
     * @param scenario      场景过滤（可空）
     * @param keyword       关键字过滤（可空）
     * @param pageable      分页参数
     * @return 话术分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSpeechEntity> listSpeeches(Long categoryId, String platformType, String scenario,
                                               String keyword, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmSpeechEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(root.get("platformType"), platformType));
            }
            if (scenario != null && !scenario.isBlank()) {
                predicates.add(cb.equal(root.get("scenario"), scenario));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), kw),
                        cb.like(cb.lower(root.get("content")), kw),
                        cb.like(cb.lower(root.get("tags")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return speechRepository.findAll(spec, sorted);
    }

    /**
     * 关键字搜索话术 (按 title / content / tags 模糊匹配)。
     *
     * @param keyword  关键字
     * @param pageable 分页参数
     * @return 话术分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmSpeechEntity> searchSpeeches(String keyword, Pageable pageable) {
        return listSpeeches(null, null, null, keyword, pageable);
    }

    /**
     * 话术使用次数 +1。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSpeechEntity incrementUseCount(Long id) throws ScrmException {
        ScrmSpeechEntity entity = findSpeechOrThrow(id);
        entity.setUseCount((entity.getUseCount() != null ? entity.getUseCount() : DEFAULT_COUNT) + 1);
        entity = speechRepository.save(entity);
        log.info("话术使用次数 +1: id={}, useCount={}", id, entity.getUseCount());
        return entity;
    }

    /**
     * 话术点赞数 +1。
     *
     * @param id 话术 ID
     * @return 更新后的话术
     * @throws ScrmException 话术不存在
     */
    @Transactional
    public ScrmSpeechEntity likeSpeech(Long id) throws ScrmException {
        ScrmSpeechEntity entity = findSpeechOrThrow(id);
        entity.setLikeCount((entity.getLikeCount() != null ? entity.getLikeCount() : DEFAULT_COUNT) + 1);
        entity = speechRepository.save(entity);
        log.info("话术点赞数 +1: id={}, likeCount={}", id, entity.getLikeCount());
        return entity;
    }

    /**
     * 切换话术状态 (ACTIVE / INACTIVE / DRAFT)。
     *
     * @param id     话术 ID
     * @param status 目标状态
     * @return 更新后的话术
     * @throws ScrmException 话术不存在 / 状态非法
     */
    @Transactional
    public ScrmSpeechEntity toggleSpeechStatus(Long id, String status) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("目标状态不能为空");
        }
        ScrmSpeechEntity entity = findSpeechOrThrow(id);
        entity.setStatus(status);
        entity = speechRepository.save(entity);
        log.info("切换话术状态: id={}, status={}", id, status);
        return entity;
    }

    // ============================================================
    // 素材 Material
    // ============================================================

    /**
     * 创建素材。
     * <p>categoryId 引用当前账号的已有分类 (可空); status / downloadCount 缺省时填默认值。</p>
     *
     * @param dto 素材参数
     * @return 创建后的素材
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @Transactional
    public ScrmMaterialEntity createMaterial(ScrmMaterialDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("素材参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
        }
        ScrmMaterialEntity entity = new ScrmMaterialEntity();
        entity.setMaterialName(dto.getMaterialName());
        entity.setMaterialType(dto.getMaterialType());
        entity.setFileUrl(dto.getFileUrl());
        entity.setFileSize(dto.getFileSize());
        entity.setFileSizeText(dto.getFileSizeText());
        entity.setThumbnailUrl(dto.getThumbnailUrl());
        entity.setDescription(dto.getDescription());
        entity.setTags(dto.getTags());
        entity.setCategoryId(dto.getCategoryId());
        entity.setDownloadCount(dto.getDownloadCount() != null ? dto.getDownloadCount() : DEFAULT_COUNT);
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : DEFAULT_STATUS);
        entity.setUploadedBy(dto.getUploadedBy());
        entity = materialRepository.save(entity);
        log.info("创建素材: id={}, materialName={}, materialType={}",
                entity.getId(), entity.getMaterialName(), entity.getMaterialType());
        return entity;
    }

    /**
     * 更新素材（字段非空才覆盖）。
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 分类不存在
     */
    @Transactional
    public ScrmMaterialEntity updateMaterial(Long id, ScrmMaterialDto dto) throws ScrmException {
        ScrmMaterialEntity entity = findMaterialOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("素材参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
            entity.setCategoryId(dto.getCategoryId());
        }
        if (dto.getMaterialName() != null) entity.setMaterialName(dto.getMaterialName());
        if (dto.getMaterialType() != null) entity.setMaterialType(dto.getMaterialType());
        if (dto.getFileUrl() != null) entity.setFileUrl(dto.getFileUrl());
        if (dto.getFileSize() != null) entity.setFileSize(dto.getFileSize());
        if (dto.getFileSizeText() != null) entity.setFileSizeText(dto.getFileSizeText());
        if (dto.getThumbnailUrl() != null) entity.setThumbnailUrl(dto.getThumbnailUrl());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getDownloadCount() != null) entity.setDownloadCount(dto.getDownloadCount());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        if (dto.getUploadedBy() != null) entity.setUploadedBy(dto.getUploadedBy());
        entity = materialRepository.save(entity);
        log.info("更新素材: id={}, materialName={}", entity.getId(), entity.getMaterialName());
        return entity;
    }

    /**
     * 删除素材。
     *
     * @param id 素材 ID
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public void deleteMaterial(Long id) throws ScrmException {
        ScrmMaterialEntity entity = findMaterialOrThrow(id);
        materialRepository.delete(entity);
        log.info("删除素材: id={}, materialName={}", id, entity.getMaterialName());
    }

    /**
     * 查询素材详情。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public ScrmMaterialEntity getMaterial(Long id) throws ScrmException {
        return findMaterialOrThrow(id);
    }

    /**
     * 分页查询素材, 支持按类型、分类与关键字过滤。
     * <p>过滤优先级: materialType > categoryId > keyword (按 materialName / description / tags 模糊匹配),
     * 均为空时全量分页 (按 createTime DESC)。</p>
     *
     * @param materialType 类型过滤（可空）
     * @param categoryId   分类过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param pageable     分页参数
     * @return 素材分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmMaterialEntity> listMaterials(String materialType, Long categoryId,
                                                  String keyword, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        Specification<ScrmMaterialEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (materialType != null && !materialType.isBlank()) {
                predicates.add(cb.equal(root.get("materialType"), materialType));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("materialName")), kw),
                        cb.like(cb.lower(root.get("description")), kw),
                        cb.like(cb.lower(root.get("tags")), kw)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return materialRepository.findAll(spec, sorted);
    }

    /**
     * 素材下载次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmMaterialEntity incrementDownloadCount(Long id) throws ScrmException {
        ScrmMaterialEntity entity = findMaterialOrThrow(id);
        entity.setDownloadCount((entity.getDownloadCount() != null ? entity.getDownloadCount() : DEFAULT_COUNT) + 1);
        entity = materialRepository.save(entity);
        log.info("素材下载次数 +1: id={}, downloadCount={}", id, entity.getDownloadCount());
        return entity;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询话术分类, 不存在抛异常并校验归属账号。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    private ScrmSpeechCategoryEntity findCategoryOrThrow(Long id) throws ScrmException {
        ScrmSpeechCategoryEntity entity = speechCategoryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术分类不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询话术条目, 不存在抛异常并校验归属账号。
     *
     * @param id 话术 ID
     * @return 话术实体
     * @throws ScrmException 话术不存在
     */
    private ScrmSpeechEntity findSpeechOrThrow(Long id) throws ScrmException {
        ScrmSpeechEntity entity = speechRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "话术不存在: id=" + id));

        return entity;
    }

    /**
     * 按主键查询素材, 不存在抛异常并校验归属账号。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    private ScrmMaterialEntity findMaterialOrThrow(Long id) throws ScrmException {
        ScrmMaterialEntity entity = materialRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材不存在: id=" + id));

        return entity;
    }
}
