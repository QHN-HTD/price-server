# PriceWise — 使用文档

> 电商智能定价决策引擎 v1.0  
> 更新日期：2026-06-02

---

## 目录

1. [项目简介](#1-项目简介)
2. [环境要求](#2-环境要求)
3. [快速启动](#3-快速启动)
4. [系统架构](#4-系统架构)
5. [Dashboard 操作指南](#5-dashboard-操作指南)
6. [API 接口文档](#6-api-接口文档)
7. [ETL 数据管道使用](#7-etl-数据管道使用)
8. [常见问题](#8-常见问题)

---

## 1. 项目简介

PriceWise 是一个基于真实电商数据的智能定价分析平台，核心功能包括：

| 模块 | 功能 | 技术 |
|------|------|------|
| 价格弹性分析 | 量化品类价格敏感度，逐品类回归建模 | Spark ML + 对数线性回归 |
| 竞品生态位画像 | 价格×评分×销量三维空间竞争定位 | 百分位分析 + 5区分类 |
| 波士顿矩阵诊断 | BCG 四象限分类 + 季度迁移追踪 | 双维度矩阵 + 雷达图 |
| What-If 模拟器 ⭐ | 交互式调价推演，预测利润影响 | 弹性模型 + 场景模拟 |
| 品类健康度评分 | 5维度加权综合评分卡 | Min-Max 归一化 + 加权模型 |

**面试叙事主线：**

> "我们发现 A 品类的价格弹性系数是 2.3，当前定价偏高，处于波士顿矩阵的'问题象限'。通过 What-If 模拟，降价 12% 预计销量提升 27%，毛利总额增长 8.5%。系统给出的策略建议是'渗透定价，抢占市场份额'。"

---

## 2. 环境要求

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 17 或 19 | 运行 Spring Boot 和 Spark |
| MySQL | 5.7 或 8.0 | 分析结果存储 |
| Maven | 3.9+ | 项目构建 |
| IntelliJ IDEA | 2024+ | 开发运行（推荐） |

### 数据源（可选）

- [Kaggle Olist Brazilian E-Commerce](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce) — 运行 ETL 时需要
- 不需要数据源也可以运行，项目内置 15 个品类的模拟演示数据

---

## 3. 快速启动

### 3.1 启动 MySQL

确保 MySQL 服务已运行（phpstudy 或其他方式）。

### 3.2 初始化数据库

```bash
# 创建数据库和表结构
mysql -u root -p < data/sql/01-create-database.sql

# 填充模拟演示数据
mysql -u root -p < data/sql/02-seed-data.sql
```

### 3.3 启动 API 服务

**方式一：IntelliJ IDEA（推荐）**

1. 打开项目：`File → Open → 选择 pricing-server 目录`
2. 运行主类：`com.pricing.server.PricingServerApplication`
3. 看到启动横幅即表示成功

**方式二：Maven 命令行**

```bash
cd pricing-server
set MYSQL_USER=root
set MYSQL_PASS=your_password
mvn spring-boot:run
```

### 3.4 访问 Dashboard

浏览器打开：**http://localhost:8080/api/v1/**

### 3.5 登录

| 用户名 | 密码 | 角色 | 权限 |
|--------|------|------|------|
| **admin** | **admin123** | ADMIN | 全量数据 + ETL管理 |
| analyst | analyst123 | ANALYST | 聚合数据查看 |

---

## 4. 系统架构

```
┌─────────────────────────────────────────────────┐
│                浏览器 Dashboard                   │
│         http://localhost:8080/api/v1/             │
└────────────────────┬────────────────────────────┘
                     │ HTTP (同源)
┌────────────────────▼────────────────────────────┐
│           Spring Boot (端口 8080)                 │
│                                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────────────┐  │
│  │ 安全层   │ │ REST API │ │ 静态资源服务     │  │
│  │ JWT+RBAC │ │ 16个端点 │ │ Dashboard HTML   │  │
│  │ 限流+XSS │ │ 5个模块  │ │ + ECharts(本地)  │  │
│  └──────────┘ └────┬─────┘ └──────────────────┘  │
│                    │                              │
│  ┌─────────────────▼──────────────────────────┐  │
│  │ MyBatis → MySQL (smart_pricing 数据库)     │  │
│  │ 9张表: 弹性/竞品/矩阵/健康度/审计/用户     │  │
│  └────────────────────────────────────────────┘  │
│                                                   │
│  ┌────────────────────────────────────────────┐  │
│  │ ETL 管道服务 (可选, 按需触发)               │  │
│  │ Spark 3.5 → CSV 加载 → 脱敏 → 建模 → MySQL │  │
│  └────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

---

## 5. Dashboard 操作指南

Dashboard 采用暗色大屏风格，登录后默认展示"价格弹性分析"面板。左侧导航栏可切换 5 个面板。

### 5.1 价格弹性分析

| 图表 | 说明 |
|------|------|
| 品类弹性系数柱状图 | 红色=高弹性(β<-1)，橙色=中等，绿色=低弹性。标记线指示高弹性线和零弹性线 |
| 弹性等级分布饼图 | 极高弹性/高弹性/中等弹性/低弹性/极低弹性的品类数量和占比 |
| 价格-销量散点图 | 每个点代表一个品类，颜色表示弹性高低，大小表示销量规模 |
| 弹性策略速查 | Top 10 品类的弹性系数和定价策略建议卡片 |

**面试展示要点：** 指着红色柱子说"这些品类价格敏感度高，降价策略非常有效"。

### 5.2 竞品生态位画像

| 图表 | 说明 |
|------|------|
| 3D 生态位气泡图 | X=价格百分位，Y=评分百分位，气泡大小=销量。5种颜色对应5个生态位区域 |
| 生态位分布饼图 | 各生态位的卖家数量占比 |
| 竞争格局概览表 | 每个品类的竞争强度、各生态位卖家分布、机会指数 |

下拉框可选择具体品类查看其内部竞争格局。

**面试展示要点：** 演示品类切换，说"在这个品类中我们的卖家处于性价比区，建议适度提价"。

### 5.3 波士顿矩阵诊断

| 图表 | 说明 |
|------|------|
| 四象限气泡图 | X=相对市场份额，Y=增长率，气泡=总营收。四象限标注：⭐明星/🐄金牛/❓问题/🐕瘦狗 |
| 象限分布统计 | 各象限品类数量和总营收 |
| 雷达图 | Top 5 品类多维度对比（市场份额/增长率/营收/销量） |

**面试展示要点：** 指着明星象限说"这三个品类应加大投入"，指着瘦狗说"这五个品类应考虑缩减"。

### 5.4 What-If 调价模拟器 ⭐

这是项目的**核心亮点**，面试时重点演示。

**操作步骤：**

1. 从下拉框选择一个品类（如 `health_beauty`）
2. 拖动滑块设置调价幅度（-30% ~ +30%）
3. 点击 **"执行模拟"** 查看该方案的详细预测
4. 点击 **"批量场景对比"** 查看 -20% ~ +20% 多方案对比表

**输出内容：**

| 输出 | 说明 |
|------|------|
| 决策建议卡片 | 推荐调价/谨慎操作/不建议 + 详细分析文本 |
| 利润影响瀑布图 | 当前营收 → 价格变化影响 → 预测营收 |
| 场景对比表 | 9种调价方案的新价格/预测销量/营收变化/建议标签 |

**面试展示要点：**

> "以 health_beauty 为例，降价 10%，预测销量提升 23.5%，营收增长约 12%。系统判断为'推荐调价'。"

### 5.5 品类健康度评分

| 图表 | 说明 |
|------|------|
| 健康度评分柱状图 | 0-100 分，颜色对应 ABCD 四级。分级标记线标注各级阈值 |
| 健康等级分布 | ABCD 四级品类的数量和占比 |
| 雷达图 | Top 5 品类的五维度对比（健康评分/营收/增长率/评分/稳定性） |
| 仪表盘汇总 | 总品类数/平均健康分/优秀数/需关注数 |

---

## 6. API 接口文档

### 6.1 基础信息

- **Base URL：** `http://localhost:8080/api/v1`
- **认证方式：** Bearer Token (JWT)
- **Token 有效期：** 30 分钟

### 6.2 认证接口

#### POST /auth/login — 用户登录

```
POST /api/v1/auth/login
Content-Type: application/json

{
    "username": "admin",
    "password": "admin123"
}

Response 200:
{
    "code": 200,
    "message": "登录成功",
    "data": {
        "token": "eyJhbGci...",
        "tokenType": "Bearer",
        "expiresIn": 1799,
        "username": "admin",
        "displayName": "系统管理员",
        "role": "ADMIN"
    }
}
```

#### GET /auth/verify — Token 验证

```
GET /api/v1/auth/verify
Authorization: Bearer <token>

Response 200: {"code": 200, "message": "Token 有效"}
```

### 6.3 弹性分析接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/elasticity/all` | 所有品类弹性系数 |
| GET | `/dashboard/elasticity/{category}` | 指定品类弹性 |
| GET | `/dashboard/elasticity/high-elasticity` | 高弹性品类 (|β|>1) |
| GET | `/dashboard/elasticity/distribution` | 弹性等级统计 |
| POST | `/dashboard/elasticity/simulate` | ⭐ 单次 What-If 模拟 |
| GET | `/dashboard/elasticity/simulate/batch?categoryName=X` | 批量场景对比 |

**What-If 模拟请求示例：**

```
POST /api/v1/dashboard/elasticity/simulate
Authorization: Bearer <token>
Content-Type: application/json

{
    "categoryName": "health_beauty",
    "priceChangePct": -10.0
}

Response 200:
{
    "code": 200,
    "data": {
        "categoryName": "health_beauty",
        "elasticity": -2.35,
        "currentPrice": 100.0,
        "newPrice": 90.0,
        "priceChangePct": -10.0,
        "predictedSalesChangePct": 23.5,
        "currentSales": 10000,
        "predictedSales": 12350,
        "currentRevenue": 1000000.0,
        "predictedRevenue": 1111500.0,
        "revenueChange": 111500.0,
        "revenueChangePct": 11.15,
        "recommendation": "推荐调价",
        "analysis": "该品类弹性系数为 -2.35，属于极高弹性。建议执行调价方案...",
        "profitImpact": 111500.0
    }
}
```

### 6.4 竞品分析接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/competitor/niche/category?categoryName=X` | 品类卖家生态位 |
| GET | `/dashboard/competitor/niche/label?nicheLabel=X` | 按生态位标签查询 |
| GET | `/dashboard/competitor/niche/distribution` | 生态位分布统计 |
| GET | `/dashboard/competitor/landscape/all` | 竞争格局列表 |
| GET | `/dashboard/competitor/landscape/{category}` | 指定品类格局 |

### 6.5 波士顿矩阵接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/boston-matrix/snapshot` | 最新季度快照 |
| GET | `/dashboard/boston-matrix/quadrant/{quadrant}` | 按象限查询 (Star/Cash Cow/Question Mark/Dog) |
| GET | `/dashboard/boston-matrix/distribution` | 象限分布统计 |
| GET | `/dashboard/boston-matrix/full` | 完整矩阵数据(含配色) |

### 6.6 健康度接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/dashboard/health/all` | 所有品类健康度 |
| GET | `/dashboard/health/level/{level}` | 按等级查询 (A/B/C/D) |
| GET | `/dashboard/health/distribution` | 健康等级分布 |
| GET | `/dashboard/health/dashboard` | 仪表盘汇总 |
| GET | `/dashboard/health/report` | 综合报告(含雷达图数据) |

### 6.7 管理接口（仅 ADMIN）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/etl/run` | 触发 ETL 管道 |
| GET | `/admin/etl/status` | 查询 ETL 状态 |
| GET | `/admin/health` | 系统健康检查 |

---

## 7. ETL 数据管道使用

ETL 管道将 Olist CSV 原始数据加工为分析结果写入 MySQL。

### 7.1 前置条件

1. 从 [Kaggle](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce) 下载 Olist 数据集
2. 将以下 CSV 文件放入 `data/raw/` 目录：

```
data/raw/
├── olist_orders_dataset.csv
├── olist_order_items_dataset.csv
├── olist_products_dataset.csv
├── olist_sellers_dataset.csv
├── olist_customers_dataset.csv
├── olist_order_reviews_dataset.csv
└── product_category_name_translation.csv
```

### 7.2 通过 API 触发

```bash
# 1. 登录获取 Token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 2. 触发 ETL
curl -X POST http://localhost:8080/api/v1/admin/etl/run \
  -H "Authorization: Bearer $TOKEN"

# 3. 查看状态
curl http://localhost:8080/api/v1/admin/etl/status \
  -H "Authorization: Bearer $TOKEN"
```

### 7.3 ETL 处理流程

```
加载CSV → 五表JOIN宽表 → PII脱敏(SHA-256)
    → 特征工程(品类×季度聚合) → 健康度评分卡
    → 价格弹性建模(Spark ML回归)
    → 竞品生态位分类(5区)
    → 波士顿矩阵诊断(四象限)
    → 写入MySQL(9张表)
```

### 7.4 脱敏安全说明

| 层级 | 处理 | 方法 |
|------|------|------|
| L1 | 所有ID | SHA-256 + 盐值 → 不可逆哈希 |
| L2 | 邮政编码 | 截断至前3位 → 区域级别 |
| L2 | 城市名 | 标准化 + 统一格式 |
| L3 | 评论文本 | 正则匹配替换电话/邮箱/CPF |

---

## 8. 常见问题

### Q1：Dashboard 打开后是空白页

**原因：** 浏览器缓存了旧版本或 ECharts 未加载。

**解决：**
1. 强制刷新：`Ctrl + Shift + R`
2. 确认 URL 是 `http://localhost:8080/api/v1/`
3. 确认 Spring Boot 服务已启动（控制台有启动横幅）

### Q2：登录后图表为空

**原因：** 数据库中无分析数据。

**解决：** 执行模拟数据脚本：
```bash
mysql -u root -p < data/sql/02-seed-data.sql
```

### Q3：运行 ETL 时报错 "Port 8080 already in use"

**原因：** 端口被占用。

**解决：**
```bash
# Windows
netstat -ano | findstr ":8080"
taskkill //PID <PID> //F
```

### Q4：ETL 报错 "NoClassDefFoundError: org/apache/spark/SparkConf"

**原因：** 在 IntelliJ 直接运行 EtlPipelineService 缺少 VM 参数。

**解决：** 不要直接运行 ETL 类，始终通过 API 端点触发（`POST /admin/etl/run`），或添加 JVM 参数：
```
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.invoke=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.net=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens=java.base/sun.security.action=ALL-UNNAMED
```

### Q5：Spark ETL 运行很慢

**原因：** 本地模式下 Spark 使用所有 CPU 核心，内存占用大。

**解决：** 在 `SparkSessionFactory.java` 中将 `local[*]` 改为 `local[2]`（限制2核）。

### Q6：CORS 跨域错误

**原因：** 前端和后端不在同一域名。

**解决：** 始终使用 `http://localhost:8080/api/v1/` 访问，Dashboard 和 API 同源，不存在跨域问题。

---

## 附录：项目文件结构

```
pricing-server/
├── src/main/java/com/pricing/server/
│   ├── PricingServerApplication.java       # 🚀 主入口
│   ├── controller/                          # 🌐 6个控制器
│   │   ├── AuthController.java              #   登录认证
│   │   ├── ElasticityController.java        #   弹性分析 + What-If
│   │   ├── CompetitorController.java        #   竞品生态位
│   │   ├── BostonMatrixController.java      #   波士顿矩阵
│   │   ├── HealthScoreController.java       #   健康度评分
│   │   └── AdminController.java             #   管理接口(ETL触发)
│   ├── service/                             # 💼 5个业务服务
│   ├── repository/                          # 📊 6个 MyBatis Mapper
│   ├── security/                            # 🔐 JWT + Spring Security
│   ├── etl/                                 # 🔵 Spark ETL 管道(9个类)
│   │   ├── EtlPipelineService.java          #   ETL 编排服务
│   │   ├── DataLoader.java                  #   CSV 加载
│   │   ├── DataMasking.java                 #   PII 脱敏
│   │   ├── FeatureEngineer.java             #   特征工程
│   │   ├── PriceElasticityModel.java        #   弹性模型(Spark ML)
│   │   ├── CompetitorAnalysisModel.java     #   竞品分析
│   │   ├── BostonMatrixModel.java           #   波士顿矩阵
│   │   ├── SparkSessionFactory.java         #   Spark会话工厂
│   │   └── SecurityUtils.java               #   安全工具类
│   ├── model/entity/ (6) + dto/ (4)         # 📦 数据模型
│   ├── aop/AuditLogAspect.java              # 📝 审计日志
│   ├── config/ (4)                          # ⚙️ 限流/CORS/XSS/异常
│   └── common/Result.java + PageResult.java # 📋 通用响应
├── src/main/resources/
│   ├── application.yml                      # 主配置
│   └── static/                              # Dashboard 静态文件
│       ├── index.html                       #   主页面
│       ├── echarts.min.js                   #   ECharts (本地)
│       ├── css/style.css                    #   暗色大屏样式
│       └── js/ (8个模块)                    #   图表 + 交互逻辑
├── pom.xml                                  # Maven 配置
└── README.md                                # 模块说明
```

---

> 📧 问题反馈：请查阅 `CLAUDE.md` 了解项目操作规则  
> 📖 设计文档：`docs/superpowers/specs/2026-06-01-smart-pricing-engine-design.md`
