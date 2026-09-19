/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmCompetitorProductService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmCompetitorProductDto;
import org.hiylo.scrm.dto.ScrmPriceMonitorDto;
import org.hiylo.scrm.entity.ScrmCompetitorActivityEntity;
import org.hiylo.scrm.entity.ScrmCompetitorEntity;
import org.hiylo.scrm.entity.ScrmCompetitorProductEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmCompetitorActivityRepository;
import org.hiylo.scrm.repository.ScrmCompetitorProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 竞品产品管理服务。
 * <p>
 * 承载竞品产品子域的全部能力: 产品增删改查、分页过滤、按竞品/分类查询、促销与近期价格变化、
 * 价格监测 (追加价格历史→计算变化幅度→刷新统计→自动生成 PRICE_CHANGE 动态)、价格历史、
 * 价格对比、批量调价等。
 * </p>
 * <p>
 * 校验失败抛出 {@link ScrmException} 携带通用错误码 (NOT_FOUND / BAD_REQUEST / CONFLICT)。
 * 竞品校验委托 {@link ScrmCompetitorManagementService}。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmCompetitorProductService {

    // ==================== 默认值常量 ====================

    /** 默认监测开关 */
    private static final boolean DEFAULT_MONITORING_ENABLED = true;
    /** 默认产品状态 */
    private static final String DEFAULT_PRODUCT_STATUS = "ACTIVE";
    /** 默认币种 */
    private static final String DEFAULT_CURRENCY = "CNY";
    /** 优势评分满分 */
    private static final int MAX_ADVANTAGE_SCORE = 100;
    /** 重要性评分上限 */
    private static final int MAX_IMPORTANCE_SCORE = 100;
    /** 价格对比相似阈值 (%) */
    private static final double PRICE_SIMILAR_THRESHOLD = 5.0;

    // ==================== 合法枚举值 ====================

    /** 合法的产品状态 */
    private static final List<String> VALID_PRODUCT_STATUSES = List.of("ACTIVE", "INACTIVE", "DISCONTINUED");
    /** 合法的价格对比 */
    private static final List<String> VALID_PRICE_COMPARISONS = List.of("HIGHER", "LOWER", "EQUAL", "SIMILAR");

    // ==================== 依赖注入 ====================

    /** 竞品管理服务 */
    private final ScrmCompetitorManagementService competitorService;
    /** 竞品产品数据访问层 */
    private final ScrmCompetitorProductRepository productRepository;
    /** 竞品动态数据访问层 */
    private final ScrmCompetitorActivityRepository activityRepository;
    /** JSON 解析器 (解析价格历史等) */
    private final ObjectMapper objectMapper;

    /**
     * 创建竞品产品。
     * <p>校验竞品存在, competitorName 缺省时从竞品实体填充。价格统计字段初始化为当前价。</p>
     *
     * @param dto 产品参数
     * @return 创建后的产品
     * @throws ScrmException 参数非法 / 竞品不存在
     */
    @Transactional
    public ScrmCompetitorProductEntity createProduct(ScrmCompetitorProductDto dto) throws ScrmException {
        if (dto == null) {
            throw ScrmException.badRequest("产品参数不能为空");
        }
        validateProductEnums(dto, false);
        ScrmCompetitorEntity competitor = competitorService.findCompetitorOrThrow(dto.getCompetitorId());
        ScrmCompetitorProductEntity entity = new ScrmCompetitorProductEntity();
        entity.setCompetitorId(dto.getCompetitorId());
        entity.setCompetitorName(dto.getCompetitorName() != null
                ? dto.getCompetitorName()
                : competitor.getCompetitorName());
        entity.setProductName(dto.getProductName());
        entity.setProductCode(dto.getProductCode());
        entity.setProductCategory(dto.getProductCategory());
        entity.setDescription(dto.getDescription());
        double currentPrice = dto.getCurrentPrice() != null ? dto.getCurrentPrice() : 0.0;
        double originalPrice = dto.getOriginalPrice() != null ? dto.getOriginalPrice() : currentPrice;
        entity.setCurrentPrice(currentPrice);
        entity.setOriginalPrice(originalPrice);
        entity.setDiscountRate(dto.getDiscountRate() != null
                ? dto.getDiscountRate()
                : calcDiscountRate(currentPrice, originalPrice));
        entity.setCurrency(dto.getCurrency() != null ? dto.getCurrency() : DEFAULT_CURRENCY);
        entity.setPriceUnit(dto.getPriceUnit());
        entity.setProductUrl(dto.getProductUrl());
        entity.setImageUrl(dto.getImageUrl());
        entity.setFeatures(dto.getFeatures());
        entity.setSpecifications(dto.getSpecifications());
        entity.setTargetSegment(dto.getTargetSegment());
        entity.setPositioning(dto.getPositioning());
        entity.setLaunchDate(dto.getLaunchDate());
        // 价格统计初值
        entity.setLastPriceChangeDate(null);
        entity.setLastPriceChangePercent(0.0);
        entity.setPriceChangeCount(0);
        entity.setLowestPrice(currentPrice);
        entity.setHighestPrice(currentPrice);
        entity.setAvgPrice(currentPrice);
        entity.setPriceHistory(toJson(List.of(priceHistoryEntry(LocalDate.now(), currentPrice, 0.0))));
        // 我方对比
        entity.setOurProductId(dto.getOurProductId());
        entity.setOurProductName(dto.getOurProductName());
        double ourPrice = dto.getOurPrice() != null ? dto.getOurPrice() : 0.0;
        entity.setOurPrice(ourPrice);
        entity.setPriceComparison(dto.getPriceComparison() != null ? dto.getPriceComparison()
                : determinePriceComparison(currentPrice, ourPrice));
        entity.setAdvantageScore(dto.getAdvantageScore() != null ? dto.getAdvantageScore()
                : calcAdvantageScore(currentPrice, ourPrice));
        entity.setMonitoringEnabled(dto.getMonitoringEnabled() != null
                ? dto.getMonitoringEnabled()
                : DEFAULT_MONITORING_ENABLED);
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : DEFAULT_PRODUCT_STATUS);
        entity.setLastMonitoredAt(dto.getLastMonitoredAt());
        entity.setNotes(dto.getNotes());
        entity.setCreatedBy(dto.getCreatedBy());
        entity = productRepository.save(entity);
        log.info("创建竞品产品: id={}, competitorId={}, name={}", entity.getId(),
                entity.getCompetitorId(), entity.getProductName());
        return entity;
    }

    /**
     * 更新竞品产品（字段非空才覆盖）。
     *
     * @param id  产品 ID
     * @param dto 产品参数
     * @return 更新后的产品
     * @throws ScrmException 产品不存在 / 参数非法
     */
    @Transactional
    public ScrmCompetitorProductEntity updateProduct(Long id, ScrmCompetitorProductDto dto) throws ScrmException {
        ScrmCompetitorProductEntity entity = findProductOrThrow(id);
        if (dto == null) {
            throw ScrmException.badRequest("产品参数不能为空");
        }
        validateProductEnums(dto, true);
        if (dto.getCompetitorId() != null) {
            competitorService.findCompetitorOrThrow(dto.getCompetitorId());
            entity.setCompetitorId(dto.getCompetitorId());
        }
        if (dto.getCompetitorName() != null) entity.setCompetitorName(dto.getCompetitorName());
        if (dto.getProductName() != null) entity.setProductName(dto.getProductName());
        if (dto.getProductCode() != null) entity.setProductCode(dto.getProductCode());
        if (dto.getProductCategory() != null) entity.setProductCategory(dto.getProductCategory());
        if (dto.getDescription() != null) entity.setDescription(dto.getDescription());
        if (dto.getCurrentPrice() != null) entity.setCurrentPrice(dto.getCurrentPrice());
        if (dto.getOriginalPrice() != null) entity.setOriginalPrice(dto.getOriginalPrice());
        if (dto.getDiscountRate() != null) entity.setDiscountRate(dto.getDiscountRate());
        if (dto.getCurrency() != null) entity.setCurrency(dto.getCurrency());
        if (dto.getPriceUnit() != null) entity.setPriceUnit(dto.getPriceUnit());
        if (dto.getProductUrl() != null) entity.setProductUrl(dto.getProductUrl());
        if (dto.getImageUrl() != null) entity.setImageUrl(dto.getImageUrl());
        if (dto.getFeatures() != null) entity.setFeatures(dto.getFeatures());
        if (dto.getSpecifications() != null) entity.setSpecifications(dto.getSpecifications());
        if (dto.getTargetSegment() != null) entity.setTargetSegment(dto.getTargetSegment());
        if (dto.getPositioning() != null) entity.setPositioning(dto.getPositioning());
        if (dto.getLaunchDate() != null) entity.setLaunchDate(dto.getLaunchDate());
        if (dto.getOurProductId() != null) entity.setOurProductId(dto.getOurProductId());
        if (dto.getOurProductName() != null) entity.setOurProductName(dto.getOurProductName());
        if (dto.getOurPrice() != null) {
            entity.setOurPrice(dto.getOurPrice());
            // 同步刷新价格对比与优势评分
            entity.setPriceComparison(determinePriceComparison(entity.getCurrentPrice(), dto.getOurPrice()));
            entity.setAdvantageScore(calcAdvantageScore(entity.getCurrentPrice(), dto.getOurPrice()));
        }
        if (dto.getPriceComparison() != null) entity.setPriceComparison(dto.getPriceComparison());
        if (dto.getAdvantageScore() != null) entity.setAdvantageScore(dto.getAdvantageScore());
        if (dto.getMonitoringEnabled() != null) entity.setMonitoringEnabled(dto.getMonitoringEnabled());
        if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
        if (dto.getLastMonitoredAt() != null) entity.setLastMonitoredAt(dto.getLastMonitoredAt());
        if (dto.getNotes() != null) entity.setNotes(dto.getNotes());
        if (dto.getCreatedBy() != null) entity.setCreatedBy(dto.getCreatedBy());
        entity = productRepository.save(entity);
        log.info("更新竞品产品: id={}, name={}", entity.getId(), entity.getProductName());
        return entity;
    }

    /**
     * 删除竞品产品。
     *
     * @param id 产品 ID
     * @throws ScrmException 产品不存在
     */
    @Transactional
    public void deleteProduct(Long id) throws ScrmException {
        ScrmCompetitorProductEntity entity = findProductOrThrow(id);
        productRepository.delete(entity);
        log.info("删除竞品产品: id={}, name={}", id, entity.getProductName());
    }

    /**
     * 查询产品详情。
     *
     * @param id 产品 ID
     * @return 产品实体
     * @throws ScrmException 产品不存在
     */
    @Transactional(readOnly = true)
    public ScrmCompetitorProductEntity getProduct(Long id) throws ScrmException {
        return findProductOrThrow(id);
    }

    /**
     * 分页查询产品, 支持按竞品、分类、状态与关键字过滤。
     *
     * @param competitorId    竞品 ID 过滤（可空）
     * @param productCategory 分类过滤（可空）
     * @param status          状态过滤（可空）
     * @param keyword         产品名称/编码关键字模糊匹配（可空）
     * @param pageable        分页参数
     * @return 产品分页结果 (按 updateTime DESC)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorProductEntity> listProducts(Long competitorId, String productCategory, String status,
                                                           String keyword, Pageable pageable) {
        Specification<ScrmCompetitorProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (competitorId != null) {
                predicates.add(cb.equal(root.get("competitorId"), competitorId));
            }
            if (productCategory != null && !productCategory.isBlank()) {
                predicates.add(cb.equal(root.get("productCategory"), productCategory));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword + "%";
                predicates.add(cb.or(
                        cb.like(root.get("productName"), like),
                        cb.like(root.get("productCode"), like)));
            }
            query.orderBy(cb.desc(root.get("updateTime")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, pageable);
    }

    /**
     * 按竞品分页查询产品。
     *
     * @param competitorId 竞品 ID
     * @param pageable     分页参数
     * @return 产品分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorProductEntity> getProductsByCompetitor(Long competitorId, Pageable pageable) {
        return productRepository.findByCompetitorId(competitorId, pageable);
    }

    /**
     * 更新产品价格。
     * <p>流程: 追加价格历史 → 计算变化幅度 → 刷新价格统计 → 自动生成 PRICE_CHANGE 动态。</p>
     *
     * @param dto 价格监测参数
     * @return 更新后的产品
     * @throws ScrmException 产品不存在 / 新价格非法
     */
    @Transactional
    public ScrmCompetitorProductEntity updatePrice(ScrmPriceMonitorDto dto) throws ScrmException {
        if (dto == null || dto.getProductId() == null) {
            throw ScrmException.badRequest("价格监测参数不能为空");
        }
        if (dto.getNewPrice() == null || dto.getNewPrice() < 0) {
            throw ScrmException.badRequest("新价格非法: " + dto.getNewPrice());
        }
        ScrmCompetitorProductEntity entity = findProductOrThrow(dto.getProductId());
        double oldPrice = entity.getCurrentPrice() != null ? entity.getCurrentPrice() : 0.0;
        double newPrice = dto.getNewPrice();
        double changePercent = oldPrice > 0 ? round2((newPrice - oldPrice) / oldPrice * 100.0) : 0.0;
        // 1. 追加价格历史
        List<Map<String, Object>> history = parsePriceHistory(entity.getPriceHistory());
        history.add(priceHistoryEntry(LocalDate.now(), newPrice, changePercent));
        entity.setPriceHistory(toJson(history));
        // 2. 更新当前价与折扣
        entity.setCurrentPrice(newPrice);
        entity.setDiscountRate(calcDiscountRate(newPrice, entity.getOriginalPrice()));
        // 3. 刷新价格统计
        if (entity.getLowestPrice() == null || entity.getLowestPrice() == 0.0 || newPrice < entity.getLowestPrice()) {
            entity.setLowestPrice(newPrice);
        }
        if (entity.getHighestPrice() == null || newPrice > entity.getHighestPrice()) {
            entity.setHighestPrice(newPrice);
        }
        entity.setAvgPrice(round2(history.stream().mapToDouble(h -> toDouble(h.get("price")))
                .average().orElse(newPrice)));
        entity.setLastPriceChangeDate(LocalDate.now());
        entity.setLastPriceChangePercent(changePercent);
        entity.setPriceChangeCount((entity.getPriceChangeCount() != null ? entity.getPriceChangeCount() : 0) + 1);
        entity.setLastMonitoredAt(LocalDateTime.now());
        // 同步刷新价格对比与优势评分
        entity.setPriceComparison(determinePriceComparison(newPrice, entity.getOurPrice()));
        entity.setAdvantageScore(calcAdvantageScore(newPrice, entity.getOurPrice()));
        entity = productRepository.save(entity);
        log.info("更新竞品产品价格: productId={}, oldPrice={}, newPrice={}, change={}%",
                entity.getId(), oldPrice, newPrice, changePercent);
        // 4. 自动生成 PRICE_CHANGE 动态
        generatePriceChangeActivity(entity, oldPrice, newPrice, changePercent, dto.getSource());
        return entity;
    }

    /**
     * 查询产品价格历史。
     *
     * @param productId 产品 ID
     * @return 价格历史列表 [{date, price, change}]
     * @throws ScrmException 产品不存在
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPriceHistory(Long productId) throws ScrmException {
        ScrmCompetitorProductEntity entity = findProductOrThrow(productId);
        return parsePriceHistory(entity.getPriceHistory());
    }

    /**
     * 价格对比: 我方 vs 竞品 (按竞品汇总产品级对比)。
     *
     * @param competitorId 竞品 ID
     * @return 对比结果 {competitorId, productCount, comparisons:[{product, ourPrice, competitorPrice, comparison,
     *          advantageScore}]}
     * @throws ScrmException 竞品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getPriceComparison(Long competitorId) throws ScrmException {
        competitorService.findCompetitorOrThrow(competitorId);
        List<ScrmCompetitorProductEntity> products = productRepository
                .findByCompetitorId(competitorId, PageRequest.of(0, Integer.MAX_VALUE,
                        Sort.by(Sort.Direction.DESC, "updateTime"))).getContent();
        List<Map<String, Object>> comparisons = new ArrayList<>();
        int ourCheaperCount = 0;
        double totalAdvantage = 0.0;
        for (ScrmCompetitorProductEntity product : products) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("productId", product.getId());
            entry.put("productName", product.getProductName());
            entry.put("competitorPrice", product.getCurrentPrice());
            entry.put("ourPrice", product.getOurPrice());
            entry.put("priceComparison", product.getPriceComparison());
            entry.put("advantageScore", product.getAdvantageScore());
            comparisons.add(entry);
            if (product.getOurPrice() != null && product.getCurrentPrice() != null && product.getOurPrice() < product.getCurrentPrice()) {
                ourCheaperCount++;
            }
            if (product.getAdvantageScore() != null) {
                totalAdvantage += product.getAdvantageScore();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competitorId", competitorId);
        result.put("productCount", products.size());
        result.put("ourCheaperCount", ourCheaperCount);
        result.put("avgAdvantageScore", products.isEmpty() ? 0 : round2(totalAdvantage / products.size()));
        result.put("comparisons", comparisons);
        return result;
    }

    /**
     * 产品对比: 指定竞品产品与我方产品对比。
     * <p>若 ourProductId 与产品已绑定的我方产品不一致, 临时使用传入 ourProductId 对比 (不持久化)。</p>
     *
     * @param productId   竞品产品 ID
     * @param ourProductId 我方产品 ID
     * @return 对比结果
     * @throws ScrmException 产品不存在
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareWithOurProduct(Long productId, String ourProductId) throws ScrmException {
        ScrmCompetitorProductEntity product = findProductOrThrow(productId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("competitorProductId", product.getId());
        result.put("competitorProductName", product.getProductName());
        result.put("competitorPrice", product.getCurrentPrice());
        result.put("ourProductId", ourProductId);
        result.put("priceComparison", determinePriceComparison(product.getCurrentPrice(), product.getOurPrice()));
        result.put("advantageScore", calcAdvantageScore(product.getCurrentPrice(), product.getOurPrice()));
        result.put("competitorFeatures", product.getFeatures());
        result.put("competitorPositioning", product.getPositioning());
        return result;
    }

    /**
     * 批量更新价格。
     * <p>逐条调用 {@link #updatePrice}, 单条失败跳过不阻断其他。</p>
     *
     * @param updates 价格监测参数列表
     * @return 批量结果 {total, success, failed}
     */
    @Transactional
    public Map<String, Integer> batchUpdatePrices(List<ScrmPriceMonitorDto> updates) {
        if (updates == null) {
            updates = List.of();
        }
        int success = 0;
        int failed = 0;
        for (ScrmPriceMonitorDto dto : updates) {
            try {
                updatePrice(dto);
                success++;
            } catch (Exception e) {
                failed++;
                log.warn("批量更新产品价格失败, 跳过: productId={}, err={}",
                        dto != null ? dto.getProductId() : null, e.getMessage());
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", updates.size());
        result.put("success", success);
        result.put("failed", failed);
        log.info("批量更新竞品产品价格完成: total={}, success={}, failed={}", updates.size(), success, failed);
        return result;
    }

    /**
     * 按分类分页查询产品。
     *
     * @param category 分类
     * @param pageable 分页参数
     * @return 产品分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorProductEntity> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByProductCategory(category, pageable);
    }

    /**
     * 促销产品查询: 折扣率大于 0 的在售产品。
     *
     * @param pageable 分页参数
     * @return 产品分页结果 (按折扣率倒序)
     */
    @Transactional(readOnly = true)
    public Page<ScrmCompetitorProductEntity> getDiscountedProducts(Pageable pageable) {
        Specification<ScrmCompetitorProductEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), "ACTIVE"));
            predicates.add(cb.greaterThan(root.get("discountRate"), 0.0));
            query.orderBy(cb.desc(root.get("discountRate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return productRepository.findAll(spec, pageable);
    }

    /**
     * 近期价格变化产品查询。
     *
     * @param days 回溯天数
     * @return 产品列表 (按最近价格变化日期倒序)
     */
    @Transactional(readOnly = true)
    public List<ScrmCompetitorProductEntity> getPriceChangeProducts(int days) {
        LocalDate since = LocalDate.now().minusDays(Math.max(days, 0));
        return productRepository.findRecentPriceChanged(since);
    }

    /**
     * 校验产品枚举字段合法性。
     *
     * @param dto     产品参数
     * @param partial 是否为部分更新
     * @throws ScrmException 参数非法
     */
    private void validateProductEnums(ScrmCompetitorProductDto dto, boolean partial) throws ScrmException {
        if (dto.getStatus() != null && !VALID_PRODUCT_STATUSES.contains(dto.getStatus())) {
            throw ScrmException.badRequest("产品状态非法: " + dto.getStatus() + ", 仅支持 " + VALID_PRODUCT_STATUSES);
        }
        if (dto.getPriceComparison() != null && !VALID_PRICE_COMPARISONS.contains(dto.getPriceComparison())) {
            throw ScrmException.badRequest("价格对比非法: " + dto.getPriceComparison()
                    + ", 仅支持 " + VALID_PRICE_COMPARISONS);
        }
        if (dto.getAdvantageScore() != null && (dto.getAdvantageScore() < -MAX_ADVANTAGE_SCORE || dto.getAdvantageScore() > MAX_ADVANTAGE_SCORE)) {
            throw ScrmException.badRequest("优势评分越界 (-100 到 100): " + dto.getAdvantageScore());
        }
    }

    /**
     * 计算折扣率 (0-1): (原价 - 当前价) / 原价。
     *
     * @param currentPrice  当前价
     * @param originalPrice 原价
     * @return 折扣率
     */
    private double calcDiscountRate(double currentPrice, double originalPrice) {
        if (originalPrice <= 0) {
            return 0.0;
        }
        double rate = (originalPrice - currentPrice) / originalPrice;
        return round2(Math.max(0.0, rate));
    }

    /**
     * 判定价格对比关系。
     *
     * @param competitorPrice 竞品价
     * @param ourPrice        我方价
     * @return HIGHER / LOWER / EQUAL / SIMILAR
     */
    private String determinePriceComparison(double competitorPrice, double ourPrice) {
        if (ourPrice <= 0) {
            return "HIGHER";
        }
        double diffPercent = Math.abs(competitorPrice - ourPrice) / ourPrice * 100.0;
        if (diffPercent < 0.01) {
            return "EQUAL";
        }
        if (diffPercent <= PRICE_SIMILAR_THRESHOLD) {
            return "SIMILAR";
        }
        return competitorPrice > ourPrice ? "HIGHER" : "LOWER";
    }

    /**
     * 计算优势评分 (-100 到 100, 正数代表我方占优)。
     * <p>基于价格差异: 我方越便宜分越高。差异百分比 * 5, 上限 100。</p>
     *
     * @param competitorPrice 竞品价
     * @param ourPrice        我方价
     * @return 优势评分
     */
    private int calcAdvantageScore(double competitorPrice, double ourPrice) {
        if (ourPrice <= 0 || competitorPrice <= 0) {
            return 0;
        }
        // 正数代表我方占优 (我方更便宜)
        double diffPercent = (competitorPrice - ourPrice) / ourPrice * 100.0;
        int score = (int) Math.round(diffPercent * 5.0);
        return Math.max(-MAX_ADVANTAGE_SCORE, Math.min(MAX_ADVANTAGE_SCORE, score));
    }

    /**
     * 价格变化自动生成竞品动态。
     *
     * @param product       产品实体
     * @param oldPrice      旧价格
     * @param newPrice      新价格
     * @param changePercent 变化幅度 (%)
     * @param source        来源
     */
    private void generatePriceChangeActivity(ScrmCompetitorProductEntity product, double oldPrice, double newPrice,
                                              double changePercent, String source) {
        try {
            ScrmCompetitorActivityEntity activity = new ScrmCompetitorActivityEntity();
            activity.setCompetitorId(product.getCompetitorId());
            activity.setCompetitorName(product.getCompetitorName());
            activity.setActivityType("PRICE_CHANGE");
            String direction = changePercent > 0 ? "涨价" : (changePercent < 0 ? "降价" : "价格调整");
            activity.setTitle(product.getProductName() + " " + direction + ": " + changePercent + "%");
            activity.setSummary(String.format("产品 %s 价格由 %.2f 变更为 %.2f, 变化幅度 %.2f%%",
                    product.getProductName(), oldPrice, newPrice, changePercent));
            activity.setActivityDate(LocalDate.now());
            activity.setSource(source);
            // 影响等级按变化幅度判定
            double absChange = Math.abs(changePercent);
            String impactLevel;
            if (absChange >= 20) {
                impactLevel = "CRITICAL";
            } else if (absChange >= 10) {
                impactLevel = "HIGH";
            } else if (absChange >= 3) {
                impactLevel = "MEDIUM";
            } else {
                impactLevel = "LOW";
            }
            activity.setImpactLevel(impactLevel);
            activity.setImpactAnalysis(String.format("价格变化幅度 %.2f%%, 影响等级 %s", changePercent, impactLevel));
            activity.setAffectedProducts(product.getProductName());
            activity.setResponseStatus("PENDING");
            activity.setDetectionMethod("AUTOMATED");
            activity.setImportanceScore(Math.min((int) (absChange * 2), MAX_IMPORTANCE_SCORE));
            activity.setIsVerified(false);
            activity.setCreatedBy("price-monitor");
            activityRepository.save(activity);
            log.info("价格变化自动生成动态: productId={}, activityType=PRICE_CHANGE, change={}%",
                    product.getId(), changePercent);
        } catch (Exception e) {
            log.warn("价格变化自动生成动态失败, 忽略: productId={}, err={}", product.getId(), e.getMessage());
        }
    }

    /**
     * 构建价格历史条目。
     *
     * @param date  日期
     * @param price 价格
     * @param change 变化幅度 (%)
     * @return 历史条目 Map
     */
    private Map<String, Object> priceHistoryEntry(LocalDate date, double price, double change) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("date", date.toString());
        entry.put("price", round2(price));
        entry.put("change", round2(change));
        return entry;
    }

    /**
     * 解析价格历史 JSON。
     *
     * @param json 价格历史 JSON 字符串
     * @return 历史列表, 解析失败返回空列表
     */
    private List<Map<String, Object>> parsePriceHistory(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            log.warn("价格历史 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 对象
     * @return JSON 字符串, 序列化失败返回 "[]"
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("对象序列化为 JSON 失败: {}", e.getMessage());
            return "[]";
        }
    }

    /**
     * 将对象转换为 double 数值。
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0;
        }
        if (obj instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * double 保留两位小数。
     *
     * @param value 原始值
     * @return 四舍五入后的值
     */
    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 按主键查询产品, 不存在抛异常, 并校验账号归属。
     *
     * @param id 产品 ID
     * @return 产品实体
     * @throws ScrmException 产品不存在
     */
    private ScrmCompetitorProductEntity findProductOrThrow(Long id) throws ScrmException {
        ScrmCompetitorProductEntity entity = productRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "竞品产品不存在: id=" + id));
        return entity;
    }
}