package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.SmartLockProperties;
import com.hnkjzyxy.ab.infrastructure.sdk.DoorAccessRuntime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 直接管理 SDK 使用的 Netty 资源，不再额外创建未接入 SDK 的线程组。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "smartlock", name = "enabled", havingValue = "true")
public class NettyConfig {
    /**
     * 创建实际门锁 SDK 运行资源，由 Spring 在销毁时关闭
     *
     * @param properties 门锁 SDK 配置
     * @return 门锁 SDK 运行资源
     */
    @Bean(destroyMethod = "close")
    public DoorAccessRuntime doorAccessRuntime(SmartLockProperties properties) {
        return new DoorAccessRuntime(properties.getShutdownTimeout());
    }
}
