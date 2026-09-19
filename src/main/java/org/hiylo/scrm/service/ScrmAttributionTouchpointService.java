/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionTouchpointService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAttributionTouchpointDto;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAttributionTouchpointRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 营销归因触点记录服务。
 * <p>
 * 承载触点记录 / 批量记录 / 详情查询 / 多条件分页检索 / 客户触点链查询 /
 * 回溯窗口内触点查询与触点参数校验。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionTouchpointService {

    /** 合法的触点类型 */
    private static final List<String> VALID_TOUCHPOINT_TYPES = List.of(
            "AD_CLICK", "AD_VIEW", "EMAIL_OPEN", "EMAIL_CLICK", "SMS_CLICK",
            "WECHAT_MESSAGE", "WEB_VISIT", "SEARCH", "REFERRAL", "SOCIAL_POST",
            "DIRECT", "STORE_VISIT", "CALL", "CONTENT_VIEW");

    /** 合法的渠道 */
    private static final List<String> VALID_CHANNELS = List.of(
            "SEARCH", "SOCIAL", "EMAIL", "SMS", "WECHAT", "DIRECT", "REFERRAL",
            "STORE", "AD", "OTHER");

    /** 归因触点数据访问层 */
    private final ScrmAttributionTouchpointRepository touchpointRepository;

    /** JSON 解析器 (校验 metadata) */
    private final ObjectMapper objectMapper;

    /**
     * 记录触点。
     * <p>校验 touchpointType / channel 合法性后写入归属账号 ID 持久化,
     * touchpointTime 缺省取当前时间, touchpointValue 缺省 0, isAttributed 缺省 false。
     * touchpointOrder 缺省由服务端按客户触点链长度 +1 回填。</p>
     *
     * @param dto 触点参数
     * @return 创建后的触点
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAttributionTouchpointEntity recordTouchpoint(
            ScrmAttributionTouchpointDto dto) throws ScrmException {
        validateTouchpointDto(dto);
        LocalDateTime touchpointTime = dto.getTouchpointTime() != null
                ? dto.getTouchpointTime() : LocalDateTime.now();
        ScrmAttributionTouchpointEntity entity = new ScrmAttributionTouchpointEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setTouchpointOrder(dto.getTouchpointOrder() != null
                ? dto.getTouchpointOrder() : nextTouchpointOrder(dto.getCustomerId()));
        entity.setTouchpointType(dto.getTouchpointType());
        entity.setChannel(dto.getChannel());
        entity.setCampaignId(dto.getCampaignId());
        entity.setCampaignName(dto.getCampaignName());
        entity.setContentId(dto.getContentId());
        entity.setTouchpointTime(touchpointTime);
        entity.setTouchpointValue(dto.getTouchpointValue() != null ? dto.getTouchpointValue() : 0.0);
        entity.setUtmSource(dto.getUtmSource());
        entity.setUtmMedium(dto.getUtmMedium());
        entity.setUtmCampaign(dto.getUtmCampaign());
        entity.setUtmContent(dto.getUtmContent());
        entity.setUtmTerm(dto.getUtmTerm());
        entity.setLandingPage(dto.getLandingPage());
        entity.setReferrer(dto.getReferrer());
        entity.setDeviceType(dto.getDeviceType());
        entity.setSessionId(dto.getSessionId());
        entity.setMetadata(dto.getMetadata());
        entity = touchpointRepository.save(entity);
        log.info("记录归因触点: id={}, customerId={}, type={}, channel={}",
                entity.getId(), entity.getCustomerId(), entity.getTouchpointType(), entity.getChannel());
        return entity;
    }

    /**
     * 批量记录触点。
     *
     * @param dtos 触点参数列表
     * @return 创建后的触点列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmAttributionTouchpointEntity> batchRecordTouchpoints(List<ScrmAttributionTouchpointDto> dtos)
            throws ScrmException {
        if (dtos == null || dtos.isEmpty()) {
            throw ScrmException.badRequest("触点记录列表不能为空");
        }
        List<ScrmAttributionTouchpointEntity> result = new ArrayList<>(dtos.size());
        for (ScrmAttributionTouchpointDto dto : dtos) {
            result.add(recordTouchpoint(dto));
        }
        log.info("批量记录归因触点: count={}", dtos.size());
        return result;
    }

    /**
     * 查询触点详情。
     *
     * @param id 触点 ID
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    @Transactional(readOnly = true)
    public ScrmAttributionTouchpointEntity getTouchpoint(Long id) throws ScrmException {
        return findTouchpointOrThrow(id);
    }

    /**
     * 分页查询触点, 支持按客户 / 触点类型 / 渠道 / 营销活动 / 时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param touchpointType 触点类型过滤（可空）
     * @param channel        渠道过滤（可空）
     * @param campaignId     营销活动 ID 过滤（可空）
     * @param startTime      触点时间起点 (含, 可空)
     * @param endTime        触点时间终点 (含, 可空)
     * @param pageable       分页参数
     * @return 触点分页结果 (按 touchpointTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAttributionTouchpointEntity> listTouchpoints(Long customerId, String touchpointType,
                                                                   String channel, Long campaignId,
                                                                   LocalDateTime startTime, LocalDateTime endTime,
                                                                   Pageable pageable) {
        Specification<ScrmAttributionTouchpointEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (touchpointType != null && !touchpointType.isBlank()) {
                predicates.add(cb.equal(root.get("touchpointType"), touchpointType));
            }
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (campaignId != null) {
                predicates.add(cb.equal(root.get("campaignId"), campaignId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("touchpointTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("touchpointTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("touchpointTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return touchpointRepository.findAll(spec, ScrmAttributionModelService.ensureSort(pageable, "touchpointTime"));
    }

    /**
     * 客户触点链: 按客户与时间范围查询触点 (按触点时间升序)。
     *
     * @param customerId 客户 ID
     * @return 触点列表 (touchpointTime ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmAttributionTouchpointEntity> getCustomerTouchpoints(Long customerId) {
        if (customerId == null) {
            return List.of();
        }
        return touchpointRepository.findCustomerTouchpointChain(
                 customerId, null, null);
    }

    /**
     * 回溯窗口内触点: 查询客户在转化时间前 lookbackDays 天内的触点 (按触点时间升序)。
     *
     * @param customerId     客户 ID
     * @param conversionTime 转化时间 (回溯窗口终点)
     * @param lookbackDays   回溯天数
     * @return 触点列表 (touchpointTime ASC)
     */
    @Transactional(readOnly = true)
    public List<ScrmAttributionTouchpointEntity> getTouchpointsInWindow(Long customerId,
                                                                          LocalDateTime conversionTime,
                                                                          int lookbackDays) {
        if (customerId == null || conversionTime == null || lookbackDays <= 0) {
            return List.of();
        }
        LocalDateTime startTime = conversionTime.minusDays(lookbackDays);
        return touchpointRepository.findCustomerTouchpointChain(
                 customerId, startTime, conversionTime);
    }

    /**
     * 按主键查询触点, 不存在抛异常, 并校验账号归属。
     *
     * @param id 触点 ID
     * @return 触点实体
     * @throws ScrmException 触点不存在
     */
    private ScrmAttributionTouchpointEntity findTouchpointOrThrow(Long id) throws ScrmException {
        ScrmAttributionTouchpointEntity entity = touchpointRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归因触点不存在: id=" + id));
        return entity;
    }

    /**
     * 计算客户触点链下一个顺序号 (按客户已有触点数 +1)。
     *
     * @param customerId 客户 ID
     * @return 下一触点顺序号
     */
    private int nextTouchpointOrder(Long customerId) {
        List<ScrmAttributionTouchpointEntity> chain = touchpointRepository.findCustomerTouchpointChain(
                 customerId, null, null);
        return chain.size() + 1;
    }

    /**
     * 校验触点参数。
     *
     * @param dto 触点参数
     * @throws ScrmException 参数非法
     */
    private void validateTouchpointDto(ScrmAttributionTouchpointDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("触点参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getTouchpointType() == null || dto.getTouchpointType().isBlank()) {
            throw ScrmException.badRequest("触点类型不能为空");
        }
        if (!VALID_TOUCHPOINT_TYPES.contains(dto.getTouchpointType())) {
            throw ScrmException.badRequest(
                    "触点类型非法: " + dto.getTouchpointType() + ", 仅支持 " + VALID_TOUCHPOINT_TYPES);
        }
        if (dto.getChannel() == null || dto.getChannel().isBlank()) {
            throw ScrmException.badRequest("渠道不能为空");
        }
        if (!VALID_CHANNELS.contains(dto.getChannel())) {
            throw ScrmException.badRequest(
                    "渠道非法: " + dto.getChannel() + ", 仅支持 " + VALID_CHANNELS);
        }
        if (dto.getMetadata() != null && !dto.getMetadata().isBlank()) {
            try {
                objectMapper.readTree(dto.getMetadata());
            } catch (Exception e) {
                throw ScrmException.badRequest("附加数据 metadata JSON 解析失败: " + e.getMessage());
            }
        }
    }
}
