package com.hnkjzyxy.ab.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL 会话锁保持到同一事务提交/回滚之后，不依赖 Redis TTL。
 */
@Component
public class TransactionalMysqlLock {
    /**
     * 与原工程共享的排程写入锁名称
     */
    public static final String COURSE_SCHEDULE_SYNC = "assessment:course_schedule:sync";
    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(TransactionalMysqlLock.class);
    /**
     * 事务锁和业务事务共用的数据源
     */
    private final DataSource dataSource;

    /**
     * 创建同一数据库事务连接上的 MySQL 会话锁工具
     *
     * @param dataSource 共享数据库数据源
     */
    public TransactionalMysqlLock(DataSource dataSource) { this.dataSource = dataSource; }

    /**
     * 在当前事务连接上获取 MySQL 会话锁，并注册事务完成后的释放回调
     *
     * @param name 数据库会话锁名称
     * @throws IllegalArgumentException 锁名称无效
     * @throws IllegalStateException 未处于同连接事务、锁被占用或获取锁失败
     */
    public void acquire(String name) {
        if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > 64) {
            throw new IllegalArgumentException("MySQL 锁名须为 1 到 64 个字符");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("数据库同步锁必须在事务内获取");
        }
        // 必须复用业务事务的连接，另开连接无法保护本次事务写入。
        Connection connection = DataSourceUtils.getConnection(dataSource);
        if (!DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
            DataSourceUtils.releaseConnection(connection, dataSource);
            throw new IllegalStateException("锁与写入必须使用同一事务连接");
        }
        // MySQL GET_LOCK 可重入；同事务同名锁只取一次，防止少释放一次后泄漏到连接池。
        for (var synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof Release release
                    && release.connection == connection && release.name.equals(name)) return;
        }
        try {
            if (!execute(connection, "SELECT GET_LOCK(?, 0)", name)) {
                throw new IllegalStateException("数据库写入锁正在被其他事务持有");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("无法获取数据库同步锁", exception);
        }
        // 提交或回滚完成后再释放，锁覆盖整个事务写入窗口。
        TransactionSynchronizationManager.registerSynchronization(new Release(connection, name));
    }

    /**
     * 执行获取或释放会话锁的参数化 SQL
     *
     * @param connection 当前事务使用的数据库连接
     * @param sql 获取或释放会话锁的 SQL
     * @param name 数据库会话锁名称
     * @return SQL 返回非空的 1 时返回 true
     * @throws SQLException 数据库会话锁 SQL 执行失败
     */
    private static boolean execute(Connection connection, String sql, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(3);
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1 && !result.wasNull();
            }
        }
    }

    /**
     * 数据库事务完成后的会话锁释放回调
     */
    private final class Release implements TransactionSynchronization {
        /**
         * 持有会话锁的事务数据库连接
         */
        private final Connection connection;
        /**
         * 数据库会话锁名称
         */
        private final String name;
        /**
         * 创建事务完成后的数据库锁释放回调
         *
         * @param connection 当前事务使用的数据库连接
         * @param name 数据库会话锁名称
         */
        private Release(Connection connection, String name) { this.connection = connection; this.name = name; }

        /**
         * 事务完成后释放会话锁，释放失败时终止连接以避免连接池复用
         *
         * @param status 事务完成状态
         */
        @Override
        public void afterCompletion(int status) {
            try {
                if (!execute(connection, "SELECT RELEASE_LOCK(?)", name)) {
                    throw new SQLException("会话锁释放失败");
                }
            } catch (SQLException exception) {
                log.error("数据库锁释放失败，将终止连接，异常类型={}", exception.getClass().getName());
                try {
                    // 终止仍可能持锁的物理连接，避免连接池将该会话交给后续请求。
                    connection.abort(Runnable::run);
                } catch (SQLException | RuntimeException abortError) {
                    log.error("终止持锁数据库连接失败，异常类型={}", abortError.getClass().getName());
                }
            }
        }
    }
}
