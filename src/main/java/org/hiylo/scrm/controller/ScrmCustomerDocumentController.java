/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerDocumentController.java
 * Date : 2026/10/25 16:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.entity.ScrmCustomerDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.finance.document.ScrmCustomerDocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/**
 * 客户资料归档控制器（资料域）。
 * <p>
 * 归档的价值在于「知道缺什么」：接户时按必备清单收齐、年检时能立刻补交、
 * 换会计时能整户交接。因此除文件增删查外，提供 {@code checklist} 与
 * {@code overview} 两个齐全度视图。
 * </p>
 * <p>
 * 文件本体存对象存储，下载走预签名 URL 直连存储，不经应用进程中转。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/documents")
@RequiredArgsConstructor
public class ScrmCustomerDocumentController {

    /** 资料归档服务 */
    private final ScrmCustomerDocumentService documentService;

    /**
     * 上传资料文件并归档。
     *
     * @param file       文件（multipart 字段名 file）
     * @param companyId  公司主体 ID
     * @param docType    资料类型
     * @param docName    资料名称（可空，默认用文件名）
     * @param issueDate  资料签发日（可空，yyyy-MM-dd）
     * @param expireDate 资料到期日（可空，yyyy-MM-dd）
     * @param remark     备注（可空）
     * @return 归档后的资料记录
     * @throws ScrmException 参数非法 / 主体不存在 / 存储失败
     */
    @RequirePermission(resource = "scrm_customer_document", action = "create")
    @PostMapping("/upload")
    public OperationResponse<ScrmCustomerDocumentEntity> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long companyId,
            @RequestParam String docType,
            @RequestParam(required = false) String docName,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expireDate,
            @RequestParam(required = false) String remark) throws ScrmException {
        return OperationResponse.build(documentService.upload(
                companyId, docType, docName, file, issueDate, expireDate, remark));
    }

    /**
     * 分页查询资料。
     *
     * @param page      页码（默认 0）
     * @param size      每页大小（默认 20）
     * @param companyId 主体过滤（可空）
     * @param docType   类型过滤（可空）
     * @param status    状态过滤（可空）
     * @param keyword   资料名/文件名/企业名关键字（可空）
     * @return 资料分页结果
     */
    @RequirePermission(resource = "scrm_customer_document", action = "read")
    @GetMapping
    public OperationResponse<Page<ScrmCustomerDocumentEntity>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return OperationResponse.build(
                documentService.list(companyId, docType, status, keyword, pageable));
    }

    /**
     * 查询某公司的资料齐全度清单（接户/年检用）。
     *
     * @param companyId 公司主体 ID
     * @return 必备项逐项状态 + 齐全率 + 资料列表
     * @throws ScrmException 主体不存在
     */
    @RequirePermission(resource = "scrm_customer_document", action = "read")
    @GetMapping("/checklist/{companyId}")
    public OperationResponse<Map<String, Object>> checklist(@PathVariable Long companyId)
            throws ScrmException {
        return OperationResponse.build(documentService.checklist(companyId));
    }

    /**
     * 全量资料齐全度总览（找出资料不全的在服企业）。
     *
     * @return 每户齐全度摘要 + 汇总
     */
    @RequirePermission(resource = "scrm_customer_document", action = "read")
    @GetMapping("/overview")
    public OperationResponse<Map<String, Object>> overview() {
        return OperationResponse.build(documentService.overview());
    }

    /**
     * 查询资料详情。
     *
     * @param id 资料 ID
     * @return 资料
     * @throws ScrmException 不存在
     */
    @RequirePermission(resource = "scrm_customer_document", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmCustomerDocumentEntity> get(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(documentService.get(id));
    }

    /**
     * 生成下载预签名 URL（直连对象存储，不经应用进程中转）。
     *
     * @param id 资料 ID
     * @return {url} 预签名下载地址
     * @throws ScrmException 资料不存在 / 生成失败
     */
    @RequirePermission(resource = "scrm_customer_document", action = "read")
    @GetMapping("/{id}/download-url")
    public OperationResponse<Map<String, String>> downloadUrl(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(Map.of("url", documentService.presignDownload(id)));
    }

    /**
     * 更新资料元数据（不换文件；换文件请重新上传）。
     *
     * @param id         资料 ID
     * @param docName    名称（可空）
     * @param docType    类型（可空）
     * @param issueDate  签发日（可空）
     * @param expireDate 到期日（可空）
     * @param remark     备注（可空）
     * @param status     状态（可空）
     * @return 更新后的资料
     * @throws ScrmException 资料不存在
     */
    @RequirePermission(resource = "scrm_customer_document", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmCustomerDocumentEntity> update(
            @PathVariable Long id,
            @RequestParam(required = false) String docName,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expireDate,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String status) throws ScrmException {
        return OperationResponse.build(
                documentService.update(id, docName, docType, issueDate, expireDate, remark, status));
    }

    /**
     * 删除资料（删记录 + 删对象存储对象）。
     *
     * @param id 资料 ID
     * @return 空响应
     * @throws ScrmException 资料不存在
     */
    @RequirePermission(resource = "scrm_customer_document", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        documentService.delete(id);
        return OperationResponse.build(null);
    }
}
