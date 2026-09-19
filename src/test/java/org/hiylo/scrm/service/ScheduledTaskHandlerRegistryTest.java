/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : ScheduledTaskHandlerRegistryTest.java
 * Date : 2026-09-19 10:12:40
 * Author : Hsi Chu
 * Version : V1.0
 */
package org.hiylo.scrm.service;

import org.hiylo.scrm.entity.ScrmScheduledTaskEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ScheduledTaskHandlerRegistry} 单元测试 (安全回归)。
 * <p>
 * {@code scrm_scheduled_task.handler_class} 经 {@code ScrmTaskSchedulerController} 对用户可写,
 * 因此调度器<b>只能</b>查这张启动期由 Spring bean 建立的白名单, 绝不能用
 * {@code Class.forName(handlerClass)} 反射任意类名 —— 否则等于把任意代码执行权限交给任何
 * 能写该表的账号。本用例锁定白名单边界:
 * </p>
 * <ul>
 *   <li>全限定类名 / 简单类名 / bean 名三种 key 均可命中, CGLIB 代理 bean 仍注册在原始类名下</li>
 *   <li>攻击载荷 (java.lang.Runtime 等) 与未注册类名一律查不到</li>
 *   <li>白名单为空不阻断启动, 且所有查找均失败 (由调用方记 FAILED)</li>
 *   <li>{@code handlerMethod} 不在 {@link ScheduledTaskHandlerRegistry#ALLOWED_HANDLER_METHODS} 内时失败</li>
 * </ul>
 *
 * @author Hsi Chu
 */
@DisplayName("ScheduledTaskHandlerRegistry 白名单安全回归")
@ExtendWith(MockitoExtension.class)
class ScheduledTaskHandlerRegistryTest {

    /** 正常处理器 */
    public static class SampleTaskHandler implements ScheduledTaskHandler {
        @Override
        public String handle(ScrmScheduledTaskEntity task) {
            return "sample-done";
        }
    }

    /** 抛异常的处理器 (验证 invoke 原样上抛, 由调度侧记 FAILED) */
    public static class BoomTaskHandler implements ScheduledTaskHandler {
        @Override
        public String handle(ScrmScheduledTaskEntity task) {
            throw new IllegalStateException("处理器内部故障");
        }
    }

    /**
     * 以给定处理器 bean 构造注册表 (模拟容器内 ObjectProvider 的收集结果)
     */
    private ScheduledTaskHandlerRegistry registryWith(ScheduledTaskHandler... handlers) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ScheduledTaskHandler> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenReturn(Arrays.stream(handlers));
        return new ScheduledTaskHandlerRegistry(provider);
    }

    /**
     * 构造任务实体
     */
    private ScrmScheduledTaskEntity buildTask(String handlerClass, String handlerMethod) {
        ScrmScheduledTaskEntity task = new ScrmScheduledTaskEntity();
        task.setId(1L);
        task.setTaskCode("T001");
        task.setHandlerClass(handlerClass);
        task.setHandlerMethod(handlerMethod);
        return task;
    }

    // ============================================================
    // 白名单 key 命中
    // ============================================================

    @Test
    @DisplayName("find: 全限定类名 / 简单类名 / bean 名三种 key 均命中同一处理器")
    void find_resolvesAllThreeKeys() {
        SampleTaskHandler handler = new SampleTaskHandler();
        ScheduledTaskHandlerRegistry registry = registryWith(handler);

        assertThat(registry.find(SampleTaskHandler.class.getName())).contains(handler);
        assertThat(registry.find("SampleTaskHandler")).contains(handler);
        assertThat(registry.find("sampleTaskHandler")).contains(handler);
        assertThat(registry.registeredHandlers()).containsExactlyInAnyOrder(
                SampleTaskHandler.class.getName(), "SampleTaskHandler", "sampleTaskHandler");
        assertThat(registry.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("find: 含包名前缀的合法类名按简单类名兜底命中, 大小写不符仍不命中")
    void find_fallsBackToSimpleNameForPackagePrefixedKey() {
        SampleTaskHandler handler = new SampleTaskHandler();
        ScheduledTaskHandlerRegistry registry = registryWith(handler);

        assertThat(registry.find("com.example.pkg.SampleTaskHandler")).contains(handler);
        assertThat(registry.find("com.example.pkg.sampleTaskHandler")).contains(handler);
        // 首尾空白不影响命中, 但大小写写错不命中 (不做模糊匹配)
        assertThat(registry.find("  SampleTaskHandler  ")).contains(handler);
        assertThat(registry.find("com.example.pkg.Sampletaskhandler")).isEmpty();
    }

    @Test
    @DisplayName("find: CGLIB 代理过的 bean 仍注册在原始类名 key 下 (@Transactional 处理器可被调度)")
    void find_resolvesCglibProxiedBeanByOriginalClassNames() {
        SampleTaskHandler target = new SampleTaskHandler();
        // 与容器内 @Transactional 处理器的实际形态一致: CGLIB 子类代理
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        ScheduledTaskHandler proxied = (ScheduledTaskHandler) factory.getProxy();
        assertThat(proxied).isNotSameAs(target);
        assertThat(ClassUtils.getUserClass(proxied)).isEqualTo(SampleTaskHandler.class);

        ScheduledTaskHandlerRegistry registry = registryWith(proxied);

        assertThat(registry.find(SampleTaskHandler.class.getName())).contains(proxied);
        assertThat(registry.find("SampleTaskHandler")).contains(proxied);
        assertThat(registry.find("sampleTaskHandler")).contains(proxied);
        // 代理类名的 $$SpringCGLIB$$ 后缀不会泄漏进白名单 key
        assertThat(registry.registeredHandlers()).containsExactlyInAnyOrder(
                SampleTaskHandler.class.getName(), "SampleTaskHandler", "sampleTaskHandler");
    }

    @Test
    @DisplayName("find: 仓库内三个真实处理器的 bean 名与类名均可查 (任务表可按任一写法配置)")
    void find_resolvesShippedHandlers() {
        ScheduledTaskHandlerRegistry registry = registryWith(
                new AccountHealthCheckTaskHandler(null),
                new SegmentRecalculateTaskHandler(null, null),
                new WorkflowDelayResumeTaskHandler(null));

        assertThat(registry.find("accountHealthCheckTaskHandler")).isPresent();
        assertThat(registry.find("segmentRecalculateTaskHandler")).isPresent();
        assertThat(registry.find("workflowDelayResumeTaskHandler")).isPresent();
        assertThat(registry.find("AccountHealthCheckTaskHandler")).isPresent();
        assertThat(registry.find(
                "org.hiylo.scrm.service.SegmentRecalculateTaskHandler")).isPresent();
        assertThat(registry.find(
                "org.hiylo.scrm.service.WorkflowDelayResumeTaskHandler")).isPresent();
    }

    // ============================================================
    // 攻击载荷 / 未注册类名必须查不到
    // ============================================================

    @Test
    @DisplayName("find: handlerClass 填入任意类名 (Runtime / ProcessBuilder / JdbcRowSetImpl 等) 一律查不到")
    void find_rejectsArbitraryClassNames() {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());

        List<String> payloads = List.of(
                "java.lang.Runtime",
                "java.lang.ProcessBuilder",
                "Runtime",
                "ProcessBuilder",
                "java.lang.reflect.Constructor",
                "javax.script.ScriptEngineManager",
                "com.sun.rowset.JdbcRowSetImpl",
                "org.hiylo.scrm.controller.ScrmTaskSchedulerController",
                "java.lang.Class",
                "SampleTaskHandlerEvil",
                "unknownHandler");
        for (String payload : payloads) {
            Optional<ScheduledTaskHandler> found = registry.find(payload);
            assertThat(found).as("payload=%s 不应命中白名单", payload).isEmpty();
        }
        // find 仅是 Map 查询: 拒绝载荷后白名单内容不变, 也没有任何类被加载或实例化
        assertThat(registry.registeredHandlers()).hasSize(3);
    }

    @Test
    @DisplayName("find: null / 空白 handlerClass 返回空 (调用方记失败而非抛 NPE)")
    void find_rejectsBlankHandlerClass() {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.find("")).isEmpty();
        assertThat(registry.find("   ")).isEmpty();
    }

    @Test
    @DisplayName("空容器: 未注册任何处理器时构造不抛异常, isEmpty 为 true 且所有查找均失败")
    void emptyWhitelistDoesNotBreakStartupAndRejectsEverything() {
        ScheduledTaskHandlerRegistry registry = registryWith();

        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.registeredHandlers()).isEmpty();
        // 白名单为空时任何 handlerClass (含真实存在的 JDK 类) 都查不到 → 调用方记 FAILED
        assertThat(registry.find("java.lang.Runtime")).isEmpty();
        assertThat(registry.find("SampleTaskHandler")).isEmpty();
    }

    @Test
    @DisplayName("registeredHandlers: 返回不可修改视图, 运行期无法被塞入新处理器")
    void registeredHandlersIsUnmodifiable() {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());

        assertThatThrownBy(() -> registry.registeredHandlers().add("java.lang.Runtime"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.find("java.lang.Runtime")).isEmpty();
    }

    // ============================================================
    // 方法名白名单
    // ============================================================

    @Test
    @DisplayName("isAllowedMethod: 仅 handle / execute / 空白 (默认 execute) 允许")
    void isAllowedMethod_acceptsConventionMethods() {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());

        assertThat(registry.isAllowedMethod("handle")).isTrue();
        assertThat(registry.isAllowedMethod("execute")).isTrue();
        assertThat(registry.isAllowedMethod("")).isTrue();
        assertThat(registry.isAllowedMethod("   ")).isTrue();
        assertThat(registry.isAllowedMethod(null)).isTrue();
        assertThat(ScheduledTaskHandlerRegistry.ALLOWED_HANDLER_METHODS)
                .containsExactly("handle", "execute", "");
    }

    @Test
    @DisplayName("isAllowedMethod: 其他方法名 (反射式提权面) 一律拒绝")
    void isAllowedMethod_rejectsOtherMethods() {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());
        for (String method : List.of("getClass", "wait", "notify", "clone", "finalize",
                "exec", "getRuntime", "forName", "newInstance", "HANDLE")) {
            assertThat(registry.isAllowedMethod(method)).as("method=%s 应被拒绝", method).isFalse();
        }
    }

    // ============================================================
    // invoke
    // ============================================================

    @Test
    @DisplayName("invoke: 直接调用接口 handle 方法并返回处理器结果")
    void invoke_callsHandleAndReturnsResult() throws Exception {
        ScheduledTaskHandlerRegistry registry = registryWith(new SampleTaskHandler());
        ScheduledTaskHandler handler = registry.find("SampleTaskHandler").orElseThrow();

        assertThat(registry.invoke(handler, buildTask("SampleTaskHandler", "handle")))
                .isEqualTo("sample-done");
    }

    @Test
    @DisplayName("invoke: 处理器异常原样抛出, 由调度侧记 FAILED 执行结果")
    void invoke_propagatesHandlerException() {
        ScheduledTaskHandlerRegistry registry =
                registryWith(new SampleTaskHandler(), new BoomTaskHandler());
        ScheduledTaskHandler handler = registry.find("BoomTaskHandler").orElseThrow();

        assertThatThrownBy(() -> registry.invoke(handler, buildTask("BoomTaskHandler", "handle")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("处理器内部故障");
        // 另一个处理器不受影响
        assertThat(registry.find("SampleTaskHandler")).isPresent();
    }
}
