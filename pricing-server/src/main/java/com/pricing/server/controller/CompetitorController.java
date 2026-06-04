package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.model.entity.CategoryLandscape;
import com.pricing.server.model.entity.CompetitorNiche;
import com.pricing.server.service.CompetitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 竞品分析控制器
 *
 * @author PriceWise Team
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/competitor")
@RequiredArgsConstructor
public class CompetitorController {

    private final CompetitorService competitorService;

    /**
     * 查询指定品类的卖家生态位分布（用于 3D 气泡图）。
     */
    @GetMapping("/niche/category")
    public Result<List<CompetitorNiche>> getNicheByCategory(
            @RequestParam String categoryName) {
        List<CompetitorNiche> result = competitorService.getNicheByCategory(categoryName);
        return Result.ok(result);
    }

    /**
     * 按生态位标签查询卖家（如"高质高价区"）。
     */
    @GetMapping("/niche/label")
    public Result<List<CompetitorNiche>> getNicheByLabel(
            @RequestParam String nicheLabel) {
        List<CompetitorNiche> result = competitorService.getNicheByLabel(nicheLabel);
        return Result.ok(result);
    }

    /**
     * 获取生态位分布统计（各品类各生态位的卖家数量）。
     */
    @GetMapping("/niche/distribution")
    public Result<List<Map<String, Object>>> getNicheDistribution() {
        List<Map<String, Object>> result = competitorService.getNicheDistribution();
        return Result.ok(result);
    }

    /**
     * 获取所有品类的竞争格局摘要。
     */
    @GetMapping("/landscape/all")
    public Result<List<CategoryLandscape>> getAllLandscapes() {
        List<CategoryLandscape> result = competitorService.getAllLandscapes();
        return Result.ok(result);
    }

    /**
     * 获取指定品类的竞争格局。
     */
    @GetMapping("/landscape/{categoryName}")
    public Result<CategoryLandscape> getLandscapeByCategory(
            @PathVariable String categoryName) {
        CategoryLandscape result = competitorService.getLandscapeByCategory(categoryName);
        if (result == null) {
            return Result.notFound("找不到品类: " + categoryName);
        }
        return Result.ok(result);
    }
}
