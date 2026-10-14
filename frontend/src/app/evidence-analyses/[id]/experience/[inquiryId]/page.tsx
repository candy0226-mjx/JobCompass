"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { Card } from "@/components/ui/card";
import { compass, ExperienceDraft, ExperienceSession } from "@/lib/api/compass";

const focusLabel: Record<string, string> = { SCENARIO: "业务场景", IMPLEMENTATION: "实际实现与职责", VALIDATION: "验证与结果" };
const phaseLabel: Record<ExperienceSession["status"], string> = { QUESTIONS_PENDING: "正在准备追问", ANSWERING: "等待补充经历", CARD_DRAFT: "证据卡待确认", CONFIRMED: "已确认补充", FAILED: "追问生成失败" };
const buttonClass = "border border-black px-4 py-2 font-bold disabled:cursor-not-allowed disabled:opacity-40";

function CardContent({ draft }: { draft: ExperienceDraft }) {
  return <div className="space-y-4">
    <h2 className="text-xl font-bold">{draft.title}</h2>
    {draft.facts.map(f => <div key={f.questionId}><h3 className="font-bold">{focusLabel[f.focus]}</h3><blockquote className="mt-2 whitespace-pre-wrap break-words border-l-4 border-blue-700 pl-3">{f.quote}</blockquote><p className="mt-1 text-xs text-gray-600">来源：你的补充回答 · {f.questionId}</p></div>)}
    <p className="text-sm">没有回答的部分保持缺失。确认仅表示你认可这些补充符合真实情况，不代表系统已核实能力或修改了原报告。</p>
  </div>;
}

export default function ExperiencePage({ params }: { params: Promise<{ id: string; inquiryId: string }> }) {
  const { id, inquiryId } = use(params);
  const base = `/evidence-analyses/${id}/experience-inquiries/${inquiryId}`;
  const [session, setSession] = useState<ExperienceSession | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmTruth, setConfirmTruth] = useState(false);

  function receive(next: ExperienceSession) {
    setSession(next);
    setAnswers(Object.fromEntries(next.questions.map(q => [q.id, next.answers.find(a => a.questionId === q.id)?.text || ""])));
    setConfirmTruth(false);
  }

  useEffect(() => {
    let active = true;
    compass<ExperienceSession>(base).then(next => { if (active) receive(next); }).catch(e => { if (active) setError((e as Error).message); });
    return () => { active = false; };
  }, [base]);

  useEffect(() => {
    if (session?.status !== "QUESTIONS_PENDING") return;
    let active = true;
    const timer = setInterval(() => {
      compass<ExperienceSession>(base).then(next => { if (active) { receive(next); setError(""); } }).catch(e => { if (active) setError((e as Error).message); });
    }, 2000);
    return () => { active = false; clearInterval(timer); };
  }, [base, session?.status]);

  async function act(path: string, method: string, body: unknown, successMessage: string) {
    setBusy(true); setError(""); setMessage("");
    try { receive(await compass<ExperienceSession>(`${base}${path}`, method, body)); setMessage(successMessage); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }

  const dirty = !!session && session.questions.some(q => (answers[q.id] || "") !== (session.answers.find(a => a.questionId === q.id)?.text || ""));
  const hasAnswer = Object.values(answers).some(text => text.trim().length > 0);
  const editable = session?.status === "ANSWERING" || session?.status === "CARD_DRAFT";
  const latestCard = session?.cards[0];
  const cardText = latestCard ? [`# 经历证据卡：${latestCard.content.title}`, "", "来源：用户确认补充；未进行独立能力核验。", `原报告 ID：${id}`, `原简历版本 ID：${latestCard.resumeVersionId}`, "", ...latestCard.content.facts.flatMap(f => [`## ${focusLabel[f.focus]}`, f.quote, ""]), "原模型报告保持不变；用于简历前请核对真实经历。"].join("\n") : "";

  return <AppShell title="经历追问与证据卡" eyebrow="补充真实经历" description="根据岗位要求追问业务场景、实际做法与验证结果。你可以如实回答没有做过、不记得或未验证。">
    <Link href={`/evidence-analyses/${id}`} className="font-bold text-blue-700 underline">返回原证据报告</Link>
    {error && <p role="alert" className="bg-red-50 p-4 text-red-800">{error}</p>}
    {message && <p role="status" className="bg-green-50 p-4 text-green-800">{message}</p>}
    {!session && !error && <p>正在读取追问记录…</p>}
    {session && <p className="font-bold">{phaseLabel[session.status]}</p>}
    {session?.status === "QUESTIONS_PENDING" && <Card><p>AI 正在结合原简历和这一条岗位要求准备问题。可以离开页面，稍后返回继续。</p></Card>}
    {session?.status === "FAILED" && <Card><p>生成失败：{session.errorCode}。检查模型服务后可重试，仍使用原报告的固定资料。</p><button disabled={busy} className={`${buttonClass} mt-3`} onClick={() => act("/retry", "POST", { expectedVersion: session.version }, "已重新提交追问")}>重试生成追问</button></Card>}

    {!!session?.questions.length && <Card>
      <div className="space-y-5">{session.questions.map(q => <label key={q.id} className="block"><span className="block font-bold">{focusLabel[q.focus]}</span><span className="my-2 block">{q.question}</span>
        <textarea aria-label={focusLabel[q.focus]} disabled={!editable || busy} className="min-h-28 w-full border border-black bg-white p-3 disabled:bg-gray-100" maxLength={2000} value={answers[q.id] || ""} placeholder="填写实际情况；可以留空，或说明尚不清楚。不要补写没有依据的数字。" onChange={e => { setAnswers(previous => ({ ...previous, [q.id]: e.target.value })); setConfirmTruth(false); setMessage(""); }}/>
      </label>)}</div>
      {editable && <div className="mt-4 flex flex-wrap gap-3">
        <button disabled={busy || !dirty} className={buttonClass} onClick={() => act("/answers", "PUT", { expectedVersion: session.version, answers: session.questions.map(q => ({ questionId: q.id, text: answers[q.id] || "" })) }, "回答已保存；原报告没有改变")}>保存回答</button>
        <button disabled={busy || dirty || !hasAnswer} className={`${buttonClass} bg-blue-700 text-white`} onClick={() => act("/draft", "POST", { expectedVersion: session.version }, "证据卡草稿已生成，请核对原文")}>生成证据卡草稿</button>
        {dirty && <p className="self-center text-sm">请先保存修改后的回答。</p>}
      </div>}
    </Card>}

    {session?.status === "CARD_DRAFT" && session.draft && <Card tone="sky">
      <p className="mb-4 font-bold text-blue-700">待确认 · 用户补充原文</p>
      <CardContent draft={session.draft}/>
      <label className="mt-5 flex items-start gap-2"><input type="checkbox" checked={confirmTruth} disabled={busy || dirty} onChange={e => setConfirmTruth(e.target.checked)}/><span>我已核对这些回答符合真实情况，未验证或不记得的部分已如实保留。</span></label>
      <button disabled={busy || dirty || !confirmTruth} className={`${buttonClass} mt-4 bg-blue-700 text-white`} onClick={() => act("/confirm", "POST", { expectedVersion: session.version, confirmTruth }, "证据卡已确认保存；原简历和原判断保持不变")}>确认并保存证据卡</button>
    </Card>}

    {session?.status === "CONFIRMED" && latestCard && <Card>
      <p className="mb-4 font-bold text-green-800">已确认 · 来源为用户补充，未进行独立能力核验</p>
      <CardContent draft={latestCard.content}/>
      <div className="mt-5 flex flex-wrap gap-3">
        <button disabled={busy} className={buttonClass} onClick={async () => { try { await navigator.clipboard.writeText(cardText); setMessage("已复制，使用前请保留真实经历及限制条件"); } catch { setError("复制失败，可直接选中页面中的证据内容复制。"); } }}>复制证据卡</button>
        <button disabled={busy} className={buttonClass} onClick={() => { const url = URL.createObjectURL(new Blob([cardText], { type: "text/markdown;charset=utf-8" })); const link = document.createElement("a"); link.href = url; link.download = `experience-card-${latestCard.id}.md`; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }}>下载 Markdown</button>
        <button disabled={busy} className={buttonClass} onClick={() => act("/reopen", "POST", { expectedVersion: session.version }, "可以修改回答；上一张确认卡保留为历史记录")}>重新编辑回答</button>
      </div>
      <p className="mt-4 text-sm">需要补进简历时，在求职罗盘创建新版正文后重新分析。保存证据卡不会自动把要求改成匹配。</p>
      <Link href="/compass" className="mt-2 inline-block font-bold text-blue-700 underline">回工作台更新简历</Link>
    </Card>}

    {!!session?.cards.length && <Card><details><summary className="cursor-pointer font-bold">历史确认卡（{session.cards.length} 张，仅供追溯）</summary><div className="mt-4 space-y-6">{session.cards.map(card => <div key={card.id} className="border-t border-black pt-4"><p className="mb-3 text-sm">确认时间：{new Date(card.confirmedAt).toLocaleString("zh-CN")}</p><CardContent draft={card.content}/></div>)}</div></details></Card>}
    {error && <button disabled={busy} className={buttonClass} onClick={async () => { setBusy(true); try { receive(await compass<ExperienceSession>(base)); setError(""); setMessage("已加载服务器保存的回答"); } catch (e) { setError((e as Error).message); } finally { setBusy(false); } }}>重新加载已保存的回答</button>}
  </AppShell>;
}
