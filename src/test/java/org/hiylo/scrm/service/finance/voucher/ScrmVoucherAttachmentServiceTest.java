/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmVoucherAttachmentServiceTest.java
 * Date : 2026/11/10 16:40:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.voucher;

import org.hiylo.scrm.entity.ScrmVoucherAttachmentEntity;
import org.hiylo.scrm.entity.ScrmVoucherEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.repository.ScrmVoucherAttachmentRepository;
import org.hiylo.scrm.repository.ScrmVoucherRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * {@link ScrmVoucherAttachmentService} 单元测试（凭证原始单据）。
 * <p>
 * 锁定四组容易写错、出事代价高的语义：
 * </p>
 * <ol>
 *   <li><b>越权</b>：会计拿到别家的凭证 ID 时必须 403，且<b>不能</b>写进对象存储；</li>
 *   <li><b>文件名净化</b>：客户端文件名不可信，含 {@code ../} 会写出目录、含换行会污染响应头；</li>
 *   <li><b>删除的失败语义</b>：对象存储删除失败时<b>仍要删掉数据库记录</b>；</li>
 *   <li><b>单据类型白名单</b>：非法类型回落 OTHER，不让任意字符串进库。</li>
 * </ol>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("凭证原始单据：越权 / 文件名净化 / 删除语义 / 类型白名单")
class ScrmVoucherAttachmentServiceTest {

    /** 单据仓储 */
    @Mock
    private ScrmVoucherAttachmentRepository attachmentRepository;

    /** 凭证仓储 */
    @Mock
    private ScrmVoucherRepository voucherRepository;

    /** 对象存储 */
    @Mock
    private ObjectStorage objectStorage;

    /** 财税数据范围 */
    @Mock
    private org.hiylo.scrm.service.finance.FinanceDataScope financeDataScope;

    /** 被测服务 */
    private ScrmVoucherAttachmentService service;

    /** 可访问主体 */
    private static final Long COMPANY_ID = 100L;

    /** 凭证 ID */
    private static final Long VOUCHER_ID = 9001L;

    /** 组装被测服务与默认数据。 */
    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("scrm-assets");
        service = new ScrmVoucherAttachmentService(attachmentRepository, voucherRepository,
                objectStorage, properties, financeDataScope);

        ScrmVoucherEntity voucher = new ScrmVoucherEntity();
        voucher.setId(VOUCHER_ID);
        voucher.setCompanyId(COMPANY_ID);
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.of(voucher));
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(true);
        when(attachmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** 文本文件构造。 */
    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "image/png", "binary".getBytes());
    }

    // ==================== 越权 ====================

    @Test
    @DisplayName("会计无权重访问该主体时上传被拒，且不写对象存储")
    void rejectsUploadForInaccessibleCompany() {
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.upload(VOUCHER_ID, file("a.png"), "INVOICE", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该主体");
        verify(objectStorage, never())
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("会计无权重时查询单据列表被拒")
    void rejectsListForInaccessibleCompany() {
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.list(VOUCHER_ID))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该主体");
        verify(attachmentRepository, never()).findByVoucherIdOrderByUploadedAtDesc(anyLong());
    }

    @Test
    @DisplayName("会计无权重时删除单据被拒，且不删记录")
    void rejectsDeleteForInaccessibleCompany() {
        ScrmVoucherAttachmentEntity entity = new ScrmVoucherAttachmentEntity();
        entity.setId(7L);
        entity.setVoucherId(VOUCHER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setObjectKey("scrm/voucher/202611/x.png");
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(financeDataScope.canAccessCompany(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("无权访问该主体");
        verify(attachmentRepository, never()).delete(any());
    }

    // ==================== 参数校验 ====================

    @Test
    @DisplayName("凭证不存在时返回 404 语义，且不写对象存储")
    void rejectsMissingVoucher() {
        when(voucherRepository.findById(8888L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upload(8888L, file("a.png"), "INVOICE", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("凭证不存在");
        verify(objectStorage, never())
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("空文件被拒，且不写对象存储")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service.upload(VOUCHER_ID, empty, "INVOICE", null))
                .isInstanceOf(ScrmException.class)
                .hasMessageContaining("上传文件不能为空");
        verify(objectStorage, never())
                .upload(anyString(), anyString(), any(), anyLong(), anyString());
    }

    // ==================== 文件名净化 / 类型白名单 ====================

    @Test
    @DisplayName("路径穿越文件名被净化，objectKey 不含 ../ 与目录分隔符")
    void sanitizesPathTraversalFileName() throws Exception {
        service.upload(VOUCHER_ID, file("../../etc/passwd.png"), "INVOICE", null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(eq("scrm-assets"), keyCaptor.capture(),
                any(InputStream.class), anyLong(), eq("image/png"));
        String objectKey = keyCaptor.getValue();

        assertThat(objectKey).startsWith("scrm/voucher/");
        assertThat(objectKey).doesNotContain("..");
        String fileNamePart = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        assertThat(fileNamePart).doesNotContain("/").doesNotContain("..");
        assertThat(fileNamePart).endsWith("passwd.png");
    }

    @Test
    @DisplayName("文件名含换行与引号时被移除，避免污染响应头")
    void sanitizesControlCharsInFileName() throws Exception {
        service.upload(VOUCHER_ID, file("bad\r\nname\".png"), "INVOICE", null);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(eq("scrm-assets"), keyCaptor.capture(),
                any(InputStream.class), anyLong(), anyString());
        assertThat(keyCaptor.getValue()).doesNotContain("\r").doesNotContain("\n")
                .doesNotContain("\"");
    }

    @Test
    @DisplayName("非法单据类型回落 OTHER，合法类型(含小写)被规范化")
    void normalizesDocTypeByWhitelist() throws Exception {
        ScrmVoucherAttachmentEntity fallback = service.upload(
                VOUCHER_ID, file("a.png"), "DROP TABLE", null);
        assertThat(fallback.getDocType()).isEqualTo(ScrmVoucherAttachmentEntity.DOC_OTHER);

        ScrmVoucherAttachmentEntity normalized = service.upload(
                VOUCHER_ID, file("b.png"), " invoice ", null);
        assertThat(normalized.getDocType()).isEqualTo(ScrmVoucherAttachmentEntity.DOC_INVOICE);

        ScrmVoucherAttachmentEntity blank = service.upload(VOUCHER_ID, file("c.png"), null, null);
        assertThat(blank.getDocType()).isEqualTo(ScrmVoucherAttachmentEntity.DOC_OTHER);
    }

    @Test
    @DisplayName("上传成功时写入正确的凭证归属与冗余主体")
    void storesVoucherAndCompanyOwnership() throws Exception {
        ScrmVoucherAttachmentEntity saved = service.upload(
                VOUCHER_ID, file("发票.png"), "INVOICE", "11月进项");

        assertThat(saved.getVoucherId()).isEqualTo(VOUCHER_ID);
        assertThat(saved.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getFileName()).isEqualTo("发票.png");
        assertThat(saved.getDocType()).isEqualTo(ScrmVoucherAttachmentEntity.DOC_INVOICE);
        assertThat(saved.getRemark()).isEqualTo("11月进项");
        assertThat(saved.getObjectKey()).startsWith("scrm/voucher/");
    }

    // ==================== 删除语义 ====================

    @Test
    @DisplayName("对象存储删除失败时仍删除数据库记录（孤儿对象优于坏记录）")
    void deleteRecordEvenIfStorageFails() {
        ScrmVoucherAttachmentEntity entity = new ScrmVoucherAttachmentEntity();
        entity.setId(7L);
        entity.setVoucherId(VOUCHER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setObjectKey("scrm/voucher/202611/x.png");
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(entity));
        doThrow(new StorageException("minio down")).when(objectStorage)
                .delete(anyString(), anyString());

        service.delete(7L);

        // 记录必须已经删掉，否则一条错记录永远删不掉
        verify(attachmentRepository).delete(entity);
    }

    @Test
    @DisplayName("级联清理返回删除行数，并在对象删除失败时不抛异常")
    void cascadeDeleteToleratesStorageFailure() {
        List<String> keys = new ArrayList<>(List.of("scrm/voucher/202611/a.png",
                "scrm/voucher/202611/b.pdf"));
        when(attachmentRepository.findObjectKeysByVoucherId(VOUCHER_ID)).thenReturn(keys);
        when(attachmentRepository.deleteByVoucherId(VOUCHER_ID)).thenReturn(2);
        doThrow(new StorageException("minio down")).when(objectStorage)
                .delete(anyString(), anyString());

        int deleted = service.deleteByVoucherId(VOUCHER_ID);

        assertThat(deleted).isEqualTo(2);
        verify(attachmentRepository).deleteByVoucherId(VOUCHER_ID);
    }

    // ==================== 单据数统计 ====================

    @Test
    @DisplayName("批量统计单据数返回 voucherId(字符串) → 数量，空入参不查库")
    void countsGroupedByVoucherIds() {
        assertThat(service.countByVoucherIds(List.of())).isEmpty();
        verify(attachmentRepository, never()).countGroupByVoucherIds(any());

        when(attachmentRepository.countGroupByVoucherIds(List.of(1L, 2L))).thenReturn(List.of(
                new Object[]{1L, 3L},
                new Object[]{2L, 1L}));
        Map<String, Integer> counts = service.countByVoucherIds(List.of(1L, 2L));

        assertThat(counts).containsEntry("1", 3).containsEntry("2", 1).hasSize(2);
    }

    @Test
    @DisplayName("下载预签名 URL 透传存储层结果")
    void presignDelegatesToStorage() {
        ScrmVoucherAttachmentEntity entity = new ScrmVoucherAttachmentEntity();
        entity.setId(7L);
        entity.setVoucherId(VOUCHER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setObjectKey("scrm/voucher/202611/x.png");
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(entity));
        when(objectStorage.presignedGetUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("http://minio/presigned");

        assertThat(service.presignDownload(7L)).isEqualTo("http://minio/presigned");
    }
}
