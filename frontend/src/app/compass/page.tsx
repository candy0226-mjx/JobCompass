"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { Card } from "@/components/ui/card";
import { compass, Plan, Run, Version, statusLabel } from "@/lib/api/compass";

type ResumeItem = { id: string; title: string };
type JobItem = { id: string; title: string; company: string };
const field = "mt-2 w-full rounded-lg border border-[var(--border)] bg-white p-3 text-sm";
const button = "rounded-lg border border-[var(--primary)] bg-[var(--primary)] px-4 py-3 text-sm font-medium text-white transition-colors hover:bg-slate-700 disabled:opacity-50";
export default function CompassPage() {
  const [resumes, setResumes] = useState<ResumeItem[]>([]), [jobs, setJobs] = useState<JobItem[]>([]);
  const [resumeId, setResumeId] = useState(""), [versions, setVersions] = useState<Version[]>([]), [versionId, setVersionId] = useState("");
  const [selectedJobs, setSelectedJobs] = useState<string[]>([]), [runs, setRuns] = useState<Run[]>([]);
  const [plans, setPlans] = useState<Plan[]>([]), [chosenRuns, setChosenRuns] = useState<string[]>([]), [hours, setHours] = useState(8), [priorities, setPriorities] = useState<Record<string, number>>({});
  const [title, setTitle] = useState(""), [text, setText] = useState(""), [company, setCompany] = useState(""), [jobTitle, setJobTitle] = useState(""), [jd, setJd] = useState(""), [deadline, setDeadline] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  async function refresh() { const [r, j, a, p] = await Promise.all([compass<ResumeItem[]>("/resumes"), compass<JobItem[]>("/jobs"), compass<Run[]>("/evidence-analyses"), compass<Plan[]>("/preparation-plans")]); setResumes(r); setJobs(j); setRuns(a); setPlans(p); }
  useEffect(() => { setChosenRuns([]); }, [versionId]);
  useEffect(() => { refresh().catch(e => setError(e.message)); }, []);
  useEffect(() => {
    let active = true; setVersionId(""); setVersions([]);
    if (resumeId) compass<Version[]>(`/resumes/${resumeId}/versions`).then(async list => {
      if (!list.length) list = [await compass<Version>(`/resumes/${resumeId}/versions/import`, "POST")];
      if (active) { setVersions(list); setVersionId(list[0].id); }
    }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [resumeId]);
  useEffect(() => {
    if (!runs.some(r => ["PENDING", "RUNNING"].includes(r.status))) return;
    const timer = setInterval(() => compass<Run[]>("/evidence-analyses").then(setRuns).catch(e => setError(e.message)), 2000);
    return () => clearInterval(timer);
  }, [runs]);
  async function action(fn: () => Promise<void>) { setBusy(true); setError(""); try { await fn(); } catch (e) { setError(e instanceof Error ? e.message : "操作失败"); } finally { setBusy(false); } }
  const current = versions.find(v => v.id === versionId);
  return <AppShell title="求职罗盘" eyebrow="证据分析与准备计划" description="选择一版简历与最多三个岗位，核对真实经历，再安排有限的准备时间。真实简历正文会发送到你配置的模型服务。">
    {error && <p role="alert" className="border border-red-700 bg-red-50 p-4 text-red-800">{error}</p>}
    <ol aria-label="求职准备流程" className="grid grid-cols-2 gap-4 rounded-xl border border-[var(--border)] bg-white p-4 sm:grid-cols-4">
      {["简历版本", "目标岗位", "证据复核", "准备计划"].map((step, index) => <li key={step} className="jc-step-label"><span className="jc-step-number">{index + 1}</span>{step}</li>)}
    </ol>
    <section className="grid gap-6 lg:grid-cols-2">
      <Card tone="paper"><h2 className="mb-2 flex items-center gap-3 text-xl font-semibold"><span className="jc-step-number" aria-hidden="true">1</span>固定简历版本</h2><p className="mb-5 text-sm text-[var(--steel-grey)]">选择已有简历，或创建一份新的简历。</p>
        <label className="block">已有简历<select className={field} value={resumeId} onChange={e => setResumeId(e.target.value)}><option value="">请选择</option>{resumes.map(r => <option key={r.id} value={r.id}>{r.title}</option>)}</select></label>
        {versions.length > 0 && <label className="mt-3 block">分析版本<select className={field} value={versionId} onChange={e => setVersionId(e.target.value)}>{versions.map(v => <option value={v.id} key={v.id}>版本 {v.versionNo} · {v.hash.slice(0, 8)}</option>)}</select></label>}
        {current && <details className="my-3"><summary>查看固定正文与分段</summary>{current.segments.map(s => <p key={s.id} className="mt-2 whitespace-pre-wrap text-sm">[{s.id}] {s.text}</p>)}</details>}
        <div className="my-5 flex items-center gap-4 text-xs text-[var(--steel-grey)]"><hr className="flex-1"/>或创建新简历<hr className="flex-1"/></div><label className="block text-sm" htmlFor="resume-title">简历名称</label><input id="resume-title" aria-label="简历名称" placeholder="新简历名称" className={field} value={title} onChange={e => setTitle(e.target.value)}/>
        <label className="mt-4 block text-sm" htmlFor="resume-body">简历正文</label><textarea id="resume-body" aria-label="简历正文" placeholder="粘贴正文；或为已选简历保存新版本" className={`${field} h-40`} value={text} onChange={e => setText(e.target.value)} maxLength={20000}/>
        <div className="mt-3 flex flex-wrap gap-2"><button className={button} disabled={busy || !title.trim() || !text.trim()} onClick={() => action(async () => { const v = await compass<Version>("/resumes/text", "POST", { title, text }); await refresh(); setResumeId(v.resumeId); })}>创建文本简历</button>
          <button className={button} disabled={busy || !resumeId || !text.trim()} onClick={() => action(async () => { const v = await compass<Version>(`/resumes/${resumeId}/versions`, "POST", { text }); setVersions([v, ...versions]); setVersionId(v.id); })}>保存为新版本</button></div>
        <label className="mt-5 block rounded-lg border border-dashed border-[var(--border)] bg-slate-50/50 p-4 text-sm">上传 PDF<span className="mt-1 block text-xs text-[var(--steel-grey)]">名称必填 · 最大 5MB · 最多 10 页</span><input aria-label="上传PDF" className={`${field} mt-3`} type="file" accept=".pdf" disabled={busy || !title.trim()} onChange={e => { const file = e.target.files?.[0]; if (file) action(async () => { const form = new FormData(); form.append("file", file); form.append("title", title); const v = await compass<Version>("/compass/resumes/upload", "POST", form); await refresh(); setResumeId(v.resumeId); }); }}/></label>
      </Card>
      <Card tone="paper"><h2 className="mb-2 flex items-center gap-3 text-xl font-semibold"><span className="jc-step-number" aria-hidden="true">2</span>选择目标岗位</h2><p className="mb-5 text-sm text-[var(--steel-grey)]">最多选择三个岗位 · 已选择 {selectedJobs.length} / 3</p>
        <div className="max-h-56 space-y-2 overflow-auto">{jobs.map(j => <label className="flex gap-2 border border-black p-3" key={j.id}><input type="checkbox" checked={selectedJobs.includes(j.id)} disabled={!selectedJobs.includes(j.id) && selectedJobs.length >= 3} onChange={e => setSelectedJobs(e.target.checked ? [...selectedJobs, j.id] : selectedJobs.filter(id => id !== j.id))}/>{j.company} · {j.title}</label>)}</div>
        <div className="mt-4 grid grid-cols-2 gap-2"><input aria-label="公司" placeholder="公司" className={field} value={company} onChange={e => setCompany(e.target.value)}/><input aria-label="岗位名称" placeholder="岗位名称" className={field} value={jobTitle} onChange={e => setJobTitle(e.target.value)}/></div>
        <textarea aria-label="岗位JD" placeholder="岗位 JD 正文" className={`${field} mt-3 h-32`} value={jd} onChange={e => setJd(e.target.value)} maxLength={20000}/>
        <label className="mt-2 block text-sm">截止日期（可选）<input type="date" className={field} value={deadline} onChange={e => setDeadline(e.target.value)}/></label>
        <button className={`${button} mt-3`} disabled={busy || !company.trim() || !jobTitle.trim() || !jd.trim()} onClick={() => action(async () => { await compass("/compass/jobs", "POST", { title: jobTitle, company, text: jd, deadline: deadline || null }); await refresh(); setJd(""); })}>保存岗位</button>
        <button className={`${button} ml-3 mt-3`} disabled={busy || !versionId || !selectedJobs.length} onClick={() => action(async () => { for (const jobId of selectedJobs) await compass("/evidence-analyses", "POST", { resumeVersionId: versionId, jobId }); await refresh(); })}>开始证据分析</button>
      </Card>
    </section>
    <Card tone="panel"><h2 className="text-2xl font-bold">3. 分析报告</h2><p className="my-2 text-sm">成功报告可以逐条复核；不同版本的报告分别保留。</p>
      <div className="space-y-3">{runs.filter(r => !versionId || r.resumeVersionId === versionId).map(r => <div className="flex flex-wrap justify-between gap-2 border border-black p-3" key={r.id}><label className="flex items-center gap-2"><input aria-label={`选择报告 ${r.job.company}`} type="checkbox" checked={chosenRuns.includes(r.id)} disabled={!versionId || r.status !== "SUCCEEDED" || (!chosenRuns.includes(r.id) && (chosenRuns.length >= 3 || runs.some(x => chosenRuns.includes(x.id) && x.job.id === r.job.id)))} onChange={e => setChosenRuns(e.target.checked ? [...chosenRuns, r.id] : chosenRuns.filter(id => id !== r.id))}/>{r.job.company} · {r.job.title} · {statusLabel[r.status]} {r.errorCode && `(${r.errorCode})`}</label><Link className="font-bold text-blue-700 underline" href={`/evidence-analyses/${r.id}`}>查看证据与复核</Link></div>)}</div>
      <div className="mt-5 flex flex-wrap items-end gap-4"><label>本周准备时间（小时）<input aria-label="每周时间预算" type="number" min={0.5} max={40} step={0.5} className={field} value={hours} onChange={e => setHours(Number(e.target.value))}/></label>{runs.filter(r => chosenRuns.includes(r.id)).map(r => <label key={r.id}>{r.job.company} 的重要程度<select aria-label={`重要程度 ${r.job.company}`} className={field} value={priorities[r.job.id] || 1} onChange={e => setPriorities({ ...priorities, [r.job.id]: Number(e.target.value) })}><option value={1}>1 · 一般</option><option value={2}>2 · 重要</option><option value={3}>3 · 优先</option></select></label>)}<button className={button} disabled={busy || !chosenRuns.length || !versionId} onClick={() => action(async () => { const jobPriorities = Object.fromEntries(runs.filter(r => chosenRuns.includes(r.id)).map(r => [r.job.id, priorities[r.job.id] || 1])); const p = await compass<Plan>("/preparation-plans", "POST", { analysisIds: chosenRuns, weeklyBudgetMinutes: hours * 60, jobPriorities }); window.location.href = `/preparation-plans/${p.id}`; })}>生成准备计划草稿</button></div>
    </Card>
    <Card tone="paper"><h2 className="mb-3 text-2xl font-bold">已有准备计划</h2>{plans.length === 0 && <p>选择成功报告并生成第一份草稿。</p>}{plans.map(p => <Link key={p.id} href={`/preparation-plans/${p.id}`} className="mb-2 block border border-black p-3 text-blue-700">{p.sources.map(s => s.company).join(" / ")} · {statusLabel[p.status]} · 已安排 {p.allocatedMinutes / 60}h / {p.budgetMinutes / 60}h</Link>)}</Card>
  </AppShell>;
}
