/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : scrm
 * File : VisionModelManager.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.feign.vision;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.hiylo.scrm.config.VisionModelProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地视觉模型进程管理器（Java 直接拉起 llama-server）。
 * <p>
 * <b>为什么不需要 Python</b>：llama-server 是原生 C++ 可执行文件（ELF 二进制）,
 * 与 Python 无关; Java 用标准 {@link ProcessBuilder} 即可启动并托管, 客户机无需
 * 安装 Python 运行时。
 * </p>
 * <p>
 * 两种形态:
 * </p>
 * <ul>
 *   <li>{@code scrm.vision.managed=true}: 启动时拉起 llama-server 子进程, 等待
 *       {@code /health} 就绪; 应用关闭时 {@link #stop()} 终止子进程（{@code @PreDestroy}）</li>
 *   <li>{@code managed=false}: 不托管进程, 直接使用配置的 {@code baseUrl}
 *       （服务器上由 systemd 等外部管理器负责生命周期）</li>
 * </ul>
 * <p>
 * <b>关键启动参数</b>（踩坑沉淀）：必须传 {@code --mmproj} 否则不是多模态;
 * 必须 {@code --reasoning off} 否则 2B/4B 小模型会长时间输出思考、超时返回空正文。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Slf4j
@Component
public class VisionModelManager {

    /** 视觉模型配置 */
    private final VisionModelProperties properties;

    /** 托管的子进程（managed=false 时恒为 null） */
    private volatile Process process;

    /**
     * 构造进程管理器。
     *
     * @param properties 视觉模型配置
     */
    public VisionModelManager(VisionModelProperties properties) {
        this.properties = properties;
    }

    /**
     * 确保模型服务可用（懒启动, 幂等）。
     * <p>
     * managed 形态: 进程不存在则拉起并等待就绪; 非 managed 形态: 只做健康探测,
     * 不可达时抛异常由调用方降级。
     * </p>
     *
     * @throws VisionModelException 启动失败或服务不可达
     */
    public synchronized void ensureAvailable() throws VisionModelException {
        if (isHealthy()) {
            return;
        }
        if (!properties.isManaged()) {
            throw new VisionModelException("视觉模型服务不可达: " + properties.getBaseUrl()
                    + "（managed=false, 请先启动外部 llama-server）");
        }
        startManagedProcess();
        awaitHealthy();
    }

    /**
     * 探测服务健康状态。
     *
     * @return 服务可用返回 true
     */
    public boolean isHealthy() {
        // 非托管模式（base-url 指向外部 OpenAI 兼容网关, 如 Magpie/LiteLLM）:
        // 这类网关没有 llama-server 的 /health, 用 /v1/models 探测更实际。
        String path = properties.isManaged() ? properties.getHealthPath() : "/v1/models";
        HttpURLConnection conn = null;
        try {
            URI uri = URI.create(trimTrailingSlash(properties.getBaseUrl()) + path);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + properties.getApiKey());
            }
            int code = conn.getResponseCode();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 拉起 llama-server 子进程。
     *
     * @throws VisionModelException 可执行文件/模型缺失或进程启动失败
     */
    private void startManagedProcess() throws VisionModelException {
        String executable = properties.getExecutable();
        if (executable == null || executable.isBlank() || !new File(executable).canExecute()) {
            throw new VisionModelException("llama-server 不可执行: " + executable);
        }
        if (properties.getModelPath() == null || !new File(properties.getModelPath()).isFile()) {
            throw new VisionModelException("模型文件不存在: " + properties.getModelPath());
        }

        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--host");
        command.add("127.0.0.1");
        command.add("--port");
        command.add(String.valueOf(properties.getPort()));
        command.add("-m");
        command.add(properties.getModelPath());
        if (properties.getMmprojPath() != null && !properties.getMmprojPath().isBlank()) {
            command.add("--mmproj");
            command.add(properties.getMmprojPath());
        }
        command.add("--n-gpu-layers");
        command.add(String.valueOf(properties.getNGpuLayers()));
        command.add("--ctx-size");
        command.add(String.valueOf(properties.getCtxSize()));
        command.add("--parallel");
        command.add(String.valueOf(properties.getParallel()));
        command.add("--threads");
        command.add(String.valueOf(properties.getThreads()));
        if (properties.isReasoningOff()) {
            command.add("--reasoning");
            command.add("off");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // llama-server 依赖同目录动态库（libllama.so / libggml*.so）, 必须显式注入
        if (properties.getLibraryPath() != null && !properties.getLibraryPath().isBlank()) {
            String existing = builder.environment().get("LD_LIBRARY_PATH");
            builder.environment().put("LD_LIBRARY_PATH",
                    existing == null || existing.isBlank()
                            ? properties.getLibraryPath()
                            : properties.getLibraryPath() + ":" + existing);
        }
        if (properties.getCudaVisibleDevices() != null
                && !properties.getCudaVisibleDevices().isBlank()) {
            builder.environment().put("CUDA_VISIBLE_DEVICES", properties.getCudaVisibleDevices());
        }

        try {
            process = builder.start();
            log.info("[视觉模型] llama-server 已拉起: pid={}, port={}, model={}",
                    process.pid(), properties.getPort(),
                    new File(properties.getModelPath()).getName());
        } catch (IOException e) {
            throw new VisionModelException("拉起 llama-server 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 等待服务就绪（轮询 /health）。
     *
     * @throws VisionModelException 超时或进程提前退出
     */
    private void awaitHealthy() throws VisionModelException {
        long deadline = System.currentTimeMillis()
                + TimeUnit.SECONDS.toMillis(properties.getStartupTimeoutSeconds());
        while (System.currentTimeMillis() < deadline) {
            if (process != null && !process.isAlive()) {
                throw new VisionModelException("llama-server 进程已退出, exitCode="
                        + process.exitValue());
            }
            if (isHealthy()) {
                log.info("[视觉模型] llama-server 就绪: {}", properties.getBaseUrl());
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new VisionModelException("等待视觉模型就绪被中断", e);
            }
        }
        throw new VisionModelException("等待视觉模型就绪超时（"
                + properties.getStartupTimeoutSeconds() + "s）: " + properties.getBaseUrl());
    }

    /**
     * 终止托管的子进程（应用关闭时调用）。
     */
    @PreDestroy
    public void stop() {
        Process p = this.process;
        if (p == null) {
            return;
        }
        if (p.isAlive()) {
            log.info("[视觉模型] 正在关闭 llama-server: pid={}", p.pid());
            p.destroy();
            try {
                if (!p.waitFor(10, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }
        }
        this.process = null;
    }

    /**
     * 去除 URL 末尾斜杠。
     *
     * @param url 原始地址
     * @return 规范化地址
     */
    private String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
