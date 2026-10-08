package com.hnkjzyxy.ab.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.Map;

/**
 * 异步执行、定时调度与请求日志上下文配置
 */
@EnableAsync
@EnableScheduling
@Configuration(proxyBeanMethods = false)
public class TaskConfig implements AsyncConfigurer {
    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(TaskConfig.class);

    /**
     * 配合 Boot 的有界执行器，传播并恢复请求日志上下文。
     *
     * @return MDC 上下文任务装饰器
     */
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return task -> {
            Map<String, String> captured = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (captured == null) MDC.clear(); else MDC.setContextMap(captured);
                    task.run();
                } finally {
                    if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
                }
            };
        };
    }

    /**
     * 记录异步无返回值方法的未捕获异常，避免输出敏感参数
     *
     * @return 异步未捕获异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // 不记录方法参数，避免意外输出 JWT/OA 凭据。
        return (error, method, parameters) ->
                log.error("异步方法 {} 执行失败，类型 {}", method.getName(), error.getClass().getSimpleName());
    }
}
