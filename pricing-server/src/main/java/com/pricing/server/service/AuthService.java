package com.pricing.server.service;

import com.pricing.server.model.dto.LoginRequest;
import com.pricing.server.model.dto.LoginResponse;
import com.pricing.server.model.entity.SysUser;
import com.pricing.server.repository.UserMapper;
import com.pricing.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 认证服务 — 处理用户登录和 Token 签发
 * <p>
 * <b>安全设计：</b>
 * <ul>
 *   <li>使用 Spring Security AuthenticationManager 统一认证</li>
 *   <li>BCrypt 密码验证（不存储也不传输明文密码）</li>
 *   <li>登录成功后更新 last_login 时间戳</li>
 *   <li>Token 有效期 30 分钟（jwt.expiration 配置）</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    /**
     * 用户登录：验证凭据 → 签发 JWT Token。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（Token + 用户信息）
     * @throws BadCredentialsException 凭据无效
     */
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();

        // Step 1: Spring Security 认证（验证密码）
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, request.getPassword()));
        } catch (BadCredentialsException e) {
            log.warn("登录失败: 密码错误 — username={}", username);
            throw e;
        } catch (UsernameNotFoundException e) {
            log.warn("登录失败: 用户不存在 — username={}", username);
            throw new BadCredentialsException("用户名或密码错误");
        }

        // Step 2: 查询用户信息
        SysUser sysUser = userMapper.findByUsername(username);
        String role = sysUser.getRole();

        // Step 3: 签发 JWT Token
        String token = jwtTokenProvider.createToken(username, role);

        // Step 4: 更新最后登录时间
        userMapper.updateLastLogin(username);

        log.info("用户登录成功: username={}, role={}", username, role);

        // Step 5: 构造响应
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(java.util.concurrent.TimeUnit.MILLISECONDS
                        .toSeconds(jwtTokenProvider.getRemainingTime(token)))
                .username(username)
                .displayName(sysUser.getDisplayName())
                .role(role)
                .build();
    }
}
