import { AppShell } from "@/components/app-shell";
import { ButtonLink } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

export default function HomePage() {
  return (
    <AppShell
      actions={
        <>
          <ButtonLink href="/compass" tone="default">进入求职罗盘</ButtonLink>
          <ButtonLink href="/upload" tone="paper">开始定制</ButtonLink>
        </>
      }
      description="固定一版简历，核对最多三个目标岗位的要求，汇总共同准备事项，再按本周时间预算确认计划与跟踪进度。"
      eyebrow="求职罗盘"
      title="用真实经历，安排下一步求职准备。"
    >
      <section className="grid gap-5 md:grid-cols-3">
        {[
          ["逐条证据核对", "每项岗位要求展示原文出处、经历证据和不确定项，保留你的复核意见。"],
          ["共同准备事项", "汇总多个岗位的同类任务，查看一项准备关联哪些岗位，减少重复投入。"],
          ["预算与进度", "调整任务产物和预计耗时，在每周预算内确认计划，跟踪准备进度与投递状态。"],
        ].map(([title, body], index) => (
          <Card className="min-h-56" key={title} tone={index === 1 ? "panel" : "paper"}>
            <p className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">0{index + 1}</p>
            <h2 className="mt-5 text-3xl font-black leading-tight">{title}</h2>
            <p className="mt-4 font-mono text-xs uppercase leading-5 text-[#6b7280]">{body}</p>
          </Card>
        ))}
      </section>

      <section className="mt-8 grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
        <Card tone="ink">
          <p className="font-mono text-xs font-black uppercase tracking-[0.22em] text-white/60">Pipeline</p>
          <div className="mt-5 grid gap-3 md:grid-cols-4">
            {["固定版本", "核对证据", "确认计划", "跟踪进度"].map((step, index) => (
              <div className="border border-white/80 bg-white/10 p-4" key={step}>
                <p className="font-mono text-xs text-white/60">STEP {index + 1}</p>
                <p className="mt-3 text-xl font-black">{step}</p>
              </div>
            ))}
          </div>
        </Card>
        <Card tone="sky">
          <h2 className="text-3xl font-black">每项准备都有依据。</h2>
          <p className="mt-4 font-mono text-xs uppercase leading-5 text-[#6b7280]">
            AI 提供分析建议，你核实真实情况。任务完成后仍需提供新证据，重新分析岗位匹配；计划不会自动投递。
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <ButtonLink href="/knowledge" tone="paper">管理 RAG</ButtonLink>
            <ButtonLink href="/settings" tone="paper">检查配置</ButtonLink>
          </div>
        </Card>
      </section>
    </AppShell>
  );
}
