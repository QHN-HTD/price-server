package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.*;

/**
 * 竞品价格生态位画像模型
 * <p>
 * 在同一品类下，将不同卖家视为天然竞品，从
 * <b>价格 × 评分 × 销量</b> 三个维度构建立体竞争画像。
 * </p>
 *
 * <p><b>生态位分类：</b>
 * <table>
 *   <tr><th>区域</th><th>价格</th><th>评分</th><th>销量</th><th>策略含义</th></tr>
 *   <tr><td>高质高价区</td><td>高(>P66)</td><td>高(>P66)</td><td>中</td><td>品牌溢价，强化差异化</td></tr>
 *   <tr><td>性价比区</td><td>低(<P33)</td><td>高(>P66)</td><td>中高</td><td>口碑驱动，扩大优势</td></tr>
 *   <tr><td>低价冲量区</td><td>低(<P33)</td><td>中</td><td>高(>P66)</td><td>规模效应，控制成本</td></tr>
 *   <tr><td>红海竞争区</td><td>中</td><td>中</td><td>中</td><td>差异化突围或退出</td></tr>
 *   <tr><td>劣势区</td><td>高(>P66)</td><td>低(<P33)</td><td>低</td><td>调整定价或提升品质</td></tr>
 * </table>
 * </p>
 *
 * <p><b>安全设计：</b>使用脱敏后的卖家ID和聚合数据，
 * 不涉及真实身份信息。</p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class CompetitorAnalysisModel {

    private final SparkSession spark;

    public CompetitorAnalysisModel(SparkSession spark) {
        this.spark = spark;
    }

    /**
     * 竞品生态位分析结果。
     */
    public static class NicheResult {
        public String sellerId;
        public String categoryName;
        public double avgPrice;
        public double avgScore;
        public long totalSales;
        public double totalRevenue;
        public double pricePercentile;   // 价格在品类内的百分位 (0-1)
        public double salesPercentile;    // 销量在品类内的百分位 (0-1)
        public double scorePercentile;    // 评分在品类内的百分位 (0-1)
        public String nicheLabel;         // 生态位标签
        public String competitiveAdvice;  // 竞争策略建议
    }

    /**
     * 基于卖家-品类竞争特征表，对每个卖家进行生态位分类。
     *
     * @param sellerCategoryFeatures 卖家-品类竞争特征表（来自 FeatureEngineer）
     * @return 生态位分析结果 DataFrame（可直接写 MySQL）
     */
    public Dataset<Row> classifyNiche(Dataset<Row> sellerCategoryFeatures) {
        System.out.println("=== 竞品生态位画像分析 ===");

        // 基于百分位进行生态位分类
        Dataset<Row> classified = sellerCategoryFeatures
                .withColumn("niche_label",
                        // 高质高价区: 价格 > P66, 评分 > P66
                        when(col("price_percentile").gt(0.66)
                                .and(col("score_percentile").gt(0.66)), lit("高质高价区"))
                                // 性价比区: 价格 < P33, 评分 > P66
                                .when(col("price_percentile").lt(0.33)
                                        .and(col("score_percentile").gt(0.66)), lit("性价比区"))
                                // 低价冲量区: 价格 < P33, 销量 > P66
                                .when(col("price_percentile").lt(0.33)
                                        .and(col("sales_percentile").gt(0.66)), lit("低价冲量区"))
                                // 劣势区: 价格 > P66, 评分 < P33, 销量 < P33
                                .when(col("price_percentile").gt(0.66)
                                        .and(col("score_percentile").lt(0.33))
                                        .and(col("sales_percentile").lt(0.33)), lit("劣势区"))
                                // 红海竞争区: 其余
                                .otherwise(lit("红海竞争区")))
                // 竞争策略建议
                .withColumn("competitive_advice",
                        when(col("niche_label").equalTo("高质高价区"),
                                lit("保持品牌溢价定位，强化产品差异化卖点。监控竞品降价动态，防止高端客户流失。"))
                                .when(col("niche_label").equalTo("性价比区"),
                                        lit("扩大口碑优势，适度提价（3-5%）接近市场中位价。利用高评分吸引价格敏感度低的客户。"))
                                .when(col("niche_label").equalTo("低价冲量区"),
                                        lit("优化供应链降低成本，保持价格优势。逐步提升评分，向性价比区迁移。"))
                                .when(col("niche_label").equalTo("劣势区"),
                                        lit("⚠ 急需调整：降级至市场中位价，同步提升产品品质和售后服务。否则建议考虑退出该品类。"))
                                .otherwise(lit("寻找细分市场差异化机会。分析头部卖家的成功策略，选择价格/品质/服务某一维度突破。")))
                // 综合竞争力评分 (0-100)
                .withColumn("competitiveness_score",
                        col("score_percentile").multiply(40)     // 评分权重40%
                                .plus(col("sales_percentile").multiply(35))  // 销量权重35%
                                .plus(
                                        // 价格适中更优（峰值在P40-P60区间）
                                        when(col("price_percentile").between(0.3, 0.7), lit(0.8))
                                                .when(col("price_percentile").between(0.15, 0.85), lit(0.5))
                                                .otherwise(lit(0.2))
                                                .multiply(25)                            // 价格适中权重25%
                                ));

        long count = classified.count();
        System.out.println("生态位分类完成: " + count + " 条卖家-品类记录");

        // 输出品类级别的生态位分布统计
        System.out.println("\n品类生态位分布概览:");
        classified.groupBy("category_name", "niche_label")
                .agg(count(lit(1)).alias("seller_count"))
                .orderBy("category_name", "niche_label")
                .show(50, false);

        return classified;
    }

    /**
     * 构建品类级别的竞争格局摘要。
     * 用于 ECharts 3D 气泡图可视化。
     *
     * @param classifiedNiche 生态位分类结果
     * @return 品类竞争格局摘要 DataFrame
     */
    public Dataset<Row> buildCategoryLandscape(Dataset<Row> classifiedNiche) {
        System.out.println("=== 构建品类竞争格局摘要 ===");

        Dataset<Row> landscape = classifiedNiche.groupBy("category_name")
                .agg(
                        // 各生态位的卖家数量
                        sum(when(col("niche_label").equalTo("高质高价区"), 1).otherwise(0))
                                .alias("premium_count"),
                        sum(when(col("niche_label").equalTo("性价比区"), 1).otherwise(0))
                                .alias("value_count"),
                        sum(when(col("niche_label").equalTo("低价冲量区"), 1).otherwise(0))
                                .alias("volume_count"),
                        sum(when(col("niche_label").equalTo("红海竞争区"), 1).otherwise(0))
                                .alias("red_ocean_count"),
                        sum(when(col("niche_label").equalTo("劣势区"), 1).otherwise(0))
                                .alias("disadvantaged_count"),
                        // 品类平均指标
                        avg("avg_price").alias("category_avg_price"),
                        avg("avg_score").alias("category_avg_score"),
                        avg("competitiveness_score").alias("category_avg_competitiveness"),
                        // 卖家总数
                        count(lit(1)).alias("total_sellers")
                )
                // 竞争强度：卖家越多越激烈
                .withColumn("competition_intensity",
                        when(col("total_sellers").geq(50), lit("激烈"))
                                .when(col("total_sellers").geq(20), lit("中等"))
                                .otherwise(lit("温和")))
                // 市场集中度：高质高价区占比越高越集中
                .withColumn("market_concentration",
                        col("premium_count").divide(col("total_sellers")))
                // 机会指数：性价比区 + 低价冲量区占比
                .withColumn("opportunity_index",
                        col("value_count").plus(col("volume_count"))
                                .divide(col("total_sellers")));

        System.out.println("竞争格局摘要构建完成: " + landscape.count() + " 个品类");

        return landscape;
    }
}
