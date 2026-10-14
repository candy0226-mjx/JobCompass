# 前端应用

这里是 JobCompass 的 Next.js 前端，新增证据报告、预算计划和投递页面，同时保留原简历优化界面。

详细操作看[使用手册](../docs/USER_GUIDE.md)，继续二改看[实施计划](../docs/实施计划.md)。下文启动脚本从仓库根目录执行。

## 页面

- `/`：首页
- `/compass`：求职罗盘工作台
- `/evidence-analyses/[id]`：证据报告、人工复核和运行状态
- `/evidence-analyses/[id]/experience/[inquiryId]`：经历追问、回答保存、证据卡确认和历史
- `/preparation-plans/[id]`：准备计划调整、确认与进度
- `/applications`：手动投递状态和备注
- `/dashboard`：工作台和历史记录
- `/upload`：简历上传、岗位录入和分析创建
- `/resumes/[resumeId]`：简历详情
- `/jobs/[jobId]`：岗位详情
- `/analyses/[analysisId]`：匹配分析报告
- `/rewrites/[rewriteId]`：改写草稿、差异对比、事实校验和导出
- `/knowledge`：RAG 知识库管理
- `/settings`：运行配置和组件状态

## 本地启动

```powershell
.\scripts\start-frontend.ps1 -Install
```

如需指定后端地址：

```powershell
.\scripts\start-frontend.ps1 -ApiBaseUrl "http://localhost:8080/api/v1"
```

## 类型检查

```powershell
cd frontend
npm run typecheck
```
