/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SafeLongSerializer.java
 * Date : 2026/10/08 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 大数值 Long 安全序列化器（雪花 ID 跨端精度保护）。
 * <p>
 * 雪花算法生成的 ID（如 366494177074221056）远超 JavaScript 安全整数
 * {@code Number.MAX_SAFE_INTEGER}（9007199254740991），前端 {@code JSON.parse}
 * 后会丢失精度（尾数被改写），导致「列表拿到 id A → 回传 id A' → 后端报
 * 会话/账号不存在」。本序列化器仅在绝对值超过安全整数时输出为字符串，
 * 常规计数（分页 totalElements、messageCount 等）不受影响。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0
 */
public class SafeLongSerializer extends JsonSerializer<Long> {

    /** JavaScript 安全整数上限（2^53 - 1） */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    /**
     * 序列化 Long：超安全整数输出字符串，否则输出数字。
     *
     * @param value  待序列化值
     * @param gen    JSON 生成器
     * @param serializers 序列化上下文
     * @throws IOException 写入失败
     */
    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
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