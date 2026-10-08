package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.IdentityProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import static org.assertj.core.api.Assertions.*;

/**
 * 共享身份表只读查询与实时权限读取的测试
 */
class JdbcIdentityResolverTest {
    /**
     * 身份查询测试使用的 H2 内存数据库
     */
    private EmbeddedDatabase database;
    /**
     * 带查询超时的 JDBC 查询工具
     */
    private JdbcTemplate jdbc;
    /**
     * 待验证的共享身份只读查询适配器
     */
    private JdbcIdentityResolver resolver;

    /**
     * 建立共享身份结构的 H2 内存测试数据与只读适配器
     */
    @BeforeEach
    void initializeSharedAuthFixture() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY, user_name VARCHAR(100), status INT, college VARCHAR(100))");
        jdbc.execute("CREATE TABLE sys_role (role_id BIGINT PRIMARY KEY, role_code VARCHAR(100), role_status INT)");
        jdbc.execute("CREATE TABLE sys_user_role (user_id BIGINT, role_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_menu (menu_id BIGINT PRIMARY KEY, code VARCHAR(100), status INT)");
        jdbc.execute("CREATE TABLE sys_role_menu (role_id BIGINT, menu_id BIGINT)");
        jdbc.update("INSERT INTO sys_user VALUES (7, 'teacher', 1, '信息学院')");
        jdbc.update("INSERT INTO sys_role VALUES (1, 'teacher', 1), (2, 'admin', 0)");
        jdbc.update("INSERT INTO sys_user_role VALUES (7, 1), (7, 2)");
        jdbc.update("INSERT INTO sys_menu VALUES (10, 'sys:classroom:list', 1), (11, 'disabled:permission', 0), (12, 'admin:permission', 1)");
        jdbc.update("INSERT INTO sys_role_menu VALUES (1, 10), (1, 11), (2, 12)");
        resolver = new JdbcIdentityResolver(database, new DataSourceTransactionManager(database),
                new IdentityProperties());
    }

    /**
     * 关闭本次测试使用的内存数据库
     */
    @AfterEach
    void closeDatabase() { database.shutdown(); }

    /**
     * 验证真实用户名、学院以及启用角色和菜单权限的读取
     */
    @Test
    void readsActualUsernameCollegeAndOnlyEnabledPermissions() {
        var identity = resolver.resolve(7, "teacher").orElseThrow();
        assertThat(identity.getName()).isEqualTo("teacher");
        assertThat(identity.college()).isEqualTo("信息学院");
        assertThat(identity.enabled()).isTrue();
        assertThat(identity.authorities()).containsExactly("ROLE_teacher", "sys:classroom:list");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class)).isEqualTo(1);
    }

    /**
     * 验证用户名精确匹配，并拒绝不存在或非法的用户身份
     */
    @Test
    void doesNotTrustCaseInsensitiveOrWrongUsernameAndUnknownUser() {
        assertThat(resolver.resolve(7, "Teacher")).isEmpty();
        assertThat(resolver.resolve(7, "other")).isEmpty();
        assertThat(resolver.resolve(8, "teacher")).isEmpty();
        assertThat(resolver.resolve(-1, "teacher")).isEmpty();
    }

    /**
     * 验证用户停用及角色变化即时生效，不复用旧权限缓存
     */
    @Test
    void observesDisableAndRoleChangesWithoutOldCache() {
        assertThat(resolver.resolve(7, "teacher").orElseThrow().authorities()).doesNotContain("ROLE_admin");
        jdbc.update("UPDATE sys_role SET role_status = 1 WHERE role_id = 2");
        assertThat(resolver.resolve(7, "teacher").orElseThrow().authorities())
                .contains("ROLE_admin", "admin:permission");
        jdbc.update("UPDATE sys_user SET status = 0 WHERE user_id = 7");
        var identity = resolver.resolve(7, "teacher").orElseThrow();
        assertThat(identity.enabled()).isFalse();
        assertThat(identity.authorities()).isEmpty();
    }

    /**
     * 验证共享身份表缺失时抛出错误，不返回认证降级身份
     */
    @Test
    void missingSharedTablesIsAnErrorAndNeverReturnsAnAuthenticatedFallback() {
        jdbc.execute("DROP TABLE sys_role");
        assertThatThrownBy(() -> resolver.resolve(7, "teacher"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
