package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨域请求允许来源配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("app.cors")
public class CorsProperties {
    /**
     * 允许访问的明确 HTTP(S) Origin 列表
     */
    @NotEmpty
    private List<String> allowedOrigins = new ArrayList<>();
}
