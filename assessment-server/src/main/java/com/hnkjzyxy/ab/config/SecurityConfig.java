package com.hnkjzyxy.ab.config;

import com.hnkjzyxy.ab.config.properties.CorsProperties;
import com.hnkjzyxy.ab.config.properties.JwtProperties;
import com.hnkjzyxy.ab.security.*;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

/**
 * 无状态 JWT 认证、接口授权与跨域配置
 */
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    /**
     * 配置无状态认证、精确接口授权与统一认证失败响应
     *
     * @param http Spring Security HTTP 配置构建器
     * @param jwt 共享 JWT 和 Redis 会话配置
     * @param verifier JWT 验签与身份解析组件
     * @param tokens 共享 Redis 当前会话读取组件
     * @param identities 实时身份与权限读取组件提供器
     * @param responses 认证失败 JSON 响应写入组件
     * @param cors 跨域请求配置
     * @return Spring Security 过滤器链
     * @throws Exception 任务实例创建或基础配置初始化失败
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtProperties jwt,
            JwtVerifier verifier, RedisTokenStore tokens, ObjectProvider<IdentityResolver> identities,
            SecurityResponseWriter responses, @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource cors) throws Exception {
        var filter = new JwtAuthenticationFilter(jwt, verifier, tokens, identities, responses);
        return http
                .cors(config -> config.configurationSource(cors))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                // 身份由每次请求的令牌决定，不创建服务端登录会话。
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, error) ->
                                responses.write(response, 401, "请先通过原认证服务登录"))
                        .accessDeniedHandler((request, response, error) ->
                                responses.write(response, 403, "无权访问")))
                // 路径均相对于 /api，不再把 /api 重复写进匹配规则。
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/lock/login").permitAll()
                        // 普通课表查询先匹配，后面的课表管理入口再要求管理员。
                        .requestMatchers(HttpMethod.POST, "/courseSchedule/list").authenticated()
                        .requestMatchers("/courseSchedule/**", "/exam/**", "/classroomOccupancy/**",
                                "/smart/lock/**", "/lock/setPassword", "/actuator/**").hasRole("admin")
                        .anyRequest().authenticated())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 配置明确的跨域来源、方法及认证请求头
     *
     * @param properties 跨域允许来源配置
     * @param jwt 共享 JWT 和 Redis 会话配置
     * @return 跨域请求配置来源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties, JwtProperties jwt) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of(jwt.getHeader(), "Content-Type", "Accept", "X-Request-Id"));
        // 允许前端读取响应中的请求标识，便于定位同一次请求的日志。
        cors.setExposedHeaders(List.of("X-Request-Id"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /**
     * 创建 BCrypt 密码编码器
     *
     * @return BCrypt 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
