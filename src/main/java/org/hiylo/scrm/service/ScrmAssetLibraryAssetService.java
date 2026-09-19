/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryAssetService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmAssetDto;
import org.hiylo.scrm.dto.ScrmAssetReviewDto;
import org.hiylo.scrm.dto.ScrmAssetSearchDto;
import org.hiylo.scrm.dto.ScrmAssetUploadDto;
import org.hiylo.scrm.entity.ScrmAssetCategoryEntity;
import org.hiylo.scrm.entity.ScrmAssetEntity;
import org.hiylo.scrm.entity.ScrmAssetUsageEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAssetRepository;
import org.hiylo.scrm.repository.ScrmAssetUsageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * SCRM 营销素材库 - 素材管理子域服务。
 * <p>
 * 承载素材的上传 / 更新 / 删除 / 状态流转 / 审核 / 复制 / 检索 / 自动补全 / 标签聚合
 * 与排序能力。同时托管素材编码生成、扩展名提取、相关度评分、查询条件构建等工具,
 * 以及素材合法类型集合常量, 供使用与统计兄弟类以 package 级访问复用。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAssetLibraryAssetService {

    /** 默认计数初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认业务版本号 */
    private static final int DEFAULT_VERSION_NO = 1;

    /** 默认存储类型 */
    private static final String DEFAULT_STORAGE_TYPE = "LOCAL";

    /** 默认素材状态 */
    private static final String DEFAULT_ASSET_STATUS = "PENDING";

    /** 默认审核状态 */
    private static final String DEFAULT_REVIEW_STATUS = "PENDING";

    /** 已发布状态 */
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已归档状态 */
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 审核通过 */
    private static final String REVIEW_APPROVED = "APPROVED";

    /** 审核拒绝 */
    private static final String REVIEW_REJECTED = "REJECTED";

    /** 审核动作: 通过 */
    private static final String ACTION_APPROVE = "APPROVE";

    /** 标签分隔符 */
    private static final String TAG_SEPARATOR = ",";

    /** 素材编码前缀 */
    private static final String ASSET_CODE_PREFIX = "ASSET";

    /** 时间格式 (用于素材编码生成) */
    private static final DateTimeFormatter CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 合法素材类型集合 (共享) */
    static final Set<String> VALID_ASSET_TYPES = new HashSet<>(Arrays.asList(
            "IMAGE", "VIDEO", "AUDIO", "DOCUMENT", "TEMPLATE", "INFOGRAPHIC", "LOGO", "ICON",
            "GIF", "PDF", "PRESENTATION", "SPREADSHEET", "ARCHIVE", "OTHER"));

    /** 合法存储类型集合 (共享) */
    static final Set<String> VALID_STORAGE_TYPES = new HashSet<>(Arrays.asList(
            "LOCAL", "OSS", "COS", "CDN", "S3"));

    /** 素材数据访问层 */
    private final ScrmAssetRepository assetRepository;

    /** 素材使用记录数据访问层 (删除素材时检查/清理使用记录) */
    private final ScrmAssetUsageRepository usageRepository;

    /** 素材分类子域服务 (分类查询与统计重算) */
    private final ScrmAssetLibraryCategoryService categoryService;

    /**
     * 上传素材 (创建记录→生成编码→初始化统计)。
     * <p>assetCode 由服务端自动生成 (ASSET + 时间戳 + 随机数), 保证唯一;
     * categoryId 引用当前账号的已有分类 (可空); 状态 / 审核状态 / 统计字段初始化为默认值。</p>
     *
     * @param uploadDto 上传参数
     * @return 创建后的素材
     * @throws ScrmException 参数非法 / 分类不存在
     */
    @Transactional
    public ScrmAssetEntity uploadAsset(ScrmAssetUploadDto uploadDto) throws ScrmException {
        if (uploadDto == null) {
            throw ScrmException.badRequest("上传参数不能为空");
        }
        if (uploadDto.getAssetType() != null && !VALID_ASSET_TYPES.contains(uploadDto.getAssetType())) {
            throw ScrmException.badRequest("素材类型非法: " + uploadDto.getAssetType());
        }
        ScrmAssetEntity entity = new ScrmAssetEntity();
        entity.setAssetName(uploadDto.getAssetName());
        // 生成编码 (创建记录→生成编码)
        entity.setAssetCode(generateAssetCode());
        if (uploadDto.getCategoryId() != null) {
            ScrmAssetCategoryEntity category = categoryService.findCategoryOrThrow(uploadDto.getCategoryId());
            entity.setCategoryId(category.getId());
            entity.setCategoryName(category.getCategoryName());
        }
        entity.setAssetType(uploadDto.getAssetType());
        entity.setMimeType(uploadDto.getMimeType());
        entity.setFileExtension(extractExtension(uploadDto.getFileUrl(), uploadDto.getMimeType()));
        entity.setFileSizeBytes(uploadDto.getFileSizeBytes() != null ? uploadDto.getFileSizeBytes() : 0L);
        entity.setFileUrl(uploadDto.getFileUrl());
        entity.setStorageType(DEFAULT_STORAGE_TYPE);
        entity.setDescription(uploadDto.getDescription());
        entity.setTags(uploadDto.getTags());
        // 初始化统计
        entity.setStatus(DEFAULT_ASSET_STATUS);
        entity.setReviewStatus(DEFAULT_REVIEW_STATUS);
        entity.setVersionNo(DEFAULT_VERSION_NO);
        entity.setIsPublic(Boolean.FALSE);
        entity.setIsTemplate(Boolean.FALSE);
        entity.setDownloadCount(DEFAULT_COUNT);
        entity.setViewCount(DEFAULT_COUNT);
        entity.setUseCount(DEFAULT_COUNT);
        entity.setLikeCount(DEFAULT_COUNT);
        entity.setShareCount(DEFAULT_COUNT);
        entity.setFavoriteCount(DEFAULT_COUNT);
        entity.setUploadedBy(uploadDto.getUploadedBy() != null ? uploadDto.getUploadedBy()
                : UserContext.getUserId());
        entity.setUploadedAt(LocalDateTime.now());
        entity.setIsExpired(Boolean.FALSE);
        entity = assetRepository.save(entity);
        // 分类素材数 +1
        if (entity.getCategoryId() != null) {
            categoryService.updateCategoryStats(entity.getCategoryId());
        }
        log.info("上传素材: id={}, assetCode={}, assetType={}",
                entity.getId(), entity.getAssetCode(), entity.getAssetType());
        return entity;
    }

    /**
     * 更新素材 (字段非空才覆盖)。
     * <p>素材编码不允许变更; 分类变更会同步 categoryName 并重算分类统计。</p>
     *
     * @param id  素材 ID
     * @param dto 素材参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 分类不存在
     */
    @Transactional
    public ScrmAssetEntity updateAsset(Long id, ScrmAssetDto dto) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("素材参数不能为空");
        }
        Long oldCategoryId = entity.getCategoryId();
        if (dto.getCategoryId() != null) {
            ScrmAssetCategoryEntity category = categoryService.findCategoryOrThrow(dto.getCategoryId());
            entity.setCategoryId(category.getId());
            entity.setCategoryName(category.getCategoryName());
        }
        if (dto.getAssetName() != null) entity.setAssetName(dto.getAssetName());
        if (dto.getAssetType() != null) {
            if (!VALID_ASSET_TYPES.contains(dto.getAssetType())) {
                throw ScrmException.badRequest("素材类型非法: " + dto.getAssetType());
            }
            entity.setAssetType(dto.getAssetType());
        }
        if (dto.getMimeType() != null) entity.setMimeType(dto.getMimeType());
        if (dto.getFileExtension() != null) entity.setFileExtension(dto.getFileExtension());
        if (dto.getFileSizeBytes() != null) entity.setFileSizeBytes(dto.getFileSizeBytes());
        if (dto.getFileUrl() != null) entity.setFileUrl(dto.getFileUrl());
        if (dto.getThumbnailUrl() != null) entity.setThumbnailUrl(dto.getThumbnailUrl());
        if (dto.getPreviewUrl() != null) entity.setPreviewUrl(dto.getPreviewUrl());
        if (dto.getDownloadUrl() != null) entity.setDownloadUrl(dto.getDownloadUrl());
        if (dto.getStorageType() != null) {
            if (!VALID_STORAGE_TYPES.contains(dto.getStorageType())) {
                throw ScrmException.badRequest("存储类型非法: " + dto.getStorageType());
            }
            entity.setStorageType(dto.getStorageType());
        }
        if (dto.getStoragePath() != null) entity.setStoragePath(dto.getStoragePath());
        if (dto.getStorageBucket() != null) entity.setStorageBucket(dto.getStorageBucket());
        if (dto.getChecksum() != null) entity.setChecksum(dto.getChecksum());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getKeywords() != null) entity.setKeywords(dto.getKeywords());
        if (dto.getWidth() != null) entity.setWidth(dto.getWidth());
        if (dto.getHeight() != null) entity.setHeight(dto.getHeight());
        if (dto.getDurationSeconds() != null) entity.setDurationSeconds(dto.getDurationSeconds());
        if (dto.getPageCount() != null) entity.setPageCount(dto.getPageCount());
        if (dto.getResolution() != null) entity.setResolution(dto.getResolution());
        if (dto.getBitrate() != null) entity.setBitrate(dto.getBitrate());
        if (dto.getFormat() != null) entity.setFormat(dto.getFormat());
        if (dto.getMetadata() != null) entity.setMetadata(dto.getMetadata());
        if (dto.getApplicableScenarios() != null) entity.setApplicableScenarios(dto.getApplicableScenarios());
        if (dto.getApplicableProducts() != null) entity.setApplicableProducts(dto.getApplicableProducts());
        if (dto.getApplicableChannels() != null) entity.setApplicableChannels(dto.getApplicableChannels());
        if (dto.getVersionNo() != null) entity.setVersionNo(dto.getVersionNo());
        if (dto.getIsPublic() != null) entity.setIsPublic(dto.getIsPublic());
        if (dto.getIsTemplate() != null) entity.setIsTemplate(dto.getIsTemplate());
        if (dto.getUploadedBy() != null) entity.setUploadedBy(dto.getUploadedBy());
        if (dto.getExpiryDate() != null) entity.setExpiryDate(dto.getExpiryDate());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = assetRepository.save(entity);
        // 分类变更时重算新老分类统计
        if (dto.getCategoryId() != null && !dto.getCategoryId().equals(oldCategoryId)) {
            if (oldCategoryId != null) {
                categoryService.updateCategoryStats(oldCategoryId);
            }
            categoryService.updateCategoryStats(entity.getCategoryId());
        }
        log.info("更新素材: id={}, assetCode={}", entity.getId(), entity.getAssetCode());
        return entity;
    }

    /**
     * 删除素材。
     * <p>删除前检查是否有使用记录 (USE 类型), 若有则阻止删除; 删除后级联清理使用记录并重算分类统计。</p>
     *
     * @param id 素材 ID
     * @throws ScrmException 素材不存在 / 素材使用中
     */
    @Transactional
    public void deleteAsset(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        // 检查使用中 (USE 类型使用记录)
        List<ScrmAssetUsageEntity> usages = usageRepository.findByAssetId(id);
        boolean inUse = usages.stream()
                .anyMatch(u -> "USE".equals(u.getUsageType()));
        if (inUse) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "素材使用中, 无法删除: assetCode=" + entity.getAssetCode());
        }
        usageRepository.deleteByAssetId(id);
        assetRepository.delete(entity);
        if (entity.getCategoryId() != null) {
            categoryService.updateCategoryStats(entity.getCategoryId());
        }
        log.info("删除素材: id={}, assetCode={}", id, entity.getAssetCode());
    }

    /**
     * 查询素材详情 (增加浏览量)。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity getAsset(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setViewCount((entity.getViewCount() != null ? entity.getViewCount() : DEFAULT_COUNT) + 1);
        entity = assetRepository.save(entity);
        return entity;
    }

    /**
     * 按素材编码查询素材。
     *
     * @param code 素材编码
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    @Transactional(readOnly = true)
    public ScrmAssetEntity getAssetByCode(String code) throws ScrmException {
        if (code == null || code.isBlank()) {
            throw ScrmException.badRequest("素材编码不能为空");
        }
        return assetRepository.findByAssetCode(code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材不存在: assetCode=" + code));
    }

    /**
     * 分页查询素材, 支持按分类 / 类型 / 标签 / 状态 / 关键词过滤与排序。
     *
     * @param searchDto 搜索条件
     * @param pageable  分页参数
     * @return 素材分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetEntity> listAssets(ScrmAssetSearchDto searchDto, Pageable pageable) {
        Pageable sorted = resolveSort(searchDto, pageable);
        Specification<ScrmAssetEntity> spec = buildAssetSpec(searchDto);
        return assetRepository.findAll(spec, sorted);
    }

    /**
     * 发布素材 (状态流转为 PUBLISHED)。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity publishAsset(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setStatus(STATUS_PUBLISHED);
        entity = assetRepository.save(entity);
        log.info("发布素材: id={}, assetCode={}", id, entity.getAssetCode());
        return entity;
    }

    /**
     * 归档素材 (状态流转为 ARCHIVED)。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity archiveAsset(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setStatus(STATUS_ARCHIVED);
        entity = assetRepository.save(entity);
        log.info("归档素材: id={}, assetCode={}", id, entity.getAssetCode());
        return entity;
    }

    /**
     * 审核素材。
     * <p>APPROVE 时状态流转为 APPROVED, 审核状态 APPROVED; REJECT 时状态流转为 REJECTED,
     * 审核状态 REJECTED, 审核意见必填。</p>
     *
     * @param reviewDto 审核参数
     * @return 更新后的素材
     * @throws ScrmException 素材不存在 / 审核动作非法 / 审核意见缺失
     */
    @Transactional
    public ScrmAssetEntity reviewAsset(ScrmAssetReviewDto reviewDto) throws ScrmException {
        if (reviewDto == null || reviewDto.getAssetId() == null) {
            throw ScrmException.badRequest("审核参数与素材 ID 不能为空");
        }
        ScrmAssetEntity entity = findAssetOrThrow(reviewDto.getAssetId());
        String action = reviewDto.getAction();
        LocalDateTime now = LocalDateTime.now();
        String reviewer = UserContext.getUserId() != null ? UserContext.getUserId() : "scrm-system";
        if (ACTION_APPROVE.equals(action)) {
            entity.setStatus(REVIEW_APPROVED);
            entity.setReviewStatus(REVIEW_APPROVED);
        } else {
            entity.setStatus(REVIEW_REJECTED);
            entity.setReviewStatus(REVIEW_REJECTED);
            if (reviewDto.getComment() == null || reviewDto.getComment().isBlank()) {
                throw ScrmException.badRequest("拒绝审核时审核意见不能为空");
            }
        }
        entity.setReviewedBy(reviewer);
        entity.setReviewedAt(now);
        entity.setReviewComment(reviewDto.getComment());
        entity = assetRepository.save(entity);
        log.info("审核素材: id={}, action={}, reviewer={}", entity.getId(), action, reviewer);
        return entity;
    }

    /**
     * 批量审核素材。
     *
     * @param assetIds 素材 ID 列表
     * @param action   审核动作: APPROVE / REJECT
     * @param comment  审核意见
     * @return 审核结果 (成功数 / 失败数)
     * @throws ScrmException 参数非法
     */
    @Transactional
    public Map<String, Object> batchReview(List<Long> assetIds, String action, String comment)
            throws ScrmException {
        if (assetIds == null || assetIds.isEmpty()) {
            throw ScrmException.badRequest("素材 ID 列表不能为空");
        }
        if (!ACTION_APPROVE.equals(action) && !"REJECT".equals(action)) {
            throw ScrmException.badRequest("审核动作仅支持 APPROVE / REJECT");
        }
        if ("REJECT".equals(action) && (comment == null || comment.isBlank())) {
            throw ScrmException.badRequest("拒绝审核时审核意见不能为空");
        }
        int success = 0;
        int failed = 0;
        for (Long assetId : assetIds) {
            try {
                ScrmAssetReviewDto reviewDto = new ScrmAssetReviewDto();
                reviewDto.setAssetId(assetId);
                reviewDto.setAction(action);
                reviewDto.setComment(comment);
                reviewAsset(reviewDto);
                success++;
            } catch (ScrmException e) {
                log.warn("批量审核素材失败: assetId={}, error={}", assetId, e.getMessage());
                failed++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("total", assetIds.size());
        log.info("批量审核素材: total={}, success={}, failed={}", assetIds.size(), success, failed);
        return result;
    }

    /**
     * 复制素材 (生成新编码)。
     *
     * @param id      源素材 ID
     * @param newCode 新素材编码 (可空, 为空时自动生成)
     * @return 新素材
     * @throws ScrmException 素材不存在 / 新编码已存在
     */
    @Transactional
    public ScrmAssetEntity duplicateAsset(Long id, String newCode) throws ScrmException {
        ScrmAssetEntity source = findAssetOrThrow(id);
        String code = (newCode != null && !newCode.isBlank()) ? newCode : generateAssetCode();
        if (assetRepository.existsByAssetCode(code)) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "素材编码已存在: assetCode=" + code);
        }
        ScrmAssetEntity target = new ScrmAssetEntity();
        target.setAssetName(source.getAssetName() + "_副本");
        target.setAssetCode(code);
        target.setCategoryId(source.getCategoryId());
        target.setCategoryName(source.getCategoryName());
        target.setAssetType(source.getAssetType());
        target.setMimeType(source.getMimeType());
        target.setFileExtension(source.getFileExtension());
        target.setFileSizeBytes(source.getFileSizeBytes());
        target.setFileUrl(source.getFileUrl());
        target.setThumbnailUrl(source.getThumbnailUrl());
        target.setPreviewUrl(source.getPreviewUrl());
        target.setDownloadUrl(source.getDownloadUrl());
        target.setStorageType(source.getStorageType());
        target.setStoragePath(source.getStoragePath());
        target.setStorageBucket(source.getStorageBucket());
        target.setChecksum(source.getChecksum());
        target.setDescription(source.getDescription());
        target.setTags(source.getTags());
        target.setKeywords(source.getKeywords());
        target.setWidth(source.getWidth());
        target.setHeight(source.getHeight());
        target.setDurationSeconds(source.getDurationSeconds());
        target.setPageCount(source.getPageCount());
        target.setResolution(source.getResolution());
        target.setBitrate(source.getBitrate());
        target.setFormat(source.getFormat());
        target.setMetadata(source.getMetadata());
        target.setApplicableScenarios(source.getApplicableScenarios());
        target.setApplicableProducts(source.getApplicableProducts());
        target.setApplicableChannels(source.getApplicableChannels());
        target.setStatus(DEFAULT_ASSET_STATUS);
        target.setReviewStatus(DEFAULT_REVIEW_STATUS);
        target.setVersionNo(DEFAULT_VERSION_NO);
        target.setIsPublic(source.getIsPublic());
        target.setIsTemplate(source.getIsTemplate());
        target.setDownloadCount(DEFAULT_COUNT);
        target.setViewCount(DEFAULT_COUNT);
        target.setUseCount(DEFAULT_COUNT);
        target.setLikeCount(DEFAULT_COUNT);
        target.setShareCount(DEFAULT_COUNT);
        target.setFavoriteCount(DEFAULT_COUNT);
        target.setUploadedBy(UserContext.getUserId());
        target.setUploadedAt(LocalDateTime.now());
        target.setExpiryDate(source.getExpiryDate());
        target.setIsExpired(source.getIsExpired());
        target.setCreatedBy(source.getCreatedBy());
        target = assetRepository.save(target);
        if (target.getCategoryId() != null) {
            categoryService.updateCategoryStats(target.getCategoryId());
        }
        log.info("复制素材: sourceId={}, targetId={}, newCode={}", id, target.getId(), code);
        return target;
    }

    /**
     * 按分类分页查询素材。
     *
     * @param categoryId 分类 ID
     * @param pageable   分页参数
     * @return 素材分页结果 (按上传时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetEntity> getAssetsByCategory(Long categoryId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        Specification<ScrmAssetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("categoryId"), categoryId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return assetRepository.findAll(spec, sorted);
    }

    /**
     * 按类型分页查询素材。
     *
     * @param assetType 素材类型
     * @param pageable  分页参数
     * @return 素材分页结果 (按上传时间倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetEntity> getAssetsByType(String assetType, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        Specification<ScrmAssetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("assetType"), assetType));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return assetRepository.findAll(spec, sorted);
    }

    /**
     * 按标签分页查询素材 (匹配逗号分隔标签中的任一)。
     *
     * @param tag      标签
     * @param pageable 分页参数
     * @return 素材分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetEntity> getAssetsByTag(String tag, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        ScrmAssetSearchDto searchDto = new ScrmAssetSearchDto();
        searchDto.setTags(tag);
        return assetRepository.findAll(buildAssetSpec(searchDto), sorted);
    }

    /**
     * 热门素材 (按使用次数倒序取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 素材列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAssetEntity> getPopularAssets(int limit) {
        int n = limit > 0 ? limit : 10;
        return assetRepository.findAllByOrderByUseCountDesc(
                 PageRequest.of(0, n)).getContent();
    }

    /**
     * 最新素材 (按上传时间倒序取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 素材列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAssetEntity> getRecentAssets(int limit) {
        int n = limit > 0 ? limit : 10;
        return assetRepository.findAllByOrderByUploadedAtDesc(
                 PageRequest.of(0, n)).getContent();
    }

    /**
     * 即将过期素材 (days 天内过期且未过期)。
     *
     * @param days 天数阈值
     * @return 素材列表
     */
    @Transactional(readOnly = true)
    public List<ScrmAssetEntity> getExpiringAssets(int days) {
        int d = Math.max(0, days);
        LocalDate now = LocalDate.now();
        LocalDate threshold = now.plusDays(d);
        return assetRepository.findByExpiryDateBetweenAndIsExpiredFalse(
                 now, threshold, PageRequest.of(0, 100)).getContent();
    }

    /**
     * 素材浏览次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity incrementViewCount(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setViewCount((entity.getViewCount() != null ? entity.getViewCount() : DEFAULT_COUNT) + 1);
        entity = assetRepository.save(entity);
        return entity;
    }

    /**
     * 素材下载次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity incrementDownloadCount(Long id) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setDownloadCount((entity.getDownloadCount() != null ? entity.getDownloadCount() : DEFAULT_COUNT) + 1);
        entity.setLastUsedAt(LocalDateTime.now());
        entity = assetRepository.save(entity);
        return entity;
    }

    /**
     * 素材使用次数 +1, 并更新最近使用时间。
     *
     * @param id          素材 ID
     * @param usageEntity 使用实体 (可空, 如 campaign_id)
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    @Transactional
    public ScrmAssetEntity incrementUseCount(Long id, String usageEntity) throws ScrmException {
        ScrmAssetEntity entity = findAssetOrThrow(id);
        entity.setUseCount((entity.getUseCount() != null ? entity.getUseCount() : DEFAULT_COUNT) + 1);
        entity.setLastUsedAt(LocalDateTime.now());
        entity = assetRepository.save(entity);
        log.info("素材使用次数 +1: id={}, useCount={}, usageEntity={}",
                id, entity.getUseCount(), usageEntity);
        return entity;
    }

    /**
     * 搜索素材 (关键词匹配名称 / 描述 / 标签, 排序按相关度)。
     * <p>相关度计算: 名称命中权重 3, 标签命中权重 2, 描述命中权重 1; 命中字段越多相关度越高。
     * 结果按相关度降序, 同相关度按使用次数倒序。</p>
     *
     * @param searchDto 搜索条件
     * @return 素材列表 (按相关度排序)
     */
    @Transactional(readOnly = true)
    public List<ScrmAssetEntity> search(ScrmAssetSearchDto searchDto) {
        if (searchDto == null || searchDto.getKeyword() == null || searchDto.getKeyword().isBlank()) {
            // 无关键词时退化为列表查询, 按使用次数倒序取前 100
            ScrmAssetSearchDto fallback = searchDto != null ? searchDto : new ScrmAssetSearchDto();
            return assetRepository.findAll(buildAssetSpec(fallback),
                    PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "useCount"))).getContent();
        }
        String keyword = searchDto.getKeyword().toLowerCase();
        List<ScrmAssetEntity> matches = assetRepository.findAll(buildAssetSpec(searchDto));
        // 按相关度排序: 名称命中 3 分, 标签命中 2 分, 描述命中 1 分
        matches.sort((a, b) -> {
            int scoreB = relevanceScore(b, keyword);
            int scoreA = relevanceScore(a, keyword);
            if (scoreB != scoreA) {
                return Integer.compare(scoreB, scoreA);
            }
            int useA = a.getUseCount() != null ? a.getUseCount() : 0;
            int useB = b.getUseCount() != null ? b.getUseCount() : 0;
            return Integer.compare(useB, useA);
        });
        return matches;
    }

    /**
     * 自动补全 (按素材名称前缀匹配, 取前 limit 条)。
     *
     * @param prefix 前缀
     * @return 素材名称列表
     */
    @Transactional(readOnly = true)
    public List<String> autoComplete(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return new ArrayList<>();
        }
        String lower = prefix.toLowerCase();
        Specification<ScrmAssetEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(cb.lower(root.get("assetName")), lower + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return assetRepository.findAll(spec, PageRequest.of(0, 10)).getContent().stream()
                .map(ScrmAssetEntity::getAssetName)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有标签 (聚合当前账号全部素材的标签, 去重)。
     *
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<String> getTags() {
        Specification<ScrmAssetEntity> spec = (root, query, cb) ->
                cb.and();
        Set<String> tags = new LinkedHashSet<>();
        for (ScrmAssetEntity asset : assetRepository.findAll(spec)) {
            if (asset.getTags() != null && !asset.getTags().isBlank()) {
                for (String t : asset.getTags().split(TAG_SEPARATOR)) {
                    String trimmed = t.trim();
                    if (!trimmed.isEmpty()) {
                        tags.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(tags);
    }

    /**
     * 获取所有关键词 (聚合当前账号全部素材的关键词, 去重)。
     *
     * @return 关键词列表
     */
    @Transactional(readOnly = true)
    public List<String> getKeywords() {
        Specification<ScrmAssetEntity> spec = (root, query, cb) ->
                cb.and();
        Set<String> keywords = new LinkedHashSet<>();
        for (ScrmAssetEntity asset : assetRepository.findAll(spec)) {
            if (asset.getKeywords() != null && !asset.getKeywords().isBlank()) {
                for (String k : asset.getKeywords().split(TAG_SEPARATOR)) {
                    String trimmed = k.trim();
                    if (!trimmed.isEmpty()) {
                        keywords.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(keywords);
    }

    /**
     * 多条件筛选素材 (与 listAssets 等价, 便于按 Map 入参调用)。
     *
     * @param filters  过滤条件 (categoryId / assetType / tags / status / keyword / sortBy)
     * @param pageable 分页参数
     * @return 素材分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmAssetEntity> filterAssets(Map<String, Object> filters, Pageable pageable) {
        ScrmAssetSearchDto searchDto = new ScrmAssetSearchDto();
        if (filters != null) {
            Object categoryId = filters.get("categoryId");
            if (categoryId != null) {
                searchDto.setCategoryId(Long.valueOf(String.valueOf(categoryId)));
            }
            Object assetType = filters.get("assetType");
            if (assetType != null) {
                searchDto.setAssetType(String.valueOf(assetType));
            }
            Object tags = filters.get("tags");
            if (tags != null) {
                searchDto.setTags(String.valueOf(tags));
            }
            Object status = filters.get("status");
            if (status != null) {
                searchDto.setStatus(String.valueOf(status));
            }
            Object keyword = filters.get("keyword");
            if (keyword != null) {
                searchDto.setKeyword(String.valueOf(keyword));
            }
            Object sortBy = filters.get("sortBy");
            if (sortBy != null) {
                searchDto.setSortBy(String.valueOf(sortBy));
            }
        }
        return listAssets(searchDto, pageable);
    }

    /**
     * 按主键查询素材, 不存在或越权抛异常。
     */
    ScrmAssetEntity findAssetOrThrow(Long id) throws ScrmException {
        ScrmAssetEntity entity = assetRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "素材不存在: id=" + id));

        return entity;
    }

    /**
     * 生成素材编码 (ASSET + 时间戳 + 4 位随机数, 保证唯一)。
     */
    private String generateAssetCode() {
        for (int i = 0; i < 5; i++) {
            String code = ASSET_CODE_PREFIX + LocalDateTime.now().format(CODE_FORMATTER)
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            if (!assetRepository.existsByAssetCode(code)) {
                return code;
            }
        }
        // 兜底: 用纳秒时间
        return ASSET_CODE_PREFIX + System.nanoTime();
    }

    /**
     * 从文件 URL 或 MIME 类型提取扩展名。
     */
    private String extractExtension(String fileUrl, String mimeType) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            int queryIdx = fileUrl.indexOf('?');
            String path = queryIdx > 0 ? fileUrl.substring(0, queryIdx) : fileUrl;
            int dotIdx = path.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < path.length() - 1) {
                String ext = path.substring(dotIdx + 1).toLowerCase();
                if (ext.length() <= 20) {
                    return ext;
                }
            }
        }
        if (mimeType != null && !mimeType.isBlank()) {
            int slashIdx = mimeType.indexOf('/');
            if (slashIdx > 0 && slashIdx < mimeType.length() - 1) {
                return mimeType.substring(slashIdx + 1).toLowerCase();
            }
        }
        return null;
    }

    /**
     * 计算素材与关键词的相关度评分 (名称命中 3 分, 标签命中 2 分, 描述命中 1 分)。
     */
    private int relevanceScore(ScrmAssetEntity asset, String keyword) {
        int score = 0;
        if (asset.getAssetName() != null && asset.getAssetName().toLowerCase().contains(keyword)) {
            score += 3;
        }
        if (asset.getTags() != null && asset.getTags().toLowerCase().contains(keyword)) {
            score += 2;
        }
        if (asset.getDescription() != null && asset.getDescription().toLowerCase().contains(keyword)) {
            score += 1;
        }
        return score;
    }

    /**
     * 构建素材查询条件 Specification。
     */
    private Specification<ScrmAssetEntity> buildAssetSpec(ScrmAssetSearchDto searchDto) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (searchDto != null) {
                if (searchDto.getCategoryId() != null) {
                    predicates.add(cb.equal(root.get("categoryId"), searchDto.getCategoryId()));
                }
                if (searchDto.getAssetType() != null && !searchDto.getAssetType().isBlank()) {
                    predicates.add(cb.equal(root.get("assetType"), searchDto.getAssetType()));
                }
                if (searchDto.getStatus() != null && !searchDto.getStatus().isBlank()) {
                    predicates.add(cb.equal(root.get("status"), searchDto.getStatus()));
                }
                if (searchDto.getTags() != null && !searchDto.getTags().isBlank()) {
                    // 匹配逗号分隔标签中的任一 (LIKE %tag%)
                    String[] tagArr = searchDto.getTags().split(TAG_SEPARATOR);
                    List<Predicate> tagPredicates = new ArrayList<>();
                    for (String t : tagArr) {
                        String trimmed = t.trim();
                        if (!trimmed.isEmpty()) {
                            tagPredicates.add(cb.like(root.get("tags"), "%" + trimmed + "%"));
                        }
                    }
                    if (!tagPredicates.isEmpty()) {
                        predicates.add(cb.or(tagPredicates.toArray(new Predicate[0])));
                    }
                }
                if (searchDto.getKeyword() != null && !searchDto.getKeyword().isBlank()) {
                    String kw = "%" + searchDto.getKeyword().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("assetName")), kw),
                            cb.like(cb.lower(root.get("description")), kw),
                            cb.like(cb.lower(root.get("tags")), kw)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 解析排序维度为 Pageable。
     */
    private Pageable resolveSort(ScrmAssetSearchDto searchDto, Pageable pageable) {
        String sortBy = searchDto != null ? searchDto.getSortBy() : null;
        Sort sort;
        if (sortBy == null || sortBy.isBlank() || "NEWEST".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "uploadedAt");
        } else if ("POPULAR".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "useCount");
        } else if ("DOWNLOADS".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "downloadCount");
        } else if ("VIEWS".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "viewCount");
        } else if ("SIZE".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "fileSizeBytes");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "uploadedAt");
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}