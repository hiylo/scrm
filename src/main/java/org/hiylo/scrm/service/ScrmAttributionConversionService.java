/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionConversionService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAttributionConversionDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAttributionConversionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 营销归因转化记录服务。
 * <p>
 * 承载转化记录 / 详情查询 / 多条件分页检索 / 按订单号查询与转化参数校验。
 * 归因字段 (totalTouchpoints 等) 初值为 0, 留待归因计算回填。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionConversionService {

    /** 默认转化窗口 (天) */
    static final int DEFAULT_CONVERSION_WINDOW_DAYS = 7;

    /** 合法的转化类型 */
    private static final List<String> VALID_CONVERSION_TYPES = List.of(
            "PURCHASE", "SIGNUP", "FORM_SUBMIT", "APPOINTMENT", "DOWNLOAD",
            "ADD_TO_CART", "CHECKOUT", "UPGRADE", "RENEWAL", "CUSTOM");

    /** 归因转化数据访问层 */
    private final ScrmAttributionConversionRepository conversionRepository;

    /** JSON 解析器 (校验 metadata) */
    private final ObjectMapper objectMapper;

    /**
     * 记录转化。
     * <p>校验 conversionType 合法性后写入归属账号 ID 持久化,
     * conversionValue / conversionCount 缺省填默认值, 归因字段 (totalTouchpoints 等) 初值为 0,
     * 留待归因计算回填。</p>
     *
     * @param dto 转化参数
     * @return 创建后的转化
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmAttributionConversionEntity recordConversion(
            ScrmAttributionConversionDto dto) throws ScrmException {
        validateConversionDto(dto);
        ScrmAttributionConversionEntity entity = new ScrmAttributionConversionEntity();
        entity.setCustomerId(dto.getCustomerId());
        entity.setCustomerName(dto.getCustomerName());
        entity.setConversionType(dto.getConversionType());
        entity.setConversionTime(dto.getConversionTime());
        entity.setConversionValue(dto.getConversionValue() != null ? dto.getConversionValue() : 0.0);
        entity.setConversionCount(dto.getConversionCount() != null ? dto.getConversionCount() : 1);
        entity.setOrderId(dto.getOrderId());
        entity.setMetadata(dto.getMetadata());
        entity.setTotalTouchpoints(0);
        entity.setAttributedTouchpoints(0);
        entity.setConversionWindowDays(DEFAULT_CONVERSION_WINDOW_DAYS);
        entity = conversionRepository.save(entity);
        log.info("记录归因转化: id={}, customerId={}, type={}, value={}",
                entity.getId(), entity.getCustomerId(), entity.getConversionType(), entity.getConversionValue());
        return entity;
    }

    /**
     * 查询转化详情。
     *
     * @param id 转化 ID
     * @return 转化实体
     * @throws ScrmException 转化不存在
     */
    @Transactional(readOnly = true)
    public ScrmAttributionConversionEntity getConversion(Long id) throws ScrmException {
        return findConversionOrThrow(id);
    }

    /**
     * 分页查询转化, 支持按客户 / 转化类型 / 模型 / 时间范围过滤。
     *
     * @param customerId     客户 ID 过滤（可空）
     * @param conversionType 转化类型过滤（可空）
     * @param modelId        归因模型 ID 过滤（可空）
     * @param startTime      转化时间起点 (含, 可空)
     * @param endTime        转化时间终点 (含, 可空)
     * @param pageable       分页参数
     * @return 转化分页结果 (按 conversionTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmAttributionConversionEntity> listConversions(Long customerId, String conversionType,
                                                                   Long modelId, LocalDateTime startTime,
                                                                   LocalDateTime endTime, Pageable pageable) {
        Specification<ScrmAttributionConversionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (conversionType != null && !conversionType.isBlank()) {
                predicates.add(cb.equal(root.get("conversionType"), conversionType));
            }
            if (modelId != null) {
                predicates.add(cb.equal(root.get("modelId"), modelId));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("conversionTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("conversionTime"), endTime));
            }
            query.orderBy(cb.desc(root.get("conversionTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return conversionRepository.findAll(spec, ScrmAttributionModelService.ensureSort(pageable, "conversionTime"));
    }

    /**
     * 按订单号查询转化。
     *
     * @param orderId 订单号
     * @return 转化实体
     * @throws ScrmException 转化不存在
     */
    @Transactional(readOnly = true)
    public ScrmAttributionConversionEntity getConversionByOrder(String orderId) throws ScrmException {
        if (orderId == null || orderId.isBlank()) {
            throw ScrmException.badRequest("订单号不能为空");
        }
        return conversionRepository
                .findFirstByOrderIdOrderByConversionTimeDesc(orderId)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "转化不存在: orderId=" + orderId));
    }

    /**
     * 按主键查询转化, 不存在抛异常, 并校验账号归属。
     * <p>供归因报告兄弟类共用。</p>
     *
     * @param id 转化 ID
     * @return 转化实体
     * @throws ScrmException 转化不存在
     */
    ScrmAttributionConversionEntity findConversionOrThrow(Long id) throws ScrmException {
        ScrmAttributionConversionEntity entity = conversionRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "归因转化不存在: id=" + id));
        return entity;
    }

    /**
     * 校验转化参数。
     *
     * @param dto 转化参数
     * @throws ScrmException 参数非法
     */
    private void validateConversionDto(ScrmAttributionConversionDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("转化参数不能为空");
        }
        if (dto.getCustomerId() == null) {
            throw ScrmException.badRequest("客户 ID 不能为空");
        }
        if (dto.getConversionType() == null || dto.getConversionType().isBlank()) {
            throw ScrmException.badRequest("转化类型不能为空");
        }
        if (!VALID_CONVERSION_TYPES.contains(dto.getConversionType())) {
            throw ScrmException.badRequest(
                    "转化类型非法: " + dto.getConversionType() + ", 仅支持 " + VALID_CONVERSION_TYPES);
        }
        if (dto.getConversionTime() == null) {
            throw ScrmException.badRequest("转化时间不能为空");
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
