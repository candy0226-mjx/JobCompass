"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, type FormEvent, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { JDResumeComparison, AnalysisPreviewStrip, RewriteDiffPreview, KeywordEvaluationNotes } from "@/components/comparison-panels";
import { EvidenceMatrix, KeywordCloud } from "@/components/keyword-panels";
import { ScoreRing } from "@/components/score-ring";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import {
  type AnalysisResponse,
  type JobDescriptionResponse,
  type ResumeResponse,
  type RewriteDraftResponse,
  createAnalysis,
  createJobDescription,
  createRewrite,
  getResume,
  structureJob,
  structureResume,
  uploadResume,
} from "@/lib/api/client";
import { deriveJobKeywords, uniqueKeywords } from "@/lib/keywords";
import { formatJson } from "@/lib/format";

export default function UploadPage() {
  return (
    <Suspense fallback={<UploadPageFallback />}>
      <UploadWorkflow />
    </Suspense>
  );
}

function UploadWorkflow() {
  const searchParams = useSearchParams();
  const selectedResumeId = searchParams.get("resumeId");
  const [resumeTitle, setResumeTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [jobTitle, setJobTitle] = useState("");
  const [company, setCompany] = useState("");
  const [jobDescription, setJobDescription] = useState("");
  const [useRag, setUseRag] = useState(true);
  const [resume, setResume] = useState<ResumeResponse | null>(null);
  const [job, setJob] = useState<JobDescriptionResponse | null>(null);
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [rewrite, setRewrite] = useState<RewriteDraftResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isRewriting, setIsRewriting] = useState(false);
  const [isStructuringResume, setIsStructuringResume] = useState(false);
  const [isStructuringJob, setIsStructuringJob] = useState(false);
  const [isLoadingExistingResume, setIsLoadingExistingResume] = useState(false);

  useEffect(() => {
    if (!selectedResumeId) {
      return;
    }

    let ignore = false;
    const resumeIdToLoad = selectedResumeId;

    async function loadExistingResume() {
      setIsLoadingExistingResume(true);
      setError(null);
      try {
        const existingResume = await getResume(resumeIdToLoad);
        if (ignore) {
          return;
        }
        setResume(existingResume);
        setResumeTitle(existingResume.title);
        setFile(null);
        setJob(null);
        setAnalysis(null);
        setRewrite(null);
      } catch (loadError) {
        if (!ignore) {
          setError(loadError instanceof Error ? loadError.message : "已解析简历加载失败，请返回简历详情页重试。");
        }
      } finally {
        if (!ignore) {
          setIsLoadingExistingResume(false);
        }
      }
    }

    void loadExistingResume();

    return () => {
      ignore = true;
    };
  }, [selectedResumeId]);

  const comparisonKeywords = useMemo(() => {
    if (analysis) {
      return uniqueKeywords([
        ...analysis.report.extractedKeywords,
        ...analysis.report.matchedKeywords,
        ...analysis.report.missingKeywords,
      ]);
    }
    return deriveJobKeywords(jobDescription);
  }, [analysis, jobDescription]);

  async function handleResumeUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!file) {
      setError("请先选择 PDF、DOCX 或 TXT 简历文件。");
      return;
    }

    setIsUploading(true);
    setError(null);
    setResume(null);
    setJob(null);
    setAnalysis(null);
    setRewrite(null);

    try {
      setResume(await uploadResume(file, resumeTitle));
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : "简历上传失败。");
    } finally {
      setIsUploading(false);
    }
  }

  async function handleAnalysis(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!resume) {
      setError("请先上传并解析简历，再创建匹配分析。");
      return;
    }
    if (jobDescription.trim().length < 30) {
      setError("请粘贴更完整的目标岗位 JD，至少包含岗位职责和任职要求。");
      return;
    }

    setIsAnalyzing(true);
    setError(null);
    setAnalysis(null);
    setRewrite(null);

    try {
      const createdJob = await createJobDescription({
        title: jobTitle,
        company,
        description: jobDescription,
      });
      setJob(createdJob);
      setAnalysis(await createAnalysis({
        resumeId: resume.id,
        jobId: createdJob.id,
        useRag,
      }));
    } catch (analysisError) {
      setError(analysisError instanceof Error ? analysisError.message : "匹配分析失败。");
    } finally {
      setIsAnalyzing(false);
    }
  }

  async function handleRewrite() {
    if (!analysis || !resume) {
      return;
    }

    setIsRewriting(true);
    setError(null);
    setRewrite(null);
    try {
      setRewrite(await createRewrite({
        analysisId: analysis.id,
        sectionId: "tailor-preview",
        sectionText: resume.rawText,
      }));
    } catch (rewriteError) {
      setError(rewriteError instanceof Error ? rewriteError.message : "智能体改写失败。");
    } finally {
      setIsRewriting(false);
    }
  }

  async function handleStructureResume() {
    if (!resume) {
      return;
    }
    setIsStructuringResume(true);
    setError(null);
    try {
      setResume(await structureResume(resume.id));
    } catch (structureError) {
      setError(structureError instanceof Error ? structureError.message : "简历结构化失败。");
    } finally {
      setIsStructuringResume(false);
    }
  }

  async function handleStructureJob() {
    if (!job) {
      return;
    }
    setIsStructuringJob(true);
    setError(null);
    try {
      setJob(await structureJob(job.id));
    } catch (structureError) {
      setError(structureError instanceof Error ? structureError.message : "JD 结构化失败。");
    } finally {
      setIsStructuringJob(false);
    }
  }

  return (
    <AppShell
      actions={
        <>
          <ButtonLink href="/dashboard" tone="paper">回到工作台</ButtonLink>
          <ButtonLink href="/knowledge" tone="lime">RAG 知识库</ButtonLink>
        </>
      }
      description="上传主简历、粘贴目标 JD、选择 RAG、生成预览分析，再通过差异预览确认智能体改写。"
      eyebrow="简历定制"
      title="为一个目标岗位定制你的简历。"
    >
      {error ? <p className="mb-6 border border-black bg-[#dc2626] p-4 font-mono text-sm font-bold uppercase text-white shadow-sw-sm">{error}</p> : null}

      <section className="mb-6 grid gap-3 md:grid-cols-4">
        <FlowStep active done={Boolean(resume)} index="01" label="上传主简历" />
        <FlowStep active={Boolean(resume)} done={Boolean(job)} index="02" label="粘贴目标 JD" />
        <FlowStep active={Boolean(job)} done={Boolean(analysis)} index="03" label="匹配分析" />
        <FlowStep active={Boolean(analysis)} done={Boolean(rewrite)} index="04" label="改写预览" />
      </section>

      <section className="grid gap-6 lg:grid-cols-[0.92fr_1.08fr]">
        <Card tone="paper">
          <CardHeader
            eyebrow="主简历"
            title={resume ? "已带入主简历" : "上传或替换主简历"}
            description={resume ? "已加载数据库中的解析文本，可直接粘贴目标 JD 生成匹配分析；也可以重新上传文件替换主简历。" : "支持 PDF、DOCX、TXT。解析后会显示文本预览，也可以进一步调用模型抽取结构化 JSON。"}
          />
          {isLoadingExistingResume ? (
            <p className="mt-5 border border-black bg-[#facc15] p-4 font-mono text-xs font-bold uppercase tracking-wide text-black shadow-sw-sm">
              正在带入已解析简历...
            </p>
          ) : null}
          {resume ? (
            <div className="mt-5 border border-black bg-[#dcfce7] p-4 shadow-sw-xs">
              <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#15803d]">已选择主简历</p>
              <p className="mt-2 font-serif text-xl font-semibold uppercase leading-tight">{resume.title}</p>
              <p className="mt-2 font-mono text-xs uppercase leading-5 text-[#166534]">
                {resume.originalFilename} · {resume.status} · {resume.rawTextLength} 字
              </p>
            </div>
          ) : null}
          <form className="mt-6 space-y-5" onSubmit={handleResumeUpload}>
            <label className="block font-mono text-xs font-bold uppercase tracking-wide">
              简历标题
              <input
                className="mt-2 w-full rounded-none border border-black bg-[#f0f0e8] px-4 py-3 outline-none focus:bg-[#e5e5e0] focus:ring-2 focus:ring-[#1d4ed8]"
                placeholder="Java 后端工程师主简历"
                value={resumeTitle}
                onChange={(event) => setResumeTitle(event.target.value)}
              />
            </label>
            <label className="block cursor-pointer border border-dashed border-black bg-[#e5e5e0] p-6 transition hover:bg-[#d8d8d2]">
              <span className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">拖放/选择文件</span>
              <span className="mt-3 block font-mono text-xs uppercase leading-5 text-[#6b7280]">{file ? `${file.name} · ${Math.ceil(file.size / 1024)} KB` : "选择一份原始简历，建议小于 25MB。"}</span>
              <input
                accept=".pdf,.doc,.docx,.txt,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                className="sr-only"
                type="file"
                onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              />
            </label>
            <div className="flex flex-wrap gap-3">
              <Button disabled={isUploading} tone="default" type="submit">
                {isUploading ? "解析中" : "上传并解析"}
              </Button>
              {resume ? (
                <>
                  <Button disabled={isStructuringResume} onClick={handleStructureResume} tone="paper" type="button">
                    {isStructuringResume ? "结构化中" : "结构化简历"}
                  </Button>
                  <ButtonLink href={`/resumes/${resume.id}`} tone="success">详情</ButtonLink>
                </>
              ) : null}
            </div>
          </form>

          <div className="mt-6 border border-black bg-[#e5e5e0] p-4">
            <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">解析预览</p>
            <p className="mt-3 whitespace-pre-wrap font-mono text-xs uppercase leading-5 text-[#6b7280]">
              {resume?.rawText || "上传后这里会显示简历文本。"}
            </p>
          </div>
          {resume?.structuredJson ? (
            <pre className="mt-4 whitespace-pre-wrap border border-black bg-black p-4 font-mono text-xs leading-5 text-white">
              {formatJson(resume.structuredJson)}
            </pre>
          ) : null}
        </Card>

        <Card tone="panel">
          <CardHeader
            eyebrow="岗位描述"
            title="粘贴目标岗位"
            description="这里是岗位输入区，提交后会创建岗位记录并生成匹配报告。"
          />
          <form className="mt-6 space-y-5" onSubmit={handleAnalysis}>
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block font-mono text-xs font-bold uppercase tracking-wide">
                岗位名称
                <input
                  className="mt-2 w-full rounded-none border border-black bg-[#f0f0e8] px-4 py-3 outline-none focus:bg-[#e5e5e0] focus:ring-2 focus:ring-[#1d4ed8]"
                  placeholder="高级 Java / AI 后端工程师"
                  value={jobTitle}
                  onChange={(event) => setJobTitle(event.target.value)}
                />
              </label>
              <label className="block font-mono text-xs font-bold uppercase tracking-wide">
                公司
                <input
                  className="mt-2 w-full rounded-none border border-black bg-[#f0f0e8] px-4 py-3 outline-none focus:bg-[#e5e5e0] focus:ring-2 focus:ring-[#1d4ed8]"
                  placeholder="目标公司"
                  value={company}
                  onChange={(event) => setCompany(event.target.value)}
                />
              </label>
            </div>
            <label className="block font-mono text-xs font-bold uppercase tracking-wide">
              JD 原文
              <textarea
                className="panel-scroll mt-2 min-h-80 w-full resize-y rounded-none border border-black bg-[#f0f0e8] px-4 py-3 font-mono text-sm leading-6 outline-none focus:bg-[#e5e5e0] focus:ring-2 focus:ring-[#1d4ed8]"
                placeholder="粘贴岗位职责、任职要求、加分项..."
                value={jobDescription}
                onChange={(event) => setJobDescription(event.target.value)}
              />
            </label>
            <div className="flex flex-wrap items-center justify-between gap-3 border border-black bg-[#f0f0e8] p-4">
              <label className="flex items-center gap-3 font-mono text-xs font-bold uppercase tracking-wide">
                <input checked={useRag} className="h-5 w-5 accent-[#1d4ed8]" onChange={(event) => setUseRag(event.target.checked)} type="checkbox" />
                使用 RAG 建议
              </label>
              <div className="flex flex-wrap gap-3">
                <Button disabled={isAnalyzing || !resume} tone="default" type="submit">
                  {isAnalyzing ? "分析中" : "生成匹配预览"}
                </Button>
                {job ? (
                  <Button disabled={isStructuringJob} onClick={handleStructureJob} tone="paper" type="button">
                    {isStructuringJob ? "结构化中" : "结构化 JD"}
                  </Button>
                ) : null}
              </div>
            </div>
          </form>
          {job?.structuredJson ? (
            <pre className="mt-4 whitespace-pre-wrap border border-black bg-black p-4 font-mono text-xs leading-5 text-white">
              {formatJson(job.structuredJson)}
            </pre>
          ) : null}
        </Card>
      </section>

      <section className="mt-6">
        <JDResumeComparison
          jobDescription={jobDescription}
          keywords={comparisonKeywords}
          resumeText={resume?.rawText ?? ""}
          analysis={analysis}
        />
      </section>

      {analysis ? (
        <section className="mt-6 grid gap-6 lg:grid-cols-[0.8fr_1.2fr]">
          <Card tone="paper">
            <CardHeader
              action={<ButtonLink href={`/analyses/${analysis.id}`} tone="ink">完整报告</ButtonLink>}
              eyebrow="匹配分析"
              title="匹配预览已生成"
              description="这一步对应参考项目里的预览检查：先看分数、缺口、证据，再决定是否生成改写。"
            />
            <div className="mt-5">
              {analysis.report.keywordEvaluation?.available === false ? <p>没有可计分技术项，无法评估。</p> : <ScoreRing label="规则综合" score={analysis.overallScore} />}
              <KeywordEvaluationNotes analysis={analysis} />
            </div>
            <div className="mt-5">
              <AnalysisPreviewStrip analysis={analysis} />
            </div>
          </Card>

          <Card tone="sky">
            <CardHeader
              eyebrow="关键词"
              title="关键词覆盖与缺口"
              description="绿色表示字面命中；红色表示未找到相应技术名，需要核实实际经历。"
            />
            <div className="mt-5 grid gap-5 md:grid-cols-2">
              <div>
                <h3 className="mb-3 font-mono text-xs font-black uppercase tracking-[0.18em]">已匹配</h3>
                <KeywordCloud keywords={analysis.report.matchedKeywords} tone="matched" />
              </div>
              <div>
                <h3 className="mb-3 font-mono text-xs font-black uppercase tracking-[0.18em]">缺失</h3>
                <KeywordCloud keywords={analysis.report.missingKeywords} tone="missing" />
              </div>
            </div>
          </Card>
        </section>
      ) : null}

      {analysis ? (
        <section className="mt-6 grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
          <Card tone="paper">
            <CardHeader eyebrow="证据" title="证据映射" description="每个关键词都应该能追溯到简历证据，缺证据时不要硬编经历。" />
            <div className="mt-5">
              <EvidenceMatrix evidence={analysis.report.evidenceMap} />
            </div>
          </Card>
          <Card tone="ink">
            <CardHeader eyebrow="RAG 建议" title="检索建议" description="来自 PGvector 的简历优化规则会作为模型改写的参考上下文。" />
            <div className="mt-5 space-y-3">
              {analysis.report.retrievedGuidance.length > 0 ? (
                analysis.report.retrievedGuidance.map((guidance) => (
                  <p className="border border-white/80 bg-white/10 p-4 font-mono text-xs uppercase leading-5 text-white/80" key={guidance}>{guidance}</p>
                ))
              ) : (
                <p className="border border-white/50 bg-white/10 p-4 font-mono text-xs uppercase leading-5 text-white/75">本次没有检索到 RAG 建议。可以先去知识库创建并索引规则文档。</p>
              )}
            </div>
          </Card>
        </section>
      ) : null}

      {analysis ? (
        <section className="mt-6">
          <div className="flex justify-center">
            <Button disabled={isRewriting} onClick={handleRewrite} tone="default" type="button">
              {isRewriting ? "改写中" : "生成改写"}
            </Button>
          </div>
        </section>
      ) : null}

      {rewrite ? (
        <section className="mt-6 space-y-6">
          <RewriteDiffPreview rewrite={rewrite} />
          <Card tone="panel">
            <CardHeader
              action={<ButtonLink href={`/rewrites/${rewrite.id}`} tone="default">查看并导出</ButtonLink>}
              eyebrow="确认"
              title="改写草稿已生成"
              description={rewrite.rationale}
            />
          </Card>
        </section>
      ) : null}
    </AppShell>
  );
}

function UploadPageFallback() {
  return (
    <AppShell
      actions={<ButtonLink href="/dashboard" tone="paper">回到工作台</ButtonLink>}
      description="正在读取定制入口参数，稍后会加载已解析简历或显示上传表单。"
      eyebrow="简历定制"
      title="正在准备定制工作台。"
    >
      <p className="border border-black bg-[#f0f0e8] p-6 font-mono font-bold uppercase shadow-sw-sm">
        正在加载定制页面...
      </p>
    </AppShell>
  );
}

function FlowStep({
  active,
  done,
  index,
  label,
}: {
  active: boolean;
  done: boolean;
  index: string;
  label: string;
}) {
  return (
    <article className={`border border-black p-4 shadow-sw-sm ${done ? "bg-[#15803d] text-white" : active ? "bg-[#1d4ed8] text-white" : "bg-[#f0f0e8] text-black"}`}>
      <p className="font-mono text-xs font-bold uppercase tracking-wide opacity-70">{index}</p>
      <p className="mt-2 font-serif text-lg font-semibold uppercase leading-tight">{label}</p>
      <p className="mt-2 font-mono text-xs font-bold uppercase tracking-wide opacity-70">{done ? "完成" : active ? "当前" : "等待"}</p>
    </article>
  );
}
