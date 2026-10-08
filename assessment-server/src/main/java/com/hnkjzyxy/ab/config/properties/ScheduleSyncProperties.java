package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * 课表同步开关、执行时间与数据校验参数
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("schedule.sync")
public class ScheduleSyncProperties {
    /**
     * 是否启用课表同步，默认关闭
     */
    private boolean enabled = false;
    /**
     * 是否在后续同步任务实现后执行启动同步
     */
    private boolean syncOnStartup = false;
    /**
     * 按学校时区执行课表同步的 Spring 六段 Cron 表达式
     */
    private String cron = "0 0 3 * * ?";
    /**
     * 同步数据批量写入时使用的批次大小
     */
    @Min(1)
    private int batchSize = 500;
    /**
     * 同步结果最低有效数据条数
     */
    @Min(0)
    private int minRows = 50;
    /**
     * 同步数据缩减保护比例
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double shrinkGuardRatio = 0.5;
    /**
     * 单页获取的数据条数
     */
    @Min(1)
    private int pageSize = 1000;
    /**
     * 同步锁的租约有效期，单位为秒
     */
    @Min(3)
    private long lockTtlSeconds = 1800;

    /**
     * 检查课表同步时间是否为有效的 Spring 六段 Cron 表达式
     *
     * @return Cron 表达式有效时返回 true
     */
    @AssertTrue(message = "课表同步 cron 必须是 Spring 六段表达式")
    public boolean isCronValid() {
        return cron != null && CronExpression.isValidExpression(cron);
    }
}
