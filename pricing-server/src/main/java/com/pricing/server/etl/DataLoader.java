package com.pricing.server.etl;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * 数据加载器 — 从 CSV 文件加载 Olist 巴西电商数据集
 * <p>
 * 负责：
 * <ul>
 *   <li>读取 7 个原始 CSV 文件</li>
 *   <li>定义显式 Schema（类型安全 + 性能优化）</li>
 *   <li>处理缺失值和异常数据</li>
 *   <li>构建核心分析视图（订单-商品-品类-卖家宽表）</li>
 * </ul>
 * </p>
 *
 * <p><b>Olist 数据集规模：</b>
 * <ul>
 *   <li>orders: ~100k | order_items: ~112k | products: ~33k</li>
 *   <li>sellers: ~3k | customers: ~96k | reviews: ~100k</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class DataLoader {

    /** 数据文件根目录 */
    private final String dataRoot;

    /** SparkSession 实例 */
    private final SparkSession spark;

    /**
     * 构造函数。
     *
     * @param spark    SparkSession 实例
     * @param dataRoot 原始数据文件所在目录路径
     */
    public DataLoader(SparkSession spark, String dataRoot) {
        this.spark = spark;
        this.dataRoot = dataRoot.endsWith("/") ? dataRoot : dataRoot + "/";
    }

    // ==================== 各数据集 Schema 定义 ====================

    /**
     * 订单表 Schema
     */
    private static StructType ordersSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("order_id", DataTypes.StringType, false),
                DataTypes.createStructField("customer_id", DataTypes.StringType, false),
                DataTypes.createStructField("order_status", DataTypes.StringType, true),
                DataTypes.createStructField("order_purchase_timestamp", DataTypes.TimestampType, true),
                DataTypes.createStructField("order_approved_at", DataTypes.TimestampType, true),
                DataTypes.createStructField("order_delivered_carrier_date", DataTypes.TimestampType, true),
                DataTypes.createStructField("order_delivered_customer_date", DataTypes.TimestampType, true),
                DataTypes.createStructField("order_estimated_delivery_date", DataTypes.TimestampType, true),
        });
    }

    /**
     * 订单商品表 Schema
     */
    private static StructType orderItemsSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("order_id", DataTypes.StringType, false),
                DataTypes.createStructField("order_item_id", DataTypes.IntegerType, false),
                DataTypes.createStructField("product_id", DataTypes.StringType, false),
                DataTypes.createStructField("seller_id", DataTypes.StringType, false),
                DataTypes.createStructField("shipping_limit_date", DataTypes.TimestampType, true),
                DataTypes.createStructField("price", DataTypes.DoubleType, true),
                DataTypes.createStructField("freight_value", DataTypes.DoubleType, true),
        });
    }

    /**
     * 商品表 Schema
     */
    private static StructType productsSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("product_id", DataTypes.StringType, false),
                DataTypes.createStructField("product_category_name", DataTypes.StringType, true),
                DataTypes.createStructField("product_name_lenght", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_description_lenght", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_photos_qty", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_weight_g", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_length_cm", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_height_cm", DataTypes.IntegerType, true),
                DataTypes.createStructField("product_width_cm", DataTypes.IntegerType, true),
        });
    }

    /**
     * 卖家表 Schema
     */
    private static StructType sellersSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("seller_id", DataTypes.StringType, false),
                DataTypes.createStructField("seller_zip_code_prefix", DataTypes.StringType, true),
                DataTypes.createStructField("seller_city", DataTypes.StringType, true),
                DataTypes.createStructField("seller_state", DataTypes.StringType, true),
        });
    }

    /**
     * 客户表 Schema
     */
    private static StructType customersSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("customer_id", DataTypes.StringType, false),
                DataTypes.createStructField("customer_unique_id", DataTypes.StringType, false),
                DataTypes.createStructField("customer_zip_code_prefix", DataTypes.StringType, true),
                DataTypes.createStructField("customer_city", DataTypes.StringType, true),
                DataTypes.createStructField("customer_state", DataTypes.StringType, true),
        });
    }

    /**
     * 评价表 Schema
     */
    private static StructType reviewsSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("review_id", DataTypes.StringType, false),
                DataTypes.createStructField("order_id", DataTypes.StringType, false),
                DataTypes.createStructField("review_score", DataTypes.IntegerType, true),
                DataTypes.createStructField("review_comment_title", DataTypes.StringType, true),
                DataTypes.createStructField("review_comment_message", DataTypes.StringType, true),
                DataTypes.createStructField("review_creation_date", DataTypes.TimestampType, true),
                DataTypes.createStructField("review_answer_timestamp", DataTypes.TimestampType, true),
        });
    }

    /**
     * 品类名称翻译表 Schema
     */
    private static StructType categoryTranslationSchema() {
        return DataTypes.createStructType(new StructField[]{
                DataTypes.createStructField("product_category_name", DataTypes.StringType, false),
                DataTypes.createStructField("product_category_name_english", DataTypes.StringType, true),
        });
    }

    // ==================== 核心加载函数 ====================

    /**
     * 加载全部 7 个数据集并返回 Dataset 映射。
     *
     * @return 包含所有数据集的容器对象
     */
    public OlistDatasets loadAll() {
        System.out.println("=== 开始加载 Olist 电商数据集 ===");

        Dataset<Row> orders = loadOrders();
        System.out.println("[1/7] 订单表加载完成: " + orders.count() + " 条记录");

        Dataset<Row> orderItems = loadOrderItems();
        System.out.println("[2/7] 订单商品表加载完成: " + orderItems.count() + " 条记录");

        Dataset<Row> products = loadProducts();
        System.out.println("[3/7] 商品表加载完成: " + products.count() + " 条记录");

        Dataset<Row> sellers = loadSellers();
        System.out.println("[4/7] 卖家表加载完成: " + sellers.count() + " 条记录");

        Dataset<Row> customers = loadCustomers();
        System.out.println("[5/7] 客户表加载完成: " + customers.count() + " 条记录");

        Dataset<Row> reviews = loadReviews();
        System.out.println("[6/7] 评价表加载完成: " + reviews.count() + " 条记录");

        Dataset<Row> categoryTranslation = loadCategoryTranslation();
        System.out.println("[7/7] 品类翻译表加载完成: " + categoryTranslation.count() + " 条记录");

        return new OlistDatasets(orders, orderItems, products, sellers, customers, reviews, categoryTranslation);
    }

    // ==================== 各表加载方法 ====================

    /**
     * 加载订单表
     */
    public Dataset<Row> loadOrders() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
                .schema(ordersSchema())
                .csv(dataRoot + "olist_orders_dataset.csv")
                // 只保留已完成/已交付的订单（有完整履约数据的订单）
                .filter("order_status IN ('delivered', 'shipped')");
    }

    /**
     * 加载订单商品表
     */
    public Dataset<Row> loadOrderItems() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
                .schema(orderItemsSchema())
                .csv(dataRoot + "olist_order_items_dataset.csv")
                // 过滤异常价格（价格应为正数）
                .filter("price > 0 AND freight_value >= 0");
    }

    /**
     * 加载商品表
     */
    public Dataset<Row> loadProducts() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(productsSchema())
                .csv(dataRoot + "olist_products_dataset.csv");
    }

    /**
     * 加载卖家表
     */
    public Dataset<Row> loadSellers() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(sellersSchema())
                .csv(dataRoot + "olist_sellers_dataset.csv");
    }

    /**
     * 加载客户表
     */
    public Dataset<Row> loadCustomers() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(customersSchema())
                .csv(dataRoot + "olist_customers_dataset.csv");
    }

    /**
     * 加载评价表
     */
    public Dataset<Row> loadReviews() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .option("timestampFormat", "yyyy-MM-dd HH:mm:ss")
                .schema(reviewsSchema())
                .csv(dataRoot + "olist_order_reviews_dataset.csv");
    }

    /**
     * 加载品类名称翻译表（葡萄牙语 → 英语）
     */
    public Dataset<Row> loadCategoryTranslation() {
        return spark.read()
                .option("header", "true")
                .option("delimiter", ",")
                .schema(categoryTranslationSchema())
                .csv(dataRoot + "product_category_name_translation.csv");
    }

    // ==================== 核心分析视图构建 ====================

    /**
     * 构建核心分析宽表：订单-商品-品类-卖家-评价五表联合视图。
     * <p>
     * 这是后续所有分析（弹性/竞品/波士顿矩阵）的基础数据集。
     * </p>
     *
     * @param datasets 全部原始数据集
     * @return 宽表 Dataset（每个订单商品一行，含品类名、地理信息、评分）
     */
    public Dataset<Row> buildAnalyticalView(OlistDatasets datasets) {
        System.out.println("=== 构建核心分析宽表 ===");

        // Step 1: 商品 × 品类翻译 → 获得英文品类名
        Dataset<Row> productsEnriched = datasets.products
                .join(datasets.categoryTranslation,
                        datasets.products.col("product_category_name")
                                .equalTo(datasets.categoryTranslation.col("product_category_name")),
                        "left")
                // 如果有翻译就用翻译，否则保留原始葡萄牙语名称
                .withColumn("category_name",
                        org.apache.spark.sql.functions.coalesce(
                                datasets.categoryTranslation.col("product_category_name_english"),
                                datasets.products.col("product_category_name")))
                .drop(datasets.categoryTranslation.col("product_category_name"));

        // Step 2: 订单 × 订单商品 → 关联价格、商品ID、卖家ID
        Dataset<Row> ordersWithItems = datasets.orders
                .join(datasets.orderItems,
                        datasets.orders.col("order_id")
                                .equalTo(datasets.orderItems.col("order_id")),
                        "inner")
                .drop(datasets.orderItems.col("order_id"));

        // Step 3: 关联商品信息 → 获得品类、重量、尺寸
        Dataset<Row> withProducts = ordersWithItems
                .join(productsEnriched,
                        ordersWithItems.col("product_id")
                                .equalTo(productsEnriched.col("product_id")),
                        "left")
                .drop(productsEnriched.col("product_id"));

        // Step 4: 关联卖家信息 → 获得卖家地理位置
        Dataset<Row> withSellers = withProducts
                .join(datasets.sellers,
                        withProducts.col("seller_id")
                                .equalTo(datasets.sellers.col("seller_id")),
                        "left")
                .drop(datasets.sellers.col("seller_id"));

        // Step 5: 关联评价信息 → 获得评分
        Dataset<Row> withReviews = withSellers
                .join(datasets.reviews,
                        withSellers.col("order_id")
                                .equalTo(datasets.reviews.col("order_id")),
                        "left")
                .drop(datasets.reviews.col("order_id"));

        // Step 6: 关联客户信息 → 获得客户地理位置
        Dataset<Row> fullView = withReviews
                .join(datasets.customers,
                        withReviews.col("customer_id")
                                .equalTo(datasets.customers.col("customer_id")),
                        "left")
                .drop(datasets.customers.col("customer_id"));

        long count = fullView.count();
        System.out.println("分析宽表构建完成: " + count + " 条记录 (预期 ~112k)");

        return fullView;
    }

    // ==================== 数据集容器 ====================

    /**
     * Olist 全部 7 个数据集的容器类。
     */
    public static class OlistDatasets {
        public final Dataset<Row> orders;
        public final Dataset<Row> orderItems;
        public final Dataset<Row> products;
        public final Dataset<Row> sellers;
        public final Dataset<Row> customers;
        public final Dataset<Row> reviews;
        public final Dataset<Row> categoryTranslation;

        public OlistDatasets(Dataset<Row> orders, Dataset<Row> orderItems,
                             Dataset<Row> products, Dataset<Row> sellers,
                             Dataset<Row> customers, Dataset<Row> reviews,
                             Dataset<Row> categoryTranslation) {
            this.orders = orders;
            this.orderItems = orderItems;
            this.products = products;
            this.sellers = sellers;
            this.customers = customers;
            this.reviews = reviews;
            this.categoryTranslation = categoryTranslation;
        }
    }
}
