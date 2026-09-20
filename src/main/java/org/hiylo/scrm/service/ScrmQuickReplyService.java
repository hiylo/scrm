/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmQuickReplyCategoryDto;
import org.hiylo.scrm.dto.ScrmQuickReplyDto;
import org.hiylo.scrm.entity.ScrmQuickReplyCategoryEntity;
import org.hiylo.scrm.entity.ScrmQuickReplyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmQuickReplyCategoryRepository;
import org.hiylo.scrm.repository.ScrmQuickReplyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SCRM 快捷回复服务。
 * <p>
 * 承载快捷回复分类与回复条目的增删改查、快捷键匹配、批量导入、重排序与状态切换能力。
 * 所有写操作写入当前用户归属账号实现数据隔离, 个人专属回复
 * (isPersonal=TRUE) 额外按 {@code ownerUserId} 隔离。{@link #getByShortcut} 按
 * 快捷键匹配回复时优先返回个人专属回复, 其次团队共享回复。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmQuickReplyService {

    /** 默认状态: ACTIVE */
    private static final String DEFAULT_STATUS = "ACTIVE";

    /** 默认回复类型: TEXT */
    private static final String DEFAULT_REPLY_TYPE = "TEXT";

    /** 默认排序值（数字越小越靠前） */
    private static final int DEFAULT_SORT_ORDER = 0;

    /** 默认计数初值 */
    private static final int DEFAULT_COUNT = 0;

    /** 默认个人专属: FALSE (团队共享) */
    private static final boolean DEFAULT_IS_PERSONAL = false;

    /** 快捷回复分类数据访问层 */
    private final ScrmQuickReplyCategoryRepository quickReplyCategoryRepository;

    /** 快捷回复条目数据访问层 */
    private final ScrmQuickReplyRepository quickReplyRepository;

    // ============================================================
    // 快捷回复分类 Category
    // ============================================================

    /**
     * 创建快捷回复分类。
     * <p>status / sortOrder 缺省时填默认值。</p>
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmQuickReplyCategoryEntity createCategory(ScrmQuickReplyCategoryDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        ScrmQuickReplyCategoryEntity entity = new ScrmQuickReplyCategoryEntity();
        entity.setCategoryName(dto.getCategoryName());
        entity.setIcon(dto.getIcon());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setPlatformType(dto.getPlatformType());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : DEFAULT_STATUS);
        entity = quickReplyCategoryRepository.save(entity);
        log.info("创建快捷回复分类: id={}, categoryName={}", entity.getId(), entity.getCategoryName());
        return entity;
    }

    /**
     * 更新快捷回复分类（字段非空才覆盖）。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @Transactional
    public ScrmQuickReplyCategoryEntity updateCategory(Long id,
            ScrmQuickReplyCategoryDto dto) throws ScrmException {
        ScrmQuickReplyCategoryEntity entity = findCategoryOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("分类参数不能为空");
        }
        if (dto.getCategoryName() != null) entity.setCategoryName(dto.getCategoryName());
        if (dto.getIcon() != null) entity.setIcon(dto.getIcon());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        entity = quickReplyCategoryRepository.save(entity);
        log.info("更新快捷回复分类: id={}, categoryName={}", entity.getId(), entity.getCategoryName());
        return entity;
    }

    /**
     * 删除快捷回复分类。
     * <p>删除前检查是否有回复条目引用该分类, 若有则阻止删除并返回引用数量。</p>
     *
     * @param id 分类 ID
     * @throws ScrmException 分类不存在 / 仍有回复引用
     */
    @Transactional
    public void deleteCategory(Long id) throws ScrmException {
        ScrmQuickReplyCategoryEntity entity = findCategoryOrThrow(id);
        // 检查回复引用
        List<ScrmQuickReplyEntity> refs = quickReplyRepository
                .findByCategoryId(id);
        if (!refs.isEmpty()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    String.format("无法删除分类: 仍有 %d 条快捷回复引用该分类, 请先迁移或删除回复后再删除",
                            refs.size()));
        }
        quickReplyCategoryRepository.delete(entity);
        log.info("删除快捷回复分类: id={}, categoryName={}", id, entity.getCategoryName());
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    @Transactional(readOnly = true)
    public ScrmQuickReplyCategoryEntity getCategory(Long id) throws ScrmException {
        return findCategoryOrThrow(id);
    }

    /**
     * 分页查询快捷回复分类, 支持按平台过滤。
     * <p>platformType 为空时返回当前账号全部分类, 按 sortOrder ASC + createTime DESC 排序。</p>
     *
     * @param platformType 平台过滤（可空）
     * @param pageable     分页参数
     * @return 分类分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQuickReplyCategoryEntity> listCategories(String platformType, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmQuickReplyCategoryEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(root.get("platformType"), platformType));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return quickReplyCategoryRepository.findAll(spec, sorted);
    }

    /**
     * 分类重排序。
     * <p>按 {@code categoryIds} 顺序依次设置 sortOrder=0,1,2,...; 列表中不存在的分类保持原序。</p>
     *
     * @param categoryIds 分类 ID 有序列表 (按目标展示顺序)
     * @return 更新后的分类列表
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @Transactional
    public List<ScrmQuickReplyCategoryEntity> reorderCategories(List<Long> categoryIds) throws ScrmException {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw ScrmException.badRequest("分类 ID 列表不能为空");
        }
        List<ScrmQuickReplyCategoryEntity> updated = new ArrayList<>();
        for (int i = 0; i < categoryIds.size(); i++) {
            ScrmQuickReplyCategoryEntity entity = findCategoryOrThrow(categoryIds.get(i));

            entity.setSortOrder(i);
            updated.add(quickReplyCategoryRepository.save(entity));
        }
        log.info("快捷回复分类重排序: count={}", updated.size());
        return updated;
    }

    // ============================================================
    // 快捷回复 Reply
    // ============================================================

    /**
     * 创建快捷回复。
     * <p>categoryId 引用当前账号的已有分类 (可空); replyType / status / sortOrder / useCount /
     * isPersonal 缺省时填默认值。isPersonal=TRUE 时 ownerUserId 必填。shortcut 在同* 业务唯一 (空 shortcut 不参与唯一性校验)。</p>
     *
     * @param dto 回复参数
     * @return 创建后的回复
     * @throws ScrmException 分类不存在 / shortcut 冲突 / 个人专属缺 ownerUserId
     */
    @Transactional
    public ScrmQuickReplyEntity createReply(ScrmQuickReplyDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("快捷回复参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
        }
        // 个人专属回复必须指定 ownerUserId
        boolean isPersonal = dto.getIsPersonal() != null ? dto.getIsPersonal() : DEFAULT_IS_PERSONAL;
        if (isPersonal && (dto.getOwnerUserId() == null || dto.getOwnerUserId().isBlank())) {
            throw ScrmException.badRequest("个人专属回复必须指定归属人 ownerUserId");
        }
        // shortcut 业务唯一性校验 (同)
        if (dto.getShortcut() != null && !dto.getShortcut().isBlank()) {
            if (quickReplyRepository.findFirstByShortcut(dto.getShortcut()).isPresent()) {
                throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                        "快捷键已被占用: shortcut=" + dto.getShortcut());
            }
        }
        ScrmQuickReplyEntity entity = new ScrmQuickReplyEntity();
        entity.setCategoryId(dto.getCategoryId());
        entity.setTitle(dto.getTitle());
        entity.setContent(dto.getContent());
        entity.setReplyType(dto.getReplyType() != null && !dto.getReplyType().isBlank()
                ? dto.getReplyType() : DEFAULT_REPLY_TYPE);
        entity.setMediaUrls(dto.getMediaUrls());
        entity.setShortcut(dto.getShortcut());
        entity.setPlatformType(dto.getPlatformType());
        entity.setScenario(dto.getScenario());
        entity.setTags(dto.getTags());
        entity.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : DEFAULT_SORT_ORDER);
        entity.setUseCount(dto.getUseCount() != null ? dto.getUseCount() : DEFAULT_COUNT);
        entity.setIsPersonal(isPersonal);
        entity.setOwnerUserId(dto.getOwnerUserId());
        entity.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank() ? dto.getStatus() : DEFAULT_STATUS);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = quickReplyRepository.save(entity);
        log.info("创建快捷回复: id={}, title={}, isPersonal={}",
                entity.getId(), entity.getTitle(), entity.getIsPersonal());
        return entity;
    }

    /**
     * 更新快捷回复（字段非空才覆盖）。
     * <p>isPersonal=TRUE 切换时 ownerUserId 必填; shortcut 变更时校验同账号唯一性。</p>
     *
     * @param id  回复 ID
     * @param dto 回复参数
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 分类不存在 / shortcut 冲突 / 个人专属缺 ownerUserId
     */
    @Transactional
    public ScrmQuickReplyEntity updateReply(Long id, ScrmQuickReplyDto dto) throws ScrmException {
        ScrmQuickReplyEntity entity = findReplyOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("快捷回复参数不能为空");
        }
        if (dto.getCategoryId() != null) {
            findCategoryOrThrow(dto.getCategoryId());
            entity.setCategoryId(dto.getCategoryId());
        }
        // isPersonal 切换时校验 ownerUserId
        if (dto.getIsPersonal() != null) {
            if (dto.getIsPersonal() && (dto.getOwnerUserId() == null || dto.getOwnerUserId().isBlank()) && (entity.getOwnerUserId() == null || entity.getOwnerUserId().isBlank())) {
                throw ScrmException.badRequest("个人专属回复必须指定归属人 ownerUserId");
            }
            entity.setIsPersonal(dto.getIsPersonal());
        }
        if (dto.getOwnerUserId() != null) entity.setOwnerUserId(dto.getOwnerUserId());
        // shortcut 变更时校验同账号唯一性
        if (dto.getShortcut() != null && !dto.getShortcut().isBlank() && !dto.getShortcut().equals(entity.getShortcut())) {
            quickReplyRepository.findFirstByShortcut(dto.getShortcut())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                                    "快捷键已被其他回复占用: shortcut=" + dto.getShortcut());
                        }
                    });
            entity.setShortcut(dto.getShortcut());
        } else if (dto.getShortcut() != null && dto.getShortcut().isBlank()) {
            // 显式清空 shortcut
            entity.setShortcut(null);
        }
        if (dto.getTitle() != null) entity.setTitle(dto.getTitle());
        if (dto.getContent() != null) entity.setContent(dto.getContent());
        if (dto.getReplyType() != null && !dto.getReplyType().isBlank()) entity.setReplyType(dto.getReplyType());
        if (dto.getMediaUrls() != null) entity.setMediaUrls(dto.getMediaUrls());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getScenario() != null) entity.setScenario(dto.getScenario());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getSortOrder() != null) entity.setSortOrder(dto.getSortOrder());
        if (dto.getUseCount() != null) entity.setUseCount(dto.getUseCount());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) entity.setStatus(dto.getStatus());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = quickReplyRepository.save(entity);
        log.info("更新快捷回复: id={}, title={}", entity.getId(), entity.getTitle());
        return entity;
    }

    /**
     * 删除快捷回复。
     *
     * @param id 回复 ID
     * @throws ScrmException 回复不存在
     */
    @Transactional
    public void deleteReply(Long id) throws ScrmException {
        ScrmQuickReplyEntity entity = findReplyOrThrow(id);
        quickReplyRepository.delete(entity);
        log.info("删除快捷回复: id={}, title={}", id, entity.getTitle());
    }

    /**
     * 查询快捷回复详情。
     *
     * @param id 回复 ID
     * @return 回复实体
     * @throws ScrmException 回复不存在
     */
    @Transactional(readOnly = true)
    public ScrmQuickReplyEntity getReply(Long id) throws ScrmException {
        return findReplyOrThrow(id);
    }

    /**
     * 分页查询快捷回复, 支持按分类、平台、场景、关键字、个人专属与归属人过滤。
     * <p>过滤优先级: categoryId > platformType > scenario > isPersonal > ownerUserId > keyword
     * (按 title / content / tags 模糊匹配), 均为空时全量分页 (按 sortOrder ASC + createTime DESC)。</p>
     * <p>个人专属回复 (isPersonal=TRUE) 仅返回归属当前 ownerUserId 的回复; 团队共享回复
     * (isPersonal=FALSE) 全部数据可见。</p>
     *
     * @param categoryId   分类过滤（可空）
     * @param platformType 平台过滤（可空）
     * @param scenario     场景过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param isPersonal   个人专属过滤（可空: TRUE 仅个人专属, FALSE 仅团队共享, null 全部）
     * @param ownerUserId  归属人过滤（可空, isPersonal=TRUE 时按此过滤）
     * @param pageable     分页参数
     * @return 回复分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQuickReplyEntity> listReplies(Long categoryId, String platformType, String scenario,
                                                  String keyword, Boolean isPersonal, String ownerUserId,
                                                  Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.ASC, "sortOrder")
                        .and(Sort.by(Sort.Direction.DESC, "createTime")));
        Specification<ScrmQuickReplyEntity> spec = (root, query, cb) -> {
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
            if (isPersonal != null) {
                predicates.add(cb.equal(root.get("isPersonal"), isPersonal));
            }
            if (ownerUserId != null && !ownerUserId.isBlank()) {
                predicates.add(cb.equal(root.get("ownerUserId"), ownerUserId));
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
        return quickReplyRepository.findAll(spec, sorted);
    }

    /**
     * 关键字搜索快捷回复 (按 title / content / tags 模糊匹配)。
     *
     * @param keyword  关键字
     * @param pageable 分页参数
     * @return 回复分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmQuickReplyEntity> searchReplies(String keyword, Pageable pageable) {
        return listReplies(null, null, null, keyword, null, null, pageable);
    }

    /**
     * 按快捷键匹配快捷回复。
     * <p>优先返回当前 ownerUserId 的个人专属回复; 若无则返回团队共享回复 (isPersonal=FALSE)。
     * 个人专属匹配时仅返回 isPersonal=TRUE 且 ownerUserId 匹配的回复; 团队共享匹配时仅返回
     * isPersonal=FALSE 的回复。命中后由调用方通过 {@link #incrementUseCount} 自增使用次数。</p>
     *
     * @param shortcut    快捷键 (如 "/你好")
     * @param ownerUserId 当前用户 ID (用于个人专属匹配, 可空)
     * @return 匹配的回复 (无匹配返回 null)
     * @throws ScrmException shortcut 为空
     */
    @Transactional(readOnly = true)
    public ScrmQuickReplyEntity getByShortcut(String shortcut, String ownerUserId) throws ScrmException {
        if (shortcut == null || shortcut.isBlank()) {
            throw ScrmException.badRequest("快捷键不能为空");
        }
        List<ScrmQuickReplyEntity> candidates = quickReplyRepository
                .findByShortcut(shortcut);
        if (candidates.isEmpty()) {
            return null;
        }
        // 优先个人专属 (ownerUserId 匹配 + status=ACTIVE)
        if (ownerUserId != null && !ownerUserId.isBlank()) {
            for (ScrmQuickReplyEntity c : candidates) {
                if (Boolean.TRUE.equals(c.getIsPersonal()) && ownerUserId.equals(c.getOwnerUserId()) && "ACTIVE".equals(c.getStatus())) {
                    return c;
                }
            }
        }
        // 其次团队共享 (isPersonal=FALSE + status=ACTIVE)
        for (ScrmQuickReplyEntity c : candidates) {
            if (!Boolean.TRUE.equals(c.getIsPersonal()) && "ACTIVE".equals(c.getStatus())) {
                return c;
            }
        }
        return null;
    }

    /**
     * 快捷回复使用次数 +1。
     *
     * @param id 回复 ID
     * @return 更新后的回复
     * @throws ScrmException 回复不存在
     */
    @Transactional
    public ScrmQuickReplyEntity incrementUseCount(Long id) throws ScrmException {
        ScrmQuickReplyEntity entity = findReplyOrThrow(id);
        entity.setUseCount((entity.getUseCount() != null ? entity.getUseCount() : DEFAULT_COUNT) + 1);
        entity = quickReplyRepository.save(entity);
        log.info("快捷回复使用次数 +1: id={}, useCount={}", id, entity.getUseCount());
        return entity;
    }

    /**
     * 回复重排序。
     * <p>按 {@code replyIds} 顺序依次设置 sortOrder=0,1,2,...; 仅更新属于当前账号且
     * (可选) 属于指定 categoryId 的回复; 列表中不存在的回复保持原序。</p>
     *
     * @param categoryId 分类 ID（可空, 空则不按分类约束）
     * @param replyIds   回复 ID 有序列表 (按目标展示顺序)
     * @return 更新后的回复列表
     * @throws ScrmException 回复不存在 / 参数非法
     */
    @Transactional
    public List<ScrmQuickReplyEntity> reorderReplies(Long categoryId, List<Long> replyIds) throws ScrmException {
        if (replyIds == null || replyIds.isEmpty()) {
            throw ScrmException.badRequest("回复 ID 列表不能为空");
        }
        List<ScrmQuickReplyEntity> updated = new ArrayList<>();
        for (int i = 0; i < replyIds.size(); i++) {
            ScrmQuickReplyEntity entity = findReplyOrThrow(replyIds.get(i));

            if (categoryId != null && !categoryId.equals(entity.getCategoryId())) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "回复不属于指定分类: replyId=" + replyIds.get(i) + ", expected categoryId=" + categoryId
                                + ", actual categoryId=" + entity.getCategoryId());
            }
            entity.setSortOrder(i);
            updated.add(quickReplyRepository.save(entity));
        }
        log.info("快捷回复重排序: count={}, categoryId={}", updated.size(), categoryId);
        return updated;
    }

    /**
     * 切换快捷回复状态 (ACTIVE / INACTIVE)。
     *
     * @param id     回复 ID
     * @param status 目标状态
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 状态非法
     */
    @Transactional
    public ScrmQuickReplyEntity toggleReplyStatus(Long id, String status) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("目标状态不能为空");
        }
        ScrmQuickReplyEntity entity = findReplyOrThrow(id);
        entity.setStatus(status);
        entity = quickReplyRepository.save(entity);
        log.info("切换快捷回复状态: id={}, status={}", id, status);
        return entity;
    }

    /**
     * 批量导入快捷回复。
     * <p>对每条回复执行 createReply 逻辑 (含 shortcut 唯一性校验); 单条失败时跳过并记录日志,
     * 不阻断其他回复导入。返回成功导入的回复列表 (含失败的条数日志)。</p>
     *
     * @param replies 回复参数列表
     * @return 成功导入的回复列表
     */
    @Transactional
    public List<ScrmQuickReplyEntity> batchImport(List<ScrmQuickReplyDto> replies) {
        if (replies == null || replies.isEmpty()) {
            return List.of();
        }
        List<ScrmQuickReplyEntity> imported = new ArrayList<>();
        int failed = 0;
        for (ScrmQuickReplyDto dto : replies) {
            try {
                imported.add(createReply(dto));
            } catch (Exception e) {
                failed++;
                log.warn("批量导入快捷回复失败, 跳过: title={}, err={}",
                        dto == null ? null : dto.getTitle(), e.getMessage());
            }
        }
        log.info("批量导入快捷回复完成: success={}, failed={}", imported.size(), failed);
        return imported;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 按主键查询快捷回复分类, 不存在抛异常并校验归属账号。
     *
     * @param id 分类 ID
     * @return 分类实体
     * @throws ScrmException 分类不存在
     */
    private ScrmQuickReplyCategoryEntity findCategoryOrThrow(Long id) throws ScrmException {
        ScrmQuickReplyCategoryEntity entity = quickReplyCategoryRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "快捷回复分类不存在: id=" + id));

        return entity;
    }

    /**
* 按主键查询快捷回复, 不存在抛异常并校验归属账号。
* <p>个人专属回复仅 ownerUserId 匹配的调用方可访问; 团队共享回复全部数据可见。
* 此处仅做账号级隔离, 个人专属细粒度访问控制由调用方 (Controller 层注入 ownerUserId) 配合完成。</p>
     *
     * @param id 回复 ID
     * @return 回复实体
     * @throws ScrmException 回复不存在
     */
    private ScrmQuickReplyEntity findReplyOrThrow(Long id) throws ScrmException {
        ScrmQuickReplyEntity entity = quickReplyRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "快捷回复不存在: id=" + id));

        return entity;
    }
}
