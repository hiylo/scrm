/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmQuickReplyController.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmQuickReplyCategoryDto;
import org.hiylo.scrm.dto.ScrmQuickReplyDto;
import org.hiylo.scrm.entity.ScrmQuickReplyCategoryEntity;
import org.hiylo.scrm.entity.ScrmQuickReplyEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmQuickReplyService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SCRM 快捷回复控制器。
 * <p>
 * 提供快捷回复分类与回复条目的增删改查、快捷键匹配、批量导入、重排序与状态切换接口。
 * 分类管理位于 {@code /scrm/quick-reply-categories}, 回复条目管理位于 {@code /scrm/quick-replies}。
 * 个人专属回复按 {@code ownerUserId} + 账号 ID 隔离, 调用 {@code GET /shortcut/{shortcut}}
 * 时优先返回当前用户的个人专属回复, 其次团队共享回复。
 * </p>
 * <p>
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm")
@RequiredArgsConstructor
public class ScrmQuickReplyController {

    /** 快捷回复服务 */
    private final ScrmQuickReplyService quickReplyService;

    // ============================================================
    // 快捷回复分类 /scrm/quick-reply-categories
    // ============================================================

    /**
     * 创建快捷回复分类。
     *
     * @param dto 分类参数
     * @return 创建后的分类
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/quick-reply-categories")
    public OperationResponse<ScrmQuickReplyCategoryEntity> createCategory(
            @Valid @RequestBody ScrmQuickReplyCategoryDto dto) throws ScrmException {
        return OperationResponse.build(quickReplyService.createCategory(dto));
    }

    /**
     * 更新快捷回复分类。
     *
     * @param id  分类 ID
     * @param dto 分类参数
     * @return 更新后的分类
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "update")
    @PutMapping("/quick-reply-categories/{id}")
    public OperationResponse<ScrmQuickReplyCategoryEntity> updateCategory(
            @PathVariable Long id, @RequestBody ScrmQuickReplyCategoryDto dto) throws ScrmException {
        return OperationResponse.build(quickReplyService.updateCategory(id, dto));
    }

    /**
     * 删除快捷回复分类。
     *
     * @param id 分类 ID
     * @return 空响应
     * @throws ScrmException 分类不存在 / 仍有回复引用
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "delete")
    @DeleteMapping("/quick-reply-categories/{id}")
    public OperationResponse<Void> deleteCategory(@PathVariable Long id) throws ScrmException {
        quickReplyService.deleteCategory(id);
        return OperationResponse.build();
    }

    /**
     * 查询分类详情。
     *
     * @param id 分类 ID
     * @return 分类详情
     * @throws ScrmException 分类不存在
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "read")
    @GetMapping("/quick-reply-categories/{id}")
    public OperationResponse<ScrmQuickReplyCategoryEntity> getCategory(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(quickReplyService.getCategory(id));
    }

    /**
     * 分页查询分类列表, 支持按平台过滤。
     *
     * @param platformType 平台过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 分类分页结果
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "read")
    @GetMapping("/quick-reply-categories/list")
    public OperationResponse<Page<ScrmQuickReplyCategoryEntity>> listCategories(
            @RequestParam(required = false) String platformType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(quickReplyService.listCategories(platformType, pageable));
    }

    /**
     * 分类重排序。
     *
     * @param categoryIds 分类 ID 有序列表
     * @return 更新后的分类列表
     * @throws ScrmException 分类不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_quick_reply_category", action = "update")
    @PostMapping("/quick-reply-categories/reorder")
    public OperationResponse<List<ScrmQuickReplyCategoryEntity>> reorderCategories(
            @RequestBody List<Long> categoryIds) throws ScrmException {
        return OperationResponse.build(quickReplyService.reorderCategories(categoryIds));
    }

    // ============================================================
    // 快捷回复条目 /scrm/quick-replies
    // ============================================================

    /**
     * 创建快捷回复。
     *
     * @param dto 回复参数
     * @return 创建后的回复
     * @throws ScrmException 分类不存在 / shortcut 冲突 / 个人专属缺 ownerUserId
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/quick-replies")
    public OperationResponse<ScrmQuickReplyEntity> createReply(@Valid @RequestBody ScrmQuickReplyDto dto)
            throws ScrmException {
        return OperationResponse.build(quickReplyService.createReply(dto));
    }

    /**
     * 更新快捷回复。
     *
     * @param id  回复 ID
     * @param dto 回复参数
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 分类不存在 / shortcut 冲突
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "update")
    @PutMapping("/quick-replies/{id}")
    public OperationResponse<ScrmQuickReplyEntity> updateReply(@PathVariable Long id,
                                                                 @RequestBody ScrmQuickReplyDto dto)
            throws ScrmException {
        return OperationResponse.build(quickReplyService.updateReply(id, dto));
    }

    /**
     * 删除快捷回复。
     *
     * @param id 回复 ID
     * @return 空响应
     * @throws ScrmException 回复不存在
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "delete")
    @DeleteMapping("/quick-replies/{id}")
    public OperationResponse<Void> deleteReply(@PathVariable Long id) throws ScrmException {
        quickReplyService.deleteReply(id);
        return OperationResponse.build();
    }

    /**
     * 查询快捷回复详情。
     *
     * @param id 回复 ID
     * @return 回复详情
     * @throws ScrmException 回复不存在
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "read")
    @GetMapping("/quick-replies/{id}")
    public OperationResponse<ScrmQuickReplyEntity> getReply(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(quickReplyService.getReply(id));
    }

    /**
     * 分页查询快捷回复, 支持按分类、平台、场景、关键字、个人专属与归属人过滤。
     *
     * @param categoryId   分类过滤（可空）
     * @param platformType 平台过滤（可空）
     * @param scenario     场景过滤（可空）
     * @param keyword      关键字过滤（可空）
     * @param isPersonal   个人专属过滤（可空）
     * @param ownerUserId  归属人过滤（可空）
     * @param page         页码（从 0 开始, 默认 0）
     * @param size         每页大小（默认 20）
     * @return 回复分页结果
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "read")
    @GetMapping("/quick-replies/list")
    public OperationResponse<Page<ScrmQuickReplyEntity>> listReplies(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isPersonal,
            @RequestParam(required = false) String ownerUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(quickReplyService.listReplies(
                categoryId, platformType, scenario, keyword, isPersonal, ownerUserId, pageable));
    }

    /**
     * 关键字搜索快捷回复 (按标题 / 内容 / 标签模糊匹配)。
     *
     * @param keyword 关键字
     * @param page    页码（从 0 开始, 默认 0）
     * @param size    每页大小（默认 20）
     * @return 回复分页结果
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "read")
    @GetMapping("/quick-replies/search")
    public OperationResponse<Page<ScrmQuickReplyEntity>> searchReplies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return OperationResponse.build(quickReplyService.searchReplies(keyword, pageable));
    }

    /**
     * 按快捷键匹配快捷回复。
     * <p>优先返回当前用户 (X-User-Id 头) 的个人专属回复, 其次团队共享回复。</p>
     *
     * @param shortcut 快捷键 (如 "/你好")
     * @return 匹配的回复 (无匹配返回 null)
     * @throws ScrmException shortcut 为空
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "read")
    @GetMapping("/quick-replies/shortcut/{shortcut}")
    public OperationResponse<ScrmQuickReplyEntity> getByShortcut(@PathVariable String shortcut)
            throws ScrmException {
        String ownerUserId = UserContext.getUserId();
        return OperationResponse.build(quickReplyService.getByShortcut(shortcut, ownerUserId));
    }

    /**
     * 快捷回复使用次数 +1。
     *
     * @param id 回复 ID
     * @return 更新后的回复
     * @throws ScrmException 回复不存在
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "update")
    @PostMapping("/quick-replies/{id}/use")
    public OperationResponse<ScrmQuickReplyEntity> use(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(quickReplyService.incrementUseCount(id));
    }

    /**
     * 切换快捷回复状态 (ACTIVE / INACTIVE)。
     *
     * @param id   回复 ID
     * @param body 请求体, 必须包含 status 字段
     * @return 更新后的回复
     * @throws ScrmException 回复不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "update")
    @PostMapping("/quick-replies/{id}/toggle-status")
    public OperationResponse<ScrmQuickReplyEntity> toggleReplyStatus(@PathVariable Long id,
                                                                       @RequestBody Map<String, String> body)
            throws ScrmException {
        String status = body == null ? null : body.get("status");
        return OperationResponse.build(quickReplyService.toggleReplyStatus(id, status));
    }

    /**
     * 批量导入快捷回复。
     * <p>单条失败跳过, 不阻断其他回复导入; 返回成功导入的回复列表。</p>
     *
     * @param replies 回复参数列表
     * @return 成功导入的回复列表
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "create")
    @PostMapping("/quick-replies/batch-import")
    public OperationResponse<List<ScrmQuickReplyEntity>> batchImport(@RequestBody List<ScrmQuickReplyDto> replies) {
        return OperationResponse.build(quickReplyService.batchImport(replies));
    }

    /**
     * 回复重排序。
     * <p>请求体可携带 categoryId 与 replyIds, 仅更新属于当前账号且 (可选) 属于指定 categoryId 的回复。</p>
     *
     * @param body 包含 categoryId (可选) 与 replyIds (必需) 的请求体
     * @return 更新后的回复列表
     * @throws ScrmException 回复不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_quick_reply", action = "update")
    @PostMapping("/quick-replies/reorder")
    public OperationResponse<List<ScrmQuickReplyEntity>> reorderReplies(@RequestBody Map<String, Object> body)
            throws ScrmException {
        if (body == null) {
            throw ScrmException.badRequest("重排序参数不能为空");
        }
        Object categoryIdRaw = body.get("categoryId");
        Long categoryId = null;
        if (categoryIdRaw != null) {
            try {
                categoryId = Long.valueOf(String.valueOf(categoryIdRaw));
            } catch (NumberFormatException e) {
                throw ScrmException.badRequest("categoryId 格式非法: " + categoryIdRaw);
            }
        }
        Object replyIdsRaw = body.get("replyIds");
        if (!(replyIdsRaw instanceof List<?> rawList)) {
            throw ScrmException.badRequest("replyIds 必须为非空数组");
        }
        List<Long> replyIds = new java.util.ArrayList<>();
        for (Object o : rawList) {
            try {
                replyIds.add(Long.valueOf(String.valueOf(o)));
            } catch (NumberFormatException e) {
                throw ScrmException.badRequest("replyIds 中存在非法 ID: " + o);
            }
        }
        return OperationResponse.build(quickReplyService.reorderReplies(categoryId, replyIds));
    }
}
