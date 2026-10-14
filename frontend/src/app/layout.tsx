import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "求职罗盘 | 岗位分析与准备计划",
  description: "核对岗位要求与简历证据，汇总多个岗位的共同准备事项，按时间预算确认和追踪求职计划。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
