package com.pricing.server.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * What-If 调价模拟请求 DTO
 */
@Data
public class WhatIfRequest {

    @NotBlank(message = "品类名称不能为空")
    private String categoryName;

    /** 调价幅度（-30% ~ +30%） */
    @NotNull(message = "调价幅度不能为空")
    @Min(value = -30, message = "降价幅度不能超过30%")
    @Max(value = 30, message = "涨价幅度不能超过30%")
    private Double priceChangePct;

    /** 可选：指定SKU级别调价（为空则品类级别） */
    private String productId;
}
