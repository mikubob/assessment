package com.hnkjzyxy.ab.result;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沿用旧接口可扩展的 code/msg 与顶层业务字段契约。
 */
public class ApiResult extends LinkedHashMap<String, Object> {
    /**
     * 序列化版本标识
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建默认成功接口响应
     */
    public ApiResult() {
        super.put("code", 200);
        super.put("msg", "success");
    }

    /**
     * 构建成功接口响应，保留默认状态及可扩展字段
     *
     * @return 统一成功响应
     */
    public static ApiResult ok() { return new ApiResult(); }
    /**
     * 构建成功接口响应，保留默认状态及可扩展字段
     *
     * @param message 响应提示信息
     * @return 统一成功响应
     */
    public static ApiResult ok(String message) { return new ApiResult().put("msg", message); }
    /**
     * 构建成功接口响应，保留默认状态及可扩展字段
     *
     * @param key 响应字段名称
     * @param value 响应字段值
     * @return 统一成功响应
     */
    public static ApiResult ok(String key, Object value) { return new ApiResult().put(key, value); }
    /**
     * 构建成功接口响应，保留默认状态及可扩展字段
     *
     * @param values 附加响应字段映射
     * @return 统一成功响应
     */
    public static ApiResult ok(Map<String, Object> values) {
        var result = new ApiResult();
        result.putAll(values);
        return result;
    }
    /**
     * 构建失败接口响应
     *
     * @return 统一失败响应
     */
    public static ApiResult error() { return error(444, "未知异常，请联系管理员"); }
    /**
     * 构建失败接口响应
     *
     * @param message 响应提示信息
     * @return 统一失败响应
     */
    public static ApiResult error(String message) { return error(400, message); }
    /**
     * 构建失败接口响应
     *
     * @param code 业务响应状态码
     * @param message 响应提示信息
     * @return 统一失败响应
     */
    public static ApiResult error(int code, String message) {
        return new ApiResult().put("code", code).put("msg", message);
    }

    /**
     * 为旧 Controller 保留链式响应构造方式。
     *
     * @param key 响应字段名称
     * @param value 响应字段值
     * @return 当前响应对象
     */
    @Override
    public ApiResult put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
