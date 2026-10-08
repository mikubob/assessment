package com.hnkjzyxy.ab.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求标识与日志 MDC 上下文过滤器
 */
public class RequestTraceFilter extends OncePerRequestFilter {
    /**
     * 设置合法请求标识并写入响应，在请求结束后恢复原日志上下文
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
        // 只接受受限格式的外部标识，不合法时生成新标识。
        String supplied = request.getHeader("X-Request-Id");
        String requestId = supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
                ? supplied : UUID.randomUUID().toString();
        String previous = MDC.get("requestId");
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 工作线程会被复用，必须恢复原上下文，防止请求之间串日志。
            if (previous == null) MDC.remove("requestId"); else MDC.put("requestId", previous);
        }
    }
}
