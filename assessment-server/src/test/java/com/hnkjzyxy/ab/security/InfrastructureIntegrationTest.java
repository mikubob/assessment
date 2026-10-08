package com.hnkjzyxy.ab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.quartz.Scheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Boot 基础设施、认证与接口格式的集成测试
 */
@SpringBootTest(properties = {
        "absolute.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "absolute.jwt.suffix=test-auth",
        "logging.file.path=${java.io.tmpdir}/assessment-device-tests"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(InfrastructureIntegrationTest.ProbeController.class)
class InfrastructureIntegrationTest {
    /**
     * Spring MVC 测试请求执行器
     */
    @Autowired MockMvc mvc;
    /**
     * JSON 序列化映射器
     */
    @Autowired ObjectMapper mapper;
    /**
     * 学校业务时钟
     */
    @Autowired Clock clock;
    /**
     * 集成测试使用的 Quartz 调度器
     */
    @Autowired Scheduler scheduler;
    /**
     * 共享认证令牌的 JSON 字符串 Redis 模板
     */
    @Autowired @Qualifier("authTokenRedisTemplate") RedisTemplate<String, String> authTemplate;
    /**
     * 共享 Redis 当前会话读取组件
     */
    @MockitoBean RedisTokenStore tokens;
    /**
     * 实时身份查询的模拟组件
     */
    @MockitoBean IdentityResolver identities;
    /**
     * 本次集成测试使用的原始 JWT
     */
    private String jwt;

    /**
     * 构造集成测试令牌、当前会话与实时身份模拟
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @BeforeEach
    void setupIdentity() throws Exception {
        Instant now = clock.instant();
        jwt = JwtVerifierTest.legacyToken("teacher,7", now.minusSeconds(60), now.plusSeconds(3600));
        when(tokens.isCurrent(eq(7L), anyString())).thenReturn(true);
        when(identities.resolve(7L, "teacher")).thenReturn(Optional.of(identity(List.of("ROLE_teacher"))));
    }

    /**
     * 构造带指定权限的真实用户测试身份
     *
     * @param authorities 服务端读取的当前角色和权限
     * @return 测试用户身份
     */
    private IdentityResolver.Identity identity(List<String> authorities) {
        return new IdentityResolver.Identity(7L, "teacher", true, authorities);
    }

    /**
     * 构造包含单次 context-path 的集成测试请求
     *
     * @param method HTTP 请求方法
     * @param path 相对于 servlet context-path 的接口路径
     * @return MVC 测试请求构建器
     */
    private MockHttpServletRequestBuilder request(HttpMethod method, String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(method, "/api" + path)
                .contextPath("/api").servletPath(path);
    }

    /**
     * 验证测试环境不依赖外部服务启动，且 Quartz 保持停止
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void bootsWithoutExternalServicesAndKeepsQuartzStopped() throws Exception {
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Shanghai");
        assertThat(scheduler.isStarted()).isFalse();
        mvc.perform(request(HttpMethod.GET, "/actuator/health/liveness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(request(HttpMethod.GET, "/actuator/health/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verifyNoInteractions(tokens, identities);
    }

    /**
     * 验证匿名请求返回 JSON 401 和请求标识，不创建登录跳转或会话
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void returnsJson401AndRequestIdWithoutLoginRedirectOrSession() throws Exception {
        var result = mvc.perform(request(HttpMethod.POST, "/classroom/list"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id")).andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    /**
     * 验证仅接受原始 JWT、认证名称正确且失效会话被拒绝
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void acceptsRawJwtOnlyAndChecksCurrentSession() throws Exception {
        mvc.perform(request(HttpMethod.POST, "/classroom/list").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.POST, "/classroom/list").header("Authorization", jwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("teacher"));
        when(tokens.isCurrent(7L, jwt)).thenReturn(false);
        mvc.perform(request(HttpMethod.POST, "/classroom/list").header("Authorization", jwt))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证实时角色变更影响授权，并拒绝 LOCK_ONLY 进入普通接口
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void usesLiveRolesAndGloballyRejectsLockOnly() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/smart/lock/info").header("Authorization", jwt))
                .andExpect(status().isForbidden());
        when(identities.resolve(7L, "teacher")).thenReturn(Optional.of(identity(List.of("ROLE_admin"))));
        mvc.perform(request(HttpMethod.GET, "/smart/lock/info").header("Authorization", jwt))
                .andExpect(status().isOk());
        when(identities.resolve(7L, "teacher")).thenReturn(Optional.of(identity(List.of("ROLE_LOCK_ONLY"))));
        mvc.perform(request(HttpMethod.POST, "/classroom/list").header("Authorization", jwt))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证 Redis 故障不会降级为只验证 JWT 签名
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void redisFailureNeverFallsBackToSignatureOnly() throws Exception {
        when(tokens.isCurrent(7L, jwt)).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        mvc.perform(request(HttpMethod.POST, "/classroom/list").header("Authorization", jwt))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
        verifyNoInteractions(identities);
    }

    /**
     * 验证明确配置的跨域来源被允许，其他来源被拒绝
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void allowsExactConfiguredCorsAndRejectsOtherOrigins() throws Exception {
        mvc.perform(request(HttpMethod.OPTIONS, "/classroom/list").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(request(HttpMethod.OPTIONS, "/classroom/list").header("Origin", "https://unapproved.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }

    /**
     * 验证共享会话值保留旧 JSON 字符串序列化协议
     */
    @Test
    @SuppressWarnings("unchecked")
    void preservesLegacyRedisJsonStringEncoding() {
        var serializer = (org.springframework.data.redis.serializer.RedisSerializer<String>) authTemplate.getValueSerializer();
        byte[] encoded = serializer.serialize(jwt);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("\"" + jwt + "\"");
        assertThat(serializer.deserialize(encoded)).isEqualTo(jwt);
    }

    /**
     * 验证 JSON 日期时间及请求参数时间格式契约
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void keepsLocalDateTimeAndOffsetDateTimeContracts() throws Exception {
        String local = mapper.writeValueAsString(LocalDateTime.of(2026, 10, 8, 8, 20));
        assertThat(local).isEqualTo("\"2026-10-08 08:20:00\"");
        assertThat(mapper.readValue(local, LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026, 10, 8, 8, 20));
        assertThat(mapper.writeValueAsString(OffsetDateTime.parse("2026-10-08T08:20:00+08:00")))
                .contains("+08:00");
        mvc.perform(request(HttpMethod.GET, "/config-test/date").header("Authorization", jwt)
                .param("time", "2026-10-08T08:20:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.time").value("2026-10-08 08:20:00"));
    }

    /**
     * 验证参数绑定与校验失败返回统一 JSON 响应
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void formatsBindingErrorsAsJson() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/config-test/date").header("Authorization", jwt)
                .param("time", "invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(request(HttpMethod.POST, "/config-test/validation").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 仅测试资源内的探针，生产产物不包含这些接口。
     */
    @RestController
    static class ProbeController {
        /**
         * 返回认证用户名，供普通接口授权与身份名称测试使用
         *
         * @param authentication 当前已认证的用户身份
         * @return 包含成功状态和认证用户名的探针响应
         */
        @PostMapping("/classroom/list")
        Map<String, Object> classroom(org.springframework.security.core.Authentication authentication) { return Map.of("code", 200, "username", authentication.getName()); }
        /**
         * 返回管理员授权测试使用的探针响应
         *
         * @return 成功状态探针响应
         */
        @GetMapping("/smart/lock/info")
        Map<String, Object> admin() { return Map.of("code", 200); }
        /**
         * 返回绑定后的日期时间，供参数格式测试使用
         *
         * @param time 待验证格式的本地日期时间
         * @return 包含本地日期时间的探针响应
         */
        @GetMapping("/config-test/date")
        Map<String, Object> date(@RequestParam LocalDateTime time) { return Map.of("time", time); }
        /**
         * 返回通过校验的测试参数
         *
         * @param input 待验证的请求参数
         * @return 包含测试名称的探针响应
         */
        @PostMapping("/config-test/validation")
        Map<String, Object> validation(@Valid @RequestBody ValidatedInput input) { return Map.of("name", input.name()); }
        /**
         * 参数校验测试使用的输入对象
         *
         * @param name 配置项或资源名称
         */
        record ValidatedInput(@NotBlank String name) {}
    }
}
