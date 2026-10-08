package com.hnkjzyxy.ab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 所有锁租约共用有界续期线程数，避免每个锁创建线程池。
 */
@Configuration(proxyBeanMethods = false)
public class LockConfig {
    /**
     * 创建共用的锁租约续期执行器，限制续期线程数量
     *
     * @return 锁租约续期执行器
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService lockRenewalExecutor() {
        var executor = new ScheduledThreadPoolExecutor(2,
                Thread.ofPlatform().daemon(true).name("device-lock-renew-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
        return executor;
    }
}
