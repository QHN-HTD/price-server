package com.pricing.server.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品类健康度 — 映射 fact_category_health 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryHealth implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String categoryName;
    private Long totalQuantityAll;
    private Double totalRevenueAll;
    private Double avgPriceWeighted;
    private Double avgReviewScoreAll;
    private Double avgReturnRate;
    private Double avgGrowthRate;
    private Double healthScore;
    private String healthLevel;          // A-优秀/B-良好/C-一般/D-关注
    private LocalDateTime createdAt;
}
