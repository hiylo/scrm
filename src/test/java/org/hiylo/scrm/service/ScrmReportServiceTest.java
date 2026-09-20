/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmReportServiceTest.java
 * Date : 2026/08/10 11:01:41
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.hiylo.scrm.dto.ScrmReportResultDto;
import org.hiylo.scrm.dto.ScrmReportTemplateDto;
import org.hiylo.scrm.entity.ScrmReportResultEntity;
import org.hiylo.scrm.entity.ScrmReportTemplateEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmReportResultRepository;
import org.hiylo.scrm.repository.ScrmReportTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmReportService 单元测试
 * <p>
 * 聚焦自定义报表模板的 CRUD、SQL 注入白名单校验 (表名 / 列名 / 聚合函数)、
 * 报表动态执行流程 (成功 / 失败落库)、越权隔离与最近结果查询等关键业务逻辑。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("ScrmReportService 单元测试")
@ExtendWith(MockitoExtension.class)
class ScrmReportServiceTest {

    /** 报表模板仓库 Mock */
    @Mock
    private ScrmReportTemplateRepository templateRepository;
    /** 报表执行结果仓库 Mock */
    @Mock
    private ScrmReportResultRepository resultRepository;
    /** JPA 实体管理器 Mock */
    @Mock
    private EntityManager entityManager;

    /** ObjectMapper 使用真实实例, 不 mock (遵循约束) */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmReportService service;

    /** 报表执行测试用的时间范围起点 (模板配置了 timeRangeField 时必填) */
    private static final LocalDateTime TIME_START = LocalDateTime.of(2026, 1, 1, 0, 0);
    /** 报表执行测试用的时间范围终点 */
    private static final LocalDateTime TIME_END = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

    @BeforeEach
    void setUp() {
        service = new ScrmReportService(templateRepository, resultRepository, entityManager, objectMapper);
    }

    @AfterEach
    void tearDown() {
    }

    /**
     * 构造合法的模板 DTO (用于 createTemplate)
     */
    private ScrmReportTemplateDto buildTemplateDto() {
        ScrmReportTemplateDto dto = new ScrmReportTemplateDto();
        dto.setTemplateName("客户生命周期分析");
        dto.setReportType("CUSTOMER");
        dto.setDataSource("scrm_customer");
        dto.setDimensions("[\"lifecycle\",\"platform_type\"]");
        dto.setMetrics("[{\"field\":\"id\",\"aggregation\":\"COUNT\"}]");
        dto.setFilters("{\"status\":\"ACTIVE\"}");
        dto.setTimeRangeField("create_time");
        dto.setChartType("BAR");
        dto.setIsPublic(true);
        dto.setCreatedBy("user1");
        return dto;
    }

    /**
     * 构造已持久化的模板实体 (用于 findById 返回)
     */
    private ScrmReportTemplateEntity buildTemplateEntity(Long id) {
        ScrmReportTemplateEntity entity = new ScrmReportTemplateEntity();
        entity.setId(id);
        entity.setTemplateName("客户生命周期分析");
        entity.setReportType("CUSTOMER");
        entity.setDataSource("scrm_customer");
        entity.setDimensions("[\"lifecycle\",\"platform_type\"]");
        entity.setMetrics("[{\"field\":\"id\",\"aggregation\":\"COUNT\"}]");
        entity.setFilters("{\"status\":\"ACTIVE\"}");
        entity.setTimeRangeField("create_time");
        entity.setChartType("BAR");
        entity.setIsPublic(true);
        entity.setCreatedBy("user1");
        return entity;
    }

    /**
     * 构造已持久化的执行结果实体
     */
    private ScrmReportResultEntity buildResultEntity(Long id, Long templateId, String status) {
        ScrmReportResultEntity entity = new ScrmReportResultEntity();
        entity.setId(id);
        entity.setTemplateId(templateId);
        entity.setRunBy("user1");
        entity.setRunAt(LocalDateTime.now());
        entity.setResultData("[]");
        entity.setRowCount(0);
        entity.setStatus(status);
        return entity;
    }

    @Test
    @DisplayName("createTemplate: 写入账号 ID 与默认操作人并持久化")
    void createTemplate_success() throws ScrmException {
        ScrmReportTemplateDto dto = buildTemplateDto();
        when(templateRepository.save(any(ScrmReportTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReportTemplateDto result = service.createTemplate(dto);

        ArgumentCaptor<ScrmReportTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmReportTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        ScrmReportTemplateEntity saved = captor.getValue();
        assertThat(saved.getTemplateName()).isEqualTo("客户生命周期分析");
        // isPublic 转换为基本 boolean
        assertThat(saved.getIsPublic()).isTrue();
        // createdBy 沿用 dto 中的 user1
        assertThat(saved.getCreatedBy()).isEqualTo("user1");
        assertThat(result.getDataSource()).isEqualTo("scrm_customer");
    }

    @Test
    @DisplayName("createTemplate: createdBy 为空时填充默认操作人")
    void createTemplate_defaultOperator() throws ScrmException {
        ScrmReportTemplateDto dto = buildTemplateDto();
        dto.setCreatedBy(null);
        when(templateRepository.save(any(ScrmReportTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createTemplate(dto);

        ArgumentCaptor<ScrmReportTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmReportTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("scrm-system");
    }

    @Test
    @DisplayName("createTemplate: 模板名称为空抛 BAD_REQUEST")
    void createTemplate_blankName() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        dto.setTemplateName("  ");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("模板名称不能为空");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 数据源为空抛 BAD_REQUEST")
    void createTemplate_blankDataSource() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        dto.setDataSource("");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("数据源不能为空");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 数据源不在白名单抛 BAD_REQUEST (防 SQL 注入)")
    void createTemplate_invalidDataSource() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        dto.setDataSource("scrm_user"); // 不在白名单

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不支持的数据源");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 维度列不在白名单抛 BAD_REQUEST (防 SQL 注入)")
    void createTemplate_invalidDimension() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        // password 不在 scrm_customer 列白名单
        dto.setDimensions("[\"password\"]");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不支持的维度");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 聚合函数不在白名单抛 BAD_REQUEST (防 SQL 注入)")
    void createTemplate_invalidAggregation() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        // STDDEV 不在聚合函数白名单
        dto.setMetrics("[{\"field\":\"id\",\"aggregation\":\"STDDEV\"}]");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不支持的聚合函数");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTemplate: 维度 JSON 格式非法抛 BAD_REQUEST")
    void createTemplate_invalidDimensionsJson() {
        ScrmReportTemplateDto dto = buildTemplateDto();
        dto.setDimensions("not a json");

        assertThatThrownBy(() -> service.createTemplate(dto))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("JSON 格式非法");
        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTemplate: 模板不存在抛 NOT_FOUND")
    void updateTemplate_notFound() {
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(10L, buildTemplateDto()))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表模板不存在");
        verify(templateRepository, never()).save(any());
    }

    
    @Test
    @DisplayName("updateTemplate: 仅更新 templateName 字段")
    void updateTemplate_partialUpdate() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(templateRepository.save(any(ScrmReportTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmReportTemplateDto dto = new ScrmReportTemplateDto();
        dto.setTemplateName("新模板名");

        ScrmReportTemplateDto result = service.updateTemplate(10L, dto);

        ArgumentCaptor<ScrmReportTemplateEntity> captor =
                ArgumentCaptor.forClass(ScrmReportTemplateEntity.class);
        verify(templateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTemplateName()).isEqualTo("新模板名");
        // 其他字段保留原值
        assertThat(captor.getValue().getDataSource()).isEqualTo("scrm_customer");
        assertThat(result.getTemplateName()).isEqualTo("新模板名");
    }

    @Test
    @DisplayName("getTemplate: 不存在抛 NOT_FOUND")
    void getTemplate_notFound() {
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表模板不存在");
    }

    
    @Test
    @DisplayName("deleteTemplate: 级联删除关联执行结果")
    void deleteTemplate_cascadeDeleteResults() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmReportResultEntity result1 = buildResultEntity(1L, 10L, "SUCCESS");
        Page<ScrmReportResultEntity> resultPage = new PageImpl<>(List.of(result1));
        when(resultRepository.findByTemplateIdOrderByRunAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(resultPage);

        service.deleteTemplate(10L);

        verify(resultRepository, times(1)).deleteAll(List.of(result1));
        verify(templateRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("deleteTemplate: 无关联结果时仅删除模板")
    void deleteTemplate_noResults() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        Page<ScrmReportResultEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(resultRepository.findByTemplateIdOrderByRunAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(emptyPage);

        service.deleteTemplate(10L);

        verify(resultRepository, never()).deleteAll(any());
        verify(templateRepository, times(1)).delete(entity);
    }

    @Test
    @DisplayName("listTemplates: 按报表类型与公开状态过滤")
    void listTemplates_withFilters() {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        Page<ScrmReportTemplateEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1L);
        when(templateRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        Page<ScrmReportTemplateDto> result = service.listTemplates("CUSTOMER", true, "user1", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReportType()).isEqualTo("CUSTOMER");
        verify(templateRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("executeReport: 模板不存在抛 NOT_FOUND")
    void executeReport_notFound() {
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.executeReport(10L, null, null, "user1"))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表模板不存在");
    }

    @Test
    @DisplayName("executeReport: 成功执行并落库 SUCCESS 状态")
    void executeReport_success() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        Query query = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        // 模拟查询结果: 一行两列 [NEW, 5]
        // 注意: 必须用 Collections.singletonList 包装, 不能用 List.of(new Object[]{...})
        // 因为 List.of 的 varargs 会把 Object[] 展开为多个元素而非单个数组元素
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[]{"NEW", 5L}));
        when(templateRepository.save(any(ScrmReportTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(resultRepository.save(any(ScrmReportResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReportResultDto result = service.executeReport(10L, TIME_START, TIME_END, "user1");

        ArgumentCaptor<ScrmReportResultEntity> resultCaptor =
                ArgumentCaptor.forClass(ScrmReportResultEntity.class);
        verify(resultRepository, times(1)).save(resultCaptor.capture());
        ScrmReportResultEntity saved = resultCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getTemplateId()).isEqualTo(10L);
        assertThat(saved.getRunBy()).isEqualTo("user1");
        assertThat(saved.getRowCount()).isEqualTo(1);
        assertThat(saved.getResultData()).contains("NEW");
        // 模板的 lastRunAt 被刷新
        ArgumentCaptor<ScrmReportTemplateEntity> templateCaptor =
                ArgumentCaptor.forClass(ScrmReportTemplateEntity.class);
        verify(templateRepository, times(1)).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getLastRunAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("executeReport: runBy 为空时填充默认操作人")
    void executeReport_defaultOperator() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        Query query = org.mockito.Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());
        when(templateRepository.save(any(ScrmReportTemplateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(resultRepository.save(any(ScrmReportResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.executeReport(10L, TIME_START, TIME_END, null);

        ArgumentCaptor<ScrmReportResultEntity> captor =
                ArgumentCaptor.forClass(ScrmReportResultEntity.class);
        verify(resultRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getRunBy()).isEqualTo("scrm-system");
    }

    @Test
    @DisplayName("executeReport: 维度列不在白名单时落库 FAILED 状态")
    void executeReport_invalidConfigRecordsFailed() throws ScrmException {
        // 模板的 dimensions 含非法列 (绕过创建校验, 直接构造已持久化实体)
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        entity.setDimensions("[\"password\"]");
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(resultRepository.save(any(ScrmReportResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReportResultDto result = service.executeReport(10L, TIME_START, TIME_END, "user1");

        ArgumentCaptor<ScrmReportResultEntity> captor =
                ArgumentCaptor.forClass(ScrmReportResultEntity.class);
        verify(resultRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getErrorMessage()).contains("维度");
        // 业务异常时不应调用 entityManager
        verify(entityManager, never()).createNativeQuery(anyString());
        // 业务异常时不应更新 template 的 lastRunAt
        verify(templateRepository, never()).save(any(ScrmReportTemplateEntity.class));
        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("executeReport: 模板配置时间范围字段但未提供起止时间时落库 FAILED")
    void executeReport_missingTimeRangeRecordsFailed() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(resultRepository.save(any(ScrmReportResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmReportResultDto result = service.executeReport(10L, null, null, "user1");

        ArgumentCaptor<ScrmReportResultEntity> captor =
                ArgumentCaptor.forClass(ScrmReportResultEntity.class);
        verify(resultRepository, times(1)).save(captor.capture());
        ScrmReportResultEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getErrorMessage()).contains("timeRangeStart");
        // result_data 列非空, 失败记录同样必须给出合法 JSON, 否则落库即报约束冲突
        assertThat(saved.getResultData()).isEqualTo("[]");
        // 校验阶段即失败, 不应进入 SQL 执行
        verify(entityManager, never()).createNativeQuery(anyString());
        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("executeReport: 系统异常时截断超长错误信息后落库 FAILED")
    void executeReport_systemExceptionTruncatesError() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        // 模拟 EntityManager 抛系统异常
        when(entityManager.createNativeQuery(anyString()))
                .thenThrow(new RuntimeException("x".repeat(600)));
        when(resultRepository.save(any(ScrmReportResultEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.executeReport(10L, TIME_START, TIME_END, "user1");

        ArgumentCaptor<ScrmReportResultEntity> captor =
                ArgumentCaptor.forClass(ScrmReportResultEntity.class);
        verify(resultRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        // 错误信息截断到 500 字符
        assertThat(captor.getValue().getErrorMessage()).hasSize(500);
    }

    @Test
    @DisplayName("listResults: 模板不存在抛 NOT_FOUND")
    void listResults_templateNotFound() {
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listResults(10L, PageRequest.of(0, 10)))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表模板不存在");
        verify(resultRepository, never()).findByTemplateIdOrderByRunAtDesc(any(), any());
    }

    @Test
    @DisplayName("listResults: 返回执行结果分页")
    void listResults_success() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmReportResultEntity result = buildResultEntity(1L, 10L, "SUCCESS");
        Page<ScrmReportResultEntity> page = new PageImpl<>(List.of(result), PageRequest.of(0, 10), 1L);
        when(resultRepository.findByTemplateIdOrderByRunAtDesc(eq(10L), any(Pageable.class)))
                .thenReturn(page);

        Page<ScrmReportResultDto> results = service.listResults(10L, PageRequest.of(0, 10));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getResult: 不存在抛 NOT_FOUND")
    void getResult_notFound() {
        when(resultRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getResult(1L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表执行结果不存在");
    }

    
    @Test
    @DisplayName("getLatestResult: 无执行记录返回 null")
    void getLatestResult_empty() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(resultRepository.findFirstByTemplateIdOrderByRunAtDesc(10L))
                .thenReturn(Optional.empty());

        assertThat(service.getLatestResult(10L)).isNull();
    }

    @Test
    @DisplayName("getLatestResult: 返回最近一次执行结果")
    void getLatestResult_success() throws ScrmException {
        ScrmReportTemplateEntity entity = buildTemplateEntity(10L);
        when(templateRepository.findById(10L)).thenReturn(Optional.of(entity));
        ScrmReportResultEntity result = buildResultEntity(1L, 10L, "SUCCESS");
        when(resultRepository.findFirstByTemplateIdOrderByRunAtDesc(10L))
                .thenReturn(Optional.of(result));

        ScrmReportResultDto dto = service.getLatestResult(10L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("getLatestResult: 模板不存在抛 NOT_FOUND")
    void getLatestResult_templateNotFound() {
        when(templateRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestResult(10L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("报表模板不存在");
    }
}
