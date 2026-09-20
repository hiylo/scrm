/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : MinioObjectStorageTest.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MinioObjectStorage} 单元测试
 * <p>
 * 通过 Mock {@link MinioClient} 验证参数映射与异常包装, 不发起任何网络请求;
 * 同时覆盖 endpoint 未配置时的 fail-closed 行为。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioObjectStorage 单元测试")
class MinioObjectStorageTest {

    private static final String BUCKET = "test-bucket";

    private static final String KEY = "scrm/conversation/202609/abc_file.jpg";

    @Mock
    private MinioClient minioClient;

    @Mock
    private GetObjectResponse getObjectResponse;

    /** 被测实例 */
    private MinioObjectStorage storage;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("minio");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-access-key");
        properties.setSecretKey("test-secret-key");
        properties.setBucket(BUCKET);
        storage = new MinioObjectStorage(properties);
        ReflectionTestUtils.setField(storage, "client", minioClient);
    }

    @Test
    @DisplayName("providerName 返回 minio")
    void providerName() {
        assertThat(storage.providerName()).isEqualTo("minio");
    }

    @Test
    @DisplayName("endpoint 未配置时客户端不构造, 所有操作抛 StorageException")
    void failClosedWhenEndpointMissing() {
        StorageProperties properties = new StorageProperties();
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucket(BUCKET);
        MinioObjectStorage unconfigured = new MinioObjectStorage(properties);

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
    }

    @Test
    @DisplayName("endpoint 不带 scheme 时可正常构造客户端 (buildClient 自动补 http://)")
    void bareHostEndpointConstructsClient() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("localhost:9000");
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        properties.setBucket(BUCKET);

        MinioObjectStorage bare = new MinioObjectStorage(properties);
        assertThat(ReflectionTestUtils.getField(bare, "client")).isNotNull();
    }

    @Test
    @DisplayName("ensureBucket: bucket 不存在时创建")
    void ensureBucketCreatesWhenMissing() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storage.ensureBucket(BUCKET);

        ArgumentCaptor<BucketExistsArgs> existsCaptor = ArgumentCaptor.forClass(BucketExistsArgs.class);
        ArgumentCaptor<MakeBucketArgs> makeCaptor = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient).bucketExists(existsCaptor.capture());
        verify(minioClient).makeBucket(makeCaptor.capture());
        assertThat(existsCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(makeCaptor.getValue().bucket()).isEqualTo(BUCKET);
    }

    @Test
    @DisplayName("ensureBucket: bucket 已存在时不重复创建")
    void ensureBucketSkipsWhenExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        storage.ensureBucket(BUCKET);

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("ensureBucket 失败包装为 StorageException")
    void ensureBucketWrapsSdkException() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> storage.ensureBucket(BUCKET))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(BUCKET)
                .hasMessageContaining("connection refused");
    }

    @Test
    @DisplayName("upload 正确传递 bucket / object / size / contentType")
    void uploadMapsArguments() throws Exception {
        InputStream content = new ByteArrayInputStream(new byte[]{1, 2, 3});

        storage.upload(BUCKET, KEY, content, 3, "image/jpeg");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs args = captor.getValue();
        assertThat(args.bucket()).isEqualTo(BUCKET);
        assertThat(args.object()).isEqualTo(KEY);
        assertThat(args.objectSize()).isEqualTo(3L);
        assertThat(args.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("upload 不传 contentType 时不设置该属性")
    void uploadWithoutContentType() throws Exception {
        storage.upload(BUCKET, KEY, new ByteArrayInputStream(new byte[0]), 0, "  ");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        // 未显式传 contentType 时由 MinIO SDK 兜底为 application/octet-stream
        assertThat(captor.getValue().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("upload 失败包装为 StorageException")
    void uploadWrapsSdkException() throws Exception {
        doThrow(new RuntimeException("access denied"))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> storage.upload(BUCKET, KEY,
                        new ByteArrayInputStream(new byte[0]), 0, null))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining(KEY)
                .hasMessageContaining("access denied");
    }

    @Test
    @DisplayName("download 返回底层对象流")
    void downloadReturnsContentStream() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);

        storage.download(BUCKET, KEY);

        ArgumentCaptor<GetObjectArgs> captor = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("download 失败包装为 StorageException")
    void downloadWrapsSdkException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("no such key"));

        assertThatThrownBy(() -> storage.download(BUCKET, KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("no such key");
    }

    @Test
    @DisplayName("presignedGetUrl 透传过期秒数并返回 URL")
    void presignedGetUrlReturnsUrl() throws Exception {
        when(minioClient.getPresignedObjectUrl(any()))
                .thenReturn("http://localhost:9000/" + BUCKET + "/" + KEY + "?X-Amz-Expires=60");

        String url = storage.presignedGetUrl(BUCKET, KEY, 60);

        assertThat(url).contains(BUCKET).contains(KEY);
    }

    @Test
    @DisplayName("presignedGetUrl 失败包装为 StorageException")
    void presignedGetUrlWrapsSdkException() throws Exception {
        when(minioClient.getPresignedObjectUrl(any()))
                .thenThrow(new RuntimeException("bad request"));

        assertThatThrownBy(() -> storage.presignedGetUrl(BUCKET, KEY, 60))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("bad request");
    }

    @Test
    @DisplayName("delete 传递 bucket / object")
    void deleteMapsArguments() throws Exception {
        storage.delete(BUCKET, KEY);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().object()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("delete 失败包装为 StorageException")
    void deleteWrapsSdkException() throws Exception {
        doThrow(new RuntimeException("delete failed"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> storage.delete(BUCKET, KEY))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("delete failed");
    }

    @Test
    @DisplayName("exists: statObject 成功即存在 (含 0 字节对象)")
    void existsTrueOnStatSuccess() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenReturn(mock(StatObjectResponse.class));

        assertThat(storage.exists(BUCKET, KEY)).isTrue();
    }

    @Test
    @DisplayName("exists: statObject 抛异常返回 false")
    void existsFalseOnStatFailure() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("no such key"));

        assertThat(storage.exists(BUCKET, KEY)).isFalse();
    }

    @Test
    @DisplayName("endpoint 未配置时抛 StorageException 且不触碰 SDK 客户端")
    void noInteractionWhenUnconfigured() {
        StorageProperties properties = new StorageProperties();
        properties.setAccessKey("ak");
        properties.setSecretKey("sk");
        MinioObjectStorage unconfigured = new MinioObjectStorage(properties);

        assertThatThrownBy(() -> unconfigured.exists(BUCKET, KEY))
                .isInstanceOf(StorageException.class);
        verifyNoInteractions(minioClient);
    }
}
