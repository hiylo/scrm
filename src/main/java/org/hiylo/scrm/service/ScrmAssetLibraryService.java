/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAssetLibraryService.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销素材库服务 (门面)。
 * <p>
 * 作为素材库模块的统一入口, 保持对外 public 方法签名不变, 实际能力按子域委托给
 * {@link ScrmAssetLibraryCategoryService} (素材分类管理)、{@link ScrmAssetLibraryAssetService}
 * (素材与搜索)、{@link ScrmAssetLibraryUsageService} (使用记录) 与
 * {@link ScrmAssetLibraryStatsService} (统计概览)。素材编码 / 分类统计 / 使用统计等
 * 能力均在各子域服务内实现。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
public class ScrmAssetLibraryService {

    /** 素材分类管理子域服务 */
    private final ScrmAssetLibraryCategoryService categoryService;

    /** 素材与搜索子域服务 */
    private final ScrmAssetLibraryAssetService assetService;

    /** 素材使用记录子域服务 */
    private final ScrmAssetLibraryUsageService usageService;

    /** 统计概览子域服务 */
    private final ScrmAssetLibraryStatsService statsService;

    // ============================================================
    // 素材分类 Category
    // ============================================================

    /**
     * 创建素材分类。
     * <p>parentId 引用当前账号的已有分类 (为空表示顶级分类); categoryCode 唯一;
     * 层级 / 排序 / 启用状态缺省时填默认值。</p>
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法 / 分类编码重复 / 父分类不存在
     */
    public ScrmAssetCategoryEntity createCategory(ScrmAssetCategoryDto dto) throws ScrmException {
        return categoryService.createCategory(dto);
    }

    /**
     * 更新素材分类 (字段非空才覆盖)。
     * <p>分类编码不允许变更; parentId 变更会重算层级与路径。</p>
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 自引用
     */
    public ScrmAssetCategoryEntity updateCategory(Long id, ScrmAssetCategoryDto dto) throws ScrmException {
        return categoryService.updateCategory(id, dto);
    }

    /**
     * 删除素材分类。
     * <p>删除前检查是否有子分类, 若有则阻止删除。</p>
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 仍有子分类
     */
    public void deleteCategory(Long id) throws ScrmException {
        categoryService.deleteCategory(id);
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    public ScrmAssetCategoryEntity getCategory(Long id) throws ScrmException {
        return categoryService.getCategory(id);
    }

    /**
     * 按分类编码查询分类。
     *
     * @param code 分类编码
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    public ScrmAssetCategoryEntity getCategoryByCode(String code) throws ScrmException {
        return categoryService.getCategoryByCode(code);
    }

    /**
     * 分页查询分类, 支持按父分类 / 启用状态 / 关键词过滤。
     *
     * @param parentId 父分类过滤 (可空)
     * @param enabled  启用状态过滤 (可空)
     * @param keyword  关键词过滤, 匹配分类名称 / 编码 / 描述 (可空)
     * @param pageable 分页参数
     * @return 分类分页结果 (按 sortOrder ASC + createTime DESC)
     */
    public Page<ScrmAssetCategoryEntity> listCategories(Long parentId, Boolean enabled,
                                                         String keyword, Pageable pageable) {
        return categoryService.listCategories(parentId, enabled, keyword, pageable);
    }

    /**
     * 查询分类树。
     * <p>加载当前账号全量分类, 在内存中按 parentId 构建多级树结构 (按 sortOrder 升序)。</p>
     *
     * @return 顶级分类列表
     */
    public List<ScrmAssetCategoryEntity> getCategoryTree() {
        return categoryService.getCategoryTree();
    }

    /**
     * 启用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmAssetCategoryEntity enableCategory(Long id) throws ScrmException {
        return categoryService.enableCategory(id);
    }

    /**
     * 禁用分类。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmAssetCategoryEntity disableCategory(Long id) throws ScrmException {
        return categoryService.disableCategory(id);
    }

    /**
     * 移动分类 (变更父分类与排序值)。
     * <p>parentId 传 null 表示移到根级; 移动后重算层级与路径。</p>
     *
     * @param id           分类 ID
     * @param newParentId  新父分类 ID (可空)
     * @param newSortOrder 新排序值 (可空)
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 父分类不存在 / 父分类为自身
     */
    public ScrmAssetCategoryEntity moveCategory(Long id, Long newParentId, Integer newSortOrder)
            throws ScrmException {
        return categoryService.moveCategory(id, newParentId, newSortOrder);
    }

    /**
     * 更新分类统计 (重新计算 assetCount 与 totalSizeBytes)。
     *
     * @param id 分类 ID
     * @return 更新后的分类
     * @throws ScrmException 分类不存在
     */
    public ScrmAssetCategoryEntity updateCategoryStats(Long id) throws ScrmException {
        return categoryService.updateCategoryStats(id);
    }

    // ============================================================
    // 素材 Asset
    // ============================================================

    /**
     * 上传素材 (创建记录→生成编码→初始化统计)。
     * <p>assetCode 由服务端自动生成 (ASSET + 时间戳 + 随机数), 保证唯一;
     * categoryId 引用当前账号的已有分类 (可空); 状态 / 审核状态 / 统计字段初始化为默认值。</p>
     *
     * @param uploadDto 上传参数
     * @return 创建后的素材
     * @throws ScrmException 参数非法 / 分类不存在
     */
    public ScrmAssetEntity uploadAsset(ScrmAssetUploadDto uploadDto) throws ScrmException {
        return assetService.uploadAsset(uploadDto);
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
    public ScrmAssetEntity updateAsset(Long id, ScrmAssetDto dto) throws ScrmException {
        return assetService.updateAsset(id, dto);
    }

    /**
     * 删除素材。
     * <p>删除前检查是否有使用记录 (USE 类型), 若有则阻止删除; 删除后级联清理使用记录并重算分类统计。</p>
     *
     * @param id 素材 ID
     * @throws ScrmException 素材不存在 / 素材使用中
     */
    public void deleteAsset(Long id) throws ScrmException {
        assetService.deleteAsset(id);
    }

    /**
     * 查询素材详情 (增加浏览量)。
     *
     * @param id 素材 ID
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity getAsset(Long id) throws ScrmException {
        return assetService.getAsset(id);
    }

    /**
     * 按素材编码查询素材。
     *
     * @param code 素材编码
     * @return 素材实体
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity getAssetByCode(String code) throws ScrmException {
        return assetService.getAssetByCode(code);
    }

    /**
     * 分页查询素材, 支持按分类 / 类型 / 标签 / 状态 / 关键词过滤与排序。
     *
     * @param searchDto 搜索条件
     * @param pageable  分页参数
     * @return 素材分页结果
     */
    public Page<ScrmAssetEntity> listAssets(ScrmAssetSearchDto searchDto, Pageable pageable) {
        return assetService.listAssets(searchDto, pageable);
    }

    /**
     * 发布素材 (状态流转为 PUBLISHED)。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity publishAsset(Long id) throws ScrmException {
        return assetService.publishAsset(id);
    }

    /**
     * 归档素材 (状态流转为 ARCHIVED)。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity archiveAsset(Long id) throws ScrmException {
        return assetService.archiveAsset(id);
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
    public ScrmAssetEntity reviewAsset(ScrmAssetReviewDto reviewDto) throws ScrmException {
        return assetService.reviewAsset(reviewDto);
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
    public Map<String, Object> batchReview(List<Long> assetIds, String action, String comment)
            throws ScrmException {
        return assetService.batchReview(assetIds, action, comment);
    }

    /**
     * 复制素材 (生成新编码)。
     *
     * @param id      源素材 ID
     * @param newCode 新素材编码 (可空, 为空时自动生成)
     * @return 新素材
     * @throws ScrmException 素材不存在 / 新编码已存在
     */
    public ScrmAssetEntity duplicateAsset(Long id, String newCode) throws ScrmException {
        return assetService.duplicateAsset(id, newCode);
    }

    /**
     * 按分类分页查询素材。
     *
     * @param categoryId 分类 ID
     * @param pageable   分页参数
     * @return 素材分页结果 (按上传时间倒序)
     */
    public Page<ScrmAssetEntity> getAssetsByCategory(Long categoryId, Pageable pageable) {
        return assetService.getAssetsByCategory(categoryId, pageable);
    }

    /**
     * 按类型分页查询素材。
     *
     * @param assetType 素材类型
     * @param pageable  分页参数
     * @return 素材分页结果 (按上传时间倒序)
     */
    public Page<ScrmAssetEntity> getAssetsByType(String assetType, Pageable pageable) {
        return assetService.getAssetsByType(assetType, pageable);
    }

    /**
     * 按标签分页查询素材 (匹配逗号分隔标签中的任一)。
     *
     * @param tag      标签
     * @param pageable 分页参数
     * @return 素材分页结果
     */
    public Page<ScrmAssetEntity> getAssetsByTag(String tag, Pageable pageable) {
        return assetService.getAssetsByTag(tag, pageable);
    }

    /**
     * 热门素材 (按使用次数倒序取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 素材列表
     */
    public List<ScrmAssetEntity> getPopularAssets(int limit) {
        return assetService.getPopularAssets(limit);
    }

    /**
     * 最新素材 (按上传时间倒序取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 素材列表
     */
    public List<ScrmAssetEntity> getRecentAssets(int limit) {
        return assetService.getRecentAssets(limit);
    }

    /**
     * 即将过期素材 (days 天内过期且未过期)。
     *
     * @param days 天数阈值
     * @return 素材列表
     */
    public List<ScrmAssetEntity> getExpiringAssets(int days) {
        return assetService.getExpiringAssets(days);
    }

    /**
     * 素材浏览次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity incrementViewCount(Long id) throws ScrmException {
        return assetService.incrementViewCount(id);
    }

    /**
     * 素材下载次数 +1。
     *
     * @param id 素材 ID
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity incrementDownloadCount(Long id) throws ScrmException {
        return assetService.incrementDownloadCount(id);
    }

    /**
     * 素材使用次数 +1, 并更新最近使用时间。
     *
     * @param id          素材 ID
     * @param usageEntity 使用实体 (可空, 如 campaign_id)
     * @return 更新后的素材
     * @throws ScrmException 素材不存在
     */
    public ScrmAssetEntity incrementUseCount(Long id, String usageEntity) throws ScrmException {
        return assetService.incrementUseCount(id, usageEntity);
    }

    // ============================================================
    // 使用记录 Usage
    // ============================================================

    /**
     * 记录素材使用。
     * <p>校验素材存在且属于当前账号; 使用类型必须合法; usedAt 缺省填充当前时间;
     * 同时更新素材对应统计字段 (viewCount / downloadCount / useCount 等)。</p>
     *
     * @param usageDto 使用记录参数
     * @return 使用记录
     * @throws ScrmException 参数非法 / 素材不存在
     */
    public ScrmAssetUsageEntity recordUsage(ScrmAssetUsageDto usageDto) throws ScrmException {
        return usageService.recordUsage(usageDto);
    }

    /**
     * 查询使用记录详情。
     *
     * @param id 使用记录 ID
     * @return 使用记录
     * @throws ScrmException 使用记录不存在
     */
    public ScrmAssetUsageEntity getUsage(Long id) throws ScrmException {
        return usageService.getUsage(id);
    }

    /**
     * 分页查询使用记录, 支持按素材 / 使用类型 / 使用模块 / 时间范围过滤。
     *
     * @param assetId     素材 ID (可空)
     * @param usageType   使用类型 (可空)
     * @param usageModule 使用模块 (可空)
     * @param startTime   起始时间 (可空)
     * @param endTime     截止时间 (可空)
     * @param pageable    分页参数
     * @return 使用记录分页结果 (按 usedAt 倒序)
     */
    public Page<ScrmAssetUsageEntity> listUsage(Long assetId, String usageType, String usageModule,
                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                 Pageable pageable) {
        return usageService.listUsage(assetId, usageType, usageModule, startTime, endTime, pageable);
    }

    /**
     * 素材使用统计 (按时间范围过滤)。
     *
     * @param assetId   素材 ID
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (总使用次数 / 各使用类型次数 / 各模块次数)
     * @throws ScrmException 素材不存在
     */
    public Map<String, Object> getUsageStats(Long assetId, LocalDateTime startTime, LocalDateTime endTime)
            throws ScrmException {
        return usageService.getUsageStats(assetId, startTime, endTime);
    }

    /**
     * 按模块统计素材使用。
     *
     * @param assetId 素材 ID
     * @return 模块 → 使用次数
     * @throws ScrmException 素材不存在
     */
    public Map<String, Object> getUsageByModule(Long assetId) throws ScrmException {
        return usageService.getUsageByModule(assetId);
    }

    /**
     * 素材使用趋势 (按天聚合最近 days 天的使用次数)。
     *
     * @param assetId 素材 ID
     * @param days    天数
     * @return 日期 → 使用次数
     * @throws ScrmException 素材不存在
     */
    public Map<String, Object> getUsageTrend(Long assetId, int days) throws ScrmException {
        return usageService.getUsageTrend(assetId, days);
    }

    /**
     * 热门素材 (按使用次数聚合, 取前 limit 条)。
     *
     * @param limit 取前 N 条
     * @return 热门素材列表
     */
    public List<Map<String, Object>> getPopularAssetsByUsage(int limit) {
        return usageService.getPopularAssetsByUsage(limit);
    }

    /**
     * 清理过期使用记录 (usedAt 早于 days 天前)。
     *
     * @param days 天数阈值
     * @return 清理的记录数
     */
    public Map<String, Object> cleanupUsage(int days) {
        return usageService.cleanupUsage(days);
    }

    // ============================================================
    // 搜索 Search
    // ============================================================

    /**
     * 搜索素材 (关键词匹配名称 / 描述 / 标签, 排序按相关度)。
     * <p>相关度计算: 名称命中权重 3, 标签命中权重 2, 描述命中权重 1; 命中字段越多相关度越高。
     * 结果按相关度降序, 同相关度按使用次数倒序。</p>
     *
     * @param searchDto 搜索条件
     * @return 素材列表 (按相关度排序)
     */
    public List<ScrmAssetEntity> search(ScrmAssetSearchDto searchDto) {
        return assetService.search(searchDto);
    }

    /**
     * 自动补全 (按素材名称前缀匹配, 取前 limit 条)。
     *
     * @param prefix 前缀
     * @return 素材名称列表
     */
    public List<String> autoComplete(String prefix) {
        return assetService.autoComplete(prefix);
    }

    /**
     * 获取所有标签 (聚合当前账号全部素材的标签, 去重)。
     *
     * @return 标签列表
     */
    public List<String> getTags() {
        return assetService.getTags();
    }

    /**
     * 获取所有关键词 (聚合当前账号全部素材的关键词, 去重)。
     *
     * @return 关键词列表
     */
    public List<String> getKeywords() {
        return assetService.getKeywords();
    }

    /**
     * 多条件筛选素材 (与 listAssets 等价, 便于按 Map 入参调用)。
     *
     * @param filters  过滤条件 (categoryId / assetType / tags / status / keyword / sortBy)
     * @param pageable 分页参数
     * @return 素材分页结果
     */
    public Page<ScrmAssetEntity> filterAssets(Map<String, Object> filters, Pageable pageable) {
        return assetService.filterAssets(filters, pageable);
    }

    // ============================================================
    // 统计 Stats
    // ============================================================

    /**
     * 素材统计 (总数 / 各类型数 / 各分类数 / 总大小)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getAssetStats() {
        return statsService.getAssetStats();
    }

    /**
     * 分类统计 (各分类素材数与总大小)。
     *
     * @return 分类 → 统计信息
     */
    public Map<String, Object> getCategoryStats() {
        return statsService.getCategoryStats();
    }

    /**
     * 存储统计 (各存储类型素材数与大小分布)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getStorageStats() {
        return statsService.getStorageStats();
    }

    /**
     * 使用统计概览 (按时间范围过滤)。
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果 (总使用次数 / 各类型次数 / 各模块次数)
     */
    public Map<String, Object> getUsageStatsOverview(LocalDateTime startTime, LocalDateTime endTime) {
        return statsService.getUsageStatsOverview(startTime, endTime);
    }

    /**
     * 审核统计 (各审核状态素材数)。
     *
     * @return 统计结果
     */
    public Map<String, Object> getReviewStats() {
        return statsService.getReviewStats();
    }

    /**
     * 趋势统计 (按天聚合最近 days 天的上传量)。
     *
     * @param days 天数
     * @return 日期 → 上传量
     */
    public Map<String, Object> getTrend(int days) {
        return statsService.getTrend(days);
    }
}