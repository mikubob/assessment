package com.hnkjzyxy.ab.security;

import com.hnkjzyxy.ab.config.properties.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;

/**
 * 仅由 SecurityFilterChain 注册，避免 Servlet 容器重复执行。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    /**
     * 共享 JWT 和 Redis 会话配置
     */
    private final JwtProperties properties;
    /**
     * JWT 验签与主题身份解析组件
     */
    private final JwtVerifier verifier;
    /**
     * 共享 Redis 当前会话读取组件
     */
    private final RedisTokenStore tokens;
    /**
     * 实时身份与权限读取组件
     */
    private final ObjectProvider<IdentityResolver> identities;
    /**
     * 认证失败 JSON 响应写入组件
     */
    private final SecurityResponseWriter responses;

    /**
     * 创建原始 JWT、当前 Redis 会话与实时身份的认证过滤器
     *
     * @param properties 共享 JWT 和 Redis 会话配置
     * @param verifier JWT 验签与身份解析组件
     * @param tokens 共享 Redis 当前会话读取组件
     * @param identities 实时身份与权限读取组件提供器
     * @param responses 认证失败 JSON 响应写入组件
     */
    public JwtAuthenticationFilter(JwtProperties properties, JwtVerifier verifier, RedisTokenStore tokens,
                                   ObjectProvider<IdentityResolver> identities, SecurityResponseWriter responses) {
        this.properties = properties;
        this.verifier = verifier;
        this.tokens = tokens;
        this.identities = identities;
        this.responses = responses;
    }

    /**
     * 判断请求是否属于无需执行 JWT 校验的精确公开入口
     *
     * @param request 当前 HTTP 请求
     * @return 无需执行认证过滤时返回 true
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equals(request.getMethod())
                || path.equals("/actuator/health/liveness") || path.equals("/actuator/health/readiness")
                || ("POST".equals(request.getMethod()) && path.equals("/lock/login"));
    }

    /**
     * 校验原始 JWT、当前 Redis 令牌及实时身份，再建立认证上下文
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param chain 后续请求过滤器链
     * @throws ServletException 后续请求过滤器处理失败
     * @throws IOException 读取请求或写入响应失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders(properties.getHeader()));
        // 未带令牌时交由授权规则处理，受保护路径仍会拒绝匿名请求。
        if (headers.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        // 沿用原始 JWT 协议，并拒绝多个认证头带来的令牌歧义。
        String token = headers.getFirst();
        if (headers.size() != 1 || !StringUtils.hasText(token) || token.startsWith("Bearer ")
                || token.length() > 8192) {
            responses.write(response, 401, "请使用原服务返回的原始 JWT");
            return;
        }
        try {
            var subject = verifier.verify(token);
            var resolver = identities.getIfAvailable();
            if (resolver == null) {
                throw new AuthenticationServiceException("身份权限读取尚未接入");
            }
            // 验签通过后仍核对当前会话，确保退出或换令牌后旧令牌失效。
            if (!tokens.isCurrent(subject.userId(), token)) {
                responses.write(response, 401, "登录会话已失效");
                return;
            }
            // 用户启用状态和权限取自实时身份源，不能从 JWT 推断。
            var resolved = resolver.resolve(subject.userId(), subject.username());
            if (resolved.isEmpty() || !resolved.get().enabled()
                    || resolved.get().userId() != subject.userId()
                    || !subject.username().equals(resolved.get().username())) {
                responses.write(response, 401, "用户身份无效");
                return;
            }
            var identity = resolved.get();
            if (identity.authorities().contains("ROLE_LOCK_ONLY")) {
                responses.write(response, 403, "专用门锁令牌不能访问普通业务接口");
                return;
            }
            // 所有校验通过后再创建认证上下文，避免留下部分认证状态。
            var authorities = identity.authorities().stream().map(SimpleGrantedAuthority::new).toList();
            var authentication = UsernamePasswordAuthenticationToken.authenticated(identity, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (AuthenticationServiceException | DataAccessException e) {
            SecurityContextHolder.clearContext();
            responses.write(response, 503, "认证服务暂不可用");
            return;
        } catch (AuthenticationException e) {
            SecurityContextHolder.clearContext();
            responses.write(response, 401, "登录令牌无效或已过期");
            return;
        } catch (RuntimeException e) {
            SecurityContextHolder.clearContext();
            responses.write(response, 503, "身份权限服务暂不可用");
            return;
        }
        chain.doFilter(request, response);
    }
}
