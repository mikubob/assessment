package com.hnkjzyxy.ab.infrastructure.health;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import com.hnkjzyxy.ab.config.properties.ReadinessProperties;
import com.hnkjzyxy.ab.security.IdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Status;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 共享认证及数据库表就绪检查的测试
 */
class ReadinessHealthTest {
    /**
     * 验证认证就绪检查要求身份适配器及合法共享配置，且不查询具体用户
     */
    @Test
    @SuppressWarnings("unchecked")
    void authReadinessRequiresAdapterSuffixAndValidSharedKeyWithoutResolvingUsers() {
        var jwt = new JwtProperties();
        ObjectProvider<IdentityResolver> provider = mock(ObjectProvider.class);
        var identity = mock(IdentityResolver.class);
        var health = new SharedAuthHealthIndicator(jwt, provider);
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        when(provider.getIfAvailable()).thenReturn(identity);
        jwt.setSuffix("shared-auth");
        jwt.setSecret("invalid");
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        jwt.setSecret("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        assertThat(health.health().getDetails()).isEmpty();
        verifyNoInteractions(identity);
    }

    /**
     * 验证关闭表结构检查时不建立数据库连接
     */
    @Test
    void disabledSchemaCheckDoesNotConnectToDatabase() {
        var dataSource = mock(DataSource.class);
        var health = new SharedSchemaHealthIndicator(new ReadinessProperties(), dataSource);
        assertThat(health.health().getStatus()).isEqualTo(Status.UNKNOWN);
        verifyNoInteractions(dataSource);
    }

    /**
     * 验证缺少必需表或数据库故障时返回未就绪
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void schemaCheckFailsOnMissingTablesAndDatabaseErrors() throws Exception {
        var properties = new ReadinessProperties();
        properties.setSchemaCheckEnabled(true);
        properties.setRequiredTables(List.of("sys_user", "sys_role"));
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString(1)).thenReturn("sys_user");
        var health = new SharedSchemaHealthIndicator(properties, source);
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
        when(result.next()).thenReturn(true, true, false);
        when(result.getString(1)).thenReturn("sys_user", "sys_role");
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
        when(source.getConnection()).thenThrow(new java.sql.SQLException("fixture offline"));
        assertThat(health.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
