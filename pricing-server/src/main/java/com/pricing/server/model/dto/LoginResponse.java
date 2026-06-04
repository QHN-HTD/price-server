package com.pricing.server.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType;        // "Bearer"
    private long expiresIn;          // 秒
    private String username;
    private String displayName;
    private String role;
}
