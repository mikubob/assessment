package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 兼容原认证服务的 JWT 与共享会话配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("absolute.jwt")
public class JwtProperties {
    /**
     * 原始 JWT 的 HTTP 请求头名称
     */
    @NotBlank
    private String header = "Authorization";
    /**
     * 原服务的 Base64 HS256 密钥；不输出到日志。
     */
    private String secret = "";
    /**
     * 保留的令牌有效期配置，单位为秒；本服务不签发普通登录令牌
     */
    @Positive
    private long expire = 604800;
    /**
     * 必须与原服务及共享 Redis DB 一致；不设置虚构的默认后缀。
     */
    private String suffix = "";
}
