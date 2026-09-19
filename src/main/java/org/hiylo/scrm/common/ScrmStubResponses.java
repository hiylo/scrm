/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmStubResponses.java
 * Date : 2026/08/05 08:55:12
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SCRM 占位实现 (stub) 统一响应工具。
 * <p>
 * 为尚未对接真实实现的 Controller 端点生成 HTTP 501 NOT_IMPLEMENTED 响应,
 * 避免调用方误以为功能可用。响应体统一格式:
 * <pre>
 * {"code":501, "message":"该功能尚未启用 (stub implementation)", "feature":"&lt;功能名&gt;"}
 * </pre>
 * </p>
 * <p>
 * 使用方式: 在 Controller 端点方法开头短路返回
 * <pre>
 * return ScrmStubResponses.notImplemented("工作流触发执行");
 * </pre>
 * 后续对接真实实现后, 删除该短路返回并恢复原有逻辑即可。
 * </p>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public final class ScrmStubResponses {

    /** 占位实现的统一提示消息 */
    private static final String STUB_MESSAGE = "该功能尚未启用 (stub implementation)";

    private ScrmStubResponses() {
    }

    /**
     * 构建 501 NOT_IMPLEMENTED 响应, 标注功能未启用。
     *
     * @param feature 功能名称 (用于标识具体是哪个占位功能)
     * @return HTTP 501 响应实体, 响应体含 code / message / feature 字段
     */
    public static ResponseEntity<Map<String, Object>> notImplemented(String feature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 501);
        body.put("message", STUB_MESSAGE);
        body.put("feature", feature);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
