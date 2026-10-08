package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.interceptor.RequestTraceFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 请求日志追踪过滤器配置
 */
@Configuration(proxyBeanMethods = false)
public class RequestTraceConfig {
    /**
     * 注册请求追踪过滤器，并设置其执行顺序
     *
     * @return 请求追踪过滤器注册信息
     */
    @Bean
    public FilterRegistrationBean<RequestTraceFilter> requestTraceFilter() {
        var registration = new FilterRegistrationBean<>(new RequestTraceFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
