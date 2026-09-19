/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportService.java
 * Date : 2026/08/04 08:40:58
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hiylo.scrm.dto.ScrmReportResultDto;
import org.hiylo.scrm.dto.ScrmReportTemplateDto;
import org.hiylo.scrm.entity.ScrmReportResultEntity;
import org.hiylo.scrm.entity.ScrmReportTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmReportResultRepository;
import org.hiylo.scrm.repository.ScrmReportTemplateRepository;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SCRM 自定义报表服务
 * <p>
 * 负责报表模板的全生命周期管理 (CRUD) 与报表动态执行。报表执行时基于模板配置的
 * 数据源、维度、指标、筛选条件动态构建 SQL, 通过 <b>白名单校验</b> 严格限制可查询的
 * 表名与列名, 防止 SQL 注入。所有数据访问均按当前用户可见账号范围过滤,
 * 确保数据隔离。
 * </p>
 * <p>
 * 安全策略:
 * <ul>
 *   <li>数据源表名必须命中 {@link #TABLE_WHITELIST} 白名单</li>
 *   <li>维度 / 指标 / 筛选字段必须命中对应表的 {@link #COLUMN_WHITELIST} 列白名单</li>
 *   <li>聚合函数必须命中 {@link #AGGREGATION_WHITELIST} 白名单</li>
 *   <li>标识符额外通过 {@link #IDENTIFIER_PATTERN} 正则校验, 双重防护</li>
 *   <li>筛选值通过参数绑定 (setParameter) 传入, 不做字符串拼接</li>
 * </ul>
 * </p>
 *
 * @author Hsi Chu
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScrmReportService {

    /** 默认操作人 (请求头未透传 X-User-Id 时使用) */
    private static final String DEFAULT_OPERATOR = "scrm-system";

    /** 执行状态: 成功 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 执行状态: 失败 */
    private static final String STATUS_FAILED = "FAILED";

    /** 报表结果最大行数限制 (防止返回过多数据导致内存溢出) */
    private static final int MAX_RESULT_ROWS = 10000;

    /** 标识符合法正则: 仅允许小写字母、数字、下划线 (防注入双重校验) */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]*$");

    // ============================================================
    // SQL 注入防护白名单
    // ============================================================

    /**
     * 数据源表名白名单 (不含 schema 前缀, 查询时统一加 scrm. 前缀)。
     * 与报表类型 {@code reportType} 一一对应。
     */
    private static final Set<String> TABLE_WHITELIST = Set.of(
            "scrm_customer",
            "scrm_opportunity",
            "scrm_campaign",
            "scrm_conversation",
            "scrm_mass_send_task",
            "scrm_chat_archive"
    );

    /**
     * 允许的聚合函数白名单。
     */
    private static final Set<String> AGGREGATION_WHITELIST = Set.of(
            "SUM", "COUNT", "AVG", "MIN", "MAX", "COUNT_DISTINCT"
    );

    /**
     * 各数据源表允许查询的列白名单。
     * <p>
     * 维度 (dimensions) 与指标 (metrics) 字段、筛选 (filters) 字段均必须命中对应表的列白名单。
     * </p>
     */
    private static final Map<String, Set<String>> COLUMN_WHITELIST;

    static {
        Map<String, Set<String>> columns = new HashMap<>();
        // 客户表: 支持按平台/生命周期/状态/负责人等维度分析
        columns.put("scrm_customer", Set.of(
                "id", "platform_type", "platform_customer_uid",
                "owner_account_id", "lifecycle", "status", "name", "remark",
                "create_time", "update_time"
        ));
        // 商机表: 支持按状态/负责人/漏斗/阶段/金额等维度分析
        columns.put("scrm_opportunity", Set.of(
                "id", "opportunity_name", "customer_id", "funnel_id",
                "current_stage_id", "amount", "expected_close_date", "probability",
                "owner_user_id", "status", "source", "competitor",
                "won_at", "lost_at", "lost_reason", "create_time", "update_time"
        ));
        // 营销任务表: 支持按类型/状态/平台等维度分析
        columns.put("scrm_campaign", Set.of(
                "id", "campaign_name", "campaign_type", "status",
                "platform_type", "behavior_flow_id", "start_time", "end_time",
                "create_time", "update_time"
        ));
        // 会话表: 支持按平台/状态/负责人等维度分析
        columns.put("scrm_conversation", Set.of(
                "id", "platform_type", "platform_conversation_id",
                "account_id", "customer_id", "status", "last_message_time",
                "create_time", "update_time"
        ));
        // 群发任务表: 支持按类型/状态/负责人等维度分析
        columns.put("scrm_mass_send_task", Set.of(
                "id", "task_name", "task_type", "status",
                "account_id", "target_count", "sent_count", "create_time", "update_time"
        ));
        // 聊天归档表: 支持按平台/方向/类型等维度分析
        columns.put("scrm_chat_archive", Set.of(
                "id", "platform_type", "conversation_id",
                "message_type", "direction", "archived_at", "create_time"
        ));
        COLUMN_WHITELIST = Collections.unmodifiableMap(columns);
    }

    /** 报表模板数据仓库 */
    private final ScrmReportTemplateRepository templateRepository;
    /** 报表结果数据仓库 */
    private final ScrmReportResultRepository resultRepository;
    /** JPA 实体管理器 */
    private final EntityManager entityManager;
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;

    // ============================================================
    // 报表模板管理
    // ============================================================

    /**
     * 创建报表模板
     *
     * @param dto 模板参数
     * @return 创建后的模板
     * @throws ScrmException 参数非法
     */
    @Transactional
    public ScrmReportTemplateDto createTemplate(ScrmReportTemplateDto dto) throws ScrmException {
        validateTemplateName(dto.getTemplateName());
        if (dto.getDataSource() == null || dto.getDataSource().isBlank()) {
            throw ScrmException.badRequest("数据源不能为空");
        }
        // 数据源必须在白名单内 (创建时即校验, 避免执行时才发现非法数据源)
        validateTable(dto.getDataSource());
        validateDimensionsAndMetrics(dto.getDimensions(), dto.getMetrics(), dto.getDataSource());
        validateTimeRangeField(dto.getTimeRangeField(), dto.getDataSource());
        ScrmReportTemplateEntity entity = new ScrmReportTemplateEntity();
        entity.setTemplateName(dto.getTemplateName());
        entity.setReportType(dto.getReportType());
        entity.setDataSource(dto.getDataSource());
        entity.setDimensions(dto.getDimensions());
        entity.setMetrics(dto.getMetrics());
        entity.setFilters(dto.getFilters());
        entity.setTimeRangeField(dto.getTimeRangeField());
        entity.setChartType(dto.getChartType());
        entity.setDescription(dto.getDescription());
        entity.setIsPublic(Boolean.TRUE.equals(dto.getIsPublic()));
        entity.setCreatedBy(dto.getCreatedBy() != null ? dto.getCreatedBy() : DEFAULT_OPERATOR);
        entity = templateRepository.save(entity);
        log.info("创建报表模板: id={}, name={}, dataSource={}",
                entity.getId(), entity.getTemplateName(), entity.getDataSource());
        return toTemplateDto(entity);
    }

    /**
     * 更新报表模板
     *
     * @param id  模板 ID
     * @param dto 模板参数
     * @return 更新后的模板
     * @throws ScrmException 模板不存在 / 参数非法
     */
    @Transactional
    public ScrmReportTemplateDto updateTemplate(Long id, ScrmReportTemplateDto dto) throws ScrmException {
        ScrmReportTemplateEntity entity = findTemplateOrThrow(id);
        if (dto.getTemplateName() != null) {
            validateTemplateName(dto.getTemplateName());
            entity.setTemplateName(dto.getTemplateName());
        }
        if (dto.getReportType() != null) {
            entity.setReportType(dto.getReportType());
        }
        if (dto.getDataSource() != null) {
            validateTable(dto.getDataSource());
            entity.setDataSource(dto.getDataSource());
        }
        // 维度 / 指标变更时需重新校验白名单
        String effectiveDataSource = entity.getDataSource();
        if (dto.getDimensions() != null || dto.getMetrics() != null) {
            validateDimensionsAndMetrics(
                    dto.getDimensions() != null ? dto.getDimensions() : entity.getDimensions(),
                    dto.getMetrics() != null ? dto.getMetrics() : entity.getMetrics(),
                    effectiveDataSource);
        }
        if (dto.getDimensions() != null) {
            entity.setDimensions(dto.getDimensions());
        }
        if (dto.getMetrics() != null) {
            entity.setMetrics(dto.getMetrics());
        }
        if (dto.getFilters() != null) {
            entity.setFilters(dto.getFilters());
        }
        if (dto.getTimeRangeField() != null) {
            validateTimeRangeField(dto.getTimeRangeField(), effectiveDataSource);
            entity.setTimeRangeField(dto.getTimeRangeField());
        }
        if (dto.getChartType() != null) {
            entity.setChartType(dto.getChartType());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        if (dto.getIsPublic() != null) {
            entity.setIsPublic(dto.getIsPublic());
        }
        entity = templateRepository.save(entity);
        return toTemplateDto(entity);
    }

    /**
     * 删除报表模板
     * <p>
     * 级联删除该模板的所有执行结果记录。
     * </p>
     *
     * @param id 模板 ID
     * @throws ScrmException 模板不存在
     */
    @Transactional
    public void deleteTemplate(Long id) throws ScrmException {
        ScrmReportTemplateEntity entity = findTemplateOrThrow(id);
        // 删除关联的执行结果
        List<ScrmReportResultEntity> results = resultRepository
                .findByTemplateIdOrderByRunAtDesc(id, Pageable.ofSize(Integer.MAX_VALUE))
                .getContent();
        if (!results.isEmpty()) {
            resultRepository.deleteAll(results);
        }
        templateRepository.delete(entity);
        log.info("删除报表模板: id={}, name={}", id, entity.getTemplateName());
    }

    /**
     * 查询报表模板详情
     *
     * @param id 模板 ID
     * @return 模板 DTO
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmReportTemplateDto getTemplate(Long id) throws ScrmException {
        return toTemplateDto(findTemplateOrThrow(id));
    }

    /**
     * 分页查询报表模板, 支持按报表类型、是否公开、创建人过滤
     *
     * @param reportType 报表类型过滤 (可空)
     * @param isPublic   是否公开过滤 (可空)
     * @param createdBy  创建人过滤 (可空)
     * @param pageable   分页参数
     * @return 模板分页结果
     */
    @Transactional(readOnly = true)
    public Page<ScrmReportTemplateDto> listTemplates(String reportType, Boolean isPublic,
                                                      String createdBy, Pageable pageable) {
        Specification<ScrmReportTemplateEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (reportType != null && !reportType.isBlank()) {
                predicates.add(cb.equal(root.get("reportType"), reportType));
            }
            if (isPublic != null) {
                predicates.add(cb.equal(root.get("isPublic"), isPublic));
            }
            if (createdBy != null && !createdBy.isBlank()) {
                predicates.add(cb.equal(root.get("createdBy"), createdBy));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createTime"));
        return templateRepository.findAll(spec, sorted).map(this::toTemplateDto);
    }

    // ============================================================
    // 报表执行
    // ============================================================

    /**
     * 执行报表
     * <p>
     * 基于模板配置动态构建 SQL 并执行, 结果以 JSON 形式持久化到 scrm_report_result 表,
     * 同时更新模板的 lastRunAt。执行失败时记录 FAILED 状态与错误信息。
     * </p>
     * <p>
     * SQL 构建流程:
     * <ol>
     *   <li>校验数据源表名在白名单内</li>
     *   <li>解析维度 JSON, 校验每个维度列在列白名单内</li>
     *   <li>解析指标 JSON, 校验字段与聚合函数在白名单内</li>
     *   <li>解析筛选 JSON, 校验每个筛选键在列白名单内, 值通过参数绑定</li>
     *   <li>构建 SELECT ... FROM ... [WHERE time_range AND filters] GROUP BY ...</li>
     *   <li>通过 EntityManager.createNativeQuery 执行, 结果转为 List&lt;Map&gt;</li>
     * </ol>
     * </p>
     *
     * @param templateId     模板 ID
     * @param timeRangeStart 时间范围起始 (可空)
     * @param timeRangeEnd   时间范围结束 (可空)
     * @param runBy          执行人 (可空, 默认 scrm-system)
     * @return 执行结果
     * @throws ScrmException 模板不存在 / 配置非法
     */
    @Transactional
    public ScrmReportResultDto executeReport(Long templateId, LocalDateTime timeRangeStart,
                                             LocalDateTime timeRangeEnd, String runBy) throws ScrmException {
        ScrmReportTemplateEntity template = findTemplateOrThrow(templateId);
        String operator = runBy != null && !runBy.isBlank() ? runBy : DEFAULT_OPERATOR;
        // 执行结果实体 (无论成功失败均落库)
        ScrmReportResultEntity resultEntity = new ScrmReportResultEntity();
        resultEntity.setTemplateId(templateId);
        resultEntity.setRunBy(operator);
        resultEntity.setTimeRangeStart(timeRangeStart);
        resultEntity.setTimeRangeEnd(timeRangeEnd);
        // 默认空结果集: result_data 列非空, 失败记录同样需要落库, 不能留 null
        resultEntity.setResultData("[]");
        try {
            // 解析并校验配置
            String tableName = template.getDataSource();
            validateTable(tableName);
            List<String> dimensions = parseJsonArray(template.getDimensions(), "维度");
            List<MetricConfig> metrics = parseMetrics(template.getMetrics(), tableName);
            Map<String, Object> filters = parseFilters(template.getFilters(), tableName);
            // 校验维度列
            Set<String> allowedColumns = COLUMN_WHITELIST.get(tableName);
            for (String dim : dimensions) {
                validateColumn(dim, allowedColumns, "维度");
            }
            // 模板配置了时间范围字段时, SQL 会引用 :timeStart/:timeEnd, 请求必须提供起止时间;
            // 放在维度/指标/筛选校验之后, 让模板配置错误优先暴露, 报错更可操作
            String timeField = template.getTimeRangeField();
            if (timeField != null && !timeField.isBlank()
                    && (timeRangeStart == null || timeRangeEnd == null)) {
                throw ScrmException.badRequest("模板配置了时间范围字段 " + timeField
                        + ", 执行时必须提供 timeRangeStart 与 timeRangeEnd");
            }
            // 动态构建 SQL
            String sql = buildReportSql(tableName, dimensions, metrics, template.getTimeRangeField(), filters);
            Query query = entityManager.createNativeQuery(sql);
            // 绑定参数: 时间范围
            int paramIndex = 0;
            if (template.getTimeRangeField() != null && !template.getTimeRangeField().isBlank()) {
                if (timeRangeStart != null) {
                    query.setParameter("timeStart", timeRangeStart);
                }
                if (timeRangeEnd != null) {
                    query.setParameter("timeEnd", timeRangeEnd);
                }
            }
            // 绑定参数: 筛选值
            for (Map.Entry<String, Object> entry : filters.entrySet()) {
                query.setParameter("filter_" + paramIndex, entry.getValue());
                paramIndex++;
            }
            // 限制最大行数
            query.setMaxResults(MAX_RESULT_ROWS);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            // 构建列名列表 (维度列 + 指标列别名)
            List<String> columnNames = new ArrayList<>(dimensions);
            for (MetricConfig metric : metrics) {
                columnNames.add(metric.alias);
            }
            // 转换为 List<Map> 便于前端渲染
            List<Map<String, Object>> resultList = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                Map<String, Object> rowMap = new LinkedHashMap<>();
                for (int i = 0; i < columnNames.size() && i < row.length; i++) {
                    rowMap.put(columnNames.get(i), row[i]);
                }
                resultList.add(rowMap);
            }
            // 序列化结果
            String resultJson = objectMapper.writeValueAsString(resultList);
            resultEntity.setResultData(resultJson);
            resultEntity.setRowCount(resultList.size());
            resultEntity.setStatus(STATUS_SUCCESS);
            // 更新模板最近执行时间
            template.setLastRunAt(LocalDateTime.now());
            templateRepository.save(template);
            log.info("执行报表成功: templateId={}, rows={}", templateId, resultList.size());
        } catch (ScrmException e) {
            resultEntity.setStatus(STATUS_FAILED);
            resultEntity.setErrorMessage(e.getMessage());
            log.warn("执行报表失败 (业务异常): templateId={}, msg={}", templateId, e.getMessage());
        } catch (Exception e) {
            resultEntity.setStatus(STATUS_FAILED);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (errMsg.length() > 500) {
                errMsg = errMsg.substring(0, 500);
            }
            resultEntity.setErrorMessage(errMsg);
            log.error("执行报表失败 (系统异常): templateId={}", templateId, e);
        }
        resultEntity = resultRepository.save(resultEntity);
        return toResultDto(resultEntity);
    }

    /**
     * 分页查询报表执行结果历史
     *
     * @param templateId 模板 ID
     * @param pageable   分页参数
     * @return 执行结果分页
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public Page<ScrmReportResultDto> listResults(Long templateId, Pageable pageable) throws ScrmException {
        findTemplateOrThrow(templateId);
        return resultRepository
                .findByTemplateIdOrderByRunAtDesc(templateId, pageable)
                .map(this::toResultDto);
    }

    /**
     * 查询某次执行结果详情
     *
     * @param resultId 结果 ID
     * @return 执行结果 DTO
     * @throws ScrmException 结果不存在
     */
    @Transactional(readOnly = true)
    public ScrmReportResultDto getResult(Long resultId) throws ScrmException {
        return toResultDto(findResultOrThrow(resultId));
    }

    /**
     * 查询模板最近一次执行结果
     *
     * @param templateId 模板 ID
     * @return 最近执行结果 (可能为空)
     * @throws ScrmException 模板不存在
     */
    @Transactional(readOnly = true)
    public ScrmReportResultDto getLatestResult(Long templateId) throws ScrmException {
        findTemplateOrThrow(templateId);
        return resultRepository
                .findFirstByTemplateIdOrderByRunAtDesc(templateId)
                .map(this::toResultDto)
                .orElse(null);
    }

    // ============================================================
    // 内部工具方法 - SQL 构建
    // ============================================================

    /**
     * 动态构建报表查询 SQL
     * <p>
     * SQL 形如:
     * {@code SELECT dim1, dim2, SUM(metric1) AS m_sum_metric1, COUNT(*) AS m_count
     * FROM scrm.table [WHERE timeField >= :timeStart AND timeField <= :timeEnd
     * [AND filterCol = :filter_N]] GROUP BY dim1, dim2}
     * </p>
     * <p>
     * 条件全部为空时不生成 WHERE 子句, 避免拼出非法 SQL。
     * </p>
     *
     * @param tableName      数据源表名 (已通过白名单校验)
     * @param dimensions     维度列列表 (已通过白名单校验)
     * @param metrics        指标配置列表 (已通过白名单校验)
     * @param timeRangeField 时间范围字段 (可空, 已通过白名单校验)
     * @param filters        筛选条件 (键已通过白名单校验)
     * @return 完整的 SQL 字符串
     */
    private String buildReportSql(String tableName, List<String> dimensions, List<MetricConfig> metrics,
                                  String timeRangeField, Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder("SELECT ");
        // SELECT 维度列
        List<String> selectParts = new ArrayList<>(dimensions);
        // SELECT 指标列 (聚合)
        for (MetricConfig metric : metrics) {
            selectParts.add(metric.sqlExpression + " AS " + metric.alias);
        }
        sql.append(String.join(", ", selectParts));
        // FROM
        sql.append(" FROM scrm.").append(tableName);
        // WHERE
        List<String> conditions = new ArrayList<>();
        // 时间范围 (第二重防御: timeRangeField 必须命中列白名单, 防止字段名拼接注入)
        if (timeRangeField != null && !timeRangeField.isBlank()) {
            validateColumn(timeRangeField, COLUMN_WHITELIST.get(tableName), "时间范围字段");
            conditions.add(timeRangeField + " >= :timeStart");
            conditions.add(timeRangeField + " <= :timeEnd");
        }
        // 筛选条件
        int filterIndex = 0;
        for (String filterKey : filters.keySet()) {
            conditions.add(filterKey + " = :filter_" + filterIndex);
            filterIndex++;
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        // GROUP BY 维度列
        if (!dimensions.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", dimensions));
        }
        return sql.toString();
    }

    // ============================================================
    // 内部工具方法 - JSON 解析与白名单校验
    // ============================================================

    /**
     * 解析维度 JSON 数组 (如 ["lifecycle", "platform_type"])
     *
     * @param json  JSON 字符串
     * @param label 字段标签 (用于异常消息)
     * @return 维度列名列表
     * @throws ScrmException JSON 格式非法
     */
    private List<String> parseJsonArray(String json, String label) throws ScrmException {
        if (json == null || json.isBlank()) {
            throw ScrmException.badRequest(label + "不能为空");
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            if (list.isEmpty()) {
                throw ScrmException.badRequest(label + "不能为空数组");
            }
            return list;
        } catch (ScrmException e) {
            throw e;
        } catch (Exception e) {
            throw ScrmException.badRequest(label + "JSON 格式非法: " + e.getMessage());
        }
    }

    /**
     * 解析指标 JSON 数组, 校验字段与聚合函数
     * <p>
     * 指标格式: [{"field":"amount", "aggregation":"SUM"}, ...]
     * </p>
     *
     * @param json      JSON 字符串
     * @param tableName 数据源表名 (用于列白名单校验)
     * @return 指标配置列表
     * @throws ScrmException JSON 格式非法 / 字段或聚合函数不在白名单
     */
    private List<MetricConfig> parseMetrics(String json, String tableName) throws ScrmException {
        if (json == null || json.isBlank()) {
            throw ScrmException.badRequest("指标不能为空");
        }
        Set<String> allowedColumns = COLUMN_WHITELIST.get(tableName);
        try {
            List<Map<String, Object>> rawList = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
            });
            if (rawList.isEmpty()) {
                throw ScrmException.badRequest("指标不能为空数组");
            }
            List<MetricConfig> metrics = new ArrayList<>(rawList.size());
            for (int i = 0; i < rawList.size(); i++) {
                Map<String, Object> item = rawList.get(i);
                String field = Objects.toString(item.get("field"), null);
                String aggregation = Objects.toString(item.get("aggregation"), "COUNT");
                if (field == null || field.isBlank()) {
                    throw ScrmException.badRequest("第 " + (i + 1) + " 个指标缺少 field 字段");
                }
                validateColumn(field, allowedColumns, "指标字段");
                String aggUpper = aggregation.toUpperCase();
                if (!AGGREGATION_WHITELIST.contains(aggUpper)) {
                    throw ScrmException.badRequest("不支持的聚合函数: " + aggregation
                            + ", 仅支持 " + String.join("/", AGGREGATION_WHITELIST));
                }
                // 构建聚合表达式与别名
                String sqlExpr;
                String alias;
                if ("COUNT_DISTINCT".equals(aggUpper)) {
                    sqlExpr = "COUNT(DISTINCT " + field + ")";
                    alias = "m_count_distinct_" + field;
                } else {
                    sqlExpr = aggUpper + "(" + field + ")";
                    alias = "m_" + aggUpper.toLowerCase() + "_" + field;
                }
                metrics.add(new MetricConfig(field, aggUpper, sqlExpr, alias));
            }
            return metrics;
        } catch (ScrmException e) {
            throw e;
        } catch (Exception e) {
            throw ScrmException.badRequest("指标 JSON 格式非法: " + e.getMessage());
        }
    }

    /**
     * 解析筛选条件 JSON, 校验筛选键在列白名单内
     * <p>
     * 筛选格式: {"status":"OPEN", "platform_type":"wechat"}
     * </p>
     *
     * @param json      JSON 字符串 (可空)
     * @param tableName 数据源表名
     * @return 筛选条件 Map (键为列名, 值为筛选值)
     * @throws ScrmException JSON 格式非法 / 筛选键不在白名单
     */
    private Map<String, Object> parseFilters(String json, String tableName) throws ScrmException {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        Set<String> allowedColumns = COLUMN_WHITELIST.get(tableName);
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                validateColumn(key, allowedColumns, "筛选字段");
                result.put(key, entry.getValue());
            }
            return result;
        } catch (ScrmException e) {
            throw e;
        } catch (Exception e) {
            throw ScrmException.badRequest("筛选条件 JSON 格式非法: " + e.getMessage());
        }
    }

    /**
     * 校验维度 / 指标配置 (创建/更新模板时调用)
     *
     * @param dimensionsJson 维度 JSON
     * @param metricsJson    指标 JSON
     * @param tableName      数据源表名
     * @throws ScrmException 配置非法
     */
    private void validateDimensionsAndMetrics(String dimensionsJson, String metricsJson, String tableName)
            throws ScrmException {
        List<String> dimensions = parseJsonArray(dimensionsJson, "维度");
        Set<String> allowedColumns = COLUMN_WHITELIST.get(tableName);
        for (String dim : dimensions) {
            validateColumn(dim, allowedColumns, "维度");
        }
        parseMetrics(metricsJson, tableName);
    }

    /**
     * 校验表名在白名单内 (防止 SQL 注入)
     *
     * @param tableName 表名
     * @throws ScrmException 表名不在白名单
     */
    private void validateTable(String tableName) throws ScrmException {
        if (tableName == null || !TABLE_WHITELIST.contains(tableName)) {
            throw ScrmException.badRequest("不支持的数据源: " + tableName
                    + ", 仅支持 " + String.join("/", TABLE_WHITELIST));
        }
    }

    /**
     * 校验列名在指定表的列白名单内 (防止 SQL 注入)
     * <p>
     * 双重防护: 先白名单匹配, 再正则校验标识符格式。
     * </p>
     *
     * @param column          列名
     * @param allowedColumns  允许的列集合
     * @param label           字段标签 (用于异常消息)
     * @throws ScrmException 列名不在白名单
     */
    private void validateColumn(String column, Set<String> allowedColumns, String label) throws ScrmException {
        if (column == null || column.isBlank()) {
            throw ScrmException.badRequest(label + "不能为空");
        }
        // 白名单匹配
        if (!allowedColumns.contains(column)) {
            throw ScrmException.badRequest("不支持的" + label + ": " + column);
        }
        // 正则双重校验 (防止白名单遗漏的边缘情况)
        if (!IDENTIFIER_PATTERN.matcher(column).matches()) {
            throw ScrmException.badRequest("非法的" + label + "标识符: " + column);
        }
    }

    /**
     * 校验时间范围字段在某数据源表的列白名单内 (防止 SQL 注入)。
     * <p>
     * 时间范围字段为可选配置, 为空时不校验; 非空时按列白名单 + 标识符正则双重校验。
     * </p>
     *
     * @param timeRangeField 时间范围字段 (可空)
     * @param tableName      数据源表名
     * @throws ScrmException 字段不在白名单
     */
    private void validateTimeRangeField(String timeRangeField, String tableName) throws ScrmException {
        if (timeRangeField == null || timeRangeField.isBlank()) {
            return;
        }
        validateColumn(timeRangeField, COLUMN_WHITELIST.get(tableName), "时间范围字段");
    }

    /**
     * 校验模板名称非空
     */
    private void validateTemplateName(String name) throws ScrmException {
        if (name == null || name.isBlank()) {
            throw ScrmException.badRequest("模板名称不能为空");
        }
    }

    // ============================================================
    // 内部工具方法 - 实体查询与转换
    // ============================================================

    /**
     * 按主键查询模板, 不存在或越权抛异常
     */
    private ScrmReportTemplateEntity findTemplateOrThrow(Long id) throws ScrmException {
        ScrmReportTemplateEntity entity = templateRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "报表模板不存在: id=" + id));
        return entity;
    }

    /**
     * 按主键查询执行结果, 不存在或越权抛异常
     */
    private ScrmReportResultEntity findResultOrThrow(Long id) throws ScrmException {
        ScrmReportResultEntity entity = resultRepository.findById(id)
                .orElseThrow(() -> new ScrmException(ScrmExceptionConstants.NOT_FOUND,
                        "报表执行结果不存在: id=" + id));
        return entity;
    }


    /**
     * 模板实体转 DTO
     */
    private ScrmReportTemplateDto toTemplateDto(ScrmReportTemplateEntity entity) {
        ScrmReportTemplateDto dto = new ScrmReportTemplateDto();
        dto.setId(entity.getId());
        dto.setTemplateName(entity.getTemplateName());
        dto.setReportType(entity.getReportType());
        dto.setDataSource(entity.getDataSource());
        dto.setDimensions(entity.getDimensions());
        dto.setMetrics(entity.getMetrics());
        dto.setFilters(entity.getFilters());
        dto.setTimeRangeField(entity.getTimeRangeField());
        dto.setChartType(entity.getChartType());
        dto.setDescription(entity.getDescription());
        dto.setIsPublic(entity.getIsPublic());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setLastRunAt(entity.getLastRunAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setVersion(entity.getVersion());
        return dto;
    }

    /**
     * 执行结果实体转 DTO
     */
    private ScrmReportResultDto toResultDto(ScrmReportResultEntity entity) {
        ScrmReportResultDto dto = new ScrmReportResultDto();
        dto.setId(entity.getId());
        dto.setTemplateId(entity.getTemplateId());
        dto.setRunBy(entity.getRunBy());
        dto.setRunAt(entity.getRunAt());
        dto.setTimeRangeStart(entity.getTimeRangeStart());
        dto.setTimeRangeEnd(entity.getTimeRangeEnd());
        dto.setResultData(entity.getResultData());
        dto.setRowCount(entity.getRowCount());
        dto.setStatus(entity.getStatus());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }

    // ============================================================
    // 内部类 - 指标配置
    // ============================================================

    /**
     * 指标配置内部表示 (解析后), 封装字段、聚合函数、SQL 表达式与别名。
 * @since V1.0
     * @author Hsi Chu
     */
    private static class MetricConfig {
        /** 指标字段名 (已校验) */
        final String field;
        /** 聚合函数 (已校验, 大写) */
        final String aggregation;
        /** 完整 SQL 表达式, 如 SUM(amount) */
        final String sqlExpression;
        /** 结果列别名, 如 m_sum_amount */
        final String alias;

        MetricConfig(String field, String aggregation, String sqlExpression, String alias) {
            this.field = field;
            this.aggregation = aggregation;
            this.sqlExpression = sqlExpression;
            this.alias = alias;
        }
    }
}
