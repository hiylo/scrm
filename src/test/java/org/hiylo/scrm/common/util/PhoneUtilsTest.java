/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : PhoneUtilsTest.java
 * Date : 2026/10/24 08:30:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PhoneUtils} 单元测试。
 * <p>
 * 号码归一化是三处业务入口（客户创建/销售通话/资料补录）的公共契约，
 * 用例覆盖格式符、国际前缀、固话、非法输入与 11 位判定。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("电话号码归一化 单元测试")
class PhoneUtilsTest {

    @Test
    @DisplayName("格式符：空格/横线/中英文括号/点号统一去除")
    void normalize_stripsFormatting() {
        assertThat(PhoneUtils.normalize("138 0013 8000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("138-0013-8000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("(138)0013-8000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("（138）0013.8000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("  13800138000  ")).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("国际前缀：+ / +86 / 0086 / 裸 86 全部剥离")
    void normalize_stripsCountryPrefix() {
        assertThat(PhoneUtils.normalize("+8613800138000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("+86 138 0013 8000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("008613800138000")).isEqualTo("13800138000");
        assertThat(PhoneUtils.normalize("8613800138000")).isEqualTo("13800138000");
    }

    @Test
    @DisplayName("固话：不误剥 86 前缀，仅去格式符")
    void normalize_keepsLandline() {
        assertThat(PhoneUtils.normalize("010-88886666")).isEqualTo("01088886666");
        assertThat(PhoneUtils.normalize("0755-88886666")).isEqualTo("075588886666");
        // 长度不足 13 的 86 开头号码不应被当作国际前缀剥离
        assertThat(PhoneUtils.normalize("8612345")).isEqualTo("8612345");
    }

    @Test
    @DisplayName("非法输入：空值与非法字符返回 null")
    void normalize_rejectsInvalid() {
        assertThat(PhoneUtils.normalize(null)).isNull();
        assertThat(PhoneUtils.normalize("")).isNull();
        assertThat(PhoneUtils.normalize("   ")).isNull();
        assertThat(PhoneUtils.normalize("abc")).isNull();
        assertThat(PhoneUtils.normalize("138#0013#8000")).isNull();
        assertThat(PhoneUtils.normalize("138abc00138000")).isNull();
    }

    @Test
    @DisplayName("isMobile：仅 11 位纯数字为真")
    void isMobile_detectsLength() {
        assertThat(PhoneUtils.isMobile("13800138000")).isTrue();
        assertThat(PhoneUtils.isMobile("1380013800")).isFalse();
        assertThat(PhoneUtils.isMobile("138001380000")).isFalse();
        // 12 位固话（含区号）不是手机号；01088886666 恰好 11 位，不能用它做反例
        assertThat(PhoneUtils.isMobile("0108888666")).isFalse();
        assertThat(PhoneUtils.isMobile(null)).isFalse();
    }
}
