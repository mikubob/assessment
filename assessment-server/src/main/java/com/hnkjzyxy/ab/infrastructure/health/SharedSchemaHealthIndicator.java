package com.hnkjzyxy.ab.infrastructure.health;

import com.hnkjzyxy.ab.config.properties.ReadinessProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import javax.sql.DataSource;

/**
 * 只检查当前库表的存在性，字段/索引/数据质量仍由专门的只读 SQL 验收。
 */
@Component("sharedSchemaHealthIndicator")
public class SharedSchemaHealthIndicator implements HealthIndicator {
    /**
     * 共享表就绪检查配置
     */
    private final ReadinessProperties properties;
    /**
     * 带查询超时的 JDBC 查询工具
     */
    private final JdbcTemplate jdbc;

    /**
     * 创建共享数据库必需表的只读就绪检查
     *
     * @param properties 共享表就绪检查配置
     * @param dataSource 共享数据库数据源
     */
    public SharedSchemaHealthIndicator(ReadinessProperties properties, DataSource dataSource) {
        this.properties = properties;
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(3);
    }

    /**
     * 只读检查当前数据库必需表的存在性，不执行数据库迁移
     *
     * @return 共享表核验的健康状态；未启用时为 UNKNOWN
     */
    @Override
    public Health health() {
        if (!properties.isSchemaCheckEnabled()) return Health.unknown().build();
        try {
            var tables = new HashSet<>(jdbc.query("""
                    SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
                    """, (rs, row) -> rs.getString(1)));
            return tables.containsAll(properties.getRequiredTables())
                    ? Health.up().build() : Health.down().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
