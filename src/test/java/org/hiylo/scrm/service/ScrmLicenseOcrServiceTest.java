/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLicenseOcrServiceTest.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hiylo.scrm.dto.ScrmLicenseOcrTaskDto;
import org.hiylo.scrm.entity.ScrmLicenseOcrTaskEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.feign.OcrApiException;
import org.hiylo.scrm.feign.OcrRecognizeClient;
import org.hiylo.scrm.feign.dto.ocr.OcrField;
import org.hiylo.scrm.feign.dto.ocr.OcrRecognizeResponse;
import org.hiylo.scrm.feign.dto.ocr.OcrVerification;
import org.hiylo.scrm.service.CompanyResolver;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerRepository;
import org.hiylo.scrm.repository.ScrmLicenseOcrTaskRepository;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmLicenseOcrService} 单元测试。
 * <p>
 * 锁定证照域三条核心语义:
 * <ul>
 *   <li>识别成功且全部必填 ok / verification.ok=true → SUCCEEDED + 无需复核</li>
 *   <li>低置信度必填字段或 verification.ok=false → PARTIAL + REQUIRED 并记录待复核清单</li>
 *   <li>OCR 调用异常不抛业务异常, 落 FAILED + REQUIRED（弱依赖降级）</li>
 * </ul>
 * 另覆盖复核状态机（REQUIRED → REVIEWED）与敏感字段脱敏展示。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmLicenseOcrService 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmLicenseOcrServiceTest {

    /** 对象存储（影像域 mock） */
    @Mock
    private ObjectStorage objectStorage;

    /** OCR 识别客户端 mock */
    @Mock
    private OcrRecognizeClient ocrRecognizeClient;

    /** 任务数据访问层 mock */
    @Mock
    private ScrmLicenseOcrTaskRepository taskRepository;

    @Mock
    private ScrmCustomerRepository customerRepository;

    @Mock
    private ScrmCompanyRepository companyRepository;
    /** 财税数据范围（测试默认不限制） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 存储配置 */
    private StorageProperties storageProperties;

    /** JSON 序列化器 */
    private ObjectMapper objectMapper;

    /** 被测服务 */
    private ScrmLicenseOcrService service;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties();
        storageProperties.setBucket("scrm-assets");
        objectMapper = new ObjectMapper();
        service = new ScrmLicenseOcrService(
                objectStorage, storageProperties, ocrRecognizeClient, taskRepository,
                new CompanyResolver(customerRepository, companyRepository), objectMapper,
                financeDataScope);
        when(taskRepository.save(any(ScrmLicenseOcrTaskEntity.class)))
                .thenAnswer(invocation -> {
                    ScrmLicenseOcrTaskEntity e = invocation.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(90001L);
                    }
                    return e;
                });
    }

    /** 构造一个必填字段全部 ok 的响应 */
    private OcrRecognizeResponse okResponse() {
        OcrRecognizeResponse response = new OcrRecognizeResponse();
        response.setRequestId("req-test-1");
        response.setOk(true);
        response.setMode("vl");
        response.setEngine("qwen3.5");
        response.setDocType("license");

        OcrField name = new OcrField();
        name.setKey("company_name");
        name.setName("名称");
        name.setValue("××财税服务有限公司");
        name.setConfidence(0.96);
        name.setVerified(true);
        name.setRequired(true);
        name.setStatus("ok");

        OcrField uscc = new OcrField();
        uscc.setKey("uscc");
        uscc.setName("统一社会信用代码");
        uscc.setValue("91440101MA5XXXXXXX");
        uscc.setConfidence(0.93);
        uscc.setVerified(true);
        uscc.setRequired(true);
        uscc.setStatus("ok");
        response.setFields(new java.util.ArrayList<>(List.of(name, uscc)));

        OcrVerification verification = new OcrVerification();
        verification.setOk(true);
        verification.setChecked(2);
        verification.setProblems(new java.util.ArrayList<>());
        response.setVerification(verification);
        return response;
    }

    /** 构造一个含低置信度必填字段 + verification.ok=false 的响应 */
    private OcrRecognizeResponse lowConfidenceResponse() {
        OcrRecognizeResponse response = okResponse();
        // okResponse 使用可变列表, 允许追加字段
        OcrField legal = new OcrField();
        legal.setKey("legal_representative");
        legal.setName("法定代表人");
        legal.setValue("张三");
        legal.setConfidence(0.45);
        legal.setVerified(false);
        legal.setRequired(true);
        legal.setStatus("low_confidence");
        legal.setProblems(List.of("疑似识别不准"));
        response.getFields().add(legal);

        OcrVerification verification = new OcrVerification();
        verification.setOk(false);
        verification.setChecked(3);
        verification.setProblems(List.of("legal_representative：未在图片原文中检出（疑有误）"));
        response.setVerification(verification);
        return response;
    }

    @Test
    @DisplayName("uploadAndRecognize_成功: 全部必填 ok → SUCCEEDED + 无需复核")
    void uploadAndRecognize_success() throws Exception {
        when(ocrRecognizeClient.recognize(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn(okResponse());
        MultipartFile file = new MockMultipartFile("file", "license.jpg",
                "image/jpeg", new byte[]{1, 2, 3});
        ScrmLicenseOcrTaskDto dto = new ScrmLicenseOcrTaskDto();
        dto.setLicenseType("license");

        ScrmLicenseOcrTaskEntity entity = service.uploadAndRecognize(file, dto);

        assertThat(entity.getTaskStatus()).isEqualTo("SUCCEEDED");
        assertThat(entity.getReviewStatus()).isEqualTo("NONE");
        assertThat(entity.getFieldsJson()).contains("company_name");
        assertThat(entity.getObjectKey()).startsWith("scrm/license/");
        assertThat(entity.getRequestId()).isEqualTo("req-test-1");
    }

    @Test
    @DisplayName("uploadAndRecognize_低置信: 必填 status!=ok 或 verification.ok=false → PARTIAL + REQUIRED")
    void uploadAndRecognize_lowConfidence() throws Exception {
        when(ocrRecognizeClient.recognize(any(MultipartFile.class), anyString(), anyString()))
                .thenReturn(lowConfidenceResponse());
        MultipartFile file = new MockMultipartFile("file", "license.jpg",
                "image/jpeg", new byte[]{1, 2, 3});
        ScrmLicenseOcrTaskDto dto = new ScrmLicenseOcrTaskDto();
        dto.setLicenseType("license");

        ScrmLicenseOcrTaskEntity entity = service.uploadAndRecognize(file, dto);

        assertThat(entity.getTaskStatus()).isEqualTo("PARTIAL");
        assertThat(entity.getReviewStatus()).isEqualTo("REQUIRED");
        assertThat(entity.getReviewFieldsJson()).contains("legal_representative");
    }

    @Test
    @DisplayName("uploadAndRecognize_调用异常: 落 FAILED + REQUIRED, 不抛业务异常")
    void uploadAndRecognize_ocrException() throws Exception {
        doThrow(new OcrApiException("OCR 服务未启用 (scrm.ocr.enabled=false)", false))
                .when(ocrRecognizeClient)
                .recognize(any(MultipartFile.class), anyString(), anyString());
        MultipartFile file = new MockMultipartFile("file", "license.jpg",
                "image/jpeg", new byte[]{1, 2, 3});
        ScrmLicenseOcrTaskDto dto = new ScrmLicenseOcrTaskDto();
        dto.setLicenseType("license");

        ScrmLicenseOcrTaskEntity entity = service.uploadAndRecognize(file, dto);

        assertThat(entity.getTaskStatus()).isEqualTo("FAILED");
        assertThat(entity.getReviewStatus()).isEqualTo("REQUIRED");
        assertThat(entity.getErrorMessage()).contains("OCR 服务未启用");
        assertThat(entity.getOverallOk()).isNull();
    }

    @Test
    @DisplayName("review_复核通过: REQUIRED → REVIEWED, 记录复核人与时间")
    void review_passed() {
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90001L);
        entity.setTaskStatus("PARTIAL");
        entity.setReviewStatus("REQUIRED");
        when(taskRepository.findById(any())).thenReturn(Optional.of(entity));

        ScrmLicenseOcrTaskEntity result = service.review(90001L, true, "人工核对通过");

        assertThat(result.getReviewStatus()).isEqualTo("REVIEWED");
        assertThat(result.getReviewRemark()).isEqualTo("人工核对通过");
        assertThat(result.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("review_状态不允许: 非 REQUIRED 任务复核 → ScrmException")
    void review_notPending() {
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90002L);
        entity.setTaskStatus("SUCCEEDED");
        entity.setReviewStatus("NONE");
        when(taskRepository.findById(any())).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.review(90002L, true, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("不处于待复核状态");
    }

    @Test
    @DisplayName("toDto_敏感字段脱敏: 身份证号/账号/姓名打码")
    void toDto_desensitize() throws Exception {
        OcrField idCard = new OcrField();
        idCard.setKey("id_card_no");
        idCard.setName("公民身份号码");
        idCard.setValue("110101199001011234");
        idCard.setConfidence(0.9);
        idCard.setVerified(true);
        idCard.setRequired(true);
        idCard.setStatus("ok");
        OcrField account = new OcrField();
        account.setKey("account_no");
        account.setName("账号");
        account.setValue("6222021234567890123");
        account.setRequired(true);
        account.setStatus("ok");

        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90003L);
        entity.setLicenseType("idcard");
        entity.setTaskStatus("SUCCEEDED");
        entity.setReviewStatus("NONE");
        entity.setFieldsJson(objectMapper.writeValueAsString(List.of(idCard, account)));
        entity.setVerificationJson(null);
        entity.setRawText("公民身份号码 110101199001011234");

        ScrmLicenseOcrTaskDto dto = service.toDto(entity);

        assertThat(dto.getFields()).hasSize(2);
        String maskedId = (String) dto.getFields().get(0).get("value");
        String maskedAccount = (String) dto.getFields().get(1).get("value");
        assertThat(maskedId).doesNotContain("110101199001011234");
        assertThat(maskedId).contains("********");
        assertThat(maskedAccount).doesNotContain("6222021234567890123");
        assertThat(maskedAccount).contains("****");
    }

    @Test
    @DisplayName("assertTaskAccessible: 会计访问他人主体任务 → 403")
    void assertTaskAccessible_foreignCompany_forbidden() {
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90010L);
        entity.setCompanyId(200L);
        when(taskRepository.findById(90010L)).thenReturn(Optional.of(entity));
        // 会计范围只有 100L
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(java.util.List.of(100L));

        assertThatThrownBy(() -> service.assertTaskAccessible(90010L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该证照识别任务");
    }

    @Test
    @DisplayName("assertTaskAccessible: 会计访问自己主体任务放行；管理角色直接放行")
    void assertTaskAccessible_allowsOwnAndManagement() throws Exception {
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90011L);
        entity.setCompanyId(100L);
        when(taskRepository.findById(90011L)).thenReturn(Optional.of(entity));

        // 会计范围含 100L → 放行
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(java.util.List.of(100L));
        service.assertTaskAccessible(90011L);

        // 管理角色 resolve 返回 null（不限制）→ 放行，且不查任务表
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(null);
        service.assertTaskAccessible(99999L);
    }

    @Test
    @DisplayName("assertTaskAccessible: 任务未挂主体时对会计不可见（fail-closed）")
    void assertTaskAccessible_nullCompany_forbidden() {
        ScrmLicenseOcrTaskEntity entity = new ScrmLicenseOcrTaskEntity();
        entity.setId(90012L);
        entity.setCompanyId(null);
        when(taskRepository.findById(90012L)).thenReturn(Optional.of(entity));
        when(financeDataScope.resolveAccessibleCompanyIds()).thenReturn(java.util.List.of(100L));

        assertThatThrownBy(() -> service.assertTaskAccessible(90012L))
                .isInstanceOf(ScrmException.class);
    }
}
