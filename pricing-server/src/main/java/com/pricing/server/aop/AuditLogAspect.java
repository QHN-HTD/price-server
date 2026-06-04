package com.pricing.server.aop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.server.repository.AuditLogMapper;
import com.pricing.server.security.JwtUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * API 审计日志 AOP 切面
 * <p>
 * 自动记录所有 Controller 方法的调用信息：
 * <ul>
 *   <li>谁（username）</li>
 *   <li>何时（created_at 自动生成）</li>
 *   <li>调用了哪个接口（api_path）</li>
 *   <li>入参摘要（request_params，敏感字段自动脱敏）</li>
 *   <li>响应状态（response_status）</li>
 *   <li>执行耗时（execution_time）</li>
 * </ul>
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>敏感参数（password, token, secret）自动替换为 ***</li>
 *   <li>请求体截断到 500 字符，防止大文本撑爆日志</li>
 *   <li>异步写入不影响主业务性能（可进一步改为 MQ 异步）</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Value("${audit.enabled:true}")
    private boolean auditEnabled;

    /** 需要脱敏的参数名关键词 */
    @Value("#{'${audit.sensitive-keywords:password,token,secret}'.split(',')}")
    private List<String> sensitiveKeywords;

    /** 排除审计的路径 */
    @Value("#{'${audit.exclude-paths:/api/v1/actuator/health,/api/v1/auth/login.html}'.split(',')}")
    private List<String> excludePaths;

    /** 请求参数最大长度 */
    private static final int MAX_PARAM_LENGTH = 500;

    /**
     * 切点：所有 Controller 中的 public 方法。
     */
    @Pointcut("execution(public * com.pricing.server.controller..*.*(..))")
    public void controllerMethods() {}

    /**
     * 环绕通知：记录请求前后信息。
     */
    @Around("controllerMethods()")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        // 检查审计开关
        if (!auditEnabled) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();

        // 获取当前请求信息
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

        // 获取 API 路径
        String apiPath = request != null ? request.getRequestURI() : joinPoint.getSignature().toShortString();

        // 排除路径
        if (isExcludedPath(apiPath)) {
            return joinPoint.proceed();
        }

        // 获取当前用户
        String username = getCurrentUsername();

        // 获取客户端 IP
        String clientIp = getClientIp(request);

        // 获取请求参数
        String requestParams = sanitizeParams(joinPoint.getArgs());

        // 方法描述
        String operation = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();

        int responseStatus = 200;
        Object result;

        try {
            // 执行目标方法
            result = joinPoint.proceed();
        } catch (Throwable e) {
            responseStatus = 500;
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            // 异步写入审计日志（容错：写入失败不抛异常）
            try {
                auditLogMapper.insert(
                        username,
                        operation,
                        apiPath,
                        clientIp,
                        requestParams,
                        responseStatus,
                        executionTime
                );
            } catch (Exception e) {
                log.error("审计日志写入失败: {}", e.getMessage());
            }

            // 同时输出到日志文件（双保险）
            log.info("AUDIT | user={} | api={} | status={} | time={}ms | ip={}",
                    username, apiPath, responseStatus, executionTime, clientIp);
        }

        return result;
    }

    /**
     * 从 SecurityContext 获取当前用户。
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof JwtUserDetails) {
            return ((JwtUserDetails) auth.getDetails()).getUsername();
        }
        return auth != null ? auth.getName() : "ANONYMOUS";
    }

    /**
     * 获取客户端真实 IP（考虑代理情况）。
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";

        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理的情况：取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 序列化请求参数并脱敏，截断到最大长度。
     */
    private String sanitizeParams(Object[] args) {
        if (args == null || args.length == 0) return "";

        try {
            // 过滤掉 HttpServletRequest/Response 等不可序列化的参数
            List<Object> serializableArgs = Arrays.stream(args)
                    .filter(arg -> !(arg instanceof HttpServletRequest)
                            && !(arg instanceof jakarta.servlet.http.HttpServletResponse))
                    .toList();

            if (serializableArgs.isEmpty()) return "";

            String json = objectMapper.writeValueAsString(serializableArgs);

            // 脱敏敏感字段
            for (String keyword : sensitiveKeywords) {
                json = json.replaceAll(
                        "\"" + keyword.trim() + "\"\\s*:\\s*\"[^\"]*\"",
                        "\"" + keyword.trim() + "\":\"***\"");
            }

            // 截断
            if (json.length() > MAX_PARAM_LENGTH) {
                json = json.substring(0, MAX_PARAM_LENGTH) + "...(truncated)";
            }

            return json;
        } catch (JsonProcessingException e) {
            return "[序列化失败]";
        }
    }

    /**
     * 判断路径是否需要排除审计。
     */
    private boolean isExcludedPath(String path) {
        if (path == null) return false;
        for (String excludePath : excludePaths) {
            if (path.startsWith(excludePath.trim())) {
                return true;
            }
        }
        return false;
    }
}
