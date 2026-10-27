# JobCompass 开发与维护指南

本文件用于个人二改、协作开发和交给 AI 继续实现，也可随开发习惯调整。

## 开始之前

先看 [文档索引](docs/README.md) 与 [使用手册](docs/USER_GUIDE.md)。环境为 JDK 21、Maven 3.9+、满足 Next.js 16 要求的 Node.js、Docker 基础服务及本地 `.env`。

当前基于 Resume-Ai-Agent 二次开发，保留 MIT `LICENSE` 与上游 Git 历史。模块归属见 [架构说明](docs/architecture.md)。改动前查看工作区差异，保留已有未提交内容。

## 实施一项改动

1. 在 [实施计划](docs/实施计划.md) 增加需求、状态、范围和验收条件。
2. 选择最小的相关模块实现，使用既有工具链；数据库结构通过新增 Flyway 迁移演进。
3. 按实际风险验证变更，将结果、未验证内容和遗留问题写入 [验收记录](docs/验收记录.md)。
4. 同步使用手册、接口或架构中的受影响章节，再更新计划状态。

公共设计与维护依据以仓库内 `docs/` 为准，克隆到任意本地目录后都可阅读。若维护者另外提供仓库外蓝图，按实际位置与权限编辑原文件；不要求所有协作者拥有该文件，也不在公共命令中写入个人电脑路径。

## 可用验证命令

常规后端测试，不调用真实云模型：

```powershell
mvn -f backend/pom.xml test
```

前端类型检查；涉及页面或构建时再执行生产构建：

```powershell
Push-Location frontend
try {
    npm run typecheck
    npm run build
} finally {
    Pop-Location
}
```

数据库规则或事务变化时，先启动 PostgreSQL，再显式运行：

```powershell
mvn -f backend/pom.xml '-Dtest=CompassPostgresIT' test
mvn -f backend/pom.xml '-Dtest=ExperiencePostgresIT' test
```

该测试通过 `COMPASS_TEST_DB_URL`、`COMPASS_TEST_DB_USER`、`COMPASS_TEST_DB_PASSWORD` 读取测试连接，未设置时使用本地开发默认值。它不自动读取根 `.env` 的数据库凭据；使用独立随机 schema，正常结束会清理，账号需要 schema 创建权限。

真实模型评估会消耗额度；它会启动临时 Spring 应用并连接已配置数据库，执行迁移并触发启动时的中断恢复检查。先停止正在运行的后端并确认没有待完成分析，再运行，避免影响已有任务。需要评估模型/工具快照时才显式执行，工作目录必须为 `backend`：

```powershell
Push-Location backend
try {
    mvn '-Dtest=CompassLiveModelIT' test
} finally {
    Pop-Location
}
```

当前评估使用 `samples/jobcompass-evaluation.json` 中 40 个虚构单要求样例（25 个开发、15 个独立验收），逐组交替执行 FULL/COMPACT，共 80 次分析任务。2026-10-27 升级 evidence-v3 后，默认输出改为 `docs/jobcompass-evidence-v3-evaluation-comparison.json`，避免覆盖此前 evidence-v2 的 `docs/jobcompass-evaluation-comparison.json`；历史 15 条结果文件也保持不变。每完成一次保存新输出，重复运行会覆盖所选输出文件。仅需试跑时执行 `mvn '-Dtest=CompassLiveModelIT' '-Dcompass.eval.limit=1' '-Dcompass.eval.output=target/evaluation-smoke.json' test`。少量试跑不能作为启用精简模式的依据。新分组评分另有 `samples/evidence-scoring-evaluation.json` 的多要求样例，不能仅凭原单要求数据证明新评分有效。

查看 `completed`、全部原始行、开发/验收集各自汇总以及 `compactEligible`。JUnit 通过表示评估执行/统计正常，不表示模型质量通过；失败任务保留在成功率和标签一致率分母中。Token 使用 ChatClient 返回的工具循环累计用量，再合计两个阶段及重试；缺失返回值不当作零，阶段尝试数不是 HTTP 请求数，Token 数不是账单。P50/P95 使用最近秩（nearest-rank）算法。当前每组仅一次对照，不是通用准确率或并发压测。

文档修改只检查内容、链接、格式与命令来源，不要求重跑整个业务链路。现有 `npm run lint` 使用旧 `next lint` 命令，尚未配置适合当前 Next.js 的 lint 流程，不能将其列为已通过的检查。

## 代码与资料约定

- 新求职罗盘业务优先放在 `com.resumeai.compass`；前端继续使用 `src/lib/api/compass.ts` 的请求封装。
- 网络模型调用不放进长数据库事务；已有模型报告与人工复核分别保存。
- 变更请求保持版本检查与幂等行为；模型输出要经过 Java 校验。
- 已在用户数据上执行的迁移脚本不要随意改写；新结构使用新增版本迁移。
- 文档和样例使用虚构资料；不要提交 `.env`、Key、真实简历、数据库备份、`node_modules`、`target`、`.next`。
- 本机验证不等于生产能力：多用户、持久化队列、认证和全应用容器部署均需要各自实现与验证。

## Git 与变更说明

可使用 `codex/功能名` 或团队约定的功能分支；当前本地分支情况以 `git status` 为准。提交前查看 diff，提交内容与任务范围保持一致。提交前缀可用 `feat:`、`fix:`、`docs:`、`refactor:`、`chore:`，不是强制冻结格式。

```text
改动目的：
主要修改：
数据库或配置影响：
验证方法和结果：
已知限制：
同步更新的文档：
```

需要远程协作时自行 Fork 或添加自己的 remote；不要将 `upstream` 当成自己的写入仓库。

先用 `git remote -v` 和 `git branch -vv` 核对当前克隆的远端、分支与写权限。直接克隆本项目时，`origin`默认指向本项目；需要独立二改且没有写权限时，先Fork，再克隆自己的Fork。分支名按当前工作选择，不依赖维护者曾使用的本地分支名。修改并验证后，选择本次相关文件提交，再推送到自己有权限的远端分支：

```powershell
git status
git diff
git add <本次相关文件>
git diff --cached
git commit -m "feat: 描述本次改动"
git push -u origin HEAD
```

首次克隆会检出远端默认分支；需要功能分支时自行创建。公开仓库包含代码、虚构样例和文档，不包含开发者数据库、对象存储数据、私有配置或构建产物。使用者需填写自己的配置、启动基础服务，并通过页面或接口录入资料；历史验收报告中的资源ID不会自动出现在新数据库里。

### 前端版本与回退

原版前端对应提交 `66d7449`，标签 `frontend-original`；方案 A 首版标签为 `frontend-style-a-v1`。标签用于查看或在独立目录体验历史代码，切换代码不会切换本机数据库内容。主题改造为独立提交 `feat: add minimal light frontend theme`，没有调整后端和数据库。

如需在当前分支撤销方案 A，先保存后续未提交修改，再查看主题提交并使用 `revert` 创建一条撤销提交：

```powershell
git log --oneline --grep="feat: add minimal light frontend theme"
git revert <上面查到的主题提交ID>
git push
```

撤销后重新运行 `npm run build` 并重启前端即可生效。若后续提交也修改了相同样式，可能需要处理冲突；不要用整个仓库的强制重置来撤销局部主题。

## 报告问题

```text
系统与 Java/Node/Docker 版本：
代码版本或分支：
复现步骤：
期望结果：
实际结果及错误码：
相关日志（隐藏 Key、私人信息）：
是否有可用数据库/模型服务：
```

可以直接在实施计划中记录，也可以在自己仓库建立 Issue。CI、Issue/PR 模板、全应用 Dockerfile 属于后续候选工作，当前没有自动发布流程。
