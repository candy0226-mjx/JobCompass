import Link from "next/link";
import type { AnchorHTMLAttributes, ButtonHTMLAttributes, ReactNode } from "react";

type Tone = "default" | "ink" | "paper" | "lime" | "gold" | "sky" | "danger" | "success" | "warning";

const toneClasses: Record<Tone, string> = {
  default: "border-[var(--primary)] bg-[var(--primary)] text-white hover:bg-[#334155]",
  ink: "border-[var(--primary)] bg-[var(--primary)] text-white hover:bg-[#334155]",
  paper: "border-[var(--border)] bg-white text-[var(--foreground)] hover:bg-[var(--secondary)]",
  lime: "border-[var(--accent)] bg-[var(--accent)] text-white hover:bg-[#115e59]",
  gold: "border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100",
  sky: "border-teal-200 bg-[var(--accent-soft)] text-[var(--accent)] hover:bg-teal-100",
  danger: "border-red-200 bg-red-50 text-red-700 hover:bg-red-100",
  success: "border-teal-200 bg-[var(--accent-soft)] text-[var(--accent)] hover:bg-teal-100",
  warning: "border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100",
};

const baseClass =
  "relative inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border px-4 py-2.5 text-sm font-medium leading-5 transition-colors disabled:pointer-events-none disabled:opacity-50";

export function Button({
  children,
  className = "",
  tone = "paper",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { tone?: Tone; children: ReactNode }) {
  return (
    <button className={`${baseClass} ${toneClasses[tone]} ${className}`} {...props}>
      {children}
    </button>
  );
}

export function ButtonLink({
  children,
  className = "",
  tone = "paper",
  ...props
}: AnchorHTMLAttributes<HTMLAnchorElement> & { href: string; tone?: Tone; children: ReactNode }) {
  return (
    <Link className={`${baseClass} ${toneClasses[tone]} ${className}`} {...props}>
      {children}
    </Link>
  );
}
