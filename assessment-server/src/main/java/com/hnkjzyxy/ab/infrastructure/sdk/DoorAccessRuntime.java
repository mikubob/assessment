package com.hnkjzyxy.ab.infrastructure.sdk;

import Door.Access.Connector.ConnectorAllocator;
import Door.Access.Connector.INConnectorEvent;
import Door.Access.Connector.NettyAllocator;
import io.netty.channel.EventLoopGroup;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 接管厂商 SDK 实际单例与线程组。不创建连接、监听端口、设备发现或动作命令。
 * 后续设备网关只通过此对象获取分配器，不自行调用静态 GetAllocator。
 */
public final class DoorAccessRuntime implements AutoCloseable {
    /**
     * 同一 JVM 中 SDK 是否已由某个运行上下文持有
     */
    private static final AtomicBoolean OWNED = new AtomicBoolean();
    /**
     * 厂商 SDK 实际使用的命令分配器
     *
     * @throws IllegalStateException SDK 运行资源已关闭
     */
    private final ConnectorAllocator allocator;
    /**
     * SDK 实际使用的事件线程组
     */
    private final EventLoopGroup events;
    /**
     * SDK 实际使用的客户端线程组
     */
    private final EventLoopGroup clients;
    /**
     * SDK 资源释放等待时间，单位为毫秒
     */
    private final long shutdownMillis;
    /**
     * 当前运行上下文已注册的 SDK 监听器
     */
    private final Set<INConnectorEvent> listeners = new LinkedHashSet<>();
    /**
     * 当前运行资源是否已经关闭
     */
    private boolean closed;

    /**
     * 创建厂商门锁 SDK 单例、监听器与线程组的生命周期管理
     *
     * @param shutdownTimeout SDK 停机释放的最长等待时间
     * @throws IllegalArgumentException 停机等待时间无效
     * @throws IllegalStateException 同一 JVM 已存在 SDK 资源持有上下文
     */
    public DoorAccessRuntime(Duration shutdownTimeout) {
        Objects.requireNonNull(shutdownTimeout);
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero() || shutdownTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("SDK 停机等待时间必须为正数");
        }
        shutdownMillis = shutdownTimeout.toMillis();
        // SDK 持有静态单例，同一 JVM 只能由一个上下文负责释放。
        if (!OWNED.compareAndSet(false, true)) {
            throw new IllegalStateException("同一 JVM 的 SDK 只能由一个 Spring 上下文管理");
        }
        ConnectorAllocator created = null;
        try {
            created = ConnectorAllocator.GetAllocator();
            allocator = created;
            events = NettyAllocator.GetEventLoopGroup();
            clients = NettyAllocator.GetClientEventLoopGroup();
        } catch (RuntimeException | Error error) {
            // 初始化中途失败时释放已创建资源，同时保留原始异常。
            try {
                if (created != null) created.Release();
            } catch (RuntimeException releaseError) {
                error.addSuppressed(releaseError);
            } finally {
                OWNED.set(false);
            }
            throw error;
        }
    }

    /**
     * 获取仍处于可用状态的厂商命令分配器
     *
     * @return 厂商命令分配器
     * @throws IllegalStateException SDK 运行资源已关闭
     */
    public synchronized ConnectorAllocator allocator() { requireOpen(); return allocator; }
    /**
     * 获取 SDK 实际使用的事件线程组
     *
     * @return SDK 事件线程组
     * @throws IllegalStateException SDK 运行资源已关闭
     */
    public synchronized EventLoopGroup eventLoopGroup() { requireOpen(); return events; }
    /**
     * 获取 SDK 实际使用的客户端线程组
     *
     * @return SDK 客户端线程组
     * @throws IllegalStateException SDK 运行资源已关闭
     */
    public synchronized EventLoopGroup clientEventLoopGroup() { requireOpen(); return clients; }

    /**
     * 同一实例只注册一次；业务回执监听实现后通过这里接入。
     *
     * @param listener SDK 事件监听器
     */
    public synchronized void addListener(INConnectorEvent listener) {
        requireOpen();
        Objects.requireNonNull(listener);
        if (listeners.add(listener)) {
            try {
                allocator.AddListener(listener);
            } catch (RuntimeException exception) {
                // 注册失败后撤销本地记录，允许后续重新注册。
                listeners.remove(listener);
                throw exception;
            }
        }
    }

    /**
     * 移除已注册的 SDK 监听器
     *
     * @param listener SDK 事件监听器
     */
    public synchronized void removeListener(INConnectorEvent listener) {
        requireOpen();
        if (listeners.remove(listener)) allocator.DeleteListener(listener);
    }

    /**
     * 检查运行资源及命令分配器是否尚未关闭
     *
     * @return 资源可用时返回 true
     */
    public synchronized boolean isOpen() { return !closed && !allocator.IsRelease(); }

    /**
     * 校验 SDK 运行资源和分配器仍可使用
     *
     * @throws IllegalStateException SDK 运行资源或分配器已关闭
     */
    private void requireOpen() {
        if (closed || allocator.IsRelease()) throw new IllegalStateException("门锁 SDK 已关闭");
    }

    /**
     * 停止 SDK 实际线程组并释放分配器、监听器与单例所有权
     *
     * @throws IllegalStateException SDK 线程组未在等待期内结束
     */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            // 先停止实际线程组，Release 内的 SDK shutdown 不再等待默认静默期。
            events.shutdownGracefully(0, shutdownMillis, TimeUnit.MILLISECONDS);
            clients.shutdownGracefully(0, shutdownMillis, TimeUnit.MILLISECONDS);
            allocator.Release(); // SDK 还会释放分配器的工作/事件执行器，并清空静态单例。
            // 确认实际线程已结束，不能只依赖发出 shutdown 请求。
            if (!events.terminationFuture().awaitUninterruptibly(shutdownMillis)
                    || !clients.terminationFuture().awaitUninterruptibly(shutdownMillis)) {
                throw new IllegalStateException("SDK 线程组未在停机等待期内结束");
            }
        } finally {
            listeners.clear();
            OWNED.set(false);
        }
    }
}
