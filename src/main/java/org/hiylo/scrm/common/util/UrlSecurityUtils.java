/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : UrlSecurityUtils.java
 * Date : 2026-09-19 00:00:00
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common.util;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * 外呼 HTTP 目标 URL 安全校验工具。
 * <p>
 * 用于 Webhook 回调 / 通知推送 / 客户旅程动作等由服务端发起的外呼场景,
 * 阻止用户配置指向内网、回环、链路本地或云 metadata 的 URL, 防止 SSRF
 * 探测内网资源或读取云实例元数据。校验失败抛出 {@link IllegalArgumentException}。
 * </p>
 * <p>
 * 校验策略: 仅允许 http/https 协议; 主机名按字面量命中黑名单 (localhost /
 * 云 metadata 域名 / IPv4 保留段 / IPv6 保留段) 直接拒绝。不执行 DNS 解析,
 * 避免引入解析期 TOCTOU 竞态且不依赖公网 DNS (离线部署亦可正常外呼);
 * 解析结果无法在接入层做连接级 IP 钉扎的前提下, 域名解析后命中内网属残余风险,
 * 由出口网络 ACL 兜底。
 * </p>
 *
 * @author Hsi Chu
 */
public final class UrlSecurityUtils {

    /** 私有构造, 防止实例化 */
    private UrlSecurityUtils() {
    }

    /**
     * 校验目标 URL 仅允许公网 http/https 地址。
     *
     * @param targetUrl 目标 URL
     * @throws IllegalArgumentException URL 为空、协议非法、缺少主机名或命中内网/保留地址
     */
    public static void validatePublicHttpUrl(String targetUrl) {
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("目标 URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(targetUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("目标 URL 非法: " + targetUrl, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("目标 URL 仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("目标 URL 缺少合法主机名");
        }
        if (isBlockedHost(host)) {
            throw new IllegalArgumentException("目标 URL 指向私网或保留地址, 禁止外呼: " + host);
        }
    }

    /**
     * 判断主机名是否命中私网/保留地址黑名单。
     *
     * @param host 主机名
     * @return true=命中黑名单
     */
    public static boolean isBlockedHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String h = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(h) || h.endsWith(".localhost")
                || h.endsWith(".local") || h.endsWith(".internal")
                || "metadata".equals(h) || h.startsWith("metadata.")
                || "metadata.google.internal".equals(h)
                || "metadata.aws.amazon.com".equals(h)
                || "0.0.0.0".equals(h)) {
            return true;
        }
        if (h.indexOf(':') >= 0) {
            return isBlockedIpv6(h);
        }
        return isBlockedIpv4(h);
    }

    /**
     * 判断 IPv4 地址字面量是否命中保留段 (私网/回环/链路本地/组播广播)。
     *
     * @param host IPv4 字面量
     * @return true=命中保留段; 非合法 IPv4 字面量返回 false
     */
    private static boolean isBlockedIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return false;
            }
        }
        int a = octets[0];
        int b = octets[1];
        return a == 0 || a == 10 || a == 127
                || (a == 172 && b >= 16 && b <= 31)
                || (a == 192 && b == 168)
                || (a == 169 && b == 254)
                || a >= 224;
    }

    /**
     * 判断 IPv6 地址字面量是否命中保留段 (::1 / fc00:: ULA / fe80:: 链路本地),
     * IPv4-mapped IPv6 (::ffff:a.b.c.d) 回退到 IPv4 校验。
     *
     * @param host IPv6 字面量
     * @return true=命中保留段; 解析失败返回 false
     */
    private static boolean isBlockedIpv6(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            byte[] bytes = addr.getAddress();
            if (addr instanceof Inet6Address && bytes.length == 16) {
                boolean ipv4Mapped = true;
                for (int i = 0; i < 10; i++) {
                    if (bytes[i] != 0) {
                        ipv4Mapped = false;
                        break;
                    }
                }
                if (ipv4Mapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
                    return isBlockedIpv4((bytes[12] & 0xff) + "." + (bytes[13] & 0xff)
                            + "." + (bytes[14] & 0xff) + "." + (bytes[15] & 0xff));
                }
                boolean loopback = true;
                for (int i = 0; i < 15; i++) {
                    if (bytes[i] != 0) {
                        loopback = false;
                        break;
                    }
                }
                if (loopback && bytes[15] == 1) {
                    return true;
                }
                if ((bytes[0] & 0xfe) == 0xfc) {
                    return true;
                }
                if ((bytes[0] & 0xfe) == 0xfe) {
                    return true;
                }
                return false;
            }
            if (bytes.length == 4) {
                return isBlockedIpv4((bytes[0] & 0xff) + "." + (bytes[1] & 0xff)
                        + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff));
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
