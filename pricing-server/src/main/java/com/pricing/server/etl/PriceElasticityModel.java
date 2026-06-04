package com.pricing.server.etl;

import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.ml.regression.LinearRegressionTrainingSummary;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

/**
 * 价格弹性分析引擎 — 基于 Spark ML 对数线性回归
 * <p>
 * <b>核心模型：</b>
 * <pre>
 *   ln(quantity) = α + β × ln(price) + Σγᵢ × controlᵢ + ε
 * </pre>
 * 其中 β 即为价格弹性系数：
 * <ul>
 *   <li>|β| > 1 → 高弹性（价格敏感，降价可显著提升销量）</li>
 *   <li>|β| < 1 → 低弹性（价格不敏感，调价空间有限）</li>
 *   <li>|β| ≈ 0 → 无弹性（需求与价格无关，如必需品）</li>
 * </ul>
 * </p>
 *
 * <p><b>建模策略：</b>
 * 对每个品类单独建模，因为不同品类的价格敏感度差异显著。
 * 统一建模会丢失品类级别的洞察精度。
 * </p>
 *
 * <p><b>安全设计：</b>所有输入数据已经过脱敏处理，
 * 模型仅使用聚合级别数据（品类×月份），不涉及个体信息。</p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class PriceElasticityModel {

    private final SparkSession spark;

    /** 最小样本量阈值：品类月度数少于此值则跳过建模 */
    private static final int MIN_SAMPLE_SIZE = 6;

    /** R² 阈值：模型拟合优度低于此值标记为低置信度 */
    private static final double MIN_R_SQUARED = 0.3;

    public PriceElasticityModel(SparkSession spark) {
        this.spark = spark;
    }

    /**
     * 弹性系数分析结果。
     */
    public static class ElasticityResult {
        public String categoryName;
        public double elasticity;       // β 系数（价格弹性）
        public double intercept;        // α 截距
        public double rSquared;         // R² 拟合优度
        public double pValue;           // 价格系数的 p-value
        public int sampleSize;          // 样本量（月份数）
        public String interpretation;   // 弹性解释标签
        public String recommendation;   // 定价策略建议

        public ElasticityResult(String categoryName, double elasticity, double intercept,
                                double rSquared, double pValue, int sampleSize,
                                String interpretation, String recommendation) {
            this.categoryName = categoryName;
            this.elasticity = elasticity;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.pValue = pValue;
            this.sampleSize = sampleSize;
            this.interpretation = interpretation;
            this.recommendation = recommendation;
        }
    }

    /**
     * 对每个品类独立运行价格弹性回归模型。
     *
     * @param categoryQuarterFeatures 品类-季度聚合特征表
     * @return 各品类弹性系数结果列表
     */
    public List<ElasticityResult> analyzeAllCategories(Dataset<Row> categoryQuarterFeatures) {
        System.out.println("=== 价格弹性分析引擎启动 ===");

        // 获取所有品类列表
        List<String> categories = categoryQuarterFeatures
                .select("category_name")
                .distinct()
                .orderBy("category_name")
                .as(org.apache.spark.sql.Encoders.STRING())
                .collectAsList();

        System.out.println("待分析品类数: " + categories.size());

        List<ElasticityResult> results = new ArrayList<>();

        for (String category : categories) {
            System.out.println("  分析品类: " + category);

            // 过滤该品类的数据
            Dataset<Row> categoryData = categoryQuarterFeatures
                    .filter(col("category_name").equalTo(category))
                    // 需要总销量和平均单价
                    .filter(col("total_quantity").isNotNull()
                            .and(col("avg_unit_price").isNotNull()))
                    .cache();

            long sampleSize = categoryData.count();

            if (sampleSize < MIN_SAMPLE_SIZE) {
                System.out.println("    跳过: 样本量不足 (" + sampleSize + " < " + MIN_SAMPLE_SIZE + ")");
                categoryData.unpersist();
                continue;
            }

            try {
                ElasticityResult result = analyzeCategory(category, categoryData);
                if (result != null) {
                    results.add(result);
                    System.out.println(String.format(
                            "    ✓ 弹性系数: %.3f | R²: %.3f | %s",
                            result.elasticity, result.rSquared, result.interpretation));
                }
            } catch (Exception e) {
                System.out.println("    ✗ 建模失败: " + e.getMessage());
            } finally {
                categoryData.unpersist();
            }
        }

        System.out.println("弹性分析完成: 成功建模 " + results.size() + " 个品类");
        return results;
    }

    /**
     * 对单个品类运行对数线性回归。
     */
    private ElasticityResult analyzeCategory(String category, Dataset<Row> data) {
        // Step 1: 对数变换 → ln(quantity) 和 ln(price)
        Dataset<Row> logTransformed = data
                .withColumn("ln_quantity", log(col("total_quantity")))
                .withColumn("ln_price", log(col("avg_unit_price")))
                // 控制变量：评分（对数变换）、卖家数
                .withColumn("ln_sellers", log(col("unique_sellers").plus(1)))
                .filter(col("ln_quantity").isNotNull()
                        .and(col("ln_price").isNotNull()))
                .cache();

        long validCount = logTransformed.count();
        if (validCount < MIN_SAMPLE_SIZE) {
            logTransformed.unpersist();
            return null;
        }

        // Step 2: 特征向量组装
        VectorAssembler assembler = new VectorAssembler()
                .setInputCols(new String[]{"ln_price", "ln_sellers", "avg_review_score"})
                .setOutputCol("features")
                .setHandleInvalid("skip");

        // Step 3: 线性回归模型
        LinearRegression lr = new LinearRegression()
                .setLabelCol("ln_quantity")
                .setFeaturesCol("features")
                .setMaxIter(100)
                .setRegParam(0.01)       // 轻微 L2 正则化防止过拟合
                .setElasticNetParam(0.0) // 纯 Ridge (L2) 回归
                .setStandardization(true);

        // Step 4: 构建 Pipeline 并训练
        Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{assembler, lr});
        PipelineModel pipelineModel = pipeline.fit(logTransformed);

        // Step 5: 提取模型参数
        LinearRegressionModel lrModel = (LinearRegressionModel) pipelineModel.stages()[1];
        LinearRegressionTrainingSummary summary = lrModel.summary();

        double[] coefficients = lrModel.coefficients().toArray();
        double elasticity = coefficients[0];  // ln_price 的系数 = β
        double intercept = lrModel.intercept();
        double rSquared = summary.r2();
        double[] pValues = summary.pValues();
        double pValue = pValues.length > 0 ? pValues[0] : Double.NaN;

        // Step 6: 解释弹性系数 & 生成策略建议
        String[] interpretation = interpretElasticity(elasticity, rSquared, pValue);

        logTransformed.unpersist();

        return new ElasticityResult(
                category,
                elasticity,
                intercept,
                rSquared,
                pValue,
                (int) validCount,
                interpretation[0],
                interpretation[1]
        );
    }

    /**
     * 解释弹性系数，生成人类可读的标签和策略建议。
     *
     * @param elasticity 弹性系数 β
     * @param rSquared   模型 R²
     * @param pValue     价格系数 p-value
     * @return [解释标签, 策略建议]
     */
    private String[] interpretElasticity(double elasticity, double rSquared, double pValue) {
        // 低置信度警告
        String confidenceNote = "";
        if (rSquared < MIN_R_SQUARED || (pValue > 0.1 && !Double.isNaN(pValue))) {
            confidenceNote = "（低置信度）";
        }

        double absBeta = Math.abs(elasticity);

        String label;
        String recommendation;

        if (absBeta > 2.0) {
            label = "极高弹性" + confidenceNote;
            recommendation = "降价促销效果显著，建议小幅降价（5-10%）抢占市场份额。涨价风险极高。";
        } else if (absBeta > 1.0) {
            label = "高弹性" + confidenceNote;
            recommendation = "价格敏感品类，建议中幅降价（10-15%）配合营销活动提升销量。谨慎涨价。";
        } else if (absBeta > 0.5) {
            label = "中等弹性" + confidenceNote;
            recommendation = "适度价格敏感，可通过差异化服务（包邮、赠品）替代直接降价。小幅调价影响可控。";
        } else if (absBeta > 0.2) {
            label = "低弹性" + confidenceNote;
            recommendation = "价格不敏感，建议小幅涨价（3-5%）提升毛利率。降价对销量刺激有限。";
        } else {
            label = "极低弹性" + confidenceNote;
            if (rSquared < MIN_R_SQUARED) {
                recommendation = "模型解释力不足，可能受品牌忠诚度、稀缺性等非价格因素主导。建议结合定性分析。";
            } else {
                recommendation = "必需品/垄断品类，涨价空间大。建议阶梯式涨价（每季度+3%），同时监测流失率。";
            }
        }

        // 额外策略提示
        if (elasticity > 0) {
            // 正弹性（吉芬商品特征）— 价格上升需求反而上升
            recommendation += " ⚠ 注意：该品类表现出正价格弹性（奢侈品/炫耀性消费特征），传统降价策略不适用。";
        }

        return new String[]{label, recommendation};
    }

    /**
     * 将弹性分析结果转换为 DataFrame（用于后续写入 MySQL）。
     */
    public Dataset<Row> resultsToDataFrame(List<ElasticityResult> results) {
        List<Row> rows = new ArrayList<>();
        for (ElasticityResult r : results) {
            rows.add(org.apache.spark.sql.RowFactory.create(
                    r.categoryName,
                    r.elasticity,
                    r.intercept,
                    r.rSquared,
                    r.pValue,
                    r.sampleSize,
                    r.interpretation,
                    r.recommendation
            ));
        }

        org.apache.spark.sql.types.StructType schema = new org.apache.spark.sql.types.StructType()
                .add("category_name", "string")
                .add("elasticity", "double")
                .add("intercept", "double")
                .add("r_squared", "double")
                .add("p_value", "double")
                .add("sample_size", "integer")
                .add("elasticity_label", "string")
                .add("pricing_strategy", "string");

        return spark.createDataFrame(rows, schema);
    }
}
