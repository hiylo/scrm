/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm-finance
 * File : ScrmTaxDeclarationSheetDto.java
 * Date : 2026/11/08 10:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 申报表 DTO（报税日历域, 申报表生成 + CSV 导出）。
 * <p>
 * 一张申报表 = 表头信息（表名 / 税种 / 所属期 / 纳税人 / 申报状态）+ 行项目明细
 * + 合计行。行项目按税种套用固定表样（增值税: 销售收入 / 销项税 / 进项税 / 应纳税额;
 * 个税: 收入额 / 费用 / 应纳税所得额 / 应纳税额 …），每行带「本期数 / 累计数 / 备注」,
 * 与纸质申报表的三列结构一致。
 * </p>
 * <p>
 * <b>数据口径</b>：本对象由「申报档案 + 申报记录」聚合而来，是纯内部闭环产物
 * （不调用电子税局、不依赖发票数据）。申报记录只落「申报税额 / 实缴税额」两个金额,
 * 因此税基类行项目（销售收入 / 收入额 / 营业收入…）在没有账务发票数据时以 0 占位,
 * 并在备注中标注来源缺失, 由会计在申报表上人工补录——绝不臆造数值。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrmTaxDeclarationSheetDto {

    /** 公司主体 ID */
    private Long companyId;

    /** 申报表名称（如「增值税及附加税费申报表（小规模纳税人适用）」） */
    private String sheetName;

    /** 税种: VAT / IIT / CIT / SOCIAL_SECURITY */
    private String taxType;

    /** 税种中文名（如「增值税」） */
    private String taxTypeLabel;

    /** 所属期年份（如 2026） */
    private Integer periodYear;

    /** 所属期月份（1-12） */
    private Integer periodMonth;

    /** 所属期展示文案（如「2026年10月」） */
    private String periodLabel;

    /** 纳税人名称 */
    private String taxpayerName;

    /** 纳税人识别号（统一社会信用代码；申报档案未登记时为空） */
    private String taxpayerTaxNo;

    /** 申报状态: NOT_DECLARED(无申报记录) / NOT_STARTED / PENDING / FILED / PAID / OVERDUE / ARCHIVED / CANCELED */
    private String declarationStatus;

    /** 申报状态中文名 */
    private String declarationStatusLabel;

    /** 申报截止日期（无记录时为空） */
    private String dueDate;

    /** 申报日期（申报完成时间, 未申报时为空） */
    private String filedAt;

    /** 完税凭证号（可空） */
    private String taxReceiptNo;

    /** 行项目明细（按表样顺序） */
    @Builder.Default
    private List<SheetRow> rows = new ArrayList<>();

    /** 合计行（各税目应纳税额之和） */
    private SheetRow total;

    /** 表级备注（数据来源 / 缺失项提示） */
    private String remark;

    /** 生成时间 */
    private LocalDateTime generatedAt;

    /**
     * 申报表行项目。
     * <p>
     * 三列结构对应纸质申报表：本期数（所属期当月/当季）、累计数（本年初至所属期末）、备注。
     * </p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SheetRow {

        /** 行名（如「应纳税额」） */
        private String label;

        /** 本期数 */
        private BigDecimal current;

        /** 累计数 */
        private BigDecimal cumulative;

        /** 备注（数值来源 / 缺失提示） */
        private String remark;
    }
}
