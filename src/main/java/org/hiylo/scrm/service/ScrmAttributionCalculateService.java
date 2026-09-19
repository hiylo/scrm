/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmAttributionCalculateService.java
 * Date : 2026/09/19 00:00:00
 * Author : Hsi Chu
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmAttributionCalculateDto;
import org.hiylo.scrm.entity.ScrmAttributionConversionEntity;
import org.hiylo.scrm.entity.ScrmAttributionModelEntity;
import org.hiylo.scrm.entity.ScrmAttributionTouchpointEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmAttributionConversionRepository;
import org.hiylo.scrm.repository.ScrmAttributionModelRepository;
import org.hiylo.scrm.repository.ScrmAttributionTouchpointRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCRM 营销归因计算服务。
 * <p>
 * 承载单次/批量归因计算流程 (选模型 → 查触点 → 应用归因规则 → 分配权重 → 回填价值),
 * 完整实现首次 / 末次 / 线性 / 时间衰减 / 位置 (U 型/W 型) / 自定义归因算法。
 * </p>
 *
 * @author Hsi Chu
 * @since 2026/09/19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmAttributionCalculateService {

    /** 模型类型: 首次触点归因 */
    private static final String MODEL_FIRST_TOUCH = "FIRST_TOUCH";
    /** 模型类型: 末次触点归因 */
    private static final String MODEL_LAST_TOUCH = "LAST_TOUCH";
    /** 模型类型: 线性归因 */
    private static final String MODEL_LINEAR = "LINEAR";
    /** 模型类型: 时间衰减归因 */
    private static final String MODEL_TIME_DECAY = "TIME_DECAY";
    /** 模型类型: 位置归因 */
    private static final String MODEL_POSITION_BASED = "POSITION_BASED";
    /** 模型类型: U 型归因 */
    private static final String MODEL_U_SHAPED = "U_SHAPED";
    /** 模型类型: W 型归因 */
    private static final String MODEL_W_SHAPED = "W_SHAPED";
    /** 模型类型: 自定义归因 */
    private static final String MODEL_CUSTOM = "CUSTOM";

    /** 默认回溯天数 */
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    /** 默认时间衰减半衰期 (天) */
    private static final int DEFAULT_TIME_DECAY_HALF_LIFE = 7;
    /** 默认转化窗口 (天) */
    private static final int DEFAULT_CONVERSION_WINDOW_DAYS = 7;

    /** U 型归因默认位置权重: first 0.4 / last 0.4 / middle 0.2 */
    private static final double U_SHAPED_FIRST = 0.4;
    /** U 型归因默认位置权重: last 末触点 */
    private static final double U_SHAPED_LAST = 0.4;
    /** U 型归因默认位置权重: middle 中间触点 (归一化后均分) */
    private static final double U_SHAPED_MIDDLE = 0.2;
    /** W 型归因默认位置权重: first 0.3 / last 0.3 / middle 0.4 */
    private static final double W_SHAPED_FIRST = 0.3;
    /** W 型归因默认位置权重: last 末触点 */
    private static final double W_SHAPED_LAST = 0.3;
    /** W 型归因默认位置权重: middle 中间触点 (归一化后均分) */
    private static final double W_SHAPED_MIDDLE = 0.4;
    /** 位置归因默认位置权重: first 0.4 / last 0.4 / middle 0.2 */
    private static final double POSITION_BASED_FIRST = 0.4;
    /** 位置归因默认位置权重: last 末触点 */
    private static final double POSITION_BASED_LAST = 0.4;
    /** 位置归因默认位置权重: middle 中间触点 (归一化后均分) */
    private static final double POSITION_BASED_MIDDLE = 0.2;

    /** 归因模型数据访问层 */
    private final ScrmAttributionModelRepository modelRepository;

    /** 归因触点数据访问层 */
    private final ScrmAttributionTouchpointRepository touchpointRepository;

    /** 归因转化数据访问层 */
    private final ScrmAttributionConversionRepository conversionRepository;

    /** JSON 解析器 (解析 positionWeights / customWeights) */
    private final ObjectMapper objectMapper;

    /** 模型管理服务 (加载模型并校验归属) */
    private final ScrmAttributionModelService modelService;

    /**
     * 计算归因: 选择模型 → 查询触点 → 应用归因规则 → 分配权重 → 计算归因价值。
     * <p>
     * 流程: 加载模型 → 加载待归因转化 (conversionIds 优先, 否则按 startDate/endDate 筛选全部转化) →
     * 对每条转化查询回溯窗口内客户触点链 → 按模型类型应用归因算法分配权重 (0-1) →
     * 按权重分配转化价值 → 更新触点与转化 → 增量更新模型应用统计。
     * </p>
     * <p>单条转化失败跳过, 不阻断其他转化。</p>
     *
     * @param calculateDto 归因计算参数
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> calculateAttribution(ScrmAttributionCalculateDto calculateDto)
            throws ScrmException {
        if (calculateDto == null || calculateDto.getModelId() == null) {
            throw ScrmException.badRequest("归因计算参数与模型 ID 不能为空");
        }
        ScrmAttributionModelEntity model = modelService.findModelOrThrow(calculateDto.getModelId());
        List<ScrmAttributionConversionEntity> conversions = loadConversionsForCalculation(calculateDto);
        int processed = 0;
        int failed = 0;
        for (ScrmAttributionConversionEntity conversion : conversions) {
            try {
                calculateSingleConversion(model, conversion);
                processed++;
            } catch (Exception e) {
                failed++;
                log.warn("归因计算失败, 跳过: conversionId={}, err={}", conversion.getId(), e.getMessage());
            }
        }
        // 增量更新模型应用统计 (best-effort)
        try {
            modelRepository.incrementAppliedCount(model.getId(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("更新归因模型应用统计失败, 忽略: modelId={}, err={}", model.getId(), e.getMessage());
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("total", conversions.size());
        result.put("processed", processed);
        result.put("failed", failed);
        log.info("归因计算完成:, modelId={}, total={}, processed={}, failed={}", model.getId(), conversions.size(), processed, failed);
        return result;
    }

    /**
     * 批量计算: 按模型 ID 与时间范围对全部转化执行归因计算。
     *
     * @param modelId   归因模型 ID
     * @param startDate 转化时间起点 (含, 可空)
     * @param endDate   转化时间终点 (含, 可空)
     * @return 计算结果: {total, processed, failed}
     * @throws ScrmException 模型不存在
     */
    @Transactional
    public Map<String, Integer> batchCalculate(Long modelId, LocalDateTime startDate, LocalDateTime endDate)
            throws ScrmException {
        ScrmAttributionCalculateDto dto = new ScrmAttributionCalculateDto();
        dto.setModelId(modelId);
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        return calculateAttribution(dto);
    }

    /**
     * 首次触点归因: 首触点权重 1.0, 其余 0。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyFirstTouch(List<ScrmAttributionTouchpointEntity> touchpoints,
                                              double conversionValue) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        if (touchpoints == null || touchpoints.isEmpty()) {
            return weights;
        }
        for (int i = 0; i < touchpoints.size(); i++) {
            weights.put(touchpoints.get(i).getId(), i == 0 ? 1.0 : 0.0);
        }
        return weights;
    }

    /**
     * 末次触点归因: 末触点权重 1.0, 其余 0。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyLastTouch(List<ScrmAttributionTouchpointEntity> touchpoints,
                                             double conversionValue) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        if (touchpoints == null || touchpoints.isEmpty()) {
            return weights;
        }
        int last = touchpoints.size() - 1;
        for (int i = 0; i < touchpoints.size(); i++) {
            weights.put(touchpoints.get(i).getId(), i == last ? 1.0 : 0.0);
        }
        return weights;
    }

    /**
     * 线性归因: 所有触点均分权重 1/n。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyLinear(List<ScrmAttributionTouchpointEntity> touchpoints,
                                          double conversionValue) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        if (touchpoints == null || touchpoints.isEmpty()) {
            return weights;
        }
        double weight = 1.0 / touchpoints.size();
        for (ScrmAttributionTouchpointEntity tp : touchpoints) {
            weights.put(tp.getId(), weight);
        }
        return weights;
    }

    /**
     * 时间衰减归因: 按触点时间距转化时间的间隔, 以 2^(-days/halfLife) 衰减,
     * 越接近转化的触点权重越大, 归一化后权重和为 1。
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @param halfLifeDays    时间衰减半衰期天数
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyTimeDecay(List<ScrmAttributionTouchpointEntity> touchpoints,
                                             double conversionValue, int halfLifeDays) {
        Map<Long, Double> weights = new LinkedHashMap<>();
        if (touchpoints == null || touchpoints.isEmpty()) {
            return weights;
        }
        int halfLife = halfLifeDays > 0 ? halfLifeDays : DEFAULT_TIME_DECAY_HALF_LIFE;
        // 取转化时间作为参考 (末次触点时间近似)
        LocalDateTime reference = touchpoints.get(touchpoints.size() - 1).getTouchpointTime();
        double[] raw = new double[touchpoints.size()];
        double sum = 0;
        for (int i = 0; i < touchpoints.size(); i++) {
            long days = Math.max(0, ChronoUnit.SECONDS.between(
                    touchpoints.get(i).getTouchpointTime(), reference));
            double dayFraction = days / 86400.0;
            double decay = Math.pow(2, -dayFraction / halfLife);
            raw[i] = decay;
            sum += decay;
        }
        for (int i = 0; i < touchpoints.size(); i++) {
            double w = sum > 0 ? raw[i] / sum : 1.0 / touchpoints.size();
            weights.put(touchpoints.get(i).getId(), w);
        }
        return weights;
    }

    /**
     * 位置归因 (U 型/W 型): 按位置权重 {first, last, middle} 分配。
     * <p>
     * 单触点: 权重 1.0; 两触点: 按 first/last 比例归一化分配;
     * 三触点及以上: 首触点取 first, 末触点取 last, 中间触点均分 middle。
     * </p>
     *
     * @param touchpoints     触点列表 (按触点时间升序)
     * @param conversionValue 转化价值
     * @param weights         位置权重 {first, last, middle}
     * @return 触点 ID → 归因权重 映射
     */
    public Map<Long, Double> applyPositionBased(List<ScrmAttributionTouchpointEntity> touchpoints,
                                                  double conversionValue, Map<String, Double> weights) {
        Map<Long, Double> result = new LinkedHashMap<>();
        if (touchpoints == null || touchpoints.isEmpty()) {
            return result;
        }
        double firstW = POSITION_BASED_FIRST;
        double lastW = POSITION_BASED_LAST;
        double middleW = POSITION_BASED_MIDDLE;
        if (weights != null) {
            Double f = weights.get("first");
            Double l = weights.get("last");
            Double m = weights.get("middle");
            if (f != null && f > 0) firstW = f;
            if (l != null && l > 0) lastW = l;
            if (m != null && m > 0) middleW = m;
        }
        int n = touchpoints.size();
        if (n == 1) {
            result.put(touchpoints.get(0).getId(), 1.0);
            return result;
        }
        if (n == 2) {
            double sum = firstW + lastW;
            result.put(touchpoints.get(0).getId(), sum > 0 ? firstW / sum : 0.5);
            result.put(touchpoints.get(1).getId(), sum > 0 ? lastW / sum : 0.5);
            return result;
        }
        // n >= 3
        int middleCount = n - 2;
        double perMiddle = middleCount > 0 ? middleW / middleCount : 0;
        // 权重和归一化 (防止 firstW + lastW + middleW != 1)
        double total = firstW + lastW + middleW;
        if (total <= 0) {
            total = 1.0;
        }
        result.put(touchpoints.get(0).getId(), firstW / total);
        result.put(touchpoints.get(n - 1).getId(), lastW / total);
        for (int i = 1; i < n - 1; i++) {
            result.put(touchpoints.get(i).getId(), perMiddle / total);
        }
        return result;
    }

    /**
     * 加载待归因转化: conversionIds 非空时按 ID 加载, 否则按时间范围加载全部转化。
     *
     * @param calculateDto 计算参数
     * @return 待归因转化列表
     */
    private List<ScrmAttributionConversionEntity> loadConversionsForCalculation(ScrmAttributionCalculateDto calculateDto) {
        if (calculateDto.getConversionIds() != null && !calculateDto.getConversionIds().isEmpty()) {
            List<ScrmAttributionConversionEntity> list = new ArrayList<>();
            for (Long id : calculateDto.getConversionIds()) {
                conversionRepository.findById(id).ifPresent(list::add);
            }
            return list;
        }
        Specification<ScrmAttributionConversionEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (calculateDto.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("conversionTime"), calculateDto.getStartDate()));
            }
            if (calculateDto.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("conversionTime"), calculateDto.getEndDate()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return conversionRepository.findAll(spec);
    }

    /**
     * 对单条转化执行归因计算。
     *
     * @param model      归因模型
     * @param conversion 转化实体
     * @throws ScrmException 归因计算失败
     */
    private void calculateSingleConversion(ScrmAttributionModelEntity model,
                                            ScrmAttributionConversionEntity conversion) throws ScrmException {
        int lookback = model.getLookbackDays() != null && model.getLookbackDays() > 0
                ? model.getLookbackDays() : DEFAULT_LOOKBACK_DAYS;
        List<ScrmAttributionTouchpointEntity> touchpoints = touchpointRepository.findCustomerTouchpointChain(
                 conversion.getCustomerId(),
                conversion.getConversionTime().minusDays(lookback),
                conversion.getConversionTime());
        // 重置触点归因标记 (覆盖上次归因结果)
        for (ScrmAttributionTouchpointEntity tp : touchpoints) {
            tp.setIsAttributed(false);
            tp.setAttributionWeight(0.0);
            tp.setAttributionValue(0.0);
        }
        double conversionValue = conversion.getConversionValue() != null
                ? conversion.getConversionValue() : 0.0;
        Map<Long, Double> weights;
        switch (model.getModelType()) {
            case MODEL_LAST_TOUCH:
                weights = applyLastTouch(touchpoints, conversionValue);
                break;
            case MODEL_LINEAR:
                weights = applyLinear(touchpoints, conversionValue);
                break;
            case MODEL_TIME_DECAY:
                weights = applyTimeDecay(touchpoints, conversionValue,
                        model.getTimeDecayHalfLife() != null ? model.getTimeDecayHalfLife()
                                : DEFAULT_TIME_DECAY_HALF_LIFE);
                break;
            case MODEL_POSITION_BASED:
                weights = applyPositionBased(touchpoints, conversionValue,
                        parsePositionWeights(model.getPositionWeights(), POSITION_BASED_FIRST,
                                POSITION_BASED_LAST, POSITION_BASED_MIDDLE));
                break;
            case MODEL_U_SHAPED:
                weights = applyPositionBased(touchpoints, conversionValue,
                        parsePositionWeights(model.getPositionWeights(), U_SHAPED_FIRST,
                                U_SHAPED_LAST, U_SHAPED_MIDDLE));
                break;
            case MODEL_W_SHAPED:
                weights = applyPositionBased(touchpoints, conversionValue,
                        parsePositionWeights(model.getPositionWeights(), W_SHAPED_FIRST,
                                W_SHAPED_LAST, W_SHAPED_MIDDLE));
                break;
            case MODEL_CUSTOM:
                weights = applyCustom(touchpoints, conversionValue, model.getCustomWeights());
                break;
            case MODEL_FIRST_TOUCH:
            default:
                weights = applyFirstTouch(touchpoints, conversionValue);
                break;
        }
        // 分配归因价值, 更新触点
        List<Map<String, Object>> attributionDetails = new ArrayList<>(touchpoints.size());
        int attributedCount = 0;
        for (ScrmAttributionTouchpointEntity tp : touchpoints) {
            double w = weights.getOrDefault(tp.getId(), 0.0);
            double value = round2(w * conversionValue);
            boolean attributed = w > 0;
            tp.setIsAttributed(attributed);
            tp.setAttributionWeight(round4(w));
            tp.setAttributionValue(value);
            if (attributed) {
                attributedCount++;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("touchpointId", String.valueOf(tp.getId()));
            detail.put("type", tp.getTouchpointType());
            detail.put("channel", tp.getChannel());
            detail.put("weight", round4(w));
            detail.put("value", value);
            attributionDetails.add(detail);
        }
        touchpointRepository.saveAll(touchpoints);
        // 更新转化
        conversion.setModelId(model.getId());
        conversion.setModelName(model.getModelName());
        conversion.setTotalTouchpoints(touchpoints.size());
        conversion.setAttributedTouchpoints(attributedCount);
        conversion.setAttributionDetails(toJson(attributionDetails));
        conversion.setConversionWindowDays(model.getConversionWindowDays() != null
                ? model.getConversionWindowDays() : DEFAULT_CONVERSION_WINDOW_DAYS);
        if (!touchpoints.isEmpty()) {
            ScrmAttributionTouchpointEntity first = touchpoints.get(0);
            ScrmAttributionTouchpointEntity last = touchpoints.get(touchpoints.size() - 1);
            conversion.setFirstTouchType(first.getTouchpointType());
            conversion.setFirstTouchChannel(first.getChannel());
            conversion.setLastTouchType(last.getTouchpointType());
            conversion.setLastTouchChannel(last.getChannel());
            long hours = Duration.between(first.getTouchpointTime(), conversion.getConversionTime()).toHours();
            conversion.setTimeToConversionHours((int) Math.max(0, hours));
        }
        conversion.setAttributedAt(LocalDateTime.now());
        conversionRepository.save(conversion);
        log.info("归因计算: conversionId={}, modelId={}, touchpoints={}, attributed={}, value={}",
                conversion.getId(), model.getId(), touchpoints.size(), attributedCount, conversionValue);
    }

    /**
     * 解析位置权重 JSON {first, last, middle}, 解析失败或缺字段时回填默认值。
     *
     * @param json         位置权重 JSON 字符串
     * @param defaultFirst 默认 first 权重
     * @param defaultLast  默认 last 权重
     * @param defaultMiddle 默认 middle 权重
     * @return 位置权重 Map
     */
    private Map<String, Double> parsePositionWeights(String json, double defaultFirst,
                                                      double defaultLast, double defaultMiddle) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("first", defaultFirst);
        weights.put("last", defaultLast);
        weights.put("middle", defaultMiddle);
        if (json == null || json.isBlank()) {
            return weights;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (parsed.containsKey("first")) {
                weights.put("first", toDouble(parsed.get("first")));
            }
            if (parsed.containsKey("last")) {
                weights.put("last", toDouble(parsed.get("last")));
            }
            if (parsed.containsKey("middle")) {
                weights.put("middle", toDouble(parsed.get("middle")));
            }
        } catch (Exception e) {
            log.warn("位置权重 JSON 解析失败, 使用默认值: {}", e.getMessage());
        }
        return weights;
    }

    /**
     * 自定义权重归因: 解析 customWeights JSON [{touchpointType, channel, weight}] 累加权重后归一化。
     * <p>JSON 格式: {@code [{"touchpointType":"AD_CLICK", "weight":2.0},...]} 或
     * {@code [{"channel":"SEARCH", "weight":1.5},...]}。未配置时回退为线性归因。</p>
     *
     * @param touchpoints     触点列表
     * @param conversionValue 转化价值
     * @param customWeights   自定义权重 JSON
     * @return 触点 ID → 归因权重 映射
     */
    private Map<Long, Double> applyCustom(List<ScrmAttributionTouchpointEntity> touchpoints,
                                           double conversionValue, String customWeights) {
        if (customWeights == null || customWeights.isBlank()) {
            return applyLinear(touchpoints, conversionValue);
        }
        List<Map<String, Object>> rules;
        try {
            rules = objectMapper.readValue(customWeights, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("自定义权重 JSON 解析失败, 回退线性归因: {}", e.getMessage());
            return applyLinear(touchpoints, conversionValue);
        }
        Map<Long, Double> weights = new LinkedHashMap<>();
        double[] raw = new double[touchpoints.size()];
        double sum = 0;
        for (int i = 0; i < touchpoints.size(); i++) {
            ScrmAttributionTouchpointEntity tp = touchpoints.get(i);
            double w = 1.0;
            for (Map<String, Object> rule : rules) {
                String type = toStringValue(rule.get("touchpointType"));
                String channel = toStringValue(rule.get("channel"));
                double ruleWeight = toDouble(rule.get("weight"));
                boolean matchType = !type.isEmpty() && type.equals(tp.getTouchpointType());
                boolean matchChannel = !channel.isEmpty() && channel.equals(tp.getChannel());
                if (matchType || matchChannel) {
                    w = Math.max(w, ruleWeight);
                }
            }
            raw[i] = w;
            sum += w;
        }
        for (int i = 0; i < touchpoints.size(); i++) {
            weights.put(touchpoints.get(i).getId(), sum > 0 ? raw[i] / sum : 1.0 / touchpoints.size());
        }
        return weights;
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
     * 将对象转换为字符串。
     *
     * @param obj 对象
     * @return 字符串, null 返回空字符串
     */
    private String toStringValue(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 将对象转换为 double 数值。
     * <p>供报告/统计兄弟类共用。</p>
     *
     * @param obj 对象
     * @return double 值, 不可转换时返回 0
     */
    static double toDouble(Object obj) {
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
     * 将统计结果数组的指定位置转为 long。
     * <p>供报告/统计兄弟类共用。</p>
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return long 值
     */
    static long toLong(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0L;
        }
        if (stats[index] instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(stats[index].toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 将统计结果数组的指定位置转为 double。
     * <p>供报告/统计兄弟类共用。</p>
     *
     * @param stats 统计结果数组
     * @param index 索引
     * @return double 值
     */
    static double toDouble(Object[] stats, int index) {
        if (stats == null || index >= stats.length || stats[index] == null) {
            return 0;
        }
        return toDouble(stats[index]);
    }

    /**
     * 保留两位小数。
     * <p>供报告/统计兄弟类共用。</p>
     *
     * @param value 原始值
     * @return 保留两位小数后的值
     */
    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 保留四位小数。
     * <p>供报告/统计兄弟类共用。</p>
     *
     * @param value 原始值
     * @return 保留四位小数后的值
     */
    static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
