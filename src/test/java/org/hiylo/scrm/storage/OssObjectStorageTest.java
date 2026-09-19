/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OssObjectStorageTest.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link OssObjectStorage} 单元测试
 * <p>
 * 通过 Mock {@link OSS} 接口验证参数映射、元数据设置、流资源释放与异常包装,
 * 不发起任何网络请求。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OssObjectStorage 单元测试")
class OssObjectStorageTest {

    private static final String BUCKET = "test-bucket";

    private static final String KEY = "scrm/conversation/202609/abc_file.jpg";

    @Mock
    private OSS ossClient;

    @Mock
    private OSSObject ossObject;

    /** 被测实例 */
    private OssObjectStorage storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("oss");
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucket(BUCKET);
        storage = new OssObjectStorage(properties);
        ReflectionTestUtils.setField(storage, "client", ossClient);
    }

    @Test
    @DisplayName("providerName 返回 oss")
    void providerName() {
        assertThat(storage.providerName()).isEqualTo("oss");
    }

    @Test
    @DisplayName("endpoint 未配置时客户端不构造, 所有操作抛 StorageException")
    void failClosedWhenEndpointMissing() {
        StorageProperties properties = new StorageProperties();
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucket(BUCKET);
        OssObjectStorage unconfigured = new OssObjectStorage(properties);

        assertThatThrownBy(() -> unconfigured.ensureBucket(BUCKET))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("scrm.storage.endpoint");
        assertThatThrownBy(() -> unconfigured.upload(BUCKET, KEY,
                        new ByteArrayInputStream(new byte[0]), 0, null))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unconfigured.download(BUCKET, KEY))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unconfigured.presignedGetUrl(BUCKET, KEY, 60))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unconfigured.delete(BUCKET, KEY))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unconfigured.exists(BUCKET, KEY))
                .isInstanceOf(StorageException.class);
        verifyNoInteractions(ossClient);
    }

    @Test
    @DisplayName("ensureBucket: bucket 不存在时创建")
    void ensureBucketCreatesWhenMissing() {
        when(ossClient.doesBucketExist(BUCKET)).thenReturn(false);

        storage.ensureBucket(BUCKET);

        verify(ossClient).createBucket(BUCKET);
    }

    @Test
    @DisplayName("ensureBucket: bucket 已存在时不重复创建")
    void ensureBucketSkipsWhenExists() {
        when(ossClient.doesBucketExist(BUCKET)).thenReturn(true);

        storage.ensureBucket(BUCKET);

        verify(ossClient).doesBucketExist(BUCKET);
        verify(ossClient, never()).createBucket(anyString());
    }

    @Test
    @DisplayName("ensureBucket 失败包装为 StorageException")
    void ensureBucketWrapsSdkException() {
        when(ossClient.doesBucketExist(BUCKET)).thenThrow(new ClientException("timeout"));

        assertThatThrownBy(() -> storage.ensureBucket(BUCKET))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(BUCKET)
                .hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("upload 设置 contentLength 与 contentType")
    void uploadSetsMetadata() {
        storage.upload(BUCKET, KEY, new ByteArrayInputStream(new byte[]{1, 2}), 2, "image/jpeg");

        ArgumentCaptor<ObjectMetadata> captor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(ossClient).putObject(eq(BUCKET), eq(KEY), any(InputStream.class), captor.capture());
        ObjectMetadata metadata = captor.getValue();
        assertThat(metadata.getContentLength()).isEqualTo(2L);
        assertThat(metadata.getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("upload size 为负且不传 contentType 时不设置对应元数据")
    void uploadSkipsOptionalMetadata() {
        storage.upload(BUCKET, KEY, new ByteArrayInputStream(new byte[0]), -1, "  ");

        ArgumentCaptor<ObjectMetadata> captor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(ossClient).putObject(eq(BUCKET), eq(KEY), any(InputStream.class), captor.capture());
        ObjectMetadata metadata = captor.getValue();
        assertThat(metadata.getContentLength()).isEqualTo(0L);
        assertThat(metadata.getContentType()).isNull();
    }

    @Test
    @DisplayName("upload 失败包装为 StorageException")
    void uploadWrapsSdkException() {
        when(ossClient.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new OSSException("AccessDenied"));

        assertThatThrownBy(() -> storage.upload(BUCKET, KEY,
                        new ByteArrayInputStream(new byte[0]), 0, null))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(KEY)
                .hasMessageContaining("AccessDenied");
    }

    @Test
    @DisplayName("download 返回包装流, 关闭时同步释放 OSSObject")
    void downloadClosesUnderlyingObject() throws Exception {
        InputStream raw = new ByteArrayInputStream(new byte[]{9});
        when(ossClient.getObject(BUCKET, KEY)).thenReturn(ossObject);
        when(ossObject.getObjectContent()).thenReturn(raw);

        InputStream content = storage.download(BUCKET, KEY);
        assertThat(content.read()).isEqualTo(9);

        content.close();

        verify(ossObject).close();
    }

    @Test
    @DisplayName("download 失败包装为 StorageException")
    void downloadWrapsSdkException() {
        when(ossClient.getObject(BUCKET, KEY)).thenThrow(new OSSException("NoSuchKey"));

        assertThatThrownBy(() -> storage.download(BUCKET, KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("NoSuchKey");
    }

    @Test
    @DisplayName("presignedGetUrl 按秒换算过期时间并返回 URL")
    void presignedGetUrlConvertsExpiry() throws Exception {
        long before = System.currentTimeMillis();
        when(ossClient.generatePresignedUrl(eq(BUCKET), eq(KEY), any(Date.class)))
                .thenReturn(new URL("https://" + BUCKET + ".oss-cn-hangzhou.aliyuncs.com/" + KEY));

        String url = storage.presignedGetUrl(BUCKET, KEY, 120);
        long after = System.currentTimeMillis();

        assertThat(url).contains(KEY);
        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(ossClient).generatePresignedUrl(eq(BUCKET), eq(KEY), captor.capture());
        long expectedMin = before + 120_000L - 5_000L;
        long expectedMax = after + 120_000L + 5_000L;
        assertThat(captor.getValue().getTime()).isBetween(expectedMin, expectedMax);
    }

    @Test
    @DisplayName("presignedGetUrl 失败包装为 StorageException")
    void presignedGetUrlWrapsSdkException() {
        when(ossClient.generatePresignedUrl(eq(BUCKET), eq(KEY), any(Date.class)))
                .thenThrow(new ClientException("sign error"));

        assertThatThrownBy(() -> storage.presignedGetUrl(BUCKET, KEY, 120))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("sign error");
    }

    @Test
    @DisplayName("delete 传递 bucket / key")
    void deleteMapsArguments() {
        storage.delete(BUCKET, KEY);

        verify(ossClient).deleteObject(BUCKET, KEY);
    }

    @Test
    @DisplayName("delete 失败包装为 StorageException")
    void deleteWrapsSdkException() {
        when(ossClient.deleteObject(BUCKET, KEY)).thenThrow(new OSSException("InternalError"));

        assertThatThrownBy(() -> storage.delete(BUCKET, KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("InternalError");
    }

    @Test
    @DisplayName("exists 透传底层判定")
    void existsDelegates() {
        when(ossClient.doesObjectExist(BUCKET, KEY)).thenReturn(true);
        assertThat(storage.exists(BUCKET, KEY)).isTrue();

        when(ossClient.doesObjectExist(BUCKET, KEY)).thenReturn(false);
        assertThat(storage.exists(BUCKET, KEY)).isFalse();
    }

    @Test
    @DisplayName("exists 查询失败返回 false")
    void existsFalseOnFailure() {
        when(ossClient.doesObjectExist(BUCKET, KEY)).thenThrow(new ClientException("timeout"));

        assertThat(storage.exists(BUCKET, KEY)).isFalse();
    }
}
