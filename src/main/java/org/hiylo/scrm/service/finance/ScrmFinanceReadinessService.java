/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScrmFinanceReadinessService.java
 * Date : 2026/10/24 16:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.ScrmInspectionProperties;
import org.hiylo.scrm.feign.OcrApiConfig;
import org.hiylo.scrm.feign.QixinApiConfig;
import org.hiylo.scrm.integration.wework.config.WeworkConfig;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财税域「就绪状态」汇总服务。
 * <p>
 * <b>解决什么问题</b>：财税模块有 5 条依赖外部条件的链路（AI 通道、证照 OCR、
 * 工商 API、公告抓取、企微官方通道），此前判断某条链路「能不能用」只能靠人
 * 逐个查环境变量和配置。出问题时也分不清是「没开」还是「开了但坏了」。
 * <p>
 * <b>为什么不列个推</b>：本项目没有移动客户端，{@code scrm_user_device} 无任何设备注册，
 * 即便开启个推也没有推送目标。通知一律走 IN_APP 站内信（落库即送达，已验证）。
 * 把它列进就绪表会误导成「配好个推就能用」，故不纳入统计；将来上线客户端再补。
 * </p>
 * </p>
 * <p>
 * 本服务把各链路的开关、凭证、就绪判定汇总成一张表，供运维/交付一次看全：
 * </p>
 * <ul>
 *   <li>{@code ready=true}：开关已开且凭证/参数齐备，功能可用</li>
 *   <li>{@code ready=false}：附 {@code blocker} 说明缺什么（不是笼统的「不可用」）</li>
 * </ul>
 * <p>
 * <b>安全约束</b>：只返回「是否已配置」的布尔值，<b>绝不回显 token / secret 明文</b>。
 * </p>
 * <p>
 * <b>不查业务数据</b>：本接口只反映依赖与配置状态，不做全表统计（避免在运维页
 * 触发慢查询）；业务数据量看各模块自己的统计端点。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrmFinanceReadinessService {

    /** 环境变量读取器（AI 等未走 @ConfigurationProperties 的开关） */
    private final Environment environment;

    /** 证照 OCR 配置（外接 OCR 服务） */
    private final OcrApiConfig ocrApiConfig;

    /** 视觉模型配置（本地 llama-server 或 OpenAI 兼容视觉网关） */
    private final org.hiylo.scrm.config.VisionModelProperties visionProperties;

    /** 启信宝（工商 API）配置 */
    private final QixinApiConfig qixinApiConfig;

    /** 巡检与公告源配置 */
    private final ScrmInspectionProperties inspectionProperties;

    /** 企微配置 */
    private final WeworkConfig weworkConfig;

    /**
     * 汇总财税域各外部依赖链路的就绪状态。
     *
     * @return 就绪汇总（含逐项状态与统计）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> readiness() {
        List<Map<String, Object>> modules = new ArrayList<>();

        // ---------- ① AI 通道（接待 + 营销批量生成共用） ----------
        boolean aiEnabled = environment.getProperty("scrm.ai.enabled", Boolean.class, false);
        String aiBaseUrl = environment.getProperty("scrm.ai.base-url", "");
        boolean aiConfigured = aiBaseUrl != null && !aiBaseUrl.isBlank();
        modules.add(module("ai", "AI 接待 / 营销文案生成", aiEnabled,
                aiConfigured ? "已配置 AI 服务地址: " + aiBaseUrl : "未配置 scrm.ai.base-url",
                aiEnabled && aiConfigured ? null
                        : "设置 SCRM_AI_ENABLED=true 并配置 SCRM_AI_BASE_URL（NAS LiteLLM 或 ai-server）"));

        // ---------- ② 证照 OCR ----------
        // 识别实际经 OcrRecognizerRouter 路由: 视觉通道启用时优先, 否则走外接 OCR 服务。
        // 就绪判定必须按「同一优先级」来, 否则会出现「视觉已启用但状态显示未就绪」的误导。
        if (visionProperties.isEnabled()) {
            modules.add(module("licenseOcr", "证照 OCR 识别", true,
                    "视觉通道: " + visionProperties.getModel() + " @ " + visionProperties.getBaseUrl(),
                    null));
        } else {
            boolean ocrEnabled = ocrApiConfig.isEnabled();
            boolean ocrConfigured = ocrApiConfig.getBaseUrl() != null
                    && !ocrApiConfig.getBaseUrl().isBlank();
            modules.add(module("licenseOcr", "证照 OCR 识别", ocrEnabled && ocrConfigured,
                    ocrConfigured
                            ? "外接 OCR 服务地址: " + ocrApiConfig.getBaseUrl()
                            : "未配置 scrm.ocr.base-url",
                    ocrEnabled && ocrConfigured ? null
                            : "开启视觉通道（SCRM_VISION_ENABLED=true）或部署 OCR 服务并设 "
                                    + "SCRM_OCR_ENABLED=true / SCRM_OCR_BASE_URL"));
        }

        // ---------- ③ 工商巡检（启信宝） ----------
        boolean qixinEnabled = qixinApiConfig.isEnabled();
        boolean qixinToken = qixinApiConfig.getToken() != null && !qixinApiConfig.getToken().isBlank();
        modules.add(module("businessInspection", "工商状态巡检（启信宝）", qixinEnabled && qixinToken,
                qixinEnabled
                        ? (qixinToken ? "token 已配置" : "token 未配置")
                        : "开关未开启",
                qixinEnabled && qixinToken ? null
                        : "设置 SCRM_INSPECTION_QIXIN_ENABLED=true + SCRM_INSPECTION_QIXIN_TOKEN（需签约）"));

        // ---------- ④ 税务公告抓取 ----------
        ScrmInspectionProperties.NoticeSources notice = inspectionProperties.getNotice();
        List<String> usableSources = new ArrayList<>();
        boolean gd = notice.isGuangdongEnabled();
        boolean zj = notice.isZhejiangEnabled();
        boolean sx = notice.isShaanxiEnabled();
        if (gd) {
            usableSources.add("GD(广东)");
        }
        if (zj) {
            usableSources.add("ZJ(浙江)");
        }
        if (sx && isShaanxiConfigured(notice)) {
            usableSources.add("SX(陕西)");
        } else if (sx) {
            usableSources.add("SX(陕西, 参数不全)");
        }
        boolean noticeReady = !usableSources.isEmpty();
        modules.add(module("noticeScraping", "税务公告抓取", noticeReady,
                usableSources.isEmpty() ? "所有公告源均已关闭" : "可用公告源: " + String.join(" / ", usableSources),
                noticeReady ? null
                        : "至少开启一个公告源（scrm.inspection.notice.guangdong-enabled / zhejiang-enabled）"));

        // ---------- ⑤ 企微官方通道 ----------
        boolean weworkReal = weworkConfig.isRealApiEnabled() && !weworkConfig.isMockMode();
        modules.add(module("wework", "企业微信官方通道", weworkReal,
                weworkReal ? "已切换真实 API" : "mock 模式（返回假数据）",
                weworkReal ? null
                        : "设置 WEWORK_REAL_API_ENABLED=true + WEWORK_MOCK_MODE=false "
                                + "并配置 WEWORK_CORP_ID/AGENT_ID/SECRET"));

        long readyCount = modules.stream()
                .filter(m -> Boolean.TRUE.equals(m.get("ready")))
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", LocalDateTime.now().toString());
        result.put("readyCount", readyCount);
        result.put("totalCount", modules.size());
        result.put("allReady", readyCount == modules.size());
        result.put("modules", modules);
        result.put("note", "ready 仅表示开关与凭证齐备；业务数据量请查各模块统计端点");
        return result;
    }

    /**
     * 构造单个模块就绪状态。
     *
     * @param key     模块键
     * @param name    模块名称
     * @param ready   是否就绪
     * @param detail  当前状态描述
     * @param blocker 未就绪时的阻塞说明（就绪时为 null）
     * @return 状态条目
     */
    private Map<String, Object> module(String key, String name, boolean ready,
                                       String detail, String blocker) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("name", name);
        item.put("ready", ready);
        item.put("detail", detail);
        item.put("blocker", blocker);
        return item;
    }

    /**
     * 判断陕西公告源参数是否齐备（dataproxy 必需的三项）。
     *
     * @param notice 公告源配置
     * @return true 表示参数齐备
     */
    private boolean isShaanxiConfigured(ScrmInspectionProperties.NoticeSources notice) {
        return notBlank(notice.getShaanxiColumnId())
                && notBlank(notice.getShaanxiUnitId())
                && notBlank(notice.getShaanxiWebId());
    }

    /**
     * 判断字符串非空。
     *
     * @param s 字符串
     * @return true 表示非空
     */
    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
