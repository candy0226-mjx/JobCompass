"use client";

import { Button } from "@/components/ui/button";

export function ConfirmDialog({
  cancelText = "再想想",
  confirmText = "确认删除",
  description,
  isOpen,
  isWorking = false,
  eyebrow = "危险操作",
  onCancel,
  onConfirm,
  title,
}: {
  cancelText?: string;
  confirmText?: string;
  description: string;
  eyebrow?: string;
  isOpen: boolean;
  isWorking?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  title: string;
}) {
  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 px-4 py-8 backdrop-blur-[2px]" role="presentation">
      <section
        aria-modal="true"
        className="w-full max-w-xl overflow-hidden rounded-xl border border-[var(--border)] bg-white text-[var(--foreground)] shadow-sw-xl"
        role="dialog"
      >
        <div className="border-b border-[var(--border)] px-5 py-5">
          <p className="text-xs font-medium text-red-700">{eyebrow}</p>
          <h2 className="mt-2 text-2xl font-semibold leading-tight">{title}</h2>
        </div>
        <div className="grid gap-5 p-5 md:grid-cols-[auto_1fr]">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-2xl font-semibold text-red-700" aria-hidden="true">
            !
          </div>
          <div>
            <p className="text-sm leading-7 text-[var(--ink-soft)]">{description}</p>
            <p className="mt-4 rounded-lg bg-[var(--secondary)] p-3 text-xs leading-6 text-[var(--steel-grey)]">
              这一步会立即写入数据库；如果有关联记录，系统会按规则一并清理。
            </p>
          </div>
        </div>
        <div className="flex flex-wrap justify-end gap-3 border-t border-[var(--border)] bg-white p-4">
          <Button disabled={isWorking} onClick={onCancel} tone="paper" type="button">
            {cancelText}
          </Button>
          <Button disabled={isWorking} onClick={onConfirm} tone="danger" type="button">
            {isWorking ? "处理中" : confirmText}
          </Button>
        </div>
      </section>
    </div>
  );
}
