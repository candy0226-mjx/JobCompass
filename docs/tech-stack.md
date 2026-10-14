# JobCompass 技术栈与用途

最后核对：2026-10-13。版本以 `backend/pom.xml`、`frontend/package-lock.json` 和部署文件为准；这里记录用途，不固定未来必须使用某个供应商或版本。

## 当前技术

| 技术 | 当前版本或入口 | 用途 |
|---|---|---|
| Java / Spring Boot | Java 21、Boot 4.1.0 | 业务接口、依赖注入、事务与健康检查 |
| Spring AI | 2.0.0 | ChatClient、Tool Calling、结构化输出及上游 VectorStore |
| DeepSeek / 百炼 | 根私有配置选择 | 当前方案分别承担聊天与向量化；切换前验证能力 |
| Spring Data JPA | Boot 管理依赖 | 上游实体和业务持久化 |
| JdbcTemplate / TransactionTemplate | Spring 既有依赖 | 新快照、报告、计划、事件和显式版本检查 |
| PostgreSQL / pgvector | 部署镜像 `pgvector/pgvector:pg17` | 关系数据与上游向量检索 |
| Flyway | V1–V10 | 表、扩展与增量结构管理；V10 为追问和证据卡 |
| Redis | `redis:8-alpine` | 上游保留基础能力；新分析不是 Redis 队列 |
| Silo + MinIO Java 客户端 | 本地覆盖文件选择 Silo；客户端 8.6.0 | 兼容 MinIO API 的文件上传、删除和导出存储 |
| Tika / PDFBox / docx4j | 3.2.3 / 3.0.6 / 11.5.6 | 上游文档解析与导出；新 PDF 输入复用解析/边界检查 |
| Next.js / React / TypeScript | 16 / 19 / 5，具体由锁文件确定 | 页面路由、表单、状态查询和类型约定 |
| Tailwind CSS | 4 | 页面样式 |
| Maven / npm / Docker Compose | 已有工具链 | 构建、依赖和基础服务启动 |
| JUnit / Spring Boot Test | 依赖树管理 | 普通规则测试与显式 PostgreSQL/真实模型测试 |

## 技术解决的问题

- 能力判断：受限工具读取固定资料，模型理解后由 Java 校验结构与引用。
- 多岗位准备：Java 的技能映射、任务合并和预算规则负责可解释取舍。
- 数据一致性：数据库事务、行锁、唯一约束、幂等键和版本条件更新。
- 等待体验：本机有界线程池执行模型任务，前端轮询状态，进度保存在 PostgreSQL。
- 资料追溯：不可变版本与运行快照、独立复核、来源关联与变更事件。
- 经历追问：Spring AI 提出针对性问题，Java 按完整回答形成来源明确的证据卡；确认与历史快照通过短事务保存。

## 需要准确区分的能力

当前没有新业务 SSE、消息队列、Redisson、ReBloom 去重、多用户鉴权、全应用 Docker 部署或动态 Data Provider。Redis 安装并不等于实现了缓存治理/分布式锁；依赖存在不等于所有能力已验证。

新证据流程没有知识库召回；pgvector 主要服务原知识库。旧检索异常或没有结果时尝试文本查询；索引仍需要向量模型。预算采用贪心规则，不能写成全局最优算法。

模型名是可改的私有配置，历史样例模型见评估 JSON。更新向量维度需连同表结构和已有索引考虑，不只修改模型名。

## 演进方式

接口与框架变化后更新本表；新依赖先说明它解决的具体问题，再将实施与验证写入 [实施计划](实施计划.md) 和 [验收记录](验收记录.md)。当前源码无需为了文档结构额外引入框架。

完整运行与配置参见 [使用手册](USER_GUIDE.md)、[数据接入](DATA_PROVIDER.md)。
