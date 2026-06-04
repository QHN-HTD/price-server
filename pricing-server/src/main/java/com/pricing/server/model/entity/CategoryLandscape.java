package com.pricing.server.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 品类竞争格局 — 映射 fact_category_landscape 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryLandscape implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String categoryName;
    private Integer premiumCount;
    private Integer valueCount;
    private Integer volumeCount;
    private Integer redOceanCount;
    private Integer disadvantagedCount;
    private Double categoryAvgPrice;
    private Double categoryAvgScore;
    private Double categoryAvgCompetitiveness;
    private Integer totalSellers;
    private String competitionIntensity;
    private Double marketConcentration;
    private Double opportunityIndex;
    private LocalDateTime createdAt;
}
