package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETL 管道服务 — 封装 Spark 数据处理管道为可调用的服务
 * <p>
 * 通过 REST API 触发：POST /api/v1/admin/etl/run
 * 前提条件：data/raw/ 目录下存在 Olist CSV 数据集
 * </p>
 */
@Service
public class EtlPipelineService {

    private static final Logger log = LoggerFactory.getLogger(EtlPipelineService.class);

    @Value("${etl.data-dir:data/raw/}")
    private String dataDir;

    @Value("${etl.output-dir:data/output/}")
    private String outputDir;

    /** 管道运行状态 */
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();
    private volatile boolean running = false;

    /**
     * 执行完整的 ETL 管道。
     *
     * @return 执行结果摘要
     */
    public Map<String, Object> runPipeline() {
        if (running) {
            return Map.of("success", false, "message", "ETL 管道正在运行中，请稍后再试");
        }

        running = true;
        statusMap.clear();
        long startTime = System.currentTimeMillis();
        String runId = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

        SparkSession spark = null;
        try {
            updateStatus("初始化", "RUNNING");
            log.info("=== ETL 管道启动 (Run ID: {}) ===", runId);

            // 创建 SparkSession
            spark = SparkSessionFactory.create("SmartPricingETL-" + runId);
            updateStatus("初始化", "DONE");

            // 阶段1: 加载数据
            updateStatus("加载数据", "RUNNING");
            DataLoader loader = new DataLoader(spark, dataDir);
            DataLoader.OlistDatasets datasets = loader.loadAll();
            updateStatus("加载数据", "DONE");

            // 阶段2: 构建视图 + 脱敏
            updateStatus("数据脱敏", "RUNNING");
            Dataset<Row> analyticalView = loader.buildAnalyticalView(datasets);
            DataMasking masking = new DataMasking(spark);
            Dataset<Row> maskedView = masking.mask(analyticalView);
            maskedView.cache();
            updateStatus("数据脱敏", "DONE");

            // 阶段3: 特征工程
            updateStatus("特征工程", "RUNNING");
            FeatureEngineer fe = new FeatureEngineer(spark);
            Dataset<Row> categoryQuarter = fe.buildCategoryQuarterFeatures(maskedView);
            categoryQuarter.cache();
            Dataset<Row> allTime = fe.buildCategoryAllTimeFeatures(categoryQuarter);
            Dataset<Row> healthScores = fe.calculateHealthScore(allTime);
            Dataset<Row> sellerCat = fe.buildSellerCategoryFeatures(maskedView);
            updateStatus("特征工程", "DONE");

            // 阶段4: 价格弹性建模
            updateStatus("弹性建模", "RUNNING");
            PriceElasticityModel elasticityModel = new PriceElasticityModel(spark);
            List<PriceElasticityModel.ElasticityResult> elasticityResults =
                    elasticityModel.analyzeAllCategories(categoryQuarter);
            Dataset<Row> elasticityDF = elasticityModel.resultsToDataFrame(elasticityResults);
            writeToMySQL(elasticityDF, "fact_elasticity");
            updateStatus("弹性建模", "DONE");

            // 阶段5: 竞品分析
            updateStatus("竞品分析", "RUNNING");
            CompetitorAnalysisModel competitorModel = new CompetitorAnalysisModel(spark);
            Dataset<Row> nicheClass = competitorModel.classifyNiche(sellerCat);
            Dataset<Row> landscape = competitorModel.buildCategoryLandscape(nicheClass);
            writeToMySQL(nicheClass.select(
                    "seller_id", "category_name", "avg_price", "avg_score",
                    "total_sales", "total_revenue", "price_percentile",
                    "sales_percentile", "score_percentile",
                    "niche_label", "competitive_advice", "competitiveness_score"
            ), "fact_competitor_niche");
            writeToMySQL(landscape, "fact_category_landscape");
            updateStatus("竞品分析", "DONE");

            // 阶段6: 波士顿矩阵
            updateStatus("波士顿矩阵", "RUNNING");
            BostonMatrixModel bostonModel = new BostonMatrixModel(spark);
            Dataset<Row> quarterly = bostonModel.classifyQuarterly(categoryQuarter);
            Dataset<Row> latest = bostonModel.buildLatestSnapshot(quarterly);
            Dataset<Row> dist = bostonModel.buildQuadrantDistribution(latest);
            writeToMySQL(latest, "fact_boston_matrix");
            writeToMySQL(dist, "fact_boston_distribution");
            updateStatus("波士顿矩阵", "DONE");

            // 写入健康度
            writeToMySQL(healthScores.select(
                    "category_name", "total_quantity_all", "total_revenue_all",
                    "avg_price_weighted", "avg_review_score_all", "avg_return_rate",
                    "avg_growth_rate", "health_score", "health_level"
            ), "fact_category_health");

            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            updateStatus("完成", "DONE");

            log.info("=== ETL 管道完成, 耗时: {}分{}秒 ===", elapsed / 60, elapsed % 60);

            return Map.of(
                    "success", true,
                    "runId", runId,
                    "elapsedSeconds", elapsed,
                    "elasticityCount", elasticityResults.size(),
                    "stages", new java.util.HashMap<>(statusMap)
            );

        } catch (Exception e) {
            log.error("ETL 管道失败", e);
            updateStatus("失败", "ERROR: " + e.getMessage());
            return Map.of(
                    "success", false,
                    "runId", runId,
                    "error", e.getMessage(),
                    "stages", new java.util.HashMap<>(statusMap)
            );
        } finally {
            running = false;
            if (spark != null) {
                try { spark.stop(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 获取当前管道状态。
     */
    public Map<String, Object> getStatus() {
        return Map.of(
                "running", running,
                "stages", new java.util.HashMap<>(statusMap)
        );
    }

    private void updateStatus(String stage, String status) {
        statusMap.put(stage, status);
        log.info("  ETL [{}] → {}", stage, status);
    }

    /**
     * 将 DataFrame 写入 MySQL。
     */
    private void writeToMySQL(Dataset<Row> df, String tableName) {
        try {
            df.write()
                    .mode("overwrite")
                    .format("jdbc")
                    .option("url", System.getenv().getOrDefault("MYSQL_URL",
                            "jdbc:mysql://localhost:3306/smart_pricing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"))
                    .option("dbtable", tableName)
                    .option("user", System.getenv().getOrDefault("MYSQL_USER", "root"))
                    .option("password", System.getenv().getOrDefault("MYSQL_PASS", "root"))
                    .option("driver", "com.mysql.cj.jdbc.Driver")
                    .option("batchsize", "5000")
                    .option("truncate", "true")
                    .save();
            log.info("  ✓ {} 写入成功", tableName);
        } catch (Exception e) {
            log.error("  ✗ {} 写入失败: {}", tableName, e.getMessage());
        }
    }
}
