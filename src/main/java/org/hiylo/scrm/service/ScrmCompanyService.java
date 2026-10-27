/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompanyService.java
 * Date : 2026/10/23 16:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmCompanyDto;
import org.hiylo.scrm.dto.ScrmCustomerDto;
import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.dto.ocr.OcrField;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLicenseOcrTaskRepository;
import org.hiylo.scrm.service.finance.FinanceDataScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 公司主体服务（财税业务主键）。
 * <p>
 * 承接「公司 = 服务主体、联系人 = 沟通入口」的建模：一个公司下可挂多个联系人
 * （老板 / 多名财务 / 纯手机号或线下联系人），财税业务数据以 {@code companyId} 归属。
 * </p>
 * <ul>
 *   <li><b>主体 CRUD</b>：创建/更新/查询/删除，统一社会信用代码在同一归属内唯一</li>
 *   <li><b>联系人归属</b>：把联系人挂到公司（设置 companyId + 公司内角色），或解除归属</li>
 *   <li><b>证照反哺</b>：从证照 OCR 任务直接建主体并回填字段（{@link #createFromLicenseTask}）</li>
 *   <li><b>公司 360</b>：详情返回主体档案 + 联系人列表 + 联系人数量</li>
 * </ul>
 * <p>
 * <b>数据隔离</b>：所有读写按 {@code ownerAccountId} 过滤；创建时若入参未带归属，
 * 从登录上下文（{@link UserContext}）解析，解析不到则拒绝（fail-closed）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCompanyService {

    /** 合法服务状态 */
    private static final List<String> VALID_SERVICE_STATUS = List.of(
            ScrmCompanyEntity.SERVICE_PENDING, ScrmCompanyEntity.SERVICE_SERVING,
            ScrmCompanyEntity.SERVICE_SUSPENDED, ScrmCompanyEntity.SERVICE_TERMINATED);

    /** 合法主体类型 */
    private static final List<String> VALID_COMPANY_TYPES = List.of(
            ScrmCompanyEntity.TYPE_ENTERPRISE, ScrmCompanyEntity.TYPE_INDIVIDUAL_BUSINESS,
            ScrmCompanyEntity.TYPE_SOLE_PROPRIETORSHIP, ScrmCompanyEntity.TYPE_INDIVIDUAL);

    /** 合法公司内角色 */
    private static final List<String> VALID_CONTACT_ROLES = List.of(
            "BOSS", "FINANCE", "CASHIER", "ADMIN", "AGENT", "OTHER");

    /** 公司主体数据访问层 */
    private final ScrmCompanyRepository companyRepository;

    /** 联系人数据访问层 */
    private final ScrmCustomerRepository customerRepository;

    /** 证照识别任务数据访问层（证照反哺建主体） */
    private final ScrmLicenseOcrTaskRepository licenseOcrTaskRepository;

    /** JSON 解析器（解析证照识别结果字段） */
    private final ObjectMapper objectMapper;

    /** 数据权限服务（与客户列表同口径解析可见账号集合） */
    private final DataScopeService dataScopeService;

    /** 财税域数据范围（按负责会计隔离公司主体，会计只看自己负责的主体） */
    private final FinanceDataScope financeDataScope;

    /** 用户数据访问层（工作量视图解析会计姓名） */
    private final org.hiylo.scrm.repository.ScrmUserRepository userRepository;

    // ============================================================
    // 主体 CRUD
    // ============================================================

    /**
     * 创建公司主体。
     *
     * @param dto 主体参数
     * @return 创建后的主体
     * @throws ScrmException 参数非法 / 信用代码重复 / 归属无法确定
     */
    @Transactional
    public ScrmCompanyDto create(ScrmCompanyDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("公司主体参数不能为空");
        }
        if (dto.getCompanyName() == null || dto.getCompanyName().isBlank()) {
            throw ScrmException.badRequest("企业名称不能为空");
        }
        if (dto.getCompanyType() != null && !VALID_COMPANY_TYPES.contains(dto.getCompanyType())) {
            throw ScrmException.badRequest("非法的主体类型: " + dto.getCompanyType());
        }
        if (dto.getServiceStatus() != null && !VALID_SERVICE_STATUS.contains(dto.getServiceStatus())) {
            throw ScrmException.badRequest("非法的服务状态: " + dto.getServiceStatus());
        }
        // 归属账号与 scrm_customer.owner_account_id 同语义（scrm_account.id），必须显式指定：
        // 登录上下文里的 userId 是用户 ID、不是账号 ID，不能用来自行推导（否则主体与联系人
        // 归属空间不一致，挂靠时会被跨归属校验拦下）
        Long ownerAccountId = dto.getOwnerAccountId();
        if (ownerAccountId == null) {
            throw ScrmException.badRequest("归属账号 ID 不能为空");
        }
        String creditCode = normalizeCreditCode(dto.getCreditCode());
        if (creditCode != null
                && companyRepository.existsByOwnerAccountIdAndCreditCode(ownerAccountId, creditCode)) {
            throw ScrmException.conflict("主体已存在: creditCode=" + creditCode);
        }

        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setOwnerAccountId(ownerAccountId);
        applyFields(entity, dto);
        entity.setCreditCode(creditCode);
        entity.setCreatedBy(UserContext.getUsername());
        entity = companyRepository.save(entity);
        log.info("[公司主体] 创建: id={}, name={}, creditCode={}, owner={}",
                entity.getId(), entity.getCompanyName(), entity.getCreditCode(), ownerAccountId);
        return toDto(entity, false);
    }

    /**
     * 批量指派负责会计（换会计、初始分派的主要操作）。
     * <p>
     * 代账公司一次接手几十上百户，逐家点「指派会计」不现实。批量指派是
     * 多会计协作的入口动作，因此单独提供，并返回逐条结果便于核对失败项。
     * </p>
     *
     * @param companyIds     公司主体 ID 列表
     * @param accountantId   负责会计用户 ID（null/0 = 取消指派）
     * @param accountantName 负责会计姓名（冗余展示）
     * @return 指派结果：成功数 + 失败明细
     */
    @Transactional
    public Map<String, Object> batchAssignAccountant(List<Long> companyIds, Long accountantId,
                                                     String accountantName) {
        if (companyIds == null || companyIds.isEmpty()) {
            return Map.of("requested", 0, "assigned", 0, "failed", List.of());
        }
        boolean cleared = accountantId == null || accountantId <= 0;
        String name = cleared || accountantName == null || accountantName.isBlank()
                ? null : accountantName.trim();
        List<Map<String, Object>> failed = new ArrayList<>();
        int assigned = 0;
        for (Long id : companyIds) {
            var optional = companyRepository.findById(id);
            if (optional.isEmpty()) {
                failed.add(Map.of("companyId", id, "reason", "公司主体不存在"));
                continue;
            }
            ScrmCompanyEntity entity = optional.get();
            entity.setAccountantId(cleared ? null : accountantId);
            entity.setAccountantName(name);
            companyRepository.save(entity);
            assigned++;
        }
        log.info("[公司主体-负责会计] 批量指派: requested={}, assigned={}, accountantId={}",
                companyIds.size(), assigned, cleared ? null : accountantId);
        return Map.of("requested", companyIds.size(), "assigned", assigned, "failed", failed);
    }

    /**
     * 我的客户（当前登录会计负责的主体列表）。
     * <p>
     * 会计登录后的默认工作台数据源。当前登录用户未绑定任何主体时返回空列表，
     * 而不是回退成全量——「看不到自己的客户」比「误看到别人的客户」安全。
     * </p>
     *
     * @param keyword 名称/信用代码关键字（可空）
     * @return 主体列表（按创建时间倒序）
     */
    @Transactional(readOnly = true)
    public List<ScrmCompanyDto> myCompanies(String keyword) {
        String userId = dataScopeService.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        Long accountantId;
        try {
            accountantId = Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            log.warn("[公司主体-我的客户] 当前用户 ID 非法: {}", userId);
            return List.of();
        }
        return companyRepository.findByAccountantIdOrderByCreateTimeDesc(accountantId).stream()
                .filter(e -> keyword == null || keyword.isBlank()
                        || (e.getCompanyName() != null
                        && e.getCompanyName().toLowerCase().contains(keyword.trim().toLowerCase())))
                .map(e -> toDto(e, false))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 更新公司主体。
     *
     * @param id  主体 ID
    /**
     * 会计工作量概览（主管视角：每个会计负责多少户）。
     * <p>
     * 未指派的主体单独统计——这个数字直接反映「还有多少户没分派」，
     * 是多会计落地进度的关键指标。
     * </p>
     *
     * @return 各会计负责户数 + 未指派户数
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> accountantWorkload() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : companyRepository.countGroupedByAccountant()) {
            Long accountantId = (Long) row[0];
            long count = ((Number) row[1]).longValue();
            // 姓名从用户表取：会计可能尚未绑定任何公司，公司表里取不到冗余姓名
            String name = userRepository.findById(accountantId)
                    .map(org.hiylo.scrm.entity.ScrmUserEntity::getDisplayName)
                    .filter(n -> n != null && !n.isBlank())
                    .or(() -> userRepository.findById(accountantId)
                            .map(org.hiylo.scrm.entity.ScrmUserEntity::getUsername))
                    .orElse(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("accountantId", String.valueOf(accountantId));
            item.put("accountantName", name != null ? name : ("用户#" + accountantId));
            item.put("companyCount", count);
            result.add(item);
        }
        result.sort((a, b) -> Long.compare(
                ((Number) b.get("companyCount")).longValue(),
                ((Number) a.get("companyCount")).longValue()));
        long unassigned = companyRepository.count() - result.stream()
                .mapToLong(m -> ((Number) m.get("companyCount")).longValue()).sum();
        Map<String, Object> none = new LinkedHashMap<>();
        none.put("accountantId", null);
        none.put("accountantName", "未指派");
        none.put("companyCount", unassigned);
        result.add(none);
        return result;
    }

    /**
     * 更新公司主体。
     *
     * @param id  主体 ID
    /**
     * 指派/取消负责会计。
     * <p>
     * 代账公司多会计协作的核心动作。{@code accountantId} 为 {@code null} 或 {@code 0}
     * 表示取消指派——「0 表示清空」是刻意的：该接口只改这一个字段，
     * 需要能区分「不动」与「清空」两种语义。
     * </p>
     *
     * @param id             公司主体 ID
     * @param accountantId   负责会计的用户 ID（null/0 = 取消指派）
     * @param accountantName 负责会计姓名（冗余展示，可空）
     * @return 更新后的主体
     * @throws ScrmException 主体不存在
     */
    @Transactional
    public ScrmCompanyDto assignAccountant(Long id, Long accountantId, String accountantName)
            throws ScrmException {
        ScrmCompanyEntity entity = findOrThrow(id);
        boolean cleared = accountantId == null || accountantId <= 0;
        entity.setAccountantId(cleared ? null : accountantId);
        entity.setAccountantName(cleared || accountantName == null || accountantName.isBlank()
                ? null : accountantName.trim());
        entity = companyRepository.save(entity);
        log.info("[公司主体-负责会计] 指派: id={}, accountantId={}", id, entity.getAccountantId());
        return toDto(entity, false);
    }

    /**
     * 更新公司主体。
     *
     * @param id  主体 ID
     * @param dto 更新参数（null 字段不覆盖）
     * @return 更新后的主体
     * @throws ScrmException 主体不存在 / 参数非法 / 信用代码冲突
     */
    @Transactional
    public ScrmCompanyDto update(Long id, ScrmCompanyDto dto) throws ScrmException {
        ScrmCompanyEntity entity = findOrThrow(id);
        if (dto.getCompanyType() != null && !VALID_COMPANY_TYPES.contains(dto.getCompanyType())) {
            throw ScrmException.badRequest("非法的主体类型: " + dto.getCompanyType());
        }
        if (dto.getServiceStatus() != null && !VALID_SERVICE_STATUS.contains(dto.getServiceStatus())) {
            throw ScrmException.badRequest("非法的服务状态: " + dto.getServiceStatus());
        }
        if (dto.getCreditCode() != null) {
            String creditCode = normalizeCreditCode(dto.getCreditCode());
            if (creditCode != null
                    && !creditCode.equals(entity.getCreditCode())
                    && companyRepository.existsByOwnerAccountIdAndCreditCode(
                            entity.getOwnerAccountId(), creditCode)) {
                throw ScrmException.conflict("信用代码已被其他主体占用: " + creditCode);
            }
            entity.setCreditCode(creditCode);
        }
        // 工商状态变化时刷新变更时间（巡检比对依赖该字段）
        if (dto.getBusinessStatus() != null
                && !dto.getBusinessStatus().equals(entity.getBusinessStatus())) {
            entity.setBusinessStatusChangedAt(java.time.LocalDateTime.now());
        }
        applyFields(entity, dto);
        entity = companyRepository.save(entity);
        return toDto(entity, false);
    }

    /**
     * 查询主体详情（含联系人列表与数量）。
     *
     * @param id 主体 ID
     * @return 主体详情
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public ScrmCompanyDto get(Long id) throws ScrmException {
        ScrmCompanyEntity entity = findOrThrow(id);
        // 越权防护：会计只能看自己负责的主体，避免直接拼 ID 读取他人数据
        if (!financeDataScope.canAccessCompany(entity.getId())) {
            throw ScrmException.forbidden("无权访问该主体: " + id);
        }
        ScrmCompanyDto dto = toDto(entity, true);
        List<ScrmCustomerEntity> contacts = customerRepository
                .findByOwnerAccountIdAndCompanyId(entity.getOwnerAccountId(), id);
        dto.setContacts(contacts.stream().map(this::toCustomerDto).toList());
        dto.setContactCount((long) contacts.size());
        return dto;
    }

    /**
     * 分页查询主体（按名称/信用代码模糊 + 服务状态/工商状态过滤）。
     *
     * @param keyword       关键词（企业名称或信用代码，可空）
     * @param serviceStatus 服务状态（可空）
     * @param businessStatus 工商状态（可空）
     * @param page          页码（0 起）
     * @param size          每页条数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompanyDto> list(String keyword, String serviceStatus, String businessStatus,
                                     int page, int size) {
        // 与客户列表同口径：scrm_company.owner_account_id 指向 scrm_account.id（账号 ID），
        // 而登录用户 ID 是另一个空间。直接比较会导致「公司明明存在却查不到」。
        List<Long> accessibleAccountIds = dataScopeService.getAccessibleAccountIds(
                dataScopeService.getCurrentUserId(),
                dataScopeService.getCurrentRole(),
                dataScopeService.getCurrentDepartmentId());
        // 财税数据范围：会计只看自己负责的主体（按 accountant_id 隔离）。
        // 与上面的账号归属是两套 ID 空间，必须分别处理：
        // 会计分支直接用负责会计过滤，不再叠加 owner_account_id 条件。
        List<Long> accessibleCompanyIds = financeDataScope.resolveAccessibleCompanyIds();
        Specification<ScrmCompanyEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleCompanyIds != null) {
                if (accessibleCompanyIds.isEmpty()) {
                    // 会计未绑定任何主体：必须返回空，绝不回退全量
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("id").in(accessibleCompanyIds));
                }
            } else if (accessibleAccountIds != null) {
                if (accessibleAccountIds.isEmpty()) {
                    predicates.add(cb.isFalse(cb.literal(true)));
                } else {
                    predicates.add(root.get("ownerAccountId").in(accessibleAccountIds));
                }
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("companyName")), like),
                        cb.like(cb.lower(root.get("creditCode")), like)));
            }
            if (serviceStatus != null && !serviceStatus.isBlank()) {
                predicates.add(cb.equal(root.get("serviceStatus"), serviceStatus));
            }
            if (businessStatus != null && !businessStatus.isBlank()) {
                predicates.add(cb.equal(root.get("businessStatus"), businessStatus));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return companyRepository.findAll(spec, pageable).map(entity -> {
            ScrmCompanyDto dto = toDto(entity, false);
            dto.setContactCount(customerRepository.countByCompanyId(entity.getId()));
            return dto;
        });
    }

    /**
     * 删除主体（仅当无联系人挂靠时允许，避免联系人变成孤儿）。
     *
     * @param id 主体 ID
     * @throws ScrmException 主体不存在 / 仍有联系人挂靠
     */
    @Transactional
    public void delete(Long id) throws ScrmException {
        ScrmCompanyEntity entity = findOrThrow(id);
        long contacts = customerRepository.countByCompanyId(id);
        if (contacts > 0) {
            throw ScrmException.badRequest("该公司下仍有 " + contacts
                    + " 个联系人，请先解除归属或转移后再删除");
        }
        companyRepository.delete(entity);
        log.info("[公司主体] 删除: id={}, name={}", id, entity.getCompanyName());
    }

    // ============================================================
    // 联系人归属
    // ============================================================

    /**
     * 把联系人挂到公司主体（一个联系人只属一家公司，重复挂靠会被覆盖）。
     *
     * @param companyId   主体 ID
     * @param customerId  联系人 ID
     * @param contactRole 公司内角色（可空）
     * @return 挂靠后的联系人
     * @throws ScrmException 主体/联系人不存在 / 角色非法 / 跨归属
     */
    @Transactional
    public ScrmCustomerDto bindContact(Long companyId, Long customerId, String contactRole)
            throws ScrmException {
        if (contactRole != null && !contactRole.isBlank()
                && !VALID_CONTACT_ROLES.contains(contactRole)) {
            throw ScrmException.badRequest("非法的公司内角色: " + contactRole
                    + "，仅支持 " + VALID_CONTACT_ROLES);
        }
        ScrmCompanyEntity company = findOrThrow(companyId);
        ScrmCustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ScrmException.notFound("联系人不存在: " + customerId));
        if (!company.getOwnerAccountId().equals(customer.getOwnerAccountId())) {
            throw ScrmException.forbidden("联系人与主体不属于同一归属账号");
        }
        customer.setCompanyId(companyId);
        if (contactRole != null && !contactRole.isBlank()) {
            customer.setContactRole(contactRole);
        }
        customer = customerRepository.save(customer);
        log.info("[公司主体] 联系人挂靠: companyId={}, customerId={}, role={}",
                companyId, customerId, customer.getContactRole());
        return toCustomerDto(customer);
    }

    /**
     * 解除联系人与公司的归属关系。
     *
     * @param customerId 联系人 ID
     * @return 解除后的联系人
     * @throws ScrmException 联系人不存在
     */
    @Transactional
    public ScrmCustomerDto unbindContact(Long customerId) throws ScrmException {
        ScrmCustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> ScrmException.notFound("联系人不存在: " + customerId));
        customer.setCompanyId(null);
        customer.setContactRole(null);
        customer = customerRepository.save(customer);
        log.info("[公司主体] 联系人解除挂靠: customerId={}", customerId);
        return toCustomerDto(customer);
    }

    /**
     * 查询公司下的联系人列表。
     *
     * @param companyId 主体 ID
     * @return 联系人列表
     * @throws ScrmException 主体不存在
     */
    @Transactional(readOnly = true)
    public List<ScrmCustomerDto> listContacts(Long companyId) throws ScrmException {
        ScrmCompanyEntity company = findOrThrow(companyId);
        return customerRepository
                .findByOwnerAccountIdAndCompanyId(company.getOwnerAccountId(), companyId)
                .stream().map(this::toCustomerDto).toList();
    }

    // ============================================================
    // 证照反哺：从 OCR 任务建主体
    // ============================================================

    /**
     * 从证照识别任务创建公司主体并回填字段（营业执照识别成功后的闭环）。
     * <p>
     * 读取任务的 {@code fieldsJson}（营业执照 9 字段），映射为企业名称/信用代码/
     * 法人/注册资本/成立日期/住所，来源标记 {@code OCR}、{@code verified=true}；
     * 若任务关联了联系人，则同时把该联系人挂到新主体上，并回写任务的 companyId。
     * </p>
     *
     * @param taskId 证照识别任务 ID
     * @return 创建后的主体
     * @throws ScrmException 任务不存在 / 非营业执照 / 字段不足 / 信用代码已存在
     */
    @Transactional
    public ScrmCompanyDto createFromLicenseTask(Long taskId) throws ScrmException {
        ScrmLicenseOcrTaskEntity task = licenseOcrTaskRepository.findById(taskId)
                .orElseThrow(() -> ScrmException.notFound("证照识别任务不存在: " + taskId));
        if (!"license".equals(task.getLicenseType())) {
            throw ScrmException.badRequest("仅营业执照识别任务可创建主体，当前类型: "
                    + task.getLicenseType());
        }
        Map<String, String> fields = parseFields(task.getFieldsJson());
        String companyName = fields.get("企业名称");
        if (companyName == null || companyName.isBlank()) {
            throw ScrmException.badRequest("识别结果缺少企业名称，无法创建主体（请人工复核后录入）");
        }
        // 归属账号取自任务关联联系人（联系人的 owner_account_id 才是账号 ID）；
        // 任务自身的 owner_account_id 记录的是操作人用户 ID，语义不同，不能直接用
        Long ownerAccountId = task.getCustomerId() == null ? null
                : customerRepository.findById(task.getCustomerId())
                        .map(ScrmCustomerEntity::getOwnerAccountId).orElse(null);
        if (ownerAccountId == null) {
            throw ScrmException.badRequest(
                    "证照任务未关联联系人，无法确定归属账号；请先给任务关联联系人后再建主体");
        }
        String creditCode = normalizeCreditCode(fields.get("统一社会信用代码"));
        Optional<ScrmCompanyEntity> existing = creditCode == null
                ? Optional.empty()
                : companyRepository.findByOwnerAccountIdAndCreditCode(ownerAccountId, creditCode);
        if (existing.isPresent()) {
            // 已存在则直接复用并挂联系人（幂等，避免重复建主体）
            ScrmCompanyEntity company = existing.get();
            linkTaskAndContact(task, company);
            log.info("[公司主体] 证照反哺命中已存在主体: taskId={}, companyId={}", taskId, company.getId());
            return toDto(company, false);
        }

        ScrmCompanyEntity entity = new ScrmCompanyEntity();
        entity.setOwnerAccountId(ownerAccountId);
        entity.setCompanyName(companyName.trim());
        entity.setCreditCode(creditCode);
        entity.setLegalRepresentative(trimToNull(fields.get("法定代表人")));
        entity.setRegisteredCapital(trimToNull(fields.get("注册资本")));
        entity.setRegisteredAddress(trimToNull(fields.get("住所")));
        entity.setEstablishDate(parseCnDate(fields.get("成立日期")));
        entity.setSource(ScrmCompanyEntity.SOURCE_OCR);
        entity.setSourceRefId(taskId);
        entity.setVerified(Boolean.TRUE.equals(task.getOverallOk()));
        entity.setVerifiedAt(entity.getVerified() ? java.time.LocalDateTime.now() : null);
        entity.setCreatedBy(UserContext.getUsername());
        entity = companyRepository.save(entity);
        linkTaskAndContact(task, entity);
        log.info("[公司主体] 证照反哺建主体: taskId={}, companyId={}, name={}",
                taskId, entity.getId(), entity.getCompanyName());
        return toDto(entity, false);
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 把证照任务与其中的联系人挂到主体上（并回写任务 companyId）。
     *
     * @param task    证照任务
     * @param company 主体
     */
    private void linkTaskAndContact(ScrmLicenseOcrTaskEntity task, ScrmCompanyEntity company) {
        task.setCompanyId(company.getId());
        licenseOcrTaskRepository.save(task);
        if (task.getCustomerId() != null) {
            customerRepository.findById(task.getCustomerId()).ifPresent(customer -> {
                customer.setCompanyId(company.getId());
                if (customer.getContactRole() == null) {
                    customer.setContactRole("BOSS");
                }
                customerRepository.save(customer);
            });
        }
    }

    /**
     * 把 DTO 的非空字段写入实体（更新语义：null 不覆盖）。
     *
     * @param entity 目标实体
     * @param dto    参数
     */
    private void applyFields(ScrmCompanyEntity entity, ScrmCompanyDto dto) {
        if (dto.getCompanyName() != null) entity.setCompanyName(dto.getCompanyName().trim());
        if (dto.getCompanyType() != null) entity.setCompanyType(dto.getCompanyType());
        if (dto.getLegalRepresentative() != null) entity.setLegalRepresentative(dto.getLegalRepresentative());
        if (dto.getRegisteredCapital() != null) entity.setRegisteredCapital(dto.getRegisteredCapital());
        if (dto.getEstablishDate() != null) entity.setEstablishDate(dto.getEstablishDate());
        if (dto.getRegisteredAddress() != null) entity.setRegisteredAddress(dto.getRegisteredAddress());
        if (dto.getIndustry() != null) entity.setIndustry(dto.getIndustry());
        if (dto.getEmployeeScale() != null) entity.setEmployeeScale(dto.getEmployeeScale());
        if (dto.getBusinessStatus() != null) entity.setBusinessStatus(dto.getBusinessStatus());
        if (dto.getTaxStatus() != null) entity.setTaxStatus(dto.getTaxStatus());
        if (dto.getServiceType() != null) entity.setServiceType(dto.getServiceType());
        if (dto.getServiceStatus() != null) entity.setServiceStatus(dto.getServiceStatus());
        if (dto.getServiceStartDate() != null) entity.setServiceStartDate(dto.getServiceStartDate());
        if (dto.getServiceEndDate() != null) entity.setServiceEndDate(dto.getServiceEndDate());
        if (dto.getSource() != null) entity.setSource(dto.getSource());
        if (dto.getSourceRefId() != null) entity.setSourceRefId(dto.getSourceRefId());
        if (dto.getVerified() != null) {
            entity.setVerified(dto.getVerified());
            if (Boolean.TRUE.equals(dto.getVerified()) && entity.getVerifiedAt() == null) {
                entity.setVerifiedAt(java.time.LocalDateTime.now());
            }
        }
        // 负责会计：用 0 表示「取消指派」（null 表示不修改），否则无法区分「不改」与「清空」
        if (dto.getAccountantId() != null) {
            entity.setAccountantId(dto.getAccountantId() > 0 ? dto.getAccountantId() : null);
        }
        if (dto.getAccountantName() != null) {
            entity.setAccountantName(dto.getAccountantName().isBlank() ? null : dto.getAccountantName());
        }
        if (dto.getRemark() != null) entity.setRemark(dto.getRemark());
    }

    /**
     * 查询主体或抛 404。
     *
     * @param id 主体 ID
     * @return 主体
     * @throws ScrmException 主体不存在
     */
    private ScrmCompanyEntity findOrThrow(Long id) throws ScrmException {
        return companyRepository.findById(id)
                .orElseThrow(() -> ScrmException.notFound("公司主体不存在: " + id));
    }

    /**
     * 实体转 DTO。
     *
     * @param entity        实体
     * @param withContacts  是否携带联系人（详情才需要）
     * @return DTO
     */
    private ScrmCompanyDto toDto(ScrmCompanyEntity entity, boolean withContacts) {
        ScrmCompanyDto dto = new ScrmCompanyDto();
        dto.setId(entity.getId());
        dto.setOwnerAccountId(entity.getOwnerAccountId());
        dto.setCompanyName(entity.getCompanyName());
        dto.setCreditCode(entity.getCreditCode());
        dto.setCompanyType(entity.getCompanyType());
        dto.setLegalRepresentative(entity.getLegalRepresentative());
        dto.setRegisteredCapital(entity.getRegisteredCapital());
        dto.setEstablishDate(entity.getEstablishDate());
        dto.setRegisteredAddress(entity.getRegisteredAddress());
        dto.setIndustry(entity.getIndustry());
        dto.setEmployeeScale(entity.getEmployeeScale());
        dto.setBusinessStatus(entity.getBusinessStatus());
        dto.setTaxStatus(entity.getTaxStatus());
        dto.setBusinessStatusChangedAt(entity.getBusinessStatusChangedAt());
        dto.setServiceType(entity.getServiceType());
        dto.setServiceStatus(entity.getServiceStatus());
        dto.setServiceStartDate(entity.getServiceStartDate());
        dto.setServiceEndDate(entity.getServiceEndDate());
        dto.setSource(entity.getSource());
        dto.setSourceRefId(entity.getSourceRefId());
        dto.setVerified(entity.getVerified());
        dto.setVerifiedAt(entity.getVerifiedAt());
        dto.setAccountantId(entity.getAccountantId());
        dto.setAccountantName(entity.getAccountantName());
        dto.setRemark(entity.getRemark());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        if (!withContacts) {
            dto.setContacts(null);
        }
        return dto;
    }

    /**
     * 联系人实体转 DTO（仅暴露公司主体相关字段，避免循环依赖 CustomerService）。
     *
     * @param entity 联系人实体
     * @return 精简 DTO
     */
    private ScrmCustomerDto toCustomerDto(ScrmCustomerEntity entity) {
        ScrmCustomerDto dto = new ScrmCustomerDto();
        dto.setId(entity.getId());
        dto.setPlatformType(entity.getPlatformType());
        dto.setPlatformCustomerUid(entity.getPlatformCustomerUid());
        dto.setPhone(entity.getPhone());
        dto.setNickname(entity.getNickname());
        dto.setOwnerAccountId(entity.getOwnerAccountId());
        dto.setLifecycle(entity.getLifecycle());
        dto.setCompanyId(entity.getCompanyId());
        dto.setContactRole(entity.getContactRole());
        dto.setSourceChannel(entity.getSourceChannel());
        dto.setSourceChannelCodeId(entity.getSourceChannelCodeId());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    /**
     * 解析证照识别结果 JSON 为「中文字段名 → 值」映射。
     *
     * @param fieldsJson 识别任务 fields_json
     * @return 字段映射（解析失败返回空 Map）
     */
    private Map<String, String> parseFields(String fieldsJson) {
        Map<String, String> result = new HashMap<>();
        if (fieldsJson == null || fieldsJson.isBlank()) {
            return result;
        }
        try {
            List<OcrField> fields = objectMapper.readValue(
                    fieldsJson, new TypeReference<List<OcrField>>() { });
            for (OcrField field : fields) {
                if (field != null && field.getName() != null && field.getValue() != null
                        && !field.getValue().isBlank()) {
                    result.put(field.getName(), field.getValue());
                }
            }
        } catch (Exception e) {
            log.warn("[公司主体] 解析证照字段失败: err={}", e.getMessage());
        }
        return result;
    }

    /**
     * 统一社会信用代码归一化（去空格、转大写）。
     *
     * @param raw 原始值
     * @return 归一化值（空返回 null）
     */
    private String normalizeCreditCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.replaceAll("[\\s　]", "").toUpperCase(java.util.Locale.ROOT);
        return code.isBlank() ? null : code;
    }

    /**
     * 解析中文日期（YYYY年M月D日）。
     *
     * @param text 文本
     * @return 日期（解析失败返回 null）
     */
    private java.time.LocalDate parseCnDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日")
                .matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return java.time.LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 空值转 null（去首尾空白）。
     *
     * @param s 文本
     * @return 非空文本或 null
     */
    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
