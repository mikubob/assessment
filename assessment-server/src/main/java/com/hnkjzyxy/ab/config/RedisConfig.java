package com.hnkjzyxy.ab.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 共享认证令牌与独立业务缓存的 Redis 序列化配置
 */
@EnableCaching
@Configuration(proxyBeanMethods = false)
public class RedisConfig {
    /**
     * 共享认证只读取原 JSON 字符串值，不加载旧缓存中的任意 Java 类型。
     *
     * @param factory Redis 连接工厂
     * @return 共享认证令牌读取模板
     */
    @Bean("authTokenRedisTemplate")
    public RedisTemplate<String, String> authTokenRedisTemplate(RedisConnectionFactory factory) {
        var template = new RedisTemplate<String, String>();
        template.setConnectionFactory(factory);
        var keys = new StringRedisSerializer();
        // 旧会话值是带 JSON 引号的字符串，不能改为裸字符串序列化。
        var values = new Jackson2JsonRedisSerializer<>(String.class);
        template.setKeySerializer(keys);
        template.setHashKeySerializer(keys);
        template.setValueSerializer(values);
        template.setHashValueSerializer(values);
        return template;
    }

    /**
     * 创建限定反序列化类型范围的独立业务缓存序列化器
     *
     * @param mapper JSON 序列化映射器
     * @return 独立业务缓存的 JSON 序列化器
     */
    @Bean
    public GenericJackson2JsonRedisSerializer deviceJsonSerializer(ObjectMapper mapper) {
        // 限定可还原的类型范围，避免加载旧缓存中的任意 Java 类型。
        var allowedTypes = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.hnkjzyxy.ab.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
        // 只修改业务缓存专用副本，避免影响接口 JSON 的序列化配置。
        var cacheMapper = mapper.copy();
        cacheMapper.activateDefaultTyping(allowedTypes, ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(cacheMapper);
    }

    /**
     * 新服务业务缓存使用隔离命名空间，不读取 authority/users 等旧类型缓存。
     *
     * @param factory Redis 连接工厂
     * @param serializer 独立业务缓存序列化器
     * @return 独立业务 Redis 操作模板
     */
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory,
                                                      GenericJackson2JsonRedisSerializer serializer) {
        var template = new RedisTemplate<String, Object>();
        template.setConnectionFactory(factory);
        var keys = new StringRedisSerializer();
        template.setKeySerializer(keys);
        template.setHashKeySerializer(keys);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        return template;
    }

    /**
     * 配置独立缓存前缀、过期时间及序列化方式
     *
     * @param properties Spring 缓存配置
     * @param serializer 独立业务缓存序列化器
     * @return Redis 缓存配置
     */
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration(CacheProperties properties,
                                                           GenericJackson2JsonRedisSerializer serializer) {
        var settings = properties.getRedis();
        var configuration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .computePrefixWith(name -> settings.getKeyPrefix() + name + "::");
        if (settings.getTimeToLive() != null) configuration = configuration.entryTtl(settings.getTimeToLive());
        return configuration.disableCachingNullValues();
    }
}
