-- ============================================================
-- PriceWise — 模拟数据填充脚本
-- ============================================================
-- 用途: 在未运行 Spark ETL 的情况下，为 Dashboard 提供演示数据
-- 执行: mysql -u root -proot < data/sql/02-seed-data.sql
-- ============================================================

USE smart_pricing;

-- 清空旧数据
TRUNCATE fact_elasticity;
TRUNCATE fact_competitor_niche;
TRUNCATE fact_category_landscape;
TRUNCATE fact_boston_matrix;
TRUNCATE fact_boston_distribution;
TRUNCATE fact_category_health;

-- ============================================================
-- 1. 价格弹性系数 (15个品类)
-- ============================================================
INSERT INTO fact_elasticity (category_name, elasticity, intercept, r_squared, p_value, sample_size, elasticity_label, pricing_strategy) VALUES
('健康美容',      -2.35, 8.92, 0.78, 0.001, 24, '极高弹性', '降价促销效果显著，建议小幅降价（5-10%）抢占市场份额。涨价风险极高。'),
('手表礼品',      -1.89, 7.45, 0.72, 0.003, 24, '高弹性', '价格敏感品类，建议中幅降价（10-15%）配合营销活动提升销量。谨慎涨价。'),
('运动休闲',     -1.62, 6.88, 0.69, 0.005, 24, '高弹性', '价格敏感品类，建议中幅降价（10-15%）配合营销活动提升销量。谨慎涨价。'),
('汽车配件',   -1.34, 7.12, 0.65, 0.008, 24, '高弹性', '价格敏感品类，建议中幅降价（10-15%）配合营销活动提升销量。谨慎涨价。'),
('家具装饰',    -0.95, 8.23, 0.58, 0.015, 24, '中等弹性', '适度价格敏感，可通过差异化服务（包邮、赠品）替代直接降价。小幅调价影响可控。'),
('电子数码',        -0.88, 9.15, 0.62, 0.012, 24, '中等弹性', '适度价格敏感，可通过差异化服务（包邮、赠品）替代直接降价。小幅调价影响可控。'),
('电脑配件', -0.76, 8.55, 0.55, 0.020, 24, '中等弹性', '适度价格敏感，可通过差异化服务（包邮、赠品）替代直接降价。小幅调价影响可控。'),
('时尚服饰',   -0.62, 10.21, 0.48, 0.035, 24, '中等弹性', '适度价格敏感，可通过差异化服务（包邮、赠品）替代直接降价。小幅调价影响可控。'),
('家用电器',    -0.45, 9.87, 0.41, 0.050, 24, '低弹性', '价格不敏感，建议小幅涨价（3-5%）提升毛利率。降价对销量刺激有限。'),
('图书教育',    -0.38, 7.66, 0.44, 0.045, 24, '低弹性', '价格不敏感，建议小幅涨价（3-5%）提升毛利率。降价对销量刺激有限。'),
('母婴用品',         -0.35, 8.34, 0.52, 0.030, 24, '低弹性', '价格不敏感，建议小幅涨价（3-5%）提升毛利率。降价对销量刺激有限。'),
('宠物用品',       -0.28, 7.89, 0.46, 0.042, 24, '低弹性', '价格不敏感，建议小幅涨价（3-5%）提升毛利率。降价对销量刺激有限。'),
('食品饮料',     -0.15, 9.45, 0.38, 0.080, 24, '极低弹性', '必需品/垄断品类，涨价空间大。建议阶梯式涨价（每季度+3%），同时监测流失率。'),
('建材五金', -0.18, 8.12, 0.35, 0.075, 24, '极低弹性', '必需品/垄断品类，涨价空间大。建议阶梯式涨价（每季度+3%），同时监测流失率。'),
('办公用品',    -0.22, 7.56, 0.40, 0.065, 24, '极低弹性', '必需品/垄断品类，涨价空间大。建议阶梯式涨价（每季度+3%），同时监测流失率。');

-- ============================================================
-- 2. 竞品生态位数据 (模拟60个卖家分布在5个生态位)
-- ============================================================
INSERT INTO fact_competitor_niche (seller_id, category_name, avg_price, avg_score, total_sales, total_revenue, price_percentile, sales_percentile, score_percentile, niche_label, competitive_advice, competitiveness_score)
SELECT
    CONCAT('seller_', LPAD(n, 4, '0')),
    ELT(FLOOR(1 + RAND() * 5), '健康美容', '电子数码', '时尚服饰', '运动休闲', '家具装饰'),
    -- 价格: 30-500
    30 + RAND() * 470,
    -- 评分: 2.0-5.0
    2.0 + RAND() * 3.0,
    -- 销量: 50-5000
    50 + FLOOR(RAND() * 4950),
    0, 0, 0, 0, '', '', 0
FROM (
    SELECT @row := @row + 1 AS n FROM
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
    (SELECT 0 UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) b,
    (SELECT @row := 0) r
) t
LIMIT 60;

-- 更新营收和百分位
UPDATE fact_competitor_niche SET total_revenue = avg_price * total_sales;

-- 计算品类内百分位 (简化: 用全局近似)
UPDATE fact_competitor_niche SET
    price_percentile = ROUND((avg_price - 30) / 470, 2),
    sales_percentile = ROUND((total_sales - 50) / 4950, 2),
    score_percentile = ROUND((avg_score - 2.0) / 3.0, 2);

-- 生态位分类
UPDATE fact_competitor_niche SET
    niche_label = CASE
        WHEN price_percentile > 0.66 AND score_percentile > 0.66 THEN '高质高价区'
        WHEN price_percentile < 0.33 AND score_percentile > 0.66 THEN '性价比区'
        WHEN price_percentile < 0.33 AND sales_percentile > 0.66 THEN '低价冲量区'
        WHEN price_percentile > 0.66 AND score_percentile < 0.33 AND sales_percentile < 0.33 THEN '劣势区'
        ELSE '红海竞争区'
    END,
    competitive_advice = CASE
        WHEN price_percentile > 0.66 AND score_percentile > 0.66 THEN '保持品牌溢价定位，强化产品差异化卖点。监控竞品降价动态。'
        WHEN price_percentile < 0.33 AND score_percentile > 0.66 THEN '扩大口碑优势，适度提价（3-5%）接近市场中位价。'
        WHEN price_percentile < 0.33 AND sales_percentile > 0.66 THEN '优化供应链降低成本，保持价格优势。逐步提升评分向性价比区迁移。'
        WHEN price_percentile > 0.66 AND score_percentile < 0.33 THEN '⚠ 急需调整：降级至市场中位价，同步提升产品品质和售后服务。'
        ELSE '寻找细分市场差异化机会。分析头部卖家的成功策略。'
    END,
    competitiveness_score = ROUND(score_percentile * 40 + sales_percentile * 35 +
        CASE WHEN price_percentile BETWEEN 0.3 AND 0.7 THEN 20
             WHEN price_percentile BETWEEN 0.15 AND 0.85 THEN 12.5
             ELSE 5 END, 1);

-- ============================================================
-- 3. 品类竞争格局摘要
-- ============================================================
INSERT INTO fact_category_landscape (category_name, premium_count, value_count, volume_count, red_ocean_count, disadvantaged_count, category_avg_price, category_avg_score, category_avg_competitiveness, total_sellers, competition_intensity, market_concentration, opportunity_index)
SELECT
    '健康美容', 3, 4, 3, 2, 1, 185.50, 3.8, 62.5, 13, '激烈', 0.23, 0.54 UNION ALL
SELECT '电子数码', 4, 2, 3, 3, 1, 320.00, 3.6, 58.2, 13, '激烈', 0.31, 0.38 UNION ALL
SELECT '时尚服饰', 2, 5, 4, 1, 0, 95.30, 3.9, 68.7, 12, '中等', 0.17, 0.75 UNION ALL
SELECT '运动休闲', 3, 3, 2, 3, 1, 150.20, 3.7, 55.8, 12, '中等', 0.25, 0.42 UNION ALL
SELECT '家具装饰', 1, 2, 3, 3, 1, 240.80, 3.4, 48.3, 10, '温和', 0.10, 0.50;

-- ============================================================
-- 4. 波士顿矩阵数据 (最新季度快照 2018-Q3)
-- ============================================================
-- 注意: boston_quadrant/boston_quadrant_cn/boston_strategy 由后端根据 relative_market_share 和 qoq_growth_rate 动态计算
--       分界线使用所有品类的中位数，确保分类随数据变化而自动调整
INSERT INTO fact_boston_matrix (category_name, year_quarter, relative_market_share, qoq_growth_rate, market_share, total_revenue, total_quantity) VALUES
('健康美容',      '2018-Q3', 0.85, 0.18, 0.22, 285000, 4200),
('运动休闲',      '2018-Q3', 0.72, 0.15, 0.18, 220000, 3800),
('时尚服饰',      '2018-Q3', 0.68, 0.12, 0.17, 195000, 5600),
('电子数码',      '2018-Q3', 0.92, 0.02, 0.24, 420000, 2800),
('家用电器',      '2018-Q3', 0.78, 0.03, 0.14, 250000, 1500),
('电脑配件',      '2018-Q3', 0.65,-0.01, 0.12, 180000, 2200),
('手表礼品',      '2018-Q3', 0.25, 0.22, 0.08, 95000, 1200),
('汽车配件',      '2018-Q3', 0.18, 0.19, 0.06, 72000, 980),
('母婴用品',      '2018-Q3', 0.15, 0.14, 0.05, 58000, 1100),
('宠物用品',      '2018-Q3', 0.12, 0.16, 0.04, 48000, 850),
('家具装饰',      '2018-Q3', 0.08,-0.05, 0.03, 35000, 420),
('图书教育',      '2018-Q3', 0.06,-0.08, 0.02, 28000, 650),
('办公用品',      '2018-Q3', 0.04,-0.03, 0.02, 18000, 380),
('食品饮料',      '2018-Q3', 0.02,-0.06, 0.01, 12000, 290),
('建材五金',      '2018-Q3', 0.01,-0.04, 0.01, 8500, 150);

-- ============================================================
-- 5. 波士顿矩阵象限分布
-- ============================================================
INSERT INTO fact_boston_distribution (boston_quadrant, boston_quadrant_cn, category_count, quadrant_total_revenue, quadrant_avg_share, quadrant_avg_growth) VALUES
('Star',          '明星', 3, 700000,  0.19, 0.15),
('Cash Cow',      '金牛', 3, 850000,  0.17, 0.01),
('Question Mark', '问题', 4, 273000,  0.06, 0.18),
('Dog',           '瘦狗', 5, 101500,  0.02, -0.05);

-- ============================================================
-- 6. 品类健康度评分
-- ============================================================
INSERT INTO fact_category_health (category_name, total_quantity_all, total_revenue_all, avg_price_weighted, avg_review_score_all, avg_return_rate, avg_growth_rate, health_score, health_level) VALUES
('时尚服饰', 28000, 520000, 82.50, 4.2, 0.04, 0.12, 85.5, 'A-优秀'),
('健康美容', 22000, 485000, 95.30, 4.1, 0.05, 0.14, 82.3, 'A-优秀'),
('运动休闲', 18500, 380000, 120.00, 3.9, 0.06, 0.10, 76.8, 'A-优秀'),
('电子数码', 12000, 620000, 280.50, 3.7, 0.08, 0.03, 72.1, 'B-良好'),
('家用电器', 8500, 420000, 310.20, 3.8, 0.05, 0.02, 70.5, 'B-良好'),
('电脑配件', 10500, 350000, 250.00, 3.6, 0.07, 0.01, 68.2, 'B-良好'),
('手表礼品', 6200, 180000, 195.00, 3.9, 0.04, 0.18, 65.8, 'B-良好'),
('母婴用品', 5500, 110000, 72.00, 4.0, 0.03, 0.11, 63.5, 'C-一般'),
('汽车配件', 4800, 135000, 210.00, 3.5, 0.09, 0.15, 58.2, 'C-一般'),
('宠物用品', 4200, 95000, 68.50, 3.8, 0.04, 0.12, 56.7, 'C-一般'),
('家具装饰', 2200, 72000, 260.00, 3.3, 0.10, -0.03, 42.5, 'C-一般'),
('图书教育', 3200, 55000, 45.00, 3.5, 0.06, -0.05, 38.8, 'D-关注'),
('食品饮料', 1500, 22000, 35.00, 3.2, 0.02, -0.04, 35.2, 'D-关注'),
('办公用品', 1800, 32000, 55.00, 3.1, 0.08, -0.02, 32.6, 'D-关注'),
('建材五金', 800, 15000, 85.00, 3.0, 0.11, -0.06, 25.4, 'D-关注');

-- ============================================================
-- 完成
-- ============================================================
SELECT '✅ 模拟数据填充完成' AS status;
SELECT COUNT(*) AS elasticity_count FROM fact_elasticity;
SELECT COUNT(*) AS competitor_count FROM fact_competitor_niche;
SELECT COUNT(*) AS boston_count FROM fact_boston_matrix;
SELECT COUNT(*) AS health_count FROM fact_category_health;
