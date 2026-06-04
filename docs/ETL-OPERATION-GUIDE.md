# PriceWise — ETL 真实数据管道操作手册

> 如何从模拟数据切换到 Kaggle Olist 真实电商数据的完整操作步骤

---

## 前置准备

### 1. 下载数据集

从 Kaggle 下载 [Olist Brazilian E-Commerce Dataset](https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce)：

```
需要注册 Kaggle 账号（免费），下载 olist.zip（约 50MB）
```

### 2. 放置 CSV 文件

解压后，将以下 **7 个文件** 放入 `data/raw/` 目录：

```
data/raw/
├── olist_orders_dataset.csv              ← 订单表 (~100k 行)
├── olist_order_items_dataset.csv         ← 订单商品表 (~112k 行)
├── olist_products_dataset.csv            ← 商品表 (~33k 行)
├── olist_sellers_dataset.csv             ← 卖家表 (~3k 行)
├── olist_customers_dataset.csv           ← 客户表 (~96k 行)
├── olist_order_reviews_dataset.csv       ← 评价表 (~100k 行)
└── product_category_name_translation.csv ← 品类翻译表 (~70 行)
```

### 3. 确认 MySQL 运行

phpstudy 中确保 MySQL 已启动，数据库 `smart_pricing` 已存在。

---

## 执行 ETL 管道

### 方式一：API 触发（推荐）

服务启动后，通过管理员 API 触发 ETL：

```bash
# 1. 获取管理员 Token
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  > /tmp/login.json

# 提取 Token（Windows PowerShell 用 jq 或手动复制）
TOKEN=$(grep -o '"token":"[^"]*"' /tmp/login.json | cut -d'"' -f4)

# 2. 触发 ETL 管道
curl -X POST http://localhost:8080/api/v1/admin/etl/run \
  -H "Authorization: Bearer $TOKEN"

# 3. 查看管道状态（可多次查询，跟踪进度）
curl http://localhost:8080/api/v1/admin/etl/status \
  -H "Authorization: Bearer $TOKEN"
```

**响应示例：**

```json
{
  "code": 200,
  "data": {
    "success": true,
    "runId": "20260602_203000",
    "elapsedSeconds": 45,
    "elasticityCount": 15,
    "stages": {
      "初始化": "DONE",
      "加载数据": "DONE",
      "数据脱敏": "DONE",
      "特征工程": "DONE",
      "弹性建模": "DONE",
      "竞品分析": "DONE",
      "波士顿矩阵": "DONE"
    }
  }
}
```

### 方式二：IntelliJ IDEA 运行（需要 JVM 参数）

如果需要断点调试，可在 IntelliJ 中运行。**必须**在 VM options 中添加：

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

然后运行 `EtlPipelineService.runPipeline()` 或通过 API 触发。

---

## ETL 处理流程

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ 阶段1: 加载  │ → │ 阶段2: 脱敏  │ → │ 阶段3: 特征  │
│ 7个CSV→宽表  │    │ SHA256+聚合  │    │ 品类×季度    │
└──────────────┘    └──────────────┘    └──────────────┘
                                               ↓
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ 阶段6: 矩阵  │ ← │ 阶段5: 竞品  │ ← │ 阶段4: 弹性  │
│ BCG四象限    │    │ 5区生态位    │    │ Spark ML回归 │
└──────────────┘    └──────────────┘    └──────────────┘
        ↓
┌──────────────┐
│ 写入 MySQL   │
│ 覆盖6张事实表│
└──────────────┘
```

| 阶段 | 内容 | 耗时（估计） |
|------|------|-------------|
| 1 | 加载 7 个 CSV，JOIN 五表为分析宽表 | ~5s |
| 2 | SHA-256 哈希脱敏 + 文本清洗 + 金额分桶 | ~3s |
| 3 | 品类×季度聚合 + 健康度评分卡计算 | ~5s |
| 4 | 逐品类 Spark ML 对数线性回归 | ~10s |
| 5 | 卖家生态位分类 + 品类竞争格局摘要 | ~5s |
| 6 | 波士顿矩阵四象限 + 迁移轨迹 | ~5s |
| **总计** | | **~30-60s** |

---

## 数据脱敏说明

真实客户数据经过三层脱敏后写入数据库：

| 层级 | 原始字段 | 处理后 |
|------|---------|--------|
| L1 哈希 | `customer_id: 06b8999e...` | `SHA256(customer_id)` → 64位十六进制 |
| L1 哈希 | `seller_id`, `order_id`, `product_id` | 同上 |
| L2 聚合 | `customer_zip_code_prefix: 14409` | `144xx` (仅保留前3位) |
| L2 聚合 | `seller_city: "sao paulo"` | `sao_paulo` (标准化统一格式) |
| L3 清洗 | `review_comment_message` | 移除电话/邮箱/CPF等PII |

---

## 清空真实数据回退模拟数据

如果想回到模拟数据状态：

```bash
# 清空并重新填充模拟数据
mysql -u root -p < data/sql/02-seed-data.sql
```

## API 端点速查

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| POST | `/admin/etl/run` | 触发 ETL 管道 | ADMIN |
| GET | `/admin/etl/status` | 查询管道进度 | ADMIN |
| GET | `/admin/health` | 系统健康检查 | ADMIN |

---

## 常见问题

### Q: 运行 ETL 后 Dashboard 数据没变化？

确认 API 返回 `"success": true`，然后刷新浏览器页面（`Ctrl+Shift+R` 强制刷新）。

### Q: ETL 报错 "File not found: olist_orders_dataset.csv"

检查 `data/raw/` 目录下文件名是否与上面列出的**完全一致**（注意大小写和下划线）。

### Q: ETL 报错 "OutOfMemoryError: Java heap space"

本地模式下 Spark 内存不足。在 IntelliJ VM options 中添加 `-Xmx4g`。

### Q: 只想更新某一个分析结果（比如重新跑弹性模型）？

目前 ETL 是全部重跑的。可以单独调用对应的 Service 方法，联系开发者了解具体代码位置。

### Q: 生产环境如何部署 ETL？

推荐使用 `spark-submit` 提交到 YARN/K8s 集群，设置环境变量 `SPARK_MODE=cluster`，去掉 pom.xml 中 Spark 依赖的默认 scope。
