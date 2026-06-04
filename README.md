# PriceWise（智价）— 智能定价决策引擎

> 基于 Spark ML + Spring Boot + ECharts 的电商智能定价分析平台  
> 求职作品集 | Java 后端 / 数据分析师 方向

---

## 🎯 一句话说清这个项目

从 **"这个品类该不该调价"** 到 **"调 12% 预计利润涨 8.5%"** — 用数据回答定价问题。

---

## 🔥 核心亮点

### 1. 定价决策闭环，不是"看看报表"

```
价格弹性建模 → 竞品生态位定位 → 波士顿矩阵诊断 → What-If 模拟 → 策略输出
     ↓                ↓                ↓               ↓            ↓
  "降价有效吗"    "对手在哪"      "值得投入吗"    "具体调多少"   "怎么做"
```

市面上大多数项目停留在可视化看板，这个项目做到了**交互式决策模拟**。

### 2. 算法驱动，不是拍脑袋

| 模块 | 算法 | 面试价值 |
|------|------|---------|
| 价格弹性 | Spark ML 对数线性回归 `ln(Q)=α+β·ln(P)` | 你能讲清楚 β 的含义 |
| 波士顿矩阵 | **P60 分位数动态阈值**（非固定值） | "避免异常值干扰，使分类更稳定" |
| 象限迁移追踪 | 品类生命周期分析 `问题→明星→金牛→瘦狗` | "实现了企业 BI 系统的核心功能" |
| 竞品生态位 | 价格×评分×销量 三维百分位定位 + 5区分类 | 多维分析能力 |
| 健康度评分 | 5维度加权 Min-Max 归一化，动态权重 | 综合评估模型 |

### 3. 安全设计分层落地

```
L1: SHA-256 + 盐值哈希 (不可逆)
L2: 邮政编码截断 + 城市名标准化 (聚合去个性化)
L3: 评论文本 PII 清洗 (正则过滤电话/邮箱/CPF)
    +
JWT 无状态认证 + RBAC 双角色 + Guava 三层限流 + XSS 过滤器 + AOP 审计日志
```

这不是"我写了登录"，而是"我设计了一套安全体系"。

### 4. 真实数据 + 模拟数据双模式

- **演示模式**：内置 15 品类 × 60 卖家模拟数据，开箱即用
- **生产模式**：接入 Kaggle Olist (~100k 真实订单)，调用 API 一键触发 Spark ETL

### 5. 技术栈完整度

| 层级 | 技术 | 深度 |
|------|------|------|
| 数据层 | Spark 3.5 + MLlib + Parquet | 6阶段管道 |
| 服务层 | Spring Boot 3.2 + Security + MyBatis | 16个 API |
| 可视化 | ECharts 5.5 暗色大屏 | 5个面板 15+图表 |
| 部署 | Docker Compose + Nginx + 多阶段 Dockerfile | 一键启动 |

---

## 🏗️ 架构图

```
Kaggle Olist (CSV)         浏览器 Dashboard
     │                     http://localhost:8080
     ▼                            │
┌──────────────┐          ┌───────┴───────┐
│  Spark ETL   │ ──────→  │  Spring Boot  │
│  脱敏→特征   │  MySQL   │  JWT+RBAC     │
│  ML建模→输出 │          │  REST API     │
└──────────────┘          └───────────────┘
```

---

## 🚀 快速启动

### 1. 数据库
```bash
# phpstudy 启动 MySQL，然后：
mysql -u root -p < data/sql/01-create-database.sql
mysql -u root -p < data/sql/02-seed-data.sql
```

### 2. 启动服务
```bash
cd pricing-server
set MYSQL_USER=root
set MYSQL_PASS=your_password
mvn spring-boot:run
```

### 3. 打开浏览器
```
http://localhost:8080/api/v1/
admin / admin123
```

---

## 📊 功能面板

| 面板 | 图表 | 说明 |
|------|------|------|
| 价格弹性分析 | 柱状图 + 散点图 + 饼图 + 策略卡片 | 逐品类弹性系数，对标行业 |
| 竞品生态位画像 | 3D气泡图 + 格局表 + 分布饼图 | 5区生态位（管理员可见个体） |
| 波士顿矩阵诊断 | 四象限气泡图 + 雷达图 + 迁移表 + 排行榜 | P60分位数动态分类 |
| What-If 模拟器 ⭐ | 滑块 + 瀑布图 + 场景对比表 | 核心交互功能 |
| 品类健康度 | 柱状图 + 饼图 + 雷达图 + 仪表盘 | ABCD 四级评分 |

---

## 📁 项目结构

```
PriceWise/
├── pricing-server/           # 主项目（唯一需要启动的）
│   ├── controller/ (6)       # REST API
│   ├── service/ (5)          # 业务逻辑
│   ├── security/ (5)         # JWT + Spring Security
│   ├── etl/ (9)              # Spark ETL 管道
│   ├── aop/                  # 审计日志
│   └── resources/static/     # Dashboard (ECharts 大屏)
├── data/sql/                 # 建库 + 模拟数据
├── docker/                   # Docker Compose 部署
├── docs/                     # 使用文档 + 设计文档
└── dashboard/                # 前端源码
```

---

## 🔑 账号

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员（全量 + ETL） |
| analyst | analyst123 | 分析师（聚合数据） |

---

## 📄 文档

- [使用文档](docs/USER-GUIDE.md)
- [ETL 操作手册](docs/ETL-OPERATION-GUIDE.md)
- [设计规格](docs/superpowers/specs/2026-06-01-smart-pricing-engine-design.md)
