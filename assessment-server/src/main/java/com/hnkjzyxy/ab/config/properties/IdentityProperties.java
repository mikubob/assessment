package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 共享身份表只读查询配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("app.identity")
public class IdentityProperties {
    /**
     * 默认使用共享身份表的只读 JDBC 查询，可由其他 IdentityResolver Bean 替代。
     */
    private boolean jdbcEnabled = true;
    /**
     * 单条身份查询的超时，单位为秒
     */
    @Min(1)
    @Max(60)
    private int queryTimeoutSeconds = 3;
}
