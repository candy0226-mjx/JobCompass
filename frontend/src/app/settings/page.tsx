"use client";

import { useEffect, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card, CardHeader } from "@/components/ui/card";
import { type SettingsResponse, type SystemStatusResponse, getSettings, getSystemStatus } from "@/lib/api/client";
import { formatDateTime } from "@/lib/format";

export default function SettingsPage() {
  const [settings, setSettings] = useState<SettingsResponse | null>(null);
  const [status, setStatus] = useState<SystemStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  async function loadSettings() {
    setIsLoading(true);
    setError(null);
    try {
      const [loadedSettings, loadedStatus] = await Promise.all([getSettings(), getSystemStatus()]);
      setSettings(loadedSettings);
      setStatus(loadedStatus);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "系统配置加载失败。");
    } finally {
      setIsLoading(false);
    }
  }

  useEffect(() => {
    void loadSettings();
  }, []);

  return (
    <AppShell
      actions={
        <>
          <Button disabled={isLoading} onClick={loadSettings} tone="paper" type="button">{isLoading ? "加载中" : "刷新"}</Button>
          <ButtonLink href="/dashboard" tone="ink">工作台</ButtonLink>
        </>
      }
      description="参考项目会明确提示 LLM 和运行状态；这里展示 DeepSeek/OpenAI 兼容配置、RAG、MinIO、Redis、上传限制和后端组件状态，同时不暴露任何密钥。"
      eyebrow="系统设置"
      title="系统配置与运行状态。"
    >
      {error ? <p className="mb-6 border border-black bg-[#dc2626] p-4 font-mono text-sm font-bold uppercase text-white shadow-sw-sm">{error}</p> : null}

      {settings ? (
        <section className="grid gap-6 lg:grid-cols-2">
          <SettingsCard
            rows={[
              ["模型提供方", settings.ai.provider],
              ["Spring 聊天模式", settings.ai.springChatModel],
              ["Spring 向量模式", settings.ai.springEmbeddingModel],
              ["聊天接口地址", settings.ai.openAiChatBaseUrl || settings.ai.openAiBaseUrl],
              ["向量接口地址", settings.ai.openAiEmbeddingBaseUrl || settings.ai.openAiBaseUrl],
              ["聊天模型", settings.ai.openAiChatModel],
              ["向量模型", settings.ai.openAiEmbeddingModel],
              ["聊天 API Key", settings.ai.openAiChatApiKeyConfigured || settings.ai.openAiApiKeyConfigured ? "已配置" : "未配置"],
              ["向量 API Key", settings.ai.openAiEmbeddingApiKeyConfigured || settings.ai.openAiApiKeyConfigured ? "已配置" : "未配置"],
              ["Ollama 地址", settings.ai.ollamaBaseUrl],
              ["Ollama 聊天模型", settings.ai.ollamaChatModel],
              ["Ollama 向量模型", settings.ai.ollamaEmbeddingModel],
            ]}
            title="AI 模型"
            tone="lime"
          />
          <SettingsCard
            rows={[
              ["向量维度", String(settings.rag.embeddingDimensions)],
              ["Top K", String(settings.rag.topK)],
              ["相似度阈值", String(settings.rag.similarityThreshold)],
            ]}
            title="RAG / PGvector"
            tone="sky"
          />
          <SettingsCard
            rows={[
              ["服务地址", settings.storage.endpoint],
              ["Bucket", settings.storage.bucket],
              ["Access Key", settings.storage.accessKeyConfigured ? "已配置" : "未配置"],
              ["Secret Key", settings.storage.secretKeyConfigured ? "已配置" : "未配置"],
            ]}
            title="MinIO"
            tone="paper"
          />
          <SettingsCard
            rows={[
              ["启用", settings.redis.enabled ? "是" : "否"],
              ["主机", settings.redis.host],
              ["端口", String(settings.redis.port)],
              ["密码", settings.redis.passwordConfigured ? "已配置" : "未配置"],
            ]}
            title="Redis"
            tone="gold"
          />
          <SettingsCard
            rows={[
              ["单文件上限", settings.upload.maxFileSize],
              ["请求总上限", settings.upload.maxRequestSize],
            ]}
            title="简历上传"
            tone="paper"
          />
        </section>
      ) : null}

      {status ? (
        <Card className="mt-6" tone="ink">
          <CardHeader eyebrow="运行状态" title={status.status} description={`最近检查：${formatDateTime(status.timestamp)}`} />
          <div className="mt-5 grid gap-3 md:grid-cols-3">
            {status.components.map((component) => (
              <article className="border border-white/80 bg-white/10 p-4" key={component.name}>
                <div className="flex items-start justify-between gap-3">
                  <p className="font-mono text-xs font-black uppercase tracking-[0.18em] text-white/70">{component.name}</p>
                  <span className="border border-white/70 px-2 py-1 font-mono text-[10px] font-black">{component.status}</span>
                </div>
                <p className="mt-3 text-sm leading-6 text-white/75">{component.detail}</p>
              </article>
            ))}
          </div>
        </Card>
      ) : null}
    </AppShell>
  );
}

function SettingsCard({
  rows,
  title,
  tone,
}: {
  rows: Array<[string, string]>;
  title: string;
  tone: "paper" | "lime" | "ink" | "gold" | "sky";
}) {
  return (
    <Card tone={tone}>
      <CardHeader eyebrow="配置" title={title} />
      <dl className="mt-5 space-y-3">
        {rows.map(([label, value]) => (
          <div className="grid gap-2 border-b border-black/20 pb-3 md:grid-cols-[0.52fr_1fr]" key={label}>
            <dt className="font-mono text-xs font-bold uppercase tracking-wide text-[#1d4ed8]">{label}</dt>
            <dd className="break-all font-bold">{value || "未配置"}</dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}
