package com.hnkjzyxy.ab.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只扫描标注 @Mapper 的接口，避免把其他接入接口注册为 Mapper。
 */
@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.hnkjzyxy.ab.mapper", annotationClass = Mapper.class)
public class MybatisPlusConfig {
    /**
     * 创建 MySQL 分页拦截器并设置分页上限
     *
     * @return MyBatis-Plus 插件拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var interceptor = new MybatisPlusInterceptor();
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
