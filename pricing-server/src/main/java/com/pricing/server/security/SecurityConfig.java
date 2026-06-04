package com.pricing.server.security;

import com.pricing.server.config.RateLimiterConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置
 * <p>
 * 核心安全策略：
 * <ul>
 *   <li>无状态 JWT 认证（禁用 Session）</li>
 *   <li>RBAC 基于角色的访问控制</li>
 *   <li>CSRF 禁用（REST API 不需要）</li>
 *   <li>路径级别的权限控制</li>
 * </ul>
 * </p>
 *
 * <p><b>路径权限矩阵：</b>
 * <table>
 *   <tr><th>路径</th><th>PUBLIC</th><th>ANALYST</th><th>ADMIN</th></tr>
 *   <tr><td>/auth/**</td><td>✓</td><td>—</td><td>—</td></tr>
 *   <tr><td>/actuator/health</td><td>✓</td><td>—</td><td>—</td></tr>
 *   <tr><td>/dashboard/**</td><td>—</td><td>✓</td><td>✓</td></tr>
 *   <tr><td>/admin/**</td><td>—</td><td>—</td><td>✓</td></tr>
 * </table>
 * </p>
 *
 * @author PriceWise Team
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // 启用 @PreAuthorize 注解
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF（REST API 无 Cookie，不需要 CSRF 防护）
                .csrf(AbstractHttpConfigurer::disable)

                // 无状态会话（JWT 认证，不创建 HTTP Session）
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 路径权限配置
                .authorizeHttpRequests(auth -> auth
                        // 公开路径
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        // 静态资源
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/echarts.min.js", "/css/**", "/js/**", "/assets/**").permitAll()

                        // 管理员专用
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 竞品细节（含个体卖家数据）→ 仅管理员
                        .requestMatchers("/dashboard/competitor/niche/**").hasRole("ADMIN")

                        // 业务 API → 分析师 + 管理员
                        .requestMatchers("/dashboard/**").hasAnyRole("ANALYST", "ADMIN")

                        // 其余路径需要认证
                        .anyRequest().authenticated()
                )

                // 添加 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // 安全响应头（CSP 由前端 HTML meta 标签控制，此处仅设置 frame 防护）
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 密码编码器（强度=12，安全性与性能的平衡点）
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
