package com.hnkjzyxy.ab.utils;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 字符串锁令牌 + 原子比较释放/续期；不访问旧认证或业务缓存。
 */
@Component
public class RedisLockUtils {
    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(RedisLockUtils.class);
    /**
     * 比较持有者令牌后释放锁的 Lua 脚本
     */
    private static final RedisScript<Long> RELEASE = script("lua/release_lock.lua");
    /**
     * 比较持有者令牌后延长租约的 Lua 脚本
     */
    private static final RedisScript<Long> RENEW = script("lua/renew_lock.lua");
    /**
     * 字符串序列化的 Redis 操作模板
     */
    private final StringRedisTemplate redis;
    /**
     * 所有锁租约共用的续期执行器
     */
    private final ScheduledExecutorService executor;
    /**
     * 当前管理器持有的活动锁租约
     */
    private final Set<LockLease> leases = ConcurrentHashMap.newKeySet();
    /**
     * 锁管理器是否已开始关闭
     */
    private boolean shuttingDown;

    /**
     * 创建基于 Redis 字符串令牌的分布式锁与续期租约工具
     *
     * @param redis 字符串序列化的 Redis 操作模板
     * @param executor 共用的锁租约续期执行器
     */
    public RedisLockUtils(StringRedisTemplate redis,
            @Qualifier("lockRenewalExecutor") ScheduledExecutorService executor) {
        this.redis = redis;
        this.executor = executor;
    }

    /**
     * 尝试立即获取带过期时间的 Redis 锁，不等待其他持有者
     *
     * @param key 锁名称
     * @param ttlSeconds 租约有效期，单位为秒
     * @return 锁令牌；未取得锁时返回 null
     * @throws IllegalArgumentException 锁名称或有效期无效
     */
    public String tryLock(String key, long ttlSeconds) {
        validate(key, ttlSeconds);
        // 每次抢锁生成独立持有者令牌，释放与续期时均须原样比较。
        String token = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttlSeconds, TimeUnit.SECONDS))
                ? token : null;
    }

    /**
     * 抢不到返回 null；Redis 故障向上抛出，不降级为本地锁。
     *
     * @param key 锁名称
     * @param ttlSeconds 租约有效期，单位为秒
     * @return 锁租约；未取得锁时返回 null
     * @throws IllegalArgumentException 租约参数无效
     * @throws IllegalStateException 锁管理器正在关闭
     */
    public synchronized LockLease tryLease(String key, long ttlSeconds) {
        if (shuttingDown) throw new IllegalStateException("锁管理器正在关闭");
        if (ttlSeconds < 3) throw new IllegalArgumentException("续期锁有效期不得低于 3 秒");
        String token = tryLock(key, ttlSeconds);
        if (token == null) return null;
        try {
            var lease = new LockLease(key, token, ttlSeconds);
            leases.add(lease);
            return lease;
        } catch (RuntimeException exception) {
            // 已抢到锁但租约创建失败时立即释放，不让锁空占到过期。
            unlock(key, token);
            throw exception;
        }
    }

    /**
     * 原子比较令牌后延长租约，拒绝延长其他持有者的锁
     *
     * @param key 锁名称
     * @param token 锁持有者令牌
     * @param ttlSeconds 租约有效期，单位为秒
     * @return 令牌匹配且续期成功时返回 true
     * @throws IllegalArgumentException 锁名称或有效期无效
     */
    public boolean renew(String key, String token, long ttlSeconds) {
        validate(key, ttlSeconds);
        if (token == null || token.isBlank()) return false;
        return Long.valueOf(1).equals(redis.execute(RENEW, List.of(key), token, Long.toString(ttlSeconds)));
    }

    /**
     * 原子比较令牌后释放自身持有的锁，失败时等待 TTL 过期
     *
     * @param key 锁名称
     * @param token 锁持有者令牌
     */
    public void unlock(String key, String token) {
        if (token == null) return;
        try {
            redis.execute(RELEASE, List.of(key), token);
        } catch (RuntimeException exception) {
            log.warn("Redis 锁释放失败，等待租约过期，异常类型={}", exception.getClass().getName());
        }
    }

    /**
     * 关闭锁管理器，停止并释放全部活动租约
     */
    @PreDestroy
    public synchronized void close() {
        shuttingDown = true;
        // 关闭租约会修改活动集合，遍历快照避免遗漏或迭代冲突。
        List.copyOf(leases).forEach(LockLease::close);
    }

    /**
     * 可续期的分布式锁租约，丢失所有权后永久失效
     */
    public final class LockLease implements AutoCloseable {
        /**
         * 锁名称
         */
        private final String key;
        /**
         * 当前锁持有者的令牌
         */
        private final String token;
        /**
         * 锁租约有效期，单位为秒
         */
        private final long ttlSeconds;
        /**
         * 租约定期续期任务
         */
        private final ScheduledFuture<?> renewal;
        /**
         * 租约是否已永久失去所有权
         */
        private boolean lost;
        /**
         * 当前租约是否已经关闭
         */
        private boolean closed;

        /**
         * 创建锁租约并注册定期续期任务
         *
         * @param key 锁名称
         * @param token 锁持有者令牌
         * @param ttlSeconds 租约有效期，单位为秒
         */
        private LockLease(String key, String token, long ttlSeconds) {
            this.key = key;
            this.token = token;
            this.ttlSeconds = ttlSeconds;
            // 在有效期的三分之一处续期，留出处理网络延迟的时间。
            long interval = ttlSeconds * 1000 / 3;
            renewal = executor.scheduleWithFixedDelay(this::renewInBackground, interval, interval,
                    TimeUnit.MILLISECONDS);
        }

        /**
         * 写入和提交前校验；一旦失去租约便永久失效。业务仍须配合同事务数据库锁。
         *
         * @throws IllegalStateException 租约已关闭、已失效或已不属于当前实例
         */
        public synchronized void requireOwned() {
            if (closed || lost) throw new IllegalStateException("锁租约已失效");
            try {
                // 所有权一旦丢失就永久失效，禁止旧持有者恢复写入。
                if (!renew(key, token, ttlSeconds)) {
                    lost = true;
                    throw new IllegalStateException("锁已不属于当前实例");
                }
            } catch (RuntimeException exception) {
                lost = true;
                throw exception;
            }
        }

        /**
         * 定期续期租约，失去所有权时取消后续续期
         */
        private void renewInBackground() {
            try {
                requireOwned();
            } catch (RuntimeException exception) {
                renewal.cancel(false);
                log.warn("锁租约续期失败，后续使用将被拒绝，异常类型={}", exception.getClass().getName());
            }
        }

        /**
         * 停止续期并释放当前租约，重复关闭不会重复释放
         */
        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            // 先停止续期，再从活动集合移除并按令牌释放。
            renewal.cancel(false);
            leases.remove(this);
            unlock(key, token);
        }
    }

    /**
     * 校验锁名称和可转换为毫秒的正数有效期
     *
     * @param key 锁名称
     * @param ttlSeconds 租约有效期，单位为秒
     * @throws IllegalArgumentException 锁名称或有效期无效
     */
    private static void validate(String key, long ttlSeconds) {
        if (key == null || key.isBlank() || ttlSeconds <= 0 || ttlSeconds > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("锁名称须非空，有效期须为可转换成毫秒的正数");
        }
    }

    /**
     * 加载必须随产物打包的 Redis 锁 Lua 脚本
     *
     * @param path 脚本在 classpath 下的资源路径
     * @return 返回 Long 结果的 Redis 脚本
     * @throws IllegalStateException 脚本资源未打包到 classpath
     */
    private static RedisScript<Long> script(String path) {
        var resource = new ClassPathResource(path);
        if (!resource.exists()) throw new IllegalStateException("缺少 Redis 锁 Lua 资源: " + path);
        return RedisScript.of(resource, Long.class);
    }
}
