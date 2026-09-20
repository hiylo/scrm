/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerController.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCustomerGroupEntity;
import org.hiylo.scrm.entity.ScrmCustomerGroupMemberEntity;
import org.hiylo.scrm.entity.ScrmCustomerLifecycleHistoryEntity;
import org.hiylo.scrm.entity.ScrmCustomerTagEntity;
import org.hiylo.scrm.entity.ScrmTagCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.repository.ScrmCustomerGroupMemberRepository;
import org.hiylo.scrm.repository.ScrmCustomerGroupRepository;
import org.hiylo.scrm.service.ScrmCustomerImportService;
import org.hiylo.scrm.service.ScrmCustomerService;
import org.hiylo.scrm.service.ScrmExportService;
import org.hiylo.scrm.service.ScrmExportService.ExportColumn;
import org.hiylo.scrm.vo.CustomerImportResultVo;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * SCRM 客户控制器
 * <p>
 * 提供客户档案维护、标签管理、分组管理与生命周期阶段切换接口。
 * 权限由 gateway-server 统一鉴权, {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/customers")
@RequiredArgsConstructor
public class ScrmCustomerController {

    /** 客户服务 */
    private final ScrmCustomerService customerService;

    /** 客户批量导入服务 (Excel / CSV) */
    private final ScrmCustomerImportService customerImportService;

    /** 数据导出服务 (Excel / CSV) */
    private final ScrmExportService exportService;

    /** 客户分组数据访问层 (Service 未提供 getGroups/deleteGroup, 直接调用) */
    private final ScrmCustomerGroupRepository groupRepository;

    /** 客户-分组关联数据访问层 */
    private final ScrmCustomerGroupMemberRepository groupMemberRepository;

    /**
     * 创建客户
     *
     * @param dto 客户参数
     * @return 创建后的客户
     */
    @RequirePermission(resource = "scrm_customer", action = "create")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping
    public OperationResponse<ScrmCustomerDto> create(@Valid @RequestBody ScrmCustomerDto dto)
            throws ScrmException {
        return OperationResponse.build(customerService.createCustomer(dto));
    }

    /**
     * 更新客户
     *
     * @param id  客户 ID
     * @param dto 客户参数
     * @return 更新后的客户
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}")
    public OperationResponse<ScrmCustomerDto> update(@PathVariable Long id,
                                                      @RequestBody ScrmCustomerDto dto)
            throws ScrmException {
        return OperationResponse.build(customerService.updateCustomer(id, dto));
    }

    /**
     * 更新客户备注
     *
     * @param id   客户 ID
     * @param body 包含 notes 字段的请求体
     * @return 更新后的客户信息
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PutMapping("/{id}/notes")
    public OperationResponse<ScrmCustomerDto> updateNotes(@PathVariable Long id,
                                                           @RequestBody java.util.Map<String, String> body)
            throws ScrmException {
        String notes = body != null ? body.get("notes") : null;
        return OperationResponse.build(customerService.updateCustomerNotes(id, notes));
    }

    /**
     * 查询客户
     *
     * @param id 客户 ID
     * @return 客户详情
     * @throws ScrmException 客户不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerService.getCustomer(id));
    }

    /**
     * 删除客户
     * <p>
     * 级联删除关联的标签与分组成员关系, 然后删除客户主记录。
     * 仅 ADMIN / OPERATOR 角色可执行此操作。
     * </p>
     *
     * @param id 客户 ID
     * @return 空响应
     * @throws ScrmException 客户不存在 / 权限不足
     */
    @RequirePermission(resource = "scrm_customer", action = "delete")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        customerService.deleteCustomer(id);
        return OperationResponse.build(null);
    }

    /**
     * 按平台类型、平台客户 UID 与归属账号 ID 查询客户
     *
     * @param platformType        平台类型
     * @param platformCustomerUid 平台客户 UID
     * @param ownerAccountId      归属账号 ID
     * @return 客户详情 (不存在返回 data=null)
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/by-platform")
    public OperationResponse<ScrmCustomerDto> getByPlatform(@RequestParam String platformType,
                                                             @RequestParam String platformCustomerUid,
                                                             @RequestParam Long ownerAccountId) {
        return OperationResponse.build(customerService.getCustomerByPlatform(
                platformType, platformCustomerUid, ownerAccountId));
    }

    /**
     * 按归属账号分页查询客户
     *
     * @param ownerAccountId 归属账号 ID
     * @param page           页码 (从 0 开始, 默认 0)
     * @param size           每页大小 (默认 20)
     * @return 客户分页结果
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/by-owner/{ownerAccountId}")
    public OperationResponse<Page<ScrmCustomerDto>> getByOwner(@PathVariable Long ownerAccountId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(customerService.getCustomersByOwner(ownerAccountId, page, size));
    }

    /**
     * 为客户打标签 (相同 tagKey 已存在则覆盖 tagValue)
     *
     * @param id       客户 ID
     * @param tagKey   标签键
     * @param tagValue 标签值
     * @return 客户标签列表
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @PostMapping("/{id}/tags")
    public OperationResponse<List<ScrmTagCustomerEntity>> addTag(@PathVariable Long id,
                                                                  @RequestParam String tagKey,
                                                                  @RequestParam(required = false) String tagValue)
            throws ScrmException {
        return OperationResponse.build(customerService.addTag(id, tagKey, tagValue));
    }

    /**
     * 删除客户标签
     *
     * @param id     客户 ID
     * @param tagKey 标签键
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @DeleteMapping("/{id}/tags/{tagKey}")
    public OperationResponse<Void> removeTag(@PathVariable Long id, @PathVariable String tagKey)
            throws ScrmException {
        customerService.removeTag(id, tagKey);
        return OperationResponse.build();
    }

    /**
     * 查询客户的所有标签
     *
     * @param id 客户 ID
     * @return 标签列表
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{id}/tags")
    public OperationResponse<List<ScrmTagCustomerEntity>> getTags(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(customerService.getTags(id));
    }

    /**
     * 批量给多个客户打标签。
     * <p>
     * 请求体携带客户 ID 列表和标签键值, 对每个客户执行 upsert 标签操作。
     * 单个客户失败不影响其他客户, 返回成功数与失败列表。
     * </p>
     *
     * @param request 批量打标签请求体
     * @return 批量操作结果
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @PostMapping("/batch-tags")
    public OperationResponse<ScrmCustomerService.BatchTagResult> batchAddTags(
            @RequestBody BatchTagRequest request) {
        if (request.customerIds() == null || request.customerIds().isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        if (request.tagKey() == null || request.tagKey().isBlank()) {
            throw ScrmException.badRequest("标签键不能为空");
        }
        return OperationResponse.build(
                customerService.batchAddTag(request.customerIds(), request.tagKey(), request.tagValue()));
    }

    /**
     * 批量打标签请求体
 * @since V1.0
     * @author Hsi Chu
     */
    public record BatchTagRequest(List<Long> customerIds, String tagKey, String tagValue) {
    }

    /**
     * 创建客户分组
     * <p>
     * 请求体携带 groupName / description / ownerAccountId, 委托
     * {@link ScrmCustomerService#createGroup} 持久化。
     * </p>
     *
     * @param request 分组创建请求体
     * @return 创建后的分组
     */
    @RequirePermission(resource = "scrm_customer_group", action = "create")
    @PostMapping("/groups")
    public OperationResponse<ScrmCustomerGroupEntity> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        return OperationResponse.build(customerService.createGroup(
                request.getGroupName(), request.getDescription(), request.getOwnerAccountId()));
    }

    /**
     * 查询客户所属分组列表
     * <p>
     * 通过客户-分组关联表查询该客户加入的所有分组。
     * </p>
     *
     * @param customerId 客户 ID
     * @return 分组列表
     */
    @RequirePermission(resource = "scrm_customer_group", action = "read")
    @GetMapping("/{customerId}/groups")
    public OperationResponse<List<ScrmCustomerGroupEntity>> getCustomerGroups(
            @PathVariable Long customerId) {
        List<Long> groupIds = groupMemberRepository.findByCustomerId(customerId).stream()
                .map(ScrmCustomerGroupMemberEntity::getGroupId)
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return OperationResponse.build(List.of());
        }
        return OperationResponse.build(groupRepository.findAllById(groupIds));
    }

    /**
     * 查询分组列表, 支持按归属账号过滤
     * <p>
     * Service 未提供 getGroups 方法, 直接调用
     * {@link ScrmCustomerGroupRepository#findByOwnerAccountId}。
     * </p>
     *
     * @param ownerAccountId 归属账号 ID (可选, 不传则返回空列表)
     * @return 分组列表
     */
    @RequirePermission(resource = "scrm_customer_group", action = "read")
    @GetMapping("/groups")
    public OperationResponse<List<ScrmCustomerGroupEntity>> listGroups(
            @RequestParam(required = false) Long ownerAccountId) {
        if (ownerAccountId == null) {
            return OperationResponse.build(List.of());
        }
        return OperationResponse.build(groupRepository.findByOwnerAccountId(ownerAccountId));
    }

    /**
     * 查询分组成员列表
     *
     * @param groupId 分组 ID
     * @return 成员关联列表
     */
    @RequirePermission(resource = "scrm_customer_group", action = "read")
    @GetMapping("/groups/{groupId}/members")
    public OperationResponse<List<ScrmCustomerGroupMemberEntity>> getGroupMembers(
            @PathVariable Long groupId) {
        return OperationResponse.build(customerService.getGroupMembers(groupId));
    }

    /**
     * 将客户加入分组
     * <p>
     * 路径使用 /members/ 风格, 与 DELETE 移除端点对齐。
     * </p>
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_customer_group", action = "update")
    @PostMapping("/groups/{groupId}/members/{customerId}")
    public OperationResponse<Void> addMember(@PathVariable Long groupId, @PathVariable Long customerId)
            throws ScrmException {
        customerService.addToGroup(groupId, customerId);
        return OperationResponse.build();
    }

    /**
     * 批量将客户加入分组
     * <p>
     * 请求体携带客户 ID 列表, 逐个加入分组。已存在的客户自动跳过,
     * 单个失败不影响其他客户。
     * </p>
     *
     * @param groupId  分组 ID
     * @param customerIds 客户 ID 列表
     * @return 成功添加的数量
     */
    @RequirePermission(resource = "scrm_customer_group", action = "update")
    @PostMapping("/groups/{groupId}/members/batch")
    public OperationResponse<Integer> batchAddMembers(
            @PathVariable Long groupId,
            @RequestBody List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            throw ScrmException.badRequest("客户 ID 列表不能为空");
        }
        return OperationResponse.build(customerService.batchAddToGroup(groupId, customerIds));
    }

    /**
     * 将客户移出分组
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_customer_group", action = "update")
    @DeleteMapping("/groups/{groupId}/members/{customerId}")
    public OperationResponse<Void> removeMember(@PathVariable Long groupId, @PathVariable Long customerId) {
        customerService.removeFromGroup(groupId, customerId);
        return OperationResponse.build();
    }

    /**
     * 删除分组
     * <p>
     * Service 未提供 deleteGroup 方法, 直接调用
     * {@link ScrmCustomerGroupRepository#deleteById}。同时清理分组-成员关联。
     * </p>
     *
     * @param groupId 分组 ID
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_customer_group", action = "delete")
    @DeleteMapping("/groups/{groupId}")
    public OperationResponse<Void> deleteGroup(@PathVariable Long groupId) {
        groupMemberRepository.findByGroupId(groupId)
                .forEach(member -> groupMemberRepository.deleteByGroupIdAndCustomerId(
                        groupId, member.getCustomerId()));
        groupRepository.deleteById(groupId);
        return OperationResponse.build();
    }

    /**
     * 创建分组请求体
     * @author Hsi Chu
     */
    @lombok.Data
    public static class CreateGroupRequest {
        /** 分组名称 */
        private String groupName;
        /** 分组描述 (可选) */
        private String description;
        /** 归属账号 ID */
        private Long ownerAccountId;
    }

    /**
     * 将客户加入分组
     *
     * @param groupId    分组 ID
     * @param customerId 客户 ID
     * @return 空响应
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @PostMapping("/groups/{groupId}/customers/{customerId}")
    public OperationResponse<Void> addToGroup(@PathVariable Long groupId, @PathVariable Long customerId)
            throws ScrmException {
        customerService.addToGroup(groupId, customerId);
        return OperationResponse.build();
    }

    /**
     * 更新客户生命周期阶段
     * <p>
     * 校验 lifecycle 必须为 LEAD/PROSPECT/ACTIVE/DORMANT/CHURNED 之一,
     * 可选携带 remark 备注, 变更记录写入日志。
     * </p>
     *
     * @param id        客户 ID
     * @param lifecycle 生命周期: LEAD / PROSPECT / ACTIVE / DORMANT / CHURNED
     * @param remark    变更备注 (可选)
     * @return 更新后的客户
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @PutMapping("/{id}/lifecycle")
    public OperationResponse<ScrmCustomerDto> updateLifecycle(@PathVariable Long id,
                                                               @RequestParam String lifecycle,
                                                               @RequestParam(required = false) String remark)
            throws ScrmException {
        return OperationResponse.build(customerService.updateLifecycle(id, lifecycle, remark));
    }

    /**
     * 查询客户生命周期变更历史。
     * <p>
     * 返回该客户所有生命周期变更记录, 按 ID 倒序 (最新变更在前), 最多 50 条。
     * 供客户详情页生命周期时间线展示。
     * </p>
     *
     * @param id 客户 ID
     * @return 变更历史列表
     * @throws ScrmException 客户不存在
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/{id}/lifecycle-history")
    public OperationResponse<List<ScrmCustomerLifecycleHistoryEntity>> getLifecycleHistory(
            @PathVariable Long id) throws ScrmException {
        return OperationResponse.build(customerService.getLifecycleHistory(id));
    }

    /**
     * 安排客户下次跟进时间
     * <p>
     * 更新客户的 nextFollowUpAt 字段, 调度器将在到期前 30 分钟内推送 FOLLOWUP_REMINDER 通知。
     * 时间格式: {@code yyyy-MM-dd HH:mm:ss}。
     * </p>
     *
     * @param customerId     客户 ID
     * @param nextFollowUpAt 下次跟进时间
     * @param remark         跟进备注 (可选)
     * @return 更新后的客户
     */
    @RequirePermission(resource = "scrm_customer", action = "update")
    @PutMapping("/{customerId}/follow-up-schedule")
    public OperationResponse<ScrmCustomerDto> scheduleFollowUp(
            @PathVariable Long customerId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime nextFollowUpAt,
            @RequestParam(required = false) String remark)
            throws ScrmException {
        return OperationResponse.build(customerService.scheduleFollowUp(customerId, nextFollowUpAt, remark));
    }

    /**
     * 分页查询客户列表, 支持按平台类型与生命周期过滤
     *
     * @param platformType 平台类型过滤 (可选)
     * @param lifecycle    生命周期过滤 (可选)
     * @param keyword      客户名称 / 昵称关键字过滤 (可选)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 客户分页结果
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping({"", "/list"})
    public OperationResponse<Page<ScrmCustomerDto>> list(
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String lifecycle,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(customerService.listCustomers(platformType, lifecycle, keyword, page, size));
    }

    /**
     * 导出客户列表 (支持 xlsx / csv 格式)。
     * <p>
     * 取大页数据 (最多 10000 条), 按列定义生成字节流并以附件形式返回。
     * 时间字段格式化为 {@code yyyy-MM-dd HH:mm:ss}, 空值导出为空字符串。
     * </p>
     *
     * @param format       导出格式: xlsx (默认) / csv
     * @param platformType 平台类型过滤 (可选)
     * @param lifecycle    生命周期过滤 (可选)
     * @return 包含导出文件的响应实体
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "xlsx") String format,
            @RequestParam(required = false) String platformType,
            @RequestParam(required = false) String lifecycle) {
        // 取大页数据用于导出
        List<ScrmCustomerDto> data = customerService
                .listCustomers(platformType, lifecycle, null, 0, 10000).getContent();
        // 时间格式化器
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 构建导出列: 客户ID / 平台类型 / 平台客户UID / 昵称 / 人设ID / 生命周期 / 标签 / 所属账号ID / 最后交互时间 / 下次跟进时间 / 备注 / 创建时间
        List<ExportColumn<ScrmCustomerDto>> columns = List.of(
                ExportColumn.of("客户ID", ScrmCustomerDto::getId),
                ExportColumn.of("平台类型", ScrmCustomerDto::getPlatformType),
                ExportColumn.of("平台客户UID", ScrmCustomerDto::getPlatformCustomerUid),
                ExportColumn.of("昵称", ScrmCustomerDto::getNickname),
                ExportColumn.of("人设ID", ScrmCustomerDto::getPersonaId),
                ExportColumn.of("生命周期", ScrmCustomerDto::getLifecycle),
                ExportColumn.of("标签", c -> joinTags(c.getId())),
                ExportColumn.of("所属账号ID", ScrmCustomerDto::getOwnerAccountId),
                ExportColumn.of("最后交互时间",
                        c -> c.getLastInteractionAt() == null ? null : c.getLastInteractionAt().format(fmt)),
                ExportColumn.of("下次跟进时间",
                        c -> c.getNextFollowUpAt() == null ? null : c.getNextFollowUpAt().format(fmt)),
                ExportColumn.of("备注", ScrmCustomerDto::getRemark),
                ExportColumn.of("创建时间",
                        c -> c.getCreateTime() == null ? null : c.getCreateTime().format(fmt))
        );
        return exportService.export(data, columns, "客户列表", "customers", format);
    }

    /**
     * 批量导入客户 (支持 xlsx / csv 格式)。
     * <p>
     * 文件第一行为表头, 必填列 platformType / platformCustomerUid, 其余可选。
     * 重复客户 (三元组重复) 计入跳过数, 校验失败或创建异常计入失败数, 单行异常不中断整体。
     * </p>
     *
     * @param file                 上传的文件 (xlsx 或 csv)
     * @param defaultOwnerAccountId 默认归属账号 ID (导入客户统一归属)
     * @return 导入结果统计
     * @throws ScrmException 文件为空 / 格式不支持 / 解析失败
     */
    @RequirePermission(resource = "scrm_customer", action = "create")
    @RateLimit(capacity = 5, refillTokens = 5, refillPeriodSeconds = 60,
            message = "导入操作过于频繁，请稍后重试")
    @PostMapping("/import")
    public OperationResponse<CustomerImportResultVo> importCustomers(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "0") Long defaultOwnerAccountId)
            throws ScrmException {
        // 校验文件非空
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("导入文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw ScrmException.badRequest("无法识别文件名");
        }
        // 按后缀分发到对应解析方法
        String lower = filename.toLowerCase();
        CustomerImportResultVo result;
        try {
            if (lower.endsWith(".xlsx")) {
                result = customerImportService.importFromExcel(file.getInputStream(), defaultOwnerAccountId);
            } else if (lower.endsWith(".csv")) {
                result = customerImportService.importFromCsv(file.getInputStream(), defaultOwnerAccountId);
            } else {
                throw ScrmException.badRequest("不支持的文件格式, 仅支持 .xlsx 和 .csv");
            }
        } catch (IOException e) {
            throw ScrmException.badRequest("文件读取失败: " + e.getMessage());
        }
        return OperationResponse.build(result);
    }

    /**
     * 下载客户导入模板 (支持 xlsx / csv 格式)。
     * <p>
     * 生成包含表头与一行示例数据的模板文件, 复用 {@link ScrmExportService#export} 生成字节流与响应头。
     * 表头: platformType, platformCustomerUid, nickname, avatarUrl, lifecycle, personaId。
     * </p>
     *
     * @param format 模板格式: xlsx (默认) / csv
     * @return 包含模板文件的响应实体
     */
    @RequirePermission(resource = "scrm_customer", action = "read")
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate(
            @RequestParam(defaultValue = "xlsx") String format) {
        // 构建示例数据行
        ScrmCustomerDto sample = new ScrmCustomerDto();
        sample.setPlatformType("wechat_personal");
        sample.setPlatformCustomerUid("wxid_abc123");
        sample.setNickname("张三");
        sample.setAvatarUrl("https://example.com/avatar.jpg");
        sample.setLifecycle("NEW");
        sample.setPersonaId("");
        // 模板列定义 (与导入表头一致)
        List<ExportColumn<ScrmCustomerDto>> columns = List.of(
                ExportColumn.of("platformType", ScrmCustomerDto::getPlatformType),
                ExportColumn.of("platformCustomerUid", ScrmCustomerDto::getPlatformCustomerUid),
                ExportColumn.of("nickname", ScrmCustomerDto::getNickname),
                ExportColumn.of("avatarUrl", ScrmCustomerDto::getAvatarUrl),
                ExportColumn.of("lifecycle", ScrmCustomerDto::getLifecycle),
                ExportColumn.of("personaId", ScrmCustomerDto::getPersonaId)
        );
        return exportService.export(List.of(sample), columns, "客户导入模板",
                "customer_import_template", format);
    }

    /**
     * 拼接客户标签为字符串 (格式: key=value, key2=value2), 失败或无标签返回空。
     *
     * @param customerId 客户 ID
     * @return 标签拼接字符串
     */
    private String joinTags(Long customerId) {
        if (customerId == null) {
            return "";
        }
        try {
            List<ScrmTagCustomerEntity> tags = customerService.getTags(customerId);
            if (tags == null || tags.isEmpty()) {
                return "";
            }
            // tagId → tagCode 解析, 展示 "tagCode=tagValue"
            java.util.Map<Long, String> codeMap = customerService.tagCodeMap(
                    tags.stream().map(ScrmTagCustomerEntity::getTagId)
                            .collect(java.util.stream.Collectors.toSet()));
            return tags.stream()
                    .map(t -> {
                        String code = codeMap.getOrDefault(t.getTagId(), String.valueOf(t.getTagId()));
                        return (t.getTagValue() != null && !t.getTagValue().isBlank())
                                ? code + "=" + t.getTagValue()
                                : code;
                    })
                    .collect(Collectors.joining(", "));
        } catch (ScrmException e) {
            return "";
        }
    }
}
