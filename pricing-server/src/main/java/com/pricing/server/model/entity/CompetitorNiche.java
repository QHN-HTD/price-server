package com.pricing.server.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 竞品生态位 — 映射 fact_competitor_niche 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorNiche implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String sellerId;           // 已脱敏
    private String categoryName;
    private Double avgPrice;
    private Double avgScore;
    private Long totalSales;
    private Double totalRevenue;
    private Double pricePercentile;
    private Double salesPercentile;
    private Double scorePercentile;
    private String nicheLabel;         // 生态位标签
    private String competitiveAdvice;  // 竞争策略
    private Double competitivenessScore;
    private LocalDateTime createdAt;
}
