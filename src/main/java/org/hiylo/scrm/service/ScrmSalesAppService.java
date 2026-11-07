/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmSalesAppService.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.common.util.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.dto.ScrmCallReportDto;
import org.hiylo.scrm.entity.ScrmCallRecordEntity;
import org.hiylo.scrm.entity.ScrmCustomerContactEntity;
import org.hiylo.scrm.entity.ScrmCustomerEntity;
import org.hiylo.scrm.entity.ScrmPublicSeaCustomerEntity;
import org.hiylo.scrm.entity.ScrmRecordingEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCallRecordRepository;
import org.hiylo.scrm.repository.ScrmCustomerContactRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmPublicSeaCustomerRepository;
import org.hiylo.scrm.repository.ScrmRecordingRepository;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageException;
import org.hiylo.scrm.storage.StorageProperties;
import org.hiylo.scrm.vo.ScrmCallReportResultVo;
import org.hiylo.scrm.vo.ScrmRecordingCapabilityVo;
import org.hiylo.scrm.vo.ScrmRecordingCoverageVo;
import org.hiylo.scrm.vo.ScrmRecordingUploadResultVo;
import org.hiylo.scrm.vo.ScrmSalesCustomerVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 销售端 App 外呼域服务。
 * <p>
 * 承载销售 Android App 的核心业务：客户（含联系方式）下发、通话记录幂等上报、
 * 录音文件上传与覆盖率统计、录音能力操作指引。
 * </p>
 * <p>
 * <b>客户数据源（重要）</b>：销售 App 的「我的客户」以<b>公海线索</b>为主
 * （{@code scrm_public_sea_customer.assigned_to = 当前用户}），因为 SCRM 的
 * 「分配客户给销售」正是通过公海领取/分配实现的。同时保留按运营账号归属的
 * 正式客户查询（{@code scope=account}），供管理员或历史数据使用。
 * </p>
 * <p>
 * 归属模型统一为二元组 {@code (ownerType, ownerId)}：
 * <ul>
 *   <li>{@code CUSTOMER}   - 正式客户，ownerId 指向 scrm_customer.id</li>
 *   <li>{@code PUBLIC_SEA} - 公海线索，ownerId 指向 scrm_public_sea_customer.id</li>
 * </ul>
 * 联系方式、通话记录、录音均按该二元组归属，避免两类 ID 空间冲突。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmSalesAppService {

    /** 归属类型：正式客户 */
    public static final String OWNER_CUSTOMER = "CUSTOMER";

    /** 归属类型：公海线索 */
    public static final String OWNER_PUBLIC_SEA = "PUBLIC_SEA";

    /** 公海状态：已分配（销售可跟进） */
    private static final String SEA_STATUS_ASSIGNED = "ASSIGNED";

    /** 公海状态：已锁定（跟进中） */
    private static final String SEA_STATUS_LOCKED = "LOCKED";

    /** 近似去重时间窗口（分钟）：无 callLogId 时同号码在该窗口内视为同一通 */
    private static final long DEDUP_WINDOW_MINUTES = 5L;

    /** 录音对象 key 前缀（独立命名空间，避免与会话媒体混用） */
    private static final String RECORDING_KEY_PREFIX = "scrm/recording/";

    /** 录音对象 key 年月目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 允许的通话方向 */
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("OUT", "IN", "MISSED", "REJECTED");

    /** 允许的通话来源 */
    private static final Set<String> ALLOWED_CALL_SOURCES = Set.of("APP_CALL", "CALL_LOG", "SCREENING", "MANUAL");

    /** 允许的录音来源 */
    private static final Set<String> ALLOWED_RECORDING_SOURCES = Set.of("OEM_RECORD", "LINE", "MANUAL");

    /** 录音记录状态: 已上传 */
    private static final String RECORDING_STATUS_UPLOADED = "UPLOADED";

    /** 客户数据访问层（正式客户） */
    private final ScrmCustomerRepository customerRepository;

    /** 公海线索数据访问层（销售「我的客户」主数据源） */
    private final ScrmPublicSeaCustomerRepository publicSeaRepository;

    /** 客户联系方式数据访问层（按 ownerType + ownerId 归属） */
    private final ScrmCustomerContactRepository contactRepository;

    /** 通话记录数据访问层 */
    private final ScrmCallRecordRepository callRecordRepository;

    /** 录音数据访问层 */
    private final ScrmRecordingRepository recordingRepository;

    /** 对象存储抽象 */
    private final ObjectStorage objectStorage;

    /** 对象存储配置 */
    private final StorageProperties storageProperties;

    // ============================================================
    // 客户与联系方式
    // ============================================================

    /**
     * 查询分配给当前销售的公海线索（销售 App「我的客户」主数据源）。
     * <p>
     * 数据源为 {@code scrm_public_sea_customer}，过滤条件
     * {@code assigned_to = userId} 且状态为 ASSIGNED / LOCKED；
     * 批量取联系方式避免 N+1。
     * </p>
     *
     * @param userId  当前销售用户 ID
     * @param keyword 昵称 / 备注 / 平台 UID 关键词（可空）
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 线索分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesCustomerVo> listAssignedLeads(String userId, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "assignedAt"));
        Page<ScrmPublicSeaCustomerEntity> leads = publicSeaRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("assignedTo"), userId));
            predicates.add(root.get("status").in(List.of(SEA_STATUS_ASSIGNED, SEA_STATUS_LOCKED)));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nickname")), like),
                        cb.like(cb.lower(root.get("remark")), like),
                        cb.like(cb.lower(root.get("platformCustomerUid")), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<Long> ids = leads.getContent().stream()
                .map(ScrmPublicSeaCustomerEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ScrmCustomerContactEntity>> contactsByOwner = ids.isEmpty()
                ? Collections.emptyMap()
                : contactRepository.findByOwnerTypeAndCustomerIdIn(OWNER_PUBLIC_SEA, ids).stream()
                        .collect(Collectors.groupingBy(ScrmCustomerContactEntity::getCustomerId));

        return leads.map(entity -> toSalesCustomerVo(entity,
                contactsByOwner.getOrDefault(entity.getId(), Collections.emptyList())));
    }

    /**
     * 查询按运营账号归属的正式客户（兼容路径，{@code scope=account}）。
     *
     * @param accessibleAccountIds 可访问账号 ID 集合（null 表示不限制）
     * @param keyword              昵称 / 备注关键词（可空）
     * @param page                 页码
     * @param size                 每页大小
     * @return 客户分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmSalesCustomerVo> listAccountCustomers(List<Long> accessibleAccountIds,
                                                          String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "updateTime"));
        Page<ScrmCustomerEntity> customers = customerRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (accessibleAccountIds != null) {
                if (accessibleAccountIds.isEmpty()) {
                    return cb.disjunction();
                }
                predicates.add(root.get("ownerAccountId").in(accessibleAccountIds));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(
                        cb.like(root.get("nickname"), like),
                        cb.like(root.get("remark"), like)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        List<Long> customerIds = customers.getContent().stream()
                .map(ScrmCustomerEntity::getId)
                .collect(Collectors.toList());
        Map<Long, List<ScrmCustomerContactEntity>> contactsByCustomer = customerIds.isEmpty()
                ? Collections.emptyMap()
                : contactRepository.findByOwnerTypeAndCustomerIdIn(OWNER_CUSTOMER, customerIds).stream()
                        .collect(Collectors.groupingBy(ScrmCustomerContactEntity::getCustomerId));

        return customers.map(entity -> toSalesCustomerVo(entity,
                contactsByCustomer.getOrDefault(entity.getId(), Collections.emptyList())));
    }

    /**
     * 查询单个客户/线索详情（含联系方式）。
     *
     * @param ownerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param ownerId   归属对象 ID
     * @return 客户视图
     * @throws ScrmException 对象不存在 / 归属类型非法
     */
    @Transactional(readOnly = true)
    public ScrmSalesCustomerVo getCustomerDetail(String ownerType, Long ownerId) throws ScrmException {
        String type = normalizeOwnerType(ownerType);
        List<ScrmCustomerContactEntity> contacts =
                contactRepository.findByOwnerTypeAndCustomerId(type, ownerId);
        if (OWNER_PUBLIC_SEA.equals(type)) {
            ScrmPublicSeaCustomerEntity lead = publicSeaRepository.findById(ownerId)
                    .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                            "线索不存在: id=" + ownerId));
            return toSalesCustomerVo(lead, contacts);
        }
        ScrmCustomerEntity customer = customerRepository.findById(ownerId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "客户不存在: id=" + ownerId));
        return toSalesCustomerVo(customer, contacts);
    }

    /**
     * 保存联系方式（新增或更新主号码）。
     * <p>
     * 同一归属对象同一号码唯一；设置 {@code isPrimary=true} 时先把该对象其他号码
     * 的主标记清空，保证「一个对象一个主号码」。
     * </p>
     *
     * @param ownerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param ownerId   归属对象 ID
     * @param phone     号码（服务端规范化）
     * @param phoneType 号码类型（MOBILE / LANDLINE / OTHER）
     * @param isPrimary 是否主号码
     * @param remark    备注
     * @return 保存后的联系方式
     * @throws ScrmException 对象不存在 / 号码非法
     */
    @Transactional
    public ScrmCustomerContactEntity saveContact(String ownerType, Long ownerId, String phone,
                                                 String phoneType, Boolean isPrimary, String remark)
            throws ScrmException {
        String type = normalizeOwnerType(ownerType);
        requireOwnerExists(type, ownerId);

        String normalized = normalizePhone(phone);
        if (normalized == null || normalized.isBlank()) {
            throw ScrmException.badRequest("号码不能为空或格式非法");
        }
        String phoneKind = (phoneType == null || phoneType.isBlank())
                ? "MOBILE" : phoneType.trim().toUpperCase();
        if (!Set.of("MOBILE", "LANDLINE", "OTHER").contains(phoneKind)) {
            throw ScrmException.badRequest("号码类型仅支持 MOBILE/LANDLINE/OTHER: " + phoneType);
        }
        boolean primary = Boolean.TRUE.equals(isPrimary);
        if (primary) {
            for (ScrmCustomerContactEntity item : contactRepository.findByOwnerTypeAndCustomerId(type, ownerId)) {
                if (Boolean.TRUE.equals(item.getIsPrimary()) && !normalized.equals(item.getPhone())) {
                    item.setIsPrimary(false);
                    contactRepository.save(item);
                }
            }
        }
        ScrmCustomerContactEntity entity = contactRepository
                .findByOwnerTypeAndCustomerIdAndPhone(type, ownerId, normalized)
                .orElseGet(() -> {
                    ScrmCustomerContactEntity created = new ScrmCustomerContactEntity();
                    created.setOwnerType(type);
                    created.setCustomerId(ownerId);
                    created.setPhone(normalized);
                    return created;
                });
        entity.setPhoneType(phoneKind);
        entity.setIsPrimary(primary);
        entity.setRemark(remark);
        return contactRepository.save(entity);
    }

    /**
     * 按号码查询全部归属对象（撞单检测 / 来电匹配）。
     *
     * @param phone 号码
     * @return 匹配结果列表（可能为空）
     */
    @Transactional(readOnly = true)
    public List<PhoneMatch> findOwnersByPhone(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null || normalized.isBlank()) {
            return Collections.emptyList();
        }
        List<ScrmCustomerContactEntity> contacts = contactRepository.findByPhone(normalized);
        if (contacts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> customerIds = contacts.stream()
                .filter(c -> OWNER_CUSTOMER.equals(c.getOwnerType()))
                .map(ScrmCustomerContactEntity::getCustomerId)
                .distinct().collect(Collectors.toList());
        List<Long> leadIds = contacts.stream()
                .filter(c -> OWNER_PUBLIC_SEA.equals(c.getOwnerType()))
                .map(ScrmCustomerContactEntity::getCustomerId)
                .distinct().collect(Collectors.toList());

        Map<Long, ScrmCustomerEntity> customerMap = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerRepository.findAllById(customerIds).stream()
                        .collect(Collectors.toMap(ScrmCustomerEntity::getId, c -> c));
        Map<Long, ScrmPublicSeaCustomerEntity> leadMap = leadIds.isEmpty()
                ? Collections.emptyMap()
                : publicSeaRepository.findAllById(leadIds).stream()
                        .collect(Collectors.toMap(ScrmPublicSeaCustomerEntity::getId, c -> c));

        List<PhoneMatch> matches = new ArrayList<>();
        for (ScrmCustomerContactEntity contact : contacts) {
            if (OWNER_CUSTOMER.equals(contact.getOwnerType())) {
                ScrmCustomerEntity c = customerMap.get(contact.getCustomerId());
                if (c != null) {
                    matches.add(new PhoneMatch(OWNER_CUSTOMER, c.getId(), c.getNickname()));
                }
            } else if (OWNER_PUBLIC_SEA.equals(contact.getOwnerType())) {
                ScrmPublicSeaCustomerEntity c = leadMap.get(contact.getCustomerId());
                if (c != null) {
                    matches.add(new PhoneMatch(OWNER_PUBLIC_SEA, c.getId(), c.getNickname()));
                }
            }
        }
        return matches;
    }

    // ============================================================
    // 通话记录
    // ============================================================

    /**
     * 批量上报通话记录（幂等）。
     * <p>
     * 逐条处理：先按 {@code (deviceId, callLogId)} 查重，无 callLogId 时按
     * 「同设备 + 同号码哈希 + 时间窗口」查重；未指定归属时按号码反查客户/线索。
     * </p>
     *
     * @param userId  当前用户 ID
     * @param reports 通话上报列表
     * @return 批量结果统计
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmCallReportResultVo reportCalls(String userId, List<ScrmCallReportDto> reports)
            throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.UNAUTHORIZED, "未获取到登录用户");
        }
        if (reports == null || reports.isEmpty()) {
            throw ScrmException.badRequest("通话上报列表不能为空");
        }
        if (reports.size() > 500) {
            throw ScrmException.badRequest("单次最多上报 500 条通话记录");
        }

        int created = 0;
        int duplicated = 0;
        int matched = 0;
        List<ScrmCallReportResultVo.Item> items = new ArrayList<>(reports.size());

        for (ScrmCallReportDto report : reports) {
            validateCallReport(report);
            String normalized = normalizePhone(report.getPeerNumber());
            if (normalized == null || normalized.isBlank()) {
                throw ScrmException.badRequest("对端号码格式非法: " + report.getPeerNumber());
            }
            String peerHash = sha256(normalized);

            Optional<ScrmCallRecordEntity> existing = findExistingCall(report, peerHash);
            if (existing.isPresent()) {
                duplicated++;
                ScrmCallRecordEntity entity = existing.get();
                items.add(ScrmCallReportResultVo.Item.builder()
                        .created(false)
                        .duplicated(true)
                        .callRecordId(entity.getId())
                        .customerId(entity.getCustomerId())
                        .customerType(entity.getCustomerType())
                        .build());
                continue;
            }

            String ownerType = report.getCustomerType();
            Long ownerId = report.getCustomerId();
            String ownerName = null;
            if (ownerId != null && ownerType != null) {
                ownerName = resolveOwnerName(ownerType, ownerId);
                if (ownerName == null) {
                    ownerId = null;
                    ownerType = null;
                }
            }
            if (ownerId == null) {
                List<PhoneMatch> candidates = findOwnersByPhone(normalized);
                if (!candidates.isEmpty()) {
                    PhoneMatch first = candidates.get(0);
                    ownerType = first.ownerType();
                    ownerId = first.ownerId();
                    ownerName = first.nickname();
                }
            }
            if (ownerId != null) {
                matched++;
            }

            ScrmCallRecordEntity entity = new ScrmCallRecordEntity();
            entity.setUserId(userId);
            entity.setDeviceId(report.getDeviceId());
            entity.setCustomerId(ownerId);
            entity.setCustomerType(ownerType);
            entity.setDirection(report.getDirection().trim().toUpperCase());
            entity.setPeerNumber(normalized);
            entity.setPeerHash(peerHash);
            entity.setStartAt(report.getStartAt());
            entity.setDurationSec(report.getDurationSec() == null ? 0 : report.getDurationSec());
            entity.setConnected(Boolean.TRUE.equals(report.getConnected())
                    || (report.getDurationSec() != null && report.getDurationSec() > 0));
            entity.setSource(report.getSource().trim().toUpperCase());
            entity.setCallLogId(report.getCallLogId());
            entity.setRemark(report.getRemark());
            entity = callRecordRepository.save(entity);
            created++;

            items.add(ScrmCallReportResultVo.Item.builder()
                    .created(true)
                    .duplicated(false)
                    .callRecordId(entity.getId())
                    .customerId(ownerId)
                    .customerType(ownerType)
                    .customerName(ownerName)
                    .build());
        }

        log.info("通话批量上报完成: userId={}, total={}, created={}, duplicated={}, matched={}",
                userId, reports.size(), created, duplicated, matched);
        return ScrmCallReportResultVo.builder()
                .total(reports.size())
                .created(created)
                .duplicated(duplicated)
                .matchedCustomer(matched)
                .items(items)
                .build();
    }

    /**
     * 查询当前用户的通话记录分页。
     *
     * @param userId   用户 ID
     * @param ownerType 归属类型过滤（可空）
     * @param ownerId  归属对象 ID 过滤（可空）
     * @param page     页码
     * @param size     每页大小
     * @return 通话记录分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmCallRecordEntity> listCalls(String userId, String ownerType, Long ownerId,
                                                int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        if (ownerId != null) {
            List<ScrmCallRecordEntity> list = OWNER_PUBLIC_SEA.equalsIgnoreCase(ownerType)
                    ? callRecordRepository.findByCustomerTypeAndCustomerIdOrderByStartAtDesc(
                            OWNER_PUBLIC_SEA, ownerId)
                    : callRecordRepository.findByCustomerTypeAndCustomerIdOrderByStartAtDesc(
                            OWNER_CUSTOMER, ownerId);
            int from = Math.min(pageable.getPageNumber() * pageable.getPageSize(), list.size());
            int to = Math.min(from + pageable.getPageSize(), list.size());
            return new PageImpl<>(list.subList(from, to), pageable, list.size());
        }
        return callRecordRepository.findByUserIdOrderByStartAtDesc(userId, pageable);
    }

    /**
     * 将未认领的通话记录关联到客户/线索。
     *
     * @param userId    用户 ID
     * @param callId    通话记录 ID
     * @param ownerType 归属类型（CUSTOMER / PUBLIC_SEA）
     * @param ownerId   归属对象 ID
     * @return 更新后的通话记录
     * @throws ScrmException 记录不存在 / 越权 / 归属对象不存在
     */
    @Transactional
    public ScrmCallRecordEntity bindCallToCustomer(String userId, Long callId,
                                                   String ownerType, Long ownerId) throws ScrmException {
        ScrmCallRecordEntity entity = callRecordRepository.findById(callId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "通话记录不存在: id=" + callId));
        if (!entity.getUserId().equals(userId)) {
            throw new ScrmException(ScrmExceptionConstants.FORBIDDEN, "无权操作他人通话记录");
        }
        String type = normalizeOwnerType(ownerType);
        requireOwnerExists(type, ownerId);
        entity.setCustomerType(type);
        entity.setCustomerId(ownerId);
        return callRecordRepository.save(entity);
    }

    // ============================================================
    // 录音
    // ============================================================

    /**
     * 上传录音文件。
     * <p>
     * 先按文件内容 SHA-256 查重（重复直接返回既有记录），再写入对象存储
     * {@code scrm/recording/{yyyyMM}/{uuid}_{fileName}}，最后落库。
     * </p>
     *
     * @param userId       用户 ID
     * @param deviceId     设备 ID
     * @param fileName     原始文件名
     * @param contentType  MIME 类型
     * @param source       来源（OEM_RECORD / LINE / MANUAL）
     * @param callRecordId 关联通话记录 ID（可空）
     * @param ownerType    归属类型（可空）
     * @param ownerId      归属对象 ID（可空）
     * @param durationSec  录音时长（可空）
     * @param inputStream  文件流（调用方负责关闭）
     * @return 上传结果
     * @throws ScrmException 参数非法 / 存储失败
     */
    @Transactional
    public ScrmRecordingUploadResultVo uploadRecording(String userId, String deviceId, String fileName,
                                                       String contentType, String source,
                                                       Long callRecordId, String ownerType, Long ownerId,
                                                       Integer durationSec, InputStream inputStream)
            throws ScrmException {
        if (userId == null || userId.isBlank()) {
            throw new ScrmException(ScrmExceptionConstants.UNAUTHORIZED, "未获取到登录用户");
        }
        if (inputStream == null) {
            throw ScrmException.badRequest("录音文件流不能为空");
        }
        String src = (source == null || source.isBlank()) ? "MANUAL" : source.trim().toUpperCase();
        if (!ALLOWED_RECORDING_SOURCES.contains(src)) {
            throw ScrmException.badRequest("录音来源仅支持 OEM_RECORD/LINE/MANUAL: " + source);
        }
        if (callRecordId != null) {
            ScrmCallRecordEntity call = callRecordRepository.findById(callRecordId)
                    .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                            "通话记录不存在: id=" + callRecordId));
            if (!call.getUserId().equals(userId)) {
                throw new ScrmException(ScrmExceptionConstants.FORBIDDEN, "无权关联他人通话记录");
            }
            if (ownerId == null) {
                ownerId = call.getCustomerId();
                ownerType = call.getCustomerType();
            }
        }
        String type = (ownerId == null) ? null : normalizeOwnerType(ownerType);

        byte[] bytes;
        try {
            bytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new ScrmException("SCRM_RECORDING_READ_FAILED", "读取录音文件失败: " + e.getMessage(), e);
        }
        if (bytes.length == 0) {
            throw ScrmException.badRequest("录音文件不能为空");
        }
        String fileHash = sha256(bytes);

        Optional<ScrmRecordingEntity> duplicate = recordingRepository.findByFileHash(fileHash);
        if (duplicate.isPresent()) {
            ScrmRecordingEntity entity = duplicate.get();
            log.info("录音重复上传, 返回既有记录: id={}, hash={}", entity.getId(), fileHash);
            return toRecordingResult(entity, true, userId);
        }

        String objectKey = buildRecordingKey(fileName);
        try {
            objectStorage.upload(storageProperties.getBucket(), objectKey,
                    new java.io.ByteArrayInputStream(bytes), bytes.length,
                    sanitizeAudioContentType(contentType));
        } catch (StorageException e) {
            log.error("录音上传对象存储失败: objectKey={}, err={}", objectKey, e.getMessage(), e);
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_UPLOAD_FAILED,
                    "录音上传失败: " + e.getMessage(), e);
        }

        ScrmRecordingEntity entity = new ScrmRecordingEntity();
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setCallRecordId(callRecordId);
        entity.setCustomerId(ownerId);
        entity.setCustomerType(type);
        entity.setObjectKey(objectKey);
        entity.setFileName(sanitizeFileName(fileName));
        entity.setFileSize((long) bytes.length);
        entity.setDurationSec(durationSec);
        entity.setSource(src);
        entity.setFileHash(fileHash);
        entity.setStatus(RECORDING_STATUS_UPLOADED);
        entity = recordingRepository.save(entity);

        log.info("录音上传成功: id={}, userId={}, source={}, size={}, objectKey={}",
                entity.getId(), userId, src, bytes.length, objectKey);
        return toRecordingResult(entity, false, userId);
    }

    /**
     * 生成录音预签名播放地址。
     *
     * @param userId        用户 ID
     * @param id            录音记录 ID
     * @param expiryMinutes 有效期（分钟）
     * @return 预签名 URL
     * @throws ScrmException 记录不存在 / 越权
     */
    @Transactional(readOnly = true)
    public String getRecordingUrl(String userId, Long id, int expiryMinutes) throws ScrmException {
        ScrmRecordingEntity entity = recordingRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "录音不存在: id=" + id));
        if (!entity.getUserId().equals(userId)) {
            throw new ScrmException(ScrmExceptionConstants.FORBIDDEN, "无权访问他人录音");
        }
        int seconds = Math.min(Math.max(expiryMinutes, 1), 24 * 60) * 60;
        try {
            return objectStorage.presignedGetUrl(storageProperties.getBucket(),
                    entity.getObjectKey(), seconds);
        } catch (StorageException e) {
            throw new ScrmException(ScrmExceptionConstants.SCRM_MEDIA_PRESIGN_FAILED,
                    "生成录音播放地址失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询当前用户录音分页。
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return 录音分页
     */
    @Transactional(readOnly = true)
    public Page<ScrmRecordingEntity> listRecordings(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        return recordingRepository.findByUserIdOrderByCreateTimeDesc(userId, pageable);
    }

    /**
     * 统计录音覆盖率。
     * <p>
     * 覆盖率 = 录音数 / 通话数。命中 0 通话时返回 0，不返回除零异常。
     * </p>
     *
     * @param userId 用户 ID（null 表示全局）
     * @param from   起始时间（可空，默认 30 天前）
     * @param to     结束时间（可空，默认当前）
     * @return 覆盖率统计
     */
    @Transactional(readOnly = true)
    public ScrmRecordingCoverageVo getCoverage(String userId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime begin = from == null ? end.minusDays(30) : from;
        if (begin.isAfter(end)) {
            throw ScrmException.badRequest("统计起始时间不能晚于结束时间");
        }
        long calls;
        long recordings;
        if (userId == null || userId.isBlank()) {
            calls = callRecordRepository.count();
            recordings = recordingRepository.count();
        } else {
            calls = callRecordRepository.countByUserIdAndStartAtBetween(userId, begin, end);
            recordings = recordingRepository.countByUserIdAndCreateTimeBetween(userId, begin, end);
        }
        double rate = calls == 0 ? 0d : Math.round((double) recordings / calls * 10000d) / 10000d;
        return ScrmRecordingCoverageVo.builder()
                .scope((userId == null || userId.isBlank()) ? "ALL" : "USER")
                .userId(userId)
                .from(begin.toString())
                .to(end.toString())
                .totalCalls(calls)
                .totalRecordings(recordings)
                .coverageRate(rate)
                .build();
    }

    /**
     * 获取录音能力与操作指引。
     *
     * @param romHint 客户端上报的 ROM 提示（可空）
     * @return 能力与指引
     */
    public ScrmRecordingCapabilityVo getRecordingCapability(String romHint) {
        String rom = detectRom(romHint);
        Map<String, ScrmRecordingCapabilityVo> table = recordingGuideTable();
        ScrmRecordingCapabilityVo vo = table.getOrDefault(rom, table.get("AOSP"));
        Map<String, String> notes = new LinkedHashMap<>(vo.getNotes() == null ? Map.of() : vo.getNotes());
        notes.put("compliance", "通话录音涉及双方个人信息，需在通话开始前告知对方并取得同意，"
                + "录音文件应加密存储并设置保留期限。");
        notes.put("coverage", "录音覆盖率 = 已上传录音数 / 通话记录数，"
                + "不支持自动录音的机型仅能手动上传。");
        vo.setNotes(notes);
        return vo;
    }

    /**
     * 录音能力指引表（按 ROM）。
     *
     * @return ROM → 指引
     */
    private Map<String, ScrmRecordingCapabilityVo> recordingGuideTable() {
        Map<String, ScrmRecordingCapabilityVo> table = new HashMap<>();

        table.put("MIUI", ScrmRecordingCapabilityVo.builder()
                .rom("MIUI").supportedByDefault(true)
                .summary("小米 / Redmi（MIUI / HyperOS）系统拨号器自带通话录音，可开启自动录音。")
                .steps(List.of(
                        "打开系统「电话」应用",
                        "点右上角设置 → 通话录音",
                        "选择「自动录音」→「所有通话」",
                        "回到本应用，点「授权录音目录」并选择 MIUI/sound_recorder/call_rec"))
                .directories(List.of("MIUI/sound_recorder/call_rec/"))
                .build());

        table.put("COLOROS", ScrmRecordingCapabilityVo.builder()
                .rom("COLOROS").supportedByDefault(true)
                .summary("OPPO / realme / 一加（ColorOS）系统拨号器自带通话录音。")
                .steps(List.of(
                        "打开系统「电话」应用",
                        "设置 → 通话录音 → 开启自动录音",
                        "回到本应用，点「授权录音目录」并选择 Recordings/Call"))
                .directories(List.of("Recordings/Call/"))
                .build());

        table.put("EMUI", ScrmRecordingCapabilityVo.builder()
                .rom("EMUI").supportedByDefault(true)
                .summary("华为 / 荣耀（EMUI / HarmonyOS 4 及以下）系统拨号器自带通话录音。")
                .steps(List.of(
                        "打开系统「电话」应用",
                        "设置 → 通话自动录音 → 开启",
                        "回到本应用，点「授权录音目录」并选择 Recordings"))
                .directories(List.of("Recordings/"))
                .build());

        table.put("VIVO", ScrmRecordingCapabilityVo.builder()
                .rom("VIVO").supportedByDefault(true)
                .summary("vivo / iQOO（OriginOS）系统拨号器自带通话录音。")
                .steps(List.of(
                        "打开系统「电话」应用",
                        "设置 → 通话录音 → 自动录音",
                        "回到本应用，点「授权录音目录」并选择 Record/Call"))
                .directories(List.of("Record/Call/", "Recordings/"))
                .build());

        table.put("SAMSUNG", ScrmRecordingCapabilityVo.builder()
                .rom("SAMSUNG").supportedByDefault(true)
                .summary("三星（One UI）国行系统拨号器支持通话录音。")
                .steps(List.of(
                        "打开系统「电话」应用",
                        "设置 → 录制通话 → 自动录制",
                        "回到本应用，点「授权录音目录」并选择 Call"))
                .directories(List.of("Call/"))
                .build());

        table.put("AOSP", ScrmRecordingCapabilityVo.builder()
                .rom("AOSP").supportedByDefault(false)
                .summary("本机可能不支持系统自动通话录音（原生 Android / 海外版 / 鸿蒙 NEXT）。"
                        + "可手动上传录音文件，通话统计功能不受影响。")
                .steps(List.of(
                        "确认系统拨号器设置中是否存在「通话录音」",
                        "若不存在，可使用「手动上传录音」",
                        "若系统为 HarmonyOS NEXT，Android 应用无法安装，请使用鸿蒙端"))
                .directories(List.of())
                .build());

        return table;
    }

    // ============================================================
    // 内部工具
    // ============================================================

    /**
     * 归一化归属类型，非法值抛 400。
     *
     * @param ownerType 原始归属类型
     * @return CUSTOMER / PUBLIC_SEA
     * @throws ScrmException 类型非法
     */
    private String normalizeOwnerType(String ownerType) throws ScrmException {
        if (ownerType == null || ownerType.isBlank()) {
            return OWNER_CUSTOMER;
        }
        String type = ownerType.trim().toUpperCase();
        if (!OWNER_CUSTOMER.equals(type) && !OWNER_PUBLIC_SEA.equals(type)) {
            throw ScrmException.badRequest("归属类型仅支持 CUSTOMER/PUBLIC_SEA: " + ownerType);
        }
        return type;
    }

    /**
     * 校验归属对象存在。
     *
     * @param ownerType 归属类型
     * @param ownerId   归属对象 ID
     * @throws ScrmException 对象不存在
     */
    private void requireOwnerExists(String ownerType, Long ownerId) throws ScrmException {
        if (ownerId == null) {
            throw ScrmException.badRequest("归属对象 ID 不能为空");
        }
        boolean exists = OWNER_PUBLIC_SEA.equals(ownerType)
                ? publicSeaRepository.existsById(ownerId)
                : customerRepository.existsById(ownerId);
        if (!exists) {
            throw new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                    "归属对象不存在: type=" + ownerType + ", id=" + ownerId);
        }
    }

    /**
     * 解析归属对象名称（用于上报结果回显）。
     *
     * @param ownerType 归属类型
     * @param ownerId   归属对象 ID
     * @return 昵称，不存在返回 null
     */
    private String resolveOwnerName(String ownerType, Long ownerId) {
        if (OWNER_PUBLIC_SEA.equalsIgnoreCase(ownerType)) {
            return publicSeaRepository.findById(ownerId)
                    .map(ScrmPublicSeaCustomerEntity::getNickname).orElse(null);
        }
        return customerRepository.findById(ownerId)
                .map(ScrmCustomerEntity::getNickname).orElse(null);
    }

    /**
     * 校验单条通话上报参数。
     *
     * @param report 上报数据
     * @throws ScrmException 参数非法
     */
    private void validateCallReport(ScrmCallReportDto report) throws ScrmException {
        if (report == null) {
            throw ScrmException.badRequest("通话上报项不能为空");
        }
        if (report.getDeviceId() == null || report.getDeviceId().isBlank()) {
            throw ScrmException.badRequest("设备 ID 不能为空");
        }
        String direction = report.getDirection() == null ? "" : report.getDirection().trim().toUpperCase();
        if (!ALLOWED_DIRECTIONS.contains(direction)) {
            throw ScrmException.badRequest("通话方向仅支持 OUT/IN/MISSED/REJECTED: " + report.getDirection());
        }
        String source = report.getSource() == null ? "" : report.getSource().trim().toUpperCase();
        if (!ALLOWED_CALL_SOURCES.contains(source)) {
            throw ScrmException.badRequest("通话来源仅支持 APP_CALL/CALL_LOG/SCREENING/MANUAL: "
                    + report.getSource());
        }
        if (report.getStartAt() == null) {
            throw ScrmException.badRequest("通话开始时间不能为空");
        }
        if (report.getStartAt().isAfter(LocalDateTime.now().plusMinutes(10))) {
            throw ScrmException.badRequest("通话开始时间不能晚于当前时间 10 分钟以上（疑似设备时间异常）");
        }
    }

    /**
     * 查找已存在的通话记录（两级去重）。
     *
     * @param report   上报数据
     * @param peerHash 号码哈希
     * @return 已存在记录
     */
    private Optional<ScrmCallRecordEntity> findExistingCall(ScrmCallReportDto report, String peerHash) {
        if (report.getCallLogId() != null) {
            Optional<ScrmCallRecordEntity> byLog =
                    callRecordRepository.findByDeviceIdAndCallLogId(report.getDeviceId(), report.getCallLogId());
            if (byLog.isPresent()) {
                return byLog;
            }
        }
        List<ScrmCallRecordEntity> near = callRecordRepository
                .findByDeviceIdAndPeerHashAndStartAtBetween(report.getDeviceId(), peerHash,
                        report.getStartAt().minusMinutes(DEDUP_WINDOW_MINUTES),
                        report.getStartAt().plusMinutes(DEDUP_WINDOW_MINUTES));
        return near.stream()
                .filter(item -> item.getDirection().equalsIgnoreCase(report.getDirection()))
                .findFirst();
    }

    /**
     * 公海线索转销售端视图。
     *
     * @param entity   公海线索实体
     * @param contacts 联系方式
     * @return 客户视图
     */
    private ScrmSalesCustomerVo toSalesCustomerVo(ScrmPublicSeaCustomerEntity entity,
                                                  List<ScrmCustomerContactEntity> contacts) {
        return buildCustomerVo(OWNER_PUBLIC_SEA, entity.getId(), entity.getNickname(),
                entity.getLifecycle(), entity.getRemark(), entity.getAssignedAt(), contacts);
    }

    /**
     * 正式客户转销售端视图。
     *
     * @param entity   客户实体
     * @param contacts 联系方式
     * @return 客户视图
     */
    private ScrmSalesCustomerVo toSalesCustomerVo(ScrmCustomerEntity entity,
                                                  List<ScrmCustomerContactEntity> contacts) {
        return buildCustomerVo(OWNER_CUSTOMER, entity.getId(), entity.getNickname(),
                entity.getLifecycle(), entity.getRemark(), entity.getNextFollowUpAt(), contacts);
    }

    /**
     * 构建销售端客户视图（两类归属共用）。
     *
     * @param ownerType      归属类型
     * @param ownerId        归属对象 ID
     * @param nickname       昵称
     * @param lifecycle      生命周期
     * @param remark         备注
     * @param nextFollowUpAt 下次跟进时间
     * @param contacts       联系方式
     * @return 客户视图
     */
    private ScrmSalesCustomerVo buildCustomerVo(String ownerType, Long ownerId, String nickname,
                                                String lifecycle, String remark,
                                                LocalDateTime nextFollowUpAt,
                                                List<ScrmCustomerContactEntity> contacts) {
        List<ScrmSalesCustomerVo.Contact> contactVos = contacts.stream()
                .map(item -> ScrmSalesCustomerVo.Contact.builder()
                        .phone(item.getPhone())
                        .phoneType(item.getPhoneType())
                        .isPrimary(item.getIsPrimary())
                        .remark(item.getRemark())
                        .build())
                .collect(Collectors.toList());
        String primaryPhone = contacts.stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsPrimary()))
                .map(ScrmCustomerContactEntity::getPhone)
                .findFirst()
                .orElseGet(() -> contacts.isEmpty() ? null : contacts.get(0).getPhone());
        return ScrmSalesCustomerVo.builder()
                .id(ownerId)
                .ownerType(ownerType)
                .nickname(nickname)
                .lifecycle(lifecycle)
                .remark(remark)
                .nextFollowUpAt(nextFollowUpAt == null ? null : nextFollowUpAt.toString())
                .primaryPhone(primaryPhone)
                .contacts(contactVos)
                .build();
    }

    /**
     * 录音实体转上传结果。
     *
     * @param entity     录音实体
     * @param duplicated 是否重复上传
     * @param userId     请求用户（用于越权保护：重复文件的既有记录归属他人时不返回 URL）
     * @return 上传结果
     */
    private ScrmRecordingUploadResultVo toRecordingResult(ScrmRecordingEntity entity, boolean duplicated,
                                                          String userId) {
        String url = null;
        if (entity.getUserId().equals(userId)) {
            try {
                url = objectStorage.presignedGetUrl(storageProperties.getBucket(), entity.getObjectKey(),
                        storageProperties.getPresignExpirySeconds());
            } catch (StorageException e) {
                log.warn("生成录音预签名地址失败(不影响上传结果): id={}, err={}", entity.getId(), e.getMessage());
            }
        }
        return ScrmRecordingUploadResultVo.builder()
                .id(entity.getId())
                .duplicated(duplicated)
                .objectKey(entity.getObjectKey())
                .url(url)
                .fileSize(entity.getFileSize())
                .source(entity.getSource())
                .callRecordId(entity.getCallRecordId())
                .customerId(entity.getCustomerId())
                .customerType(entity.getCustomerType())
                .build();
    }

    /**
     * 号码规范化。
     * <p>
     * 去除空格、横线、括号、点号；去掉 {@code +86} / {@code 0086} / {@code 86} 前缀
     * （仅当剩余为 13 位且以 86 开头时，避免误伤固话）。
     * </p>
     *
     * @param raw 原始号码
     * @return 规范化号码，非法时返回 null
     */
    static String normalizePhone(String raw) {
        // 统一委托到 PhoneUtils，避免多处实现语义漂移
        return PhoneUtils.normalize(raw);
    }

    /**
     * 计算 SHA-256（十六进制小写）。
     *
     * @param input 字符串
     * @return 哈希
     */
    static String sha256(String input) {
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 SHA-256（十六进制小写）。
     *
     * @param input 字节数组
     * @return 哈希
     */
    static String sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 构建录音对象 key。
     *
     * @param fileName 原始文件名
     * @return objectKey
     */
    private String buildRecordingKey(String fileName) {
        String month = LocalDateTime.now().format(MONTH_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return RECORDING_KEY_PREFIX + month + "/" + uuid + "_" + sanitizeFileName(fileName);
    }

    /**
     * 净化文件名，防止路径穿越与响应头注入。
     *
     * @param fileName 原始文件名
     * @return 安全文件名
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "recording";
        }
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "")
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return name.isEmpty() ? "recording" : name;
    }

    /**
     * 限定录音 MIME 类型，只允许音频与通用二进制，防止上传 HTML 等可执行内容。
     *
     * @param contentType 客户端声明类型
     * @return 安全类型
     */
    private static String sanitizeAudioContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String base = contentType.trim().toLowerCase().split(";")[0].trim();
        if (base.startsWith("audio/") || "application/octet-stream".equals(base)
                || "video/mp4".equals(base) || "video/3gpp".equals(base)) {
            return base;
        }
        return "application/octet-stream";
    }

    /**
     * 归一化 ROM 标识。
     *
     * @param hint 客户端上报（制造商/品牌/ROM）
     * @return 已知 ROM 键
     */
    static String detectRom(String hint) {
        if (hint == null || hint.isBlank()) {
            return "AOSP";
        }
        String value = hint.toLowerCase();
        if (value.contains("xiaomi") || value.contains("redmi") || value.contains("miui")
                || value.contains("hyperos") || value.contains("poco")) {
            return "MIUI";
        }
        if (value.contains("oppo") || value.contains("realme") || value.contains("oneplus")
                || value.contains("coloros")) {
            return "COLOROS";
        }
        if (value.contains("huawei") || value.contains("honor") || value.contains("emui")
                || value.contains("harmony")) {
            return "EMUI";
        }
        if (value.contains("vivo") || value.contains("iqoo") || value.contains("originos")) {
            return "VIVO";
        }
        if (value.contains("samsung") || value.contains("one ui")) {
            return "SAMSUNG";
        }
        return "AOSP";
    }

    /**
     * 号码归属匹配结果。
     *
     * @param ownerType 归属类型
     * @param ownerId   归属对象 ID
     * @param nickname  昵称
     */
    public record PhoneMatch(String ownerType, Long ownerId, String nickname) {
    }
}

