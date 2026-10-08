package com.hnkjzyxy.ab.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import java.net.URI;

/**
 * OA 地址、凭据、超时与分页配置
 */
@Getter
@Setter
@Validated
@ConfigurationProperties("oa")
public class OaProperties {
    /**
     * 是否启用 OA 客户端接入，默认关闭
     */
    private boolean enabled = false;
    /**
     * OA 服务基础地址
     */
    @NotBlank
    private String baseUrl = "https://dmp.hnkjxy.net.cn";
    /**
     * OA 应用标识
     */
    private String appKey = "";
    /**
     * OA 应用密钥，不写入日志
     */
    private String appSecret = "";
    /**
     * OA HTTP 连接超时，单位为毫秒
     */
    @Min(1)
    private int connectTimeout = 10000;
    /**
     * OA HTTP 读取超时，单位为毫秒
     */
    @Min(1)
    private int readTimeout = 30000;
    /**
     * OA 令牌缓存有效期，单位为秒
     */
    @Min(1)
    private long tokenCacheSeconds = 3300;
    /**
     * 单页获取的数据条数
     */
    @Min(1)
    private int pageSize = 1000;

    /**
     * 检查 OA 启用时是否已配置必要凭据
     *
     * @return OA 未启用或凭据齐全时返回 true
     */
    @AssertTrue(message = "OA 启用时必须配置 app-key 和 app-secret")
    public boolean isCredentialsConfigured() {
        return !enabled || (StringUtils.hasText(appKey) && StringUtils.hasText(appSecret));
    }

    /**
     * 检查 OA 基础地址是否为不含用户信息的 HTTP(S) 地址
     *
     * @return 基础地址有效时返回 true
     */
    @AssertTrue(message = "OA base-url 必须为有效的 HTTP(S) 地址")
    public boolean isBaseUrlValid() {
        try {
            URI uri = URI.create(baseUrl);
            return uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
