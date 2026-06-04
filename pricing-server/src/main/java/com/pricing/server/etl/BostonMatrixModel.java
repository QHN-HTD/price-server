package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import static org.apache.spark.sql.functions.*;

/**
 * 品类波士顿矩阵诊断模型
 * <p>
 * 自动将品类映射到波士顿矩阵四象限，并追踪跨季度迁移轨迹。
 * </p>
 *
 * <p><b>波士顿矩阵四象限：</b>
 * <table>
 *   <tr><th>象限</th><th>市场份额</th><th>市场增长率</th><th>策略</th></tr>
 *   <tr><td>⭐ 明星 (Star)</td><td>高</td><td>高</td><td>加大投入，保持领先</td></tr>
 *   <tr><td>🐄 金牛 (Cash Cow)</td><td>高</td><td>低</td><td>收割利润，维持地位</td></tr>
 *   <tr><td>❓ 问题 (Question Mark)</td><td>低</td><td>高</td><td>选择性投资或放弃</td></tr>
 *   <tr><td>🐕 瘦狗 (Dog)</td><td>低</td><td>低</td><td>缩减或退出</td></tr>
 * </table>
 * </p>
 *
 * <p><b>动态追踪：</b>按季度计算各品类位置，生成迁移路径，
 * 可视化品类生命周期的演化过程。</p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class BostonMatrixModel {

    private final SparkSession spark;

    /** 市场份额分界线（高于此值视为高份额） */
    private static final double MARKET_SHARE_THRESHOLD = 0.15;

    /** 增长率分界线（高于此值视为高增长） */
    private static final double GROWTH_RATE_THRESHOLD = 0.05;

    public BostonMatrixModel(SparkSession spark) {
        this.spark = spark;
    }

    /**
     * 对单个季度计算波士顿矩阵分类。
     * <p>
     * 分类逻辑：
     * <ul>
     *   <li>计算每个品类在该季度的市场份额（品类营收 / 该季度总营收）</li>
     *   <li>计算每个品类的环比增长率</li>
     *   <li>基于两个维度映射到四象限</li>
     * </ul>
     * </p>
     *
     * @param categoryQuarterFeatures 品类-季度聚合特征表
     * @return 每个季度每个品类的波士顿矩阵分类
     */
    public Dataset<Row> classifyQuarterly(Dataset<Row> categoryQuarterFeatures) {
        System.out.println("=== 波士顿矩阵季度分类 ===");

        // Step 1: 计算每个季度的总营收
        Dataset<Row> quarterTotal = categoryQuarterFeatures
                .groupBy("year_quarter")
                .agg(sum("total_revenue").alias("quarter_total_revenue"));

        // Step 2: 关联季度总营收，计算市场份额
        Dataset<Row> withMarketShare = categoryQuarterFeatures
                .join(quarterTotal, "year_quarter")
                .withColumn("market_share",
                        col("total_revenue").divide(col("quarter_total_revenue")))
                .drop("quarter_total_revenue");

        // Step 3: 计算市场份额的品类内排名（用于确定"相对市场地位"）
        WindowSpec quarterWindow = Window.partitionBy("year_quarter")
                .orderBy(col("market_share").desc());

        Dataset<Row> withRank = withMarketShare
                .withColumn("market_share_rank", row_number().over(quarterWindow))
                // 相对市场份额（与品类内第一名的比例）
                .withColumn("relative_market_share",
                        col("market_share").divide(
                                first("market_share").over(quarterWindow)));

        // Step 4: 分类到波士顿矩阵四象限
        Dataset<Row> classified = withRank
                .withColumn("boston_quadrant",
                        when(col("relative_market_share").gt(MARKET_SHARE_THRESHOLD)
                                        .and(col("qoq_growth_rate").gt(GROWTH_RATE_THRESHOLD)),
                                lit("Star"))
                                .when(col("relative_market_share").gt(MARKET_SHARE_THRESHOLD)
                                                .and(col("qoq_growth_rate").leq(GROWTH_RATE_THRESHOLD)),
                                        lit("Cash Cow"))
                                .when(col("relative_market_share").leq(MARKET_SHARE_THRESHOLD)
                                                .and(col("qoq_growth_rate").gt(GROWTH_RATE_THRESHOLD)),
                                        lit("Question Mark"))
                                .otherwise(lit("Dog")))
                // 象限中文名（用于前端展示）
                .withColumn("boston_quadrant_cn",
                        when(col("boston_quadrant").equalTo("Star"), lit("明星"))
                                .when(col("boston_quadrant").equalTo("Cash Cow"), lit("金牛"))
                                .when(col("boston_quadrant").equalTo("Question Mark"), lit("问题"))
                                .otherwise(lit("瘦狗")))
                // 策略建议
                .withColumn("boston_strategy",
                        when(col("boston_quadrant").equalTo("Star"),
                                lit("加大营销投入，扩大市场份额，巩固领先地位。可适度提价测试价格天花板。"))
                                .when(col("boston_quadrant").equalTo("Cash Cow"),
                                        lit("稳定现有定价，最大化利润贡献。精简运营成本，将资源转移到明星/问题品类。"))
                                .when(col("boston_quadrant").equalTo("Question Mark"),
                                        lit("选择性投入。对有潜力的子品类加大推广（→明星），其余观察或放弃。降价策略可加速增长。"))
                                .otherwise(lit("考虑缩减该品类的SKU数量，降低库存深度。或通过差异化改造寻找细分市场机会。")));

        long count = classified.count();
        System.out.println("波士顿矩阵分类完成: " + count + " 条季度记录");

        // 输出各象限分布
        System.out.println("\n波士顿矩阵分布:");
        classified.groupBy("boston_quadrant", "boston_quadrant_cn")
                .agg(count(lit(1)).alias("count"))
                .orderBy(col("count").desc())
                .show(false);

        return classified;
    }

    /**
     * 构建品类迁移轨迹数据。
     * <p>
     * 对比前后两个季度的波士顿象限变化，识别：
     * <ul>
     *   <li>↑ 向上升级（如：问题 → 明星）</li>
     *   <li>↓ 向下降级（如：明星 → 金牛）</li>
     *   <li>→ 保持稳定</li>
     *   <li>↻ 跨越迁移（如：问题 → 金牛）</li>
     * </ul>
     * </p>
     *
     * @param quarterlyClassification 季度波士顿矩阵分类
     * @return 含迁移路径的数据集
     */
    public Dataset<Row> buildMigrationTrajectory(Dataset<Row> quarterlyClassification) {
        System.out.println("=== 构建品类迁移轨迹 ===");

        WindowSpec categoryWindow = Window.partitionBy("category_name")
                .orderBy("year_quarter");

        Dataset<Row> withTrajectory = quarterlyClassification
                // 上一季度的象限
                .withColumn("prev_quadrant", lag("boston_quadrant", 1).over(categoryWindow))
                .withColumn("prev_quadrant_cn", lag("boston_quadrant_cn", 1).over(categoryWindow))
                // 上一季度的市场份额
                .withColumn("prev_market_share", lag("market_share", 1).over(categoryWindow))
                .withColumn("prev_growth_rate", lag("qoq_growth_rate", 1).over(categoryWindow))
                // 迁移方向标签
                .withColumn("migration_direction",
                        when(col("prev_quadrant").isNull(), lit("初始"))
                                .when(col("boston_quadrant").equalTo(col("prev_quadrant")), lit("稳定"))
                                .when(
                                        // 升级路径
                                        (col("prev_quadrant").equalTo("Question Mark")
                                                .and(col("boston_quadrant").equalTo("Star")))
                                                .or(col("prev_quadrant").equalTo("Dog")
                                                        .and(col("boston_quadrant").isin("Question Mark", "Cash Cow"))),
                                        lit("升级"))
                                .when(
                                        // 降级路径
                                        (col("prev_quadrant").equalTo("Star")
                                                .and(col("boston_quadrant").isin("Cash Cow", "Question Mark", "Dog")))
                                                .or(col("prev_quadrant").equalTo("Cash Cow")
                                                        .and(col("boston_quadrant").isin("Question Mark", "Dog"))),
                                        lit("降级"))
                                .otherwise(lit("跨越迁移")))
                // 市场份额变化
                .withColumn("share_change",
                        col("market_share").minus(col("prev_market_share")))
                // 用于 ECharts 的坐标：x=相对市场份额, y=增长率, bubble_size=总营收
                .withColumn("bubble_x", col("relative_market_share"))
                .withColumn("bubble_y", col("qoq_growth_rate"))
                .withColumn("bubble_size",
                        log(col("total_revenue").plus(1)).multiply(10));

        System.out.println("迁移轨迹构建完成");

        // 输出迁移统计
        System.out.println("\n品类迁移统计:");
        withTrajectory
                .filter(col("migration_direction").notEqual("初始"))
                .groupBy("migration_direction")
                .agg(count(lit(1)).alias("count"))
                .orderBy(col("count").desc())
                .show(false);

        return withTrajectory;
    }

    /**
     * 构建最新季度的波士顿矩阵快照（用于大屏展示）。
     *
     * @param quarterlyClassification 季度波士顿矩阵分类
     * @return 最新季度的波士顿矩阵数据
     */
    public Dataset<Row> buildLatestSnapshot(Dataset<Row> quarterlyClassification) {
        // 找到最新季度
        Row latestQuarter = quarterlyClassification
                .agg(max("year_quarter").alias("latest"))
                .first();
        String latestQ = latestQuarter.getString(0);

        System.out.println("最新季度快照: " + latestQ);

        return quarterlyClassification
                .filter(col("year_quarter").equalTo(latestQ))
                .select(
                        "category_name",
                        "year_quarter",
                        "boston_quadrant",
                        "boston_quadrant_cn",
                        "boston_strategy",
                        "relative_market_share",
                        "qoq_growth_rate",
                        "market_share",
                        "total_revenue",
                        "total_quantity",
                        "bubble_x",
                        "bubble_y",
                        "bubble_size"
                );
    }

    /**
     * 计算品类象限分布的柱状图数据（每个象限的品类数量）。
     */
    public Dataset<Row> buildQuadrantDistribution(Dataset<Row> latestSnapshot) {
        return latestSnapshot
                .groupBy("boston_quadrant", "boston_quadrant_cn")
                .agg(
                        count(lit(1)).alias("category_count"),
                        sum("total_revenue").alias("quadrant_total_revenue"),
                        avg("relative_market_share").alias("quadrant_avg_share"),
                        avg("qoq_growth_rate").alias("quadrant_avg_growth")
                )
                .orderBy(col("category_count").desc());
    }
}
