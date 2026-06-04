package com.pricing.server.service;

import com.pricing.server.model.dto.WhatIfRequest;
import com.pricing.server.model.dto.WhatIfResponse;
import com.pricing.server.model.entity.Elasticity;
import com.pricing.server.repository.ElasticityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 价格弹性分析服务
 * <p>
 * 核心功能：
 * <ul>
 *   <li>查询品类弹性系数</li>
 *   <li>What-If 调价模拟：输入调价幅度 → 预测销量和营收变化</li>
 *   <li>弹性分布统计（高弹性/低弹性品类分类）</li>
 * </ul>
 * </p>
 *
 * <p><b>What-If 模拟算法：</b>
 * <pre>
 *   预测销量 = 当前销量 × (1 + β × ΔP%)
 *   预测营收 = 预测销量 × 新价格
 *   利润影响 = 预测营收 - 当前营收
 * </pre>
 * 其中 β = 价格弹性系数（负值表示涨价销量下降）
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticityService {

    private final ElasticityMapper elasticityMapper;

    /**
     * 获取所有品类的弹性系数列表。
     */
    public List<Elasticity> getAllElasticities() {
        return elasticityMapper.findAll();
    }

    /**
     * 获取高弹性品类（|β| > 1）。
     */
    public List<Elasticity> getHighElasticityCategories() {
        return elasticityMapper.findHighElasticity();
    }

    /**
     * 获取指定品类的弹性系数。
     */
    public Elasticity getElasticityByCategory(String categoryName) {
        return elasticityMapper.findByCategory(categoryName);
    }

    /**
     * 获取弹性等级分布统计。
     */
    public List<Map<String, Object>> getElasticityDistribution() {
        return elasticityMapper.countByLabel();
    }

    /**
     * What-If 调价模拟：预测调价后的销量和营收变化。
     *
     * @param request 调价请求（品类 + 调价幅度）
     * @return 模拟结果
     */
    public WhatIfResponse simulatePriceChange(WhatIfRequest request) {
        String categoryName = request.getCategoryName();
        double priceChangePct = request.getPriceChangePct() / 100.0; // 转为小数

        // 查询弹性系数
        Elasticity elasticity = elasticityMapper.findByCategory(categoryName);
        if (elasticity == null) {
            log.warn("What-If 模拟失败: 品类不存在 — {}", categoryName);
            throw new IllegalArgumentException("找不到品类: " + categoryName + " 的弹性数据");
        }

        double beta = elasticity.getElasticity();

        // 预测销量变化率：ΔQ% ≈ β × ΔP%
        // 使用弹性公式：Q_new = Q_current × (P_new / P_current)^β
        // 近似简化：ΔQ% = β × ΔP%
        double predictedSalesChangePct = beta * priceChangePct;

        // 生成模拟数据（基于弹性系数反推典型场景）
        // 当前价格：假设为弹性系数的标准化基准（实际应查询数据库）
        double currentPrice = 100.0;  // 基准价格
        double newPrice = currentPrice * (1 + priceChangePct);
        long currentSales = 10000;     // 基准销量（实际应从数据库获取）
        long predictedSales = Math.round(currentSales * (1 + predictedSalesChangePct));
        double currentRevenue = currentPrice * currentSales;
        double predictedRevenue = newPrice * predictedSales;
        double revenueChange = predictedRevenue - currentRevenue;
        double revenueChangePct = revenueChange / currentRevenue;

        // 决策建议
        String recommendation;
        String analysis;
        double profitImpact = revenueChange;

        if (predictedRevenue > currentRevenue * 1.05) {
            recommendation = "推荐调价";
            analysis = String.format(
                    "该品类弹性系数为 %.2f，属于%s。建议执行调价方案，预计营收增长 %.1f%%，利润影响约 ¥%.0f。",
                    beta, elasticity.getElasticityLabel(),
                    revenueChangePct * 100, profitImpact);
        } else if (predictedRevenue < currentRevenue * 0.95) {
            recommendation = "不建议";
            analysis = String.format(
                    "该品类弹性系数为 %.2f，属于%s。调价可能导致营收下降 %.1f%%。建议重新评估调价方案或考虑其他策略。",
                    beta, elasticity.getElasticityLabel(),
                    Math.abs(revenueChangePct) * 100);
        } else {
            recommendation = "谨慎操作";
            analysis = String.format(
                    "该品类弹性系数为 %.2f，属于%s。调价对营收影响有限（约 %.1f%%），建议配合其他营销手段同步推进。",
                    beta, elasticity.getElasticityLabel(),
                    revenueChangePct * 100);
        }

        return WhatIfResponse.builder()
                .categoryName(categoryName)
                .productId(request.getProductId())
                .currentPrice(currentPrice)
                .newPrice(Math.round(newPrice * 100.0) / 100.0)
                .priceChangePct(request.getPriceChangePct())
                .elasticity(beta)
                .predictedSalesChangePct(Math.round(predictedSalesChangePct * 10000.0) / 100.0)
                .currentSales(currentSales)
                .predictedSales(predictedSales)
                .currentRevenue(currentRevenue)
                .predictedRevenue(Math.round(predictedRevenue * 100.0) / 100.0)
                .revenueChange(Math.round(revenueChange * 100.0) / 100.0)
                .revenueChangePct(Math.round(revenueChangePct * 10000.0) / 100.0)
                .recommendation(recommendation)
                .analysis(analysis)
                .profitImpact(Math.round(profitImpact * 100.0) / 100.0)
                .chartLabel(priceChangePct > 0 ? "涨价" : "降价")
                .build();
    }
}
