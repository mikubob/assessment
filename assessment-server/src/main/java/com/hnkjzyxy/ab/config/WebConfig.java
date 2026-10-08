package com.hnkjzyxy.ab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * query/form 的默认 java.time 绑定采用 ISO；业务特例须显式 @DateTimeFormat。
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {
    /**
     * 注册查询参数和表单参数使用的 ISO 日期时间格式
     *
     * @param registry Web 参数格式化器注册表
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        var registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }
}
