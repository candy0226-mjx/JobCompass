# 求职罗盘 JobCompass

> 项目仓库：[candy0226-mjx/JobCompass](https://github.com/candy0226-mjx/JobCompass)。二次开发基线：[Mr12138666/Resume-Ai-Agent](https://github.com/Mr12138666/Resume-Ai-Agent)。保留原 Git 历史与 MIT `LICENSE`。JobCompass 新增证据核对、多岗位预算计划、确认跟踪与经历追问；使用、架构与开发资料均从仓库内的 `docs/` 阅读，不依赖维护者的本地目录或仓库外蓝图。

面向求职者的岗位分析与准备规划系统：固定一版简历，核对最多三个目标岗位的真实要求和经历证据，再汇总共同任务，在每周时间预算内确认并追踪准备计划。

## 学习与维护入口

第一次启动看[用户使用手册](docs/USER_GUIDE.md)，继续二改看[实施计划](docs/实施计划.md)，面试准备看[面试问答](docs/面试问答.md)。全部资料在[文档索引](docs/README.md)，按功能变化直接更新原文件，可随时补充和调整。

[贡献指南](CONTRIBUTING.md)与[AI 协作说明](AGENTS.md)约定了文档同步方式、验证命令和资料边界。历史验收与当前运行状态分别查看，不把待开发方案当成已实现能力。

## 核心能力

- **证据核对**：不可变简历版本与 JD 快照，Spring AI 只读工具读取受限资料，要求提取与逐条核对分两阶段；Java 校验要求 ID、引用 ID 与原文。缺少记录默认不确定，模型失败保留运行状态，支持有限修正和手动重试。
- **多岗位准备计划**：用户独立保存复核意见，校正技能映射；同技能、同类型任务合并并保留全部来源。依据岗位重要程度、必备/加分要求和用户确认的耗时，生成可解释的预算安排；待安排任务可见。
- **确认与跟踪**：草稿编辑与确认、幂等键、短数据库事务、显式版本校验；更新任务与申请状态并保存事件，重复确认不重复创建申请，已有申请不被重置。
- **经历追问**：针对岗位要求提出三个问题，用户补充真实场景、实际职责和验证情况；按完整回答生成并确认来源清晰的证据卡，可复制、下载与重新编辑，历史确认卡保留。

## 上游保留能力

- 简历上传与解析：支持 PDF、DOCX、TXT，原文件写入 MinIO，正文由 Apache Tika 提取并保存到 PostgreSQL。
- 岗位描述管理：创建目标岗位，调用大模型结构化岗位职责、技能要求和加分项。
- 匹配分析报告：输出整体分、关键词分、语义分、ATS 分、缺失关键词、证据映射和优化建议。
- RAG 知识库：维护简历写作规则、岗位指南等知识文档，分块后写入 PGvector，并在分析阶段检索引用。
- 智能体改写：基于 Spring AI Tool Calling 调用简历解析、岗位解析、检索、评分和改写工具，生成可解释的优化段落。
- 事实校验与对比：展示原文和改写后的差异，高亮增删内容，提醒需要人工复核的事实。
- 导出文件：支持将改写结果导出为 Markdown 或 PDF，并通过 MinIO 临时下载链接获取。
- 记录管理：简历、岗位、分析报告、改写草稿和知识文档都支持列表查看和删除。

## 技术栈

- 后端：Java 21、Spring Boot 4.1、Spring AI 2.0、Spring Data JPA、Flyway
- AI：OpenAI 兼容接口、DeepSeek 聊天模型、阿里云百炼（DashScope）向量模型、Ollama 本地模型可选
- 数据：PostgreSQL 17、PGvector、Redis 8、Silo（兼容 MinIO API）
- 文档解析：Apache Tika、PDFBox、docx4j
- 前端：Next.js 16、React 19、TypeScript、Tailwind CSS

新增业务位于 `backend/src/main/java/com/resumeai/compass`，使用已有 Spring JDBC 与事务能力实现增量业务。版本修改通过 `WHERE version = expectedVersion` 检查，确认使用短行锁和唯一约束处理竞态；原 JPA 模块继续保留。既有迁移保留，增量迁移为 V5–V10，V10 仅增加追问与证据卡表。

## 目录结构

```text
JobCompass/
  backend/   后端 Spring Boot 服务
  frontend/  前端 Next.js 应用
  deploy/    本地基础设施和远程环境模板
  docs/      使用、架构、接口、接入、计划、面试、验收与排查
  samples/   可选的本地体验输入素材
  scripts/   前后端启动脚本
```

`参考/` 目录只用于本地参考，已被 `.gitignore` 忽略，不会进入公开仓库。

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20.9+，满足当前 Next.js 16 依赖要求
- PostgreSQL + PGvector
- MinIO
- Redis 为上游保留基础组件；新分析采用本机线程池，进度保存在 PostgreSQL
- 一个可用的大模型 API Key，以及一个 1024 维向量模型

## 配置方式

项目默认通过 Spring Boot `application.yml` 读取配置，并会额外导入根目录 `.env` 与 `backend/src/main/resources/application-private.yml`。真实密钥不要提交到 Git。

首次配置时按需复制示例；已有 `.env` 或私有配置时直接编辑，避免覆盖已填的 Key：

```powershell
if (-not (Test-Path -LiteralPath '.env')) {
    Copy-Item .env.example .env
}
if (-not (Test-Path -LiteralPath 'backend/src/main/resources/application-private.yml')) {
    Copy-Item backend/src/main/resources/application-private.example.yml backend/src/main/resources/application-private.yml
}
```

然后按自己的数据库、MinIO、Redis 和模型服务填写 `.env` 或 `application-private.yml`。公开仓库中的示例文件只保留占位符。

常用关键配置：

```env
POSTGRES_URL=jdbc:postgresql://localhost:5432/resume_ai
POSTGRES_USER=resume_ai
POSTGRES_PASSWORD=resume_ai

MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=resume-ai

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

```

### DeepSeek 聊天 + 阿里云百炼向量

两家服务使用不同的 API Key 和地址，按以下步骤配置：

1. 打开 [**阿里云百炼控制台**](https://bailian.console.aliyun.com/)，进入 [API Key 管理页面](https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key)。
2. 选择 **华北 2（北京）** 地域，创建自己的阿里云百炼 API Key。
3. 在项目根目录的本地 `.env` 中替换已有的模型选择，并填写以下配置：

```env
RESUME_AI_LLM_PROVIDER=deepseek
SPRING_AI_CHAT_MODEL=openai
SPRING_AI_EMBEDDING_MODEL=openai
OPENAI_CHAT_API_KEY=<你的 DeepSeek Key>
OPENAI_CHAT_BASE_URL=https://api.deepseek.com
OPENAI_CHAT_MODEL=deepseek-flash
OPENAI_EMBEDDING_API_KEY=<你的阿里云百炼 Key>
OPENAI_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
OPENAI_EMBEDDING_MODEL=text-embedding-v4
RAG_EMBEDDING_DIMENSIONS=1024
```

同名变量在 `.env` 中只保留一行；不要让原来的 `SPRING_AI_CHAT_MODEL=ollama`、`SPRING_AI_EMBEDDING_MODEL=ollama` 或示例模型名与新配置并存。上面的阿里云百炼地址适用于华北 2（北京）；其他地域需使用控制台给出的对应地址。项目的密钥只保存在被 Git 忽略的本地 `.env` 中，不要把密钥发到聊天或提交到仓库。Docker Compose 只启动 PostgreSQL、Redis 和对象存储；使用云端模型时无需启动 Ollama。

`OPENAI_EMBEDDING_API_KEY` 必须填写阿里云百炼控制台“API Key”页面创建的模型服务密钥，不能填写阿里云 AccessKey ID（通常以 `LTAI` 开头）或 AccessKey Secret；后者会使向量接口返回 401。

如果使用远程数据库且库里已有对象，可以开启 Flyway baseline：

```env
FLYWAY_BASELINE_ON_MIGRATE=true
FLYWAY_BASELINE_VERSION=0
```

数据库表和 PGvector 扩展由 Flyway 自动初始化，正常情况下不需要手工运行 `V1__init_core_schema.sql`。

## 启动项目

### 获取项目到本地

先选择自己的本地工作目录，在该目录打开终端并执行：

```shell
git clone https://github.com/candy0226-mjx/JobCompass.git
cd JobCompass
```

也可以克隆自己的Fork，或在克隆命令末尾指定其他目录名。后文的“项目根目录”指包含 `backend/`、`frontend/` 和本README的克隆目录，与盘符、用户名和文件夹名称无关。首次运行先按上面的配置说明，从 `.env.example` 创建并填写自己的 `.env`；克隆不会带来维护者的密钥、数据库或演示记录。

### 在 IDEA 中运行 Java 后端

1. 将项目拉取到自己的本地文件夹，用 IDEA 打开该项目根目录，把 `backend/pom.xml` 添加为 Maven 工程，选择 JDK 21。
2. 在项目根目录运行下面的 Docker Compose 命令，启动数据库、Redis 和文件存储。业务代码直接在本机运行。
3. 运行仓库内的 `JobCompass Backend` 配置；它使用 `$PROJECT_DIR$/backend` 作为工作目录。若 IDEA 的模块名不同，选择 `backend/pom.xml` 对应的实际模块。也可直接运行 `com.resumeai.ResumeAiAgentApplication`，工作目录设置为自己克隆目录下的 `backend/`。
4. 工作目录为 `backend/` 时，Spring Boot 读取项目根目录的 `../.env`。请先创建并填写自己的配置，不需要把 Key 写进 IDEA 配置。
5. 在另一个终端运行前端启动脚本，打开 `http://localhost:3000/compass`。

本机已有后端或前端进程运行时，先停止对应进程，再切换到 IDEA 或另一个启动方式，避免重复占用 8080/3000 端口。

### 使用命令行启动

在项目根目录启动本地基础服务（PostgreSQL、Redis、兼容 MinIO API 的 Silo）：

```powershell
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.local.yml up -d postgres redis minio
```

后端：

```powershell
.\scripts\start-backend.ps1
```

前端：

```powershell
.\scripts\start-frontend.ps1
```

首次缺少 `frontend/node_modules` 时，前端脚本会自动安装依赖；已有依赖时复用，不重复安装。Docker 命令找不到或引擎未启动时，按[故障排查](docs/故障排查.md)检查。

上面的 `.ps1` 命令用于Windows PowerShell。后端脚本基于脚本所在目录定位项目，并依次查找PATH、`MAVEN_HOME`/`M2_HOME`及当前用户的Maven 3.9缓存；不需要特定用户或固定Maven补丁版本。其他系统，或不使用脚本时，可在项目根目录的一个终端运行 `mvn -f backend/pom.xml spring-boot:run`；在另一个终端先运行 `npm --prefix frontend install`，再运行 `npm --prefix frontend run dev`。JDK、Maven、Node与Docker须由使用者安装并配置好。

浏览器打开：

```text
http://localhost:3000
```

后端默认地址：

```text
http://localhost:8080/api/v1
```

## 使用流程

求职罗盘主流程：

1. 打开 `/compass`，创建文本简历、上传 PDF，或选择已有简历并固定版本。新 PDF 入口限制 5MB、10 页，扫描版要求粘贴正文。
2. 录入公司、职位和 JD，选择最多三个岗位，开始分析。成功报告展示逐条判断与原文引用。
3. 在报告页复核真实经历，选择无需准备、完善表述、学习实践或核实情况；未知技能默认不合并，可校正为支持的技能键。
   如经历没写清楚，可点击“追问并整理经历”，保存回答、核对证据卡并确认。补充来源与原简历区分，不自动改变原判断。
4. 返回工作台，选择同版本、不同岗位的成功报告，设置本周预算与岗位重要程度，生成草稿。
5. 修改任务产物、耗时和选中状态，保存后确认。确认后内容冻结，更新准备任务进度及投递状态；完成任务不会自动改变旧能力判断。

上游简历优化流程仍可使用：

1. 打开“简历定制”，上传原始简历。
2. 粘贴目标岗位 JD，创建岗位记录。
3. 点击生成匹配分析，查看分数、关键词覆盖、证据映射和建议。
4. 如需 RAG 增强，先到“知识库”新增或编辑规则文档，并点击索引。
5. 在分析报告页创建改写草稿。
6. 在改写页查看左右对比、事实校验和改写理由。
7. 导出 Markdown 或 PDF。

`samples/resumes/`、`samples/jobs/` 提供多种体验正文，`samples/knowledge/` 提供知识库规则，按页面录入即可使用；评估 JSON 单独用于模型验证，不自动导入业务库。

## 知识库索引说明

- 文档保存后不会自动进入向量库，需要点击“索引”。
- 重新索引同一文档会先清理旧分块，再写入新的分块和向量，因此适合修改内容后的更新。
- 删除知识文档时，会同时清理业务记录和对应向量分块。
- 如果向量模型不可用，检索会退化为文本搜索；控制台会出现相应 warning。

## 常用命令

新增业务自检（数据库测试使用自动创建、清理的独立 schema）：

```powershell
cd backend
mvn test
mvn -Dtest=CompassPostgresIT test
```

需要核对真实模型表现时显式运行以下命令，它会发送 `samples/jobcompass-evaluation.json` 中的十五条虚构样例，消耗模型额度，并更新 `docs/jobcompass-evaluation-results.json`：

```powershell
mvn -Dtest=CompassLiveModelIT test
```

2026-10-13 本机验收：13 项常规测试与 9 项真实 PostgreSQL 测试通过；15 条真实 DeepSeek 脱敏样例通过引用校验，15 条与人工标签一致、没有负例被判成 MATCH，样例平均约 6.28 秒。小样本结果不代表普遍准确率，真实判断仍须人工复核。三岗位 → 复核 → 8h 草稿 → 调整 → 确认 → 任务与投递更新已在浏览器验证；前端生产构建通过。

已知范围：本机单用户；预算采用可解释贪心规则，非全局最优；复杂条件宁可保留 UNCERTAIN。模型服务中断或格式错误会保留失败运行；重启遗留运行标记 INTERRUPTED，不自动重放。原语义分和 ATS 公式仅保留在旧接口，新证据页不把它们作为能力判断。

实际页面参见[准备计划验收截图](docs/jobcompass-acceptance.png)，模型评估原始统计见[评估结果](docs/jobcompass-evaluation-results.json)。

后端测试：

```powershell
mvn -f backend\pom.xml test
```

前端类型检查：

```powershell
cd frontend
npm run typecheck
```

敏感信息自查：

```powershell
git grep -n -I -E "<your-secret-pattern>|<your-server-address>" -- .
```

## 公开仓库注意事项

- 不要提交 `.env`、`application-private.yml`、MinIO/Redis/PostgreSQL 密码或模型 API Key。
- 不要提交 `frontend/node_modules/`、`backend/target/`、`.next/` 等构建产物。
- `参考/` 目录已忽略，只保留本项目源码、文档、脚本和必要示例素材。

## 项目文档（可持续更新）

- [全部文档与维护方式](docs/README.md)
- [用户使用手册](docs/USER_GUIDE.md)
- [数据与模型接入](docs/DATA_PROVIDER.md)
- [架构说明](docs/architecture.md)
- [技术选型](docs/tech-stack.md)
- [接口设计](docs/api-design.md)
- [路线图](docs/roadmap.md)
- [实施计划](docs/实施计划.md)
- [验收记录](docs/验收记录.md)
- [故障排查](docs/故障排查.md)
- [面试问答](docs/面试问答.md)
- [开发与协作指南](CONTRIBUTING.md)
