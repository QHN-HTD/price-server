package com.pricing.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.server.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * <p>
 * 每个请求到达时拦截并验证 JWT Token：
 * <ol>
 *   <li>从 Authorization Header 提取 Bearer Token</li>
 *   <li>验证 Token 签名和有效期</li>
 *   <li>解析用户名和角色，设置 SecurityContext</li>
 * </ol>
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>无状态认证：不依赖 Session，每次请求独立验证</li>
 *   <li>失败不阻断：认证失败返回 401，不抛出异常破坏请求链</li>
 *   <li>审计预留：认证成功后设置 SecurityContext，供 AOP 审计使用</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    private static final String BEARER_PREFIX = "Bearer ";

    /** 不需要认证的路径 */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/actuator/health",
            "/api/v1/actuator/info"
    };

    /** 静态资源路径前缀（无需认证） */
    private static final String[] STATIC_PATHS = {
            "/api/v1/index.html",
            "/api/v1/css/",
            "/api/v1/js/",
            "/api/v1/assets/",
            "/api/v1/echarts.min.js",
            "/api/v1/favicon.ico"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // 公开路径：跳过认证
        if (isPublicPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 静态资源路径：跳过认证
        if (isStaticPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 Token
        String token = extractToken(request);
        if (token == null) {
            sendUnauthorizedError(response, "缺少认证 Token，请先登录");
            return;
        }

        // 验证 Token
        if (!jwtTokenProvider.validateToken(token)) {
            sendUnauthorizedError(response, "Token 无效或已过期，请重新登录");
            return;
        }

        // 解析用户信息并设置安全上下文
        String username = jwtTokenProvider.getUsername(token);
        String role = jwtTokenProvider.getRole(token);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));

        // 存储额外信息到 details（供 AOP 审计使用）
        authentication.setDetails(new JwtUserDetails(username, role));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 记录访问
        log.debug("JWT 认证成功: username={}, role={}, path={}", username, role, requestPath);

        filterChain.doFilter(request, response);
    }

    /**
     * 从 Authorization Header 提取 Bearer Token。
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 判断是否为静态资源路径（无需认证）。
     */
    private boolean isStaticPath(String path) {
        for (String staticPath : STATIC_PATHS) {
            if (path.startsWith(staticPath)) {
                return true;
            }
        }
        // 根路径 "/api/v1/" 也放行
        return "/api/v1/".equals(path);
    }

    /**
     * 判断是否为公开路径（无需认证）。
     */
    private boolean isPublicPath(String path) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.startsWith(publicPath)) {
                return true;
            }
        }
        // OPTIONS 预检请求放行（CORS）
        return false;
    }

    /**
     * 返回 401 未授权 JSON 响应（不抛异常，优雅降级）。
     */
    private void sendUnauthorizedError(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Result<Void> errorResult = Result.unauthorized(message);
        objectMapper.writeValue(response.getWriter(), errorResult);
    }
}
