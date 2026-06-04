package com.pricing.server.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

/**
 * JWT 解析后的用户信息（存储在 SecurityContext 的 details 中）。
 * 供 AOP 审计日志和业务层使用。
 *
 * @author PriceWise Team
 */
@Getter
@AllArgsConstructor
public class JwtUserDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String role;

    /**
     * 判断是否为管理员角色。
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 判断是否为分析师角色。
     */
    public boolean isAnalyst() {
        return "ANALYST".equalsIgnoreCase(role);
    }
}
