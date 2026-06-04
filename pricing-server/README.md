# Pricing Server — Spring Boot REST API 服务层

> 电商智能定价决策引擎的后端 API 服务，为可视化大屏提供数据接口。

## 模块职责

```
前端 ECharts ← REST API → Spring Boot ← MyBatis → MySQL
                                  ↓
                          JWT 认证 + RBAC 授权 + 审计日志
```

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.x | 认证授权框架 |
| jjwt | 0.12.5 | JWT Token 签发/验证 |
| MyBatis | 3.0.3 | 数据库访问层 |
| MySQL Connector | 8.x | JDBC 驱动 |
| Guava | 33.1.0 | RateLimiter 限流 |
| Lombok | 1.18.x | 代码简化 |

## 项目结构

```
pricing-server/
├── pom.xml
├── src/main/resources/
│   └── application.yml                     # 主配置
└── src/main/java/com/pricing/server/
    ├── PricingServerApplication.java       # 入口
    ├── common/
    │   ├── Result.java                     # 统一 API 响应
    │   └── PageResult.java                 # 分页响应
    ├── model/
    │   ├── entity/                         # 6个实体类
    │   │   ├── Elasticity.java
    │   │   ├── CompetitorNiche.java
    │   │   ├── CategoryLandscape.java
    │   │   ├── BostonMatrix.java
    │   │   ├── CategoryHealth.java
    │   │   └── SysUser.java
    │   └── dto/                            # 4个 DTO
    │       ├── LoginRequest.java
    │       ├── LoginResponse.java
    │       ├── WhatIfRequest.java
    │       └── WhatIfResponse.java
    ├── security/                           # 安全模块
    │   ├── JwtTokenProvider.java           # JWT 签发/验证
    │   ├── JwtAuthenticationFilter.java    # 请求拦截
    │   ├── JwtUserDetails.java             # 用户信息容器
    │   ├── UserDetailsServiceImpl.java     # 用户加载
    │   └── SecurityConfig.java             # Spring Security 配置
    ├── repository/                         # 6个 Mapper
    │   ├── UserMapper.java
    │   ├── ElasticityMapper.java
    │   ├── CompetitorMapper.java
    │   ├── BostonMatrixMapper.java
    │   ├── CategoryHealthMapper.java
    │   └── AuditLogMapper.java
    ├── service/                            # 5个 Service
    │   ├── AuthService.java
    │   ├── ElasticityService.java  ⭐ What-If 模拟
    │   ├── CompetitorService.java
    │   ├── BostonMatrixService.java
    │   └── CategoryHealthService.java
    ├── controller/                         # 5个 Controller
    │   ├── AuthController.java
    │   ├── ElasticityController.java ⭐ 含 What-If API
    │   ├── CompetitorController.java
    │   ├── BostonMatrixController.java
    │   └── HealthScoreController.java
    ├── aop/
    │   └── AuditLogAspect.java            # 审计日志切面
    └── config/
        ├── RateLimiterConfig.java         # Guava 限流
        ├── CorsConfig.java               # CORS 跨域
        ├── XssFilter.java                # XSS 防护
        └── GlobalExceptionHandler.java   # 全局异常处理
```

## API 端点

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/v1/auth/login` | 用户登录 → 获取 JWT | PUBLIC |
| GET | `/api/v1/auth/verify` | Token 有效性验证 | 认证即可 |
| GET | `/api/v1/dashboard/elasticity/all` | 所有品类弹性系数 | AUTH |
| GET | `/api/v1/dashboard/elasticity/high-elasticity` | 高弹性品类 | AUTH |
| GET | `/api/v1/dashboard/elasticity/{category}` | 指定品类弹性 | AUTH |
| POST | `/api/v1/dashboard/elasticity/simulate` | ⭐ What-If 模拟 | AUTH |
| GET | `/api/v1/dashboard/elasticity/simulate/batch` | 批量模拟（曲线） | AUTH |
| GET | `/api/v1/dashboard/competitor/niche/category` | 品类卖家生态位 | AUTH |
| GET | `/api/v1/dashboard/competitor/niche/distribution` | 生态位分布 | AUTH |
| GET | `/api/v1/dashboard/competitor/landscape/all` | 竞争格局列表 | AUTH |
| GET | `/api/v1/dashboard/boston-matrix/snapshot` | 波士顿矩阵快照 | AUTH |
| GET | `/api/v1/dashboard/boston-matrix/full` | 完整矩阵数据 | AUTH |
| GET | `/api/v1/dashboard/health/all` | 健康度评分列表 | AUTH |
| GET | `/api/v1/dashboard/health/report` | 健康度综合报告 | AUTH |

## 安全设计亮点

| 层次 | 措施 | 实现 |
|------|------|------|
| 认证 | JWT 无状态 Token | HMAC-SHA256 签名，30分钟过期 |
| 授权 | RBAC 双角色 | ADMIN（全量）/ ANALYST（聚合） |
| 限流 | 三层令牌桶 | 登录5次/分 + IP限流 + 全局100次/分 |
| 注入防护 | XSS Filter + PreparedStatement | 参数清洗 + MyBatis 参数化 |
| 审计 | AOP 全链路记录 | 谁→何时→调了什么→耗时→结果 |
| 传输 | HTTPS + CSP + X-Frame | Spring Security Headers 配置 |

## 运行方式

```bash
cd pricing-server
mvn spring-boot:run

# 或者
mvn clean package -DskipTests
java -jar target/pricing-server-1.0.0.jar
```

## 默认用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN（全量数据） |
| analyst | analyst123 | ANALYST（聚合数据） |

> ⚠ 生产环境务必修改默认密码！
