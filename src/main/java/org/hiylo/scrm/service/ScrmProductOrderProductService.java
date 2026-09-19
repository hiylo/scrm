/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderProductService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmProductDto;
import org.hiylo.scrm.entity.ScrmProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * SCRM 商品管理服务 (产品子域)。
 * <p>
 * 承载商品管理子域: 商品增删改查 / 按编码查询 / 库存调整 / 状态更新 / 批量导入, 同时托管
 * 商品状态与默认值常量以及按主键查询商品能力, 供订单 / 订单项 / 统计兄弟类以 package 级
 * 访问复用。门面 {@link ScrmProductOrderService} 委托本类实现产品管理。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmProductOrderProductService {

    // ==================== 商品状态常量 (共享) ====================

    /** 商品状态: 在售 */
    static final String PRODUCT_STATUS_ACTIVE = "ACTIVE";
    /** 商品状态: 下架 */
    static final String PRODUCT_STATUS_INACTIVE = "INACTIVE";
    /** 商品状态: 停产 */
    static final String PRODUCT_STATUS_DISCONTINUED = "DISCONTINUED";

    // ==================== 默认值 ====================

    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 默认库存 */
    private static final int DEFAULT_STOCK = 0;
    /** 默认销量 */
    private static final int DEFAULT_SALES_COUNT = 0;
    /** 默认浏览量 */
    private static final int DEFAULT_VIEW_COUNT = 0;
    /** 默认评分 */
    private static final double DEFAULT_RATING_SCORE = 0d;

    /** 商品数据访问层 */
    private final ScrmProductRepository productRepository;

    // ============================================================
    // 商品管理
    // ============================================================

    /**
     * 创建商品。
     * <p>校验商品编码唯一性后写入归属账号 ID 持久化, 状态缺省 ACTIVE, 计数字段缺省 0。</p>
     *
     * @param dto 商品参数
     * @return 创建后的商品
     * @throws ScrmException 参数非法 / 商品编码已存在
     */
    @Transactional
    public ScrmProductDto createProduct(ScrmProductDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("商品参数不能为空");
        }
        if (productRepository.findByProductCode(dto.getProductCode()).isPresent()) {
            throw new ScrmException(ScrmExceptionConstants.CONFLICT,
                    "商品编码已存在: productCode=" + dto.getProductCode());
        }
        ScrmProductEntity entity = new ScrmProductEntity();
        entity.setProductCode(dto.getProductCode());
        entity.setProductName(dto.getProductName());
        entity.setCategory(dto.getCategory());
        entity.setBrand(dto.getBrand());
        entity.setSpec(dto.getSpec());
        entity.setDescription(dto.getDescription());
        entity.setPrice(dto.getPrice());
        entity.setOriginalPrice(dto.getOriginalPrice());
        entity.setCost(dto.getCost());
        entity.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : DEFAULT_CURRENCY);
        entity.setStock(dto.getStock() != null ? dto.getStock() : DEFAULT_STOCK);
        entity.setUnit(dto.getUnit());
        entity.setImageUrl(dto.getImageUrl());
        entity.setImages(dto.getImages());
        entity.setTags(dto.getTags());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : PRODUCT_STATUS_ACTIVE);
        entity.setSku(dto.getSku());
        entity.setBarcode(dto.getBarcode());
        entity.setWeight(dto.getWeight());
        entity.setSalesCount(DEFAULT_SALES_COUNT);
        entity.setViewCount(DEFAULT_VIEW_COUNT);
        entity.setRatingScore(DEFAULT_RATING_SCORE);
        entity.setCreatedBy(dto.getCreatedBy());
        entity = productRepository.save(entity);
        log.info("创建商品: id={}, productCode={}", entity.getId(), entity.getProductCode());
        return toProductDto(entity);
    }

    /**
     * 更新商品（字段非空才覆盖）。
     * <p>商品编码不允许修改, 避免与已有订单项快照不一致。</p>
     *
     * @param id  商品 ID
     * @param dto 商品参数
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 商品编码已存在
     */
    @Transactional
    public ScrmProductDto updateProduct(Long id, ScrmProductDto dto) throws ScrmException {
        ScrmProductEntity entity = findProductOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("商品参数不能为空");
        }
        // 商品编码若变更需校验唯一性
        if (dto.getProductCode() != null && !dto.getProductCode().equals(entity.getProductCode())) {
            throw ScrmException.badRequest("商品编码不允许修改: productCode=" + entity.getProductCode());
        }
        if (dto.getProductName() != null) entity.setProductName(dto.getProductName());
        if (dto.getCategory() != null) entity.setCategory(dto.getCategory());
        if (dto.getBrand() != null) entity.setBrand(dto.getBrand());
        if (dto.getSpec() != null) entity.setSpec(dto.getSpec());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getPrice() != null) entity.setPrice(dto.getPrice());
        if (dto.getOriginalPrice() != null) entity.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getCost() != null) entity.setCost(dto.getCost());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getStock() != null) entity.setStock(dto.getStock());
        if (dto.getUnit() != null) entity.setUnit(dto.getUnit());
        if (dto.getImageUrl() != null) entity.setImageUrl(dto.getImageUrl());
        if (dto.getImages() != null) entity.setImages(dto.getImages());
        if (dto.getTags() != null) entity.setTags(dto.getTags());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getSku() != null) entity.setSku(dto.getSku());
        if (dto.getBarcode() != null) entity.setBarcode(dto.getBarcode());
        if (dto.getWeight() != null) entity.setWeight(dto.getWeight());
        if (dto.getSalesCount() != null) entity.setSalesCount(dto.getSalesCount());
        if (dto.getViewCount() != null) entity.setViewCount(dto.getViewCount());
        if (dto.getRatingScore() != null) entity.setRatingScore(dto.getRatingScore());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = productRepository.save(entity);
        log.info("更新商品: id={}", id);
        return toProductDto(entity);
    }

    /**
     * 删除商品。
     * <p>已停用商品方可删除, 在售商品请先下架。</p>
     *
     * @param id 商品 ID
     * @throws ScrmException 商品不存在 / 在售商品不允许删除
     */
    @Transactional
    public void deleteProduct(Long id) throws ScrmException {
        ScrmProductEntity entity = findProductOrThrow(id);
        if (PRODUCT_STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "在售商品不允许删除, 请先下架: id=" + id);
        }
        productRepository.delete(entity);
        log.info("删除商品: id={}", id);
    }

    /**
     * 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    @Transactional(readOnly = true)
    public ScrmProductDto getProduct(Long id) throws ScrmException {
        return toProductDto(findProductOrThrow(id));
    }

    /**
     * 按商品编码查询商品。
     *
     * @param code 商品编码
     * @return 商品 DTO
     * @throws ScrmException 商品不存在
     */
    @Transactional(readOnly = true)
    public ScrmProductDto getProductByCode(String code) throws ScrmException {
        return toProductDto(findProductByCodeOrThrow(code));
    }

    /**
     * 分页查询商品, 支持按分类、品牌、状态与关键词过滤。
     * <p>关键词匹配商品编码、名称、SKU 与条码。</p>
     *
     * @param category 分类过滤 (可空)
     * @param brand    品牌过滤 (可空)
     * @param status   状态过滤 (可空)
     * @param keyword  关键词过滤 (可空)
     * @param pageable 分页参数
     * @return 商品分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmProductDto> listProducts(String category, String brand, String status,
                                                        String keyword, Pageable pageable) {
        Specification<ScrmProductEntity> spec = this.buildProductSpec(category, brand, status, keyword);
        return productRepository.findAll(spec, pageable).map(this::toProductDto);
    }

    /**
     * 调整库存 (delta 可正可负, 正数为入库, 负数为出库)。
     *
     * @param id     商品 ID
     * @param delta  库存变化量 (正入库 / 负出库)
     * @param reason 调整原因 (可空)
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 库存不足
     */
    @Transactional
    public ScrmProductDto adjustStock(Long id, int delta, String reason) throws ScrmException {
        ScrmProductEntity entity = findProductOrThrow(id);
        int current = entity.getStock() != null ? entity.getStock() : 0;
        int target = current + delta;
        if (target < 0) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "库存不足: current=" + current + ", delta=" + delta);
        }
        entity.setStock(target);
        entity = productRepository.save(entity);
        log.info("调整库存: id={}, delta={}, reason={}, current={}", id, delta, reason, target);
        return toProductDto(entity);
    }

    /**
     * 更新商品状态 (ACTIVE / INACTIVE / DISCONTINUED)。
     *
     * @param id     商品 ID
     * @param status 目标状态
     * @return 更新后的商品
     * @throws ScrmException 商品不存在 / 状态非法
     */
    @Transactional
    public ScrmProductDto updateStatus(Long id, String status) throws ScrmException {
        if (status == null || status.isBlank()) {
            throw ScrmException.badRequest("商品状态不能为空");
        }
        if (!PRODUCT_STATUS_ACTIVE.equals(status) && !PRODUCT_STATUS_INACTIVE.equals(status) && !PRODUCT_STATUS_DISCONTINUED.equals(status)) {
            throw ScrmException.badRequest("商品状态非法: status=" + status);
        }
        ScrmProductEntity entity = findProductOrThrow(id);
        entity.setStatus(status);
        entity = productRepository.save(entity);
        log.info("更新商品状态: id={}, status={}", id, status);
        return toProductDto(entity);
    }

    /**
     * 批量导入商品。
     * <p>已存在的商品编码跳过 (幂等), 返回成功导入的商品列表。</p>
     *
     * @param products 商品参数列表
     * @return 导入成功的商品列表
     * @throws ScrmException 参数非法
     */
    @Transactional
    public List<ScrmProductDto> batchImport(List<ScrmProductDto> products) throws ScrmException {
        if (products == null || products.isEmpty()) {
            throw ScrmException.badRequest("导入商品列表不能为空");
        }
        List<ScrmProductDto> result = new ArrayList<>();
        for (ScrmProductDto dto : products) {
            if (dto == null || dto.getProductCode() == null || dto.getProductCode().isBlank()) {
                continue;
            }
            // 幂等: 已存在的编码跳过
            if (productRepository.findByProductCode(dto.getProductCode()).isPresent()) {
                continue;
            }
            result.add(createProduct(dto));
        }
        log.info("批量导入商品: total={}, imported={}", products.size(), result.size());
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建商品查询条件 Specification。
     */
    private Specification<ScrmProductEntity> buildProductSpec(String category, String brand, String status,
                                                               String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(root.get("brand"), brand));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("productCode")), like),
                        cb.like(cb.lower(root.get("productName")), like),
                        cb.like(cb.lower(root.get("sku")), like),
                        cb.like(cb.lower(root.get("barcode")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 按主键查询商品, 不存在抛异常
     */
    ScrmProductEntity findProductOrThrow(Long id) throws ScrmException {
        ScrmProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "商品不存在: id=" + id));
        return product;
    }

    /**
     * 按商品编码查询商品, 不存在抛异常
     */
    private ScrmProductEntity findProductByCodeOrThrow(String code) throws ScrmException {
        ScrmProductEntity product = productRepository.findByProductCode(
                        code)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "商品不存在: productCode=" + code));
        return product;
    }

    /**
     * 商品实体转 DTO
     */
    private ScrmProductDto toProductDto(ScrmProductEntity entity) {
        ScrmProductDto dto = new ScrmProductDto();
        dto.setId(entity.getId());
        dto.setProductCode(entity.getProductCode());
        dto.setProductName(entity.getProductName());
        dto.setCategory(entity.getCategory());
        dto.setBrand(entity.getBrand());
        dto.setSpec(entity.getSpec());
        dto.setDescription(entity.getDescription());
        dto.setPrice(entity.getPrice());
        dto.setOriginalPrice(entity.getOriginalPrice());
        dto.setCost(entity.getCost());
        dto.setCurrency(entity.getCurrency());
        dto.setStock(entity.getStock());
        dto.setUnit(entity.getUnit());
        dto.setImageUrl(entity.getImageUrl());
        dto.setImages(entity.getImages());
        dto.setTags(entity.getTags());
        dto.setStatus(entity.getStatus());
        dto.setSku(entity.getSku());
        dto.setBarcode(entity.getBarcode());
        dto.setWeight(entity.getWeight());
        dto.setSalesCount(entity.getSalesCount());
        dto.setViewCount(entity.getViewCount());
        dto.setRatingScore(entity.getRatingScore());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }
}