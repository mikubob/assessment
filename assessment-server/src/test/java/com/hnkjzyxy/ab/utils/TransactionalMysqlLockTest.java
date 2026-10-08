package com.hnkjzyxy.ab.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据库事务锁获取、去重与释放的测试
 */
class TransactionalMysqlLockTest {
    /**
     * 清理事务同步状态与线程绑定的测试连接
     */
    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        java.util.List.copyOf(TransactionSynchronizationManager.getResourceMap().keySet())
                .forEach(TransactionSynchronizationManager::unbindResource);
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /**
     * 验证会话锁必须在真实事务内获取
     */
    @Test
    void requiresRealTransactionBeforeOpeningConnection() {
        var source = mock(DataSource.class);
        var lock = new TransactionalMysqlLock(source);
        assertThatThrownBy(() -> lock.acquire(TransactionalMysqlLock.COURSE_SCHEDULE_SYNC))
                .hasMessageContaining("事务内");
        verifyNoInteractions(source);
    }

    /**
     * 验证同事务同名锁不重入，且仅在事务完成后释放
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void sameTransactionDoesNotReenterMysqlLockAndReleasesOnlyAfterCompletion() throws Exception {
        var fixture = transactionalFixture(true);
        fixture.lock.acquire("shared");
        fixture.lock.acquire("shared");
        verify(fixture.connection).prepareStatement("SELECT GET_LOCK(?, 0)");
        verify(fixture.connection, never()).prepareStatement("SELECT RELEASE_LOCK(?)");
        var synchronization = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronization).hasSize(1);
        synchronization.getFirst().afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        verify(fixture.connection).prepareStatement("SELECT RELEASE_LOCK(?)");
        verify(fixture.connection, never()).abort(any());
    }

    /**
     * 验证释放失败时终止连接，避免连接池复用仍持锁的会话
     *
     * @throws Exception 测试数据构造或请求执行失败
     */
    @Test
    void releaseFailureAbortsConnectionInsteadOfReturningHeldLockToPool() throws Exception {
        var fixture = transactionalFixture(false);
        fixture.lock.acquire("shared");
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fixture.connection).abort(any());
    }

    /**
     * 构造事务绑定连接及会话锁 SQL 结果模拟
     *
     * @param releaseSuccess 模拟释放会话锁是否成功
     * @return 事务锁测试夹具
     * @throws Exception 测试数据构造或请求执行失败
     */
    private Fixture transactionalFixture(boolean releaseSuccess) throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        var acquire = mock(PreparedStatement.class);
        var release = mock(PreparedStatement.class);
        var acquired = mock(ResultSet.class);
        var released = mock(ResultSet.class);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(acquire);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(release);
        when(acquire.executeQuery()).thenReturn(acquired);
        when(release.executeQuery()).thenReturn(released);
        when(acquired.next()).thenReturn(true);
        when(acquired.getInt(1)).thenReturn(1);
        when(released.next()).thenReturn(true);
        when(released.getInt(1)).thenReturn(releaseSuccess ? 1 : 0);
        TransactionSynchronizationManager.bindResource(source, new ConnectionHolder(connection));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        return new Fixture(new TransactionalMysqlLock(source), connection);
    }

    /**
     * 事务锁测试使用的锁工具与数据库连接
     *
     * @param lock 待验证的数据库事务锁工具
     * @param connection 当前事务使用的数据库连接
     */
    private record Fixture(TransactionalMysqlLock lock, Connection connection) {}
}
