package com.pricing.server.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * What-If 调价模拟响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
public class WhatIfResponse {
    private String categoryName;
    private String productId;
    private Double currentPrice;
    private Double newPrice;
    private Double priceChangePct;
    private Double elasticity;           // 弹性系数

    // 预测结果
    private Double predictedSalesChangePct;  // 预测销量变化率
    private Long currentSales;               // 当前销量
    private Long predictedSales;             // 预测销量
    private Double currentRevenue;           // 当前营收
    private Double predictedRevenue;         // 预测营收
    private Double revenueChange;            // 营收变化绝对值
    private Double revenueChangePct;         // 营收变化率

    // 决策标签
    private String recommendation;      // 推荐调价 / 谨慎操作 / 不建议
    private String analysis;            // 详细分析文本

    // 可视化数据
    private Double profitImpact;        // 利润影响
    private String chartLabel;          // 瀑布图标签
}
