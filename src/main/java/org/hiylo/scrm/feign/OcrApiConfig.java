/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : OcrApiConfig.java
 * Date : 2026/10/23 14:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCR 服务（wx-console ocr-service）接入配置。
 * <p>
 * 绑定 {@code scrm.ocr.*}: 总开关 + 基础地址 + 内部令牌 + 连接/读取超时。
 * <b>访问令牌只从环境变量/配置中心注入, 不落库、不入日志、不入提交</b>
 * （见 docs/tasks/D04-config.md 配置说明, 由集成步骤统一写入 application.yml 新段,
 * 本任务不改动既有配置段）。部署形态见 docs/tasks/T07/deploy.md（主选 180
 * ocr-service 独立进程, port 8097）。
 * </p>
 * <ul>
 *   <li>enabled - 是否启用 OCR 能力, 默认 false（内部服务未部署前不发请求）</li>
 *   <li>baseUrl - OCR 服务基础地址, 默认 http://192.168.1.180:8097/api/ocr</li>
 *   <li>token - 内部访问令牌（与 OCR 服务端 OCR_TOKEN 一致, 请求头 X-OCR-Token）</li>
 *   <li>connectTimeoutSeconds / readTimeoutSeconds - 超时（默认 10s / 200s,
 *       VL 180s 上限 + 排队余量, 对齐 T07 契约 §7 调用封装要求）</li>
 * </ul>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.ocr")
public class OcrApiConfig {

    /** 是否启用（默认 false, ocr-service 未部署前不发请求） */
    private boolean enabled = false;

    /** OCR 服务基础地址（含 /api/ocr 服务前缀） */
    private String baseUrl = "http://192.168.1.180:8097/api/ocr";

    /** 内部访问令牌（只走配置/环境变量; 请求头 X-OCR-Token） */
    private String token = "";

    /** 连接超时（秒, 默认 10） */
    private int connectTimeoutSeconds = 10;

    /** 读取超时（秒, 默认 200; VL 180s 上限 + 排队余量） */
    private int readTimeoutSeconds = 200;
}