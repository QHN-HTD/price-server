package com.pricing.server.security;

import com.pricing.server.model.entity.SysUser;
import com.pricing.server.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Spring Security UserDetailsService 实现
 * <p>
 * 从 MySQL sys_user 表加载用户信息，用于登录认证。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>密码使用 BCrypt 存储（数据库只存哈希值）</li>
 *   <li>禁用用户无法登录（enabled=false）</li>
 *   <li>统一异常消息，不泄露用户是否存在的信息</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser sysUser = userMapper.findByUsername(username);

        if (sysUser == null) {
            log.warn("登录失败: 用户不存在 — username={}", username);
            // 统一错误信息，防止用户名枚举攻击
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        if (sysUser.getEnabled() == null || !sysUser.getEnabled()) {
            log.warn("登录失败: 用户已禁用 — username={}", username);
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        // 转换为 Spring Security UserDetails
        return new User(
                sysUser.getUsername(),
                sysUser.getPasswordHash(),
                Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + sysUser.getRole()))
        );
    }
}
