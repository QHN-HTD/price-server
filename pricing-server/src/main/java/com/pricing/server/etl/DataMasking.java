package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * 数据脱敏处理器 — PII（个人身份信息）保护
 * <p>
 * 在 Spark ETL 管道的最前端执行，确保后续所有分析和存储
 * 都使用脱敏后的数据。这是安全设计的第一道防线。
 * </p>
 *
 * <p><b>脱敏策略（三层深度）：</b>
 * <table>
 *   <tr><th>层级</th><th>字段</th><th>方法</th><th>不可逆性</th></tr>
 *   <tr><td>L1 - 完全哈希</td><td>customer_id, order_id, seller_id</td><td>SHA-256 + 盐值</td><td>不可逆 ✓</td></tr>
 *   <tr><td>L2 - 区域聚合</td><td>zip_code → 前3位, GPS → 1位小数</td><td>精度截断</td><td>不可逆 ✓</td></tr>
 *   <tr><td>L3 - 文本清洗</td><td>review_comment_message</td><td>正则替换PII</td><td>不可逆 ✓</td></tr>
 * </table>
 * </p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class DataMasking {

    private final SparkSession spark;

    public DataMasking(SparkSession spark) {
        this.spark = spark;
    }

    /**
     * 对分析宽表执行完整脱敏处理。
     * <p>
     * 脱敏顺序：
     * <ol>
     *   <li>哈希化所有ID字段（customer_id, order_id, seller_id, product_id）</li>
     *   <li>截断邮政编码 → 区域级别</li>
     *   <li>标准化城市名称</li>
     *   <li>清洗评论文本中的PII</li>
     *   <li>对价格字段进行分桶（保留精确值用于建模）</li>
     * </ol>
     * </p>
     *
     * @param analyticalView 原始分析宽表
     * @return 脱敏后的分析宽表
     */
    public Dataset<Row> mask(Dataset<Row> analyticalView) {
        System.out.println("=== 开始数据脱敏处理 ===");

        // 注册 UDF（User Defined Function）用于脱敏操作
        registerMaskingUDFs();

        Dataset<Row> masked = analyticalView
                // L1: 哈希化所有ID字段
                .withColumn("customer_id", functions.callUDF("mask_id", functions.col("customer_id")))
                .withColumn("order_id", functions.callUDF("mask_id", functions.col("order_id")))
                .withColumn("seller_id", functions.callUDF("mask_id", functions.col("seller_id")))
                .withColumn("product_id", functions.callUDF("mask_id", functions.col("product_id")))
                .withColumn("review_id", functions.callUDF("mask_id", functions.col("review_id")))
                // L2: 邮政编码截断
                .withColumn("seller_zip_code_prefix",
                        functions.callUDF("mask_zip", functions.col("seller_zip_code_prefix")))
                .withColumn("customer_zip_code_prefix",
                        functions.callUDF("mask_zip", functions.col("customer_zip_code_prefix")))
                // L2: 城市名标准化
                .withColumn("seller_city", functions.callUDF("normalize_city", functions.col("seller_city")))
                .withColumn("customer_city", functions.callUDF("normalize_city", functions.col("customer_city")))
                // L3: 评论文本PII清洗
                .withColumn("review_comment_message",
                        functions.callUDF("mask_review", functions.col("review_comment_message")))
                .withColumn("review_comment_title",
                        functions.callUDF("mask_review", functions.col("review_comment_title")))
                // 金额分桶（用于展示，精确值保留在 price 和 freight_value 中用于建模）
                .withColumn("price_bucket", functions.callUDF("bucket_price", functions.col("price")))
                .withColumn("freight_bucket", functions.callUDF("bucket_price", functions.col("freight_value")));

        long count = masked.count();
        System.out.println("脱敏处理完成: " + count + " 条记录");
        System.out.println("  盐值指纹: " + SecurityUtils.getSaltFingerprint());

        return masked;
    }

    /**
     * 注册所有脱敏 UDF。
     */
    private void registerMaskingUDFs() {
        // ID 哈希化 UDF
        spark.udf().register("mask_id",
                (String id) -> SecurityUtils.hashIdentifier(id),
                DataTypes.StringType);

        // 邮政编码截断 UDF
        spark.udf().register("mask_zip",
                (String zip) -> SecurityUtils.truncateZipCode(zip),
                DataTypes.StringType);

        // 城市名标准化 UDF
        spark.udf().register("normalize_city",
                (String city) -> SecurityUtils.normalizeCity(city),
                DataTypes.StringType);

        // 评论文本PII清洗 UDF
        spark.udf().register("mask_review",
                (String text) -> SecurityUtils.maskReviewText(text),
                DataTypes.StringType);

        // 金额分桶 UDF
        spark.udf().register("bucket_price",
                (Double price) -> {
                    if (price == null) return "UNKNOWN";
                    return SecurityUtils.bucketAmount(price);
                },
                DataTypes.StringType);
    }

    /**
     * 对特定 Dataset 执行字段级脱敏（用于非宽表的单独处理）。
     *
     * @param df            输入 DataFrame
     * @param columnName    需要脱敏的列名
     * @param maskingType   脱敏类型：HASH / ZIP / CITY / REVIEW
     * @return 脱敏后的 DataFrame
     */
    public Dataset<Row> maskColumn(Dataset<Row> df, String columnName, String maskingType) {
        switch (maskingType.toUpperCase()) {
            case "HASH":
                return df.withColumn(columnName,
                        functions.callUDF("mask_id", functions.col(columnName)));
            case "ZIP":
                return df.withColumn(columnName,
                        functions.callUDF("mask_zip", functions.col(columnName)));
            case "CITY":
                return df.withColumn(columnName,
                        functions.callUDF("normalize_city", functions.col(columnName)));
            case "REVIEW":
                return df.withColumn(columnName,
                        functions.callUDF("mask_review", functions.col(columnName)));
            default:
                throw new IllegalArgumentException("不支持的脱敏类型: " + maskingType);
        }
    }
}
