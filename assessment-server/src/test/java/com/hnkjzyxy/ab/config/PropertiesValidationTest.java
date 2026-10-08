package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.*;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/**
 * 启动配置、参数约束与启用条件的测试
 */
class PropertiesValidationTest {
    /**
     * 配置约束测试使用的校验器工厂
     */
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    /**
     * 配置属性校验器
     */
    private final Validator validator = FACTORY.getValidator();

    /**
     * 释放配置校验测试使用的校验器工厂
     */
    @AfterAll
    static void closeValidatorFactory() { FACTORY.close(); }

    /**
     * 验证 OA 启用后才要求完整凭据
     */
    @Test
    void validatesOaCredentialsOnlyWhenEnabled() {
        var properties = new OaProperties();
        assertThat(validator.validate(properties)).isEmpty();
        properties.setEnabled(true);
        assertThat(validator.validate(properties)).isNotEmpty();
    }

    /**
     * 验证非法 Cron 及倒置可信覆盖日期被拒绝
     */
    @Test
    void rejectsInvalidCronAndUnconfirmedCoverageDates() {
        var sync = new ScheduleSyncProperties();
        sync.setCron("invalid");
        assertThat(validator.validate(sync)).isNotEmpty();
        var school = new SchoolScheduleProperties();
        school.setCoverageConfirmed(true);
        school.setCoverageStart(LocalDate.of(2026, 10, 8));
        school.setCoverageEnd(LocalDate.of(2026, 10, 7));
        assertThat(validator.validate(school)).isNotEmpty();
    }

    /**
     * 验证生产环境缺少共享认证配置时拒绝启动
     */
    @Test
    void productionDoesNotStartWithBlankSharedAuthConfiguration() {
        var env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("https://example.edu"));
        var configuration = new ConfigurationValidator(env, new JwtProperties(), cors,
                new OaProperties(), new ScheduleSyncProperties(), new SmartLockProperties(), new BackgroundTaskProperties());
        assertThatThrownBy(() -> configuration.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
    }

    /**
     * 验证跨域通配符和未启用 OA 的同步配置被拒绝
     */
    @Test
    void rejectsWildcardCorsAndSyncWithoutOa() {
        var cors = new CorsProperties();
        cors.setAllowedOrigins(List.of("*"));
        var sync = new ScheduleSyncProperties();
        var configuration = new ConfigurationValidator(new MockEnvironment(), new JwtProperties(),
                cors, new OaProperties(), sync, new SmartLockProperties(), new BackgroundTaskProperties());
        assertThatThrownBy(() -> configuration.run(new DefaultApplicationArguments()))
                .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        cors.setAllowedOrigins(List.of("https://example.edu"));
        sync.setEnabled(true);
        assertThatThrownBy(() -> configuration.run(new DefaultApplicationArguments()))
                .hasMessageContaining("OA");
    }

    /**
     * 验证门锁后台任务不能脱离 SDK 基础设施启用
     */
    @Test
    void refusesEnabledLockJobsWithoutSdkInfrastructure() {
        var jobs = new BackgroundTaskProperties();
        jobs.setLockReconcileEnabled(true);
        var configuration = new ConfigurationValidator(new MockEnvironment(), new JwtProperties(),
                new CorsProperties(), new OaProperties(), new ScheduleSyncProperties(),
                new SmartLockProperties(), jobs);
        assertThatThrownBy(() -> configuration.run(new DefaultApplicationArguments()))
                .hasMessageContaining("SMARTLOCK_ENABLED");
    }
}
