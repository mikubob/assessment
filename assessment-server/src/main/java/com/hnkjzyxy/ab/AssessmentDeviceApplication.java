package com.hnkjzyxy.ab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 独立设备服务入口；普通账号登录仍由原认证服务提供。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class AssessmentDeviceApplication {
    /**
     * 启动智能门锁与智能班牌独立应用
     *
     * @param args 应用启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AssessmentDeviceApplication.class, args);
    }
}
