package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 原认证服务共享 Redis 当前令牌的读取组件
 */
@Component
public class RedisTokenStore {
    /**
     * 兼容原 JSON 字符串值的认证 Redis 模板
     */
    private final RedisTemplate<String, String> template;
    /**
     * 共享 JWT 和 Redis 会话配置
     */
    private final JwtProperties properties;

    /**
     * 创建原认证服务共享 Redis 当前令牌的读取组件
     *
     * @param template 兼容原 JSON 字符串编码的认证 Redis 模板
     * @param properties 共享 JWT 和 Redis 会话配置
     */
    public RedisTokenStore(@Qualifier("authTokenRedisTemplate") RedisTemplate<String, String> template,
                           JwtProperties properties) {
        this.template = template;
        this.properties = properties;
    }

    /**
     * 检查请求令牌是否与共享 Redis 中当前会话令牌一致
     *
     * @param userId 真实用户 ID
     * @param token 请求令牌
     * @return 令牌仍为当前会话时返回 true
     * @throws org.springframework.dao.DataAccessException 共享 Redis 会话读取失败
     */
    public boolean isCurrent(long userId, String token) {
        String stored = template.opsForValue().get(properties.getSuffix() + "-" + userId);
        return token.equals(stored);
    }
}
