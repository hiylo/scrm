/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : StoragePropertiesTest.java
 * Date : 2026/09/18 11:24:39
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StorageProperties} 单元测试
 * <p>
 * 校验配置默认值与 endpoint 判定逻辑, 确保未注入凭证时存储走 fail-closed 路径。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("StorageProperties 单元测试")
class StoragePropertiesTest {

    @Test
    @DisplayName("默认 provider 为 minio, bucket 与预签名有效期取内置默认值")
    void defaultsApplied() {
        StorageProperties properties = new StorageProperties();

        assertThat(properties.getProvider()).isEqualTo("minio");
        assertThat(properties.getBucket()).isEqualTo("scrm-assets");
        assertThat(properties.isSecure()).isFalse();
        assertThat(properties.getPresignExpirySeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("endpoint 未配置 / 空串 / 纯空白 均判定为未配置")
    void endpointNotConfiguredVariants() {
        StorageProperties properties = new StorageProperties();

        assertThat(properties.isEndpointConfigured()).isFalse();

        properties.setEndpoint("");
        assertThat(properties.isEndpointConfigured()).isFalse();

        properties.setEndpoint("   ");
        assertThat(properties.isEndpointConfigured()).isFalse();
    }

    @Test
    @DisplayName("endpoint 非空时判定为已配置")
    void endpointConfigured() {
        StorageProperties properties = new StorageProperties();
        properties.setEndpoint("http://localhost:9000");

        assertThat(properties.isEndpointConfigured()).isTrue();
    }

    @Test
    @DisplayName("provider 可切换为 oss")
    void providerSwitchableToOss() {
        StorageProperties properties = new StorageProperties();
        properties.setProvider("oss");

        assertThat(properties.getProvider()).isEqualTo("oss");
    }
}
