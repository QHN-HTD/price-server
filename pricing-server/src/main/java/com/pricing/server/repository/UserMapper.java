package com.pricing.server.repository;

import com.pricing.server.model.entity.SysUser;
import org.apache.ibatis.annotations.*;

/**
 * 用户表 Mapper — sys_user
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户（用于登录认证）。
     */
    @Select("SELECT id, username, password_hash, role, display_name, enabled, " +
            "last_login, created_at, updated_at FROM sys_user WHERE username = #{username}")
    @Results(id = "userResult", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "role", column = "role"),
            @Result(property = "displayName", column = "display_name"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "lastLogin", column = "last_login"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at"),
    })
    SysUser findByUsername(@Param("username") String username);

    /**
     * 更新最后登录时间。
     */
    @Update("UPDATE sys_user SET last_login = NOW() WHERE username = #{username}")
    int updateLastLogin(@Param("username") String username);
}
