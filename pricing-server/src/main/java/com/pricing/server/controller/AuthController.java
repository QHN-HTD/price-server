package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.model.dto.LoginRequest;
import com.pricing.server.model.dto.LoginResponse;
import com.pricing.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器 — 用户登录/登出
 *
 * @author PriceWise Team
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录：验证凭据 → 签发 JWT Token。
     *
     * @param request 登录请求（username + password）
     * @return Token + 用户信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return Result.ok("登录成功", response);
        } catch (BadCredentialsException e) {
            return Result.unauthorized("用户名或密码错误");
        }
    }

    /**
     * Token 验证接口（前端用于检查 Token 是否有效）。
     */
    @GetMapping("/verify")
    public Result<String> verify() {
        // 如果能走到这里（经过 JWT 过滤器验证），Token 就是有效的
        return Result.ok("Token 有效");
    }
}
