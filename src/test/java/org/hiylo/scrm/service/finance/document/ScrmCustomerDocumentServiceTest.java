/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmCustomerDocumentServiceTest.java
 * Date : 2026/10/25 17:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.document;

import org.hiylo.scrm.entity.ScrmCompanyEntity;
import org.hiylo.scrm.entity.ScrmCustomerDocumentEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmCompanyRepository;
import org.hiylo.scrm.repository.ScrmCustomerDocumentRepository;
import org.hiylo.scrm.storage.ObjectStorage;
import org.hiylo.scrm.storage.StorageException;
import org.hiylo.scrm.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScrmCustomerDocumentService} 单元测试（资料归档域）。
 * <p>
 * 重点锁定两个容易写错、出事代价高的行为：
 * </p>
 * <ol>
 *   <li><b>上传文件名净化</b>：客户端文件名不可信，含 {@code ../} 会写出目录、含换行会污染响应头。</li>
 *   <li><b>删除的失败语义</b>：对象存储删除失败时<b>仍要删掉数据库记录</b>，
 *       否则一条错记录会永远删不掉；留下孤儿对象比留下坏记录安全。</li>
 * </ol>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("资料归档服务：上传净化 / 删除语义 / 齐全度")
class ScrmCustomerDocumentServiceTest {

    /** 资料仓库 */
    @Mock
    private ScrmCustomerDocumentRepository documentRepository;

    /** 主体仓库 */
    @Mock
    private ScrmCompanyRepository companyRepository;

    /** 对象存储 */
    @Mock
    private ObjectStorage objectStorage;
    /** 财税数据范围（测试默认不限制：resolveAccessibleCompanyIds 返回 null） */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;


    /** 被测服务 */
    private ScrmCustomerDocumentService service;

    /** 标准主体 */
    private ScrmCompanyEntity company;

    /**
     * 组装被测服务并准备一个可用主体。
     */
    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("scrm-assets");
        service = new ScrmCustomerDocumentService(
                documentRepository, companyRepository, objectStorage, properties, financeDataScope);

        company = new ScrmCompanyEntity();
        company.setId(100L);
        company.setCompanyName("测试公司");
        when(companyRepository.findById(100L)).thenReturn(Optional.of(company));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== 上传：参数校验 ====================

    @Test
    @DisplayName("空文件被拒绝，且不写对象存储")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> service.upload(100L, "OTHER", "x", empty, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("上传文件不能为空");
        verify(objectStorage, never())
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("缺少资料类型被拒绝")
    void rejectsMissingDocType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.upload(100L, "  ", "x", file, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("资料类型不能为空");
    }

    @Test
    @DisplayName("主体不存在时返回 404 语义，且不写对象存储")
    void rejectsMissingCompany() {
        when(companyRepository.findById(999L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "x".getBytes());

        assertThatThrownBy(() -> service.upload(999L, "OTHER", "x", file, null, null, null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("公司主体不存在");
        verify(objectStorage, never())
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    // ==================== 上传：文件名净化 ====================

    @Test
    @DisplayName("路径穿越文件名被净化，objectKey 不含 ../ 与目录分隔符")
    void sanitizesPathTraversalFileName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd.txt", "text/plain", "secret".getBytes());

        service.upload(100L, "OTHER", null, file, null, null, null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(eq("scrm-assets"), keyCaptor.capture(),
                any(InputStream.class), anyLong(), eq("text/plain"));
        String objectKey = keyCaptor.getValue();

        assertThat(objectKey).startsWith("scrm/document/");
        assertThat(objectKey).doesNotContain("..");
        // 文件名字段（最后一段）里不能再出现目录分隔符
        String fileNamePart = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        assertThat(fileNamePart).doesNotContain("/").doesNotContain("..");
        assertThat(fileNamePart).endsWith("passwd.txt");
    }

    @Test
    @DisplayName("文件名含换行与引号时被移除，避免污染响应头")
    void sanitizesControlCharsInFileName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad\r\nname\".txt", "text/plain", "x".getBytes());

        service.upload(100L, "OTHER", null, file, null, null, null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(eq("scrm-assets"), keyCaptor.capture(),
                any(InputStream.class), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).doesNotContain("\r").doesNotContain("\n")
                .doesNotContain("\"");
    }

    @Test
    @DisplayName("资料名称留空时回落为净化后的文件名")
    void defaultsDocNameToFileName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "营业执照.png", "image/png", "x".getBytes());

        ScrmCustomerDocumentEntity saved = service.upload(
                100L, "BUSINESS_LICENSE", "  ", file, null, null, null);

        assertThat(saved.getDocName()).isEqualTo("营业执照.png");
        assertThat(saved.getCompanyName()).isEqualTo("测试公司");
        assertThat(saved.getStatus()).isEqualTo(ScrmCustomerDocumentEntity.STATUS_ACTIVE);
    }

    // ==================== 删除：失败语义 ====================

    @Test
    @DisplayName("对象存储删除失败时仍删除数据库记录（孤儿对象优于坏记录）")
    void deleteRecordEvenIfStorageFails() throws Exception {
        ScrmCustomerDocumentEntity entity = new ScrmCustomerDocumentEntity();
        entity.setId(7L);
        entity.setObjectKey("scrm/document/202610/x.txt");
        when(documentRepository.findById(7L)).thenReturn(Optional.of(entity));
        doThrow(new StorageException("minio down"))
                .when(objectStorage).delete(anyString(), anyString());

        // 不抛异常，且记录已删
        service.delete(7L);

        verify(documentRepository).delete(entity);
        verify(objectStorage).delete(eq("scrm-assets"), eq("scrm/document/202610/x.txt"));
    }

    @Test
    @DisplayName("资料不存在时删除抛 404 语义")
    void deleteMissingThrows() {
        when(documentRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(8L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("资料不存在");
    }

    // ==================== 齐全度 ====================

    @Test
    @DisplayName("5 项必备中收到 2 项时齐全度为 40%，缺失项准确列出")
    @SuppressWarnings("unchecked")
    void checklistComputesCompleteness() throws Exception {
        // 只有营业执照与开户许可证有有效资料
        when(documentRepository.findByCompanyIdAndDocTypeAndStatus(
                100L, "BUSINESS_LICENSE", ScrmCustomerDocumentEntity.STATUS_ACTIVE))
                .thenReturn(List.of(new ScrmCustomerDocumentEntity()));
        when(documentRepository.findByCompanyIdAndDocTypeAndStatus(
                100L, "BANK_PERMIT", ScrmCustomerDocumentEntity.STATUS_ACTIVE))
                .thenReturn(List.of(new ScrmCustomerDocumentEntity()));
        when(documentRepository.findByCompanyIdAndDocTypeAndStatus(
                100L, "LEGAL_ID_CARD", ScrmCustomerDocumentEntity.STATUS_ACTIVE))
                .thenReturn(List.of());
        when(documentRepository.findByCompanyIdAndDocTypeAndStatus(
                100L, "ARTICLES", ScrmCustomerDocumentEntity.STATUS_ACTIVE))
                .thenReturn(List.of());
        when(documentRepository.findByCompanyIdAndDocTypeAndStatus(
                100L, "LEASE_CONTRACT", ScrmCustomerDocumentEntity.STATUS_ACTIVE))
                .thenReturn(List.of());
        when(documentRepository.findByCompanyIdOrderByUploadedAtDesc(100L))
                .thenReturn(List.of());

        var result = service.checklist(100L);

        assertThat(result.get("requiredTotal")).isEqualTo(5);
        assertThat(result.get("requiredPresent")).isEqualTo(2);
        assertThat(result.get("completeness")).isEqualTo(40);
        assertThat((List<String>) result.get("missingTypes"))
                .containsExactly("LEGAL_ID_CARD", "ARTICLES", "LEASE_CONTRACT");
    }
}
