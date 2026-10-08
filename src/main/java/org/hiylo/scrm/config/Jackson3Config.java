/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : Jackson3Config.java
 * Date : 2026/10/08 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 3 安全 Long 序列化配置（Spring Boot 4 HTTP 序列化）。
 * <p>
 * Spring Boot 4.0.7 的 {@code JacksonAutoConfiguration} 用 Jackson 3
 * （{@code tools.jackson.*}）驱动 HTTP 消息转换器。官方扩展点是
 * {@link JsonMapperBuilderCustomizer}：容器自动装配时收集所有该类型 Bean，
 * 逐个对属性默认的 {@code JsonMapper.Builder} 调用 {@code customize}。
 * </p>
 * <p>
 * 本配置注册一个 {@code JsonMapperBuilderCustomizer}：给全局 Jackson 3 mapper
 * 追加 {@link SafeLongValueSerializer}，让雪花 Long ID 在超出 JavaScript
 * 安全整数（2^53-1 ≈ 9e15）时输出为字符串，防止前端 {@code JSON.parse}
 * 精度丢失后回传错误 ID（会话/账号/客户不存在）。
 * </p>
 *
 * @author Hsi Chu
 * @since 1.0
 */
@Configuration
public class Jackson3Config {

    /**
     * 注册 Jackson 3 MapperBuilder 定制器（追加安全 Long 序列化器）。
     *
     * @return JsonMapperBuilderCustomizer
     */
    @Bean
    public JsonMapperBuilderCustomizer safeLongSerializationCustomizer() {
        return builder -> builder.addModule(new tools.jackson.databind.module.SimpleModule("scrm-safe-long")
                .addSerializer(Long.class, new SafeLongValueSerializer())
                .addSerializer(Long.TYPE, new SafeLongValueSerializer()));
    }
}