package com.pricing.server.config;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口限流配置
 * <p>
 * 使用 Guava RateLimiter 实现令牌桶算法限流。
 * </p>
 *
 * <p><b>限流策略：</b>
 * <ul>
 *   <li>全局限流：100次/分钟（所有 API 共享）</li>
 *   <li>登录限流：5次/分钟（防暴力破解）</li>
 *   <li>IP 级别限流：每 IP 独立计数（通过 ConcurrentHashMap 实现）</li>
 * </ul>
 * </p>
 *
 * <p><b>安全价值：</b>
 * 防止暴力攻击、DDoS 攻击、API 滥用。
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Configuration
public class RateLimiterConfig {

    @Value("${rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${rate-limit.permits-per-minute:100}")
    private double globalPermitsPerMinute;

    @Value("${rate-limit.login-permits-per-minute:5}")
    private double loginPermitsPerMinute;

    /**
     * 全局限流器。
     */
    @Bean
    public RateLimiter globalRateLimiter() {
        return RateLimiter.create(globalPermitsPerMinute / 60.0); // 每秒许可数
    }

    /**
     * 登录接口限流器（更严格）。
     */
    @Bean
    public RateLimiter loginRateLimiter() {
        return RateLimiter.create(loginPermitsPerMinute / 60.0);
    }

    /**
     * IP 级别限流器映射表。
     */
    private final Map<String, RateLimiter> ipRateLimiters = new ConcurrentHashMap<>();

    /**
     * 获取或创建指定 IP 的限流器。
     */
    private RateLimiter getIpRateLimiter(String ip) {
        return ipRateLimiters.computeIfAbsent(ip,
                k -> RateLimiter.create(globalPermitsPerMinute / 60.0));
    }

    /**
     * 注册限流过滤器。
     */
    @Bean
    public FilterRegistrationBean<Filter> rateLimitingFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RateLimitingFilter());
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1); // 最先执行
        registration.setName("rateLimitingFilter");
        return registration;
    }

    /**
     * 限流过滤器实现。
     */
    private class RateLimitingFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response,
                             FilterChain chain) throws IOException, ServletException {

            if (!enabled) {
                chain.doFilter(request, response);
                return;
            }

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            String clientIp = getClientIp(httpRequest);
            String path = httpRequest.getRequestURI();

            // 登录接口使用更严格的限流
            if (path.contains("/auth/login")) {
                if (!loginRateLimiter().tryAcquire()) {
                    log.warn("登录限流触发: IP={}", clientIp);
                    sendRateLimitError(httpResponse);
                    return;
                }
            }

            // IP 级别限流
            if (!getIpRateLimiter(clientIp).tryAcquire()) {
                log.warn("IP 限流触发: IP={}, path={}", clientIp, path);
                sendRateLimitError(httpResponse);
                return;
            }

            // 全局限流
            if (!globalRateLimiter().tryAcquire()) {
                log.warn("全局限流触发: IP={}, path={}", clientIp, path);
                sendRateLimitError(httpResponse);
                return;
            }

            chain.doFilter(request, response);
        }

        private String getClientIp(HttpServletRequest request) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }

        private void sendRateLimitError(HttpServletResponse response) throws IOException {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}");
        }
    }
}
