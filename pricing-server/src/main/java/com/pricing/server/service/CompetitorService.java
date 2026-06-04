package com.pricing.server.service;

import com.pricing.server.model.entity.CategoryLandscape;
import com.pricing.server.model.entity.CompetitorNiche;
import com.pricing.server.repository.CompetitorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 竞品分析服务
 * <p>
 * 提供品类竞争格局和卖家生态位查询。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>卖家ID已通过 Spark ETL 脱敏处理（SHA-256）</li>
 *   <li>不包含任何真实身份信息</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final CompetitorMapper competitorMapper;

    /**
     * 查询指定品类的卖家生态位分布。
     */
    public List<CompetitorNiche> getNicheByCategory(String categoryName) {
        return competitorMapper.findByCategory(categoryName);
    }

    /**
     * 按生态位标签查询卖家。
     */
    public List<CompetitorNiche> getNicheByLabel(String nicheLabel) {
        return competitorMapper.findByNiche(nicheLabel);
    }

    /**
     * 获取各品类各生态位的卖家数量统计（用于前端图表）。
     */
    public List<Map<String, Object>> getNicheDistribution() {
        return competitorMapper.countByCategoryAndNiche();
    }

    /**
     * 获取所有品类的竞争格局摘要。
     */
    public List<CategoryLandscape> getAllLandscapes() {
        return competitorMapper.findAllLandscapes();
    }

    /**
     * 获取指定品类的竞争格局。
     */
    public CategoryLandscape getLandscapeByCategory(String categoryName) {
        return competitorMapper.findLandscapeByCategory(categoryName);
    }
}
