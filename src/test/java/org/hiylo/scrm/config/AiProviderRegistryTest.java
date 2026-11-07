/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiProviderRegistryTest.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.repository.ScrmAiProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AI Provider 运行时路由注册表单元测试。
 * <p>校验：按能力（text / image）解析目标 provider，默认标记优先、能力兜底。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AiProviderRegistry：按能力路由")
class AiProviderRegistryTest {

    @Mock
    private ScrmAiProviderRepository repository;

    private AiProviderRegistry registry;

    /** 构造一个 provider 实体 */
    private ScrmAiProviderEntity provider(Long id, String name, String capability,
                                          boolean defaultText, boolean defaultImage) {
        ScrmAiProviderEntity p = new ScrmAiProviderEntity();
        p.setId(id);
        p.setProviderName(name);
        p.setCapability(capability);
        p.setEnabled(true);
        p.setDefaultText(defaultText);
        p.setDefaultImage(defaultImage);
        p.setPriority(100);
        return p;
    }

    @BeforeEach
    void setUp() {
        when(repository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(List.of());
        registry = new AiProviderRegistry(repository);
    }

    /**
     * 重新设置启用列表快照并刷新注册表（模拟配置变更后的 reload）。
     *
     * @param providers 启用中的 provider 列表
     */
    private void apply(List<ScrmAiProviderEntity> providers) {
        when(repository.findByEnabledTrueOrderByPriorityAsc()).thenReturn(providers);
        registry.reload();
    }

    @Test
    @DisplayName("具备 text 能力的 provider 可被解析为文本任务目标")
    void resolveText_findsTextProvider() {
        ScrmAiProviderEntity magpie = provider(1L, "Magpie", "text,image", false, false);
        apply(List.of(magpie));

        Optional<ScrmAiProviderEntity> resolved = registry.resolveTextProvider();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getProviderName()).isEqualTo("Magpie");
    }

    @Test
    @DisplayName("默认文本 provider 优先于普通文本 provider（即使优先级更低）")
    void resolveText_prefersDefault() {
        ScrmAiProviderEntity ollama = provider(1L, "Ollama", "text", false, false);
        ScrmAiProviderEntity magpie = provider(2L, "Magpie", "text,image", true, true);
        ollama.setPriority(10);
        magpie.setPriority(100);
        apply(List.of(ollama, magpie));

        Optional<ScrmAiProviderEntity> resolved = registry.resolveTextProvider();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getProviderName()).isEqualTo("Magpie");
    }

    @Test
    @DisplayName("无 text 能力时文本任务解析为空（不应选中纯视觉 provider）")
    void resolveText_emptyWhenNoTextCapability() {
        ScrmAiProviderEntity vision = provider(1L, "VisionOnly", "image", false, true);
        apply(List.of(vision));

        assertThat(registry.resolveTextProvider()).isEmpty();
    }

    @Test
    @DisplayName("默认多模态 provider 被解析为证照 OCR 目标")
    void resolveImage_findsDefaultImageProvider() {
        ScrmAiProviderEntity magpie = provider(1L, "Magpie", "text,image", true, true);
        apply(List.of(magpie));

        Optional<ScrmAiProviderEntity> resolved = registry.resolveImageProvider();

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getProviderName()).isEqualTo("Magpie");
    }

    @Test
    @DisplayName("无 image 能力时 OCR 解析为空（纯文本 provider 不参与视觉路由）")
    void resolveImage_emptyWhenNoImageCapability() {
        ScrmAiProviderEntity ollama = provider(1L, "Ollama", "text", true, false);
        apply(List.of(ollama));

        assertThat(registry.resolveImageProvider()).isEmpty();
    }

    @Test
    @DisplayName("重新加载快照后路由目标可切换（配置变更即时生效）")
    void reload_switchesRouting() {
        ScrmAiProviderEntity ollama = provider(1L, "Ollama", "text", true, false);
        apply(List.of(ollama));
        assertThat(registry.resolveTextProvider()).get()
                .extracting(ScrmAiProviderEntity::getProviderName).isEqualTo("Ollama");

        // 配置变更：Ollama 被禁用，换成 Magpie
        apply(List.of(provider(2L, "Magpie", "text,image", true, true)));

        assertThat(registry.resolveTextProvider()).get()
                .extracting(ScrmAiProviderEntity::getProviderName).isEqualTo("Magpie");
    }

    @Test
    @DisplayName("hasCapability 支持逗号分隔多能力判断")
    void hasCapability_parsesCommaSeparated() {
        ScrmAiProviderEntity p = provider(1L, "P", "text,image", false, false);

        assertThat(AiProviderRegistry.hasCapability(p, "text")).isTrue();
        assertThat(AiProviderRegistry.hasCapability(p, "image")).isTrue();
        assertThat(AiProviderRegistry.hasCapability(p, "embedding")).isFalse();
        assertThat(AiProviderRegistry.hasCapability(p, "TEXT")).isTrue();
    }
}