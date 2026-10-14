"use client";
import { useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { Card } from "@/components/ui/card";
import { compass, Application, ActivityEvent, statusLabel } from "@/lib/api/compass";
export default function ApplicationsPage() {
  const [apps, setApps] = useState<Application[]>([]), [edits, setEdits] = useState<Record<string, { status: string; note: string }>>({}), [events, setEvents] = useState<ActivityEvent[]>([]), [error, setError] = useState(""), [busy, setBusy] = useState(false);
  useEffect(() => { compass<Application[]>("/applications").then(setApps).catch(e => setError(e.message)); }, []);
  return <AppShell title="投递记录" eyebrow="状态与变更追踪" description="确认准备计划后创建岗位记录，投递与面试状态由你更新。已有记录不会因新计划被重置。">
    <Link className="text-blue-700 underline" href="/compass">返回求职罗盘</Link><button className="ml-4 underline" disabled={busy} onClick={() => { setEdits({}); compass<Application[]>("/applications").then(setApps).catch(e => setError(e.message)); }}>刷新记录</button>
    {error && <p role="alert" className="bg-red-50 p-4 text-red-800">{error}。请刷新记录后重试。</p>}{apps.length === 0 && <Card>确认一份准备计划后，这里会出现对应岗位。</Card>}
    {apps.map(a => { const edit = edits[a.id] || { status: a.status, note: a.note }; return <Card tone="paper" key={a.id}><h2 className="text-2xl font-bold">{a.company} · {a.title}</h2><p className="my-3">当前：{statusLabel[a.status]} · 版本 {a.version}</p>
      <div className="grid gap-3 md:grid-cols-[1fr_2fr_auto]"><select aria-label={`投递状态 ${a.company}`} className="border border-black p-3" disabled={busy} value={edit.status} onChange={e => setEdits({ ...edits, [a.id]: { ...edit, status: e.target.value } })}>{["PLANNED","APPLIED","INTERVIEW","OFFER","REJECTED","WITHDRAWN"].map(s => <option key={s} value={s}>{statusLabel[s]}</option>)}</select>
        <input aria-label={`投递备注 ${a.company}`} className="border border-black p-3" placeholder="备注" maxLength={1000} value={edit.note} onChange={e => setEdits({ ...edits, [a.id]: { ...edit, note: e.target.value } })}/>
        <button disabled={busy} className="border border-black bg-blue-700 p-3 text-white disabled:opacity-40" onClick={async () => { setBusy(true); setError(""); try { const updated = await compass<Application>(`/applications/${a.id}`, "PATCH", { ...edit, expectedVersion: a.version }); setApps(apps.map(item => item.id === a.id ? updated : item)); const rest = { ...edits }; delete rest[a.id]; setEdits(rest); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }}>保存状态</button></div>
      <button className="mt-3 text-blue-700 underline" onClick={() => compass<ActivityEvent[]>(`/activity-events?resourceId=${a.id}`).then(setEvents).catch(e => setError(e.message))}>查看该岗位变更历史</button>
    </Card>; })}
    {events.length > 0 && <Card><h2 className="text-xl font-bold">岗位变更历史</h2>{events.map(e => <p className="mt-2 text-sm" key={e.id}>{new Date(e.occurredAt).toLocaleString()} · {statusLabel[e.oldValue || ""] || "新建"} → {statusLabel[e.newValue] || e.newValue}</p>)}</Card>}
  </AppShell>;
}
