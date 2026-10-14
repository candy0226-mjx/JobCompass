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
    <main className="swiss-grid flex min-h-screen w-full items-start justify-center px-4 py-8 text-black md:px-8 md:py-12">
      <div className="flex w-full max-w-[86rem] flex-col border border-black bg-[#f0f0e8] shadow-sw-lg">
        <header className="relative z-30 shrink-0 border-b border-black bg-[#f0f0e8] p-6 md:p-10">
          <div className="mb-8 flex flex-wrap items-center justify-between gap-4">
            <Link className="font-mono text-sm font-bold uppercase tracking-wide text-[#1d4ed8]" href="/">
              求职罗盘
            </Link>
            <nav className="flex flex-wrap gap-2">
              {navItems.map((item) => {
                const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
                return (
                  <Link
                    className={`border border-black px-4 py-2 font-mono text-xs font-bold uppercase tracking-wide shadow-sw-sm transition-[transform,box-shadow,background-color,color] hover:translate-x-[1px] hover:translate-y-[1px] hover:shadow-none ${
                      active ? "bg-[#1d4ed8] text-white" : "bg-[#f0f0e8] text-black"
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
          <div className="grid gap-6 lg:grid-cols-[1fr_auto] lg:items-end">
            <div>
              <p className="font-mono text-sm font-bold uppercase tracking-wide text-[#1d4ed8]">// {eyebrow}</p>
              <h1 className="mt-5 max-w-5xl font-serif text-5xl font-semibold uppercase leading-[0.95] tracking-tight text-black md:text-7xl">
                {title}
              </h1>
              {description ? <p className="mt-6 max-w-2xl font-mono text-sm font-bold uppercase leading-6 tracking-wide text-[#1d4ed8]">{description}</p> : null}
            </div>
            {actions ? <div className="flex flex-wrap gap-3 lg:justify-end">{actions}</div> : null}
          </div>
        </header>
        <div className="p-[1.5px]">
          <div className="space-y-6 p-4 md:p-6">{children}</div>
        </div>
        <footer className="relative z-30 flex shrink-0 flex-wrap items-center justify-between gap-3 border-t border-black bg-[#f0f0e8] p-4 font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">
          <span>求职罗盘 JobCompass</span>
          <span>依据真实经历 · 用户确认后执行</span>
        </footer>
      </div>
    </main>
  );
}
