# 数据与模型接入指南

最后核对：2026-10-15。借鉴参考项目的数据接入手册组织方式；本项目当前没有 `DATA_PROVIDER` 插件机制，也没有只改一个变量就能接入任意招聘平台的能力。

## 1. 当前支持的数据来源

| 数据 | 当前接入方式 | 保存位置 |
|---|---|---|
| 简历正文 | 工作台粘贴文本、新 PDF 上传、已有简历版本导入 | PostgreSQL `resumes`、`resume_version` |
| 岗位要求 | 手工输入或调用岗位创建接口 | `job_descriptions`；运行时保存 JD 快照 |
| 分析、复核、计划、投递状态 | 页面和 REST 接口 | PostgreSQL 新业务表 |
| 经历追问回答与确认卡 | 报告中的追问入口；回答保存后生成草稿并确认 | `experience_inquiry`、`experience_evidence_card`；来源为用户补充，不覆盖原简历 |
| 原始 PDF、旧流程导出文件 | 已有对象存储接口 | MinIO API 兼容存储；本地使用 Silo |
| 知识文档 | 旧知识库页面或 API，保存后单独索引 | `knowledge_documents`、`vector_store` |
| 通用体验素材 | 从 `samples/resumes/`、`samples/jobs/` 读取正文后手工录入 | 原素材为文本文件，不自动写入业务库 |
| 评估数据 | 人工编写的虚构样例 | `samples/jobcompass-evaluation.json` |

数据库保存实际业务数据。评估样例不是数据库种子；运行模型评估不会把这些样例变成工作台里的岗位和简历。

## 2. 本地数据库与文件服务

使用仓库 Compose 默认配置时，在根 `.env` 中设置：

```properties
POSTGRES_URL=jdbc:postgresql://localhost:5432/resume_ai
POSTGRES_USER=resume_ai
POSTGRES_PASSWORD=resume_ai
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=resume-ai
```

这些是 Compose 中的本地开发默认凭据，真实环境使用实际凭据。已有数据卷初始化后，修改 Compose 密码并不会自动修改库内账号密码。

按README的开发方式，后端在宿主机的IDEA或Maven中运行，因此用 `localhost` 连接本机映射端口。数据库位于其他电脑时使用实际主机地址；后端若也进入Compose，容器内应连接服务名，例如 `postgres:5432`。浏览器端API地址必须从浏览器所在电脑可访问，不能直接使用Compose内部域名。

Flyway 创建表和 PostgreSQL 扩展。切换远程数据库需要网络可达、账号权限和实际扩展；不对已有业务库盲目启用 baseline 或删除表。

## 3. 聊天模型与向量模型分别配置

项目根目录的 `.env` 是私有配置入口，不随Git克隆。首次使用从 `.env.example` 创建并填写自己的配置；若文件已存在则编辑原文件。前后端源码、公开文档和示例配置只放变量名与占位符。

| 变量 | 用途 |
|---|---|
| `RESUME_AI_LLM_PROVIDER` | 业务展示的模型供应商标识；当前 DeepSeek 使用 `deepseek` |
| `SPRING_AI_CHAT_MODEL` | Spring AI 聊天实现选择；兼容接口使用 `openai` |
| `OPENAI_CHAT_BASE_URL`、`OPENAI_CHAT_API_KEY`、`OPENAI_CHAT_MODEL` | 聊天服务地址、Key、控制台实际可用的模型名 |
| `SPRING_AI_EMBEDDING_MODEL` | 向量实现选择；兼容接口使用 `openai` |
| `OPENAI_EMBEDDING_BASE_URL`、`OPENAI_EMBEDDING_API_KEY`、`OPENAI_EMBEDDING_MODEL` | 向量服务地址、Key、模型名 |
| `RAG_EMBEDDING_DIMENSIONS` | 当前数据库向量维度为 `1024`，需与返回结果一致 |
| `NEXT_PUBLIC_API_BASE_URL` | 前端可访问的后端地址，默认 `http://localhost:8080/api/v1` |

README提供DeepSeek聊天、阿里云百炼向量的配置示例。请按自己的服务商账号、地域和可用模型填写 `.env`，不要假设克隆后已有可用Key；历史评估使用的模型名不是未来必须固定使用的模型。

DeepSeek 聊天 Key 不能代替阿里云百炼向量 Key。阿里云百炼需要使用模型服务 API Key，不是阿里云 AccessKey ID/Secret。同名变量只保留一个有效配置；切换模型后重启后端。前端 `NEXT_PUBLIC_*` 不存任何密钥，生产构建改变 API 地址后需要重新构建。

新证据分析实现见 `SpringAiEvidenceModel.java`，当前针对 OpenAI 兼容聊天能力及 Tool Calling 编写。切换供应商时需验证工具调用和结构化输出；Ollama 属于上游保留选项，新业务没有逐一验证所有模型的兼容性。

新证据分析默认使用 COMPACT 工具快照，保留全部原文分段，移除重复字段。需要对照或恢复原结构时，在自己的 `.env` 设置 `JOBCOMPASS_EVIDENCE_SNAPSHOT_MODE=FULL` 并重启后端；可选值只用 `FULL / COMPACT`。不改变聊天 Key、知识库向量配置或原改写流程。默认值依据 2026-10-14 的 40 组单轮虚构对照，结果与限制见 [验收记录](验收记录.md)；换模型或真实长文本仍需另行验证。

向量维度发生变化时，需要评估数据库列与索引迁移、历史向量重建；只改环境变量不足以完成切换。

## 4. 通过现有接口导入自己的资料

完整字段见 [接口说明](api-design.md)。下面是可执行的 PowerShell 示例，执行后会创建真实业务记录；请先将虚构正文替换为自己的内容。

```powershell
$apiBase = 'http://localhost:8080/api/v1'
$resumeBody = @{ title = '演示简历'; text = '使用 Spring Boot 编写预约接口，并用唯一约束避免重复预约。' } | ConvertTo-Json
$resumeVersion = Invoke-RestMethod -Uri "$apiBase/resumes/text" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($resumeBody))

$jobBody = @{ title = 'Java 后端实习'; company = '演示公司'; text = '要求有 Spring Boot 接口开发经历。'; location = ''; sourceUrl = ''; deadline = $null } | ConvertTo-Json
$jobSnapshot = Invoke-RestMethod -Uri "$apiBase/compass/jobs" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($jobBody))
```

简历接口返回的是版本对象：`id` 为版本 ID，`resumeId` 才是简历父记录 ID。分析需要 `resumeVersionId = $resumeVersion.id`，不是父记录 ID。开始分析会调用模型并消耗额度，操作和进度查询见接口说明。

## 5. 以后接入外部招聘数据

状态：待开发。可以从 CSV 导入开始，需求明确后再决定是否增加统一 Provider 接口。

建议处理过程：外部资料 → 字段映射与正文清洗 → 预览确认 → 现有岗位创建服务 → 固定快照 → 分析。这样仍能复用当前版本、证据和计划流程。

| 接入约定 | 需要解决的问题 |
|---|---|
| 最小字段：公司、岗位名、完整 JD | 与 `SnapshotController.JobInput` 保持一致 |
| 可选字段：地点、来源链接、截止日期 | 保留来源，但不要把来源 URL 当成自动抓取结果 |
| 外部唯一标识、去重与更新规则 | 当前按公司、岗位名和正文哈希查重；外部 ID 映射尚未实现 |
| 空正文、编码、分页和部分失败 | 逐项反馈，不直接写入数据库跳过业务校验 |
| 更新后的分析 | 新资料形成新分析依据，旧报告保留原快照 |
| 新增工具 | 仍限定运行上下文，读取外部内容不能执行正文里的指令 |

每次新增数据来源，在本节补充其实际实现入口、配置、示例和验证记录。不要把接口设想写成已经上线的能力。
