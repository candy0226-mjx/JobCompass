"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

const navItems = [
  { href: "/compass", label: "求职罗盘" },
  { href: "/applications", label: "投递记录" },
  { href: "/dashboard", label: "工作台" },
  { href: "/upload", label: "简历定制" },
  { href: "/knowledge", label: "知识库" },
  { href: "/settings", label: "设置" },
];

export function AppShell({
  actions,
  children,
  description,
  eyebrow,
  title,
}: {
  actions?: ReactNode;
  children: ReactNode;
  description?: string;
  eyebrow: string;
  title: string;
}) {
  const pathname = usePathname();

  return (
    <main className="jc-app min-h-screen w-full">
      <div className="flex min-h-screen w-full flex-col">
        <header className="relative z-30 border-b border-[var(--border)] bg-white">
          <div className="mx-auto flex max-w-[86rem] flex-wrap items-center justify-between gap-5 px-5 py-5 md:px-8">
            <Link className="flex items-center gap-3 text-[var(--foreground)]" href="/" aria-label="求职罗盘首页">
              <svg aria-hidden="true" className="h-10 w-10 shrink-0 text-[var(--accent)]" viewBox="0 0 40 40" fill="none">
                <circle cx="20" cy="20" r="17" stroke="currentColor" strokeWidth="2" />
                <path d="m27 13-4 10-10 4 4-10 10-4Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" />
                <circle cx="20" cy="20" r="2" fill="currentColor" />
              </svg>
              <span><span className="block text-lg font-semibold">求职罗盘</span><span className="block text-xs text-[var(--steel-grey)]">JobCompass</span></span>
            </Link>
            <nav aria-label="主导航" className="flex flex-wrap gap-1.5">
              {navItems.map((item) => {
                const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
                return (
                  <Link
                    aria-current={active ? "page" : undefined}
                    className={`rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                      active ? "bg-[var(--accent-soft)] text-[var(--accent)]" : "text-[var(--ink-soft)] hover:bg-[var(--secondary)]"
                    }`}
                    href={item.href}
                    key={item.href}
                  >
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>
        </header>
        <div className="mx-auto w-full max-w-[86rem] flex-1 px-5 py-7 md:px-8 md:py-9">
          <div className="mb-7 grid gap-5 lg:grid-cols-[1fr_auto] lg:items-end">
            <div>
              <p className="text-sm font-medium text-[var(--steel-grey)]">{eyebrow}</p>
              <h1 className="mt-2 max-w-5xl text-3xl font-semibold leading-tight tracking-tight md:text-4xl">
                {title}
              </h1>
              {description ? <p className="mt-3 max-w-3xl text-sm leading-7 text-[var(--ink-soft)]">{description}</p> : null}
            </div>
            {actions ? <div className="flex flex-wrap gap-3 lg:justify-end">{actions}</div> : null}
          </div>
          <div className="space-y-6">{children}</div>
        </div>
        <footer className="border-t border-[var(--border)] bg-white">
          <div className="mx-auto flex max-w-[86rem] flex-wrap items-center justify-between gap-3 px-5 py-5 text-xs text-[var(--steel-grey)] md:px-8">
            <span>求职罗盘 JobCompass</span>
            <span>依据真实经历 · 用户确认后执行</span>
          </div>
        </footer>
      </div>
    </main>
  );
}
