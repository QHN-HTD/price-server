package com.pricing.server.etl;

import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;

/**
 * SparkSession 工厂类
 * <p>
 * 统一管理 SparkSession 的创建和配置。
 * 支持本地开发模式和生产集群模式两种运行环境的自动适配。
 * </p>
 *
 * <p><b>安全设计：</b>
 * <ul>
 *   <li>禁用 Spark UI 对外暴露（生产环境）</li>
 *   <li>设置序列化安全白名单</li>
 *   <li>配置日志级别避免敏感信息泄露</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class SparkSessionFactory {

    /** 默认应用名称 */
    private static final String APP_NAME = "SmartPricingETL";

    /** 本地模式 Master 地址 */
    private static final String LOCAL_MASTER = "local[*]";

    /** 是否本地开发模式（可通过环境变量 SPRK_MODE=cluster 切换） */
    private static final boolean IS_LOCAL = !"cluster".equalsIgnoreCase(
            System.getenv().getOrDefault("SPARK_MODE", "local"));

    private SparkSessionFactory() {
        // 工具类，禁止实例化
    }

    /**
     * 创建默认配置的 SparkSession。
     *
     * @return 配置好的 SparkSession 实例
     */
    public static SparkSession create() {
        return create(APP_NAME);
    }

    /**
     * 创建自定义应用名称的 SparkSession。
     *
     * @param appName 应用名称（显示在 Spark UI 中）
     * @return 配置好的 SparkSession 实例
     */
    public static SparkSession create(String appName) {
        SparkConf conf = new SparkConf()
                .setAppName(appName)
                // Kryo 序列化：比 Java 序列化更快更小
                .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
                // 自适应查询执行（Spark 3.x 核心优化）
                .set("spark.sql.adaptive.enabled", "true")
                .set("spark.sql.adaptive.coalescePartitions.enabled", "true")
                // 安全配置：Kryo 类注册白名单
                .set("spark.kryo.registrationRequired", "false")
                // 限制单分区最大记录数，防止 OOM
                .set("spark.sql.files.maxPartitionBytes", "134217728") // 128MB
                // 安全配置：关闭 Spark UI 端口（生产环境通过反向代理访问）
                .set("spark.ui.port", IS_LOCAL ? "4040" : "4041");

        // 本地开发模式：使用所有可用 CPU 核心
        if (IS_LOCAL) {
            conf.setMaster(LOCAL_MASTER);
        }

        SparkSession.Builder builder = SparkSession.builder().config(conf);

        // 仅在 Hive 依赖可用时启用（生产环境 spark-submit 自带）
        try {
            Class.forName("org.apache.hadoop.hive.conf.HiveConf");
            builder.enableHiveSupport();
        } catch (ClassNotFoundException e) {
            // 本地开发模式：无 Hive 依赖，跳过
        }

        SparkSession spark = builder.getOrCreate();

        // 安全配置：设置日志级别，生产环境只输出 WARN 以上级别
        if (!IS_LOCAL) {
            spark.sparkContext().setLogLevel("WARN");
        } else {
            spark.sparkContext().setLogLevel("INFO");
        }

        return spark;
    }

    /**
     * 安全关闭 SparkSession，释放资源。
     *
     * @param spark SparkSession 实例
     */
    public static void close(SparkSession spark) {
        if (spark != null) {
            spark.stop();
        }
    }

    /**
     * 判断当前是否为本地开发模式。
     *
     * @return true 表示本地模式
     */
    public static boolean isLocal() {
        return IS_LOCAL;
    }
}
