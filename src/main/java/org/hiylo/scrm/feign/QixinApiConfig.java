/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : QixinApiConfig.java
 * Date : 2026/10/23 09:10:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 启信宝企业信息 API 配置。
 * <p>
 * 绑定 {@code scrm.inspection.qixin.*}: 总开关 + 基础地址 + 访问凭证 + 连接/读取超时。
 * <b>凭证只从环境变量/配置中心注入, 不落库、不入日志</b>（见 D05 报告「API key 配置项清单」）。
 * </p>
 * <ul>
 *   <li>enabled - 是否启用工商 API 巡检, 默认 false（未签约前不发起任何请求）</li>
 *   <li>baseUrl - 开放平台基础地址, 默认 https://api.qixin.com</li>
 *   <li>token - 访问令牌（注册启信慧眼后从凭据页获取, 支持 IP 白名单绑定）</li>
 *   <li>connectTimeoutSeconds / readTimeoutSeconds - 超时（默认 5s / 10s）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.inspection.qixin")
public class QixinApiConfig {

    /** 是否启用（默认 false, 未签约前不发请求） */
    private boolean enabled = false;

    /** 开放平台基础地址 */
    private String baseUrl = "https://api.qixin.com";

    /** 访问令牌（只走配置/环境变量, 不落库） */
    private String token = "";

    /** 连接超时（秒, 默认 5） */
    private int connectTimeoutSeconds = 5;

    /** 读取超时（秒, 默认 10） */
    private int readTimeoutSeconds = 10;
}