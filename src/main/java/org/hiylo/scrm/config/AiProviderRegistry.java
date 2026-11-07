/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : AiProviderRegistry.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.repository.ScrmAiProviderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * AI Provider 运行时路由注册表。
 * <p>
 * 启动时加载全部启用中的 provider 到内存，文本任务与证照 OCR 按能力
 * （text / image）路由到对应默认 provider。管理端保存/删除/启停后调用
 * {@link #reload()} 即时刷新，无需重启进程。</p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class AiProviderRegistry {

    /** Provider 数据访问 */
    private final ScrmAiProviderRepository repository;

    /** 启用中的 provider 快照（按优先级升序） */
    private volatile List<ScrmAiProviderEntity> snapshot;

    /**
     * 构造注册表并加载初始快照。
     *
     * @param repository Provider 数据访问
     */
    public AiProviderRegistry(ScrmAiProviderRepository repository) {
        this.repository = repository;
        reload();
    }

    /**
     * 重新加载 provider 快照（管理端配置变更后调用）。
     */
    public synchronized void reload() {
        this.snapshot = repository.findByEnabledTrueOrderByPriorityAsc();
        log.info("AI Provider 路由快照已刷新, 启用 provider {} 个",
                this.snapshot == null ? 0 : this.snapshot.size());
    }

    /**
     * 解析文本任务（对话/客服/文案）目标 provider：
     * 优先默认文本 provider，其次第一个具备 text 能力的启用 provider。
     *
     * @return 目标 provider，无可用则返回 empty
     */
    public Optional<ScrmAiProviderEntity> resolveTextProvider() {
        List<ScrmAiProviderEntity> list = current();
        ScrmAiProviderEntity firstText = null;
        for (ScrmAiProviderEntity p : list) {
            if (hasCapability(p, "text")) {
                if (Boolean.TRUE.equals(p.getDefaultText())) {
                    return Optional.of(p);
                }
                if (firstText == null) {
                    firstText = p;
                }
            }
        }
        return Optional.ofNullable(firstText);
    }

    /**
     * 解析证照 OCR（多模态）目标 provider：
     * 优先默认多模态 provider，其次第一个具备 image 能力的启用 provider。
     *
     * @return 目标 provider，无可用则返回 empty
     */
    public Optional<ScrmAiProviderEntity> resolveImageProvider() {
        List<ScrmAiProviderEntity> list = current();
        ScrmAiProviderEntity firstImage = null;
        for (ScrmAiProviderEntity p : list) {
            if (hasCapability(p, "image")) {
                if (Boolean.TRUE.equals(p.getDefaultImage())) {
                    return Optional.of(p);
                }
                if (firstImage == null) {
                    firstImage = p;
                }
            }
        }
        return Optional.ofNullable(firstImage);
    }

    /**
     * 按 ID 查询启用中的 provider。
     *
     * @param id Provider ID
     * @return 启用中的 provider，不存在返回 empty
     */
    public Optional<ScrmAiProviderEntity> getById(Long id) {
        return current().stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    /**
     * 全部启用中的 provider（按优先级升序）。
     *
     * @return 启用中的 provider 列表
     */
    public List<ScrmAiProviderEntity> all() {
        return current();
    }

    /**
     * 判断 provider 是否具备指定能力。
     *
     * @param provider   Provider 实体
     * @param capability 能力名（text / image / embedding）
     * @return true 表示具备
     */
    public static boolean hasCapability(ScrmAiProviderEntity provider, String capability) {
        if (provider == null || provider.getCapability() == null
                || provider.getCapability().isBlank()) {
            return false;
        }
        String target = capability == null ? "" : capability.trim().toLowerCase(Locale.ROOT);
        for (String c : provider.getCapability().split(",")) {
            if (c.trim().toLowerCase(Locale.ROOT).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前快照（懒加载兜底）。
     *
     * @return 启用中的 provider 列表
     */
    private List<ScrmAiProviderEntity> current() {
        List<ScrmAiProviderEntity> s = snapshot;
        if (s == null) {
            s = repository.findByEnabledTrueOrderByPriorityAsc();
            snapshot = s;
        }
        return s;
    }
}