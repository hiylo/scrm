/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : KnowledgeTemplateEntry.java
 * Date : 2026/10/23 12:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service.finance.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * T06 财税业务规则录入模板解析结果（单条）。
 * <p>
 * 对应 {@code docs/tasks/T06/knowledge-template.md} 通用字段 + 分类业务字段。
 * 通用元信息（分类/标题/规则编号/适用地区/适用对象/来源/状态/审查状态/审核人/
 * 最后人工复核日期/版本号/变更说明/录入人/录入日期/关联条目/问答对）由解析器
 * 从 Markdown 行首 {@code - 字段：值} 提取; 业务字段按分类原样保留在
 * {@link #businessFields}（渲染进正文, 便于检索命中与人工核对）。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
public class KnowledgeTemplateEntry {

    /** 分类: 营业执照办理 / 注销流程 / 资质代办 / 代账报税规则 */
    private String category;

    /** 标题 (一句话, ≤50 字) */
    private String title;

    /** 规则编号, 如 R-BIZ-0001 (全局唯一) */
    private String articleCode;

    /** 适用地区 */
    private String applicableRegion;

    /** 适用对象 */
    private String applicableObject;

    /** 来源: MANUAL / IMPORT / URL */
    private String sourceType;

    /** 状态: 草稿 / 审核中 / 已发布 / 已归档 */
    private String status;

    /** 审查状态: 待人工审核 / 已审核 / 审核驳回 */
    private String reviewStatus;

    /** 审核人 (姓名/工号) */
    private String reviewer;

    /** 最后人工复核日期 (YYYY-MM-DD, 待审核为 —) */
    private LocalDate lastReviewedDate;

    /** 版本号 (从 1 开始) */
    private Integer versionNumber;

    /** 变更说明 (版本>1 必填) */
    private String changeLog;

    /** 录入人 (AI 起草标注「AI 起草」) */
    private String createdBy;

    /** 录入日期 (YYYY-MM-DD) */
    private LocalDate createdDate;

    /** 关联条目 (规则编号, 逗号分隔) */
    private String relatedArticles;

    /** 问答对 (问/答列表, 可空) */
    private List<String> qaPairs;

    /** 分类业务字段 (key=模板字段名, value=填写内容, 含「—」占位) */
    private Map<String, String> businessFields;

    /** 附注内容 (待确认事项等, 可空) */
    private String notes;
}
