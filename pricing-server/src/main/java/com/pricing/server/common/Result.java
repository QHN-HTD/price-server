package com.pricing.server.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一 API 响应封装
 * <p>
 * 所有 REST API 返回此格式，确保前端能统一处理成功/失败响应。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>生产环境不返回详细异常堆栈（仅开发环境）</li>
 *   <li>敏感字段通过 @JsonInclude(NON_NULL) 排除空值</li>
 * </ul>
 * </p>
 *
 * @param <T> 响应数据类型
 * @author PriceWise Team
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码（200=成功，4xx=客户端错误，5xx=服务端错误） */
    private final int code;

    /** 响应消息 */
    private final String message;

    /** 响应数据 */
    private final T data;

    /** 时间戳 */
    private final long timestamp;

    /** 请求追踪ID（用于日志关联） */
    private String traceId;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    // ── 成功响应 ──

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ── 客户端错误响应 ──

    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }

    public static <T> Result<T> tooManyRequests(String message) {
        return new Result<>(429, message, null);
    }

    // ── 服务端错误响应 ──

    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    // ── Builder 方法 ──

    public Result<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    // ── 便捷判断 ──

    public boolean isSuccess() {
        return this.code == 200;
    }
}
