package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

/**
 * 原服务 JWT 密钥、签名与身份解析的兼容性测试
 */
class JwtVerifierTest {
    /**
     * 用于构造旧协议测试令牌的 Base64 密钥，不用于真实环境
     */
    static final String SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    /**
     * 待验证的 JWT 验签与身份解析组件
     */
    private JwtVerifier verifier;
    /**
     * JWT 校验测试使用的固定时刻
     */
    private final Instant now = Instant.parse("2026-10-08T00:00:00Z");

    /**
     * 使用固定学校时间建立 JWT 校验测试对象
     */
    @BeforeEach
    void setup() {
        var properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setSuffix("test-auth");
        verifier = new JwtVerifier(properties, Clock.fixed(now, ZoneId.of("Asia/Shanghai")));
    }

    /**
     * 验证旧 Base64 HMAC 密钥与用户主题可被正确解析
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void readsLegacyBase64HmacSubject() throws Exception {
        var subject = verifier.verify(legacyToken("teacher,7", now.minusSeconds(60), now.plusSeconds(60)));
        assertThat(subject.userId()).isEqualTo(7);
        assertThat(subject.username()).isEqualTo("teacher");
    }

    /**
     * 验证已过期令牌被拒绝
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void rejectsExpiredToken() throws Exception {
        String token = legacyToken("teacher,7", now.minusSeconds(120), now.minusSeconds(1));
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(BadCredentialsException.class);
    }

    /**
     * 验证签名被修改的令牌被拒绝
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void rejectsChangedSignature() throws Exception {
        String token = legacyToken("teacher,7", now.minusSeconds(60), now.plusSeconds(60));
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("a") ? "b" : "a") + parts[2].substring(1);
        assertThatThrownBy(() -> verifier.verify(String.join(".", parts)))
                .isInstanceOf(BadCredentialsException.class);
    }

    /**
     * 验证非法主题身份及未来签发时间被拒绝
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void rejectsInvalidIdentityAndFutureIssuedAt() throws Exception {
        String invalidIdentity = legacyToken("teacher,0", now.minusSeconds(60), now.plusSeconds(60));
        String future = legacyToken("teacher,7", now.plusSeconds(30), now.plusSeconds(60));
        assertThatThrownBy(() -> verifier.verify(invalidIdentity)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> verifier.verify(future)).isInstanceOf(BadCredentialsException.class);
    }

    /**
     * 验证弱旧密钥不会被静默接受
     */
    @Test
    void doesNotSilentlyUseWeakLegacyKey() {
        String weak = Base64.getEncoder().encodeToString("weak".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> JwtVerifier.legacySigningKey(weak)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 不使用 JJWT 签发，直接复现旧版 Base64 密钥 + HS256 的线上线格式。
     *
     * @param subject 旧令牌的用户主题
     * @param issuedAt 令牌签发时间
     * @param expiration 令牌过期时间
     * @return 仅用于测试的原协议 JWT
     * @throws Exception 测试数据构造或请求执行失败
     */
    public static String legacyToken(String subject, Instant issuedAt, Instant expiration) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"typ\":\"JWT\",\"alg\":\"HS256\"}"
                .getBytes(StandardCharsets.UTF_8));
        String claims = "{\"sub\":\"" + subject + "\",\"iat\":" + issuedAt.getEpochSecond()
                + ",\"exp\":" + expiration.getEpochSecond() + "}";
        String payload = encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String content = header + "." + payload;
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(SECRET), "HmacSHA256"));
        return content + "." + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
    }
}
