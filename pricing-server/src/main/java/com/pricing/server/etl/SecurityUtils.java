package com.pricing.server.etl;

import org.apache.commons.codec.digest.DigestUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全工具类 — 数据脱敏核心函数
 * <p>
 * 提供 PII（个人身份信息）脱敏所需的所有工具函数：
 * <ul>
 *   <li>客户ID → SHA-256 哈希化（带盐值）</li>
 *   <li>支付金额 → 区间分桶（模糊化处理）</li>
 *   <li>GPS坐标 → 城市级别（精度截断）</li>
 *   <li>地址信息 → 区域级别聚合</li>
 * </ul>
 * </p>
 *
 * <p><b>安全设计原则：</b>
 * <ul>
 *   <li>哈希不可逆 — 即使数据库泄露也无法还原原始ID</li>
 *   <li>盐值统一管理 — 可通过环境变量注入，支持定期轮换</li>
 *   <li>金额分桶 — 模糊到区间，防止通过金额反推个人交易</li>
 * </ul>
 * </p>
 *
 * @author PriceWise Team
 * @since 1.0.0
 */
public class SecurityUtils {

    /** 哈希盐值（生产环境应通过环境变量注入并定期轮换） */
    private static final String HASH_SALT = System.getenv()
            .getOrDefault("PII_HASH_SALT", "SmartPricing-2026-SecretSalt");

    /** 金额分桶边界 */
    private static final double[] AMOUNT_BUCKET_BOUNDS = {
            0, 50, 100, 200, 500, 1000, 2000, 5000, Double.MAX_VALUE
    };

    /** 金额分桶标签 */
    private static final String[] AMOUNT_BUCKET_LABELS = {
            "0-50", "50-100", "100-200", "200-500",
            "500-1000", "1000-2000", "2000-5000", "5000+"
    };

    /** GPS 坐标精度（保留小数点后位数，1位 ≈ 11km 精度，城市级别） */
    private static final int GPS_DECIMAL_PLACES = 1;

    private SecurityUtils() {
        // 工具类，禁止实例化
    }

    // ==================== PII 脱敏函数 ====================

    /**
     * 对客户ID进行 SHA-256 哈希处理（带盐值）。
     * <p>
     * 适用场景：Spark ETL 阶段对所有 customer_id 进行不可逆哈希，
     * 确保即使数据库泄露也无法还原原始客户身份。
     * </p>
     *
     * @param customerId 原始客户ID（如 "06b8999e2fba1a1f"）
     * @return SHA-256 哈希值（64位十六进制字符串）
     */
    public static String hashCustomerId(String customerId) {
        if (customerId == null || customerId.isEmpty()) {
            return "";
        }
        // 盐值 + 原始ID → SHA-256
        return DigestUtils.sha256Hex(HASH_SALT + customerId);
    }

    /**
     * 对任意标识符进行哈希处理（用于 seller_id、order_id 等）。
     * 使用与 customer_id 相同的盐值策略。
     *
     * @param id 原始标识符
     * @return SHA-256 哈希值
     */
    public static String hashIdentifier(String id) {
        return hashCustomerId(id); // 复用相同逻辑
    }

    // ==================== 金额脱敏函数 ====================

    /**
     * 将支付金额映射到预定义的模糊区间。
     * <p>
     * 目的：防止通过精确金额反推个人交易记录。
     * 聚合分析时使用区间中值作为近似值。
     * </p>
     *
     * @param amount 原始金额
     * @return 金额区间标签（如 "100-200"）
     */
    public static String bucketAmount(double amount) {
        for (int i = 0; i < AMOUNT_BUCKET_BOUNDS.length - 1; i++) {
            if (amount >= AMOUNT_BUCKET_BOUNDS[i] && amount < AMOUNT_BUCKET_BOUNDS[i + 1]) {
                return AMOUNT_BUCKET_LABELS[i];
            }
        }
        return AMOUNT_BUCKET_LABELS[AMOUNT_BUCKET_LABELS.length - 1];
    }

    /**
     * 获取金额区间的中值（用于聚合计算）。
     *
     * @param bucketLabel 金额区间标签（如 "100-200"）
     * @return 区间中值
     */
    public static double getBucketMidpoint(String bucketLabel) {
        Map<String, Double> midpointMap = new HashMap<>();
        for (int i = 0; i < AMOUNT_BUCKET_LABELS.length; i++) {
            if (i < AMOUNT_BUCKET_BOUNDS.length - 1) {
                double mid = (AMOUNT_BUCKET_BOUNDS[i] + AMOUNT_BUCKET_BOUNDS[i + 1]) / 2.0;
                midpointMap.put(AMOUNT_BUCKET_LABELS[i], mid);
            }
        }
        return midpointMap.getOrDefault(bucketLabel, 0.0);
    }

    /**
     * 聚合级别金额脱敏：对统计结果进行四舍五入到整数。
     * 适用于均值、总和等聚合指标的前端展示。
     *
     * @param value 聚合计算值
     * @return 四舍五入后的整数
     */
    public static long roundAggregatedAmount(double value) {
        return Math.round(value);
    }

    // ==================== GPS/地址脱敏函数 ====================

    /**
     * 将GPS坐标精度截断到城市级别（保留1位小数 ≈ 11km）。
     * <p>
     * 目的：将精确GPS坐标降级为城市区域级别，无法定位到具体地址。
     * </p>
     *
     * @param coordinate 原始GPS坐标
     * @return 精度截断后的坐标
     */
    public static double truncateCoordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate)
                .setScale(GPS_DECIMAL_PLACES, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * 标准化城市名称：去除特殊字符、统一大小写格式。
     *
     * @param city 原始城市名
     * @return 标准化后的城市名
     */
    public static String normalizeCity(String city) {
        if (city == null || city.isEmpty()) {
            return "UNKNOWN";
        }
        return city.trim()
                .toLowerCase()
                .replaceAll("[^a-zà-ü\\s-]", "")
                .replaceAll("\\s+", "_");
    }

    /**
     * 将 ZIP Code 前缀截断到区域级别（仅保留前3位）。
     * 前3位代表较大的地理区域，无法定位到具体街道。
     *
     * @param zipCode 原始邮政编码
     * @return 截断后的区域码（前3位）
     */
    public static String truncateZipCode(String zipCode) {
        if (zipCode == null || zipCode.length() < 3) {
            return zipCode == null ? "" : zipCode;
        }
        return zipCode.substring(0, 3) + "xx";
    }

    // ==================== 文本脱敏函数 ====================

    /**
     * 对评论文本进行脱敏：移除可能包含的姓名、地址、电话等PII。
     * <p>
     * 使用正则表达式匹配常见巴西PII模式：
     * <ul>
     *   <li>电话号码格式: (xx)xxxxx-xxxx</li>
     *   <li>CPF格式 (巴西税号): xxx.xxx.xxx-xx</li>
     *   <li>Email地址</li>
     * </ul>
     * </p>
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String maskReviewText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text
                // 巴西电话号码
                .replaceAll("\\(\\d{2}\\)\\s*\\d{4,5}-\\d{4}", "[PHONE_REDACTED]")
                // CPF格式
                .replaceAll("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}", "[CPF_REDACTED]")
                // Email地址
                .replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[EMAIL_REDACTED]");
    }

    // ==================== 盐值管理 ====================

    /**
     * 获取当前使用的哈希盐值（用于审计，不输出完整值）。
     *
     * @return 盐值的 SHA-256 哈希（双重哈希，安全展示）
     */
    public static String getSaltFingerprint() {
        return DigestUtils.sha256Hex(HASH_SALT).substring(0, 16);
    }
}
