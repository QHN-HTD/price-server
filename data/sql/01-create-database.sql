-- ============================================================
-- PriceWise — 数据库初始化脚本
-- ============================================================
-- 版本: v1.0
-- 日期: 2026-06-01
-- 数据库: MySQL 8.0+
--
-- 安全说明:
--   1. 所有敏感字段已通过 Spark ETL 脱敏处理
--   2. InnoDB 表空间加密需在 MySQL 配置中单独启用
--   3. 数据库用户应使用最小权限原则
-- ============================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS smart_pricing
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_pricing;

-- ============================================================
-- 1. 品类维度表
-- ============================================================
CREATE TABLE IF NOT EXISTS dim_category (
    category_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name       VARCHAR(100) NOT NULL UNIQUE COMMENT '品类名称（英文）',
    category_name_pt    VARCHAR(100) COMMENT '品类名称（葡萄牙语原版）',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_category_name (category_name)
) ENGINE=InnoDB COMMENT='品类维度表';

-- ============================================================
-- 2. 价格弹性系数事实表
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_elasticity (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name       VARCHAR(100) NOT NULL COMMENT '品类名称',
    elasticity          DOUBLE NOT NULL COMMENT '价格弹性系数 β（负值=正常，正=吉芬商品）',
    intercept           DOUBLE COMMENT '回归截距 α',
    r_squared           DOUBLE COMMENT 'R² 拟合优度 (0-1)',
    p_value             DOUBLE COMMENT '价格系数的 p-value',
    sample_size         INT COMMENT '样本量（月份数）',
    elasticity_label    VARCHAR(50) COMMENT '弹性解释标签（如"高弹性"）',
    pricing_strategy    TEXT COMMENT '定价策略建议',
    model_version       VARCHAR(20) DEFAULT 'v1.0' COMMENT '模型版本',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_category (category_name),
    INDEX idx_elasticity (elasticity),
    INDEX idx_created (created_at)
) ENGINE=InnoDB COMMENT='价格弹性系数事实表 — Spark ML 对数线性回归结果';

-- ============================================================
-- 3. 竞品生态位事实表
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_competitor_niche (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id               VARCHAR(64) NOT NULL COMMENT '卖家ID（已脱敏-SHA256）',
    category_name           VARCHAR(100) NOT NULL COMMENT '品类名称',
    avg_price               DOUBLE COMMENT '平均售价',
    avg_score               DOUBLE COMMENT '平均评分',
    total_sales             BIGINT COMMENT '总销量',
    total_revenue           DOUBLE COMMENT '总营收',
    price_percentile        DOUBLE COMMENT '价格品类内百分位 (0-1)',
    sales_percentile        DOUBLE COMMENT '销量品类内百分位 (0-1)',
    score_percentile        DOUBLE COMMENT '评分品类内百分位 (0-1)',
    niche_label             VARCHAR(30) COMMENT '生态位标签（高质高价区/性价比区/低价冲量区/红海竞争区/劣势区）',
    competitive_advice      TEXT COMMENT '竞争策略建议',
    competitiveness_score   DOUBLE COMMENT '综合竞争力评分 (0-100)',
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_seller (seller_id),
    INDEX idx_category (category_name),
    INDEX idx_niche (niche_label),
    UNIQUE KEY uk_seller_category (seller_id, category_name)
) ENGINE=InnoDB COMMENT='竞品生态位事实表';

-- ============================================================
-- 4. 品类竞争格局摘要表
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_category_landscape (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name                   VARCHAR(100) NOT NULL UNIQUE COMMENT '品类名称',
    premium_count                   INT DEFAULT 0 COMMENT '高质高价区卖家数',
    value_count                     INT DEFAULT 0 COMMENT '性价比区卖家数',
    volume_count                    INT DEFAULT 0 COMMENT '低价冲量区卖家数',
    red_ocean_count                 INT DEFAULT 0 COMMENT '红海竞争区卖家数',
    disadvantaged_count             INT DEFAULT 0 COMMENT '劣势区卖家数',
    category_avg_price              DOUBLE COMMENT '品类平均价格',
    category_avg_score              DOUBLE COMMENT '品类平均评分',
    category_avg_competitiveness    DOUBLE COMMENT '品类平均竞争力评分',
    total_sellers                   INT COMMENT '卖家总数',
    competition_intensity           VARCHAR(10) COMMENT '竞争强度（激烈/中等/温和）',
    market_concentration            DOUBLE COMMENT '市场集中度 (0-1)',
    opportunity_index               DOUBLE COMMENT '机会指数 (0-1)',
    created_at                      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_category (category_name),
    INDEX idx_intensity (competition_intensity)
) ENGINE=InnoDB COMMENT='品类竞争格局摘要表';

-- ============================================================
-- 5. 波士顿矩阵事实表（最新季度快照）
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_boston_matrix (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name           VARCHAR(100) NOT NULL COMMENT '品类名称',
    year_quarter            VARCHAR(10) NOT NULL COMMENT '年份-季度 (如 2018-Q3)',
    boston_quadrant         VARCHAR(20) COMMENT '波士顿象限（Star/Cash Cow/Question Mark/Dog）',
    boston_quadrant_cn      VARCHAR(10) COMMENT '象限中文名（明星/金牛/问题/瘦狗）',
    boston_strategy         TEXT COMMENT '象限对应的策略建议',
    relative_market_share   DOUBLE COMMENT '相对市场份额 (0-1)',
    qoq_growth_rate         DOUBLE COMMENT '环比增长率',
    market_share            DOUBLE COMMENT '绝对市场份额 (0-1)',
    total_revenue           DOUBLE COMMENT '总营收',
    total_quantity          BIGINT COMMENT '总销量',
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_category (category_name),
    INDEX idx_quarter (year_quarter),
    INDEX idx_quadrant (boston_quadrant),
    UNIQUE KEY uk_category_quarter (category_name, year_quarter)
) ENGINE=InnoDB COMMENT='波士顿矩阵事实表（最新季度快照）';

-- ============================================================
-- 6. 波士顿矩阵象限分布统计表
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_boston_distribution (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    boston_quadrant         VARCHAR(20) NOT NULL COMMENT '波士顿象限',
    boston_quadrant_cn      VARCHAR(10) COMMENT '象限中文名',
    category_count          INT COMMENT '该象限品类数量',
    quadrant_total_revenue  DOUBLE COMMENT '该象限总营收',
    quadrant_avg_share      DOUBLE COMMENT '该象限平均市场份额',
    quadrant_avg_growth     DOUBLE COMMENT '该象限平均增长率',
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_quadrant (boston_quadrant)
) ENGINE=InnoDB COMMENT='波士顿矩阵象限分布统计表';

-- ============================================================
-- 7. 品类健康度评分卡表
-- ============================================================
CREATE TABLE IF NOT EXISTS fact_category_health (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name           VARCHAR(100) NOT NULL UNIQUE COMMENT '品类名称',
    total_quantity_all      BIGINT COMMENT '全周期总销量',
    total_revenue_all       DOUBLE COMMENT '全周期总营收',
    avg_price_weighted      DOUBLE COMMENT '时间加权平均价格',
    avg_review_score_all    DOUBLE COMMENT '平均评分',
    avg_return_rate         DOUBLE COMMENT '平均退货率',
    avg_growth_rate         DOUBLE COMMENT '平均季度增长率',
    health_score            DOUBLE COMMENT '综合健康度评分 (0-100)',
    health_level            VARCHAR(10) COMMENT '健康等级（A-优秀/B-良好/C-一般/D-关注）',
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_category (category_name),
    INDEX idx_health_score (health_score),
    INDEX idx_health_level (health_level)
) ENGINE=InnoDB COMMENT='品类健康度评分卡表';

-- ============================================================
-- 8. 审计日志表
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) COMMENT '操作用户',
    operation       VARCHAR(100) NOT NULL COMMENT '操作描述',
    api_path        VARCHAR(200) COMMENT 'API 路径',
    client_ip       VARCHAR(45) COMMENT '客户端 IP',
    request_params  TEXT COMMENT '请求参数摘要',
    response_status INT COMMENT 'HTTP 响应状态码',
    execution_time  BIGINT COMMENT '执行耗时 (ms)',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_username (username),
    INDEX idx_operation (operation),
    INDEX idx_created (created_at)
) ENGINE=InnoDB COMMENT='API 审计日志表 — AOP 自动记录';

-- ============================================================
-- 9. 用户表（Spring Security 认证用）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash   VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    role            VARCHAR(20) NOT NULL DEFAULT 'ANALYST' COMMENT '角色（ADMIN/ANALYST）',
    display_name    VARCHAR(50) COMMENT '显示名称',
    enabled         TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    last_login      TIMESTAMP COMMENT '最后登录时间',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_username (username),
    INDEX idx_role (role)
) ENGINE=InnoDB COMMENT='系统用户表 — Spring Security 认证';

-- ============================================================
-- 初始化默认用户（密码需在应用层使用 BCrypt 加密）
-- 默认管理员: admin / admin123
-- 默认分析师: analyst / analyst123
-- ============================================================
INSERT INTO sys_user (username, password_hash, role, display_name) VALUES
    ('admin',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN',   '系统管理员'),
    ('analyst', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ANALYST', '数据分析师')
ON DUPLICATE KEY UPDATE username=username;

-- ============================================================
-- 完成
-- ============================================================
SELECT '✅ 数据库初始化完成 — smart_pricing' AS status;
SHOW TABLES;
