import type { AnalysisResponse, RewriteCandidateResponse, RewriteDraftResponse } from "@/lib/api/client";
import { highlightText } from "@/lib/keywords";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";

export function JDResumeComparison({
  jobDescription,
  resumeText,
  keywords,
  analysis,
}: {
  jobDescription: string;
  resumeText: string;
  keywords: string[];
  analysis?: AnalysisResponse | null;
}) {
  const evaluation = analysis?.report.keywordEvaluation;
  const jobHighlights = evaluation?.terms.flatMap(t => t.jobOccurrences.map(o => o.original)) ?? keywords;
  const resumeHighlights = evaluation?.terms.flatMap(t => t.resumeOccurrences.map(o => o.original)) ?? keywords;

  return (
    <section className="overflow-hidden border border-black bg-[#f0f0e8] shadow-sw-card">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-black bg-[#e5e5e0] px-5 py-4">
        <div className="flex flex-wrap gap-3 font-mono text-xs font-bold uppercase tracking-wide">
          <span className="border border-black bg-[#1d4ed8] px-3 py-1 text-white">{analysis ? `JD 关键词 ${analysis.report.extractedKeywords.length}` : "文本预览"}</span>
          {analysis && <span className="border border-black bg-[#15803d] px-3 py-1 text-white">字面命中 {analysis.report.matchedKeywords.length}</span>}
        </div>
        <p className="font-mono text-sm font-bold uppercase tracking-wide text-[#1d4ed8]">{analysis ? evaluation?.available === false ? "没有可计分技术项" : `关键词覆盖 ${evaluation?.coverageScore ?? analysis.keywordScore}%（后端结果）` : "分析后显示正式覆盖率；高亮仅供预览"}</p>
      </div>
      <div className="grid min-h-[34rem] lg:grid-cols-2">
        <article className="border-b border-black lg:border-b-0 lg:border-r">
          <div className="border-b border-black bg-[#e5e5e0] px-5 py-3">
            <h3 className="font-mono text-sm font-bold uppercase tracking-wide">目标 JD</h3>
          </div>
          <p className="whitespace-pre-wrap p-5 font-mono text-sm leading-7">{highlightText(jobDescription, jobHighlights)}</p>
        </article>
        <article>
          <div className="border-b border-black bg-[#e5e5e0] px-5 py-3">
            <h3 className="font-mono text-sm font-bold uppercase tracking-wide">简历命中预览</h3>
          </div>
          <p className="whitespace-pre-wrap p-5 font-mono text-sm leading-7">
            {resumeText ? highlightText(resumeText, resumeHighlights) : "上传简历后显示解析文本。"}
          </p>
        </article>
      </div>
    </section>
  );
}

export function AnalysisPreviewStrip({ analysis }: { analysis: AnalysisResponse }) {
  const v2 = !!analysis.report.keywordEvaluation;
  return (
    <section className="grid gap-3 md:grid-cols-4" aria-label="关键词规则估算">
      {[
        ["规则综合", analysis.overallScore],
        ["关键词", analysis.keywordScore],
        [v2 ? "兼容语义字段（同覆盖率）" : "语义规则估算", analysis.semanticScore],
        [v2 ? "兼容 ATS 字段（同覆盖率）" : "ATS 规则估算", analysis.atsScore],
      ].map(([label, value]) => (
        <article className="border border-black bg-[#f0f0e8] p-4 shadow-sw-sm" key={label}>
          <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">{label}</p>
          <p className="mt-2 font-mono text-4xl font-bold">{analysis.report.keywordEvaluation?.available === false ? "—" : value}</p>
        </article>
      ))}
    </section>
  );
}

export function KeywordEvaluationNotes({ analysis }: { analysis: AnalysisResponse }) {
  const e = analysis.report.keywordEvaluation;
  return <div className="mt-4 space-y-2 text-sm">
    <p>算法：{e?.algorithmVersion ?? "legacy-keyword-v1"} · 关键词字面覆盖用于检查表达，不代表能力或录用概率。{e ? "规则综合等于关键词覆盖率；语义与 ATS 为兼容字段，返回相同数值，不是独立测量。" : "历史语义和 ATS 数值由旧规则公式估算。"}</p>
    {e && <p>词典：{e.dictionaryVersion}。仅统计已识别技术项；{e.available ? "分数不包含待识别候选。" : "没有可计分技术项，无法评估。"}</p>}
    {!!e?.unknownCandidates.length && <p>待识别候选（未计分）：{e.unknownCandidates.join("、")}</p>}
    {!!e?.corrections.length && <p>原 JD 纠错：{e.corrections.map(o => `${o.original} → ${o.canonical}`).join("；")}。原文保留。</p>}
    {e?.relatedCandidates.map(note => <p key={note}>{note}</p>)}
  </div>;
}

export function RewriteDiffPreview({ rewrite }: { rewrite: RewriteDraftResponse }) {
  return <CandidateDiffPreview rewrite={rewrite} candidate={null} />;
}

export function CandidateDiffPreview({
  rewrite,
  candidate,
  conversationHistory,
  onSave,
  onRegenerate,
  onAccept,
  onReject,
  isRegenerating,
  isApplying,
}: {
  rewrite: RewriteDraftResponse;
  candidate: RewriteCandidateResponse | null;
  conversationHistory?: string | null;
  onSave?: (rewrittenText: string) => Promise<void>;
  onRegenerate?: (message: string) => Promise<void>;
  onAccept?: () => Promise<void>;
  onReject?: (reason: string) => Promise<void>;
  isRegenerating?: boolean;
  isApplying?: boolean;
}) {
  const [isEditing, setIsEditing] = useState(false);
  const [editText, setEditText] = useState(candidate?.rewrittenText ?? rewrite.rewrittenText);
  const [regenMessage, setRegenMessage] = useState("");
  const [showRegenPanel, setShowRegenPanel] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    setEditText(candidate?.rewrittenText ?? rewrite.rewrittenText);
  }, [candidate?.rewrittenText, rewrite.rewrittenText]);

  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = "auto";
      el.style.height = `${el.scrollHeight}px`;
    }
  }, [editText, isEditing]);

  const displayText = candidate?.rewrittenText ?? (isEditing ? editText : rewrite.rewrittenText);
  const diff = useMemo(() => buildSideBySideDiff(rewrite.originalText, displayText), [rewrite.originalText, displayText]);
  const conversation = conversationHistory ?? candidate?.conversationHistory ?? rewrite.conversationHistory;

  async function handleRegenSubmit() {
    if (!regenMessage.trim() || !onRegenerate) {
      return;
    }
    await onRegenerate(regenMessage.trim());
    setRegenMessage("");
    setShowRegenPanel(false);
  }

  return (
    <section className="overflow-hidden border border-black bg-[#f0f0e8] shadow-sw-card">
      <div className="border-b border-black bg-black px-5 py-4 text-white">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h2 className="font-mono text-lg font-bold uppercase tracking-wide">改写差异预览</h2>
            <p className="mt-2 text-sm leading-6 text-white/75">
              {candidate
                ? "这是候选版本。先看差异，再决定是否接受或拒绝。"
                : isEditing
                  ? "左侧编辑 Markdown 内容，右侧实时预览渲染效果。"
                  : "左右两侧保留原始换行和排版；红色标记被删除或替换的原文，绿色标记新增或强化后的表达。"}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {!candidate && !isEditing && onSave ? (
              <>
                <Button onClick={() => setIsEditing(true)} tone="default" type="button">
                  手动编辑
                </Button>
                {onRegenerate ? (
                  <Button onClick={() => setShowRegenPanel(!showRegenPanel)} tone="paper" type="button">
                    {showRegenPanel ? "收起" : "对话重写"}
                  </Button>
                ) : null}
              </>
            ) : null}
            {candidate ? (
              <>
                <Button disabled={Boolean(isApplying)} onClick={() => void onAccept?.()} tone="success" type="button">
                  {isApplying ? "应用中..." : "接受候选"}
                </Button>
                <Button
                  disabled={Boolean(isApplying)}
                  onClick={() => void onReject?.("当前候选不合适，请继续调整。")}
                  tone="danger"
                  type="button"
                >
                  {isApplying ? "处理中..." : "拒绝候选"}
                </Button>
              </>
            ) : null}
          </div>
        </div>
      </div>

      {!isEditing && showRegenPanel && onRegenerate ? (
        <div className="border-b border-black bg-black/5 p-5">
          <ConversationHistory history={conversation} />
          <textarea
            className="mt-4 w-full resize-none border border-black bg-white p-4 font-mono text-sm leading-6 focus:outline-none focus:ring-2 focus:ring-[#1d4ed8]"
            placeholder="请输入你的修改要求，例如：请把第一段写得更简洁、突出项目成果..."
            rows={4}
            value={regenMessage}
            onChange={(e) => setRegenMessage(e.target.value)}
          />
          <div className="mt-4 flex justify-end gap-3">
            <Button disabled={Boolean(isRegenerating) || !regenMessage.trim()} onClick={handleRegenSubmit} tone="default" type="button">
              {isRegenerating ? "重写中..." : "提交要求，生成候选"}
            </Button>
          </div>
        </div>
      ) : null}

      <div className="grid lg:grid-cols-2">
        {isEditing && !candidate ? (
          <>
            <article className="flex flex-col border-b border-black lg:border-b-0 lg:border-r">
              <div className="flex items-center justify-between border-b border-black bg-[#1d4ed8] px-5 py-3 font-mono text-sm font-bold uppercase tracking-wide text-white">
                编辑区
              </div>
                <MarkdownToolbar onInsert={(before, after) => handleInsert(textareaRef, editText, setEditText, before, after)} />
              <textarea
                ref={textareaRef}
                className="min-h-80 w-full resize-none whitespace-pre-wrap border-0 bg-[#f0f0e8] p-5 font-mono text-sm leading-7 text-black focus:outline-none focus:ring-2 focus:ring-[#1d4ed8] focus:ring-inset"
                value={editText}
                onChange={(e) => setEditText(e.target.value)}
              />
              <div className="flex items-center justify-end gap-3 border-t border-black bg-[#e5e5e0] px-5 py-3">
                <Button
                  disabled={Boolean(isApplying)}
                  onClick={() => {
                    setEditText(rewrite.rewrittenText);
                    setIsEditing(false);
                  }}
                  tone="paper"
                  type="button"
                >
                  取消
                </Button>
                <Button
                    disabled={Boolean(isApplying) || !onSave || !editText.trim()}
                    onClick={async () => { await onSave?.(editText); setIsEditing(false); }}
                    tone="success"
                    type="button"
                >
                  保存修改
                </Button>
              </div>
            </article>
            <article>
              <div className="border-b border-black bg-[#15803d] px-5 py-3 font-mono text-sm font-bold uppercase tracking-wide text-white">
                实时预览
              </div>
              <div
                className="min-h-80 p-5 font-sans text-sm leading-7 [&_h1]:mb-3 [&_h1]:text-2xl [&_h1]:font-bold [&_h2]:mb-2 [&_h2]:text-xl [&_h2]:font-bold [&_h3]:mb-2 [&_h3]:text-lg [&_h3]:font-bold [&_p]:mb-2 [&_ul]:mb-2 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:mb-2 [&_ol]:list-decimal [&_ol]:pl-6 [&_li]:mb-1 [&_blockquote]:mb-2 [&_blockquote]:border-l-4 [&_blockquote]:border-[#1d4ed8] [&_blockquote]:bg-[#e5e5e0] [&_blockquote]:p-3 [&_blockquote]:italic [&_code]:rounded [&_code]:bg-[#e5e5e0] [&_code]:px-1 [&_code]:font-mono [&_code]:text-xs [&_pre]:mb-2 [&_pre]:overflow-x-auto [&_pre]:border [&_pre]:border-black [&_pre]:bg-[#e5e5e0] [&_pre]:p-4 [&_pre]:font-mono [&_pre]:text-xs [&_pre]:leading-5 [&_hr]:my-4 [&_hr]:border-t [&_hr]:border-black [&_a]:text-[#1d4ed8] [&_a]:underline [&_a]:hover:opacity-80"
                dangerouslySetInnerHTML={{ __html: renderMarkdownToHtml(editText) }}
              />
            </article>
          </>
        ) : (
          <>
            <article className="border-b border-black lg:border-b-0 lg:border-r">
              <div className="border-b border-black bg-[#dc2626] px-5 py-3 font-mono text-sm font-bold uppercase tracking-wide text-white">原文</div>
              <pre className="whitespace-pre-wrap p-5 font-mono text-sm leading-7">{diff.left}</pre>
            </article>
            <article>
              <div className="border-b border-black bg-[#15803d] px-5 py-3 font-mono text-sm font-bold uppercase tracking-wide text-white">
                {candidate ? "候选改写" : "改写后"}
              </div>
              <pre className="whitespace-pre-wrap p-5 font-mono text-sm leading-7">{diff.right}</pre>
            </article>
          </>
        )}
      </div>
    </section>
  );
}

function handleInsert(
  textareaRef: React.RefObject<HTMLTextAreaElement | null>,
  value: string,
  setValue: (next: string) => void,
  before: string,
  after: string,
) {
  const el = textareaRef.current;
  if (!el) {
    return;
  }
  const start = el.selectionStart;
  const end = el.selectionEnd;
  const selected = value.substring(start, end);
  const next = value.substring(0, start) + before + selected + after + value.substring(end);
  setValue(next);
  requestAnimationFrame(() => {
    el.focus();
    const pos = selected ? start + before.length + selected.length + after.length : start + before.length;
    el.setSelectionRange(pos, pos);
  });
}

function MarkdownToolbar({ onInsert }: { onInsert: (before: string, after: string) => void }) {
  const buttons = [
    { label: "B", title: "加粗", before: "**", after: "**" },
    { label: "I", title: "斜体", before: "*", after: "*" },
    { label: "H1", title: "一级标题", before: "\n# ", after: "" },
    { label: "H2", title: "二级标题", before: "\n## ", after: "" },
    { label: "H3", title: "三级标题", before: "\n### ", after: "" },
    { label: "•", title: "无序列表", before: "\n- ", after: "" },
    { label: "1.", title: "有序列表", before: "\n1. ", after: "" },
    { label: "`", title: "行内代码", before: "`", after: "`" },
    { label: "```", title: "代码块", before: "\n```\n", after: "\n```\n" },
    { label: ">", title: "引用", before: "\n> ", after: "" },
    { label: "🔗", title: "链接", before: "[", after: "](url)" },
    { label: "—", title: "分隔线", before: "\n---\n", after: "" },
  ];

  return (
    <div className="flex flex-wrap gap-1 border-b border-black bg-[#e5e5e0] px-3 py-2">
      {buttons.map(({ label, title, before, after }) => (
        <button
          className="border border-black bg-white px-2.5 py-1 font-mono text-xs font-bold uppercase tracking-wide shadow-sw-xs transition hover:translate-x-[1px] hover:translate-y-[1px] hover:bg-[#1d4ed8] hover:text-white hover:shadow-none active:translate-x-[2px] active:translate-y-[2px]"
          key={title}
          title={title}
          type="button"
          onClick={() => onInsert(before, after)}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

function renderMarkdownToHtml(text: string): string {
  const lines = text.split("\n");
  const out: string[] = [];
  let inList: "ul" | "ol" | null = null;
  const items: string[] = [];

  function closeList() {
    if (inList && items.length > 0) {
      out.push(`<${inList}>${items.join("")}</${inList}>`);
      items.length = 0;
    }
    inList = null;
  }

  for (let i = 0; i < lines.length; i++) {
    const raw = lines[i];
    if (raw.trimStart().startsWith("```")) {
      closeList();
      const codeLines: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trimStart().startsWith("```")) {
        codeLines.push(escapeHtml(lines[i]));
        i++;
      }
      out.push(`<pre><code>${codeLines.join("\n")}</code></pre>`);
      continue;
    }
    if (raw.trim() === "") {
      closeList();
      continue;
    }

    const trimmed = raw.trim();
    const h1 = trimmed.match(/^# (.+)/);
    if (h1) {
      closeList();
      out.push(`<h1>${applyInline(h1[1])}</h1>`);
      continue;
    }
    const h2 = trimmed.match(/^## (.+)/);
    if (h2) {
      closeList();
      out.push(`<h2>${applyInline(h2[1])}</h2>`);
      continue;
    }
    const h3 = trimmed.match(/^### (.+)/);
    if (h3) {
      closeList();
      out.push(`<h3>${applyInline(h3[1])}</h3>`);
      continue;
    }
    if (/^-{3,}$/.test(trimmed)) {
      closeList();
      out.push("<hr>");
      continue;
    }
    const bq = trimmed.match(/^> (.+)/);
    if (bq) {
      closeList();
      out.push(`<blockquote><p>${applyInline(bq[1])}</p></blockquote>`);
      continue;
    }
    const ul = trimmed.match(/^- (.+)/);
    if (ul) {
      if (inList !== "ul") {
        closeList();
        inList = "ul";
      }
      items.push(`<li>${applyInline(ul[1])}</li>`);
      continue;
    }
    const ol = trimmed.match(/^\d+\. (.+)/);
    if (ol) {
      if (inList !== "ol") {
        closeList();
        inList = "ol";
      }
      items.push(`<li>${applyInline(ol[1])}</li>`);
      continue;
    }

    closeList();
    out.push(`<p>${applyInline(raw)}</p>`);
  }

  closeList();
  return out.join("\n");
}

function applyInline(text: string): string {
  const escaped = escapeHtml(text);
  return escaped
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.+?)\*/g, "<em>$1</em>")
    .replace(/`(.+?)`/g, "<code>$1</code>")
    .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2" rel="noopener noreferrer" target="_blank">$1</a>');
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

type DiffPart = {
  text: string;
  type: "same" | "removed" | "added";
};

function buildSideBySideDiff(originalText: string, rewrittenText: string) {
  const originalTokens = tokenizeForDiff(originalText);
  const rewrittenTokens = tokenizeForDiff(rewrittenText);
  const rows = Array.from({ length: originalTokens.length + 1 }, () => Array(rewrittenTokens.length + 1).fill(0) as number[]);

  for (let i = originalTokens.length - 1; i >= 0; i -= 1) {
    for (let j = rewrittenTokens.length - 1; j >= 0; j -= 1) {
      rows[i][j] = originalTokens[i] === rewrittenTokens[j] ? rows[i + 1][j + 1] + 1 : Math.max(rows[i + 1][j], rows[i][j + 1]);
    }
  }

  const left: DiffPart[] = [];
  const right: DiffPart[] = [];
  let i = 0;
  let j = 0;

  while (i < originalTokens.length && j < rewrittenTokens.length) {
    if (originalTokens[i] === rewrittenTokens[j]) {
      left.push({ text: originalTokens[i], type: "same" });
      right.push({ text: rewrittenTokens[j], type: "same" });
      i += 1;
      j += 1;
    } else if (rows[i + 1][j] >= rows[i][j + 1]) {
      left.push({ text: originalTokens[i], type: "removed" });
      i += 1;
    } else {
      right.push({ text: rewrittenTokens[j], type: "added" });
      j += 1;
    }
  }

  while (i < originalTokens.length) {
    left.push({ text: originalTokens[i], type: "removed" });
    i += 1;
  }
  while (j < rewrittenTokens.length) {
    right.push({ text: rewrittenTokens[j], type: "added" });
    j += 1;
  }

  return {
    left: renderDiffParts(left, "removed"),
    right: renderDiffParts(right, "added"),
  };
}

function tokenizeForDiff(text: string) {
  return text.match(/\s+|[A-Za-z0-9_@./:+#-]+|[\u4e00-\u9fff]|[^\s]/g) ?? [];
}

function renderDiffParts(parts: DiffPart[], tone: "removed" | "added") {
  return parts.map((part, index) => {
    if (part.type === "same") {
      return part.text;
    }
    const className =
      tone === "removed"
        ? "bg-[#fecaca] text-[#7f1d1d] line-through decoration-[#dc2626] decoration-2"
        : "bg-[#bbf7d0] text-[#14532d] underline decoration-[#15803d] decoration-2";
    return (
      <mark className={`${className} px-0.5`} key={`${part.type}-${index}`}>
        {part.text}
      </mark>
    );
  });
}

function ConversationHistory({ history }: { history: string | null | undefined }) {
  if (!history || history === "[]") {
    return null;
  }
  let messages: Array<{ role: string; content: string }> = [];
  try {
    messages = JSON.parse(history) as Array<{ role: string; content: string }>;
  } catch {
    return null;
  }
  if (messages.length === 0) {
    return null;
  }
  return (
    <div className="space-y-3">
      <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#6b7280]">对话记录</p>
      <div className="max-h-48 space-y-2 overflow-y-auto">
        {messages.map((msg, index) => (
          <div
            className={`rounded border p-3 font-mono text-xs leading-5 ${
              msg.role === "user" ? "border-[#1d4ed8]/60 bg-[#1d4ed8]/10" : "border-black/20 bg-white/60"
            }`}
            key={index}
          >
            <span className="mr-2 font-bold uppercase tracking-wide">{msg.role === "user" ? "你" : "AI"}</span>
            {msg.content}
          </div>
        ))}
      </div>
    </div>
  );
}
