package com.pricing.server.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 价格弹性系数 — 映射 fact_elasticity 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Elasticity implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String categoryName;
    private Double elasticity;        // 弹性系数 β
    private Double intercept;         // 截距 α
    private Double rSquared;          // R²
    private Double pValue;            // p-value
    private Integer sampleSize;       // 样本量
    private String elasticityLabel;   // 解释标签
    private String pricingStrategy;   // 策略建议
    private String modelVersion;
    private LocalDateTime createdAt;
}
