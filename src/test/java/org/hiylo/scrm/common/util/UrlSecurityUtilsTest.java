/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : UrlSecurityUtilsTest.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UrlSecurityUtils} 单元测试。
 * <p>
 * 覆盖 SSRF 防护关键路径: 协议白名单、回环/私网/链路本地/云 metadata 地址拒绝、
 * 公网 http/https 放行。域名解析依赖公网 DNS, 不在此覆盖 (真实失败路径会将
 * "无法解析" 视为拒绝, 见 resolve rejection 用例)。
 * </p>
 *
 * @author Hsi Chu
 */
@DisplayName("UrlSecurityUtils SSRF 校验单元测试")
class UrlSecurityUtilsTest {

    @Test
    @DisplayName("空 / 空白 URL 拒绝")
    void nullOrBlankUrl_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非 http/https 协议拒绝 (含 file/gopher ftp)")
    void nonHttpScheme_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http/https");
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("gopher://localhost"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("缺少主机名的 URL 拒绝")
    void missingHost_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IP 字面量私网段拒绝 (127/8 10/8 172.16/12 192.168/16 169.254/16)")
    void privateIpLiterals_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://127.0.0.1/x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("保留地址");
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://172.16.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://192.168.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
        // 云实例元数据服务
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
        // 0.0.0.0
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://0.0.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("localhost 及云 metadata 域名拒绝")
    void reservedHostnames_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://localhost"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://metadata.google.internal"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://metadata"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://myhost.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IPv6 回环 / ULA 拒绝")
    void ipv6Reserved_rejected() {
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://[::1]/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UrlSecurityUtils.validatePublicHttpUrl("http://[fc00::1]/x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("公网 https URL 放行")
    void publicHttpsUrl_allowed() {
        UrlSecurityUtils.validatePublicHttpUrl("https://api.example.com/webhook");
        UrlSecurityUtils.validatePublicHttpUrl("https://www.example.com/path?q=1");
    }

    @Test
    @DisplayName("公网 IP 字面量放行")
    void publicIpLiteral_allowed() {
        UrlSecurityUtils.validatePublicHttpUrl("http://8.8.8.8/ping");
        UrlSecurityUtils.validatePublicHttpUrl("http://198.51.100.10/endpoint");
    }

    @Test
    @DisplayName("isBlockedHost 关键命中")
    void isBlockedHost_matches() {
        assertThat(UrlSecurityUtils.isBlockedHost("localhost")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("metadata.google.internal")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("127.0.0.1")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("192.168.0.1")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("169.254.169.254")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("10.1.2.3")).isTrue();
        assertThat(UrlSecurityUtils.isBlockedHost("8.8.8.8")).isFalse();
    }
}