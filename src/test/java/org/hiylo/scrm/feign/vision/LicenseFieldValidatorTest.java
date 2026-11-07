/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : LicenseFieldValidatorTest.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.vision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证照字段确定性校验单元测试。
 * <p>
 * 覆盖视觉模型幻觉/退化件的拦截能力：身份证校验位、出生日期与号码交叉比对、
 * 性别交叉比对、统一社会信用代码校验、日期格式解析。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("证照字段确定性校验 单元测试")
class LicenseFieldValidatorTest {

    /** 合法身份证号（GB11643 校验位由算法生成, 生日 1988-06-12, 顺序码 123 为奇数=男） */
    private static final String VALID_ID = "310115198806121231";

    @Test
    @DisplayName("身份证号：合法号码通过校验")
    void validIdCard_numberPasses() {
        assertThat(LicenseFieldValidator.isValidIdCard18(VALID_ID)).isTrue();
    }

    @Test
    @DisplayName("身份证号：校验位错误被拦截")
    void invalidIdCard_wrongCheckDigit() {
        // 把末位 1 改成 9，校验位不通过
        String broken = VALID_ID.substring(0, 17) + "9";
        assertThat(LicenseFieldValidator.isValidIdCard18(broken)).isFalse();
    }

    @Test
    @DisplayName("身份证号：长度/非数字被拦截")
    void invalidIdCard_badShape() {
        assertThat(LicenseFieldValidator.isValidIdCard18("31011519880612123")).isFalse();
        assertThat(LicenseFieldValidator.isValidIdCard18("31011519880612123X")).isFalse();
        assertThat(LicenseFieldValidator.isValidIdCard18(null)).isFalse();
    }

    @Test
    @DisplayName("身份证号：非法出生日期（2月30日）被拦截")
    void invalidIdCard_impossibleBirthday() {
        // 构造生日为 1988-02-30 的号码，即使校验位算对也应被判非法
        String body = "310115" + "19880230" + "123";
        int sum = 0;
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        String[] codes = {"1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"};
        for (int i = 0; i < 17; i++) {
            sum += (body.charAt(i) - '0') * weights[i];
        }
        String id = body + codes[sum % 11];
        assertThat(LicenseFieldValidator.isValidIdCard18(id)).isFalse();
    }

    @Test
    @DisplayName("出生日期：与身份证号一致时无问题（干净件场景）")
    void birthDate_consistent_noProblem() {
        Map<String, String> values = new HashMap<>();
        values.put("姓名", "陈静雯");
        values.put("性别", "男");
        values.put("民族", "汉");
        values.put("出生", "1988年6月12日");
        values.put("住址", "上海市浦东新区张杨路500弄12号1802室");
        values.put("公民身份号码", VALID_ID);

        Map<String, List<String>> problems = LicenseFieldValidator.validateIdCard(values);
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("出生日期：与身份证号不一致时被交叉比对拦截（退化件核心防线）")
    void birthDate_inconsistent_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("姓名", "陈静雯");
        values.put("性别", "男");
        values.put("出生", "1983年6月12日");      // 实测退化样本的错误：1983 应为 1988
        values.put("住址", "上海市浦东新区张杨路500弄12号1802室");
        values.put("公民身份号码", VALID_ID);

        Map<String, List<String>> problems = LicenseFieldValidator.validateIdCard(values);
        assertThat(problems).containsKey("出生");
        assertThat(problems.get("出生").get(0)).contains("与身份证号记载的出生日期不一致");
        // 性别：号码第 17 位是 3（奇数=男），识别为男 → 一致，不报错
        assertThat(problems).doesNotContainKey("性别");
    }

    @Test
    @DisplayName("性别：与号码第 17 位奇偶不一致时被拦截")
    void gender_inconsistent_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("姓名", "陈静雯");
        values.put("性别", "女");                   // 号码第 17 位 3 = 奇数 = 男，冲突
        values.put("出生", "1988年6月12日");
        values.put("住址", "上海市浦东新区张杨路500弄12号1802室");
        values.put("公民身份号码", VALID_ID);

        Map<String, List<String>> problems = LicenseFieldValidator.validateIdCard(values);
        assertThat(problems).containsKey("性别");
        assertThat(problems.get("性别").get(0)).contains("第 17 位不一致");
    }

    @Test
    @DisplayName("身份证号：数字错误时校验位拦截（模型幻觉防线）")
    void idCard_hallucinatedDigit_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("姓名", "陈静雯");
        values.put("性别", "女");
        values.put("出生", "1988年6月12日");
        values.put("住址", "上海市浦东新区张杨路500弄12号1802室");
        // 模型把倒数第二位 3 编成 5
        values.put("公民身份号码", "310115198806121251");

        Map<String, List<String>> problems = LicenseFieldValidator.validateIdCard(values);
        assertThat(problems).containsKey("公民身份号码");
        assertThat(problems.get("公民身份号码").get(0)).contains("GB11643");
    }

    @Test
    @DisplayName("必填项缺失：姓名/住址为空时被标记")
    void requiredFields_missing_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("姓名", "");
        values.put("公民身份号码", VALID_ID);
        values.put("出生", "1988年6月12日");
        values.put("住址", "");

        Map<String, List<String>> problems = LicenseFieldValidator.validateIdCard(values);
        assertThat(problems).containsKeys("姓名", "住址");
    }

    @Test
    @DisplayName("统一社会信用代码：合法代码通过 GB32100 校验")
    void creditCode_valid_passes() {
        // 91310115MA1K3WXY7X（GB32100 校验位由算法验证通过）
        assertThat(LicenseFieldValidator.isValidCreditCode("91310115MA1K3WXY7X")).isTrue();
    }

    @Test
    @DisplayName("统一社会信用代码：校验位错误被拦截")
    void creditCode_wrongCheck_caught() {
        assertThat(LicenseFieldValidator.isValidCreditCode("91310115MA1K3WXY7N")).isFalse();
        assertThat(LicenseFieldValidator.isValidCreditCode("91310115MA1K3WXY")).isFalse();
        assertThat(LicenseFieldValidator.isValidCreditCode(null)).isFalse();
    }

    @Test
    @DisplayName("营业执照：字段齐全且校验通过时无问题")
    void businessLicense_allGood_noProblem() {
        Map<String, String> values = new HashMap<>();
        values.put("企业名称", "上海云溪财税服务有限公司");
        values.put("统一社会信用代码", "91310115MA1K3WXY7X");
        values.put("法定代表人", "张明远");
        values.put("成立日期", "2019年03月15日");

        assertThat(LicenseFieldValidator.validateBusinessLicense(values)).isEmpty();
    }

    @Test
    @DisplayName("营业执照：信用代码错误被拦截")
    void businessLicense_badCreditCode_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("企业名称", "上海云溪财税服务有限公司");
        values.put("统一社会信用代码", "91310115MA1K3WXY7N");
        values.put("法定代表人", "张明远");

        Map<String, List<String>> problems = LicenseFieldValidator.validateBusinessLicense(values);
        assertThat(problems).containsKey("统一社会信用代码");
        assertThat(problems.get("统一社会信用代码").get(0)).contains("GB32100");
    }

    @Test
    @DisplayName("开户许可证：账号格式异常被拦截")
    void accountLicense_badAccount_caught() {
        Map<String, String> values = new HashMap<>();
        values.put("账户名称", "上海云溪财税服务有限公司");
        values.put("账号", "3100-1234");
        values.put("开户行", "中国工商银行上海分行");

        Map<String, List<String>> problems = LicenseFieldValidator.validateAccountLicense(values);
        assertThat(problems).containsKey("账号");
    }

    @Test
    @DisplayName("日期解析：中文日期与区间日期")
    void dateParsing() {
        assertThat(LicenseFieldValidator.parseCnDate("1988年6月12日")).hasToString("1988-06-12");
        assertThat(LicenseFieldValidator.parseCnDate("2019年03月15日")).hasToString("2019-03-15");
        assertThat(LicenseFieldValidator.parseCnDate("不是日期")).isNull();
        assertThat(LicenseFieldValidator.parseDotDate("2018.07.01")).hasToString("2018-07-01");
        assertThat(LicenseFieldValidator.isValidDateRange("2018.07.01-2038.07.01")).isTrue();
        assertThat(LicenseFieldValidator.isValidDateRange("2018.07.01-长期")).isTrue();
        assertThat(LicenseFieldValidator.isValidDateRange("乱码")).isFalse();
    }

    @Test
    @DisplayName("身份证号规范化：去空格且 X 大写")
    void normalizeIdNo() {
        assertThat(LicenseFieldValidator.normalizeIdNo(" 310115 19880612123x "))
                .isEqualTo("31011519880612123X");
        assertThat(LicenseFieldValidator.normalizeIdNo(null)).isEmpty();
    }
}
