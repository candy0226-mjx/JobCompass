"use client";
import { use, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { Card } from "@/components/ui/card";
import { compass, ExperienceSession, Run, statusLabel } from "@/lib/api/compass";
export default function EvidencePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params); const [run, setRun] = useState<Run | null>(null), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  const [edits, setEdits] = useState<Record<string, { category: string; note: string; skillKey: string }>>({});
  useEffect(() => { let active = true; let timer: ReturnType<typeof setInterval>; const load = () => compass<Run>(`/evidence-analyses/${id}`).then(r => { if (active) { setRun(r); if (["SUCCEEDED", "FAILED"].includes(r.status)) clearInterval(timer); } }).catch(e => { if (active) setError(e.message); }); load(); timer = setInterval(load, 2000); return () => { active = false; clearInterval(timer); }; }, [id]);
  return <AppShell title={run?.job.title || "证据分析"} eyebrow="逐条核对与用户复核" description="引用存在只说明出处有效，能力判断仍需你核对。复核意见单独保存，不覆盖模型原始报告。">
    <Link href="/compass" className="font-bold text-blue-700 underline">返回求职罗盘</Link>
    {error && <p role="alert" className="bg-red-50 p-4 text-red-800">{error}</p>}
    {run && <p>{run.job.company} · {statusLabel[run.status]} · 工具读取 {run.toolCalls} 次 · {run.durationMs === null ? "等待结果" : `${(run.durationMs / 1000).toFixed(1)} 秒`}</p>}
    {run?.status === "FAILED" && <Card><p>失败类别：{run.errorCode}。本次没有有效报告。</p><button disabled={busy} className="mt-3 border border-black p-3" onClick={async () => { setBusy(true); try { const next = await compass<Run>(`/evidence-analyses/${id}/retry`, "POST"); window.location.href = `/evidence-analyses/${next.id}`; } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }}>创建新运行重试</button></Card>}
    {run?.report?.requirements.map(q => { const a = run.report!.assessments.find(x => x.requirementId === q.id)!; const review = run.reviews.find(x => x.requirementId === q.id); const edit = edits[q.id] || { category: review?.category || (a.status === "MATCH" ? "CONFIRMED_MATCH" : a.actionType === "LEARN" ? "LEARN" : "CLARIFY"), note: review?.note || "", skillKey: review?.skillKey || "" }; return <Card tone="paper" key={q.id}>
      <h2 className="text-xl font-bold">{statusLabel[q.kind]} · {q.text}</h2><p className="my-3 font-bold">{statusLabel[a.status]}：{a.reason}</p>
      <blockquote className="border-l-4 border-blue-700 p-3 text-sm">JD [{q.jdEvidence.segmentId}] {q.jdEvidence.quote}</blockquote>
      {a.resumeEvidence.length ? a.resumeEvidence.map((r, i) => <blockquote key={i} className="mt-2 border-l-4 border-green-700 p-3 text-sm">简历 [{r.segmentId}] {r.quote}</blockquote>) : <p className="mt-2 text-sm">没有足够的简历证据，需要核实。</p>}
      {a.actionType !== "NONE" && <p className="my-3 text-sm">候选任务：{a.taskTitle}；产物：{a.expectedOutput}；建议 {a.effortMinutes} 分钟（需要你确认）。</p>}
      <div className="mt-4 grid gap-3 md:grid-cols-[1fr_2fr_auto]"><select aria-label={`复核类别 ${q.text}`} className="border border-black p-2" value={edit.category} onChange={e => setEdits({ ...edits, [q.id]: { ...edit, category: e.target.value } })}><option value="CONFIRMED_MATCH">核实已有能力，无需准备任务</option><option value="CLARIFY">尚不确定，需要核实</option><option value="EVIDENCE_FIX">实际做过，补充经历表述</option><option value="LEARN">确实需要学习或实践</option></select>
        <input aria-label={`复核备注 ${q.text}`} className="border border-black p-2" placeholder="说明实际情况" maxLength={1000} value={edit.note} onChange={e => setEdits({ ...edits, [q.id]: { ...edit, note: e.target.value } })}/>
        <button disabled={busy} className="border border-black bg-blue-700 p-2 text-white disabled:opacity-40" onClick={async () => { setBusy(true); setError(""); try { setRun(await compass<Run>(`/evidence-analyses/${id}/reviews`, "PUT", { reviews: [{ requirementId: q.id, category: edit.category, note: edit.note, skillKey: edit.skillKey || null, expectedVersion: review?.version || 0 }] })); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }}>保存复核</button></div>
      <label className="mt-3 block text-sm">校正用于任务合并的技能（可选）<select className="ml-3 border border-black p-2" value={edit.skillKey} onChange={e => setEdits({ ...edits, [q.id]: { ...edit, skillKey: e.target.value } })}><option value="">保留原判断；未知技能不合并</option>{["redis.cache_consistency","redis.cache_penetration","redis.basics","springboot.api","java.core","javascript.core","database.unique_constraints","database.idempotency","postgresql.basics"].map(key => <option key={key} value={key}>{key}</option>)}</select></label>
      {review && <p className="mt-2 text-sm">已保存复核：{statusLabel[review.category]}（版本 {review.version}）</p>}
      <div className="mt-5 border-t border-black pt-4"><p className="mb-2 text-sm">做过但没有写清楚？围绕这项要求回答三个问题，整理一张标明来源的经历证据卡。</p>
        <button disabled={busy} className="border border-black p-3 font-bold disabled:opacity-40" onClick={async () => { setBusy(true); setError(""); try { const inquiry = await compass<ExperienceSession>(`/evidence-analyses/${id}/experience-inquiries`, "POST", { requirementId: q.id }); window.location.href = `/evidence-analyses/${id}/experience/${inquiry.id}`; } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }}>追问并整理经历</button>
      </div>
    </Card>; })}
  </AppShell>;
}
