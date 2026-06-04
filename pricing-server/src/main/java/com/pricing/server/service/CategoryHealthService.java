package com.pricing.server.service;

import com.pricing.server.model.entity.CategoryHealth;
import com.pricing.server.repository.CategoryHealthMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 品类健康度评分服务
 * <p>
 * 提供品类综合健康度评分的查询和统计分析。
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryHealthService {

    private final CategoryHealthMapper healthMapper;

    /**
     * 获取所有品类的健康度评分（降序）。
     */
    public List<CategoryHealth> getAllHealthScores() {
        return healthMapper.findAll();
    }

    /**
     * 按健康等级查询品类。
     */
    public List<CategoryHealth> getByLevel(String level) {
        return healthMapper.findByLevel(level);
    }

    /**
     * 获取健康等级分布统计。
     */
    public List<Map<String, Object>> getHealthDistribution() {
        return healthMapper.countByLevel();
    }

    /**
     * 获取健康度仪表盘数据（汇总统计）。
     */
    public Map<String, Object> getHealthDashboard() {
        List<CategoryHealth> allScores = healthMapper.findAll();
        List<Map<String, Object>> distribution = healthMapper.countByLevel();

        // 计算汇总统计
        double avgHealth = allScores.stream()
                .mapToDouble(CategoryHealth::getHealthScore)
                .average()
                .orElse(0.0);

        long excellentCount = allScores.stream()
                .filter(h -> "A-优秀".equals(h.getHealthLevel()))
                .count();

        long attentionCount = allScores.stream()
                .filter(h -> "D-关注".equals(h.getHealthLevel()))
                .count();

        return Map.of(
                "totalCategories", allScores.size(),
                "avgHealthScore", Math.round(avgHealth * 10.0) / 10.0,
                "excellentCount", excellentCount,
                "attentionCount", attentionCount,
                "distribution", distribution
        );
    }
}
