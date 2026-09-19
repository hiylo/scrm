/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : RiskRuleEvaluator.java
 * Date : 2026/07/27 02:41:22
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.ConstructorReference;
import org.springframework.expression.spel.ast.FunctionReference;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 基于 SpEL 的风险规则评估器。
 * <p>
 * 将 {@link RiskRuleContext} 中的字段注册为 SpEL 变量 (如 {@code #message}、{@code #platformType}),
 * 解析规则条件表达式并求值为 boolean。命中返回 true, 否则 false。
 * </p>
 * <p>
 * 安全策略:
 * <ul>
 *   <li>求值前对表达式做静态校验 ({@link #validateExpression}): 遍历 AST 拒绝类型引用
 *       {@code T(...)}、构造器 {@code new ...}、函数引用 {@code #fn(...)}, 并对规范化后的源码
 *       做危险 token 黑名单扫描 (如 {@code Runtime}/{@code System}/{@code getClass}/{@code exec}/
 *       {@code invoke}/{@code forName}/{@code getRuntime}/{@code Environment}/{@code exit}/
 *       {@code loadLibrary} 等), 含危险语法直接拒绝并抛业务异常。</li>
 *   <li>求值上下文使用 {@link SimpleEvaluationContext} (而非 {@code StandardEvaluationContext}),
 *       仅启用实例方法调用与只读数据绑定, 运行时同样禁止类型引用、构造器与任意反射。</li>
 *   <li>求值结果类型白名单: 仅允许 Boolean/Number/String, 超范围直接抛业务异常拒绝。</li>
 *   <li>规则创建/更新入口由 {@code ScrmRiskRuleService} 在持久化前调用 {@link #validateExpression},
 *       危险表达式在入库前即被拦截。</li>
 * </ul>
 * </p>
 * <p>
 * 异常策略: 解析失败 / 执行失败时仅记录 warn 日志并返回 false, 不抛异常, 避免单条规则评估异常
 * 阻断整个评估链路; 但表达式安全校验失败 ({@link ScrmException}) 会向上抛出, 由调用方按
 * 单条规则跳过处理 (消息评估链路) 或以 400 直接拒绝 (规则创建/更新入口)。
 * </p>
 *
 * <h3>SpEL 表达式示例</h3>
 * <ul>
 *   <li>{@code #message.contains('微信')} - 关键词命中</li>
 *   <li>{@code #message.length() > 100} - 消息超长</li>
 *   <li>{@code #messageCountInWindow > 10} - 窗口内消息频次超限</li>
 *   <li>{@code #platformType == 'wechat_personal' and #message.contains('转账')} - 平台+关键词组合</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@Slf4j
@Component
public class RiskRuleEvaluator {

    /** SpEL 表达式解析器 (无状态, 可复用) */
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 危险 token 黑名单 (小写, 词边界感知匹配)。
     * <p>覆盖类型引用、反射、进程/系统调用等 SpEL 注入常用入口, 命中即拒绝。</p>
     */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "t(", "new", "runtime", "processbuilder", "class", "getclass", "system",
            "exec", "invoke", "method", "forname", "getruntime", "environment", "exit",
            "loadlibrary", "getmethod", "getdeclaredmethod", "getconstructor", "getdeclaredconstructor",
            "getfield", "getdeclaredfield", "setaccessible", "newinstance", "loadclass",
            "getproperty", "getenv", "defineclass", "reflect", "wait", "notify",
            "currentthread", "thread", "socket", "shutdown");

    /**
     * 评估规则条件表达式。
     * <p>
     * 将 context 字段注册为 SpEL 变量后解析执行, 结果需为 Boolean, Number/String 按未命中处理。
     * 表达式先经 {@link #validateExpression} 静态安全校验, 再受求值结果类型白名单约束。
     * </p>
     *
     * @param conditionExpression SpEL 条件表达式
     * @param context             风险规则上下文（提供事实变量）
     * @return true 表示命中规则, false 表示未命中或评估异常
     * @throws ScrmException 表达式含危险语法或返回类型越界 (由评估调用方按单条规则跳过)
     */
    public boolean evaluate(String conditionExpression, RiskRuleContext context) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return false;
        }
        validateExpression(conditionExpression);
        try {
            EvaluationContext evalContext = buildEvaluationContext(context);
            Expression expression = parser.parseExpression(conditionExpression);
            Object result = expression.getValue(evalContext);
            return interpretResult(conditionExpression, result);
        } catch (ScrmException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SpEL 表达式评估失败, 视为未命中: expr={}, err={}",
                    conditionExpression, e.getMessage());
            return false;
        }
    }

    /**
     * 对条件表达式执行安全校验。
     * <p>依次执行: 空值检查 → AST 结构拦截 (类型引用/构造器/函数引用) → 危险 token 黑名单扫描。
     * 任一环节命中即抛业务异常, 供规则创建/更新入口在持久化前拦截。</p>
     *
     * @param conditionExpression 待校验的 SpEL 条件表达式
     * @throws ScrmException 表达式为空 / 无法解析 / 含危险语法
     */
    public void validateExpression(String conditionExpression) throws ScrmException {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            throw ScrmException.badRequest("条件表达式不能为空");
        }
        SpelNode ast;
        try {
            ast = ((SpelExpression) parser.parseExpression(conditionExpression)).getAST();
        } catch (Exception e) {
            throw ScrmException.badRequest("条件表达式非法, 无法解析: " + e.getMessage());
        }
        checkSpelNode(ast, conditionExpression);
        String normalized = normalize(conditionExpression);
        for (String token : FORBIDDEN_TOKENS) {
            if (matchesToken(normalized, token)) {
                throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                        "条件表达式包含被安全策略禁止的语法/引用 (token=" + token + "), 已被拒绝:"
                                + conditionExpression);
            }
        }
    }

    /**
     * 从 facts map 构建风险规则上下文。
     * <p>支持从外部传入的键值对构建上下文, 便于扩展与测试。</p>
     *
     * @param facts 事实键值对 (key 对应 RiskRuleContext 字段名)
     * @return 风险规则上下文
     */
    public RiskRuleContext buildContext(Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return RiskRuleContext.builder().build();
        }
        RiskRuleContext.RiskRuleContextBuilder builder = RiskRuleContext.builder();
        Object message = facts.get("message");
        if (message instanceof String s) {
            builder.message(s);
        }
        Object platformType = facts.get("platformType");
        if (platformType instanceof String s) {
            builder.platformType(s);
        }
        Object accountId = facts.get("accountId");
        if (accountId instanceof Long l) {
            builder.accountId(l);
        }
        Object customerId = facts.get("customerId");
        if (customerId instanceof String s) {
            builder.customerId(s);
        }
        Object eventTime = facts.get("eventTime");
        if (eventTime instanceof LocalDateTime t) {
            builder.eventTime(t);
        }
        Object messageCountInWindow = facts.get("messageCountInWindow");
        if (messageCountInWindow instanceof Integer i) {
            builder.messageCountInWindow(i);
        }
        Object extra = facts.get("extra");
        if (extra instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extraMap = (Map<String, Object>) m;
            builder.extra(extraMap);
        }
        return builder.build();
    }

    /**
     * 递归扫描 SpEL AST, 拒绝类型引用、构造器与函数引用节点。
     *
     * @param node       当前 AST 节点
     * @param expression 原始表达式 (用于错误消息)
     * @throws ScrmException 发现危险节点
     */
    private void checkSpelNode(SpelNode node, String expression) throws ScrmException {
        if (node == null) {
            return;
        }
        if (node instanceof TypeReference) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "条件表达式禁止类型引用 T(...): " + expression);
        }
        if (node instanceof ConstructorReference) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "条件表达式禁止构造器实例化 new ...: " + expression);
        }
        if (node instanceof FunctionReference) {
            throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                    "条件表达式禁止函数引用 #fn(...): " + expression);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkSpelNode(node.getChild(i), expression);
        }
    }

    /**
     * 解释求值结果, 仅接受 Boolean/Number/String 三种返回类型。
     * <p>Boolean 直接作为命中判定; Number/String 视为非布尔条件, 记 warn 并按未命中处理;
     * 其余类型 (如任意对象/集合/Class 等) 视为返回类型越界, 抛业务异常拒绝。</p>
     *
     * @param expression 原始表达式 (用于日志与错误消息)
     * @param result     求值结果
     * @return 是否为命中
     * @throws ScrmException 返回类型越界
     */
    private boolean interpretResult(String expression, Object result) throws ScrmException {
        if (result instanceof Boolean boolValue) {
            return boolValue;
        }
        if (result instanceof Number || result instanceof String) {
            log.warn("SpEL 表达式返回非 Boolean 类型, 视为未命中: expr={}, resultType={}, result={}",
                    expression,
                    result.getClass().getSimpleName(),
                    result);
            return false;
        }
        throw new ScrmException(ScrmExceptionConstants.BAD_REQUEST,
                "表达式返回类型不受支持, 仅允许 Boolean/Number/String:"
                        + (result == null ? "null" : result.getClass().getSimpleName())
                        + ", expr=" + expression);
    }

    /**
     * 规范化表达式用于 token 扫描: 剥离子符串字面量并转小写。
     * <p>保留原有字符间隔 (不去除空白), 避免相邻标识符拼接后干扰词边界判断。</p>
     *
     * @param source 原始表达式
     * @return 规范化后的表达式
     */
    private static String normalize(String source) {
        String s = source.replaceAll("'(?:\\\\.|[^'])*'", "");
        s = s.replaceAll("\"(?:\\\\.|[^\"])*\"", "");
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 词边界感知的子串匹配 (字母/数字/下划线/美元符视为标识符, 其前后不得紧邻 token)。
     * <p>以 {@code (} 结尾的 token (如 {@code t(}) 仅要求左侧边界, 右侧为方法参数/类型名,
     * 不参与边界判断, 以便命中 {@code T(java.lang.Runtime)} 这类写法。</p>
     *
     * @param s     已规范化的表达式
     * @param token 待匹配的危险 token
     * @return true 表示命中
     */
    private static boolean matchesToken(String s, String token) {
        boolean parenTerminated = token.endsWith("(");
        int from = 0;
        while (from < s.length()) {
            int idx = s.indexOf(token, from);
            if (idx < 0) {
                return false;
            }
            boolean leftBoundary = idx == 0 || !isIdentifierChar(s.charAt(idx - 1));
            int end = idx + token.length();
            boolean rightBoundary = parenTerminated
                    || end >= s.length()
                    || !isIdentifierChar(s.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            from = end;
        }
        return false;
    }

    /**
     * 判断是否为标识符字符 (字母/数字/下划线/美元符)。
     *
     * @param c 字符
     * @return true 表示标识符字符
     */
    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 构建 SpEL 评估上下文, 将 RiskRuleContext 字段注册为变量。
     * <p>使用 {@link SimpleEvaluationContext} (只读数据绑定 + 实例方法), 允许 {@code #message.contains(...)}
     * 这类调用, 同时禁止类型引用、构造器与任意反射, 限制危险方法调用。</p>
     *
     * @param context 风险规则上下文
     * @return SpEL 评估上下文
     */
    private EvaluationContext buildEvaluationContext(RiskRuleContext context) {
        SimpleEvaluationContext evalContext = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        if (context == null) {
            return evalContext;
        }
        evalContext.setVariable("message", context.getMessage());
        evalContext.setVariable("platformType", context.getPlatformType());
        evalContext.setVariable("accountId", context.getAccountId());
        evalContext.setVariable("customerId", context.getCustomerId());
        evalContext.setVariable("eventTime", context.getEventTime());
        evalContext.setVariable("messageCountInWindow", context.getMessageCountInWindow());
        evalContext.setVariable("extra", context.getExtra());
        return evalContext;
    }

    /**
     * 风险规则上下文, 封装一次会话事件的事实数据供 SpEL 表达式引用。
     * <p>变量引用前缀为 {@code #}, 如 {@code #message}、{@code #platformType}。</p>
     *
     * @author Hsi Chu
     */
    @Data
    @Builder
    public static class RiskRuleContext {

        /** 消息内容（文本消息正文） */
        private String message;

        /** 平台类型（如 whatsapp / wechat_personal / telegram） */
        private String platformType;

        /** 账号 ID */
        private Long accountId;

        /** 客户 ID */
        private String customerId;

        /** 事件时间 */
        private LocalDateTime eventTime;

        /** 窗口内消息数（频次类规则用） */
        private Integer messageCountInWindow;

        /** 扩展事实 (key→value), 供高级表达式引用 */
        private Map<String, Object> extra;
    }
}