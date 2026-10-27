import { Card } from "@/components/ui/card";
import { EvidenceScore, statusLabel } from "@/lib/api/compass";

export function EvidenceScorePanel({ score }: { score?: EvidenceScore | null }) {
  if (!score) return <Card><p>该报告没有证据评分摘要。历史报告保持原结果，不自动重新计算。</p></Card>;
  return <Card>
    <h2 className="text-xl font-bold">简历证据覆盖</h2>
    {score.available ? <div className="my-4 grid gap-3 sm:grid-cols-3">
      <p className="rounded border p-3">有证据覆盖 <strong className="block text-3xl">{score.coverageScore}/100</strong></p>
      <p className="rounded border p-3">待确认权重 <strong className="block text-3xl">{score.uncertainPercent}%</strong></p>
      <p className="rounded border p-3">明确缺口权重 <strong className="block text-3xl">{score.gapPercent}%</strong></p>
    </div> : <p className="my-3 font-semibold">本次无法生成有效分数，请查看提取警告和逐项判断。</p>}
    <p className="text-sm">仅对提取并校验的技术/经验要求计算：必需组权重 3，加分组权重 1。ALL 子项共享组权重；ANY 只需一个分支有证据。待确认不等于不会。分数不表示实际能力或录用概率。</p>
    <p className="mt-2 text-xs text-slate-600">算法 {score.algorithmVersion} · 权重 {score.weightVersion} · 来源为模型原报告，人工复核与经历卡不自动改分。资格及岗位职责单独展示。</p>
    <details className="mt-4"><summary className="cursor-pointer font-semibold">查看计分依据与逐项权重</summary>
      <div className="mt-3 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">要求组及子项</th><th className="p-2">权重</th><th className="p-2">有证据 / 待确认 / 缺口</th></tr></thead>
        <tbody>{score.groups.map(g=><tr key={g.groupKey} className="border-t"><td className="p-2">{g.members.map(m=><p key={m.requirementId}>{m.text}：{statusLabel[m.status]}{m.weightShare !== null && `（权重 ${Number(m.weightShare.toFixed(3))}）`}</p>)}<p className="text-xs text-slate-500">{statusLabel[g.kind]} · {g.logic === "ANY" ? "任选一项" : "各子项分别核对"}</p></td><td className="p-2">{g.weight}</td><td className="p-2">{Number(g.matchedWeight.toFixed(3))} / {Number(g.uncertainWeight.toFixed(3))} / {Number(g.gapWeight.toFixed(3))}</td></tr>)}</tbody>
      </table></div>
      <p className="mt-2 text-xs">计分总权重 {score.totalWeight}；显示值四舍五入，比例之和可能有尾差。</p>
    </details>
    {!!score.excludedGroups.length && <div className="mt-4"><h3 className="font-semibold">资格条件与岗位职责（未计入分数）</h3>{score.excludedGroups.map(g=><div className="mt-2 border-l-4 border-slate-300 pl-3 text-sm" key={g.groupKey}><p>{g.category === "QUALIFICATION" ? "资格条件" : "岗位职责"} · {statusLabel[g.status]}</p>{g.members.map(m=><p key={m.requirementId}>{m.text}：{statusLabel[m.status]}</p>)}</div>)}</div>}
    {!!score.warnings.length && <ul className="mt-4 list-disc space-y-1 pl-5 text-xs text-slate-600">{score.warnings.map(w=><li key={w}>{w}</li>)}</ul>}
  </Card>;
}
