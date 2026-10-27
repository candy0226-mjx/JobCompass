"use client";

import { useRouter } from "next/navigation";
import { use, useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { AnalysisPreviewStrip, KeywordEvaluationNotes } from "@/components/comparison-panels";
import { EvidenceMatrix, KeywordCloud } from "@/components/keyword-panels";
import { ScoreRing } from "@/components/score-ring";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import {
  type AnalysisResponse,
  type RewriteDraftResponse,
  createRewrite,
  deleteAnalysis,
  getAnalysis,
} from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

export default function AnalysisDetailPage({ params }: { params: Promise<{ analysisId: string }> }) {
  const { analysisId } = use(params);
  const router = useRouter();
  const [analysis, setAnalysis] = useState<AnalysisResponse | null>(null);
  const [rewrite, setRewrite] = useState<RewriteDraftResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRewriting, setIsRewriting] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [customPrompt, setCustomPrompt] = useState("");
  const [showPromptInput, setShowPromptInput] = useState(false);

  async function loadAnalysis() {
    setIsLoading(true);
    setError(null);
    try {
      setAnalysis(await getAnalysis(analysisId));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "分析报告加载失败。");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadAnalysis();
  }, [analysisId]);

  async function handleRewrite() {
    if (!analysis) {
      return;
    }
    setIsRewriting(true);
    setError(null);
    try {
      setRewrite(await createRewrite({
        analysisId: analysis.id,
        sectionId: "analysis-report",
        customPrompt: customPrompt.trim() || undefined,
      }));
      setShowPromptInput(false);
    } catch (rewriteError) {
      setError(rewriteError instanceof Error ? rewriteError.message : "改写生成失败。");
    } finally {
      setIsRewriting(false);
    }
  }

  async function handleDelete() {
    if (!analysis) {
      return;
    }
    setIsDeleting(true);
    setError(null);
    try {
      await deleteAnalysis(analysis.id);
      setShowDeleteDialog(false);
      router.push("/dashboard");
      router.refresh();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "分析报告删除失败。");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <AppShell
      actions={
        <>
          <ButtonLink href="/dashboard" tone="paper">工作台</ButtonLink>
          <ButtonLink href="/upload" tone="gold">继续定制</ButtonLink>
        </>
      }
      description="按照参考项目的报告视角，把分数、关键词缺口、证据链、RAG 建议和智能体改写动作放在同一张检查单里。"
      eyebrow="分析报告"
      title="匹配报告不是结论，是改写前的证据清单。"
    >
      {error ? <p className="mb-6 border border-black bg-[#dc2626] p-4 font-mono text-sm font-bold uppercase text-white shadow-sw-sm">{error}</p> : null}
      {isLoading ? <p className="border border-black bg-[#f0f0e8] p-6 font-mono font-bold uppercase shadow-sw-sm">正在加载分析报告...</p> : null}

      {analysis ? (
        <div className="space-y-6">
          <section className="grid gap-6 lg:grid-cols-[0.85fr_1.15fr]">
            <Card tone="lime">
              <CardHeader
                action={
                  <div className="flex flex-wrap gap-3">
                    <span className="border border-black bg-[#f0f0e8] px-3 py-1 font-mono text-xs font-bold uppercase tracking-wide">{analysis.status}</span>
                    <Button disabled={isDeleting} onClick={() => setShowDeleteDialog(true)} tone="danger" type="button">
                      {isDeleting ? "删除中" : "删除报告"}
                    </Button>
                  </div>
                }
                eyebrow="评分"
                title="整体匹配"
                description={`创建时间：${formatDateTime(analysis.createdAt)}。分数来自关键词及规则估算，未进行真实语义或招聘方 ATS 测试。`}
              />
              <div className="mt-5">
                {analysis.report.keywordEvaluation?.available === false ? <p>没有可计分技术项，无法评估。</p> : <ScoreRing label="规则综合" score={analysis.overallScore} />}
                <KeywordEvaluationNotes analysis={analysis} />
              </div>
            </Card>
            <Card tone="paper">
              <CardHeader
                action={
                  <div className="flex flex-wrap gap-2">
                    <Button onClick={() => setShowPromptInput(!showPromptInput)} tone="paper" type="button">
                      {showPromptInput ? "收起" : "自定义提示词"}
                    </Button>
                    <Button disabled={isRewriting} onClick={handleRewrite} tone="ink" type="button">
                      {isRewriting ? "改写中" : "创建改写"}
                    </Button>
                  </div>
                }
                eyebrow="下一步"
                title="下一步：生成可确认的改写草稿"
                description="智能体会使用分析结果、缺失关键词和 RAG 建议生成中文段落改写，并附带事实校验 JSON。"
              />
              {showPromptInput ? (
                <div className="mt-5 border border-black bg-[#e5e5e0] p-4 shadow-sw-xs">
                  <label className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]" htmlFor="custom-prompt">
                    自定义改写提示词
                  </label>
                  <textarea
                    className="mt-2 w-full resize-none border border-black bg-white p-3 font-mono text-sm leading-6 focus:outline-none focus:ring-2 focus:ring-[#1d4ed8]"
                    id="custom-prompt"
                    placeholder="例如：请突出我的团队管理经验和项目交付成果..."
                    rows={3}
                    value={customPrompt}
                    onChange={(e) => setCustomPrompt(e.target.value)}
                  />
                  <p className="mt-1 font-mono text-[10px] uppercase leading-4 text-[#6b7280]">此提示词将附加到 AI 改写指令中，用于指导改写方向。</p>
                </div>
              ) : null}
              <div className="mt-5">
                <AnalysisPreviewStrip analysis={analysis} />
              </div>
              {rewrite ? (
                <div className="mt-5 border border-black bg-[#e5e5e0] p-4 shadow-sw-xs">
                  <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">草稿已生成</p>
                  <p className="mt-2 line-clamp-3 text-sm leading-6">{rewrite.rewrittenText}</p>
                  <ButtonLink className="mt-4" href={`/rewrites/${rewrite.id}`} tone="ink">打开草稿</ButtonLink>
                </div>
              ) : null}
            </Card>
          </section>

          <section className="grid gap-6 lg:grid-cols-2">
            <Card tone="paper">
              <CardHeader eyebrow="已命中" title="字面命中关键词" description="原词或受控别名出现在简历中，实际能力仍需结合项目经历核对。" />
              <div className="mt-5">
                <KeywordCloud keywords={analysis.report.matchedKeywords} tone="matched" />
              </div>
            </Card>
            <Card tone="gold">
              <CardHeader eyebrow="待补齐" title="缺失关键词" description="优先判断是否有真实经历可补充，没有证据时只做表达重构，不编造。" />
              <div className="mt-5">
                <KeywordCloud keywords={analysis.report.missingKeywords} tone="missing" />
              </div>
            </Card>
          </section>

          <Card tone="paper">
            <CardHeader eyebrow="建议" title="优化建议" description="建议会尽量保持中文、可执行、面向简历段落修改。" />
            <ol className="mt-5 grid gap-3 md:grid-cols-2">
              {analysis.report.suggestions.map((suggestion, index) => (
                <li className="border border-black bg-[#f0f0e8] p-4 font-mono text-xs uppercase leading-5 shadow-sw-xs" key={`${suggestion}-${index}`}>
                  <span className="mr-2 font-mono text-xs font-black">#{index + 1}</span>
                  {suggestion}
                </li>
              ))}
            </ol>
          </Card>

          <Card tone="sky">
            <CardHeader eyebrow="证据" title="证据映射" description="参考项目强调可预览差异，我们这里先把每个关键词和证据关系展开。" />
            <div className="mt-5">
              <EvidenceMatrix evidence={analysis.report.evidenceMap} />
            </div>
          </Card>

          <Card tone="ink">
            <CardHeader eyebrow="RAG" title="检索到的简历优化规则" description="这些内容来自 PGvector 知识库，会成为提示词上下文的一部分。" />
            <div className="mt-5 grid gap-3 md:grid-cols-2">
              {analysis.report.retrievedGuidance.length > 0 ? (
                analysis.report.retrievedGuidance.map((guidance, index) => (
                  <p className="border border-white/80 bg-white/10 p-4 font-mono text-xs uppercase leading-5 text-white/80" key={`${guidance}-${index}`}>{guidance}</p>
                ))
              ) : (
                <p className="border border-white/50 bg-white/10 p-5 font-mono text-xs uppercase leading-5 text-white/75">暂无 RAG 建议。可以先进入知识库创建并索引文档。</p>
              )}
            </div>
          </Card>
        </div>
      ) : null}
      {analysis ? (
        <ConfirmDialog
          description="将删除这份分析报告；基于它生成的改写草稿也会一起删除。"
          isOpen={showDeleteDialog}
          isWorking={isDeleting}
          onCancel={() => setShowDeleteDialog(false)}
          onConfirm={() => void handleDelete()}
          title="删除这份报告？"
        />
      ) : null}
    </AppShell>
  );
}
