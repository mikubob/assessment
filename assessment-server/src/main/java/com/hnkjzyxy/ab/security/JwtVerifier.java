package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;

/**
 * 原认证服务 HS256 JWT 的验签与身份解析组件
 */
@Component
public class JwtVerifier {
    /**
     * 共享 JWT 和 Redis 会话配置
     */
    private final JwtProperties properties;
    /**
     * 学校业务时钟
     */
    private final Clock clock;

    /**
     * 创建原认证服务 HS256 JWT 的验签与身份解析组件
     *
     * @param properties 共享 JWT 和 Redis 会话配置
     * @param schoolBusinessClock 学校业务时钟
     */
    public JwtVerifier(JwtProperties properties, Clock schoolBusinessClock) {
        this.properties = properties;
        this.clock = schoolBusinessClock;
    }

    /**
     * 按原服务 Base64 语义解码并校验 HS256 密钥强度
     *
     * @param base64Secret 原服务使用的 Base64 编码密钥
     * @return 兼容原服务的 HMAC 签名密钥
     * @throws IllegalArgumentException 密钥解码后长度不足 HS256 要求
     */
    public static SecretKey legacySigningKey(String base64Secret) {
        byte[] bytes = Decoders.BASE64.decode(base64Secret);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("HS256 密钥解码后至少需要 32 字节");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 校验 JWT 签名、算法、时间声明并解析主题身份
     *
     * @param token 请求令牌
     * @return 经验证的令牌主题身份
     * @throws org.springframework.security.authentication.AuthenticationServiceException 共享认证配置缺失或密钥不合法
     * @throws org.springframework.security.authentication.BadCredentialsException 令牌签名、算法、时间声明或主题身份无效
     */
    public Subject verify(String token) {
        if (!StringUtils.hasText(properties.getSecret()) || !StringUtils.hasText(properties.getSuffix())) {
            throw new AuthenticationServiceException("认证共享配置尚未完成");
        }
        SecretKey key;
        try {
            key = legacySigningKey(properties.getSecret());
        } catch (RuntimeException e) {
            throw new AuthenticationServiceException("认证密钥配置不合法");
        }
        try {
            // 令牌时间校验统一使用学校时钟，便于固定时间测试。
            var signed = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant()))
                    .build().parseSignedClaims(token);
            if (!"HS256".equals(signed.getHeader().getAlgorithm())) {
                throw new BadCredentialsException("令牌算法不受支持");
            }
            // 时间声明必须齐全，不能仅凭签名有效就接受令牌。
            var claims = signed.getPayload();
            if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
                throw new BadCredentialsException("令牌已过期");
            }
            if (claims.getIssuedAt() == null || claims.getIssuedAt().toInstant().isAfter(clock.instant())) {
                throw new BadCredentialsException("令牌签发时间不合法");
            }
            String subject = claims.getSubject();
            // 最后一个逗号分隔用户 ID，保留前面的完整用户名。
            int separator = subject == null ? -1 : subject.lastIndexOf(',');
            if (separator <= 0 || separator == subject.length() - 1) {
                throw new BadCredentialsException("令牌身份格式不合法");
            }
            long userId = Long.parseLong(subject.substring(separator + 1));
            if (userId <= 0) throw new BadCredentialsException("令牌用户不合法");
            return new Subject(userId, subject.substring(0, separator));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadCredentialsException("令牌无效");
        }
    }

    /**
     * JWT 主题中解析得到的用户身份
     *
     * @param userId 真实用户 ID
     * @param username 真实用户名
     */
    public record Subject(long userId, String username) {}
}
