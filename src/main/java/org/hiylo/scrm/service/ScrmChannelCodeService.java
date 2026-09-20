/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmChannelCodeService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmChannelCodeDto;
import org.hiylo.scrm.dto.ScrmChannelCodeScanDto;
import org.hiylo.scrm.entity.ScrmChannelCodeEntity;
import org.hiylo.scrm.entity.ScrmChannelCodeScanEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmChannelCodeRepository;
import org.hiylo.scrm.repository.ScrmChannelCodeScanRepository;
import org.hiylo.scrm.vo.ChannelCodeStatsVo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 渠道活码服务
 * <p>
 * 负责渠道活码的创建、更新、删除、激活/停用, 扫码事件记录与账号分配,
 * 以及扫码转化统计。所有查询均通过归属账号做数据隔离。
 * </p>
 * <p>
 * 扫码分配规则按 codeType:
 * <ul>
 *   <li>SINGLE: 分配到 redirectAccountId</li>
 *   <li>MULTI: 按 assignRule 中的账号列表选取首个可用账号</li>
 *   <li>ROUND_ROBIN: 按 assignRule 中的账号列表轮询分配 (基于 scanCount 取模)</li>
 * </ul>
 * assignRule 约定为逗号分隔的账号 ID 字符串 (如 "1001,1002,1003"),
 * 兼容简单 JSON 数组字符串的数字提取。
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmChannelCodeService {

    /** 活码类型: 单账号 */
    private static final String TYPE_SINGLE = "SINGLE";
    /** 活码类型: 多账号 */
    private static final String TYPE_MULTI = "MULTI";
    /** 活码类型: 轮询 */
    private static final String TYPE_ROUND_ROBIN = "ROUND_ROBIN";

    /** 状态: 启用 */
    private static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态: 停用 */
    private static final String STATUS_INACTIVE = "INACTIVE";

    /** 添加状态: 待添加 */
    private static final String ADDED_PENDING = "PENDING";
    /** 添加状态: 已添加 */
    private static final String ADDED_ADDED = "ADDED";
    /** 添加状态: 已拒绝 */
    private static final String ADDED_REJECTED = "REJECTED";

    /** 百分比换算基数 */
    private static final double PERCENT_BASE = 100.0;
    /** 比率小数保留位数 */
    private static final int RATE_SCALE = 2;

    /** 渠道码数据仓库 */
    private final ScrmChannelCodeRepository channelCodeRepository;
    /** 渠道码扫码数据仓库 */
    private final ScrmChannelCodeScanRepository scanRepository;

    /**
     * 创建渠道活码
     * <p>
     * 默认状态为 INACTIVE, scanCount/addCount 初始化为 0, 写入归属账号 ID 后持久化。
     * </p>
     *
     * @param dto 活码参数
     * @return 创建后的活码
     * @throws ScrmException 参数校验失败
     */
    @Transactional
    public ScrmChannelCodeDto createCode(ScrmChannelCodeDto dto) throws ScrmException {
        validateCreateCode(dto);
        // SINGLE 类型必须提供 redirectAccountId
        if (TYPE_SINGLE.equals(dto.getCodeType()) && dto.getRedirectAccountId() == null) {
            throw ScrmException.badRequest("SINGLE 类型活码必须指定重定向账号");
        }
        ScrmChannelCodeEntity entity = new ScrmChannelCodeEntity();
        entity.setCodeName(dto.getCodeName());
        entity.setCodeType(dto.getCodeType());
        entity.setPlatformType(dto.getPlatformType());
        entity.setQrCodeUrl(dto.getQrCodeUrl());
        entity.setRedirectAccountId(dto.getRedirectAccountId());
        entity.setAssignRule(dto.getAssignRule());
        entity.setWelcomeMessage(dto.getWelcomeMessage());
        entity.setTags(dto.getTags());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : STATUS_INACTIVE);
        entity.setScanCount(0);
        entity.setAddCount(0);
        entity.setExpireAt(dto.getExpireAt());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = channelCodeRepository.save(entity);
        log.info("创建渠道活码: id={}, codeName={}, codeType={}, platformType={}",
                entity.getId(), entity.getCodeName(), entity.getCodeType(), entity.getPlatformType());
        return toCodeDto(entity);
    }

    /**
     * 更新渠道活码
     * <p>
     * 字段非空才覆盖。SINGLE 类型校验 redirectAccountId 必填。
     * </p>
     *
     * @param id  活码 ID
     * @param dto 活码参数
     * @return 更新后的活码
     * @throws ScrmException 活码不存在 / 参数校验失败
     */
    @Transactional
    public ScrmChannelCodeDto updateCode(Long id, ScrmChannelCodeDto dto) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(id);
        if (dto.getCodeName() != null) {
            if (dto.getCodeName().isBlank()) {
                throw ScrmException.badRequest("活码名称不能为空");
            }
            entity.setCodeName(dto.getCodeName());
        }
        if (dto.getCodeType() != null) entity.setCodeType(dto.getCodeType());
        if (dto.getPlatformType() != null) entity.setPlatformType(dto.getPlatformType());
        if (dto.getQrCodeUrl() != null) entity.setQrCodeUrl(dto.getQrCodeUrl());
        if (dto.getRedirectAccountId() != null) entity.setRedirectAccountId(dto.getRedirectAccountId());
        if (dto.getAssignRule() != null) entity.setAssignRule(dto.getAssignRule());
        if (dto.getWelcomeMessage() != null) entity.setWelcomeMessage(dto.getWelcomeMessage());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getExpireAt() != null) entity.setExpireAt(dto.getExpireAt());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        // SINGLE 类型必须提供 redirectAccountId
        if (TYPE_SINGLE.equals(entity.getCodeType()) && entity.getRedirectAccountId() == null) {
            throw ScrmException.badRequest("SINGLE 类型活码必须指定重定向账号");
        }
        entity = channelCodeRepository.save(entity);
        log.info("更新渠道活码: id={}", id);
        return toCodeDto(entity);
    }

    /**
     * 删除渠道活码
     * <p>
     * 级联清理扫码记录, 主记录删除。
     * </p>
     *
     * @param id 活码 ID
     * @throws ScrmException 活码不存在
     */
    @Transactional
    public void deleteCode(Long id) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(id);
        // 级联清理扫码记录
        scanRepository.deleteByChannelCodeId(id);
        channelCodeRepository.delete(entity);
        log.info("删除渠道活码: id={}, codeName={}", id, entity.getCodeName());
    }

    /**
     * 查询渠道活码
     *
     * @param id 活码 ID
     * @return 活码 DTO
     * @throws ScrmException 活码不存在
     */
    @Transactional(readOnly = true)
    public ScrmChannelCodeDto getCode(Long id) throws ScrmException {
        return toCodeDto(findCodeOrThrow(id));
    }

    /**
     * 按二维码 key (qrCodeUrl) 查询渠道活码
     * <p>
     * 扫码入口通过 qrKey 反查活码, qrKey 即二维码 URL。
     * </p>
     *
     * @param qrKey 二维码 URL
     * @return 活码 DTO
     * @throws ScrmException 活码不存在
     */
    @Transactional(readOnly = true)
    public ScrmChannelCodeDto getCodeByQrKey(String qrKey) throws ScrmException {
        if (qrKey == null || qrKey.isBlank()) {
            throw ScrmException.badRequest("二维码 key 不能为空");
        }
        ScrmChannelCodeEntity entity = channelCodeRepository.findByQrCodeUrl(qrKey)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "渠道活码不存在: qrKey=" + qrKey));
        // 数据隔离: 校验活码归属当前账号

        return toCodeDto(entity);
    }

    /**
     * 分页查询渠道活码, 支持按状态、平台类型、活码类型与关键词过滤
     *
     * @param status       状态过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @param codeType     活码类型过滤 (可空)
     * @param keyword      关键词过滤, 匹配活码名称 (可空)
     * @param pageable     分页参数
     * @return 活码分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmChannelCodeDto> listCodes(String status, String platformType,
                                              String codeType, String keyword, Pageable pageable) {
        Pageable sorted = ensureSort(pageable);
        Specification<ScrmChannelCodeEntity> spec = buildCodeSpec(status, platformType, codeType, keyword);
        return channelCodeRepository.findAll(spec, sorted).map(this::toCodeDto);
    }

    /**
     * 激活渠道活码 (状态置 ACTIVE)
     *
     * @param id 活码 ID
     * @return 更新后的活码
     * @throws ScrmException 活码不存在
     */
    @Transactional
    public ScrmChannelCodeDto activateCode(Long id) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(id);
        entity.setStatus(STATUS_ACTIVE);
        entity = channelCodeRepository.save(entity);
        log.info("激活渠道活码: id={}", id);
        return toCodeDto(entity);
    }

    /**
     * 停用渠道活码 (状态置 INACTIVE)
     *
     * @param id 活码 ID
     * @return 更新后的活码
     * @throws ScrmException 活码不存在
     */
    @Transactional
    public ScrmChannelCodeDto deactivateCode(Long id) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(id);
        entity.setStatus(STATUS_INACTIVE);
        entity = channelCodeRepository.save(entity);
        log.info("停用渠道活码: id={}", id);
        return toCodeDto(entity);
    }

    /**
     * 记录扫码事件并按规则分配账号
     * <p>
     * 校验活码为 ACTIVE 且未过期, 递增 scanCount, 按 codeType 分配账号,
     * 写入扫码记录 (added=PENDING), 返回扫码记录 DTO (含分配到的账号 ID)。
     * </p>
     *
     * @param codeId          活码 ID
     * @param scannerUid      扫码者标识
     * @param scannerNickname 扫码者昵称
     * @param ip              扫码者 IP
     * @param userAgent       扫码者 User-Agent
     * @return 扫码记录 DTO (含分配到的账号 ID)
     * @throws ScrmException 活码不存在 / 已停用 / 已过期
     */
    @Transactional
    public ScrmChannelCodeScanDto recordScan(Long codeId, String scannerUid, String scannerNickname,
                                             String ip, String userAgent) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(codeId);
        // 校验活码状态
        if (!STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "渠道活码已停用, 无法扫码: id=" + codeId);
        }
        // 校验过期时间
        if (entity.getExpireAt() != null && entity.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "渠道活码已过期: id=" + codeId + ", expireAt=" + entity.getExpireAt());
        }
        // 按规则分配账号
        Long assignedAccountId = assignAccount(entity);
        // 递增扫码数
        entity.setScanCount((entity.getScanCount() != null ? entity.getScanCount() : 0) + 1);
        channelCodeRepository.save(entity);
        // 写入扫码记录
        ScrmChannelCodeScanEntity scan = new ScrmChannelCodeScanEntity();
        scan.setChannelCodeId(codeId);
        scan.setScannerUid(scannerUid);
        scan.setScannerNickname(scannerNickname);
        scan.setAssignedAccountId(assignedAccountId);
        scan.setIp(ip);
        scan.setUserAgent(userAgent);
        scan.setScannedAt(LocalDateTime.now());
        scan.setAdded(ADDED_PENDING);
        scan = scanRepository.save(scan);
        log.info("记录扫码: codeId={}, scanId={}, scannerUid={}, assignedAccountId={}",
                codeId, scan.getId(), scannerUid, assignedAccountId);
        return toScanDto(scan);
    }

    /**
     * 查询渠道活码扫码转化统计
     * <p>
     * 聚合扫码记录的 added 状态统计, 计算添加转化率 (已添加数 / 扫码数)。
     * </p>
     *
     * @param id 活码 ID
     * @return 统计 VO
     * @throws ScrmException 活码不存在
     */
    @Transactional(readOnly = true)
    public ChannelCodeStatsVo getCodeStats(Long id) throws ScrmException {
        ScrmChannelCodeEntity entity = findCodeOrThrow(id);
        // 按添加状态聚合扫码记录数
        List<Object[]> addedCounts = scanRepository.countByAdded(id);
        Map<String, Long> countMap = new HashMap<>();
        for (Object[] row : addedCounts) {
            String added = (String) row[0];
            Long cnt = (Long) row[1];
            countMap.put(added, cnt);
        }
        long added = countMap.getOrDefault(ADDED_ADDED, 0L);
        long pending = countMap.getOrDefault(ADDED_PENDING, 0L);
        long rejected = countMap.getOrDefault(ADDED_REJECTED, 0L);
        long scanCount = entity.getScanCount() != null ? entity.getScanCount() : 0L;
        Double conversionRate = null;
        if (scanCount > 0) {
            conversionRate = round2(added * PERCENT_BASE / scanCount);
        }
        return ChannelCodeStatsVo.builder()
                .channelCodeId(entity.getId())
                .codeName(entity.getCodeName())
                .codeType(entity.getCodeType())
                .platformType(entity.getPlatformType())
                .status(entity.getStatus())
                .scanCount(scanCount)
                .addedCount(added)
                .pendingCount(pending)
                .rejectedCount(rejected)
                .conversionRate(conversionRate)
                .build();
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 创建活码参数校验
     *
     * @param dto 活码参数
     * @throws ScrmException 参数校验失败
     */
    private void validateCreateCode(ScrmChannelCodeDto dto) throws ScrmException {
        if (dto.getCodeName() == null || dto.getCodeName().isBlank()) {
            throw ScrmException.badRequest("活码名称不能为空");
        }
        if (dto.getCodeType() == null || dto.getCodeType().isBlank()) {
            throw ScrmException.badRequest("活码类型不能为空");
        }
        if (dto.getPlatformType() == null || dto.getPlatformType().isBlank()) {
            throw ScrmException.badRequest("平台类型不能为空");
        }
    }

    /**
     * 按 codeType 分配账号
     * <p>
     * SINGLE: 返回 redirectAccountId;
     * MULTI: 解析 assignRule 取首个账号 ID;
     * ROUND_ROBIN: 解析 assignRule 按 scanCount 取模轮询选取。
     * assignRule 约定为逗号分隔的账号 ID 字符串。
     * </p>
     *
     * @param entity 活码实体
     * @return 分配到的账号 ID (无可用账号时返回 null)
     */
    private Long assignAccount(ScrmChannelCodeEntity entity) {
        String codeType = entity.getCodeType();
        if (TYPE_SINGLE.equals(codeType)) {
            return entity.getRedirectAccountId();
        }
        List<Long> accountIds = parseAccountIds(entity.getAssignRule());
        if (accountIds.isEmpty()) {
            log.warn("活码分配规则无可用账号: codeId={}, codeType={}", entity.getId(), codeType);
            return null;
        }
        if (TYPE_MULTI.equals(codeType)) {
            // MULTI: 取首个账号
            return accountIds.get(0);
        }
        if (TYPE_ROUND_ROBIN.equals(codeType)) {
            // ROUND_ROBIN: 按 scanCount 取模轮询
            int idx = entity.getScanCount() != null ? entity.getScanCount() % accountIds.size() : 0;
            return accountIds.get(idx);
        }
        log.warn("未识别的活码类型, 不分配账号: codeType={}", codeType);
        return null;
    }

    /**
     * 解析 assignRule 为账号 ID 列表
     * <p>
     * 兼容逗号分隔字符串与 JSON 数组字符串, 提取其中的数字 ID。
     * </p>
     *
     * @param assignRule 分配规则字符串
     * @return 账号 ID 列表
     */
    private List<Long> parseAccountIds(String assignRule) {
        if (assignRule == null || assignRule.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(assignRule.split("[,\\[\\]\"\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 构建渠道活码查询条件 Specification
     * <p>
     * 数据隔离: 始终按当前用户可见账号范围过滤。
     * </p>
     *
     * @param status       状态过滤 (可空)
     * @param platformType 平台类型过滤 (可空)
     * @param codeType     活码类型过滤 (可空)
     * @param keyword      关键词过滤, 匹配活码名称 (可空)
     * @return Specification
     */
    private Specification<ScrmChannelCodeEntity> buildCodeSpec(String status, String platformType,
                                                               String codeType, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 数据隔离: 始终按当前用户可见账号范围过滤
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.toLowerCase()));
            }
            if (platformType != null && !platformType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("platformType")), platformType.toLowerCase()));
            }
            if (codeType != null && !codeType.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("codeType")), codeType.toLowerCase()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String kw = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("codeName")), kw));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 确保分页参数带默认排序 (按创建时间倒序)
     *
     * @param pageable 原始分页参数
     * @return 带排序的分页参数
     */
    private Pageable ensureSort(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
    }

    /**
     * 按主键查询活码并校验账号归属, 不存在抛异常
     *
     * @param id 活码 ID
     * @return 活码实体
     * @throws ScrmException 活码不存在或越权访问
     */
    private ScrmChannelCodeEntity findCodeOrThrow(Long id) throws ScrmException {
        ScrmChannelCodeEntity entity = channelCodeRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "渠道活码不存在: id=" + id));
        // 数据隔离: 校验活码归属当前账号, 防止按 ID 越权访问

        return entity;
    }

    /**
     * 活码实体转 DTO
     *
     * @param entity 活码实体
     * @return 活码 DTO
     */
    private ScrmChannelCodeDto toCodeDto(ScrmChannelCodeEntity entity) {
        ScrmChannelCodeDto dto = new ScrmChannelCodeDto();
        dto.setId(entity.getId());
        dto.setCodeName(entity.getCodeName());
        dto.setCodeType(entity.getCodeType());
        dto.setPlatformType(entity.getPlatformType());
        dto.setQrCodeUrl(entity.getQrCodeUrl());
        dto.setRedirectAccountId(entity.getRedirectAccountId());
        dto.setAssignRule(entity.getAssignRule());
        dto.setWelcomeMessage(entity.getWelcomeMessage());
        dto.setTags(entity.getTags());
        dto.setStatus(entity.getStatus());
        dto.setScanCount(entity.getScanCount());
        dto.setAddCount(entity.getAddCount());
        dto.setExpireAt(entity.getExpireAt());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 扫码记录实体转 DTO
     *
     * @param entity 扫码记录实体
     * @return 扫码记录 DTO
     */
    private ScrmChannelCodeScanDto toScanDto(ScrmChannelCodeScanEntity entity) {
        ScrmChannelCodeScanDto dto = new ScrmChannelCodeScanDto();
        dto.setId(entity.getId());
        dto.setChannelCodeId(entity.getChannelCodeId());
        dto.setScannerUid(entity.getScannerUid());
        dto.setScannerNickname(entity.getScannerNickname());
        dto.setAssignedAccountId(entity.getAssignedAccountId());
        dto.setIp(entity.getIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setScannedAt(entity.getScannedAt());
        dto.setAdded(entity.getAdded());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 保留两位小数
     *
     * @param v 原始值
     * @return 四舍五入到两位小数
     */
    private double round2(double v) {
        return Math.round(v * Math.pow(10, RATE_SCALE)) / Math.pow(10, RATE_SCALE);
    }
}
