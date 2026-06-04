package com.pricing.server.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 波士顿矩阵 — 映射 fact_boston_matrix 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BostonMatrix implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String categoryName;
    private String yearQuarter;
    private String bostonQuadrant;       // Star/Cash Cow/Question Mark/Dog
    private String bostonQuadrantCn;     // 明星/金牛/问题/瘦狗
    private String bostonStrategy;       // 策略建议
    private Double relativeMarketShare;
    private Double qoqGrowthRate;
    private Double marketShare;
    private Double totalRevenue;
    private Long totalQuantity;
    private LocalDateTime createdAt;
}
