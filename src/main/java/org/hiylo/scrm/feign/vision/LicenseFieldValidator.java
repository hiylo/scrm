/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : LicenseFieldValidator.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.vision;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 证照字段确定性校验（视觉模型的准确性防线）。
 * <p>
 * 视觉大模型是生成式模型, <b>对退化/折叠/光照不均的纸件会产生数字错误</b>
 * （实测：身份证退化样本把「1988」读成「1983」、住址丢掉一位数字）。本类不依赖
 * 任何 OCR 引擎, 用<b>格式算法 + 跨字段交叉比对</b>把错误确定性地拦住:
 * </p>
 * <ul>
 *   <li><b>公民身份号码</b>：GB 11643 校验位（第 18 位）+ 出生日期合法性</li>
 *   <li><b>出生日期交叉比对</b>：与身份证号第 7-14 位比对——这是最强的一道防线,
 *       模型把「出生」字段读错时会被立刻发现（即使身份证号本身读对了）</li>
 *   <li><b>性别交叉比对</b>：与身份证号第 17 位奇偶比对（奇男偶女）</li>
 *   <li><b>统一社会信用代码</b>：GB 32100 校验码（第 18 位）</li>
 *   <li><b>日期格式</b>：中文日期（YYYY年M月D日）与区间日期（YYYY.MM.DD-YYYY.MM.DD）</li>
 * </ul>
 * <p>
 * <b>为何内置而非引用 components</b>：scrm 为独立单体（零 components 依赖），
 * 客户机部署要求轻量；校验算法仅 GB 11643 / GB 32100 两段加权取模（约 30 行）,
 * 内置可避免为少量逻辑引入整套基础库及其传递依赖。算法与
 * {@code org.hiylo.components.commons.utils.IdcardUtils} 一致, 可交叉验证。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
public final class LicenseFieldValidator {

    /** GB 11643 加权因子 */
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /** GB 11643 校验码表（模 11） */
    private static final String[] ID_CHECK_CODES =
            {"1", "0", "X", "9", "8", "7", "6", "5", "4", "3", "2"};

    /** GB 32100 统一社会信用代码字符集（不含 I/O/S/V/Z） */
    private static final String CREDIT_CODE_CHARS = "0123456789ABCDEFGHJKLMNPQRTUWXY";

    /** GB 32100 加权因子 */
    private static final int[] CREDIT_WEIGHTS =
            {1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28};

    /** 中文日期：2019年03月15日 / 1988年6月12日 */
    private static final Pattern CN_DATE =
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");

    /** 点分日期：2018.07.01 */
    private static final Pattern DOT_DATE = Pattern.compile("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})");

    /** 工具类，私有构造防止实例化 */
    private LicenseFieldValidator() {
    }

    /**
     * 身份证字段校验（正面人像面）。
     * <p>
     * 校验身份证号本身 + 出生日期/性别与号码的交叉一致性。返回的问题会追加到对应字段。
     * </p>
     *
     * @param values 识别出的字段值（键为中文标准键：姓名/性别/民族/出生/住址/公民身份号码）
     * @return 按字段键分组的问题清单（空 Map 表示全部通过）
     */
    public static Map<String, List<String>> validateIdCard(Map<String, String> values) {
        Map<String, List<String>> problems = new java.util.LinkedHashMap<>();
        String idNo = normalizeIdNo(values.get("公民身份号码"));

        // 1) 身份证号: 校验位 + 生日合法性
        if (isBlank(idNo)) {
            addProblem(problems, "公民身份号码", "未识别到公民身份号码");
        } else if (!isValidIdCard18(idNo)) {
            addProblem(problems, "公民身份号码", "GB11643 校验位不通过（数字有误，必须人工核对）");
        }

        // 2) 出生日期: 与号码第 7-14 位交叉比对（退化件最易错字段）
        String birth = values.get("出生");
        if (isBlank(birth)) {
            addProblem(problems, "出生", "未识别到出生日期");
        } else if (!isBlank(idNo) && idNo.length() >= 14) {
            LocalDate fromId = parseCompactDate(idNo.substring(6, 14));
            LocalDate fromField = parseCnDate(birth);
            if (fromField == null) {
                addProblem(problems, "出生", "日期格式无法解析：" + birth);
            } else if (fromId != null && !fromId.equals(fromField)) {
                addProblem(problems, "出生", "与身份证号记载的出生日期不一致（号码：" + fromId
                        + "，识别：" + fromField + "），以号码为准并人工核对");
            }
        }

        // 3) 性别: 与号码第 17 位奇偶比对（奇=男 / 偶=女）
        String gender = trimToEmpty(values.get("性别"));
        if (!isBlank(gender) && !isBlank(idNo) && idNo.length() >= 17) {
            char seq = idNo.charAt(16);
            if (Character.isDigit(seq)) {
                boolean male = ((seq - '0') % 2) == 1;
                boolean statedMale = gender.contains("男");
                boolean statedFemale = gender.contains("女");
                if (statedMale != male && statedFemale == male) {
                    addProblem(problems, "性别", "与身份证号第 17 位不一致（号码为"
                            + (male ? "男" : "女") + "，识别为" + gender + "）");
                }
            }
        }

        // 4) 必填项存在性
        requireField(problems, values, "姓名");
        requireField(problems, values, "住址");

        return problems;
    }

    /**
     * 营业执照字段校验。
     *
     * @param values 识别出的字段值（统一社会信用代码/企业名称/成立日期 等）
     * @return 按字段键分组的问题清单
     */
    public static Map<String, List<String>> validateBusinessLicense(Map<String, String> values) {
        Map<String, List<String>> problems = new java.util.LinkedHashMap<>();
        String creditCode = trimToEmpty(values.get("统一社会信用代码")).replace(" ", "")
                .toUpperCase(Locale.ROOT);

        if (isBlank(creditCode)) {
            addProblem(problems, "统一社会信用代码", "未识别到统一社会信用代码");
        } else if (!isValidCreditCode(creditCode)) {
            addProblem(problems, "统一社会信用代码", "GB32100 校验码不通过（数字/字母有误，必须人工核对）");
        }

        requireField(problems, values, "企业名称");
        requireField(problems, values, "法定代表人");

        String establish = values.get("成立日期");
        if (!isBlank(establish) && parseCnDate(establish) == null) {
            addProblem(problems, "成立日期", "日期格式无法解析：" + establish);
        }
        return problems;
    }

    /**
     * 开户许可证字段校验（账号为纯数字、长度 8~32）。
     *
     * @param values 识别出的字段值
     * @return 按字段键分组的问题清单
     */
    public static Map<String, List<String>> validateAccountLicense(Map<String, String> values) {
        Map<String, List<String>> problems = new java.util.LinkedHashMap<>();
        String account = trimToEmpty(values.get("账号")).replace(" ", "");
        if (isBlank(account)) {
            addProblem(problems, "账号", "未识别到银行账号");
        } else if (!account.matches("\\d{8,32}")) {
            addProblem(problems, "账号", "账号格式异常（应为 8~32 位数字）");
        }
        requireField(problems, values, "账户名称");
        requireField(problems, values, "开户行");
        return problems;
    }

    /**
     * 校验 18 位居民身份证号（GB 11643）。
     * <p>
     * 校验内容：长度、前 17 位纯数字、第 18 位校验码（加权和模 11）、出生日期合法性。
     * 15 位老号码不在此校验范围（识别场景以 18 位为主）。
     * </p>
     *
     * @param idCard 18 位身份证号
     * @return 是否合法
     */
    public static boolean isValidIdCard18(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        String body = idCard.substring(0, 17);
        for (int i = 0; i < body.length(); i++) {
            if (!Character.isDigit(body.charAt(i))) {
                return false;
            }
        }
        int sum = 0;
        for (int i = 0; i < body.length(); i++) {
            sum += (body.charAt(i) - '0') * ID_WEIGHTS[i];
        }
        String expected = ID_CHECK_CODES[sum % 11];
        if (!expected.equalsIgnoreCase(idCard.substring(17))) {
            return false;
        }
        // 出生日期段（第 7-14 位）必须为合法日期
        return parseCompactDate(idCard.substring(6, 14)) != null;
    }

    /**
     * GB 32100 统一社会信用代码校验（18 位, 第 18 位为校验码）。
     *
     * @param code 统一社会信用代码
     * @return 是否通过
     */
    public static boolean isValidCreditCode(String code) {
        if (code == null || code.length() != 18) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int idx = CREDIT_CODE_CHARS.indexOf(code.charAt(i));
            if (idx < 0) {
                return false;
            }
            sum += idx * CREDIT_WEIGHTS[i];
        }
        int checkIdx = (31 - (sum % 31)) % 31;
        return code.charAt(17) == CREDIT_CODE_CHARS.charAt(checkIdx);
    }

    /**
     * 校验并规范化身份证号（去空格、X 大写）。
     *
     * @param raw 原始识别值
     * @return 规范化后的号码（无值时返回空串）
     */
    public static String normalizeIdNo(String raw) {
        return trimToEmpty(raw).replace(" ", "").replace("　", "")
                .toUpperCase(Locale.ROOT);
    }

    /**
     * 解析中文日期（YYYY年M月D日）。
     *
     * @param text 原始文本
     * @return 解析结果, 解析失败返回 null
     */
    public static LocalDate parseCnDate(String text) {
        if (isBlank(text)) {
            return null;
        }
        Matcher m = CN_DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析点分日期（YYYY.MM.DD）。
     *
     * @param text 原始文本
     * @return 解析结果, 解析失败返回 null
     */
    public static LocalDate parseDotDate(String text) {
        if (isBlank(text)) {
            return null;
        }
        Matcher m = DOT_DATE.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析紧凑日期（身份证号内的 YYYYMMDD）。
     *
     * @param compact 8 位日期串
     * @return 解析结果, 解析失败返回 null
     */
    public static LocalDate parseCompactDate(String compact) {
        if (compact == null || compact.length() != 8) {
            return null;
        }
        try {
            // 必须用 STRICT：默认 SMART 模式会把 2 月 30 日「智能纠正」为 2 月 29 日，
            // 从而放过非法出生日期（如号码中编造 19880230）
            return LocalDate.parse(compact, DateTimeFormatter.ofPattern("uuuuMMdd")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 判断有效期区间是否形如 2018.07.01-2038.07.01（或长期）。
     *
     * @param text 有效期限文本
     * @return 是否合法
     */
    public static boolean isValidDateRange(String text) {
        if (isBlank(text)) {
            return false;
        }
        if (text.contains("长期")) {
            return true;
        }
        String[] parts = text.split("\\s*[-—~至]\\s*");
        if (parts.length != 2) {
            return false;
        }
        return parseDotDate(parts[0]) != null && parseDotDate(parts[1]) != null;
    }

    /**
     * 追加问题（内部工具）。
     *
     * @param problems 问题容器
     * @param key      字段键
     * @param message  问题描述
     */
    private static void addProblem(Map<String, List<String>> problems, String key, String message) {
        problems.computeIfAbsent(key, k -> new ArrayList<>()).add(message);
    }

    /**
     * 必填项存在性校验（内部工具）。
     *
     * @param problems 问题容器
     * @param values   字段值
     * @param key      字段键
     */
    private static void requireField(Map<String, List<String>> problems,
                                     Map<String, String> values, String key) {
        if (isBlank(values.get(key))) {
            addProblem(problems, key, "必填项未识别（图片可能模糊/遮挡）");
        }
    }

    /**
     * 空值判断（内部工具）。
     *
     * @param s 文本
     * @return 是否为空
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 空值安全转换（内部工具）。
     *
     * @param s 文本
     * @return 非空字符串
     */
    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
