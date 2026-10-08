package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.IdentityProperties;
import com.hnkjzyxy.ab.security.IdentityResolver;
import com.hnkjzyxy.ab.security.JdbcIdentityResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import javax.sql.DataSource;

/**
 * 共享身份只读查询适配器的装配配置
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfig {
    /**
     * 创建共享身份表的只读查询适配器
     *
     * @param dataSource 共享数据库数据源
     * @param transactionManager 数据库事务管理器
     * @param properties 共享身份查询配置
     * @return 实时身份与权限读取组件
     */
    @Bean
    @ConditionalOnMissingBean(IdentityResolver.class)
    @ConditionalOnProperty(prefix = "app.identity", name = "jdbc-enabled", havingValue = "true",
            matchIfMissing = true)
    public IdentityResolver jdbcIdentityResolver(DataSource dataSource,
            PlatformTransactionManager transactionManager, IdentityProperties properties) {
        return new JdbcIdentityResolver(dataSource, transactionManager, properties);
    }
}
