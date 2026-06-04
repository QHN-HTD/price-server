package com.pricing.server.config;

import com.pricing.server.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一拦截所有未捕获异常，返回结构化的错误响应。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>生产环境不返回详细异常堆栈（仅开发环境）</li>
 *   <li>统一错误格式，不泄露内部实现细节</li>
 *   <li>分别处理参数校验、权限拒绝、系统异常等不同场景</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 参数校验异常 ──

    /**
     * 处理 @Valid 参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("参数校验失败: {}", message);
        return Result.badRequest(message);
    }

    /**
     * 处理缺少必需参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.badRequest("缺少必需参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.badRequest("参数类型错误: " + e.getName());
    }

    /**
     * 处理请求体无法解析。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return Result.badRequest("请求体格式错误，请检查 JSON 格式");
    }

    // ── 权限异常 ──

    /**
     * 处理权限拒绝（403）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限拒绝: {}", e.getMessage());
        return Result.forbidden("权限不足，请联系管理员");
    }

    // ── 业务异常 ──

    /**
     * 处理 IllegalArgumentException（业务参数错误）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("业务参数错误: {}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    // ── 未知异常 ──

    /**
     * 处理所有未捕获的异常（500）。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknownException(Exception e, HttpServletRequest request) {
        log.error("未捕获异常: path={}, error={}", request.getRequestURI(), e.getMessage(), e);
        // 生产环境不返回详细错误信息
        return Result.error("服务器内部错误，请稍后重试");
    }
}
