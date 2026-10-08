package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * 仅定义后续任务开关和周期，不注册或执行任何业务任务。
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("app.jobs")
public class BackgroundTaskProperties {
    /**
     * 是否启用考试生命周期刷新任务，默认关闭
     */
    private boolean examRefreshEnabled = false;
    /**
     * 考试生命周期刷新任务的执行周期
     */
    private Duration examRefreshInterval = Duration.ofSeconds(15);
    /**
     * 是否启用门锁任务对账，默认关闭
     */
    private boolean lockReconcileEnabled = false;
    /**
     * 门锁任务对账的执行周期
     */
    private Duration lockReconcileInterval = Duration.ofSeconds(60);
    /**
     * 是否启用命令超期检查，默认关闭
     */
    private boolean commandExpiryEnabled = false;
    /**
     * 命令超期检查的执行周期
     */
    private Duration commandExpiryInterval = Duration.ofSeconds(60);

    /**
     * 检查各后台任务周期是否为正数
     *
     * @return 全部周期有效时返回 true
     */
    @AssertTrue(message = "后台任务周期必须为正数")
    public boolean isIntervalsValid() {
        return Stream.of(examRefreshInterval, lockReconcileInterval, commandExpiryInterval)
                .allMatch(d -> d != null && !d.isZero() && !d.isNegative());
    }
}
