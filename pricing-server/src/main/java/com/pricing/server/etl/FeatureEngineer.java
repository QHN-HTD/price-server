package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import static org.apache.spark.sql.functions.*;

/**
 * 特征工程处理器 — 从脱敏后的分析宽表构建建模特征
 * <p>
 * 产出三类特征集：
 * <ul>
 *   <li><b>品类-季度聚合特征</b>：用于弹性建模 & 波士顿矩阵</li>
 *   <li><b>卖家-品类竞争特征</b>：用于竞品生态位分析</li>
 *   <li><b>品类健康度综合评分</b>：多维度加权评分卡</li>
 * </ul>
 * </p>
 *
 * <p><b>安全设计：</b>所有特征基于脱敏后的聚合数据构建，
 * 无法反向推导个体信息（聚合度 ≥ 品类×季度级别）。</p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class FeatureEngineer {

    private final SparkSession spark;

    public FeatureEngineer(SparkSession spark) {
        this.spark = spark;
    }

    // ==================== 品类-季度聚合特征 ====================

    /**
     * 构建品类-季度级别的聚合特征表。
     * <p>
     * 产出字段：
     * <ul>
     *   <li>category_name — 品类名称（英文）</li>
     *   <li>year_quarter — 年份-季度（如 "2017-Q3"）</li>
     *   <li>total_quantity — 总销量（订单行数）</li>
     *   <li>total_revenue — 总营收（price × quantity）</li>
     *   <li>avg_unit_price — 平均单价</li>
     *   <li>avg_freight — 平均运费</li>
     *   <li>avg_review_score — 平均评分</li>
     *   <li>unique_sellers — 活跃卖家数</li>
     *   <li>unique_customers — 活跃客户数</li>
     *   <li>return_rate — 退货率（order_status='canceled' 占比）</li>
     * </ul>
     * </p>
     *
     * @param maskedView 脱敏后的分析宽表
     * @return 品类-季度聚合特征表
     */
    public Dataset<Row> buildCategoryQuarterFeatures(Dataset<Row> maskedView) {
        System.out.println("=== 构建品类-季度聚合特征 ===");

        // 提取季度字段
        Dataset<Row> withQuarter = maskedView
                .withColumn("year_quarter",
                        concat(
                                year(col("order_purchase_timestamp")),
                                lit("-Q"),
                                quarter(col("order_purchase_timestamp"))));

        // 品类 × 季度聚合
        Dataset<Row> features = withQuarter.groupBy("category_name", "year_quarter")
                .agg(
                        // 销量
                        count(lit(1)).alias("total_quantity"),
                        // 总营收
                        sum(col("price")).alias("total_revenue"),
                        // 平均单价
                        avg(col("price")).alias("avg_unit_price"),
                        // 平均运费
                        avg(col("freight_value")).alias("avg_freight"),
                        // 平均评分（排除空值）
                        avg(when(col("review_score").isNotNull(), col("review_score"))).alias("avg_review_score"),
                        // 活跃卖家数
                        countDistinct("seller_id").alias("unique_sellers"),
                        // 活跃客户数
                        countDistinct("customer_id").alias("unique_customers"),
                        // 退货/取消率
                        sum(when(col("order_status").isin("canceled", "unavailable"), 1).otherwise(0))
                                .divide(count(lit(1))).alias("return_rate")
                )
                // 过滤：至少 10 条订单的品类-季度组合才有统计意义
                .filter(col("total_quantity").geq(10));

        // 按品类开窗，计算环比增长率
        WindowSpec categoryWindow = Window.partitionBy("category_name")
                .orderBy("year_quarter");

        Dataset<Row> withGrowth = features
                // 销量环比增长率
                .withColumn("prev_quantity", lag("total_quantity", 1).over(categoryWindow))
                .withColumn("qoq_growth_rate",
                        when(col("prev_quantity").isNotNull().and(col("prev_quantity").gt(0)),
                                col("total_quantity").minus(col("prev_quantity"))
                                        .divide(col("prev_quantity")))
                                .otherwise(0.0))
                .drop("prev_quantity")
                // 价格环比变化率
                .withColumn("prev_price", lag("avg_unit_price", 1).over(categoryWindow))
                .withColumn("price_change_pct",
                        when(col("prev_price").isNotNull().and(col("prev_price").gt(0)),
                                col("avg_unit_price").minus(col("prev_price"))
                                        .divide(col("prev_price")))
                                .otherwise(0.0))
                .drop("prev_price");

        long featureCount = withGrowth.count();
        System.out.println("品类-季度特征构建完成: " + featureCount + " 条记录");

        return withGrowth;
    }

    // ==================== 全周期品类聚合特征 ====================

    /**
     * 构建全周期（所有时间汇总）品类级别特征。
     * 用于跨品类对比、波士顿矩阵市场份额计算。
     *
     * @param categoryQuarterFeatures 品类-季度特征表
     * @return 全周期品类特征表
     */
    public Dataset<Row> buildCategoryAllTimeFeatures(Dataset<Row> categoryQuarterFeatures) {
        System.out.println("=== 构建全周期品类特征 ===");

        Dataset<Row> allTime = categoryQuarterFeatures.groupBy("category_name")
                .agg(
                        // 累计销量
                        sum("total_quantity").alias("total_quantity_all"),
                        // 累计营收
                        sum("total_revenue").alias("total_revenue_all"),
                        // 时间加权平均价格
                        sum(col("avg_unit_price").multiply(col("total_quantity")))
                                .divide(sum("total_quantity")).alias("avg_price_weighted"),
                        // 平均评分
                        avg("avg_review_score").alias("avg_review_score_all"),
                        // 平均退货率
                        avg("return_rate").alias("avg_return_rate"),
                        // 平均季度增长率
                        avg("qoq_growth_rate").alias("avg_growth_rate"),
                        // 季度数（活跃度）
                        count(lit(1)).alias("active_quarters"),
                        // 平均活跃卖家数
                        avg("unique_sellers").alias("avg_sellers"),
                        // 最近季度销量（用于判断趋势）
                        last("total_quantity").alias("latest_quarter_quantity"),
                        // 最近季度均价
                        last("avg_unit_price").alias("latest_avg_price")
                );

        // 计算相对市场份额（品类营收 / 最大品类营收）
        long totalRecords = allTime.count();
        if (totalRecords > 0) {
            Row maxRevenueRow = allTime.agg(max("total_revenue_all")).first();
            double maxRevenue = maxRevenueRow != null ? maxRevenueRow.getDouble(0) : 1.0;

            allTime = allTime
                    .withColumn("relative_market_share",
                            col("total_revenue_all").divide(lit(maxRevenue)))
                    // 价格定位（与全局均价的比例）
                    .withColumn("price_index",
                            col("avg_price_weighted")
                                    .divide(lit(allTime.agg(avg("avg_price_weighted")).first().getDouble(0))));
        }

        System.out.println("全周期品类特征构建完成: " + totalRecords + " 个品类");

        return allTime;
    }

    // ==================== 卖家-品类竞争特征 ====================

    /**
     * 构建卖家在品类内的竞争定位特征。
     * <p>
     * 产出字段：
     * <ul>
     *   <li>seller_id — 卖家ID（已脱敏）</li>
     *   <li>category_name — 品类</li>
     *   <li>total_sales — 总销量</li>
     *   <li>avg_price — 平均售价</li>
     *   <li>avg_score — 平均评分</li>
     *   <li>price_percentile — 价格在品类内的百分位</li>
     *   <li>sales_percentile — 销量在品类内的百分位</li>
     *   <li>score_percentile — 评分在品类内的百分位</li>
     * </ul>
     * </p>
     *
     * @param maskedView 脱敏后的分析宽表
     * @return 卖家-品类竞争特征表
     */
    public Dataset<Row> buildSellerCategoryFeatures(Dataset<Row> maskedView) {
        System.out.println("=== 构建卖家-品类竞争特征 ===");

        // 卖家 × 品类聚合
        Dataset<Row> sellerAgg = maskedView.groupBy("seller_id", "category_name")
                .agg(
                        count(lit(1)).alias("total_sales"),
                        sum(col("price")).alias("total_revenue"),
                        avg(col("price")).alias("avg_price"),
                        avg(when(col("review_score").isNotNull(), col("review_score"))).alias("avg_score"),
                        avg(col("freight_value")).alias("avg_freight")
                )
                // 过滤：至少 5 笔交易的卖家-品类组合
                .filter(col("total_sales").geq(5));

        // 品类内排名（使用 cume_dist 计算百分位）
        WindowSpec categoryPriceWindow = Window.partitionBy("category_name")
                .orderBy(col("avg_price").asc());

        WindowSpec categorySalesWindow = Window.partitionBy("category_name")
                .orderBy(col("total_sales").asc());

        WindowSpec categoryScoreWindow = Window.partitionBy("category_name")
                .orderBy(col("avg_score").asc());

        Dataset<Row> withPercentiles = sellerAgg
                .withColumn("price_percentile",
                        cume_dist().over(categoryPriceWindow))
                .withColumn("sales_percentile",
                        cume_dist().over(categorySalesWindow))
                .withColumn("score_percentile",
                        cume_dist().over(categoryScoreWindow));

        System.out.println("卖家-品类竞争特征构建完成: " + withPercentiles.count() + " 条记录");

        return withPercentiles;
    }

    // ==================== 品类健康度综合评分卡 ====================

    /**
     * 计算品类健康度综合评分（0-100分，加权模型）。
     * <p>
     * 评分维度与权重：
     * <table>
     *   <tr><th>维度</th><th>权重</th><th>指标</th></tr>
     *   <tr><td>增长力</td><td>30%</td><td>近4季度平均增长率</td></tr>
     *   <tr><td>盈利能力</td><td>25%</td><td>均价 × 销量（总营收）</td></tr>
     *   <tr><td>客户口碑</td><td>20%</td><td>平均评分 (1-5)</td></tr>
     *   <tr><td>稳定性</td><td>15%</td><td>1 - 退货率</td></tr>
     *   <tr><td>活跃度</td><td>10%</td><td>活跃卖家数 / 季度</td></tr>
     * </table>
     * </p>
     *
     * @param allTimeFeatures 全周期品类特征表
     * @return 带健康度评分的品类特征表
     */
    public Dataset<Row> calculateHealthScore(Dataset<Row> allTimeFeatures) {
        System.out.println("=== 计算品类健康度评分卡 ===");

        // 各维度的全局统计值（用于 min-max 归一化）
        Row stats = allTimeFeatures.agg(
                min("avg_growth_rate"), max("avg_growth_rate"),
                min("total_revenue_all"), max("total_revenue_all"),
                min("avg_review_score_all"), max("avg_review_score_all"),
                min("avg_return_rate"), max("avg_return_rate"),
                min("avg_sellers"), max("avg_sellers")
        ).first();

        double grMin = stats.getDouble(0), grMax = stats.getDouble(1);
        double rvMin = stats.getDouble(2), rvMax = stats.getDouble(3);
        double scMin = stats.getDouble(4), scMax = stats.getDouble(5);
        double rrMin = stats.getDouble(6), rrMax = stats.getDouble(7);
        double seMin = stats.getDouble(8), seMax = stats.getDouble(9);

        Dataset<Row> scored = allTimeFeatures
                .withColumn("score_growth",
                        normalize(
                                col("avg_growth_rate"),
                                lit(grMin), lit(grMax)))
                .withColumn("score_revenue",
                        normalize(
                                log(col("total_revenue_all").plus(1)),
                                lit(Math.log(rvMin + 1)), lit(Math.log(rvMax + 1))))
                .withColumn("score_review",
                        normalize(
                                col("avg_review_score_all"),
                                lit(scMin), lit(scMax)))
                .withColumn("score_stability",
                        lit(1.0).minus(
                                normalize(
                                        col("avg_return_rate"),
                                        lit(rrMin), lit(rrMax))))
                .withColumn("score_activity",
                        normalize(
                                col("avg_sellers"),
                                lit(seMin), lit(seMax)))
                // 加权综合评分
                .withColumn("health_score",
                        col("score_growth").multiply(0.30)
                                .plus(col("score_revenue").multiply(0.25))
                                .plus(col("score_review").multiply(0.20))
                                .plus(col("score_stability").multiply(0.15))
                                .plus(col("score_activity").multiply(0.10))
                                .multiply(100))
                // 健康等级标签
                .withColumn("health_level",
                        when(col("health_score").geq(75), lit("A-优秀"))
                                .when(col("health_score").geq(60), lit("B-良好"))
                                .when(col("health_score").geq(40), lit("C-一般"))
                                .otherwise(lit("D-关注")));

        System.out.println("健康度评分卡计算完成");

        return scored;
    }

    /**
     * Min-Max 归一化函数：将值映射到 [0, 1] 区间。
     * 处理边界情况：当 max == min 时返回 0.5。
     */
    private static org.apache.spark.sql.Column normalize(
            org.apache.spark.sql.Column value,
            org.apache.spark.sql.Column minVal,
            org.apache.spark.sql.Column maxVal) {
        return when(maxVal.equalTo(minVal), lit(0.5))
                .otherwise(
                        value.minus(minVal).divide(maxVal.minus(minVal)));
    }
}
