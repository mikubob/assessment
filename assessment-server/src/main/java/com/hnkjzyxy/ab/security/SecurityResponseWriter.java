package com.hnkjzyxy.ab.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnkjzyxy.ab.result.ApiResult;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证与授权失败的统一 JSON 响应写入组件
 */
@Component
public class SecurityResponseWriter {
    /**
     * JSON 序列化映射器
     */
    private final ObjectMapper mapper;

    /**
     * 创建认证与授权失败的统一 JSON 响应写入组件
     *
     * @param mapper JSON 序列化映射器
     */
    public SecurityResponseWriter(ObjectMapper mapper) { this.mapper = mapper; }

    /**
     * 写入 UTF-8 JSON 错误响应，并附加请求标识
     *
     * @param response 当前 HTTP 响应
     * @param status 响应 HTTP 状态码
     * @param message 响应提示信息
     * @throws IOException 读取请求或写入响应失败
     */
    public void write(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var result = ApiResult.error(status, message);
        String requestId = MDC.get("requestId");
        if (requestId != null) result.put("requestId", requestId);
        mapper.writeValue(response.getOutputStream(), result);
    }
}
