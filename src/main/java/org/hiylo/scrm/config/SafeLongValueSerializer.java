/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SafeLongValueSerializer.java
 * Date : 2026/10/08 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 大数值 Long 安全序列化器（Jackson 3 版，雪花 ID 跨端精度保护）。
 * <p>
 * Spring Boot 4.0.7 的 HTTP 消息序列化使用 Jackson 3（{@code tools.jackson.*}），
 * 本实现注册到 Jackson 3 {@code ObjectMapper}。雪花算法生成的 ID（如
 * 366494177074221056）远超 JavaScript 安全整数（2^53-1 ≈ 9e15），前端
 * {@code JSON.parse} 会丢失精度（尾数被改写），导致「列表拿到的 id → 回传时
 * 已失真 → 后端报会话/账号/客户不存在」。

 * 仅在绝对值超过安全整数时输出为字符串，常规计数（分页 totalElements、
 * messageCount 等）不受影响。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0
 */
public class SafeLongValueSerializer extends ValueSerializer<Long> {

    /** JavaScript 安全整数上限（2^53 - 1） */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    /**
     * 序列化 Long：超安全整数输出字符串，否则输出数字。
     *
     * @param value 待序列化值
     * @param gen   JSON 生成器
     * @param ctxt  序列化上下文
     * @throws JacksonException 写入失败
     */
    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt)
            throws JacksonException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        long longValue = value;
        if (longValue > MAX_SAFE_INTEGER || longValue < -MAX_SAFE_INTEGER) {
            gen.writeString(Long.toString(longValue));
        } else {
            gen.writeNumber(longValue);
        }
    }
}