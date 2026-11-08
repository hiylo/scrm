/*
 * Copyright(c) 2016 - Present, Hsi Chu (hiylo.org). All rights reserved.
 * Project : scrm
 * File : ScrmAiProviderServiceTest.java
 * Date : 2026/11/07 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.scrm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hiylo.scrm.config.AiProviderRegistry;
import org.hiylo.scrm.config.UserContext;
import org.hiylo.scrm.dto.ScrmAiProviderDto;
import org.hiylo.scrm.entity.ScrmAiProviderEntity;
import org.hiylo.scrm.exception.ScrmException;
import org.hiylo.scrm.exception.ScrmExceptionConstants;
import org.hiylo.scrm.repository.ScrmAiProviderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScrmAiProviderService 单元测试。
 * <p>
 * 覆盖 AI Provider 后台配置体系的配置面行为：增删改查、同名冲突、
 * apiKey「写入不读」掩码语义、默认文本/多模态标记互斥、启停、
 * 连通性探测与模型清单拉取，并统一校验任何配置变更都会触发
 * {@link AiProviderRegistry#reload()}（改完即时生效，无需重启）。
 * </p>
 * <p>
 * 探测类用例用 JDK 内置 {@link HttpServer} 起本地桩（零额外依赖），
 * 真实走一遍 WebClient GET 链路，避免 mock 静态构造的 WebClient。
 * </p>
 *
 * @author Hsi Chu
 * @since V1.0
 */
@DisplayName("ScrmAiProviderService：AI Provider 配置管理")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScrmAiProviderServiceTest {

    /** Provider 数据访问 Mock */
    @Mock
    private ScrmAiProviderRepository providerRepository;

    /** 运行时路由注册表 Mock（只验证 reload 是否被调用） */
    @Mock
    private AiProviderRegistry providerRegistry;

    /** 真实 JSON 解析器（模型清单缓存序列化/反序列化走真实实现） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 被测服务实例 */
    private ScrmAiProviderService service;

    /** 本地桩服务（探测 / 拉取模型） */
    private HttpServer stubServer;

    /** 桩服务基址（http://127.0.0.1:port） */
    private String stubBaseUrl;

    /** 桩服务返回体 */
    private volatile String stubBody = "{}";

    /** 桩服务返回状态码 */
    private volatile int stubStatus = 200;

    /** 桩收到的请求路径 */
    private final List<String> recordedPaths = new CopyOnWriteArrayList<>();

    /** 桩收到的 Authorization 头 */
    private final List<String> recordedAuth = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ScrmAiProviderService(providerRepository, providerRegistry, objectMapper);
        // 审计字段依赖请求上下文，测试中显式写入操作人
        UserContext.setUserId("1001");
        UserContext.setUsername("tester");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (stubServer != null) {
            stubServer.stop(0);
            stubServer = null;
        }
        recordedPaths.clear();
        recordedAuth.clear();
    }

    // ==================== 测试夹具 ====================

    /**
     * 构造 provider 实体。
     *
     * @param id          主键
     * @param name        名称
     * @param capability  能力位串
     * @param defaultText 是否默认文本
     * @param defaultImage 是否默认多模态
     * @return 实体
     */
    private ScrmAiProviderEntity entity(Long id, String name, String capability,
                                       boolean defaultText, boolean defaultImage) {
        ScrmAiProviderEntity e = new ScrmAiProviderEntity();
        e.setId(id);
        e.setProviderName(name);
        e.setProviderType("OPENAI_LLM");
        e.setApiBaseUrl("http://192.168.1.150:3425");
        e.setChatPath("/v1/chat/completions");
        e.setHealthPath("/v1/models");
        e.setCapability(capability);
        e.setEnabled(true);
        e.setDefaultText(defaultText);
        e.setDefaultImage(defaultImage);
        e.setPriority(100);
        e.setTemperature(0.7);
        e.setMaxTokens(500);
        e.setConnectionStatus("UNKNOWN");
        return e;
    }

    /**
     * 构造新增/更新用 DTO。
     *
     * @param name 名称
     * @return DTO
     */
    private ScrmAiProviderDto dto(String name) {
        ScrmAiProviderDto d = new ScrmAiProviderDto();
        d.setProviderName(name);
        d.setProviderType("openai_llm");
        d.setApiBaseUrl("http://192.168.1.150:3425");
        d.setApiKey("sk-plain-secret");
        d.setCapability("text,image");
        d.setDefaultModel("group/sensenova-pool");
        return d;
    }

    /**
     * 启动本地桩 HTTP 服务，记录请求路径与鉴权头。
     *
     * @throws IOException 端口绑定失败
     */
    private void startStub() throws IOException {
        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.createContext("/", this::handle);
        stubServer.start();
        stubBaseUrl = "http://127.0.0.1:" + stubServer.getAddress().getPort();
    }

    /**
     * 桩处理逻辑：记录请求并回固定响应。
     *
     * @param exchange 交换对象
     * @throws IOException 写响应失败
     */
    private void handle(HttpExchange exchange) throws IOException {
        recordedPaths.add(exchange.getRequestURI().getPath());
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null) {
            recordedAuth.add(auth);
        }
        byte[] bytes = stubBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(stubStatus, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ==================== create ====================

    @Test
    @DisplayName("create: 保存实体、补齐默认值、写入审计人并刷新路由快照")
    void create_success() {
        when(providerRepository.findByProviderName("Magpie")).thenReturn(Optional.empty());
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> {
                    ScrmAiProviderEntity e = inv.getArgument(0);
                    e.setId(9L);
                    return e;
                });
        ScrmAiProviderDto req = dto("Magpie");
        // 显式置空以验证服务端兜底默认值
        req.setPriority(null);
        req.setTemperature(null);
        req.setMaxTokens(null);
        req.setChatPath("  ");
        req.setHealthPath(null);
        req.setCapability("  ");

        ScrmAiProviderDto result = service.create(req);

        ArgumentCaptor<ScrmAiProviderEntity> captor =
                ArgumentCaptor.forClass(ScrmAiProviderEntity.class);
        verify(providerRepository, times(1)).save(captor.capture());
        ScrmAiProviderEntity saved = captor.getValue();
        assertThat(saved.getProviderName()).isEqualTo("Magpie");
        // providerType 去空格转大写
        assertThat(saved.getProviderType()).isEqualTo("OPENAI_LLM");
        assertThat(saved.getChatPath()).isEqualTo("/v1/chat/completions");
        assertThat(saved.getHealthPath()).isEqualTo("/v1/models");
        assertThat(saved.getCapability()).isEqualTo("text");
        assertThat(saved.getPriority()).isEqualTo(100);
        assertThat(saved.getTemperature()).isEqualTo(0.7);
        assertThat(saved.getMaxTokens()).isEqualTo(500);
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getConnectionStatus()).isEqualTo("UNKNOWN");
        // 审计字段取自 UserContext
        assertThat(saved.getCreatedBy()).isEqualTo("tester");
        // 配置变更必须刷新注册表
        verify(providerRegistry, times(1)).reload();
        // 返回值密钥已掩码，并带 hasApiKey 标记
        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getApiKey()).isEqualTo("****cret");
        assertThat(result.getHasApiKey()).isTrue();
    }

    @Test
    @DisplayName("create: 无请求上下文（UserContext 为空）时仍能保存，createdBy 为 null")
    void create_withoutUserContext_createdByNull() {
        UserContext.clear();
        when(providerRepository.findByProviderName("NoCtx")).thenReturn(Optional.empty());
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.create(dto("NoCtx"));

        ArgumentCaptor<ScrmAiProviderEntity> captor =
                ArgumentCaptor.forClass(ScrmAiProviderEntity.class);
        verify(providerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isNull();
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("create: 名称重复抛 CONFLICT，不落库、不刷新快照")
    void create_duplicateName_conflict() {
        when(providerRepository.findByProviderName("Magpie"))
                .thenReturn(Optional.of(entity(1L, "Magpie", "text", false, false)));

        ScrmException ex = catchThrowableOfType(() -> service.create(dto("Magpie")),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.CONFLICT);
        assertThat(ex.getMessage()).contains("Provider 名称已存在");
        verify(providerRepository, never()).save(any());
        verify(providerRegistry, never()).reload();
    }

    @Test
    @DisplayName("create: 新建设为默认文本时清除其它 provider 的默认文本标记")
    void create_defaultText_clearsOthers() {
        ScrmAiProviderEntity existing = entity(2L, "Ollama", "text", true, false);
        when(providerRepository.findByProviderName("Magpie")).thenReturn(Optional.empty());
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> {
                    ScrmAiProviderEntity e = inv.getArgument(0);
                    if (e.getId() == null) {
                        e.setId(1L);
                    }
                    return e;
                });
        when(providerRepository.findAll()).thenReturn(new ArrayList<>(List.of(existing)));
        ScrmAiProviderDto req = dto("Magpie");
        req.setDefaultText(true);

        service.create(req);

        assertThat(existing.getDefaultText()).isFalse();
        verify(providerRepository, times(1)).save(existing);
        verify(providerRegistry, times(1)).reload();
    }

    // ==================== update ====================

    @Test
    @DisplayName("update: 正常字段更新后保存，并刷新路由快照")
    void update_success() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        entity.setApiKey("sk-old");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.findByProviderName("Magpie-2")).thenReturn(Optional.empty());
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmAiProviderDto req = dto("Magpie-2");
        req.setApiKey("sk-new-secret");
        req.setPriority(5);
        req.setEnabled(false);
        req.setRemark("切换到备用接入点");

        ScrmAiProviderDto result = service.update(1L, req);

        assertThat(entity.getProviderName()).isEqualTo("Magpie-2");
        assertThat(entity.getApiKey()).isEqualTo("sk-new-secret");
        assertThat(entity.getPriority()).isEqualTo(5);
        assertThat(entity.getEnabled()).isFalse();
        assertThat(entity.getRemark()).isEqualTo("切换到备用接入点");
        verify(providerRepository, times(1)).save(entity);
        verify(providerRegistry, times(1)).reload();
        assertThat(result.getProviderName()).isEqualTo("Magpie-2");
    }

    @Test
    @DisplayName("update: apiKey 传掩码值时保留原密钥（不被掩码串污染）")
    void update_maskedApiKey_keepsOriginal() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        entity.setApiKey("sk-plain-secret");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.findByProviderName("Magpie"))
                .thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ScrmAiProviderDto req = new ScrmAiProviderDto();
        req.setProviderName("Magpie");
        req.setApiKey("****cret");
        req.setRemark("只改备注");

        service.update(1L, req);

        ArgumentCaptor<ScrmAiProviderEntity> captor =
                ArgumentCaptor.forClass(ScrmAiProviderEntity.class);
        verify(providerRepository, times(1)).save(captor.capture());
        // 掩码值不覆盖明文密钥
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-plain-secret");
        assertThat(captor.getValue().getRemark()).isEqualTo("只改备注");
        // 同名（自身）不算冲突
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("update: 名称与他人重复抛 CONFLICT，不保存")
    void update_duplicateOtherName_conflict() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.findByProviderName("Ollama"))
                .thenReturn(Optional.of(entity(2L, "Ollama", "text", false, false)));

        ScrmException ex = catchThrowableOfType(() -> service.update(1L, dto("Ollama")),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.CONFLICT);
        verify(providerRepository, never()).save(any());
        verify(providerRegistry, never()).reload();
    }

    @Test
    @DisplayName("update: provider 不存在抛 NOT_FOUND")
    void update_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.update(404L, dto("Magpie")),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        verify(providerRepository, never()).save(any());
        verify(providerRegistry, never()).reload();
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete: 删除实体并刷新路由快照")
    void delete_success() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(providerRepository, times(1)).delete(entity);
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("delete: 不存在抛 NOT_FOUND，不删除、不刷新快照")
    void delete_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.delete(404L), ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        assertThat(ex.getMessage()).contains("AI Provider 不存在");
        verify(providerRepository, never()).delete(any());
        verify(providerRegistry, never()).reload();
    }

    // ==================== setEnabled ====================

    @Test
    @DisplayName("setEnabled: 停用后 enabled=false 被持久化并刷新快照")
    void setEnabled_disable() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.setEnabled(1L, false);

        assertThat(entity.getEnabled()).isFalse();
        assertThat(result.getEnabled()).isFalse();
        verify(providerRepository, times(1)).save(entity);
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("setEnabled: 启用后 enabled=true 被持久化并刷新快照")
    void setEnabled_enable() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        entity.setEnabled(false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.setEnabled(1L, true);

        assertThat(entity.getEnabled()).isTrue();
        assertThat(result.getEnabled()).isTrue();
        verify(providerRepository, times(1)).save(entity);
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("setEnabled: 不存在抛 NOT_FOUND")
    void setEnabled_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.setEnabled(404L, true),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        verify(providerRegistry, never()).reload();
    }

    // ==================== setDefaultText / setDefaultImage ====================

    @Test
    @DisplayName("setDefaultText: 目标置默认、清除其它默认文本标记并刷新快照")
    void setDefaultText_success_clearsOthers() {
        ScrmAiProviderEntity magpie = entity(1L, "Magpie", "text,image", false, false);
        ScrmAiProviderEntity ollama = entity(2L, "Ollama", "text", true, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(magpie));
        when(providerRepository.findAll()).thenReturn(Arrays.asList(ollama, magpie));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.setDefaultText(1L);

        assertThat(magpie.getDefaultText()).isTrue();
        assertThat(ollama.getDefaultText()).isFalse();
        assertThat(result.getDefaultText()).isTrue();
        assertThat(result.getProviderName()).isEqualTo("Magpie");
        verify(providerRepository, times(1)).save(ollama);
        verify(providerRepository, times(1)).save(magpie);
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("setDefaultText: 纯 image 能力 provider 设默认文本抛 BAD_REQUEST")
    void setDefaultText_imageOnlyProvider_badRequest() {
        ScrmAiProviderEntity vision = entity(1L, "VisionOnly", "image", false, true);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(vision));

        ScrmException ex = catchThrowableOfType(() -> service.setDefaultText(1L),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("设置默认文本 Provider 需要 text 能力");
        assertThat(vision.getDefaultText()).isFalse();
        verify(providerRepository, never()).save(any());
        verify(providerRegistry, never()).reload();
    }

    @Test
    @DisplayName("setDefaultText: provider 不存在抛 NOT_FOUND")
    void setDefaultText_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.setDefaultText(404L),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        verify(providerRegistry, never()).reload();
    }

    @Test
    @DisplayName("setDefaultImage: 目标置默认多模态、清除其它标记并刷新快照")
    void setDefaultImage_success_clearsOthers() {
        ScrmAiProviderEntity magpie = entity(1L, "Magpie", "text,image", false, false);
        ScrmAiProviderEntity vision = entity(2L, "Vision", "text,image", false, true);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(magpie));
        when(providerRepository.findAll()).thenReturn(Arrays.asList(vision, magpie));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.setDefaultImage(1L);

        assertThat(magpie.getDefaultImage()).isTrue();
        assertThat(vision.getDefaultImage()).isFalse();
        assertThat(result.getDefaultImage()).isTrue();
        verify(providerRepository, times(1)).save(vision);
        verify(providerRepository, times(1)).save(magpie);
        verify(providerRegistry, times(1)).reload();
    }

    @Test
    @DisplayName("setDefaultImage: 无 image 能力 provider 抛 BAD_REQUEST")
    void setDefaultImage_noImageCapability_badRequest() {
        ScrmAiProviderEntity ollama = entity(1L, "Ollama", "text", true, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(ollama));

        ScrmException ex = catchThrowableOfType(() -> service.setDefaultImage(1L),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("需要 image 能力");
        assertThat(ollama.getDefaultImage()).isFalse();
        verify(providerRegistry, never()).reload();
    }

    // ==================== list / get ====================

    @Test
    @DisplayName("list: 按优先级顺序返回，密钥掩码且标记 hasApiKey")
    void list_masksApiKeyAndKeepsPriorityOrder() {
        ScrmAiProviderEntity first = entity(1L, "Magpie", "text,image", true, true);
        first.setApiKey("sk-abcdefgh");
        first.setPriority(10);
        ScrmAiProviderEntity second = entity(2L, "Ollama", "text", false, false);
        second.setPriority(100);
        when(providerRepository.findAllByOrderByPriorityAsc())
                .thenReturn(List.of(first, second));

        List<ScrmAiProviderDto> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getProviderName()).isEqualTo("Magpie");
        assertThat(result.get(0).getPriority()).isEqualTo(10);
        assertThat(result.get(1).getProviderName()).isEqualTo("Ollama");
        // 只回显后 4 位
        assertThat(result.get(0).getApiKey()).isEqualTo("****efgh");
        assertThat(result.get(0).getHasApiKey()).isTrue();
        assertThat(result.get(1).getApiKey()).isNull();
        assertThat(result.get(1).getHasApiKey()).isFalse();
    }

    @Test
    @DisplayName("get: 命中返回 DTO，短密钥整体掩码为 ****")
    void get_success_shortKeyFullyMasked() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text,image", true, false);
        entity.setApiKey("ab");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));

        ScrmAiProviderDto result = service.get(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getProviderName()).isEqualTo("Magpie");
        assertThat(result.getApiKey()).isEqualTo("****");
        assertThat(result.getHasApiKey()).isTrue();
        assertThat(result.getConnectionStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("get: 不存在抛 NOT_FOUND")
    void get_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.get(404L), ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        assertThat(ex.getMessage()).contains("id=404");
    }

    @Test
    @DisplayName("get: modelsCache 为合法 JSON 时解析为模型清单，脏数据回落空列表")
    void get_parsesModelsCache() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text,image", false, false);
        entity.setModelsCache("[{\"name\":\"qwen3:8b\",\"text\":true,\"image\":false},"
                + "{\"name\":\"qwen-vl\",\"text\":true,\"image\":true}]");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        ScrmAiProviderEntity dirty = entity(2L, "Dirty", "text", false, false);
        dirty.setModelsCache("not-a-json");
        when(providerRepository.findById(2L)).thenReturn(Optional.of(dirty));

        List<ScrmAiProviderDto.AiModelInfo> models = service.get(1L).getModels();

        assertThat(models).hasSize(2);
        assertThat(models.get(0).getName()).isEqualTo("qwen3:8b");
        assertThat(models.get(0).isText()).isTrue();
        assertThat(models.get(1).isImage()).isTrue();
        // 脏缓存不抛异常，返回空清单
        assertThat(service.get(2L).getModels()).isEmpty();
    }

    // ==================== testConnection ====================

    @Test
    @DisplayName("testConnection: 探测成功置 CONNECTED，带 Bearer 鉴权并记录测试时间")
    void testConnection_success() throws IOException {
        startStub();
        stubBody = "{\"data\":[{\"id\":\"qwen3:8b\"}]}";
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        // 尾部斜杠应被归一化
        entity.setApiBaseUrl(stubBaseUrl + "/");
        entity.setApiKey("sk-live");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.testConnection(1L);

        assertThat(result.getConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(entity.getConnectionStatus()).isEqualTo("CONNECTED");
        assertThat(entity.getLastTestedAt()).isNotNull();
        assertThat(recordedPaths).containsExactly("/v1/models");
        assertThat(recordedAuth).containsExactly("Bearer sk-live");
        verify(providerRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("testConnection: 探测 5xx 置 DISCONNECTED 并仍持久化状态")
    void testConnection_serverError_disconnected() throws IOException {
        startStub();
        stubStatus = 500;
        stubBody = "{\"error\":\"boom\"}";
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        entity.setApiBaseUrl(stubBaseUrl);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.testConnection(1L);

        assertThat(result.getConnectionStatus()).isEqualTo("DISCONNECTED");
        assertThat(entity.getLastTestedAt()).isNotNull();
        verify(providerRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("testConnection: 端点不可达置 DISCONNECTED（异常不外抛）")
    void testConnection_unreachable_disconnected() {
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        // 127.0.0.1:1 基本不会监听
        entity.setApiBaseUrl("http://127.0.0.1:1");
        entity.setApiKey("");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ScrmAiProviderDto result = service.testConnection(1L);

        assertThat(result.getConnectionStatus()).isEqualTo("DISCONNECTED");
        verify(providerRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("testConnection: provider 不存在抛 NOT_FOUND")
    void testConnection_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.testConnection(404L),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
    }

    // ==================== fetchModels ====================

    @Test
    @DisplayName("fetchModels: 解析 data[].id 与 modalities，并序列化缓存到 models_cache")
    void fetchModels_success() throws IOException {
        startStub();
        stubBody = "{\"data\":["
                + "{\"id\":\"qwen3:8b\"},"
                + "{\"id\":\"qwen-vl\",\"modalities\":{\"input\":[\"text\",\"image\"]}},"
                + "{\"name\":\"fallback-name\"}"
                + "]}";
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text,image", false, false);
        entity.setApiBaseUrl(stubBaseUrl);
        entity.setApiKey("sk-models");
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ScrmAiProviderDto.AiModelInfo> models = service.fetchModels(1L);

        assertThat(models).hasSize(3);
        // 无模态描述按 OpenAI 兼容默认视为文本模型
        assertThat(models.get(0).getName()).isEqualTo("qwen3:8b");
        assertThat(models.get(0).isText()).isTrue();
        assertThat(models.get(0).isImage()).isFalse();
        // 带 modalities 的多模态模型
        assertThat(models.get(1).getName()).isEqualTo("qwen-vl");
        assertThat(models.get(1).isImage()).isTrue();
        assertThat(models.get(1).isText()).isTrue();
        // id 缺失回落 name 字段
        assertThat(models.get(2).getName()).isEqualTo("fallback-name");
        assertThat(recordedPaths).containsExactly("/v1/models");
        assertThat(recordedAuth).containsExactly("Bearer sk-models");
        // 缓存已序列化落库
        assertThat(entity.getModelsCache()).contains("qwen3:8b").contains("qwen-vl");
        verify(providerRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("fetchModels: 拉取失败返回空清单，缓存写为空数组")
    void fetchModels_failure_returnsEmpty() throws IOException {
        startStub();
        stubStatus = 502;
        stubBody = "bad gateway";
        ScrmAiProviderEntity entity = entity(1L, "Magpie", "text", false, false);
        entity.setApiBaseUrl(stubBaseUrl);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<ScrmAiProviderDto.AiModelInfo> models = service.fetchModels(1L);

        assertThat(models).isEmpty();
        assertThat(entity.getModelsCache()).isEqualTo("[]");
        verify(providerRepository, times(1)).save(entity);
    }

    @Test
    @DisplayName("fetchModels: provider 不存在抛 NOT_FOUND")
    void fetchModels_notFound() {
        when(providerRepository.findById(404L)).thenReturn(Optional.empty());

        ScrmException ex = catchThrowableOfType(() -> service.fetchModels(404L),
                ScrmException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo(ScrmExceptionConstants.NOT_FOUND);
        verify(providerRepository, never()).save(any());
    }

    // ==================== 工具方法 ====================

    @Test
    @DisplayName("isMasked: 仅 **** 前缀视为掩码，null/明文不算")
    void isMasked_detectsMaskPrefix() {
        assertThat(ScrmAiProviderService.isMasked("****abcd")).isTrue();
        assertThat(ScrmAiProviderService.isMasked("****")).isTrue();
        assertThat(ScrmAiProviderService.isMasked("sk-plain")).isFalse();
        assertThat(ScrmAiProviderService.isMasked("abc****")).isFalse();
        assertThat(ScrmAiProviderService.isMasked(null)).isFalse();
    }

    @Test
    @DisplayName("setEnabled: 变更后不触碰其它 provider（只保存目标实体）")
    void setEnabled_savesOnlyTarget() {
        ScrmAiProviderEntity target = entity(1L, "Magpie", "text", false, false);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(target));
        when(providerRepository.save(any(ScrmAiProviderEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setEnabled(1L, false);

        verify(providerRepository, times(1)).save(any(ScrmAiProviderEntity.class));
        verify(providerRepository, never()).findAll();
        verify(providerRepository, never()).delete(any());
        verify(providerRegistry, times(1)).reload();
    }
}
