/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrController.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmLicenseOcrTaskDto;
import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmLicenseOcrService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 证照 OCR 识别控制器（模块②）。
 * <p>
 * 证照上传→识别→落库→低置信度字段人工复核。端点一律声明
 * {@code @RequirePermission} 资源与动作元数据（RBAC 由 gateway 统一鉴权）。
 * 识别结果中的字号号/银行账号等敏感字段在 DTO 层脱敏后返回, 不复显客户侧;
 * 复核接口（涉及 OCR 全文）声明更高动作权限。
 * </p>
 * <ul>
 *   <li>POST /scrm/license-ocr/tasks - 上传证照并识别（multipart: file + licenseType + side）</li>
 *   <li>GET  /scrm/license-ocr/tasks - 分页查询（类型/状态/复核状态过滤）</li>
 *   <li>GET  /scrm/license-ocr/tasks/{id} - 任务详情（识别字段脱敏）</li>
 *   <li>GET  /scrm/license-ocr/reviews/pending - 待人工复核队列</li>
 *   <li>POST /scrm/license-ocr/tasks/{id}/review - 复核低置信度字段</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/license-ocr")
@RequiredArgsConstructor
public class ScrmLicenseOcrController {

    /** 证照 OCR 识别任务服务 */
    private final ScrmLicenseOcrService licenseOcrService;

    // ============================================================
    // 上传识别
    // ============================================================

    /**
     * 上传证照图片/PDF 并触发 OCR 识别（同步返回任务, 含复核标记）。
     *
     * @param file      证照文件（multipart 字段名 file, ≤20MB）
     * @param dto       任务参数（licenseType 必填; customerId/businessId/side 可空; 表单字段）
     * @return 落库后的任务（脱敏 DTO）
     * @throws ScrmException 参数非法 / 文件读取失败
     */
    @RequirePermission(resource = "scrm_license_ocr", action = "create")
    @RateLimit(capacity = 10, refillTokens = 10, refillPeriodSeconds = 60)
    @PostMapping("/tasks")
    public OperationResponse<ScrmLicenseOcrTaskDto> createTask(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("dto") ScrmLicenseOcrTaskDto dto) throws ScrmException {
        return OperationResponse.build(
                licenseOcrService.toDto(licenseOcrService.uploadAndRecognize(file, dto)));
    }

    // ============================================================
    // 查询
    // ============================================================

    /**
     * 分页查询识别任务（支持类型/状态/复核状态/归属过滤）。
     *
     * @param licenseType   识别类型过滤（可空）
     * @param taskStatus    任务状态过滤（可空）
     * @param reviewStatus  复核状态过滤（可空）
     * @param ownerAccountId 归属账号过滤（可空）
     * @param page          页码（从 0 开始, 默认 0）
     * @param size          每页大小（默认 20）
     * @return 任务分页（识别字段脱敏）
     */
    @RequirePermission(resource = "scrm_license_ocr", action = "read")
    @GetMapping("/tasks")
    public OperationResponse<Page<ScrmLicenseOcrTaskDto>> listTasks(
            @RequestParam(required = false) String licenseType,
            @RequestParam(required = false) String taskStatus,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Long ownerAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<ScrmLicenseOcrTaskEntity> result = licenseOcrService.list(
                licenseType, taskStatus, reviewStatus, ownerAccountId, pageable);
        return OperationResponse.build(result.map(licenseOcrService::toDto));
    }

    /**
     * 查询任务详情（识别字段/全文脱敏后返回）。
     *
     * @param id 任务 ID
     * @return 任务 DTO
     * @throws ScrmException 任务不存在
     */
    @RequirePermission(resource = "scrm_license_ocr", action = "read")
    @GetMapping("/tasks/{id}")
    public OperationResponse<ScrmLicenseOcrTaskDto> getTask(@PathVariable Long id)
            throws ScrmException {
        // 列表过滤挡不住直接拼 ID，详情必须单独校验归属
        licenseOcrService.assertTaskAccessible(id);
        return OperationResponse.build(licenseOcrService.toDto(licenseOcrService.get(id)));
    }

    /**
     * 查询待人工复核队列（低置信度字段）。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 待复核任务分页
     */
    @RequirePermission(resource = "scrm_license_ocr", action = "read")
    @GetMapping("/reviews/pending")
    public OperationResponse<Page<ScrmLicenseOcrTaskDto>> listPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "createTime"));
        Page<ScrmLicenseOcrTaskEntity> result = licenseOcrService.listPendingReview(pageable);
        return OperationResponse.build(result.map(licenseOcrService::toDto));
    }

    // ============================================================
    // 低置信度字段人工复核（复用 QualityInspection 语义）
    // ============================================================

    /**
     * 复核低置信度字段（复核通过 → REVIEWED, 之后才允许写业务档案域）。
     *
     * @param id     任务 ID
     * @param passed 是否复核通过
     * @param remark 复核备注（可空）
     * @return 更新后的任务
     * @throws ScrmException 任务不存在 / 状态不允许复核
     */
    @RequirePermission(resource = "scrm_license_ocr", action = "review")
    @RateLimit(capacity = 60, refillTokens = 60, refillPeriodSeconds = 60)
    @PostMapping("/tasks/{id}/review")
    public OperationResponse<ScrmLicenseOcrTaskDto> reviewTask(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean passed,
            @RequestParam(required = false) String remark) throws ScrmException {
        // 复核是写操作，同样不能让会计用他人任务 ID 越权复核
        licenseOcrService.assertTaskAccessible(id);
        return OperationResponse.build(
                licenseOcrService.toDto(licenseOcrService.review(id, passed, remark)));
    }
}