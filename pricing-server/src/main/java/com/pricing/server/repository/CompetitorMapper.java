package com.pricing.server.repository;

import com.pricing.server.model.entity.CategoryLandscape;
import com.pricing.server.model.entity.CompetitorNiche;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 竞品分析表 Mapper — fact_competitor_niche + fact_category_landscape
 */
@Mapper
public interface CompetitorMapper {

    // ── 卖家生态位 ──

    /**
     * 查询指定品类的所有卖家生态位（按竞争力评分降序）。
     */
    @Select("SELECT id, seller_id, category_name, avg_price, avg_score, " +
            "total_sales, total_revenue, price_percentile, sales_percentile, " +
            "score_percentile, niche_label, competitive_advice, competitiveness_score, created_at " +
            "FROM fact_competitor_niche " +
            "WHERE category_name = #{categoryName} " +
            "ORDER BY competitiveness_score DESC")
    List<CompetitorNiche> findByCategory(@Param("categoryName") String categoryName);

    /**
     * 查询指定生态位的所有卖家。
     */
    @Select("SELECT id, seller_id, category_name, avg_price, avg_score, " +
            "total_sales, total_revenue, price_percentile, sales_percentile, " +
            "score_percentile, niche_label, competitive_advice, competitiveness_score, created_at " +
            "FROM fact_competitor_niche " +
            "WHERE niche_label = #{nicheLabel} " +
            "ORDER BY competitiveness_score DESC")
    List<CompetitorNiche> findByNiche(@Param("nicheLabel") String nicheLabel);

    /**
     * 按品类统计各生态位卖家数量。
     */
    @Select("SELECT category_name, niche_label, COUNT(*) as seller_count, " +
            "AVG(avg_price) as avg_price, AVG(avg_score) as avg_score " +
            "FROM fact_competitor_niche " +
            "GROUP BY category_name, niche_label " +
            "ORDER BY category_name, niche_label")
    List<java.util.Map<String, Object>> countByCategoryAndNiche();

    // ── 品类竞争格局 ──

    /**
     * 查询所有品类的竞争格局。
     */
    @Select("SELECT id, category_name, premium_count, value_count, volume_count, " +
            "red_ocean_count, disadvantaged_count, category_avg_price, category_avg_score, " +
            "category_avg_competitiveness, total_sellers, competition_intensity, " +
            "market_concentration, opportunity_index, created_at " +
            "FROM fact_category_landscape " +
            "ORDER BY total_sellers DESC")
    List<CategoryLandscape> findAllLandscapes();

    /**
     * 根据品类查询竞争格局。
     */
    @Select("SELECT id, category_name, premium_count, value_count, volume_count, " +
            "red_ocean_count, disadvantaged_count, category_avg_price, category_avg_score, " +
            "category_avg_competitiveness, total_sellers, competition_intensity, " +
            "market_concentration, opportunity_index, created_at " +
            "FROM fact_category_landscape " +
            "WHERE category_name = #{categoryName}")
    CategoryLandscape findLandscapeByCategory(@Param("categoryName") String categoryName);
}
