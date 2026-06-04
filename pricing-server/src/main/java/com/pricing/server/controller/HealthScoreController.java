package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.model.entity.CategoryHealth;
import com.pricing.server.service.CategoryHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 品类健康度评分控制器
 *
 * @author PriceWise Team
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/health")
@RequiredArgsConstructor
public class HealthScoreController {

    private final CategoryHealthService healthService;

    /**
     * 获取所有品类的健康度评分（降序排列）。
     */
    @GetMapping("/all")
    public Result<List<CategoryHealth>> getAll() {
        List<CategoryHealth> result = healthService.getAllHealthScores();
        return Result.ok(result);
    }

    /**
     * 按健康等级查询品类。
     *
     * @param level 健康等级：A-优秀 / B-良好 / C-一般 / D-关注
     */
    @GetMapping("/level/{level}")
    public Result<List<CategoryHealth>> getByLevel(@PathVariable String level) {
        List<CategoryHealth> result = healthService.getByLevel(level);
        return Result.ok(result);
    }

    /**
     * 获取健康等级分布统计。
     */
    @GetMapping("/distribution")
    public Result<List<Map<String, Object>>> getDistribution() {
        List<Map<String, Object>> result = healthService.getHealthDistribution();
        return Result.ok(result);
    }

    /**
     * 获取健康度仪表盘汇总数据。
     */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        Map<String, Object> result = healthService.getHealthDashboard();
        return Result.ok(result);
    }

    /**
     * 获取健康度综合报告（用于 ECharts 雷达图）。
     */
    @GetMapping("/report")
    public Result<Map<String, Object>> getReport() {
        Map<String, Object> dashboard = healthService.getHealthDashboard();
        List<CategoryHealth> allScores = healthService.getAllHealthScores();

        // 取 Top 5 品类用于雷达图对比
        List<CategoryHealth> top5 = allScores.stream()
                .limit(5)
                .toList();

        List<Map<String, Object>> radarData = top5.stream()
                .map(h -> Map.<String, Object>of(
                        "name", h.getCategoryName(),
                        "healthScore", h.getHealthScore(),
                        "avgReviewScore", h.getAvgReviewScoreAll(),
                        "avgGrowthRate", h.getAvgGrowthRate(),
                        "totalRevenue", h.getTotalRevenueAll(),
                        "healthLevel", h.getHealthLevel()
                ))
                .toList();

        return Result.ok(Map.of(
                "dashboard", dashboard,
                "top5Radar", radarData
        ));
    }
}
