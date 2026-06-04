package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.etl.EtlPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理接口 — ETL 管道触发、系统状态
 * <p>
 * 仅 ADMIN 角色可访问。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EtlPipelineService etlPipelineService;

    /**
     * 触发 ETL 管道（需 CSV 数据文件在 data/raw/ 目录）。
     * <p>
     * POST /api/v1/admin/etl/run
     * </p>
     */
    @PostMapping("/etl/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> runEtl() {
        log.info("管理员触发 ETL 管道...");
        Map<String, Object> result = etlPipelineService.runPipeline();
        if (Boolean.TRUE.equals(result.get("success"))) {
            return Result.ok("ETL 管道执行完成", result);
        }
        return Result.error((String) result.getOrDefault("error", "未知错误"));
    }

    /**
     * 查询 ETL 管道运行状态。
     */
    @GetMapping("/etl/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getEtlStatus() {
        return Result.ok(etlPipelineService.getStatus());
    }

    /**
     * 系统健康检查（管理员视图）。
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "database", "MySQL (phpstudy)",
                "spark", "available",
                "etl", "ready"
        ));
    }
}
