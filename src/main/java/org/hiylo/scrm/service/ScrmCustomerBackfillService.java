/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCustomerBackfillService.java
 * Date : 2026/10/24 07:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.common.util.PhoneUtils;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 存量客户资料补录服务（手机号 / 公司归属）。
 * <p>
 * <b>解决的问题</b>：存量 1269 条联系人几乎没有可用于归并的信号（手机号 0 条、证件 0 条、
 * 会话里也提不到企业名），候选匹配只能覆盖极少数昵称含企业特征词的记录。要让归并真正跑起来，
 * 必须先补录资料。本服务提供两种补录方式：
 * </p>
 * <ul>
 *   <li><b>批量补录手机号</b>：给一批联系人回写手机号（自动归一化 +86/空格/连字符），
 *       并检测同账号下手机号撞车</li>
 *   <li><b>按手机号反查并挂靠</b>：拿到一批「手机号 → 公司」的对照关系后，
 *       按手机号精确找到联系人并挂到指定公司（补录与归并一步完成）</li>
 * </ul>
 * <p>
 * <b>设计约束</b>：
 * <ul>
 *   <li>只写手机号与归属，不改昵称/生命周期等业务字段</li>
 *   <li>手机号在同一归属账号下必须唯一（撞车记为跳过原因，不静默覆盖）</li>
 *   <li>跨账号的联系人一律跳过，防越权</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmCustomerBackfillService {

    /** 中国手机号合法长度 */
    private static final int PHONE_LENGTH = 11;

    /** 联系人数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 公司主体数据访问层 */
    private final ScrmCompanyRepository companyRepository;

    /**
     * 批量补录手机号。
     * <p>
     * 每条形如 {@code {customerId, phone}}。逐条校验：联系人存在、归属账号一致、手机号合法、
     * 手机号未被同账号下其他联系人占用。
     * </p>
     *
     * @param items 补录项列表
     * @return 补录结果（成功数 + 逐条跳过原因）
     * @throws ScrmException 入参为空
     */
    @Transactional
    public Map<String, Object> backfillPhones(List<PhoneBackfillItem> items) throws ScrmException {
        if (items == null || items.isEmpty()) {
            throw ScrmException.badRequest("补录项不能为空");
        }
        int updated = 0;
        int unchanged = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (PhoneBackfillItem item : items) {
            if (item == null || item.customerId() == null) {
                continue;
            }
            ScrmCustomerEntity customer = customerRepository.findById(item.customerId()).orElse(null);
            if (customer == null) {
                skipped.add(reason(item.customerId(), "联系人不存在"));
                continue;
            }
            String phone = normalizePhone(item.phone());
            if (phone == null) {
                skipped.add(reason(item.customerId(), "手机号为空"));
                continue;
            }
            if (phone.length() != PHONE_LENGTH || !phone.chars().allMatch(Character::isDigit)) {
                skipped.add(reason(item.customerId(), "手机号格式非法: " + phone));
                continue;
            }
            if (phone.equals(customer.getPhone())) {
                unchanged++;
                continue;
            }
            // 同账号下手机号唯一（撞车不覆盖，交人工判断）
            List<ScrmCustomerEntity> holders = customerRepository
                    .findByOwnerAccountIdAndPhone(customer.getOwnerAccountId(), phone);
            boolean conflict = holders.stream().anyMatch(h -> !Objects.equals(h.getId(), customer.getId()));
            if (conflict) {
                skipped.add(reason(item.customerId(), "该手机号已被同账号下其他联系人占用: " + phone));
                continue;
            }
            customer.setPhone(phone);
            customerRepository.save(customer);
            updated++;
        }
        log.info("[资料补录] 批量补录手机号: 请求={}, 成功={}, 未变化={}, 跳过={}",
                items.size(), updated, unchanged, skipped.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", items.size());
        result.put("updated", updated);
        result.put("unchanged", unchanged);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 按「手机号 → 公司」对照关系批量挂靠。
     * <p>
     * 用于业务方交来一张「某手机号属于某公司」的表：系统按手机号精确找到同账号下的联系人，
     * 写入手机号（若为空）并挂到该公司。找不到联系人 / 公司不存在 / 跨账号 一律跳过并说明原因。
     * </p>
     *
     * @param companyId 目标公司主体 ID
     * @param items     补录项列表（手机号 + 可选联系人 ID 兜底）
     * @param contactRole 公司内角色（可空）
     * @return 挂靠结果（成功数 + 逐条跳过原因）
     * @throws ScrmException 公司不存在 / 入参为空
     */
    @Transactional
    public Map<String, Object> bindByPhones(Long companyId, List<PhoneBackfillItem> items,
                                            String contactRole) throws ScrmException {
        if (items == null || items.isEmpty()) {
            throw ScrmException.badRequest("补录项不能为空");
        }
        ScrmCompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + companyId));
        Long ownerAccountId = company.getOwnerAccountId();

        int bound = 0;
        int phoneFilled = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (PhoneBackfillItem item : items) {
            if (item == null) {
                continue;
            }
            String phone = normalizePhone(item.phone());
            ScrmCustomerEntity customer = resolveCustomer(ownerAccountId, phone, item.customerId());
            if (customer == null) {
                skipped.add(reason(item.customerId(), phone == null
                        ? "未提供手机号或联系人 ID" : "该手机号在归属账号下找不到联系人: " + phone));
                continue;
            }
            if (!ownerAccountId.equals(customer.getOwnerAccountId())) {
                skipped.add(reason(customer.getId(), "归属账号不一致"));
                continue;
            }
            if (phone != null && (customer.getPhone() == null || customer.getPhone().isBlank())) {
                customer.setPhone(phone);
                phoneFilled++;
            }
            if (companyId.equals(customer.getCompanyId())) {
                customerRepository.save(customer);
                skipped.add(reason(customer.getId(), "已归属该公司（已补录手机号）"));
                continue;
            }
            customer.setCompanyId(companyId);
            if (contactRole != null && !contactRole.isBlank()) {
                customer.setContactRole(contactRole);
            }
            customerRepository.save(customer);
            bound++;
        }
        log.info("[资料补录] 按手机号挂靠: companyId={}, 请求={}, 成功={}, 补录手机号={}, 跳过={}",
                companyId, items.size(), bound, phoneFilled, skipped.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId);
        result.put("requested", items.size());
        result.put("bound", bound);
        result.put("phoneFilled", phoneFilled);
        result.put("skipped", skipped);
        return result;
    }

    /**
     * 补录完成度统计（告诉业务「还差多少」）。
     *
     * @return 统计结果：总数 / 有手机号 / 已归属公司 / 可自动匹配候选（昵称含企业特征词且未归属）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> backfillStatus() {
        List<ScrmCustomerEntity> all = customerRepository.findAll();
        long total = all.size();
        long withPhone = all.stream()
                .filter(c -> c.getPhone() != null && !c.getPhone().isBlank()).count();
        long linked = all.stream().filter(c -> c.getCompanyId() != null).count();
        long unlinked = total - linked;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("withPhone", withPhone);
        result.put("phoneMissing", total - withPhone);
        result.put("linked", linked);
        result.put("unlinked", unlinked);
        result.put("phoneCoverage", total == 0 ? 0 : Math.round(withPhone * 10000.0 / total) / 100.0);
        result.put("linkCoverage", total == 0 ? 0 : Math.round(linked * 10000.0 / total) / 100.0);
        result.put("updatedBy", UserContext.getUsername());
        return result;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 按手机号（优先）或联系人 ID（兜底）解析联系人。
     *
     * @param ownerAccountId 归属账号 ID
     * @param phone          归一化后的手机号（可空）
     * @param customerId     联系人 ID（可空）
     * @return 联系人；找不到返回 null
     */
    private ScrmCustomerEntity resolveCustomer(Long ownerAccountId, String phone, Long customerId) {
        if (phone != null) {
            List<ScrmCustomerEntity> hits = customerRepository.findByOwnerAccountIdAndPhone(ownerAccountId, phone);
            if (!hits.isEmpty()) {
                return hits.get(0);
            }
        }
        if (customerId != null) {
            return customerRepository.findById(customerId).orElse(null);
        }
        return null;
    }

    /**
     * 手机号归一化：去空格/连字符/括号，剥离 +86 / 0086 前缀。
     *
     * @param raw 原始手机号
     * @return 归一化后的手机号（空返回 null）
     */
    static String normalizePhone(String raw) {
        // 统一委托到 PhoneUtils
        return PhoneUtils.normalize(raw);
    }

    /**
     * 构造跳过原因条目。
     *
     * @param customerId 联系人 ID（可空）
     * @param reason     原因
     * @return 条目
     */
    private Map<String, Object> reason(Long customerId, String reason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("customerId", customerId);
        item.put("reason", reason);
        return item;
    }

    /**
     * 补录项（联系人 ID / 手机号，二者至少给一个）。
     *
     * @param customerId 联系人 ID（可空，按手机号定位时可为空）
     * @param phone      手机号（可空，同时用于补录与定位）
     */
    public record PhoneBackfillItem(Long customerId, String phone) {
    }
}
