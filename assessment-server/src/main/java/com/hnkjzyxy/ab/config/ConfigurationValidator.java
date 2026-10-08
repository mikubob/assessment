package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.*;
import com.hnkjzyxy.ab.security.JwtVerifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import java.net.URI;

/**
 * 应用启动时的配置校验，检查共享认证和基础设施启用条件
 */
@Component
public class ConfigurationValidator implements ApplicationRunner {
    /**
     * 应用运行环境与配置来源
     */
    private final Environment environment;
    /**
     * 共享 JWT 和 Redis 会话配置
     */
    private final JwtProperties jwt;
    /**
     * 跨域请求配置
     */
    private final CorsProperties cors;
    /**
     * OA 客户端配置
     */
    private final OaProperties oa;
    /**
     * 课表同步配置
     */
    private final ScheduleSyncProperties sync;
    /**
     * 门锁 SDK 配置
     */
    private final SmartLockProperties smartLock;
    /**
     * 后台任务开关与周期配置
     */
    private final BackgroundTaskProperties jobs;

    /**
     * 创建应用启动时的配置校验，检查共享认证和基础设施启用条件
     *
     * @param environment 当前运行环境与配置来源
     * @param jwt 共享 JWT 和 Redis 会话配置
     * @param cors 跨域请求配置
     * @param oa OA 客户端配置
     * @param sync 课表同步配置
     * @param smartLock 门锁 SDK 配置
     * @param jobs 后台任务开关与周期配置
     */
    public ConfigurationValidator(Environment environment, JwtProperties jwt, CorsProperties cors,
                                  OaProperties oa, ScheduleSyncProperties sync, SmartLockProperties smartLock, BackgroundTaskProperties jobs) {
        this.environment = environment;
        this.jwt = jwt;
        this.cors = cors;
        this.oa = oa;
        this.sync = sync;
        this.smartLock = smartLock;
        this.jobs = jobs;
    }

    /**
     * 校验共享认证、跨域来源、外部依赖配置及后台任务启用条件
     *
     * @param args 应用启动参数
     * @throws IllegalStateException 共享配置缺失、格式不合法或启用条件不满足
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!jwt.getHeader().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("JWT 请求头名称不合法");
        }
        boolean production = environment.acceptsProfiles(Profiles.of("prod", "prd"));
        boolean configured = StringUtils.hasText(jwt.getSecret()) || StringUtils.hasText(jwt.getSuffix());
        // 生产环境必须具备完整共享认证配置；开发环境一旦配置也按同样规则校验。
        if (production || configured) {
            requireText(jwt.getSecret(), "JWT_SECRET");
            requireText(jwt.getSuffix(), "AUTH_REDIS_SUFFIX");
            try {
                JwtVerifier.legacySigningKey(jwt.getSecret());
            } catch (RuntimeException e) {
                throw new IllegalStateException("JWT_SECRET 必须为至少 32 字节的 Base64 HS256 密钥");
            }
        }
        // Origin 只包含协议、主机和端口，不能混入路径或通配符。
        for (String origin : cors.getAllowedOrigins()) {
            try {
                URI uri = URI.create(origin);
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                        || uri.getFragment() != null || !uri.getPath().isEmpty()
                        || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS 必须为明确的 HTTP(S) Origin，不含路径或通配符");
            }
        }
        // 同步依赖 OA，分页参数也需一致，避免同步保护条件与实际拉取范围不符。
        if (sync.isEnabled() && !oa.isEnabled()) {
            throw new IllegalStateException("启用课表同步前必须启用并配置 OA");
        }
        if (sync.getMinRows() > sync.getPageSize() || sync.getPageSize() != oa.getPageSize()) {
            throw new IllegalStateException("同步 page-size 必须与 OA 一致，且不得小于 min-rows");
        }
        // 门锁后台执行链必须具备 SDK 资源，不能单独打开任务开关。
        if ((jobs.isLockReconcileEnabled() || jobs.isCommandExpiryEnabled()
                || environment.getProperty("spring.quartz.auto-startup", Boolean.class, false))
                && !smartLock.isEnabled()) {
            throw new IllegalStateException("启用门锁后台任务或 Quartz 前必须启用 SMARTLOCK_ENABLED");
        }
        if (production) {
            requireText(environment.getProperty("spring.datasource.url"), "DB_URL");
            requireText(environment.getProperty("spring.datasource.username"), "DB_USERNAME");
            requireText(environment.getProperty("spring.datasource.password"), "DB_PASSWORD");
            requireText(environment.getProperty("spring.data.redis.host"), "REDIS_HOST");
        }
    }

    /**
     * 校验必填配置值，拒绝空值与未解析的占位符
     *
     * @param value 待校验的配置值
     * @param name 配置项名称
     * @throws IllegalStateException 配置为空或仍含未解析的占位符
     */
    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value) || value.contains("$" + "{")) {
            throw new IllegalStateException("请配置 " + name);
        }
    }
}
