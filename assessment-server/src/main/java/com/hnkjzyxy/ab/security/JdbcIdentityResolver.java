package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.IdentityProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.LinkedHashSet;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * 每次认证从共享表读取有效身份和权限，无密码查询、缓存、写入或用户管理 API。
 * 查询使用同一个只读可重复读事务，避免多条查询读取不同用户/角色快照。
 */
public final class JdbcIdentityResolver implements IdentityResolver {
    /**
     * 带查询超时的 JDBC 查询工具
     */
    private final JdbcTemplate jdbc;
    /**
     * 读取身份及权限时使用的只读事务模板
     */
    private final TransactionTemplate transaction;

    /**
     * 创建共享身份权限表的 JDBC 只读适配器
     *
     * @param dataSource 共享数据库数据源
     * @param transactionManager 数据库事务管理器
     * @param properties 共享身份查询配置
     */
    public JdbcIdentityResolver(DataSource dataSource, PlatformTransactionManager transactionManager,
                                IdentityProperties properties) {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(properties.getQueryTimeoutSeconds());
        // 用户与权限在独立只读事务中查询，保持同一数据快照。
        transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(properties.getQueryTimeoutSeconds() * 3);
    }

    /**
     * 在独立只读事务中解析共享用户身份和当前权限
     *
     * @param userId 真实用户 ID
     * @param username 真实用户名
     * @return 匹配的用户身份；无匹配身份时为空
     * @throws org.springframework.dao.DataAccessException 共享身份表查询失败
     */
    @Override
    public Optional<Identity> resolve(long userId, String username) {
        if (userId <= 0 || username == null || username.isBlank()) return Optional.empty();
        return transaction.execute(status -> readIdentity(userId, username));
    }

    /**
     * 读取共享用户以及启用角色和菜单权限
     *
     * @param userId 真实用户 ID
     * @param username 真实用户名
     * @return 共享表中的实时身份
     */
    private Optional<Identity> readIdentity(long userId, String username) {
        var users = jdbc.query("""
                SELECT user_id, user_name, status, college FROM sys_user WHERE user_id = ?
                """, (rs, row) -> new Identity(rs.getLong("user_id"), rs.getString("user_name"),
                rs.getInt("status") == 1, rs.getString("college"), java.util.List.of()), userId);
        // 用 Java 精确比较用户名，避免数据库不区分大小写的排序规则放宽身份匹配。
        if (users.size() != 1 || !username.equals(users.getFirst().username())) return Optional.empty();
        var user = users.getFirst();
        // 停用用户不再读取权限，交由认证过滤器拒绝该身份。
        if (!user.enabled()) return Optional.of(user);

        // 合并启用角色和菜单权限，去重并保留读取顺序。
        var authorities = new LinkedHashSet<String>();
        jdbc.query("""
                SELECT DISTINCT r.role_code FROM sys_role r
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? AND r.role_status = 1
                """, (rs, row) -> rs.getString(1), userId).stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> "ROLE_" + code).forEach(authorities::add);
        // 菜单权限同时受角色启用状态约束，防止停用角色继续提供权限。
        jdbc.query("""
                SELECT DISTINCT m.code FROM sys_menu m
                JOIN sys_role_menu rm ON rm.menu_id = m.menu_id
                JOIN sys_role r ON r.role_id = rm.role_id AND r.role_status = 1
                JOIN sys_user_role ur ON ur.role_id = r.role_id
                WHERE ur.user_id = ? AND m.status = 1
                """, (rs, row) -> rs.getString(1), userId).stream()
                .filter(code -> code != null && !code.isBlank()).forEach(authorities::add);
        return Optional.of(new Identity(user.userId(), user.username(), true, user.college(),
                java.util.List.copyOf(authorities)));
    }
}
