# JobCompass 接口说明

最后核对：2026-10-27。以控制器、请求 record 和前端类型为准；字段变化时修改本文件。

## 1. 访问约定

本机基础地址 `http://localhost:8080/api/v1`，以下路径均相对于它。新进度通过轮询查询，不存在新模块的 SSE 接口。当前没有登录鉴权或多用户隔离。

新接口的 UUID 参数使用字符串，时间使用 ISO 日期/时间格式。创建资料/草稿返回 201，提交分析/重试返回 202；202 表示已受理，不表示分析成功。

## 2. 输入与版本接口

| 方法 | 路径 | 请求与结果 |
|---|---|---|
| POST | `/resumes/text` | `title, text`；返回简历版本对象 |
| POST | `/resumes/{id}/versions` | `title, text` record 中实际只使用 `text`；id 为简历父 ID |
| POST | `/resumes/{id}/versions/import` | 固定已有正文；最新版本相同正文时复用 |
| GET | `/resumes/{id}/versions` | 返回该简历版本列表 |
| POST | `/compass/resumes/upload` | multipart：`file, title`；新 PDF 专用入口 |
| POST | `/compass/jobs` | `title, company, text, location, sourceUrl, deadline`；后三项可选 |

版本返回字段：`id, resumeId, versionNo, text, hash, segments, createdAt`。其中 `id` 是分析所需的版本 ID，不是父简历 ID。分段包含 `id, text`。

岗位返回字段：`id, title, company, text, hash, location, sourceUrl, deadline, segments`。来源链接只保存，不自动抓取。正文上限 20,000 字符；新 PDF 上限 5MB、10 页、需可提取正文且不能加密。

## 3. 证据分析与复核

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/evidence-analyses` | `resumeVersionId, jobId`，返回运行对象 |
| GET | `/evidence-analyses` | 最近最多 100 条运行 |
| GET | `/evidence-analyses/{id}` | 查询状态/报告/复核；id 为运行 ID |
| POST | `/evidence-analyses/{id}/retry` | 仅失败运行可重试，保留原资料，新建运行 |
| PUT | `/evidence-analyses/{id}/reviews` | 批量保存本报告的复核意见 |

运行对象包含 `id, status, resumeVersionId, job, report, reviews, errorCode, durationMs, toolCalls, createdAt, metrics`。状态为 `PENDING / RUNNING / SUCCEEDED / FAILED`；只有成功时使用报告，不将 202 响应当完成结果。后台失败通过运行状态/错误码查看。

`metrics` 可空：历史数据、排队中或在模型调用前失败时没有统计。新模型调用成功及失败均保存已获取的数值。字段为 `snapshotMode, attempts, retryCount, toolCalls, validationFailures, modelFailures, inputTokens, outputTokens, reportedInputTokens, reportedOutputTokens, usageComplete, durationMs`。每项 attempt 包含 `stage`（EXTRACT/ASSESS）、`number`（该阶段第几次）、`durationMs, toolCalls, failureCode, inputTokens, outputTokens`。

尝试数不是 HTTP 调用数；Token 按工具循环累计响应用量，再合计阶段与重试。若任一尝试的用量未返回，`usageComplete=false`，总 `inputTokens/outputTokens=null`，`reported*` 只表示已获取的部分。`metrics.durationMs` 是模型分析范围，外层 `durationMs` 包含服务处理，两者无需完全相等。统计不保存正文/Key，不等于费用明细，也不包含其他模块调用。新版证据核对每批最多 5 项，stage 使用 `ASSESS_BATCH_1` 等名称；每批尝试 number 从 1 开始，每批最多两次，Token 与重试跨所有批次累计，旧 ASSESS 统计仍可读。

`report.requirements` 包含要求 ID、文本、`REQUIRED/PREFERRED`、技能键和岗位引用；`report.assessments` 包含 `requirementId, status, resumeEvidence, reason, actionType, taskTitle, expectedOutput, effortMinutes`。

判断为 `MATCH / GAP / UNCERTAIN`；原模型行动类型为 `NONE / EVIDENCE_FIX / LEARN / CLARIFY`。引用对象为 `segmentId, quote`，必须匹配对应原文。

复核请求示例，先替换占位 ID；首次保存 `expectedVersion = 0`，之后使用返回的 `version`：

```json
{
  "reviews": [
    {
      "requirementId": "<要求ID>",
      "category": "EVIDENCE_FIX",
      "note": "确实做过该项目，需要补充真实场景和验证过程。",
      "expectedVersion": 0,
      "skillKey": "springboot.api"
    }
  ]
}
```

人工类别还支持 `LEARN / CLARIFY / CONFIRMED_MATCH`。技能键可空；非空必须是当前支持的映射键，未知要求默认独立。人工复核不覆盖模型报告。请求的 `expectedVersion` 与返回的 `version` 名称不同。

## 4. 准备计划与投递

### 2026-10-27：新版证据评分与历史兼容

运行对象增加可空 `score`，仅新 `evidence-v3` 报告在成功保存时生成；历史报告、排队和失败运行保持 null，不做隐式重算。`score` 与原报告在同一事务保存，刷新及人工复核不重新计算。

字段为 `algorithmVersion=evidence-coverage-v1, weightVersion=required3-preferred1-v1, source=MODEL, available, coverageScore, uncertainPercent, gapPercent, totalWeight, groups, excludedGroups, warnings, calculatedAt`。时间使用 UTC。available=false 时数值为 null，不能解释为零分；例如未完整提取或无可计分项。

`groups` 每组包含 `groupKey, kind, category, logic, status, weight, matchedWeight, uncertainWeight, gapWeight, members`。成员包含 `requirementId, criterionKey, text, status, weightShare`；ALL 平分组权重，ANY 的成员 weightShare=null、按整个组判断。SKILL/EXPERIENCE 参与数字，DUTY/QUALIFICATION 在 excludedGroups 单列。权重 REQUIRED=3、PREFERRED=1；覆盖/待确认/缺口比例分别为对应权重除总权重，保留两位小数，显示值可能有尾差。

新版 `report` 增加 `schemaVersion, extractionComplete, warnings, taskAdjustments, referenceAdjustments`；每条要求增加 `groupKey, criterionKey, category, logic, additionalJdEvidence`。原子子项最多 60 条、去重后要求组最多 20 组。原来的单条 jdEvidence 仍在，其他重复出处放入 additionalJdEvidence。旧字段和历史 JSON 可读。已有 reviews 是准备行动，不转换为能力等级或复核分数。

referenceAdjustments 每项为 `requirementId, reported, resolved`，记录模型原引用及采用的原文引用列表。仅当忽略排版空白/换行后存在唯一字面来源，且英文/数字词边界不变时允许机械对齐；最终每条 resolved.quote 必须逐字存在于对应段落。不能替换文字、跳过正文、猜测重复出处或用语义相似度补引用，失败仍重试/拒绝。跨原文连续分段的引用拆为多条，最多五条。

模型任务耗时允许给出 1–960 分钟正数估计，Java 向上规范到 30 分钟倍数，保存到 assessment.effortMinutes；发生调整时在 taskAdjustments 保留 `requirementId, reportedMinutes, allocatedMinutes`。零、负数和超过 960 仍校验失败。调整只影响规划建议，不改变 status、引用或证据覆盖分；页面可见原始估计。准备计划接口的 30 分钟单位约束保持原样。

原 `/analyses` 报告 JSON 增加可空 `keywordEvaluation`：`algorithmVersion=keyword-v2, dictionaryVersion, available, coverageScore, unknownCandidates, corrections, terms, relatedCandidates`。terms 记录规范技术名及 JD/简历 occurrence；每处为 `canonical, original, start, end, source`，source 为 EXACT/ALIAS/CORRECTION，位置对应原始 Java 字符串（UTF-16）。未知候选不参与分母。顶层 overallScore/keywordScore 为去重技术覆盖率，semanticScore/atsScore 为兼容别名；无有效项时顶层零仅用于兼容，以 available=false、coverageScore=null 判断无法评估。历史 keywordEvaluation=null 表示 legacy-keyword-v1，原数值不改写。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/preparation-plans` | `analysisIds, weeklyBudgetMinutes, jobPriorities`；保存草稿 |
| GET | `/preparation-plans` | 最近最多 100 个计划 |
| GET | `/preparation-plans/{id}` | 计划、来源岗位、预算和任务 |
| PATCH | `/preparation-plans/{id}` | `expectedVersion, budgetMinutes, tasks`；调整草稿 |
| POST | `/preparation-plans/{id}/confirm` | `idempotencyKey, expectedVersion`；确认并创建/复用申请 |
| PATCH | `/preparation-plans/{id}/tasks/{taskId}` | `status, expectedVersion`；更新选中任务进度 |
| GET | `/preparation-plans/{id}/events` | 本计划与任务事件，最近最多 100 条 |
| GET | `/applications` | 投递记录列表 |
| PATCH | `/applications/{id}` | `status, note, expectedVersion` |
| GET | `/activity-events` | 最近最多 100 条事件，可用 `resourceId` 筛选 |

创建要求：1–3 份成功报告、同一简历版本、不同岗位；`jobPriorities` 是岗位 ID 到 1–3 的映射，不填默认 1。预算为 30–2400 分钟、30 的倍数；任务耗时为 30–960 分钟、30 的倍数；最多 20 个合并事项。

创建草稿示例：

```json
{
  "analysisIds": ["<分析运行ID>"],
  "weeklyBudgetMinutes": 480,
  "jobPriorities": { "<岗位ID>": 2 }
}
```

草稿编辑中 `tasks` 每项为 `id, title, expectedOutput, effortMinutes, selected`。只编辑提交的任务；标题和产物不能为空，选中总耗时不得超预算。

确认键最长 120 字符。相同计划/内容的重复确认复用结果；不同计划或内容占用同一键返回冲突。已确认计划不能继续改标题/预算等内容，可另建草稿。状态枚举包含 `ARCHIVED`，但当前没有归档 API。

任务状态：`TODO / DOING / DONE / SKIPPED`。允许 TODO→DOING/SKIPPED，DOING→TODO/DONE/SKIPPED，DONE→TODO/DOING，SKIPPED→TODO。相同状态不重复写事件。

投递状态：`PLANNED / APPLIED / INTERVIEW / OFFER / REJECTED / WITHDRAWN`，由用户手动记录和修正；备注最长 1000 字符，不发送实际投递。

## 5. 经历追问与证据卡

基础路径为 `/evidence-analyses/{analysisId}/experience-inquiries`，须属于成功报告。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/evidence-analyses/{analysisId}/experience-inquiries` | `requirementId`；202，创建或复用同一要求的追问 |
| GET | `/evidence-analyses/{analysisId}/experience-inquiries` | 该报告的追问列表 |
| GET | `/evidence-analyses/{analysisId}/experience-inquiries/{id}` | 状态、问题、回答、草稿和历史确认卡 |
| PUT | `/evidence-analyses/{analysisId}/experience-inquiries/{id}/answers` | `expectedVersion, answers`，三项 `{questionId,text}` |
| POST | `/evidence-analyses/{analysisId}/experience-inquiries/{id}/draft` | `expectedVersion`；从已保存回答生成草稿，不调用模型 |
| POST | `/evidence-analyses/{analysisId}/experience-inquiries/{id}/confirm` | `expectedVersion, confirmTruth:true`；原子保存确认卡 |
| POST | `/evidence-analyses/{analysisId}/experience-inquiries/{id}/reopen` | `expectedVersion`；重新编辑，历史卡保留 |
| POST | `/evidence-analyses/{analysisId}/experience-inquiries/{id}/retry` | `expectedVersion`；仅失败追问重试，202 |

会话状态：`QUESTIONS_PENDING / ANSWERING / CARD_DRAFT / CONFIRMED / FAILED`。`version` 用于回答、草稿、确认和重新编辑；确认后的重复确认复用已有结果，不重复写卡。

问题 ID 由后端固定为 `q1/q2/q3`，分别对应 `SCENARIO / IMPLEMENTATION / VALIDATION`。每项回答不超过 2000 字符；可以空白，至少一项非空才能生成卡。生成卡不接受客户端提供的事实或来源标签。

草稿来源 `USER_SUPPLIED`，事实来源 `USER_ANSWER`；确认卡来源 `USER_CONFIRMED`，包含固定报告/要求/简历版本 ID、确认时间、问题和完整回答快照。不存在自动改简历或自动将原判断改为 MATCH 的接口。下载和复制由前端完成。

模型最多尝试两次生成问题，独立 1 线程、4 等待名额；队列满时 429 并保留失败记录；失败码可能为 `MODEL_FAILED / INVALID_QUESTIONS / INTERRUPTED / QUEUE_FULL`。跨报告访问会被拒绝。当前来源标识仍是单用户声明，不是多用户身份认证。

## 6. 新模块错误

错误对象：`code, message, requestId`。requestId 是本次错误响应标识，目前不是完整调用链追踪能力。

| HTTP | 常见 code | 处理 |
|---|---|---|
| 400 | `INVALID_INPUT` | 修正正文、预算、组合等参数 |
| 404 | `NOT_FOUND` | 核实 ID 和记录是否存在 |
| 409 | `CONFLICT` | 刷新版本、核实计划状态或幂等键 |
| 422 | `INVALID_PDF / PDF_NO_TEXT / PDF_PARSE_FAILED` | 换可读 PDF 或粘贴正文 |
| 429 | `QUEUE_FULL` | 稍后重试，失败运行仍保留 |
| 500 | `OPERATION_FAILED` | 检查服务，再按实际问题处理 |

模型/证据阶段的 `MODEL_FAILED / INVALID_EVIDENCE / ANALYSIS_FAILED / INTERRUPTED` 常在异步运行的 `errorCode` 返回，不是一次同步提交请求就能看到的成功/失败结果。旧接口的错误处理不一定与新模块完全相同。

## 7. 上游保留接口

| 控制器 | 已有路径 |
|---|---|
| 健康与配置 | GET `/health`、`/status`、`/settings` |
| 简历 | POST/GET `/resumes`，GET/DELETE `/resumes/{id}`，POST `/resumes/{id}/structure` |
| 岗位 | POST/GET `/jobs`，GET/DELETE `/jobs/{id}`，POST `/jobs/{id}/structure` |
| 旧分析 | POST/GET `/analyses`，GET/DELETE `/analyses/{id}` |
| 改写 | POST `/analyses/{id}/rewrites`，GET `/rewrites`，GET/DELETE/PUT `/rewrites/{id}`；POST 其 `regenerate / accept / reject / exports/markdown / exports/pdf` 子路径 |
| 知识库 | POST/GET `/knowledge/documents`，PUT/DELETE `/knowledge/documents/{id}`，POST `/knowledge/documents/{id}/index`，POST `/knowledge/search` |

Actuator 健康地址是 `http://localhost:8080/actuator/health`，不在 `/api/v1` 下。配置显示正常不代表模型接口已成功调用。原简历文件入口支持的格式与新 PDF 入口不同；引用保护会阻止删除新版本简历及关联岗位。

详细旧请求字段见原控制器及 DTO，旧分析与新证据报告不能混用。2026-10-14 已完成一份虚构样例的原分析、真实模型改写与导出下载回归，范围见 [验收记录](验收记录.md)。

### 原改写候选确认（2026-10-14 修复）

`regenerate` 仍接收 `{userMessage}`，返回候选；候选及理由、校验信息暂存于现有 `conversation_history`，生成次数随生成增加，已应用正文不提前替换。历史候选标记 `PENDING / SUPERSEDED / ACCEPTED / REJECTED`；这是历史条目的状态，不替代草稿本身的状态。

`accept` 请求仍为 `{rewrittenText}`，后端要求其匹配最新待处理候选，从服务端候选读取正文、理由和校验信息，在同一事务中保存并标记接受；客户端不能提供替换的理由或校验结果。相同已接受候选的重复请求不增加生成次数。旧候选、被拒绝候选或没有候选快照的旧记录需重新生成，错误沿用原接口的处理方式；手动 `PUT` 编辑入口保留。

`reject` 标记待处理候选为拒绝，保留已应用正文。导出读取当前持久化草稿，而非未确认的预览候选。
