package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.model.dto.WhatIfRequest;
import com.pricing.server.model.dto.WhatIfResponse;
import com.pricing.server.model.entity.Elasticity;
import com.pricing.server.service.ElasticityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 价格弹性分析控制器
 *
 * @author PriceWise Team
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/elasticity")
@RequiredArgsConstructor
public class ElasticityController {

    private final ElasticityService elasticityService;

    /**
     * 获取所有品类的弹性系数列表。
     */
    @GetMapping("/all")
    public Result<List<Elasticity>> getAll() {
        List<Elasticity> result = elasticityService.getAllElasticities();
        return Result.ok(result);
    }

    /**
     * 获取指定品类的弹性系数。
     */
    @GetMapping("/{categoryName}")
    public Result<Elasticity> getByCategory(@PathVariable String categoryName) {
        Elasticity result = elasticityService.getElasticityByCategory(categoryName);
        if (result == null) {
            return Result.notFound("找不到品类: " + categoryName);
        }
        return Result.ok(result);
    }

    /**
     * 获取高弹性品类（|β| > 1）。
     */
    @GetMapping("/high-elasticity")
    public Result<List<Elasticity>> getHighElasticity() {
        List<Elasticity> result = elasticityService.getHighElasticityCategories();
        return Result.ok(result);
    }

    /**
     * 获取弹性等级分布统计。
     */
    @GetMapping("/distribution")
    public Result<List<Map<String, Object>>> getDistribution() {
        List<Map<String, Object>> result = elasticityService.getElasticityDistribution();
        return Result.ok(result);
    }

    // ── What-If 调价模拟器 ⭐ 核心功能 ──

    /**
     * What-If 调价模拟：输入调价方案 → 预测结果 → 策略建议。
     * <p>
     * 这是项目的核心亮点功能，面试中重点展示。
     * </p>
     *
     * @param request 调价请求（品类 + 调价幅度 -30% ~ +30%）
     * @return 预测销量/营收变化 + 决策建议
     */
    @PostMapping("/simulate")
    public Result<WhatIfResponse> simulatePriceChange(@Valid @RequestBody WhatIfRequest request) {
        try {
            WhatIfResponse result = elasticityService.simulatePriceChange(request);
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    /**
     * 批量 What-If 模拟：同一品类不同调价幅度对比。
     * 用于前端绘制调价-利润关系曲线。
     *
     * @param categoryName 品类名称
     * @return 多个调价幅度的模拟结果列表
     */
    @GetMapping("/simulate/batch")
    public Result<List<WhatIfResponse>> batchSimulate(
            @RequestParam String categoryName) {
        double[] scenarios = {-20.0, -15.0, -10.0, -5.0, 0.0, 5.0, 10.0, 15.0, 20.0};
        List<WhatIfResponse> results = new java.util.ArrayList<>();

        for (double pct : scenarios) {
            WhatIfRequest req = new WhatIfRequest();
            req.setCategoryName(categoryName);
            req.setPriceChangePct(pct);
            try {
                results.add(elasticityService.simulatePriceChange(req));
            } catch (Exception e) {
                log.warn("批量模拟失败: category={}, pct={}%, error={}", categoryName, pct, e.getMessage());
            }
        }

        return Result.ok(results);
    }
}
