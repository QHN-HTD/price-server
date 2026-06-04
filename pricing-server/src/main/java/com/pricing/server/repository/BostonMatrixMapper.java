package com.pricing.server.repository;

import com.pricing.server.model.entity.BostonMatrix;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 波士顿矩阵表 Mapper — fact_boston_matrix + fact_boston_distribution
 */
@Mapper
public interface BostonMatrixMapper {

    /**
     * 查询最新季度的波士顿矩阵快照。
     */
    @Select("SELECT id, category_name, year_quarter, boston_quadrant, boston_quadrant_cn, " +
            "boston_strategy, relative_market_share, qoq_growth_rate, market_share, " +
            "total_revenue, total_quantity, created_at " +
            "FROM fact_boston_matrix " +
            "ORDER BY boston_quadrant, relative_market_share DESC")
    List<BostonMatrix> findLatestSnapshot();

    /**
     * 按象限查询品类。
     */
    @Select("SELECT id, category_name, year_quarter, boston_quadrant, boston_quadrant_cn, " +
            "boston_strategy, relative_market_share, qoq_growth_rate, market_share, " +
            "total_revenue, total_quantity, created_at " +
            "FROM fact_boston_matrix " +
            "WHERE boston_quadrant = #{quadrant} " +
            "ORDER BY relative_market_share DESC")
    List<BostonMatrix> findByQuadrant(@Param("quadrant") String quadrant);

    /**
     * 查询象限分布统计。
     */
    @Select("SELECT boston_quadrant, boston_quadrant_cn, category_count, " +
            "quadrant_total_revenue, quadrant_avg_share, quadrant_avg_growth " +
            "FROM fact_boston_distribution " +
            "ORDER BY quadrant_total_revenue DESC")
    List<Map<String, Object>> findDistribution();

    /** 查询所有季度数据（用于迁移追踪） */
    @Select("SELECT id, category_name, year_quarter, boston_quadrant, boston_quadrant_cn, " +
            "boston_strategy, relative_market_share, qoq_growth_rate, market_share, " +
            "total_revenue, total_quantity, created_at " +
            "FROM fact_boston_matrix ORDER BY category_name, year_quarter")
    List<BostonMatrix> findAllQuarters();
}
