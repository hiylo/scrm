/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmRecordingCapabilityVo.java
 * Date : 2026/10/22 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 录音能力与操作指引 VO。
 * <p>
 * 返回本机（按 ROM / 系统标识匹配）的录音能力说明与「如何开启自动录音」
 * 的分步指引。App 首次启动或设置页拉取，能力探测逻辑在客户端完成，
 * 后端只提供静态指引文本（可配置化，便于后续按机型补充分支）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmRecordingCapabilityVo {

    /** 是否支持系统通话录音（客户端探测后上报覆盖, 后端仅返回默认规则） */
    private Boolean supportedByDefault;

    /** 匹配到的 ROM 规则标识（如 MIUI / COLOROS / EMUI / VIVO / SAMSUNG / AOSP） */
    private String rom;

    /** 系统提示文案（如「本机不支持自动录音, 可使用手动上传」） */
    private String summary;

    /** 开启自动录音的分步指引 */
    private List<String> steps;

    /** 录音文件目录说明（按 ROM, 供高级用户核对） */
    private List<String> directories;

    /** 附加说明（合规告知 / 覆盖率口径等） */
    private Map<String, String> notes;
}
