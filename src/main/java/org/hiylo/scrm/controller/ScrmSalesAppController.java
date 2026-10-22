/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmSalesAppController.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCallReportDto;
import org.hiylo.scrm.entity.ScrmCallRecordEntity;
import org.hiylo.scrm.entity.ScrmCustomerContactEntity;
import org.hiylo.scrm.entity.ScrmRecordingEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.DataScopeService;
import org.hiylo.scrm.service.ScrmSalesAppService;
import org.hiylo.scrm.service.ScrmSalesAppService.PhoneMatch;
import org.hiylo.scrm.vo.ScrmCallReportResultVo;
import org.hiylo.scrm.vo.ScrmRecordingCapabilityVo;
import org.hiylo.scrm.vo.ScrmRecordingCoverageVo;
import org.hiylo.scrm.vo.ScrmRecordingUploadResultVo;
import org.hiylo.scrm.vo.ScrmSalesCustomerVo;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售端 App 外呼域控制器。
 * <p>
 * 提供给销售 Android App 使用的专属接口，统一前缀 {@code /scrm/sales-app}。
 * 「我的客户」以公海线索为主（assigned_to = 当前销售），兼容正式客户查询。
 * </p>
 * <p>
 * 归属模型：{@code ownerType} ∈ {CUSTOMER, PUBLIC_SEA}，{@code ownerId} 为对应对象 ID。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/sales-app")
@RequiredArgsConstructor
public class ScrmSalesAppController {

    /** 通话批量上报单次上限 */
    private static final int MAX_BATCH_SIZE = 500;

    /** 销售端外呼域服务 */
    private final ScrmSalesAppService salesAppService;

    /** 数据隔离服务（按角色计算可访问账号范围） */
    private final DataScopeService dataScopeService;

    // ============================================================
    // 客户（销售「我的客户」= 分配给我的公海线索）
    // ============================================================

    /**
     * 查询分配给当前销售的公海线索（销售 App 主数据源）。
     *
     * @param keyword 昵称 / 备注 / 平台 UID 关键词
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 线索分页
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/leads")
    public OperationResponse<Page<ScrmSalesCustomerVo>> listMyLeads(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                salesAppService.listAssignedLeads(requireUserId(), keyword, page, size));
    }

    /**
     * 查询按运营账号归属的正式客户（兼容路径）。
     *
     * @param keyword 昵称 / 备注关键词
     * @param page    页码
     * @param size    每页大小
     * @return 客户分页
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/customers")
    public OperationResponse<Page<ScrmSalesCustomerVo>> listAccountCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = requireUserId();
        List<Long> accessible = dataScopeService.getAccessibleAccountIds(userId,
                currentRole(), null);
        return OperationResponse.build(
                salesAppService.listAccountCustomers(accessible, keyword, page, size));
    }

    /**
     * 查询客户/线索详情。
     *
     * @param ownerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param ownerId   归属对象 ID
     * @return 客户视图
     * @throws ScrmException 对象不存在
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/owners/{ownerType}/{ownerId}")
    public OperationResponse<ScrmSalesCustomerVo> getCustomerDetail(
            @PathVariable String ownerType,
            @PathVariable Long ownerId) throws ScrmException {
        return OperationResponse.build(salesAppService.getCustomerDetail(ownerType, ownerId));
    }

    /**
     * 保存客户/线索联系方式。
     *
     * @param ownerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param ownerId   归属对象 ID
     * @param body      联系方式参数
     * @return 保存后的联系方式
     * @throws ScrmException 对象不存在 / 号码非法
     */
    @RequirePermission(resource = "scrm_sales_app", action = "write")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/owners/{ownerType}/{ownerId}/contacts")
    public OperationResponse<ScrmCustomerContactEntity> saveContact(
            @PathVariable String ownerType,
            @PathVariable Long ownerId,
            @Valid @RequestBody ContactRequest body) throws ScrmException {
        return OperationResponse.build(salesAppService.saveContact(
                ownerType, ownerId, body.getPhone(), body.getPhoneType(),
                body.getIsPrimary(), body.getRemark()));
    }

    /**
     * 按号码查询归属对象（撞单检测 / 来电匹配）。
     *
     * @param phone 号码
     * @return 归属对象列表
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/phone-lookup")
    public OperationResponse<List<PhoneMatch>> phoneLookup(
            @RequestParam String phone) {
        return OperationResponse.build(salesAppService.findOwnersByPhone(phone));
    }

    // ============================================================
    // 通话记录
    // ============================================================

    /**
     * 批量上报通话记录（幂等）。
     *
     * @param reports 通话上报列表（最多 500 条）
     * @return 批量结果统计
     * @throws ScrmException 参数非法
     */
    @RequirePermission(resource = "scrm_sales_app", action = "write")
    @RateLimit(capacity = 120, refillTokens = 120, refillPeriodSeconds = 60)
    @PostMapping("/calls/batch")
    public OperationResponse<ScrmCallReportResultVo> reportCalls(
            @Valid @RequestBody List<ScrmCallReportDto> reports) throws ScrmException {
        if (reports == null || reports.isEmpty()) {
            throw ScrmException.badRequest("通话上报列表不能为空");
        }
        if (reports.size() > MAX_BATCH_SIZE) {
            throw ScrmException.badRequest("单次最多上报 " + MAX_BATCH_SIZE + " 条通话记录");
        }
        return OperationResponse.build(salesAppService.reportCalls(requireUserId(), reports));
    }

    /**
     * 查询我的通话记录（分页）。
     *
     * @param ownerType 归属类型过滤（可空）
     * @param ownerId   归属对象 ID 过滤（可空）
     * @param page      页码
     * @param size      每页大小
     * @return 通话记录分页
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/calls")
    public OperationResponse<Page<ScrmCallRecordEntity>> listCalls(
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                salesAppService.listCalls(requireUserId(), ownerType, ownerId, page, size));
    }

    /**
     * 将通话记录关联到客户/线索（待认领 → 已归属）。
     *
     * @param id   通话记录 ID
     * @param body 关联参数
     * @return 更新后的通话记录
     * @throws ScrmException 记录不存在 / 越权 / 归属对象不存在
     */
    @RequirePermission(resource = "scrm_sales_app", action = "write")
    @PostMapping("/calls/{id}/bind")
    public OperationResponse<ScrmCallRecordEntity> bindCall(
            @PathVariable Long id,
            @Valid @RequestBody BindCallRequest body) throws ScrmException {
        return OperationResponse.build(
                salesAppService.bindCallToCustomer(requireUserId(), id,
                        body.getOwnerType(), body.getOwnerId()));
    }

    // ============================================================
    // 录音
    // ============================================================

    /**
     * 上传录音文件。
     *
     * @param file         录音文件（multipart 字段名 file）
     * @param deviceId     设备 ID
     * @param source       来源: OEM_RECORD / LINE / MANUAL
     * @param callRecordId 关联通话记录 ID（可空）
     * @param ownerType    归属类型（可空）
     * @param ownerId      归属对象 ID（可空）
     * @param durationSec  录音时长（秒，可空）
     * @return 上传结果
     * @throws ScrmException 文件为空 / 参数非法 / 存储失败
     */
    @RequirePermission(resource = "scrm_sales_app", action = "write")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/recordings")
    public OperationResponse<ScrmRecordingUploadResultVo> uploadRecording(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String deviceId,
            @RequestParam(defaultValue = "MANUAL") String source,
            @RequestParam(required = false) Long callRecordId,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Integer durationSec) throws ScrmException {
        if (file == null || file.isEmpty()) {
            throw ScrmException.badRequest("上传录音文件不能为空");
        }
        try (InputStream inputStream = file.getInputStream()) {
            return OperationResponse.build(salesAppService.uploadRecording(
                    requireUserId(), deviceId, file.getOriginalFilename(),
                    file.getContentType(), source, callRecordId, ownerType, ownerId,
                    durationSec, inputStream));
        } catch (IOException e) {
            throw new ScrmException("SCRM_RECORDING_READ_FAILED",
                    "读取上传录音文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询我的录音（分页）。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 录音分页
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/recordings")
    public OperationResponse<Page<ScrmRecordingEntity>> listRecordings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(salesAppService.listRecordings(requireUserId(), page, size));
    }

    /**
     * 获取录音预签名播放地址。
     *
     * @param id            录音记录 ID
     * @param expiryMinutes 有效期（分钟，默认 60）
     * @return 预签名 URL
     * @throws ScrmException 记录不存在 / 越权
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/recordings/{id}/url")
    public OperationResponse<String> getRecordingUrl(
            @PathVariable Long id,
            @RequestParam(defaultValue = "60") int expiryMinutes) throws ScrmException {
        return OperationResponse.build(
                salesAppService.getRecordingUrl(requireUserId(), id, expiryMinutes));
    }

    /**
     * 录音覆盖率统计。
     *
     * @param userId 目标用户 ID（ADMIN 可查他人；普通用户只能查自己）
     * @param from   起始时间（可空）
     * @param to     结束时间（可空）
     * @return 覆盖率
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/recordings/coverage")
    public OperationResponse<ScrmRecordingCoverageVo> getCoverage(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {
        String current = requireUserId();
        String target = current;
        if (userId != null && !userId.isBlank() && !userId.equals(current)) {
            if (!isAdmin()) {
                throw new ScrmException(org.hiylo.scrm.exception.ScrmExceptionConstants.FORBIDDEN,
                        "无权查看他人录音覆盖率");
            }
            target = userId;
        }
        return OperationResponse.build(salesAppService.getCoverage(target, from, to));
    }

    /**
     * 获取录音能力与操作指引（按 ROM）。
     *
     * @param romHint 客户端上报的系统标识（制造商/品牌/ROM，可空）
     * @return 能力与指引
     */
    @RequirePermission(resource = "scrm_sales_app", action = "read")
    @GetMapping("/recordings/capability")
    public OperationResponse<ScrmRecordingCapabilityVo> getRecordingCapability(
            @RequestParam(required = false) String romHint) {
        return OperationResponse.build(salesAppService.getRecordingCapability(romHint));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 获取当前登录用户 ID，缺失时抛出 401。
     *
     * @return 用户 ID
     */
    private String requireUserId() {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) {
            throw new ScrmException(org.hiylo.scrm.exception.ScrmExceptionConstants.UNAUTHORIZED,
                    "未获取到登录用户");
        }
        return userId;
    }

    /**
     * 获取当前请求的业务角色（用于数据隔离）。
     *
     * @return 角色字符串
     */
    private String currentRole() {
        org.springframework.web.context.request.RequestAttributes attributes =
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader(DataScopeService.HEADER_USER_ROLE);
        }
        return null;
    }

    /**
     * 当前用户是否管理员。
     *
     * @return 管理员返回 true
     */
    private boolean isAdmin() {
        String role = currentRole();
        return role != null && role.toUpperCase().contains(DataScopeService.ROLE_ADMIN);
    }

    /**
     * 联系方式保存请求体。
     *
     * @author Hsi Chu
     */
    @lombok.Data
    public static class ContactRequest {
        /** 号码 */
        @jakarta.validation.constraints.NotBlank(message = "号码不能为空")
        @jakarta.validation.constraints.Size(max = 32, message = "号码长度不能超过 32")
        private String phone;
        /** 号码类型: MOBILE / LANDLINE / OTHER */
        private String phoneType;
        /** 是否主号码 */
        private Boolean isPrimary;
        /** 备注 */
        @jakarta.validation.constraints.Size(max = 200, message = "备注长度不能超过 200")
        private String remark;
    }

    /**
     * 通话关联客户/线索请求体。
     *
     * @author Hsi Chu
     */
    @lombok.Data
    public static class BindCallRequest {
        /** 归属类型: CUSTOMER / PUBLIC_SEA */
        @jakarta.validation.constraints.NotBlank(message = "归属类型不能为空")
        private String ownerType;
        /** 归属对象 ID */
        @jakarta.validation.constraints.NotNull(message = "归属对象 ID 不能为空")
        private Long ownerId;
    }
}
