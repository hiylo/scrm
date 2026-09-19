/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmContractController.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmContractApproveDto;
import org.hiylo.scrm.dto.ScrmContractCreateDto;
import org.hiylo.scrm.dto.ScrmContractDto;
import org.hiylo.scrm.dto.ScrmContractReminderDto;
import org.hiylo.scrm.dto.ScrmContractRenewDto;
import org.hiylo.scrm.dto.ScrmContractSignDto;
import org.hiylo.scrm.dto.ScrmContractTemplateDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RateLimit;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmContractService;
import org.hiylo.scrm.common.OperationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SCRM 客户合同管理控制器。
 * <p>
 * 提供合同模板管理 (增删改查/启停/渲染/复制/变量查询)、合同全生命周期管理
 * (创建/审批/签署/激活/终止/取消/归档/续约/复制)、合同提醒管理 (增删改查/发送/批量发送/
 * 取消/待发送/生成/标记处理) 以及合同与提醒统计接口。权限由 gateway-server 统一鉴权,
 * {@code @RequirePermission} 作为端点权限元数据声明。
 * </p>
 *
 * @author Hsi Chu
 */
@Slf4j
@RestController
@RequestMapping("/scrm/contracts")
@RequiredArgsConstructor
public class ScrmContractController {

    /** 合同服务 */
    private final ScrmContractService scrmContractService;

    // ============================================================
    // 模板管理 /templates
    // ============================================================

    /**
     * 创建合同模板。
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法 / 模板编码重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建模板过于频繁，请稍后重试")
    @PostMapping("/templates")
    public OperationResponse<ScrmContractTemplateDto> createTemplate(@Valid @RequestBody ScrmContractTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.createTemplate(dto));
    }

    /**
     * 更新合同模板 (字段非空才覆盖)。
     * <p>更新模板内容时自动递增业务版本号。</p>
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法 / 模板编码重复
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PutMapping("/templates/{id}")
    public OperationResponse<ScrmContractTemplateDto> updateTemplate(@PathVariable Long id,
                                                                      @RequestBody ScrmContractTemplateDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.updateTemplate(id, dto));
    }

    /**
     * 删除合同模板。
     * <p>已被合同引用的模板不允许删除, 请先停用。</p>
     *
     * @param id 模板 ID
     * @return 空响应
     * @throws ScrmException 模板不存在 / 已被合同引用不允许删除
     */
    @RequirePermission(resource = "scrm_contract", action = "delete")
    @DeleteMapping("/templates/{id}")
    public OperationResponse<Void> deleteTemplate(@PathVariable Long id) throws ScrmException {
        scrmContractService.deleteTemplate(id);
        return OperationResponse.build();
    }

    /**
     * 查询合同模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/templates/{id}")
    public OperationResponse<ScrmContractTemplateDto> getTemplate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.getTemplate(id));
    }

    /**
     * 按模板编码查询合同模板。
     *
     * @param code 模板编码
     * @return 模板详情
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/templates/code/{code}")
    public OperationResponse<ScrmContractTemplateDto> getTemplateByCode(@PathVariable String code)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.getTemplateByCode(code));
    }

    /**
     * 分页查询合同模板, 支持按合同类型、状态与关键词过滤。
     *
     * @param contractType 合同类型过滤 (可空): SALES / SERVICE / PARTNERSHIP / NDA / RESELLER / AGENCY / MAINTENANCE / RENTAL
       * * / PURCHASE / CUSTOM * @param status 状态过滤 (可空): ACTIVE / INACTIVE / DRAFT
     * @param keyword      关键词过滤, 匹配模板名称或编码 (可空)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 模板分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/templates/list")
    public OperationResponse<Page<ScrmContractTemplateDto>> listTemplates(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractService.listTemplates(contractType, status, keyword, pageable));
    }

    /**
     * 启用合同模板 (状态置 ACTIVE)。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PostMapping("/templates/{id}/activate")
    public OperationResponse<ScrmContractTemplateDto> activateTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.activateTemplate(id));
    }

    /**
     * 停用合同模板 (状态置 INACTIVE), 已创建合同不受影响。
     *
     * @param id 模板 ID
     * @return 更新后的模板
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PostMapping("/templates/{id}/deactivate")
    public OperationResponse<ScrmContractTemplateDto> deactivateTemplate(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.deactivateTemplate(id));
    }

    /**
     * 渲染模板: 将模板内容中的 {{variableName}} 占位符替换为 variables 中的值。
     *
     * @param id        模板 ID
     * @param variables 变量值 JSON 字符串 (键值对, 可空)
     * @return 渲染后的合同内容
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @PostMapping("/templates/{id}/render")
    public OperationResponse<String> renderTemplate(@PathVariable Long id,
                                                     @RequestParam(required = false) String variables)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.renderTemplate(id, variables));
    }

    /**
     * 复制合同模板 (基于已有模板创建新模板, 新模板编码由参数指定)。
     * <p>复制后的模板状态为 DRAFT, 使用次数为 0, 业务版本号重置为 1。</p>
     *
     * @param id      源模板 ID
     * @param newCode 新模板编码
     * @return 新模板
     * @throws ScrmException 模板不存在 / 新编码重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @PostMapping("/templates/{id}/copy")
    public OperationResponse<ScrmContractTemplateDto> copyTemplate(@PathVariable Long id,
                                                                    @RequestParam String newCode)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.copyTemplate(id, newCode));
    }

    /**
     * 获取模板变量定义 (解析 variables JSON 并返回变量列表)。
     *
     * @param id 模板 ID
     * @return 变量定义列表
     * @throws ScrmException 模板不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/templates/{id}/variables")
    public OperationResponse<List<Map<String, Object>>> getTemplateVariables(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.getTemplateVariables(id));
    }

    // ============================================================
    // 合同 CRUD
    // ============================================================

    /**
     * 创建合同 (基于模板渲染)。
     * <p>选择模板 → 渲染模板内容 (变量替换) → 创建合同记录 → 递增模板使用次数 → 生成提醒。</p>
     *
     * @param createDto 创建请求 (模板 ID + 客户 ID + 变量 + 自定义字段)
     * @return 创建后的合同
     * @throws ScrmException 模板不存在 / 模板未启用 / 客户不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60, message = "创建合同过于频繁，请稍后重试")
    @PostMapping
    public OperationResponse<ScrmContractDto> createContract(@Valid @RequestBody ScrmContractCreateDto createDto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.createContract(createDto));
    }

    /**
     * 更新合同 (字段非空才覆盖)。
     * <p>仅 DRAFT 状态合同允许更新核心字段 (金额/日期/内容)。</p>
     *
     * @param id  合同 ID
     * @param dto 合同参数
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 参数非法 / 状态不允许更新
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PutMapping("/{id}")
    public OperationResponse<ScrmContractDto> updateContract(@PathVariable Long id,
                                                              @RequestBody ScrmContractDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.updateContract(id, dto));
    }

    /**
     * 删除合同。
     * <p>仅 DRAFT / CANCELLED 状态合同允许删除, 已生效合同请先终止或归档。</p>
     *
     * @param id 合同 ID
     * @return 空响应
     * @throws ScrmException 合同不存在 / 状态不允许删除
     */
    @RequirePermission(resource = "scrm_contract", action = "delete")
    @DeleteMapping("/{id}")
    public OperationResponse<Void> deleteContract(@PathVariable Long id) throws ScrmException {
        scrmContractService.deleteContract(id);
        return OperationResponse.build();
    }

    /**
     * 查询合同详情。
     *
     * @param id 合同 ID
     * @return 合同详情
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/{id}")
    public OperationResponse<ScrmContractDto> getContract(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.getContract(id));
    }

    /**
     * 按合同编号查询合同。
     *
     * @param contractNo 合同编号
     * @return 合同详情
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/no/{contractNo}")
    public OperationResponse<ScrmContractDto> getContractByNo(@PathVariable String contractNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.getContractByNo(contractNo));
    }

    /**
     * 分页查询合同, 支持按合同类型、客户、状态、销售人员、日期范围与关键词过滤。
     *
     * @param contractType  合同类型过滤 (可空)
     * @param customerId    客户 ID 过滤 (可空)
     * @param status 状态过滤 (可空): DRAFT / PENDING_REVIEW / PENDING_SIGNATURE / SIGNED / ACTIVE / EXPIRED / TERMINATED / *
       * CANCELLED / ARCHIVED * @param salesPersonId 销售人员 ID 过滤 (可空)
     * @param startDate     开始日期下限 (按合同开始日期, 可空, ISO 格式: yyyy-MM-dd)
     * @param endDate       开始日期上限 (按合同开始日期, 可空, ISO 格式: yyyy-MM-dd)
     * @param keyword       关键词过滤, 匹配合同名称或编号 (可空)
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/list")
    public OperationResponse<Page<ScrmContractDto>> listContracts(
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String salesPersonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractService.listContracts(contractType, customerId, status,
                salesPersonId, startDate, endDate, keyword, pageable));
    }

    /**
     * 分页查询客户合同列表。
     *
     * @param customerId 客户 ID
     * @param page       页码 (从 0 开始, 默认 0)
     * @param size       每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/customer/{customerId}")
    public OperationResponse<Page<ScrmContractDto>> getContractsByCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractService.getContractsByCustomer(customerId, pageable));
    }

    /**
     * 分页查询销售人员合同列表。
     *
     * @param salesPersonId 销售人员 ID
     * @param page          页码 (从 0 开始, 默认 0)
     * @param size          每页大小 (默认 20)
     * @return 合同分页结果 (按创建时间倒序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/salesPerson/{salesPersonId}")
    public OperationResponse<Page<ScrmContractDto>> getContractsBySalesPerson(
            @PathVariable String salesPersonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        return OperationResponse.build(scrmContractService.getContractsBySalesPerson(salesPersonId, pageable));
    }

    // ============================================================
    // 合同生命周期
    // ============================================================

    /**
     * 提交审批: 合同状态由 DRAFT 流转至 PENDING_REVIEW。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "approve")
    @PostMapping("/{id}/submit")
    public OperationResponse<ScrmContractDto> submitForApproval(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.submitForApproval(id));
    }

    /**
     * 审批合同: 通过则流转至 PENDING_SIGNATURE, 驳回则回退至 DRAFT。
     *
     * @param approveDto 审批请求 (合同 ID + 动作 + 意见)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "approve")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/approve")
    public OperationResponse<ScrmContractDto> approve(@Valid @RequestBody ScrmContractApproveDto approveDto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.approve(approveDto));
    }

    /**
     * 驳回合同 (状态回退至 DRAFT 并记录原因)。
     *
     * @param id     合同 ID
     * @param reason 驳回原因 (可空)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "approve")
    @PostMapping("/{id}/reject")
    public OperationResponse<ScrmContractDto> reject(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.reject(id, reason));
    }

    /**
     * 签署合同: 状态由 PENDING_SIGNATURE 流转至 SIGNED, 记录签署人与签署方式。
     *
     * @param signDto 签署请求 (合同 ID + 签署人 + 签署方式 + 签署文件 URL)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "sign")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/sign")
    public OperationResponse<ScrmContractDto> sign(@Valid @RequestBody ScrmContractSignDto signDto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.sign(signDto));
    }

    /**
     * 激活合同: 状态由 SIGNED 流转至 ACTIVE。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/activate")
    public OperationResponse<ScrmContractDto> activate(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.activate(id));
    }

    /**
     * 终止合同: 状态流转至 TERMINATED, 记录失效日期。
     *
     * @param id              合同 ID
     * @param reason          终止原因 (可空)
     * @param terminationDate 终止日期 (可空, 缺省为当天, ISO 格式: yyyy-MM-dd)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/terminate")
    public OperationResponse<ScrmContractDto> terminate(@PathVariable Long id,
                                                         @RequestParam(required = false) String reason,
                                                         @RequestParam(required = false) @DateTimeFormat(
                                                                  iso = DateTimeFormat.ISO.DATE)
                                                                          LocalDate terminationDate)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.terminate(id, reason, terminationDate));
    }

    /**
     * 取消合同: 状态流转至 CANCELLED。
     *
     * @param id     合同 ID
     * @param reason 取消原因 (可空)
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/cancel")
    public OperationResponse<ScrmContractDto> cancel(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.cancel(id, reason));
    }

    /**
     * 归档合同: 状态流转至 ARCHIVED。
     *
     * @param id 合同 ID
     * @return 更新后的合同
     * @throws ScrmException 合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{id}/archive")
    public OperationResponse<ScrmContractDto> archive(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.archive(id));
    }

    /**
     * 续约合同: 基于原合同创建新合同, 关联原合同并生成提醒。
     *
     * @param renewDto 续约请求 (原合同 ID + 新结束日期 + 自动续约 + 新金额)
     * @return 新合同
     * @throws ScrmException 原合同不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 20, refillTokens = 20, refillPeriodSeconds = 60)
    @PostMapping("/renew")
    public OperationResponse<ScrmContractDto> renew(@Valid @RequestBody ScrmContractRenewDto renewDto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.renew(renewDto));
    }

    /**
     * 复制合同 (基于已有合同创建新合同, 新合同编号由参数指定)。
     * <p>复制后的合同状态为 DRAFT, 不继承审批/签署信息。</p>
     *
     * @param id    源合同 ID
     * @param newNo 新合同编号
     * @return 新合同
     * @throws ScrmException 源合同不存在 / 新编号重复
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @PostMapping("/{id}/duplicate")
    public OperationResponse<ScrmContractDto> duplicate(@PathVariable Long id,
                                                         @RequestParam String newNo)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.duplicate(id, newNo));
    }

    /**
     * 查询即将到期的合同 (未来 N 天内到期, 状态为 ACTIVE/SIGNED)。
     *
     * @param days 天数 (默认 30)
     * @return 合同列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/expiring")
    public OperationResponse<List<ScrmContractDto>> getExpiringContracts(
            @RequestParam(defaultValue = "30") int days) {
        return OperationResponse.build(scrmContractService.getExpiringContracts(days));
    }

    /**
     * 查询已到期合同 (结束日期早于今天, 状态为 ACTIVE/SIGNED)。
     *
     * @return 合同列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/expired")
    public OperationResponse<List<ScrmContractDto>> getExpiredContracts() {
        return OperationResponse.build(scrmContractService.getExpiredContracts());
    }

    // ============================================================
    // 提醒管理 /reminders
    // ============================================================

    /**
     * 创建合同提醒。
     *
     * @param dto 提醒参数
     * @return 创建后的提醒
     * @throws ScrmException 参数非法 / 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "create")
    @PostMapping("/reminders")
    public OperationResponse<ScrmContractReminderDto> createReminder(@Valid @RequestBody ScrmContractReminderDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.createReminder(dto));
    }

    /**
     * 更新合同提醒 (字段非空才覆盖)。
     *
     * @param id  提醒 ID
     * @param dto 提醒参数
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 参数非法
     */
    @RequirePermission(resource = "scrm_contract", action = "update")
    @PutMapping("/reminders/{id}")
    public OperationResponse<ScrmContractReminderDto> updateReminder(@PathVariable Long id,
                                                                      @RequestBody ScrmContractReminderDto dto)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.updateReminder(id, dto));
    }

    /**
     * 删除合同提醒。
     *
     * @param id 提醒 ID
     * @return 空响应
     * @throws ScrmException 提醒不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "delete")
    @DeleteMapping("/reminders/{id}")
    public OperationResponse<Void> deleteReminder(@PathVariable Long id) throws ScrmException {
        scrmContractService.deleteReminder(id);
        return OperationResponse.build();
    }

    /**
     * 查询合同提醒详情。
     *
     * @param id 提醒 ID
     * @return 提醒详情
     * @throws ScrmException 提醒不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/reminders/{id}")
    public OperationResponse<ScrmContractReminderDto> getReminder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.getReminder(id));
    }

    /**
     * 分页查询合同提醒, 支持按合同 ID、提醒类型、状态与日期范围过滤。
     *
     * @param contractId   合同 ID 过滤 (可空)
     * @param reminderType 提醒类型过滤 (可空): EXPIRY / PAYMENT / RENEWAL / REVIEW / CUSTOM
     * @param status       状态过滤 (可空): PENDING / SENT / FAILED / CANCELLED
     * @param startDate    起始日期 (按提醒日期, 可空, ISO 格式: yyyy-MM-dd)
     * @param endDate      截止日期 (按提醒日期, 可空, ISO 格式: yyyy-MM-dd)
     * @param page         页码 (从 0 开始, 默认 0)
     * @param size         每页大小 (默认 20)
     * @return 提醒分页结果 (按提醒日期升序)
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/reminders/list")
    public OperationResponse<Page<ScrmContractReminderDto>> listReminders(
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) String reminderType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "reminderDate"));
        return OperationResponse.build(scrmContractService.listReminders(contractId, reminderType, status,
                startDate, endDate, pageable));
    }

    /**
     * 发送提醒 (模拟): 状态置 SENT, 累加发送次数, 刷新合同最后提醒时间。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 30, refillTokens = 30, refillPeriodSeconds = 60)
    @PostMapping("/reminders/{id}/send")
    public OperationResponse<ScrmContractReminderDto> sendReminder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.sendReminder(id));
    }

    /**
     * 批量发送提醒 (定时任务): 扫描当前账号下 reminderDate 早于今天的 PENDING 提醒并发送。
     *
     * @return 发送的提醒数量
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/reminders/batchSend")
    public OperationResponse<Integer> batchSendReminders() {
        return OperationResponse.build(scrmContractService.batchSendReminders());
    }

    /**
     * 取消提醒 (状态置 CANCELLED)。
     *
     * @param id 提醒 ID
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在 / 状态非法
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/reminders/{id}/cancel")
    public OperationResponse<ScrmContractReminderDto> cancelReminder(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(scrmContractService.cancelReminder(id));
    }

    /**
     * 查询待发送提醒 (状态为 PENDING, 按提醒日期升序)。
     *
     * @return 待发送提醒列表
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/reminders/pending")
    public OperationResponse<List<ScrmContractReminderDto>> getPendingReminders() {
        return OperationResponse.build(scrmContractService.getPendingReminders());
    }

    /**
     * 为合同生成提醒 (到期/续约/付款)。
     *
     * @param contractId 合同 ID
     * @return 生成的提醒列表
     * @throws ScrmException 合同不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/{contractId}/reminders/generate")
    public OperationResponse<List<ScrmContractReminderDto>> generateRemindersForContract(
            @PathVariable Long contractId) throws ScrmException {
        return OperationResponse.build(scrmContractService.generateRemindersForContract(contractId));
    }

    /**
     * 检查并生成提醒 (定时任务): 扫描即将到期的合同并生成提醒。
     *
     * @return 生成提醒的合同数量
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @RateLimit(capacity = 1, refillTokens = 1, refillPeriodSeconds = 60)
    @PostMapping("/reminders/checkGenerate")
    public OperationResponse<Integer> checkAndGenerateReminders() {
        return OperationResponse.build(scrmContractService.checkAndGenerateReminders());
    }

    /**
     * 标记提醒已处理。
     *
     * @param id       提醒 ID
     * @param actionBy 处理人 (可空)
     * @return 更新后的提醒
     * @throws ScrmException 提醒不存在
     */
    @RequirePermission(resource = "scrm_contract", action = "execute")
    @PostMapping("/reminders/{id}/markTaken")
    public OperationResponse<ScrmContractReminderDto> markActionTaken(@PathVariable Long id,
                                                                      @RequestParam(required = false) String actionBy)
            throws ScrmException {
        return OperationResponse.build(scrmContractService.markActionTaken(id, actionBy));
    }

    // ============================================================
    // 统计 /stats
    // ============================================================

    /**
     * 合同统计: 总数 / 各类型 / 各状态 / 总金额 / 平均期限。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats")
    public OperationResponse<Map<String, Object>> getContractStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractService.getContractStats(startTime, endTime));
    }

    /**
     * 到期统计: 未来 N 个月内到期的合同数量与金额。
     *
     * @param months 月数 (默认 3)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/expiry")
    public OperationResponse<Map<String, Object>> getExpiryStats(
            @RequestParam(defaultValue = "3") int months) {
        return OperationResponse.build(scrmContractService.getExpiryStats(months));
    }

    /**
     * 续约统计: 续约率 / 平均续约金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/renewal")
    public OperationResponse<Map<String, Object>> getRenewalStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractService.getRenewalStats(startTime, endTime));
    }

    /**
     * 销售人员合同统计: 合同数 / 总金额 / 各状态分布。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param salesPersonId 销售人员 ID
     * @param startTime     起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime       截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/salesPerson/{salesPersonId}")
    public OperationResponse<Map<String, Object>> getSalesPersonContractStats(
            @PathVariable String salesPersonId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractService.getSalesPersonContractStats(salesPersonId,
                startTime, endTime));
    }

    /**
     * 金额统计: 总金额 / 平均金额 / 各类型金额。
     * <p>时间范围按合同创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/value")
    public OperationResponse<Map<String, Object>> getValueStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractService.getValueStats(startTime, endTime));
    }

    /**
     * 合同趋势: 过去 N 个月每月新增合同数与金额。
     *
     * @param months 月数 (默认 6)
     * @return 趋势数据
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/trend")
    public OperationResponse<Map<String, Object>> getContractTrend(
            @RequestParam(defaultValue = "6") int months) {
        return OperationResponse.build(scrmContractService.getContractTrend(months));
    }

    /**
     * 提醒统计: 总数 / 各类型 / 各状态 / 已处理率。
     * <p>时间范围按提醒创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @param endTime   截止时间 (可空, ISO 格式: yyyy-MM-dd'T'HH:mm:ss)
     * @return 统计结果
     */
    @RequirePermission(resource = "scrm_contract", action = "read")
    @GetMapping("/stats/reminders")
    public OperationResponse<Map<String, Object>> getReminderStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return OperationResponse.build(scrmContractService.getReminderStats(startTime, endTime));
    }
}
