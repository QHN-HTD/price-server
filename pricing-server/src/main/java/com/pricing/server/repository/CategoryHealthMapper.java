package com.pricing.server.repository;

import com.pricing.server.model.entity.CategoryHealth;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 品类健康度表 Mapper — fact_category_health
 */
@Mapper
public interface CategoryHealthMapper {

    /**
     * 查询所有品类的健康度（按评分降序）。
     */
    @Select("SELECT id, category_name, total_quantity_all, total_revenue_all, " +
            "avg_price_weighted, avg_review_score_all, avg_return_rate, " +
            "avg_growth_rate, health_score, health_level, created_at " +
            "FROM fact_category_health " +
            "ORDER BY health_score DESC")
    List<CategoryHealth> findAll();

    /**
     * 按健康等级查询品类。
     */
    @Select("SELECT id, category_name, total_quantity_all, total_revenue_all, " +
            "avg_price_weighted, avg_review_score_all, avg_return_rate, " +
            "avg_growth_rate, health_score, health_level, created_at " +
            "FROM fact_category_health " +
            "WHERE health_level = #{level} " +
            "ORDER BY health_score DESC")
    List<CategoryHealth> findByLevel(@Param("level") String level);

    /**
     * 按健康等级统计品类数量。
     */
    @Select("SELECT health_level, COUNT(*) as count, " +
            "AVG(health_score) as avg_score, SUM(total_revenue_all) as total_revenue " +
            "FROM fact_category_health " +
            "GROUP BY health_level " +
            "ORDER BY avg_score DESC")
    List<Map<String, Object>> countByLevel();
}
