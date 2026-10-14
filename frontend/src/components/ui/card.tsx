import type { ReactNode } from "react";

type Tone = "paper" | "lime" | "ink" | "gold" | "sky" | "panel";

export function Card({
  children,
  className = "",
  tone = "paper",
}: {
  children: ReactNode;
  className?: string;
  tone?: Tone;
}) {
  const tones: Record<Tone, string> = {
    paper: "bg-white text-[var(--foreground)]",
    panel: "bg-white text-[var(--foreground)]",
    lime: "bg-[var(--accent-soft)] text-[var(--foreground)]",
    ink: "bg-[var(--primary)] text-white",
    gold: "bg-amber-50 text-[var(--foreground)]",
    sky: "bg-white text-[var(--foreground)]",
  };

  return (
    <section className={`min-w-0 rounded-xl border border-[var(--border)] p-5 shadow-sw-card md:p-6 ${tones[tone]} ${className}`}>
      {children}
    </section>
  );
}

export function CardHeader({
  action,
  eyebrow,
  title,
  description,
}: {
  action?: ReactNode;
  eyebrow?: string;
  title: string;
  description?: string;
}) {
  return (
    <div className="mb-4 flex flex-wrap items-start justify-between gap-4">
      <div>
        {eyebrow ? <p className="text-xs font-medium text-[var(--accent)]">{eyebrow}</p> : null}
        <h2 className="mt-2 text-xl font-semibold leading-7 tracking-tight text-current">{title}</h2>
        {description ? <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--steel-grey)]">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export function MetricCard({
  label,
  value,
  helper,
  tone = "paper",
}: {
  label: string;
  value: string | number;
  helper?: string;
  tone?: Tone;
}) {
  return (
    <Card className="p-5" tone={tone}>
      <p className="text-xs font-medium text-[var(--steel-grey)]">{label}</p>
      <p className="mt-3 break-words text-3xl font-semibold leading-none">{value}</p>
      {helper ? <p className="mt-3 text-xs leading-5 text-[var(--steel-grey)]">{helper}</p> : null}
    </Card>
  );
}
