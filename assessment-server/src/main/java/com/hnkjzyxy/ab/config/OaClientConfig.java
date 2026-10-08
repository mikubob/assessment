package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.OaProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * OA HTTP 客户端配置
 */
@Configuration(proxyBeanMethods = false)
public class OaClientConfig {
    /**
     * 创建带连接超时且禁止自动重定向的 OA HTTP 客户端
     *
     * @param properties OA 客户端配置
     * @return OA 底层 HTTP 客户端
     */
    @Bean(name = "oaHttpClient", destroyMethod = "close")
    public HttpClient oaHttpClient(OaProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 仅创建带超时的客户端；不获取 token，不在启动时调用 OA。
     *
     * @param builder Spring 管理的 HTTP 请求客户端构建器
     * @param properties OA 客户端配置
     * @param httpClient OA 底层 HTTP 客户端
     * @return OA 请求客户端
     */
    @Bean("oaRestClient")
    public RestClient oaRestClient(RestClient.Builder builder, OaProperties properties,
                                  @Qualifier("oaHttpClient") HttpClient httpClient) {
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeout()));
        return builder.baseUrl(properties.getBaseUrl()).requestFactory(requestFactory).build();
    }
}
