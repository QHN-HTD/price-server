package com.pricing.server.controller;

import com.pricing.server.common.Result;
import com.pricing.server.model.entity.BostonMatrix;
import com.pricing.server.service.BostonMatrixService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 波士顿矩阵控制器
 *
 * @author PriceWise Team
 */
@Slf4j
@RestController
@RequestMapping("/dashboard/boston-matrix")
@RequiredArgsConstructor
public class BostonMatrixController {

    private final BostonMatrixService bostonMatrixService;

    /**
     * 获取最新季度波士顿矩阵快照（用于四象限散点气泡图）。
     */
    @GetMapping("/snapshot")
    public Result<List<BostonMatrix>> getLatestSnapshot() {
        List<BostonMatrix> result = bostonMatrixService.getLatestSnapshot();
        return Result.ok(result);
    }

    /**
     * 按象限查询品类。
     *
     * @param quadrant 象限名称：Star / Cash Cow / Question Mark / Dog
     */
    @GetMapping("/quadrant/{quadrant}")
    public Result<List<BostonMatrix>> getByQuadrant(@PathVariable String quadrant) {
        List<BostonMatrix> result = bostonMatrixService.getByQuadrant(quadrant);
        return Result.ok(result);
    }

    /**
     * 获取象限分布统计。
     */
    @GetMapping("/distribution")
    public Result<List<Map<String, Object>>> getDistribution() {
        List<Map<String, Object>> result = bostonMatrixService.getDistribution();
        return Result.ok(result);
    }

    /**
     * 获取波士顿矩阵完整数据（含配色方案，前端直接渲染）。
     */
    @GetMapping("/full")
    public Result<Map<String, Object>> getFullMatrixData() {
        Map<String, Object> result = bostonMatrixService.getFullMatrixData();
        return Result.ok(result);
    }
}
