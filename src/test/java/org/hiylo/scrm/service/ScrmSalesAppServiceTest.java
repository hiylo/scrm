/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmSalesAppServiceTest.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScrmSalesAppService} 纯函数单元测试。
 * <p>
 * 覆盖号码规范化、SHA-256 计算与 ROM 识别三块无外部依赖的逻辑。
 * 号码规范化是客户匹配与去重的核心，任何偏差都会导致「同一个人被当成两个」，
 * 因此按格式变体逐一断言。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmSalesAppService 纯函数单元测试")
class ScrmSalesAppServiceTest {

    /**
     * 号码规范化：各种常见写法应归一到同一结果。
     */
    @Test
    @DisplayName("normalizePhone: 空格/横线/括号/+86 前缀归一")
    void normalizePhoneShouldUnifyFormats() {
        assertThat(ScrmSalesAppService.normalizePhone("13800138000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("138 0013 8000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("138-0013-8000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("(138)0013-8000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("+8613800138000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("008613800138000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("8613800138000")).isEqualTo("13800138000");
        assertThat(ScrmSalesAppService.normalizePhone("  13800138000  ")).isEqualTo("13800138000");
    }

    /**
     * 号码规范化：固话不应被误伤（不能把 86 开头的固话截断）。
     */
    @Test
    @DisplayName("normalizePhone: 固话前缀不被误处理")
    void normalizePhoneShouldKeepLandline() {
        assertThat(ScrmSalesAppService.normalizePhone("010-88886666")).isEqualTo("01088886666");
        assertThat(ScrmSalesAppService.normalizePhone("0755-88886666")).isEqualTo("075588886666");
        // 86 开头但不是 13 位手机号，应原样保留
        assertThat(ScrmSalesAppService.normalizePhone("8612345")).isEqualTo("8612345");
    }

    /**
     * 号码规范化：非法输入返回 null。
     */
    @Test
    @DisplayName("normalizePhone: 空值与非法字符返回 null")
    void normalizePhoneShouldRejectInvalid() {
        assertThat(ScrmSalesAppService.normalizePhone(null)).isNull();
        assertThat(ScrmSalesAppService.normalizePhone("")).isNull();
        assertThat(ScrmSalesAppService.normalizePhone("   ")).isNull();
        assertThat(ScrmSalesAppService.normalizePhone("abc")).isNull();
        assertThat(ScrmSalesAppService.normalizePhone("138#0013#8000")).isNull();
    }

    /**
     * SHA-256：相同输入稳定，不同输入不同。
     */
    @Test
    @DisplayName("sha256: 结果稳定且长度 64")
    void sha256ShouldBeStable() {
        String hash1 = ScrmSalesAppService.sha256("13800138000");
        String hash2 = ScrmSalesAppService.sha256("13800138000");
        assertThat(hash1).isEqualTo(hash2).hasSize(64);
        assertThat(ScrmSalesAppService.sha256("13800138001")).isNotEqualTo(hash1);
        // 已知向量校验（"abc" 的 SHA-256）
        assertThat(ScrmSalesAppService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    /**
     * ROM 识别：制造商/品牌字符串映射到已知 ROM 键。
     */
    @Test
    @DisplayName("detectRom: 主流厂商映射正确")
    void detectRomShouldMapVendors() {
        assertThat(ScrmSalesAppService.detectRom("Xiaomi-Redmi")).isEqualTo("MIUI");
        assertThat(ScrmSalesAppService.detectRom("Redmi Note 12")).isEqualTo("MIUI");
        assertThat(ScrmSalesAppService.detectRom("HyperOS")).isEqualTo("MIUI");
        assertThat(ScrmSalesAppService.detectRom("OPPO-ColorOS")).isEqualTo("COLOROS");
        assertThat(ScrmSalesAppService.detectRom("realme")).isEqualTo("COLOROS");
        assertThat(ScrmSalesAppService.detectRom("HUAWEI-EMUI")).isEqualTo("EMUI");
        assertThat(ScrmSalesAppService.detectRom("HONOR")).isEqualTo("EMUI");
        assertThat(ScrmSalesAppService.detectRom("vivo-OriginOS")).isEqualTo("VIVO");
        assertThat(ScrmSalesAppService.detectRom("samsung")).isEqualTo("SAMSUNG");
    }

    /**
     * ROM 识别：未知或空值回退到 AOSP。
     */
    @Test
    @DisplayName("detectRom: 未知厂商回退 AOSP")
    void detectRomShouldFallbackToAosp() {
        assertThat(ScrmSalesAppService.detectRom(null)).isEqualTo("AOSP");
        assertThat(ScrmSalesAppService.detectRom("")).isEqualTo("AOSP");
        assertThat(ScrmSalesAppService.detectRom("Google-Pixel")).isEqualTo("AOSP");
        assertThat(ScrmSalesAppService.detectRom("unknown-vendor")).isEqualTo("AOSP");
    }
}
