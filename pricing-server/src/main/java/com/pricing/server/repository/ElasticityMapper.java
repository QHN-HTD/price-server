package com.pricing.server.repository;

import com.pricing.server.model.entity.Elasticity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 价格弹性表 Mapper — fact_elasticity
 */
@Mapper
public interface ElasticityMapper {

    /**
     * 查询所有品类的弹性系数（按弹性绝对值降序）。
     */
    @Select("SELECT id, category_name, elasticity, intercept, r_squared, p_value, " +
            "sample_size, elasticity_label, pricing_strategy, model_version, created_at " +
            "FROM fact_elasticity ORDER BY ABS(elasticity) DESC")
    List<Elasticity> findAll();

    /**
     * 根据品类名查询弹性系数。
     */
    @Select("SELECT id, category_name, elasticity, intercept, r_squared, p_value, " +
            "sample_size, elasticity_label, pricing_strategy, model_version, created_at " +
            "FROM fact_elasticity WHERE category_name = #{categoryName}")
    Elasticity findByCategory(@Param("categoryName") String categoryName);

    /**
     * 查询高弹性品类（|β| > 1）。
     */
    @Select("SELECT id, category_name, elasticity, intercept, r_squared, p_value, " +
            "sample_size, elasticity_label, pricing_strategy, model_version, created_at " +
            "FROM fact_elasticity WHERE ABS(elasticity) > 1.0 ORDER BY ABS(elasticity) DESC")
    List<Elasticity> findHighElasticity();

    /**
     * 按弹性等级统计品类数量。
     */
    @Select("SELECT elasticity_label, COUNT(*) as count " +
            "FROM fact_elasticity GROUP BY elasticity_label ORDER BY count DESC")
    List<java.util.Map<String, Object>> countByLabel();
}
