package com.hnkjzyxy.ab.infrastructure.health;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import com.hnkjzyxy.ab.security.IdentityResolver;
import com.hnkjzyxy.ab.security.JwtVerifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 不访问具体用户或会话；DB/Redis 连通性由各自健康指标检查。
 */
@Component("sharedAuthHealthIndicator")
public class SharedAuthHealthIndicator implements HealthIndicator {
    /**
     * 共享 JWT 和 Redis 会话配置
     */
    private final JwtProperties jwt;
    /**
     * 实时身份与权限读取组件
     */
    private final ObjectProvider<IdentityResolver> identities;

    /**
     * 创建共享认证配置和身份适配器的就绪检查
     *
     * @param jwt 共享 JWT 和 Redis 会话配置
     * @param identities 实时身份与权限读取组件提供器
     */
    public SharedAuthHealthIndicator(JwtProperties jwt, ObjectProvider<IdentityResolver> identities) {
        this.jwt = jwt;
        this.identities = identities;
    }

    /**
     * 检查共享认证密钥、会话后缀及身份读取适配器是否就绪
     *
     * @return 不包含敏感详情的认证健康状态
     */
    @Override
    public Health health() {
        try {
            if (identities.getIfAvailable() == null || !StringUtils.hasText(jwt.getSuffix())) {
                return Health.down().build();
            }
            JwtVerifier.legacySigningKey(jwt.getSecret());
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
