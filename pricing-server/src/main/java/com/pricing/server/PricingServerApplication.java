package com.pricing.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PriceWise — Spring Boot 主入口
 * <p>
 * 电商智能定价决策引擎的 REST API 服务端。
 * 提供价格弹性、竞品分析、波士顿矩阵、健康度评分等数据接口。
 * </p>
 *
 * <p><b>安全架构：</b>
 * <ul>
 *   <li>Spring Security + JWT 无状态认证</li>
 *   <li>RBAC 双角色：ADMIN（全量数据）/ ANALYST（聚合数据）</li>
 *   <li>AOP 审计日志全链路记录</li>
 *   <li>Guava RateLimiter 接口限流</li>
 *   <li>XSS 过滤器 + CSP 响应头</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
@SpringBootApplication
public class PricingServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingServerApplication.class, args);
        System.out.println("""

                ╔══════════════════════════════════════════════════╗
                ║   PriceWise — API Server v1.0.0      ║
                ║   Swagger: http://localhost:8080/api/v1/docs    ║
                ║   Health:  http://localhost:8080/api/v1/actuator/health ║
                ╚══════════════════════════════════════════════════╝
                """);
    }
}
