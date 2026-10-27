"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ScoreRing } from "@/components/score-ring";
import {
  type AnalysisResponse,
  type JobDescriptionResponse,
  type KnowledgeDocumentResponse,
  type ResumeResponse,
  type RewriteDraftResponse,
  type SystemStatusResponse,
  deleteAnalysis,
  deleteJobDescription,
  getSystemStatus,
  deleteRewrite,
  deleteResume,
  listAnalyses,
  listJobDescriptions,
  listKnowledgeDocuments,
  listResumes,
  listRewrites,
} from "@/lib/api/client";
import { clampText, formatDateTime } from "@/lib/format";

type DashboardData = {
  status: SystemStatusResponse | null;
  resumes: ResumeResponse[];
  jobs: JobDescriptionResponse[];
  analyses: AnalysisResponse[];
  rewrites: RewriteDraftResponse[];
  knowledge: KnowledgeDocumentResponse[];
};

type DeleteTarget =
  | { kind: "resume"; item: ResumeResponse }
  | { kind: "job"; item: JobDescriptionResponse }
  | { kind: "analysis"; item: AnalysisResponse }
  | { kind: "rewrite"; item: RewriteDraftResponse };

const emptyData: DashboardData = {
  status: null,
  resumes: [],
  jobs: [],
  analyses: [],
  rewrites: [],
  knowledge: [],
};

export default function DashboardPage() {
  const [data, setData] = useState<DashboardData>(emptyData);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [deletingRecordId, setDeletingRecordId] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

  async function loadDashboard() {
    setIsLoading(true);
    setError(null);
    try {
      const [status, resumes, jobs, analyses, rewrites, knowledge] = await Promise.all([
        getSystemStatus(),
        listResumes(),
        listJobDescriptions(),
        listAnalyses(),
        listRewrites(),
        listKnowledgeDocuments(),
      ]);
      setData({ status, resumes, jobs, analyses, rewrites, knowledge });
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "工作台加载失败。");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadDashboard();
  }, []);

  const latestResume = data.resumes[0] ?? null;
  const latestJob = data.jobs[0] ?? null;
  const bestAnalysis = useMemo(
    () => {
      const version = data.analyses[0]?.report.keywordEvaluation?.algorithmVersion ?? "legacy-keyword-v1";
      return data.analyses.filter(a => a.report.keywordEvaluation?.available !== false &&
        (a.report.keywordEvaluation?.algorithmVersion ?? "legacy-keyword-v1") === version)
        .reduce<AnalysisResponse | null>((best, a) => (!best || a.overallScore > best.overallScore ? a : best), null);
    },
    [data.analyses],
  );
  const llmComponent = data.status?.components.find((component) => component.name === "llm");
  const readyForTailor = Boolean(latestResume && llmComponent?.status === "CONFIGURED");

  async function confirmDelete() {
    if (!deleteTarget) {
      return;
    }
    if (deleteTarget.kind === "resume") {
      await handleDeleteResume(deleteTarget.item);
    } else if (deleteTarget.kind === "job") {
      await handleDeleteJob(deleteTarget.item);
    } else if (deleteTarget.kind === "analysis") {
      await handleDeleteAnalysis(deleteTarget.item);
    } else {
      await handleDeleteRewrite(deleteTarget.item);
    }
  }

  async function handleDeleteResume(resume: ResumeResponse) {
    setDeletingRecordId(resume.id);
    setError(null);
    try {
      await deleteResume(resume.id);
      setData((current) => ({
        ...current,
        resumes: current.resumes.filter((item) => item.id !== resume.id),
        analyses: current.analyses.filter((analysis) => analysis.resumeId !== resume.id),
        rewrites: current.rewrites.filter((rewrite) => current.analyses.find((analysis) => analysis.id === rewrite.analysisId)?.resumeId !== resume.id),
      }));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "简历删除失败。");
    } finally {
      setDeletingRecordId(null);
      setDeleteTarget(null);
    }
  }

  async function handleDeleteJob(job: JobDescriptionResponse) {
    setDeletingRecordId(job.id);
    setError(null);
    try {
      await deleteJobDescription(job.id);
      setData((current) => ({
        ...current,
        jobs: current.jobs.filter((item) => item.id !== job.id),
        analyses: current.analyses.filter((analysis) => analysis.jobId !== job.id),
        rewrites: current.rewrites.filter((rewrite) => current.analyses.find((analysis) => analysis.id === rewrite.analysisId)?.jobId !== job.id),
      }));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "岗位删除失败。");
    } finally {
      setDeletingRecordId(null);
      setDeleteTarget(null);
    }
  }

  async function handleDeleteAnalysis(analysis: AnalysisResponse) {
    setDeletingRecordId(analysis.id);
    setError(null);
    try {
      await deleteAnalysis(analysis.id);
      setData((current) => ({
        ...current,
        analyses: current.analyses.filter((item) => item.id !== analysis.id),
        rewrites: current.rewrites.filter((rewrite) => rewrite.analysisId !== analysis.id),
      }));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "分析报告删除失败。");
    } finally {
      setDeletingRecordId(null);
      setDeleteTarget(null);
    }
  }

  async function handleDeleteRewrite(rewrite: RewriteDraftResponse) {
    setDeletingRecordId(rewrite.id);
    setError(null);
    try {
      await deleteRewrite(rewrite.id);
      setData((current) => ({
        ...current,
        rewrites: current.rewrites.filter((item) => item.id !== rewrite.id),
      }));
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "改写草稿删除失败。");
    } finally {
      setDeletingRecordId(null);
      setDeleteTarget(null);
    }
  }

  const deleteCopy = deleteTarget ? describeDeleteTarget(deleteTarget) : null;

  return (
    <AppShell
      actions={
        <>
          <Button disabled={isLoading} onClick={loadDashboard} tone="paper" type="button">
            {isLoading ? "刷新中" : "刷新"}
          </Button>
          <ButtonLink href="/upload" tone="gold">新建定制</ButtonLink>
        </>
      }
      description="参考 Resume-Matcher 的工作台结构：先确认主简历和模型状态，再进入定制流程，并在同一页追踪最近生成的分析、改写与 RAG 资产。"
      eyebrow="工作台"
      title="你的简历优化控制塔。"
    >
      {error ? <p className="border border-black bg-[#dc2626] p-4 font-mono text-sm font-bold uppercase text-white shadow-sw-sm">{error}</p> : null}

      <section className="grid grid-cols-1 gap-[1px] border border-black bg-black md:grid-cols-2 xl:grid-cols-12 xl:auto-rows-[6.75rem]">
        <ModuleCard className="min-h-72 xl:col-span-3 xl:row-span-2 xl:min-h-0" href={latestResume ? `/resumes/${latestResume.id}` : "/upload"} tone="primary">
          <div className="flex h-full flex-col justify-between">
            <div>
              <div className="flex items-start justify-between gap-4">
                <div className="flex h-16 w-16 items-center justify-center border-2 border-black bg-[#1d4ed8] font-mono text-xl font-bold text-white">
                  简
                </div>
                <StatusPill label={readyForTailor ? "就绪" : "待配置"} />
              </div>
              <h2 className="mt-8 font-serif text-3xl font-semibold uppercase leading-none tracking-tight">
                {latestResume ? "主简历" : "初始化简历"}
              </h2>
              <p className="mt-4 line-clamp-5 font-mono text-xs uppercase leading-5 text-[#6b7280]">
                {latestResume ? `${latestResume.title} // ${latestResume.status} // ${latestResume.rawTextLength} 字` : "上传一份基础简历，作为后续 JD 定制和改写的主档案。"}
              </p>
            </div>
            <span className="mt-6 font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">
              {latestResume ? "打开简历" : "立即上传"}
            </span>
          </div>
        </ModuleCard>

        <ModuleCard className="min-h-72 xl:col-span-3 xl:row-span-2 xl:min-h-0" href="/upload">
          <div className="flex h-full flex-col justify-between">
              <div className="flex h-12 w-12 items-center justify-center border-2 border-current text-3xl leading-none">+</div>
            <div>
              <h2 className="font-serif text-3xl font-semibold uppercase leading-none tracking-tight">定制简历</h2>
              <p className="mt-3 font-mono text-xs uppercase leading-5 text-[#6b7280]">粘贴目标 JD，生成关键词覆盖、证据映射、RAG 建议和改写草稿。</p>
            </div>
          </div>
        </ModuleCard>

        <ModuleCard className="min-h-72 xl:col-span-3 xl:row-span-2 xl:min-h-0" href="/settings" tone="dark">
          <div className="flex h-full flex-col justify-between">
            <div>
              <p className="font-mono text-xs font-bold uppercase tracking-wide text-white/60">系统状态</p>
              <h2 className="mt-3 font-serif text-4xl font-semibold uppercase leading-none">{data.status?.status ?? "未知"}</h2>
              <p className="mt-3 font-mono text-xs uppercase leading-5 text-white/60">
                {data.status ? formatDateTime(data.status.timestamp) : "等待后端状态"}
              </p>
            </div>
            <div className="space-y-2">
              {(data.status?.components ?? []).slice(0, 4).map((component) => (
                <div className="flex items-center justify-between gap-2 border border-white/40 px-2 py-1 font-mono text-[10px] uppercase text-white/75" key={component.name}>
                  <span>{formatComponentName(component.name)}</span>
                  <span>{formatStatus(component.status)}</span>
                </div>
              ))}
            </div>
          </div>
        </ModuleCard>

        <StatsCluster
          className="min-h-72 md:col-span-2 xl:col-span-3 xl:row-span-2 xl:min-h-0"
          stats={[
            ["简历", data.resumes.length, "#resumes"],
            ["岗位", data.jobs.length, "#jobs"],
            ["分析", data.analyses.length, "#analyses"],
            ["改写", data.rewrites.length, "#rewrites"],
            ["RAG 文档", data.knowledge.length, "/knowledge"],
          ]}
        />

        <ModuleCard className="min-h-72 md:col-span-2 xl:col-span-8 xl:row-span-2 xl:min-h-0" href={bestAnalysis ? `/analyses/${bestAnalysis.id}` : "/upload"} compact>
          <div className="grid h-full gap-8 md:grid-cols-[auto_1fr] md:items-center">
            {bestAnalysis ? <ScoreRing label={`同算法最高规则分 · ${bestAnalysis.report.keywordEvaluation?.algorithmVersion ?? "legacy-keyword-v1"}`} score={bestAnalysis.overallScore} /> : <div className="flex h-32 w-32 items-center justify-center border border-black bg-[#e5e5e0] font-mono text-xs font-bold uppercase">暂无可评分报告</div>}
            <div>
              <h2 className="font-serif text-3xl font-semibold uppercase leading-none tracking-tight">{bestAnalysis ? "最佳分析" : "创建第一份分析"}</h2>
              <p className="mt-4 font-mono text-xs uppercase leading-5 text-[#6b7280]">
                {bestAnalysis ? `缺失关键词 ${bestAnalysis.report.missingKeywords.length} 个，建议 ${bestAnalysis.report.suggestions.length} 条。` : "完成简历定制流程后，这里显示最适合继续改写的报告。"}
              </p>
            </div>
          </div>
        </ModuleCard>

        <ModuleCard className="min-h-72 md:col-span-2 xl:col-span-4 xl:row-span-2 xl:min-h-0" href="/knowledge" tone="primary" compact>
          <div className="flex h-full flex-col justify-between">
            <div>
              <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8] group-hover:text-white/70">知识库</p>
              <h2 className="mt-4 font-serif text-3xl font-semibold uppercase leading-none tracking-tight">让改写有依据</h2>
            </div>
            <p className="font-mono text-xs uppercase leading-5 text-[#6b7280] group-hover:text-white/80">
              把简历规则、岗位偏好和 ATS 建议放进 RAG，后续改写就能稳定参考同一套标准。
            </p>
          </div>
        </ModuleCard>
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <RecordList
          id="resumes"
          emptyText="还没有上传简历。"
          items={data.resumes.slice(0, 5).map((resume) => ({
            action: (
              <Button
                disabled={deletingRecordId === resume.id}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setDeleteTarget({ kind: "resume", item: resume });
                }}
                tone="danger"
                type="button"
              >
                {deletingRecordId === resume.id ? "删除中" : "删除"}
              </Button>
            ),
            href: `/resumes/${resume.id}`,
            meta: `${resume.status} · ${formatDateTime(resume.createdAt)}`,
            title: resume.title,
            body: resume.rawTextPreview || resume.originalFilename,
          }))}
          title="最近简历"
        />
        <RecordList
          id="jobs"
          emptyText="还没有创建岗位。"
          items={data.jobs.slice(0, 5).map((job) => ({
            action: (
              <Button
                disabled={deletingRecordId === job.id}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setDeleteTarget({ kind: "job", item: job });
                }}
                tone="danger"
                type="button"
              >
                {deletingRecordId === job.id ? "删除中" : "删除"}
              </Button>
            ),
            href: `/jobs/${job.id}`,
            meta: `${job.company || "未知公司"} · ${job.status}`,
            title: job.title || "未命名岗位",
            body: job.description,
          }))}
          title="目标岗位"
        />
        <RecordList
          id="analyses"
          emptyText="还没有分析报告。"
          items={data.analyses.slice(0, 5).map((analysis) => ({
            action: (
              <Button
                disabled={deletingRecordId === analysis.id}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setDeleteTarget({ kind: "analysis", item: analysis });
                }}
                tone="danger"
                type="button"
              >
                {deletingRecordId === analysis.id ? "删除中" : "删除"}
              </Button>
            ),
            href: `/analyses/${analysis.id}`,
            meta: `${analysis.status} · ${analysis.report.keywordEvaluation?.algorithmVersion ?? "legacy-keyword-v1"} · ${analysis.report.keywordEvaluation?.available === false ? "无法评估" : `规则综合 ${analysis.overallScore}`}`,
            title: `缺口 ${analysis.report.missingKeywords.length} · 建议 ${analysis.report.suggestions.length}`,
            body: analysis.report.suggestions.join(" "),
          }))}
          title="分析报告"
        />
        <RecordList
          id="rewrites"
          emptyText="还没有改写草稿。"
          items={data.rewrites.slice(0, 5).map((rewrite) => ({
            action: (
              <Button
                disabled={deletingRecordId === rewrite.id}
                onClick={(event) => {
                  event.preventDefault();
                  event.stopPropagation();
                  setDeleteTarget({ kind: "rewrite", item: rewrite });
                }}
                tone="danger"
                type="button"
              >
                {deletingRecordId === rewrite.id ? "删除中" : "删除"}
              </Button>
            ),
            href: `/rewrites/${rewrite.id}`,
            meta: `${rewrite.status} · ${formatDateTime(rewrite.createdAt)}`,
            title: rewrite.sectionId || "改写草稿",
            body: rewrite.rewrittenText,
          }))}
          title="改写草稿"
        />
      </section>
      {deleteCopy ? (
        <ConfirmDialog
          description={deleteCopy.description}
          isOpen={Boolean(deleteTarget)}
          isWorking={Boolean(deletingRecordId)}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => void confirmDelete()}
          title={deleteCopy.title}
        />
      ) : null}
    </AppShell>
  );
}

function describeDeleteTarget(target: DeleteTarget) {
  if (target.kind === "resume") {
    return {
      title: "删除这份简历？",
      description: `将删除“${target.item.title}”及其原始文件；关联的分析报告和改写草稿也会一起删除。`,
    };
  }
  if (target.kind === "job") {
    return {
      title: "删除这个岗位？",
      description: `将删除“${target.item.title || "未命名岗位"}”；基于它生成的分析报告和改写草稿也会一起删除。`,
    };
  }
  if (target.kind === "analysis") {
    return {
      title: "删除这份报告？",
      description: "将删除这份分析报告；基于它生成的改写草稿也会一起删除。",
    };
  }
  return {
    title: "删除这份草稿？",
    description: "将删除这份改写草稿；如果它导出过 Markdown 或 PDF，系统也会尝试清理对应文件。",
  };
}

function StatusPill({ label }: { label: string }) {
  return <span className="border border-black bg-[#f0f0e8] px-3 py-1 font-mono text-xs font-bold uppercase tracking-wide text-black">{label}</span>;
}

function StatsCluster({
  className = "",
  stats,
}: {
  className?: string;
  stats: Array<[string, number, string]>;
}) {
  return (
    <div className={`grid grid-cols-2 gap-[1px] bg-black ${className}`}>
      {stats.map(([label, value, href], index) => (
        <Link
          className={`flex min-h-0 flex-col justify-between bg-[#f0f0e8] p-4 transition-[transform,box-shadow,background-color,color] duration-150 hover:z-20 hover:-translate-x-[2px] hover:-translate-y-[2px] hover:bg-[#e5e5e0] hover:shadow-sw-default ${index === stats.length - 1 ? "col-span-2" : ""}`}
          href={href}
          key={label}
        >
          <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">{label}</p>
          <p className="font-mono text-3xl font-bold leading-none">{value}</p>
        </Link>
      ))}
    </div>
  );
}

function ModuleCard({
  children,
  className = "",
  compact = false,
  href,
  tone = "light",
}: {
  children: React.ReactNode;
  className?: string;
  compact?: boolean;
  href: string;
  tone?: "light" | "primary" | "dark";
}) {
  const toneClass = {
    light: "bg-[#f0f0e8] text-black hover:bg-[#e5e5e0]",
    primary: "bg-[#f0f0e8] text-black hover:bg-[#1d4ed8] hover:text-white",
    dark: "bg-black text-white hover:bg-[#1d4ed8]",
  }[tone];

  return (
    <Link className={`group block ${compact ? "p-5" : "p-6"} transition-[transform,box-shadow,background-color,color] duration-150 hover:z-20 hover:-translate-x-[2px] hover:-translate-y-[2px] hover:shadow-sw-default ${toneClass} ${className}`} href={href}>
      {children}
    </Link>
  );
}

function formatComponentName(name: string) {
  const names: Record<string, string> = {
    database: "数据库",
    embedding: "向量模型",
    llm: "大模型",
    minio: "对象存储",
    redis: "缓存",
    storage: "对象存储",
  };
  return names[name.toLowerCase()] ?? name;
}

function formatStatus(status: string) {
  const statuses: Record<string, string> = {
    CONFIGURED: "已配置",
    DEGRADED: "降级",
    DOWN: "异常",
    ERROR: "错误",
    HEALTHY: "正常",
    NOT_CONFIGURED: "未配置",
    READY: "就绪",
    UNKNOWN: "未知",
    UP: "正常",
  };
  return statuses[status.toUpperCase()] ?? status;
}

function RecordList({
  emptyText,
  id,
  items,
  title,
}: {
  emptyText: string;
  id?: string;
  items: Array<{ action?: React.ReactNode; body: string; href: string; meta: string; title: string }>;
  title: string;
}) {
  return (
    <div id={id}>
      <Card tone="paper">
        <CardHeader eyebrow="记录" title={title} />
      <div className="mt-5 space-y-3">
        {items.length > 0 ? (
          items.map((item) => (
            <Link className="block border border-black bg-[#f0f0e8] p-4 transition hover:translate-x-[1px] hover:translate-y-[1px] hover:bg-[#e5e5e0] hover:shadow-none shadow-sw-sm" href={item.href} key={item.href}>
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">{item.meta}</p>
                  <h3 className="mt-2 font-serif text-xl font-semibold uppercase leading-tight">{item.title}</h3>
                </div>
                {item.action}
              </div>
              <p className="mt-2 line-clamp-3 font-mono text-xs uppercase leading-5 text-[#6b7280]">{clampText(item.body, 220)}</p>
            </Link>
          ))
        ) : (
          <p className="border border-dashed border-black bg-[#e5e5e0] p-5 font-mono text-xs uppercase leading-5 text-[#6b7280]">{emptyText}</p>
        )}
      </div>
    </Card>
    </div>
  );
}
