package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * 门锁 SDK 启用开关、资源释放和命令超时配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("smartlock")
public class SmartLockProperties {
    /**
     * 只创建 SDK 基础设施；发现设备和发送动作仍须显式业务调用。
     */
    private boolean enabled = false;
    /**
     * SDK 停机释放的最长等待时间
     */
    private Duration shutdownTimeout = Duration.ofSeconds(10);
    /**
     * 后续网关使用的门锁命令超时
     */
    private Duration commandTimeout = Duration.ofSeconds(30);
    /**
     * 动作命令默认不自动重新发送；后续网关必须显式应用此值。
     */
    @Min(0)
    @Max(0)
    private int commandRetries = 0;
    /**
     * 门锁状态观测的有效期
     */
    private Duration observationTtl = Duration.ofMinutes(2);
    /**
     * 未取得最终回执的命令超期时间
     */
    private Duration receiptExpiry = Duration.ofMinutes(2);
    /**
     * 后续门锁任务对账使用的执行周期
     */
    private Duration reconcileInterval = Duration.ofSeconds(60);

    /**
     * 检查门锁超时、周期及停机等待时间是否满足约束
     *
     * @return 各时间参数有效时返回 true
     */
    @AssertTrue(message = "门锁超时和周期必须为正数，停机等待不得超过 20 秒")
    public boolean isDurationsValid() {
        return Stream.of(shutdownTimeout, commandTimeout, observationTtl, receiptExpiry, reconcileInterval)
                .allMatch(d -> d != null && !d.isZero() && !d.isNegative())
                && shutdownTimeout.compareTo(Duration.ofSeconds(20)) <= 0;
    }
}
