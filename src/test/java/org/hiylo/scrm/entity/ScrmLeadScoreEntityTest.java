/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmLeadScoreEntityTest.java
 * Date : 2026/10/24 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScrmLeadScoreEntity} 默认值回归测试。
 * <p>
 * <b>背景</b>：{@code is_converted} 等字段在库中是 {@code NOT NULL DEFAULT false}，
 * 但数据库默认值只在「INSERT 未包含该列」时生效。Hibernate 会把 Java 的 {@code null}
 * 显式写成 {@code NULL}，于是首次评分（新建实体）直接违反约束返回 500。
 * </p>
 * <p>
 * 该缺陷只在真实写库时暴露，纯 mock 的单测无法发现，故在此用「实体默认值」做守门。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("线索评分为实体默认值")
class ScrmLeadScoreEntityTest {

    @Test
    @DisplayName("新建实体：三个非空布尔字段默认 FALSE（防止首次评分 NOT NULL 报错）")
    void defaultsAreFalse() {
        ScrmLeadScoreEntity entity = new ScrmLeadScoreEntity();

        assertThat(entity.getIsConverted()).isFalse();
        assertThat(entity.getIsHotLead()).isFalse();
        assertThat(entity.getIsQualified()).isFalse();
    }

    @Test
    @DisplayName("显式赋值后不被默认值覆盖")
    void explicitValuesAreKept() {
        ScrmLeadScoreEntity entity = new ScrmLeadScoreEntity();
        entity.setIsConverted(Boolean.TRUE);
        entity.setIsHotLead(Boolean.TRUE);

        assertThat(entity.getIsConverted()).isTrue();
        assertThat(entity.getIsHotLead()).isTrue();
        assertThat(entity.getIsQualified()).isFalse();
    }
}
