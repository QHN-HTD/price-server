package com.pricing.server.repository;

import org.apache.ibatis.annotations.*;

/**
 * 审计日志表 Mapper — audit_log
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 插入审计日志。
     *
     * @param username       操作用户
     * @param operation      操作描述
     * @param apiPath        API 路径
     * @param clientIp       客户端 IP
     * @param requestParams  请求参数摘要
     * @param responseStatus 响应状态码
     * @param executionTime  执行耗时 (ms)
     */
    @Insert("INSERT INTO audit_log (username, operation, api_path, client_ip, " +
            "request_params, response_status, execution_time) " +
            "VALUES (#{username}, #{operation}, #{apiPath}, #{clientIp}, " +
            "#{requestParams}, #{responseStatus}, #{executionTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(@Param("username") String username,
               @Param("operation") String operation,
               @Param("apiPath") String apiPath,
               @Param("clientIp") String clientIp,
               @Param("requestParams") String requestParams,
               @Param("responseStatus") Integer responseStatus,
               @Param("executionTime") Long executionTime);
}
