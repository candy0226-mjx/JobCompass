# 后端服务

这里是 JobCompass 的 Spring Boot 后端。新增求职罗盘业务包括证据分析、多岗位预算计划与投递跟踪，同时保留原上传、评分、知识库、改写和导出能力。

完整启动看[使用手册](../docs/USER_GUIDE.md)，接口与字段看[接口说明](../docs/api-design.md)，测试前提看[贡献指南](../CONTRIBUTING.md)。下文命令从仓库根目录执行。

## 主要模块

- `interfaces`：REST Controller、异常处理、跨域配置。
- `application`：简历、岗位、分析、知识库、改写和设置等用例编排。
- `domain`：核心业务实体和状态枚举。
- `infrastructure`：JPA、PGvector、MinIO、Redis、文档解析和大模型适配。
- `agent`：Spring AI 工具调用相关能力。
- `compass`：不可变输入版本、受限工具、证据与复核、预算计划、事务确认和跟踪。

## 本地启动

先准备 `.env` 或 `backend/src/main/resources/application-private.yml`，再运行：

```powershell
.\scripts\start-backend.ps1
```

后端默认监听 `http://localhost:8080`，接口前缀为 `/api/v1`。

## 数据库初始化

Flyway 会在启动时自动执行 `src/main/resources/db/migration` 下的迁移脚本。首次连接空库时不需要手动执行 SQL；连接已有对象的数据库时，先核对结构与迁移历史，只有确认接管方式后才考虑以下配置，不将 baseline 当作通用修复：

```env
FLYWAY_BASELINE_ON_MIGRATE=true
FLYWAY_BASELINE_VERSION=0
```

## 测试

```powershell
mvn -f backend\pom.xml test
```
