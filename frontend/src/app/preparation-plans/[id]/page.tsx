"use client";
import { use, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { Card } from "@/components/ui/card";
import { compass, Plan, Task, ActivityEvent, statusLabel } from "@/lib/api/compass";
const button = "border border-black bg-blue-700 px-4 py-3 text-white disabled:opacity-40";
const field = "w-full border border-black bg-white p-2 text-sm disabled:bg-gray-100";
export default function PlanPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params); const [plan, setPlan] = useState<Plan | null>(null), [tasks, setTasks] = useState<Task[]>([]), [hours, setHours] = useState(8), [error, setError] = useState(""), [busy, setBusy] = useState(false), [dirty, setDirty] = useState(false), [events, setEvents] = useState<ActivityEvent[]>([]);
  function apply(p: Plan) { setPlan(p); setTasks(p.tasks); setHours(p.budgetMinutes / 60); setDirty(false); }
  async function history(p: Plan) { setEvents(await compass<ActivityEvent[]>(`/preparation-plans/${p.id}/events`)); }
  useEffect(() => { let active = true; compass<Plan>(`/preparation-plans/${id}`).then(p => { if (active) { apply(p); history(p).catch(e => setError(e.message)); } }).catch(e => { if (active) setError(e.message); }); return () => { active = false; }; }, [id]);
  async function action(fn: () => Promise<Plan>) { setBusy(true); setError(""); try { const p = await fn(); apply(p); await history(p); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }
  function edit(id: string, update: Partial<Task>) { setTasks(tasks.map(t => t.id === id ? { ...t, ...update } : t)); setDirty(true); }
  const allocated = tasks.filter(t => t.selected).reduce((n, t) => n + t.effortMinutes, 0), draft = plan?.status === "DRAFT";
  return <AppShell title="岗位准备计划" eyebrow="共同事项 · 时间预算 · 确认执行" description="任务耗时是建议值，请按自己的情况调整。完成任务后仍需提供真实经历，再重新分析岗位匹配。">
    <div className="flex gap-4"><Link href="/compass" className="text-blue-700 underline">返回求职罗盘</Link><Link href="/applications" className="text-blue-700 underline">投递记录</Link><button disabled={busy} className="underline" onClick={() => action(() => compass<Plan>(`/preparation-plans/${id}`))}>刷新当前版本</button></div>
    {error && <p role="alert" className="bg-red-50 p-4 text-red-800">{error}。若发生版本冲突，请刷新后重新编辑。</p>}
    {plan && <Card tone="panel"><p className="text-xl font-bold">{statusLabel[plan.status]} · 版本 {plan.version}</p><div className="my-3 space-y-2">{plan.sources.map(s => <p key={s.analysisId}><Link className="text-blue-700 underline" href={`/evidence-analyses/${s.analysisId}`}>{s.company} · {s.title}</Link> · 重要程度 {s.priority}{s.deadline && ` · 截止 ${s.deadline}`}</p>)}</div>
      <label>本周预算（小时）<input aria-label="计划时间预算" className={`${field} mt-2 max-w-48`} type="number" min={0.5} max={40} step={0.5} disabled={!draft || busy} value={hours} onChange={e => { setHours(Number(e.target.value)); setDirty(true); }}/></label>
      <p className={`my-3 font-bold ${allocated > hours * 60 ? "text-red-700" : "text-blue-700"}`}>已选 {allocated / 60} 小时 / 预算 {hours} 小时 · 未安排 {tasks.filter(t => !t.selected).length} 项</p>
      {dirty && <p className="mb-3 text-sm">有未保存修改，请先保存草稿再确认。</p>}
      {draft && <div className="flex gap-3"><button className={button} disabled={busy || !dirty || allocated > hours * 60} onClick={() => action(() => compass<Plan>(`/preparation-plans/${id}`, "PATCH", { expectedVersion: plan.version, budgetMinutes: hours * 60, tasks: tasks.map(t => ({ id: t.id, title: t.title, expectedOutput: t.expectedOutput, effortMinutes: t.effortMinutes, selected: t.selected })) }))}>保存草稿调整</button>
        <button className={button} disabled={busy || dirty} onClick={() => action(async () => { const storageKey = `compass-confirm-${id}`; const key = sessionStorage.getItem(storageKey) || crypto.randomUUID(); sessionStorage.setItem(storageKey, key); return compass<Plan>(`/preparation-plans/${id}/confirm`, "POST", { idempotencyKey: key, expectedVersion: plan.version }); })}>确认准备计划</button></div>}
    </Card>}
    {plan && tasks.length === 0 && <Card><p>没有待准备任务。你可以确认这份计划，并在投递记录中管理岗位状态。</p></Card>}
    {tasks.map(t => <Card key={t.id} tone={t.selected ? "paper" : "panel"}><div className="mb-3 flex flex-wrap justify-between gap-2"><label className="font-bold"><input className="mr-2" type="checkbox" aria-label={`安排任务 ${t.title}`} checked={t.selected} disabled={!draft || busy} onChange={e => edit(t.id, { selected: e.target.checked })}/>{t.selected ? "已安排" : "待安排"} · {statusLabel[t.actionType]}</label><span>关联 {t.jobIds.length} 个岗位 · {statusLabel[t.status]}</span></div>
      <label className="block text-sm">任务标题<input aria-label={`任务标题 ${t.id}`} className={`${field} mt-1`} maxLength={200} disabled={!draft || busy} value={t.title} onChange={e => edit(t.id, { title: e.target.value })}/></label>
      <label className="mt-3 block text-sm">可检查产物<textarea aria-label={`任务产物 ${t.id}`} className={`${field} mt-1`} maxLength={500} disabled={!draft || busy} value={t.expectedOutput} onChange={e => edit(t.id, { expectedOutput: e.target.value })}/></label>
      <label className="mt-3 block text-sm">预计投入（分钟）<input aria-label={`任务耗时 ${t.id}`} className={`${field} mt-1 max-w-40`} type="number" step={30} min={30} max={960} disabled={!draft || busy} value={t.effortMinutes} onChange={e => edit(t.id, { effortMinutes: Number(e.target.value) })}/></label>
      <p className="my-3 text-sm">{t.explanation}</p><p className="text-sm">来源：{plan?.sources.filter(s => t.jobIds.includes(s.jobId)).map(s => `${s.company} · ${s.title}`).join("；")}</p>
      {plan?.status === "CONFIRMED" && t.selected && <label className="mt-4 block">更新准备进度<select aria-label={`准备进度 ${t.title}`} className={`${field} mt-1 max-w-48`} disabled={busy} value={t.status} onChange={e => action(() => compass<Plan>(`/preparation-plans/${id}/tasks/${t.id}`, "PATCH", { status: e.target.value, expectedVersion: t.version }))}>{([t.status, ...({ TODO: ["DOING", "SKIPPED"], DOING: ["TODO", "DONE", "SKIPPED"], DONE: ["TODO", "DOING"], SKIPPED: ["TODO"] } as Record<string, string[]>)[t.status]]).map(state => <option value={state} key={state}>{statusLabel[state]}</option>)}</select></label>}
    </Card>)}
    <Card tone="paper"><h2 className="text-xl font-bold">计划与任务变更记录</h2>{events.length === 0 && <p className="mt-3 text-sm">尚无变更记录。</p>}{events.map(e => <p key={e.id} className="mt-2 border-t border-black pt-2 text-sm">{new Date(e.occurredAt).toLocaleString()} · {e.resourceType} · {statusLabel[e.oldValue || ""] || e.oldValue || "新建"} → {statusLabel[e.newValue] || e.newValue}</p>)}</Card>
  </AppShell>;
}
