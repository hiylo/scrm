/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : SnowflakeIdGenerator.java
 * Date : 2026/07/29 21:19:51
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * 基于 Snowflake 雪花算法的 Hibernate ID 生成器
 * <p>
 * 通过 {@link SequenceGeneratorHolder} 获取 Spring 容器管理的
 * {@link org.hiylo.scrm.id.sequence.SingletonSequence} 实例，
 * 在实体持久化时自动生成分布式唯一 ID。
 * </p>
 *
 * <p>使用方式：</p>
 * <pre>
 * &#64;Id
 * &#64;GeneratedValue(generator = "snowflake")
 * &#64;GenericGenerator(name = "snowflake", strategy = "org.hiylo.scrm.config.SnowflakeIdGenerator")
 * private Long id;
 * </pre>
 *
 * @since V1.0
 * @author Hsi Chu
 */
public class SnowflakeIdGenerator implements IdentifierGenerator {

    /**
     * 生成 Snowflake 唯一 ID
     *
     * @param session 当前 Hibernate 会话
     * @param object  待持久化的实体对象
     * @return Snowflake 生成的唯一 ID
     * @throws HibernateException 如果序列生成器未初始化或生成失败
     */
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        return SequenceGeneratorHolder.getSequenceGenerator().nextId();
    }
}
