# JobCompass 当前架构

最后核对：2026-10-27。本文件描述已实现结构，未来方案在 [实施计划](实施计划.md) 中标记。原架构文档中的规划内容已按当前源码更新；上游基础功能及 Git 历史保留。

## 1. 从使用流程看模块

```mermaid
flowchart TD
    U["用户：简历与目标岗位"] --> F["Next.js 工作台"]
    F --> S["输入与不可变版本 / JD 快照"]
    S --> E["后台分析：提取要求 → 核对经历"]
    E --> T["受限只读工具"]
    T --> L["Spring AI / 聊天模型"]
    L --> V["Java 结构与引用校验"]
    V --> R["报告 + 人工复核"]
    R --> B["Java 任务合并与预算安排"]
    B --> P["草稿编辑 → 事务确认"]
    P --> A["任务进度 / 手动投递记录"]
    S --> DB[("PostgreSQL")]
    R --> DB
    P --> DB
    A --> DB
```

新分析直接读取简历和岗位快照，不走知识库召回。上游知识库使用独立的 pgvector 检索流程，提供写作与岗位建议。

## 2. 目录与职责

| 位置 | 当前职责 |
|---|---|
| `backend/src/main/java/com/resumeai/compass` | 新输入、证据分析、复核、预算与计划跟踪 |
| 原 `interfaces / application / domain / infrastructure / agent` | 上游接口、实体、知识库、改写、文件与模型设施 |
| `backend/src/main/resources/db/migration` | Flyway；V1–V4 保留，V5–V9 为原三项改造，V10 新增追问与证据卡，V11 添加分析统计 JSONB，V12 添加证据评分快照 |
| `frontend/src/app/compass` | 输入、分析选择、预算和计划列表 |
| `frontend/src/app/evidence-analyses/[id]` | 状态查询、证据报告、复核与失败重试 |
| `backend/src/main/java/com/resumeai/compass/experience` | 针对要求追问、回答保存、证据卡与历史确认 |
| `frontend/src/app/evidence-analyses/[id]/experience/[inquiryId]` | 回答、草稿预览、确认、复制/下载及重新编辑 |
| `frontend/src/app/preparation-plans/[id]` | 草稿调整、确认、进度与事件 |
| `frontend/src/app/applications` | 手动投递状态和备注 |
| `frontend/src/lib/api/compass.ts` | 新前后端请求和类型约定 |
| `deploy / scripts / .run` | 基础服务编排、本机启动和 IDEA 配置 |
| `samples / docs` | 虚构评估输入、结果、使用与维护文档 |

新模块复用 Spring JDBC、已有事务管理器、文档解析和对象存储；没有为二改重写原 JPA 分层。原删除接口加入窄范围保护，避免删除已被新业务引用的资料。

## 3. 分析执行与证据边界

`SnapshotService` 保存正文、SHA-256 和分段，锁定父简历串行分配版本号。分析绑定固定版本，岗位快照写入 `analysis_run`；失败重试继续使用原资料。

`EvidenceService` 保存运行后将任务提交到 2 线程、8 等待名额的本机队列。队列满保留失败记录并返回 429。前端轮询报告；启动时将遗留运行标记为 `FAILED / INTERRUPTED`，没有持久化自动调度。

`SpringAiEvidenceModel` 先提取最多 20 个独立组、60 条原子 JD 子项，再以每批最多 5 项核对简历（旧版最多 20 条且整份核对）。提取阶段和每个核对批次各最多两次尝试，每次创建独立的 `JobCompassReadTools`，只能读当前 ID 范围且最多四次访问。批次分别校验后，完整合并再统一计分，不遗漏或重复要求。工具没有写数据库或投递能力。

`EvidenceValidator` 检查字段、状态、要求 ID、段落 ID 和逐字引用。缺少经历记录默认不确定；明确缺口规则偏保守，引用校验不保证理解完全正确。人工复核独立保存，原模型判断保留。提示词与校验配合约束正文指令，但不是完整安全认证体系。

`AnalysisMetrics` 每次分析单独创建，通过 CallAdvisor 在结构转换前取得 ChatClient 工具循环累计用量；在每阶段/批次的 finally 中记录耗时、工具读取和失败代码，成功/失败快照分别随运行结果保存到 V11 的 `analysis_run.metrics_json`。统计本身不额外调用模型或增加数据库表；新版分批核对会增加分析批次。不记录提示词、工具正文、模型正文或凭据。用量不完整时保留部分统计、总量置空；历史记录仍为空。若网络中断前服务商已产生用量但没有返回，客户端无法准确统计账单。

FULL 工具快照保持原结构；COMPACT 仅移除重复正文、哈希、时间等分析不需要的元数据，保留 ID 与全部原文分段。两种模式共用原提示词、范围校验、工具次数上限和证据校验。模型模式可以用 `jobcompass.evidence.snapshot-mode` 调整；启用决定由独立验收集对照结果记录，不能因 Token 变少就认定质量合格。统计仅覆盖新证据分析，未给原改写和追问增加统计。

## 4. 经历追问与证据卡

报告入口选择一项要求，`ExperienceService` 绑定原报告、要求和简历版本；每条要求仅有一个追问会话。`SpringAiExperienceModel` 用已固定资料生成三个问题，`ExperienceValidator` 校验数量、类别和字段。模型只生成问题，回答原文由 Java 组织为卡，完整保留否认、限制和不确定，避免生成摘要时反转原意。

追问使用独立 1 线程、4 等待名额；模型网络调用不占数据库事务，每次最多两次尝试。问题完成和回答变更带版本条件；队列拒绝及重启中断保存失败状态。确认在短事务中锁住会话，原子写入确认卡、状态与事件；重复确认幂等。

`experience_inquiry` 保存当前问题、回答和草稿；`experience_evidence_card` 保存不可变确认快照。重新编辑产生新草稿，旧确认卡保留。来源明确为用户补充/用户确认，不写入旧简历或模型报告，不自动设置复核为 MATCH。现有模型和计划模块保持原行为。

## 5. 计划与一致性

`BudgetPlanner` 使用受控技能映射；相同技能和行动类型合并，未知要求保持独立。每岗位取最高要求权重并跨岗位累加，用收益/预计小时排序，在预算内贪心选择，非全局最优。

`PlanningService` 将草稿、任务、来源与复核快照写入数据库。确认时用短事务、计划行锁、确认键和内容哈希，并通过岗位唯一约束创建或复用申请。进度和备注采用显式版本检查，不是新 JPA `@Version`。

模型调用在持久化结果的短事务之外。事件与对应状态变更在同一事务中保存；网络调用不占着确认事务等待。

## 6. 数据归属

| 表或存储 | 内容 |
|---|---|
| `resumes / job_descriptions` | 上游父记录及新增岗位信息 |
| `resume_version` | 不可变简历版本、正文、分段与哈希 |
| `analysis_run / analysis_requirement` | 快照、运行状态、要求和判断 |
| `requirement_review` | 人工复核、备注、技能修正、版本 |
| `preparation_plan / plan_analysis / plan_task / task_requirement` | 草稿、来源、预算任务及要求关联 |
| `job_application / activity_event` | 手动申请记录、变更历史 |
| `experience_inquiry / experience_evidence_card` | V10：追问会话与不可变用户确认卡 |
| `knowledge_documents / vector_store` | 上游知识文档与向量检索 |
| Silo / MinIO API | 原文件及原流程导出文件 |
| Redis | 上游保留基础能力；新分析进度和队列不存这里 |

新版本简历及已有分析/申请引用的岗位受删除保护。当前没有归档入口和完整删除管理流程。

## 7. 运行与扩展范围

### 2026-10-27：规则归一与证据评分快照

`TechnologyLexicon` 以受控词典识别完整技术名、别名和少量纠错，最长匹配避免一处 Spring Boot 同时贡献 Spring/boot。`KeywordMatcher` 按规范技能去重并保存原文 occurrence；未知候选、纠错及关联提示与正式命中分开。分析服务与改写工具共享规则，前端正式覆盖率读取后端结果。keyword-v2 的规则综合即字面覆盖，不再按简历长度加分；原 semantic/ATS 字段只作兼容别名，旧持久化报告仍保留原算法值。

新证据提取使用 evidence-v3：要求分类、语义组键、子项键、ALL/ANY 与重复原文出处。Java 按 JD 分段/分句和类别确定计分组，约束模型自由分组；合并相同文本/受控技术别名的重复项和相同子项集的重复组，保留来源。不同能力不能只因 skillKey 相同而合并。子项语义键由模型生成，语义重复识别仍可能出错，引用和结构校验不能替代语义评测。检测到跨组子项重复、组元数据冲突或引用跨句时要求模型纠正。

`EvidenceScoring` 对校验后的技术/经验要求确定性计算权重覆盖。ALL 组拆分保持总权重，ANY 组按最符合的分支判断；资格和职责单列。除模型 complete 声明外，额外检查非标题 JD 分段是否有出处引用；有遗漏则报告保留但评分不可用。该检查不证明同一分段的所有子要求均已提取。

V12 增加 `analysis_run.score_json`。模型调用仍在事务外；要求明细、报告、统计与评分在同一短事务提交。Snapshot 包含版本、权重贡献和 UTC 计算时间，GET 不重算；历史 NULL 和旧构造保持兼容。人工复核及经历卡的准备语义保持原样，不自动改原模型分。新旧评分分别显示，未合成混合总分。

任务建议的正数分钟估计由 Java 向上取至 30 分钟单位，原值与调整值保存在 report.taskAdjustments 并显示；零、负数及超界值仍拒绝。该处理只规范规划成本，不修补模型能力判断或原文引用。校验日志记录错误类别及内部校验原因，不记录提示词、简历正文或模型原文；引用纠错反馈会指出要求 ID，便于模型定位。

模型判断使用内部枚举 DTO 输出 Schema，再转换为兼容的字符串状态 DTO。`LiteralReferenceResolver` 只解析绑定版本中唯一的字面序列，允许排版空白/换行及段落编号差异，英文/数字词边界保持不变。跨连续原文段落时拆成逐字引用；不允许改词、跳过正文或选择歧义出处。reported/resolved 随 report.referenceAdjustments 保存并展示。最终引用仍由原文子串校验，语义是否支持要求仍需复核。

Compose 只运行 PostgreSQL + pgvector、Redis 和 Silo；后端在 IDEA/Maven/Java 运行，前端在 Node 运行。本机默认端口 5432、6379、9000/9001、8080、3000。

当前为单用户、单实例范围，没有 SSE 新进度流、动态 Provider、认证、自动投递或云端发布。需要这些能力时从 [实施计划](实施计划.md) 新增任务，并同步接口、手册和验收。

原知识检索遇到异常或没有召回时会尝试文本检索；索引依然需要向量模型。知识建议与个人能力证据不能混用。

原改写复用 `rewrite_drafts.conversation_history` 保存对话与候选快照；生成时只记录候选，不覆盖已应用正文。接受最新候选时，正文、理由、校验信息及候选处理状态在同一事务中保存，导出读取已持久化草稿。2026-10-14 的局部修复没有新增数据库表、迁移或前端请求字段。原改写的模型调用仍在其既有事务方法内，未改成新证据模块的任务队列结构。
