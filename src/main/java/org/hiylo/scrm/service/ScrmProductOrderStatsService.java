/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmProductOrderStatsService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.entity.ScrmOrderEntity;
import org.hiylo.scrm.entity.ScrmProductEntity;
import org.hiylo.scrm.repository.ScrmOrderItemRepository;
import org.hiylo.scrm.repository.ScrmOrderRepository;
import org.hiylo.scrm.repository.ScrmProductRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SCRM 统计服务 (统计子域)。
 * <p>
 * 承载多维度统计: 商品统计 / 订单统计 / 客户购买历史 / 热销商品 / 销售趋势 / 渠道收入。
 * 统计结果构建参考订单子域的共享常量与 DTO 转换能力, 通过注入
 * {@link ScrmProductOrderOrderService} 复用。门面 {@link ScrmProductOrderService}
 * 委托本类实现统计。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmProductOrderStatsService {

    /** 默认热销商品返回条数 */
    private static final int DEFAULT_TOP_PRODUCTS_LIMIT = 10;
    /** 默认销售趋势天数 */
    private static final int DEFAULT_SALES_TREND_DAYS = 30;
    /** 销售趋势最大天数 */
    private static final int MAX_SALES_TREND_DAYS = 365;

    /** 商品数据访问层 */
    private final ScrmProductRepository productRepository;

    /** 订单数据访问层 */
    private final ScrmOrderRepository orderRepository;

    /** 订单项数据访问层 */
    private final ScrmOrderItemRepository orderItemRepository;

    /** 订单管理子域服务 (订单查询 / 客户名称 / DTO 转换 / 共享常量) */
    private final ScrmProductOrderOrderService orderService;

    // ============================================================
    // 统计
    // ============================================================

    /**
     * 商品统计: 商品总数 / 各状态数 / 库存总量 / 销量总量 / 评分分布。
     * <p>时间范围按创建时间过滤, 为空时统计全量。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getProductStats(LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ScrmProductEntity> spec = buildProductTimeSpec(startTime, endTime);
        List<ScrmProductEntity> products = productRepository.findAll(spec);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalCount", products.size());
        stats.put("activeCount", products.stream().filter(p -> ScrmProductOrderProductService.PRODUCT_STATUS_ACTIVE.equals(p.getStatus())).count());
        stats.put("inactiveCount",
                products.stream().filter(p -> ScrmProductOrderProductService.PRODUCT_STATUS_INACTIVE.equals(p.getStatus())).count());
        stats.put("discontinuedCount",
                products.stream().filter(p -> ScrmProductOrderProductService.PRODUCT_STATUS_DISCONTINUED.equals(p.getStatus())).count());
        stats.put("totalStock", products.stream().mapToInt(p -> p.getStock() != null ? p.getStock() : 0).sum());
        stats.put("totalSalesCount",
                products.stream().mapToInt(p -> p.getSalesCount() != null ? p.getSalesCount() : 0).sum());
        stats.put("totalViewCount",
                products.stream().mapToInt(p -> p.getViewCount() != null ? p.getViewCount() : 0).sum());
        stats.put("avgRatingScore", ScrmProductOrderOrderService.round(products.stream()
                .map(ScrmProductEntity::getRatingScore)
                .filter(r -> r != null && r > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0d)));
        // 评分分布
        Map<String, Long> ratingDistribution = new LinkedHashMap<>();
        ratingDistribution.put("0-1", products.stream().filter(p -> p.getRatingScore() != null && p.getRatingScore() >= 0 && p.getRatingScore() < 1).count());
        ratingDistribution.put("1-2", products.stream().filter(p -> p.getRatingScore() != null && p.getRatingScore() >= 1 && p.getRatingScore() < 2).count());
        ratingDistribution.put("2-3", products.stream().filter(p -> p.getRatingScore() != null && p.getRatingScore() >= 2 && p.getRatingScore() < 3).count());
        ratingDistribution.put("3-4", products.stream().filter(p -> p.getRatingScore() != null && p.getRatingScore() >= 3 && p.getRatingScore() < 4).count());
        ratingDistribution.put("4-5", products.stream().filter(p -> p.getRatingScore() != null && p.getRatingScore() >= 4 && p.getRatingScore() <= 5).count());
        stats.put("ratingDistribution", ratingDistribution);
        stats.put("sumStock", productRepository.sumStock());
        stats.put("sumSalesCount", productRepository.sumSalesCount());
        return stats;
    }

    /**
     * 订单统计: 订单总数 / 各状态数 / 总金额 / 已付金额 / 转化率。
     * <p>时间范围按创建时间过滤, 为空时统计全量。转化率 = 已完成订单数 / 总订单数。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrderStats(LocalDateTime startTime, LocalDateTime endTime) {
        List<ScrmOrderEntity> orders = orderRepository.findByTimeRange(startTime, endTime);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalOrders", orders.size());
        // 各状态分布
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : new String[]{ScrmProductOrderOrderService.ORDER_STATUS_PENDING, ScrmProductOrderOrderService.ORDER_STATUS_CONFIRMED, ScrmProductOrderOrderService.ORDER_STATUS_PAID,
                ScrmProductOrderOrderService.ORDER_STATUS_SHIPPED, ScrmProductOrderOrderService.ORDER_STATUS_DELIVERED, ScrmProductOrderOrderService.ORDER_STATUS_COMPLETED,
                ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED, ScrmProductOrderOrderService.ORDER_STATUS_REFUNDED}) {
            statusCount.put(s, 0L);
        }
        for (ScrmOrderEntity o : orders) {
            statusCount.merge(o.getOrderStatus(), 1L, Long::sum);
        }
        stats.put("statusCount", statusCount);
        // 金额汇总
        stats.put("totalAmount", ScrmProductOrderOrderService.round(orders.stream()
                .filter(o -> !ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED.equals(o.getOrderStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0d)
                .sum()));
        stats.put("paidAmount", ScrmProductOrderOrderService.round(orders.stream()
                .mapToDouble(o -> o.getPaidAmount() != null ? o.getPaidAmount() : 0d)
                .sum()));
        stats.put("completedAmount", orderRepository.sumCompletedAmount(startTime, endTime));
        stats.put("actualRevenue", orderRepository.sumPaidAmount(startTime, endTime));
        // 转化率
        long completed = statusCount.getOrDefault(ScrmProductOrderOrderService.ORDER_STATUS_COMPLETED, 0L);
        double conversionRate = orders.isEmpty() ? 0d : ScrmProductOrderOrderService.round(completed * 100d / orders.size());
        stats.put("completedCount", completed);
        stats.put("conversionRate", conversionRate);
        stats.put("cancelledCount", statusCount.getOrDefault(ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED, 0L));
        return stats;
    }

    /**
     * 客户购买历史: 订单列表与汇总 (订单数 / 总消费 / 已完成订单数 / 最近下单时间)。
     *
     * @param customerId 客户 ID
     * @return 购买历史
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCustomerPurchaseHistory(Long customerId) {
        List<ScrmOrderEntity> orders = orderRepository
                .findByCustomerIdOrderByCreateTimeDesc(customerId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("customerId", customerId);
        stats.put("customerName", orders.isEmpty() ? orderService.resolveCustomerName(customerId) : orders.get(0).getCustomerName());
        stats.put("totalOrders", orders.size());
        stats.put("totalSpent", ScrmProductOrderOrderService.round(orders.stream()
                .filter(o -> !ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED.equals(o.getOrderStatus()))
                .mapToDouble(o -> o.getPaidAmount() != null ? o.getPaidAmount() : 0d)
                .sum()));
        stats.put("completedOrders", orders.stream()
                .filter(o -> ScrmProductOrderOrderService.ORDER_STATUS_COMPLETED.equals(o.getOrderStatus())).count());
        stats.put("cancelledOrders", orders.stream()
                .filter(o -> ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED.equals(o.getOrderStatus())).count());
        stats.put("lastOrderTime", orders.isEmpty() ? null : orders.get(0).getCreateTime());
        stats.put("orders", orders.stream().map(o -> orderService.toOrderDto(o, null)).collect(Collectors.toList()));
        return stats;
    }

    /**
     * 热销商品 (按销量排序, 取前 limit 条)。
     *
     * @param limit     返回条数 (默认 10)
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 热销商品列表: [{productId, productName, salesCount, salesAmount}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopProducts(Integer limit, LocalDateTime startTime, LocalDateTime endTime) {
        int top = limit != null && limit > 0 ? limit : DEFAULT_TOP_PRODUCTS_LIMIT;
        List<Object[]> rows = orderItemRepository.topProductsBySales(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Object[] row : rows) {
            if (count >= top) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", row[0]);
            item.put("productName", row[1]);
            item.put("salesCount", row[2]);
            item.put("salesAmount", ScrmProductOrderOrderService.round(((Number) row[3]).doubleValue()));
            result.add(item);
            count++;
        }
        return result;
    }

    /**
     * 销售趋势 (按日聚合, 最近 days 天)。
     * <p>聚合订单数与已付金额 (不含取消订单), 按日期升序返回。</p>
     *
     * @param days 天数 (默认 30, 最大 365)
     * @return 趋势列表: [{date, orderCount, revenue}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSalesTrend(Integer days) {
        int d = days != null && days > 0 ? days : DEFAULT_SALES_TREND_DAYS;
        if (d > MAX_SALES_TREND_DAYS) {
            d = MAX_SALES_TREND_DAYS;
        }
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(d - 1L);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();
        List<ScrmOrderEntity> orders = orderRepository
                .findByTimeRange(startTime, endTime);
        Map<LocalDate, List<ScrmOrderEntity>> grouped = new LinkedHashMap<>();
        for (LocalDate cur = startDate; !cur.isAfter(today); cur = cur.plusDays(1)) {
            grouped.put(cur, new ArrayList<>());
        }
        for (ScrmOrderEntity o : orders) {
            if (o.getCreateTime() == null) {
                continue;
            }
            LocalDate date = o.getCreateTime().toLocalDate();
            grouped.computeIfAbsent(date, k -> new ArrayList<>()).add(o);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ScrmOrderEntity>> entry : grouped.entrySet()) {
            List<ScrmOrderEntity> dayOrders = entry.getValue();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey().toString());
            item.put("orderCount", dayOrders.size());
            item.put("revenue", ScrmProductOrderOrderService.round(dayOrders.stream()
                    .filter(o -> !ScrmProductOrderOrderService.ORDER_STATUS_CANCELLED.equals(o.getOrderStatus()))
                    .mapToDouble(o -> o.getPaidAmount() != null ? o.getPaidAmount() : 0d)
                    .sum()));
            result.add(item);
        }
        return result;
    }

    /**
     * 渠道收入: 按下单渠道聚合订单数与总金额 (不含取消订单)。
     * <p>时间范围按创建时间过滤, 为空时统计全量。channel 为空的归入 UNKNOWN。</p>
     *
     * @param startTime 起始时间 (可空)
     * @param endTime   截止时间 (可空)
     * @return 渠道收入列表: [{channel, orderCount, revenue}]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRevenueByChannel(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> rows = orderRepository.revenueByChannel(startTime, endTime);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("channel", row[0]);
            item.put("orderCount", row[1]);
            item.put("revenue", ScrmProductOrderOrderService.round(((Number) row[2]).doubleValue()));
            result.add(item);
        }
        return result;
    }

    // ============================================================
    // 内部工具方法
    // ============================================================

    /**
     * 构建商品时间范围查询条件 Specification (统计用)。
     */
    private Specification<ScrmProductEntity> buildProductTimeSpec(LocalDateTime startTime, LocalDateTime endTime) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}