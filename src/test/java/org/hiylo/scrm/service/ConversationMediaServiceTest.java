/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ConversationMediaServiceTest.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ConversationMediaService} 单元测试
 * <p>
 * 验证 objectKey 命名规范、预签名过期时间换算、对象存储 key 前缀校验,
 * 以及 {@link StorageException} 到业务错误码的映射。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ConversationMediaService 单元测试")
class ConversationMediaServiceTest {

    private static final String BUCKET = "test-bucket";

    private static final String VALID_KEY = "scrm/conversation/202609/abc_file.jpg";

    @Mock
    private ObjectStorage objectStorage;

    /** 存储配置 */
    private StorageProperties properties;

    /** 被测服务 */
    private ConversationMediaService service;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        properties.setProvider("minio");
        properties.setEndpoint("http://localhost:9000");
        properties.setBucket(BUCKET);
        service = new ConversationMediaService(objectStorage, properties);
    }

    @Test
    @DisplayName("getBucket 与 getProviderName 透传配置与实现标识")
    void exposesProviderAndBucket() {
        when(objectStorage.providerName()).thenReturn("minio");

        assertThat(service.getBucket()).isEqualTo(BUCKET);
        assertThat(service.getProviderName()).isEqualTo("minio");
    }

    @Test
    @DisplayName("uploadMedia 按规范生成 objectKey 并透传存储参数")
    void uploadMediaBuildsObjectKey() {
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        String objectKey = service.uploadMedia(content, "hello.png", 3, "image/png");

        assertThat(objectKey).matches("scrm/conversation/" + currentMonth + "/[0-9a-f]{32}_hello\\.png");

        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(objectStorage).upload(eq(BUCKET), eq(objectKey), streamCaptor.capture(),
                eq(3L), eq("image/png"));
        assertThat(streamCaptor.getValue()).isSameAs(content);
    }

    @Test
    @DisplayName("uploadMedia 文件名为空时使用 media 兜底")
    void uploadMediaFallsBackToMediaName() {
        String objectKey = service.uploadMedia(new ByteArrayInputStream(new byte[0]), "  ", 0, null);

        assertThat(objectKey).matches("scrm/conversation/\\d{6}/[0-9a-f]{32}_media");
    }

    @Test
    @DisplayName("uploadMedia 文件名含路径分隔符时仅取 basename, 不产生额外层级")
    void uploadMediaStripsPathSeparators() {
        String objectKey = service.uploadMedia(new ByteArrayInputStream(new byte[0]),
                "scrm/conversation/202609/zzz_客户报表.pdf", 0, null);

        String[] segments = objectKey.split("/");
        assertThat(segments).hasSize(4);
        assertThat(segments[3]).doesNotContain("/");
        assertThat(segments[3]).endsWith("_客户报表.pdf");
    }

    @Test
    @DisplayName("uploadMedia 文件名含路径穿越时无法逃逸命名空间")
    void uploadMediaBlocksPathTraversal() {
        String objectKey = service.uploadMedia(new ByteArrayInputStream(new byte[0]),
                "../../etc/passwd", 0, null);

        assertThat(objectKey).startsWith("scrm/conversation/");
        assertThat(objectKey).doesNotContain("..");
        assertThat(objectKey).endsWith("_passwd");
        assertThat(objectKey.split("/")).hasSize(4);
    }

    @Test
    @DisplayName("uploadMedia 文件名含引号或换行等注入字符时被清除")
    void uploadMediaStripsInjectionChars() {
        String objectKey = service.uploadMedia(new ByteArrayInputStream(new byte[0]),
                "a\"b\r\nc.png", 0, null);

        assertThat(objectKey).doesNotContain("\"").doesNotContain("\r").doesNotContain("\n");
        assertThat(objectKey).endsWith("_abc.png");
    }

    @Test
    @DisplayName("uploadMedia 文件名为纯路径穿越或点号时使用 media 兜底")
    void uploadMediaFallsBackForDotTraversalName() {
        assertThat(service.uploadMedia(new ByteArrayInputStream(new byte[0]), "..", 0, null))
                .matches("scrm/conversation/\\d{6}/[0-9a-f]{32}_media");
        assertThat(service.uploadMedia(new ByteArrayInputStream(new byte[0]), "/", 0, null))
                .matches("scrm/conversation/\\d{6}/[0-9a-f]{32}_media");
    }

    @Test
    @DisplayName("uploadMedia 输入流为空抛 400")
    void uploadMediaRejectsNullStream() {
        assertThatThrownBy(() -> service.uploadMedia(null, "a.png", 0, null))
                .isInstanceOf(ScrmException.class)
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("uploadMedia 存储失败映射为 SCRM_MEDIA_UPLOAD_FAILED")
    void uploadMediaMapsStorageException() {
        doThrow(new StorageException("boom"))
                .when(objectStorage).upload(anyString(), anyString(), any(InputStream.class), anyLong(), any());

        assertThatThrownBy(() -> service.uploadMedia(new ByteArrayInputStream(new byte[0]), "a.png", 0, null))
                .isInstanceOf(ScrmException.class)
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.SCRM_MEDIA_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("uploadMedia 安全 MIME (image/png) 原样透传")
    void uploadMediaKeepsSafeContentType() {
        service.uploadMedia(new ByteArrayInputStream(new byte[0]), "a.png", 0, "image/png");

        verify(objectStorage).upload(eq(BUCKET), anyString(), any(InputStream.class), eq(0L), eq("image/png"));
    }

    @Test
    @DisplayName("uploadMedia 危险 MIME (text/html) 强制降级为 octet-stream 防 XSS")
    void uploadMediaDowngradesUnsafeContentType() {
        service.uploadMedia(new ByteArrayInputStream(new byte[0]), "a.html", 0, "text/html");

        verify(objectStorage).upload(eq(BUCKET), anyString(), any(InputStream.class), eq(0L),
                eq("application/octet-stream"));
    }

    @Test
    @DisplayName("uploadMedia 带参数的 MIME 仅取基础类型判断")
    void uploadMediaStripsMimeParameters() {
        service.uploadMedia(new ByteArrayInputStream(new byte[0]), "a.pdf", 0,
                "application/pdf; charset=utf-8");

        verify(objectStorage).upload(eq(BUCKET), anyString(), any(InputStream.class), eq(0L),
                eq("application/pdf"));
    }

    @Test
    @DisplayName("uploadMedia 空白 MIME 使用 octet-stream 兜底")
    void uploadMediaFallsBackContentType() {
        service.uploadMedia(new ByteArrayInputStream(new byte[0]), "a.bin", 0, null);

        verify(objectStorage).upload(eq(BUCKET), anyString(), any(InputStream.class), eq(0L),
                eq("application/octet-stream"));
    }

    @Test
    @DisplayName("downloadMedia 委托对象存储")
    void downloadMediaDelegates() {
        InputStream content = new ByteArrayInputStream(new byte[0]);
        when(objectStorage.download(BUCKET, VALID_KEY)).thenReturn(content);

        assertThat(service.downloadMedia(VALID_KEY)).isSameAs(content);
    }

    @Test
    @DisplayName("downloadMedia 非法 key 抛 400 且不触碰存储")
    void downloadMediaRejectsInvalidKey() {
        assertThatThrownBy(() -> service.downloadMedia(""))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        assertThatThrownBy(() -> service.downloadMedia("../other-bucket/secret"))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("downloadMedia 存储失败映射为 SCRM_MEDIA_DOWNLOAD_FAILED")
    void downloadMediaMapsStorageException() {
        when(objectStorage.download(BUCKET, VALID_KEY)).thenThrow(new StorageException("boom"));

        assertThatThrownBy(() -> service.downloadMedia(VALID_KEY))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.SCRM_MEDIA_DOWNLOAD_FAILED);
    }

    @Test
    @DisplayName("getMediaUrl 默认 1 小时换算为 3600 秒")
    void getMediaUrlDefaultExpiry() {
        when(objectStorage.presignedGetUrl(BUCKET, VALID_KEY, 3600))
                .thenReturn("https://signed.example/" + VALID_KEY);

        assertThat(service.getMediaUrl(VALID_KEY)).isEqualTo("https://signed.example/" + VALID_KEY);
        verify(objectStorage).presignedGetUrl(BUCKET, VALID_KEY, 3600);
    }

    @Test
    @DisplayName("getMediaUrl 按分钟换算为秒")
    void getMediaUrlConvertsMinutesToSeconds() {
        when(objectStorage.presignedGetUrl(BUCKET, VALID_KEY, 300))
                .thenReturn("https://signed.example");

        assertThat(service.getMediaUrl(VALID_KEY, 5)).isEqualTo("https://signed.example");
        verify(objectStorage).presignedGetUrl(BUCKET, VALID_KEY, 300);
    }

    @Test
    @DisplayName("getMediaUrl 过期时间非正抛 400")
    void getMediaUrlRejectsInvalidExpiry() {
        assertThatThrownBy(() -> service.getMediaUrl(VALID_KEY, 0))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        assertThatThrownBy(() -> service.getMediaUrl(VALID_KEY, -1))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        // 超过 24 小时上限 (1440 分钟) 拒绝, 防止生成永久有效的预签名 URL
        assertThatThrownBy(() -> service.getMediaUrl(VALID_KEY, 1441))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        assertThatThrownBy(() -> service.getMediaUrl(VALID_KEY, Integer.MAX_VALUE))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        verifyNoInteractions(objectStorage);
    }

    @Test
    @DisplayName("getMediaUrl 24 小时边界值 (1440 分钟) 被允许")
    void getMediaUrlAllowsMaxExpiry() {
        when(objectStorage.presignedGetUrl(BUCKET, VALID_KEY, 1440 * 60))
                .thenReturn("https://signed.example");

        assertThat(service.getMediaUrl(VALID_KEY, 1440)).isEqualTo("https://signed.example");
        verify(objectStorage).presignedGetUrl(BUCKET, VALID_KEY, 1440 * 60);
    }

    @Test
    @DisplayName("getMediaUrl 存储失败映射为 SCRM_MEDIA_PRESIGN_FAILED")
    void getMediaUrlMapsStorageException() {
        when(objectStorage.presignedGetUrl(anyString(), anyString(), anyInt()))
                .thenThrow(new StorageException("boom"));

        assertThatThrownBy(() -> service.getMediaUrl(VALID_KEY, 10))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.SCRM_MEDIA_PRESIGN_FAILED);
    }

    @Test
    @DisplayName("deleteMedia 委托对象存储")
    void deleteMediaDelegates() {
        service.deleteMedia(VALID_KEY);

        verify(objectStorage).delete(BUCKET, VALID_KEY);
    }

    @Test
    @DisplayName("deleteMedia 空 key 与非法前缀均静默忽略")
    void deleteMediaIgnoresInvalidKey() {
        service.deleteMedia(null);
        service.deleteMedia("");
        service.deleteMedia("../other-bucket/secret");

        verify(objectStorage, never()).delete(anyString(), anyString());
    }

    @Test
    @DisplayName("deleteMedia 存储失败映射为 SCRM_MEDIA_DELETE_FAILED")
    void deleteMediaMapsStorageException() {
        doThrow(new StorageException("boom"))
                .when(objectStorage).delete(BUCKET, VALID_KEY);

        assertThatThrownBy(() -> service.deleteMedia(VALID_KEY))
                .extracting("code")
                .isEqualTo(ScrmExceptionConstants.SCRM_MEDIA_DELETE_FAILED);
    }
}
