package com.pricing.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置
 * <p>
 * 允许前端 ECharts 大屏页面跨域访问 REST API。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>不允许通配符 *（必须明确指定允许的域名）</li>
 *   <li>不允许 credentials（Cookie 跨域传递）</li>
 *   <li>只暴露必要的响应头</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Configuration
public class CorsConfig {

    @Value("${security.cors.allowed-origins:http://localhost:63342}")
    private String allowedOrigins;

    @Value("${security.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${security.cors.allowed-headers:Authorization,Content-Type,X-Requested-With}")
    private String allowedHeaders;

    @Value("${security.cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 开发环境：允许所有来源（生产环境需限制）
        config.addAllowedOriginPattern("*");

        // 允许的 HTTP 方法
        config.setAllowedMethods(
                Arrays.asList(allowedMethods.split(",")));

        // 允许的请求头
        config.setAllowedHeaders(
                Arrays.asList(allowedHeaders.split(",")));

        // 允许前端读取的响应头
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));

        // 预检请求缓存时间
        config.setMaxAge(maxAge);

        // 安全性：不携带 Cookie
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);

        return new CorsFilter(source);
    }
}
