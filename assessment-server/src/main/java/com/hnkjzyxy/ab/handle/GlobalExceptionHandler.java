package com.hnkjzyxy.ab.handle;

import com.hnkjzyxy.ab.result.ApiResult;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理，将基础设施异常转换为统一 JSON 响应
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 日志记录器
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 将参数缺失、绑定或校验失败转换为参数错误响应
     *
     * @param exception 当前捕获的异常
     * @return HTTP 400 的统一错误响应
     */
    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResult> invalidRequest(Exception exception) {
        return response(400, "请求参数缺失或格式不合法");
    }

    /**
     * 将访问权限不足转换为拒绝访问响应
     *
     * @param exception 当前捕获的异常
     * @return HTTP 403 的统一错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult> accessDenied(AccessDeniedException exception) {
        return response(403, "无权访问");
    }

    /**
     * 将不存在的接口或资源转换为未找到响应
     *
     * @param exception 当前捕获的异常
     * @return HTTP 404 的统一错误响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult> notFound(NoResourceFoundException exception) {
        return response(404, "接口不存在");
    }

    /**
     * 将不支持的 HTTP 请求方法转换为错误响应
     *
     * @param exception 当前捕获的异常
     * @return HTTP 405 的统一错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult> methodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        return response(405, "请求方法不受支持");
    }

    /**
     * 记录未预期异常的类型，并返回统一服务器错误
     *
     * @param exception 当前捕获的异常
     * @return HTTP 500 的统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult> unexpected(Exception exception) {
        log.error("请求处理失败，异常类型 {}", exception.getClass().getName());
        return response(500, "服务器内部错误");
    }

    /**
     * 创建带请求标识的统一 HTTP 错误响应
     *
     * @param status 响应 HTTP 状态码
     * @param message 响应提示信息
     * @return 带 HTTP 状态码的统一响应
     */
    private ResponseEntity<ApiResult> response(int status, String message) {
        var result = ApiResult.error(status, message);
        String requestId = MDC.get("requestId");
        if (requestId != null) result.put("requestId", requestId);
        return ResponseEntity.status(status).body(result);
    }
}
