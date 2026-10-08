package com.hnkjzyxy.ab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnkjzyxy.ab.config.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 认证身份接入边界的测试
 */
class JwtAuthenticationFilterTest {
    /**
     * 验证身份适配器缺失时，即使签名有效也拒绝建立认证
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    @SuppressWarnings("unchecked")
    void refusesValidSignatureWhenIdentityAdapterIsMissing() throws Exception {
        Instant now = Instant.parse("2026-10-08T00:00:00Z");
        var properties = new JwtProperties();
        properties.setSecret(JwtVerifierTest.SECRET);
        properties.setSuffix("test-auth");
        var tokenStore = mock(RedisTokenStore.class);
        ObjectProvider<IdentityResolver> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        var verifier = new JwtVerifier(properties, Clock.fixed(now, ZoneId.of("Asia/Shanghai")));
        var filter = new JwtAuthenticationFilter(properties, verifier, tokenStore, provider,
                new SecurityResponseWriter(new ObjectMapper()));
        var request = new MockHttpServletRequest("POST", "/api/classroom/list");
        request.setServletPath("/classroom/list");
        request.addHeader("Authorization", JwtVerifierTest.legacyToken("teacher,7",
                now.minusSeconds(60), now.plusSeconds(60)));
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> continued.set(true));
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(continued.get()).isFalse();
        verifyNoInteractions(tokenStore);
    }
}
