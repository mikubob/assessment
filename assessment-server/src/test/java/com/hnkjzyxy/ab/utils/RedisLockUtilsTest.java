package com.hnkjzyxy.ab.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Redis 锁所有权、租约失效与容器释放的测试
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisLockUtilsTest {
    /**
     * 验证租约失效不可恢复，且释放使用原持有者令牌
     */
    @Test
    void losingLeaseIsPermanentAndReleaseUsesSameOwnerToken() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        var executor = mock(ScheduledExecutorService.class);
        var future = mock(ScheduledFuture.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("shared-lock"), anyString(), eq(3L), eq(TimeUnit.SECONDS))).thenReturn(true);
        doReturn(future).when(executor).scheduleWithFixedDelay(any(Runnable.class), eq(1000L), eq(1000L),
                eq(TimeUnit.MILLISECONDS));
        when(redis.execute(any(RedisScript.class), eq(List.of("shared-lock")), anyString(), eq("3")))
                .thenReturn(1L, 0L, 1L);
        var locks = new RedisLockUtils(redis, executor);
        var lease = locks.tryLease("shared-lock", 3);
        assertThat(lease).isNotNull();
        lease.requireOwned();
        assertThatThrownBy(lease::requireOwned).hasMessageContaining("不属于当前实例");
        assertThatThrownBy(lease::requireOwned).hasMessageContaining("失效");
        var token = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq("shared-lock"), token.capture(), eq(3L), eq(TimeUnit.SECONDS));
        lease.close();
        lease.close();
        verify(redis).execute(any(RedisScript.class), eq(List.of("shared-lock")), eq(token.getValue()));
        verify(future).cancel(false);
        verify(executor, never()).shutdownNow();
        locks.close();
    }

    /**
     * 验证未抢到锁或 Redis 故障时不创建租约或本地降级锁
     */
    @Test
    void refusedLockOrRedisFailureNeverCreatesLeaseOrLocalFallback() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        var executor = mock(ScheduledExecutorService.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        var locks = new RedisLockUtils(redis, executor);
        assertThat(locks.tryLease("lock", 3)).isNull();
        verifyNoInteractions(executor);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"));
        assertThatThrownBy(() -> locks.tryLease("lock", 3))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class);
        assertThatThrownBy(() -> locks.tryLease("lock", 1)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 验证容器关闭时取消续期并释放活动租约
     */
    @Test
    void containerShutdownCancelsAndReleasesAllActiveLeases() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        var executor = mock(ScheduledExecutorService.class);
        var future = mock(ScheduledFuture.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        doReturn(future).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
                eq(TimeUnit.MILLISECONDS));
        var locks = new RedisLockUtils(redis, executor);
        var lease = locks.tryLease("lock", 3);
        locks.close();
        verify(future).cancel(false);
        verify(redis).execute(any(RedisScript.class), eq(List.of("lock")), anyString());
        assertThatThrownBy(lease::requireOwned).hasMessageContaining("失效");
        assertThatThrownBy(() -> locks.tryLease("new-lock", 3)).hasMessageContaining("关闭");
    }
}
