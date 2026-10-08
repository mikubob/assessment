package com.hnkjzyxy.ab.config;

import Door.Access.Connector.INConnectorEvent;
import com.hnkjzyxy.ab.config.properties.SmartLockProperties;
import com.hnkjzyxy.ab.infrastructure.sdk.DoorAccessRuntime;
import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 基础设施资源释放与异步日志上下文的测试
 */
class InfrastructureLifecycleTest {
    /**
     * 验证 SDK 实际资源随容器关闭释放，且释放后可在同 JVM 重新创建
     */
    @Test
    void closesActualSdkGroupsAndAllocatorWithoutDeviceAccess() {
        var allocator = new AtomicReference<Door.Access.Connector.ConnectorAllocator>();
        var events = new AtomicReference<EventLoopGroup>();
        var clients = new AtomicReference<EventLoopGroup>();
        new ApplicationContextRunner().withUserConfiguration(NettyConfig.class)
                .withBean(SmartLockProperties.class, SmartLockProperties::new)
                .withPropertyValues("smartlock.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var runtime = context.getBean(DoorAccessRuntime.class);
                    allocator.set(runtime.allocator());
                    events.set(runtime.eventLoopGroup());
                    clients.set(runtime.clientEventLoopGroup());
                    var listener = mock(INConnectorEvent.class);
                    runtime.addListener(listener);
                    runtime.addListener(listener);
                    runtime.removeListener(listener);
                    events.get().next().submit(() -> {}).syncUninterruptibly();
                    clients.get().next().submit(() -> {}).syncUninterruptibly();
                    assertThatThrownBy(() -> new DoorAccessRuntime(Duration.ofSeconds(1)))
                            .hasMessageContaining("一个 Spring 上下文");
                });
        assertThat(allocator.get().IsRelease()).isTrue();
        assertThat(events.get().isTerminated()).isTrue();
        assertThat(clients.get().isTerminated()).isTrue();
        // SDK 已清空单例：同 JVM 新上下文可以重新创建和关闭，不返回已释放的旧分配器。
        try (var second = new DoorAccessRuntime(Duration.ofSeconds(1))) {
            assertThat(second.allocator()).isNotSameAs(allocator.get());
            assertThat(second.isOpen()).isTrue();
        }
    }

    /**
     * 验证关闭设备开关时不创建 SDK 运行资源
     */
    @Test
    void disabledSdkDoesNotCreateRuntimeBean() {
        new ApplicationContextRunner().withUserConfiguration(NettyConfig.class)
                .withBean(SmartLockProperties.class, SmartLockProperties::new)
                .withPropertyValues("smartlock.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DoorAccessRuntime.class));
    }

    /**
     * 验证异步装饰器传播请求上下文并恢复工作线程原上下文
     */
    @Test
    void asyncDecoratorRestoresWorkerMdc() {
        var decorator = new TaskConfig().mdcTaskDecorator();
        try {
            MDC.put("requestId", "request-context");
            var task = decorator.decorate(() -> assertThat(MDC.get("requestId")).isEqualTo("request-context"));
            MDC.put("requestId", "worker-context");
            task.run();
            assertThat(MDC.get("requestId")).isEqualTo("worker-context");
        } finally {
            MDC.clear();
        }
    }
}
