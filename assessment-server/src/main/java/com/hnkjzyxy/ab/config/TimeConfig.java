package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.SchoolScheduleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

/**
 * 学校业务时钟配置
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {
    /**
     * 创建学校业务时区对应的服务器时钟
     *
     * @param properties 学校作息配置
     * @return 学校业务时钟
     */
    @Bean("schoolBusinessClock")
    public Clock schoolBusinessClock(SchoolScheduleProperties properties) {
        return Clock.system(ZoneId.of(properties.getZoneId()));
    }
}
