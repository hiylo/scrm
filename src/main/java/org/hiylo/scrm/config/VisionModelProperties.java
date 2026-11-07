/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : VisionModelProperties.java
 * Date : 2026/10/23 18:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地视觉模型（证照识别）配置。
 * <p>
 * 绑定 {@code scrm.vision.*}。两种部署形态由 {@link #managed} 切换:
 * </p>
 * <ul>
 *   <li><b>managed=true</b>（客户机自包含形态）: 由 scrm 进程用
 *       {@link ProcessBuilder} 拉起 llama-server 子进程, 应用退出时一并终止</li>
 *   <li><b>managed=false</b>（服务器形态）: 连接外部已托管的 llama-server
 *       （如 systemd 管理）, scrm 只做 HTTP 调用</li>
 * </ul>
 * <p>
 * <b>推荐模型档位（实测）</b>：Qwen3-VL-4B-Instruct Q4_K_M + mmproj-Q8 —— 8G 显存
 * （实测占用 ~4.7GB）下识别 4B 档, 中文证照字段全对、约 1-4 秒/张; 8B 需 16G 卡;
 * 2B 用于 2~4G 小显存客户机兜底。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@Data
@ConfigurationProperties(prefix = "scrm.vision")
public class VisionModelProperties {

    /** 是否启用本地视觉识别（与 AI Provider 多模态通道互为兜底，由路由决定优先级） */
    private boolean enabled = false;

    /** 是否由 scrm 托管模型进程（true=Java 用 ProcessBuilder 拉起; false=连外部服务） */
    private boolean managed = false;

    /** llama-server HTTP 地址（managed=true 时为本地回环端口） */
    private String baseUrl = "http://127.0.0.1:8096";

    /** 对话补全路径 */
    private String chatPath = "/v1/chat/completions";

    /** 健康检查路径 */
    private String healthPath = "/health";

    /** 模型别名（llama-server 的 --alias, 仅日志/展示用） */
    private String model = "qwen3vl-4b";

    /** 推理温度（证照抽取用低温, 减少随机性） */
    private double temperature = 0.1;

    /** 单次生成最大 token 数 */
    private int maxTokens = 1200;

    /** 连接超时（秒） */
    private int connectTimeoutSeconds = 10;

    /** 读取超时（秒, 视觉推理含图片编码, 默认 180） */
    private int readTimeoutSeconds = 180;

    /** 单文件大小上限（MB） */
    private int maxFileSizeMb = 20;

    // ---------------- 以下仅 managed=true 时生效 ----------------

    /** llama-server 可执行文件绝对路径 */
    private String executable = "/opt/llama.cpp/build/bin/llama-server";

    /** 动态库搜索路径（llama-server 依赖同目录 .so, 必须显式设置） */
    private String libraryPath = "/opt/llama.cpp/build/bin";

    /** 模型权重文件（GGUF）路径 */
    private String modelPath = "";

    /** 视觉投影器（mmproj GGUF）路径 */
    private String mmprojPath = "";

    /** 监听端口（managed 模式下由 scrm 指定） */
    private int port = 8096;

    /** 卸载到 GPU 的层数（-1=全部, 0=纯 CPU） */
    private int nGpuLayers = 99;

    /** 上下文大小 */
    private int ctxSize = 8192;

    /** 并发槽位 */
    private int parallel = 2;

    /** 推理线程数（纯 CPU 时生效） */
    private int threads = 16;

    /** 是否关闭模型思考模式（证照抽取必须关闭, 否则 reasoning 吃光 token） */
    private boolean reasoningOff = true;

    /** 是否限制 GPU 设备（CUDA_VISIBLE_DEVICES; 空=不限制） */
    private String cudaVisibleDevices = "";

    /** 启动后等待服务就绪的最长秒数 */
    private int startupTimeoutSeconds = 120;

    /**
     * 访问令牌（可空）。
     * <p>
     * 本地 llama-server 不需要鉴权；但把 {@code base-url} 指向 OpenAI 兼容网关
     * （如 NAS Magpie / LiteLLM）时必须携带，否则 401。
     * <b>只从环境变量注入，不落库、不入日志</b>。
     * </p>
     */
    private String apiKey = "";

    /**
     * 是否在请求体里注入 {@code enable_thinking=false}。
     * <p>
     * llama-server 用启动参数 {@code --reasoning off} 关闭思考；而 OpenAI 兼容网关
     * （sensenova / deepseek 等推理模型）没有该启动参数，只能在请求体里关，
     * 否则 reasoning 会吃掉 max_tokens，正文返回空。默认开启。
     * </p>
     */
    private boolean enableThinking = false;
}
