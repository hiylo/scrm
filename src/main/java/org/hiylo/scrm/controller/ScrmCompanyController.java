/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmCompanyController.java
 * Date : 2026/10/23 16:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.OperationResponse;
import org.hiylo.scrm.dto.ScrmCompanyDto;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.rbac.annotation.RequirePermission;
import org.hiylo.scrm.service.ScrmCompanyMergeService;
import org.hiylo.scrm.service.ScrmCustomerBackfillService;
import org.hiylo.scrm.service.ScrmCompanyOverviewService;
import org.hiylo.scrm.service.ScrmCompanyService;
import org.springframework.data.domain.Page;
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
 * 公司主体控制器（财税业务主键）。
 * <p>
 * 一个公司下挂多个联系人（老板 / 多名财务 / 纯手机号或线下联系人），财税业务
 * （报税、证照、巡检、续约、合同）以公司为主体。端点一律声明
 * {@code @RequirePermission} 资源与动作元数据（RBAC 由 gateway 统一鉴权）。
 * </p>
 * <ul>
 *   <li>POST   /scrm/companies                      - 创建主体</li>
 *   <li>PUT    /scrm/companies/{id}                 - 更新主体</li>
 *   <li>GET    /scrm/companies/{id}                 - 主体详情（含联系人列表）</li>
 *   <li>GET    /scrm/companies                      - 分页查询（名称/信用代码/状态）</li>
 *   <li>DELETE /scrm/companies/{id}                 - 删除主体（无联系人挂靠时）</li>
 *   <li>GET    /scrm/companies/{id}/contacts        - 公司下联系人列表</li>
 *   <li>POST   /scrm/companies/{id}/contacts        - 联系人挂靠（含公司内角色）</li>
 *   <li>DELETE /scrm/companies/contacts/{customerId} - 解除联系人归属</li>
 *   <li>POST   /scrm/companies/from-license-task/{taskId} - 证照识别结果建主体（闭环）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@RestController
@RequestMapping("/scrm/companies")
@RequiredArgsConstructor
public class ScrmCompanyController {

    /** 公司主体服务 */
    private final ScrmCompanyService companyService;

    /** 公司 360 聚合服务 */
    private final ScrmCompanyOverviewService overviewService;

    /** 存量联系人归并服务（待归并清单 + 候选 + 批量挂靠） */
    private final ScrmCompanyMergeService mergeService;

    /** 存量资料补录服务（手机号 / 按手机号挂靠） */
    private final ScrmCustomerBackfillService backfillService;

    /**
     * 创建公司主体。
     *
     * @param dto 主体参数
     * @return 创建后的主体
     * @throws ScrmException 参数非法 / 信用代码重复
     */
    @PostMapping
    @RequirePermission(resource = "scrm_company", action = "create")
    public OperationResponse<ScrmCompanyDto> create(@Valid @RequestBody ScrmCompanyDto dto)
            throws ScrmException {
        return OperationResponse.build(companyService.create(dto));
    }

    /**
     * 更新公司主体。
     *
     * @param id  主体 ID
     * @param dto 更新参数（null 字段不覆盖）
     * @return 更新后的主体
     * @throws ScrmException 主体不存在 / 参数非法
     */
    @PutMapping("/{id}")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<ScrmCompanyDto> update(@PathVariable Long id,
                                                    @Valid @RequestBody ScrmCompanyDto dto)
            throws ScrmException {
        return OperationResponse.build(companyService.update(id, dto));
    }

    /**
     * 指派/取消负责会计（专用端点）。
     * <p>
     * 不走 {@code PUT /{id}} 的原因是那个端点的 DTO 带 {@code @NotBlank companyName}
     * 等全量校验，只为改一个会计字段而回传整个主体既繁琐又容易误改其它字段。
     * </p>
     * <p>
     * {@code accountantId} 传 {@code null} 或 {@code 0} 表示取消指派；取消后
     * 报税/续费提醒自动回落到配置的默认收件人。
     * </p>
     *
     * @param id            公司主体 ID
     * @param accountantId  负责会计的用户 ID（null/0 = 取消指派）
     * @param accountantName 负责会计姓名（冗余展示）
     * @return 更新后的主体
     * @throws ScrmException 主体不存在
     */
    @PutMapping("/{id}/accountant")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<ScrmCompanyDto> assignAccountant(
            @PathVariable Long id,
            @RequestParam(required = false) Long accountantId,
            @RequestParam(required = false) String accountantName) throws ScrmException {
        return OperationResponse.build(
                companyService.assignAccountant(id, accountantId, accountantName));
    }

    /**
     * 批量指派负责会计（代账公司一次接手几十上百户的入口动作）。
     *
     * @param payload 公司 ID 列表 + 会计 ID + 会计姓名
     * @return 指派结果（成功数 + 失败明细）
     */
    @PutMapping("/accountants/batch")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<Map<String, Object>> batchAssignAccountant(
            @RequestBody Map<String, Object> payload) {
        List<Long> companyIds = new java.util.ArrayList<>();
        Object rawIds = payload.get("companyIds");
        if (rawIds instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    companyIds.add(Long.valueOf(String.valueOf(item)));
                }
            }
        }
        Long accountantId = payload.get("accountantId") == null
                ? null : Long.valueOf(String.valueOf(payload.get("accountantId")));
        String accountantName = payload.get("accountantName") == null
                ? null : String.valueOf(payload.get("accountantName"));
        return OperationResponse.build(
                companyService.batchAssignAccountant(companyIds, accountantId, accountantName));
    }

    /**
     * 我的客户（当前登录会计负责的主体）。
     * <p>
     * 会计登录后的默认工作台入口，数据范围由 {@code accountant_id} 限定，
     * 不依赖前端传参，避免越权查看他人客户。
     * </p>
     *
     * @param keyword 名称/信用代码关键字（可空）
     * @return 主体列表
     */
    @GetMapping("/mine")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<List<ScrmCompanyDto>> myCompanies(
            @RequestParam(required = false) String keyword) {
        return OperationResponse.build(companyService.myCompanies(keyword));
    }

    /**
     * 会计工作量概览（主管视角：每个会计负责多少户、还有多少未指派）。
     *
     * @return 各会计负责户数
     */
    @GetMapping("/accountants/workload")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<List<Map<String, Object>>> accountantWorkload() {
        return OperationResponse.build(companyService.accountantWorkload());
    }

    /**
     * 查询主体详情（含联系人列表与数量）。
     *
     * @param id 主体 ID
     * @return 主体详情
     * @throws ScrmException 主体不存在
     */
    @GetMapping("/{id}")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<ScrmCompanyDto> get(@PathVariable Long id) throws ScrmException {
        return OperationResponse.build(companyService.get(id));
    }

    /**
     * 分页查询主体。
     *
     * @param keyword        关键词（企业名称/信用代码，可空）
     * @param serviceStatus  服务状态（可空）
     * @param businessStatus 工商状态（可空）
     * @param page           页码（0 起）
     * @param size           每页条数
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<Page<ScrmCompanyDto>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String serviceStatus,
            @RequestParam(required = false) String businessStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(
                companyService.list(keyword, serviceStatus, businessStatus, page, size));
    }

    /**
     * 公司 360 概览（主体 + 联系人分布 + 业务计数 + 风险提示）。
     *
     * @param id 主体 ID
     * @return 概览数据
     * @throws ScrmException 主体不存在
     */
    @GetMapping("/{id}/overview")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<Map<String, Object>> overview(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(overviewService.overview(id));
    }

    /**
     * 删除公司主体（仅当无联系人挂靠）。
     *
     * @param id 主体 ID
     * @return 空响应
     * @throws ScrmException 主体不存在 / 仍有联系人挂靠
     */
    @DeleteMapping("/{id}")
    @RequirePermission(resource = "scrm_company", action = "delete")
    public OperationResponse<Void> delete(@PathVariable Long id) throws ScrmException {
        companyService.delete(id);
        return OperationResponse.build(null);
    }

    /**
     * 查询公司下的联系人列表。
     *
     * @param id 主体 ID
     * @return 联系人列表
     * @throws ScrmException 主体不存在
     */
    @GetMapping("/{id}/contacts")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<List<ScrmCustomerDto>> listContacts(@PathVariable Long id)
            throws ScrmException {
        return OperationResponse.build(companyService.listContacts(id));
    }

    /**
     * 联系人挂靠到公司（一个联系人只属一家公司）。
     *
     * @param id          主体 ID
     * @param customerId  联系人 ID
     * @param contactRole 公司内角色（BOSS/FINANCE/CASHIER/ADMIN/AGENT/OTHER，可空）
     * @return 挂靠后的联系人
     * @throws ScrmException 主体/联系人不存在 / 角色非法 / 跨归属
     */
    @PostMapping("/{id}/contacts")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<ScrmCustomerDto> bindContact(
            @PathVariable Long id,
            @RequestParam Long customerId,
            @RequestParam(required = false) String contactRole) throws ScrmException {
        return OperationResponse.build(companyService.bindContact(id, customerId, contactRole));
    }

    /**
     * 解除联系人归属。
     *
     * @param customerId 联系人 ID
     * @return 解除后的联系人
     * @throws ScrmException 联系人不存在
     */
    @DeleteMapping("/contacts/{customerId}")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<ScrmCustomerDto> unbindContact(@PathVariable Long customerId)
            throws ScrmException {
        return OperationResponse.build(companyService.unbindContact(customerId));
    }

    /**
     * 从证照识别任务创建主体（营业执照识别 → 主体建档 → 联系人挂靠 闭环）。
     *
     * @param taskId 证照识别任务 ID
     * @return 创建（或复用）的主体
     * @throws ScrmException 任务不存在 / 非营业执照 / 字段不足
     */
    @PostMapping("/from-license-task/{taskId}")
    @RequirePermission(resource = "scrm_company", action = "create")
    public OperationResponse<ScrmCompanyDto> createFromLicenseTask(@PathVariable Long taskId)
            throws ScrmException {
        return OperationResponse.build(companyService.createFromLicenseTask(taskId));
    }

    // ============================================================
    // 存量归并（待归并清单 + 候选建议 + 批量挂靠）
    // ============================================================

    /**
     * 待归并清单：尚未归属任何公司的联系人，附可用信号摘要。
     *
     * @param keyword 昵称关键词（可空）
     * @param page    页码（0 起）
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/unlinked-contacts")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<Page<Map<String, Object>>> listUnlinkedContacts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return OperationResponse.build(mergeService.listUnlinked(keyword, page, size));
    }

    /**
     * 为公司推荐候选联系人（昵称命中公司名关键词；无命中返回空，不编造）。
     *
     * @param id   公司主体 ID
     * @param size 最多返回条数
     * @return 候选列表
     * @throws ScrmException 公司不存在
     */
    @GetMapping("/{id}/suggest-contacts")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<List<Map<String, Object>>> suggestContacts(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        return OperationResponse.build(mergeService.suggestContacts(id, size));
    }

    /**
     * 为联系人推荐候选公司（联系人昵称关键词命中公司名）。
     *
     * @param customerId 联系人 ID
     * @param size       最多返回条数
     * @return 候选公司列表
     * @throws ScrmException 联系人不存在
     */
    @GetMapping("/suggest-companies")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<List<Map<String, Object>>> suggestCompanies(
            @RequestParam Long customerId,
            @RequestParam(defaultValue = "20") int size) throws ScrmException {
        return OperationResponse.build(mergeService.suggestCompanies(customerId, size));
    }

    /**
     * 批量挂靠：一次把多个联系人挂到同一公司（单事务）。
     *
     * @param id          公司主体 ID
     * @param customerIds 联系人 ID 列表
     * @param contactRole 公司内角色（可空）
     * @return 挂靠结果（成功数 + 跳过明细）
     * @throws ScrmException 公司不存在 / 入参为空
     */
    @PostMapping("/{id}/contacts/bulk")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<Map<String, Object>> bulkBind(
            @PathVariable Long id,
            @RequestBody List<Long> customerIds,
            @RequestParam(required = false) String contactRole) throws ScrmException {
        return OperationResponse.build(mergeService.bulkBind(id, customerIds, contactRole));
    }

    // ============================================================
    // 资料补录（手机号批量回写 + 按手机号挂靠）
    // ============================================================

    /**
     * 批量补录手机号（存量资料补录入口）。
     *
     * @param items 补录项（联系人 ID + 手机号）
     * @return 补录结果（成功数 + 跳过原因）
     * @throws ScrmException 入参为空
     */
    @PostMapping("/backfill/phones")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<Map<String, Object>> backfillPhones(
            @RequestBody List<ScrmCustomerBackfillService.PhoneBackfillItem> items)
            throws ScrmException {
        return OperationResponse.build(backfillService.backfillPhones(items));
    }

    /**
     * 按「手机号 → 公司」批量挂靠：按手机号定位联系人，补录手机号并挂到该公司。
     *
     * @param id          目标公司主体 ID
     * @param items       补录项（手机号 / 可选联系人 ID）
     * @param contactRole 公司内角色（可空）
     * @return 挂靠结果（成功数 + 补录手机号数 + 跳过原因）
     * @throws ScrmException 公司不存在 / 入参为空
     */
    @PostMapping("/{id}/contacts/by-phones")
    @RequirePermission(resource = "scrm_company", action = "update")
    public OperationResponse<Map<String, Object>> bindByPhones(
            @PathVariable Long id,
            @RequestBody List<ScrmCustomerBackfillService.PhoneBackfillItem> items,
            @RequestParam(required = false) String contactRole) throws ScrmException {
        return OperationResponse.build(backfillService.bindByPhones(id, items, contactRole));
    }

    /**
     * 补录完成度统计（还差多少手机号 / 还有多少未归属）。
     *
     * @return 统计结果
     */
    @GetMapping("/backfill/status")
    @RequirePermission(resource = "scrm_company", action = "read")
    public OperationResponse<Map<String, Object>> backfillStatus() {
        return OperationResponse.build(backfillService.backfillStatus());
    }
}
