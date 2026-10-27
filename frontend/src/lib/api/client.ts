export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type ResumeResponse = {
  id: string;
  title: string;
  originalFilename: string;
  contentType: string;
  status: string;
  rawTextLength: number;
  rawText: string;
  rawTextPreview: string;
  structuredJson: string | null;
  createdAt: string;
  updatedAt: string;
};

export type JobDescriptionResponse = {
  id: string;
  title: string | null;
  company: string | null;
  description: string;
  status: string;
  structuredJson: string | null;
  createdAt: string;
  updatedAt: string;
};

export type KeywordOccurrence = { canonical: string; original: string; start: number; end: number; source: string };
export type KeywordEvaluation = {
  algorithmVersion: string; dictionaryVersion: string; available: boolean; coverageScore: number | null;
  unknownCandidates: string[]; corrections: KeywordOccurrence[]; relatedCandidates: string[];
  terms: { keyword: string; jobOccurrences: KeywordOccurrence[]; resumeOccurrences: KeywordOccurrence[] }[];
};
export type AnalysisResponse = {
  id: string;
  resumeId: string;
  jobId: string;
  overallScore: number;
  keywordScore: number;
  semanticScore: number;
  atsScore: number;
  status: string;
  report: {
    extractedKeywords: string[];
    matchedKeywords: string[];
    missingKeywords: string[];
    suggestions: string[];
    retrievedGuidance: string[];
    evidenceMap: Array<{
      keyword: string;
      evidence: string;
      matched: boolean;
    }>;
    keywordEvaluation?: KeywordEvaluation | null;
  };
  createdAt: string;
  updatedAt: string;
};

export type RewriteDraftResponse = {
  id: string;
  analysisId: string;
  sectionId: string | null;
  originalText: string;
  rewrittenText: string;
  rationale: string;
  verificationJson: string;
  conversationHistory: string;
  regeneratedCount: number;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type RewriteCandidateResponse = {
  rewriteId: string;
  userMessage: string;
  rewrittenText: string;
  rationale: string;
  verificationJson: string;
  originalText: string;
  conversationHistory: string;
  regeneratedCount: number;
  status: string;
  suggestions: string[];
};

export type ExportRewriteResponse = {
  rewriteId: string;
  format: string;
  objectKey: string;
  contentType: string;
  size: number;
  exportedAt: string;
  downloadUrl: string;
  downloadUrlExpiresAt: string;
};

export type KnowledgeDocumentResponse = {
  id: string;
  documentType: string;
  title: string;
  sourceType: string | null;
  content: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type KnowledgeSearchResult = {
  id: string;
  title: string;
  content: string;
  score: number | null;
  metadata: Record<string, unknown>;
};

export type SystemStatusResponse = {
  status: string;
  timestamp: string;
  components: Array<{
    name: string;
    status: string;
    detail: string;
  }>;
};

export type SettingsResponse = {
  ai: {
    provider: string;
    springChatModel: string;
    springEmbeddingModel: string;
    openAiBaseUrl: string;
    openAiChatBaseUrl: string;
    openAiEmbeddingBaseUrl: string;
    openAiChatModel: string;
    openAiEmbeddingModel: string;
    openAiApiKeyConfigured: boolean;
    openAiChatApiKeyConfigured: boolean;
    openAiEmbeddingApiKeyConfigured: boolean;
    ollamaBaseUrl: string;
    ollamaChatModel: string;
    ollamaEmbeddingModel: string;
  };
  rag: {
    embeddingDimensions: number;
    topK: number;
    similarityThreshold: number;
  };
  storage: {
    endpoint: string;
    bucket: string;
    accessKeyConfigured: boolean;
    secretKeyConfigured: boolean;
  };
  redis: {
    enabled: boolean;
    host: string;
    port: number;
    passwordConfigured: boolean;
  };
  upload: {
    maxFileSize: string;
    maxRequestSize: string;
  };
};

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "接口请求失败");
  }

  return response.json() as Promise<T>;
}

async function throwApiError(response: Response, fallbackMessage: string): Promise<never> {
  const fallback = `${fallbackMessage}：${response.status}`;
  let message: string | undefined;

  try {
    const error = (await response.json()) as { message?: string; code?: string };
    message = error.message;
  } catch (error) {
    message = undefined;
  }

  throw new Error(message ?? fallback);
}

export async function getSystemStatus(): Promise<SystemStatusResponse> {
  return apiGet<SystemStatusResponse>("/status");
}

export async function getSettings(): Promise<SettingsResponse> {
  return apiGet<SettingsResponse>("/settings");
}

export async function listResumes(): Promise<ResumeResponse[]> {
  return apiGet<ResumeResponse[]>("/resumes");
}

export async function listJobDescriptions(): Promise<JobDescriptionResponse[]> {
  return apiGet<JobDescriptionResponse[]>("/jobs");
}

export async function listAnalyses(): Promise<AnalysisResponse[]> {
  return apiGet<AnalysisResponse[]>("/analyses");
}

export async function listRewrites(): Promise<RewriteDraftResponse[]> {
  return apiGet<RewriteDraftResponse[]>("/rewrites");
}

export async function getResume(resumeId: string): Promise<ResumeResponse> {
  return apiGet<ResumeResponse>(`/resumes/${resumeId}`);
}

export async function getJobDescription(jobId: string): Promise<JobDescriptionResponse> {
  return apiGet<JobDescriptionResponse>(`/jobs/${jobId}`);
}

export async function getAnalysis(analysisId: string): Promise<AnalysisResponse> {
  return apiGet<AnalysisResponse>(`/analyses/${analysisId}`);
}

export async function getRewrite(rewriteId: string): Promise<RewriteDraftResponse> {
  return apiGet<RewriteDraftResponse>(`/rewrites/${rewriteId}`);
}

export async function exportRewriteMarkdown(rewriteId: string): Promise<ExportRewriteResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}/exports/markdown`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "改写导出失败");
  }

  return response.json() as Promise<ExportRewriteResponse>;
}

export async function exportRewritePdf(rewriteId: string): Promise<ExportRewriteResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}/exports/pdf`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "PDF 导出失败");
  }

  return response.json() as Promise<ExportRewriteResponse>;
}

export async function uploadResume(file: File, title?: string): Promise<ResumeResponse> {
  const formData = new FormData();
  formData.append("file", file);
  if (title?.trim()) {
    formData.append("title", title.trim());
  }

  const response = await fetch(`${API_BASE_URL}/resumes`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    await throwApiError(response, "简历上传失败");
  }

  return response.json() as Promise<ResumeResponse>;
}

export async function deleteResume(resumeId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/resumes/${resumeId}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "简历删除失败");
  }
}

export async function createJobDescription(input: {
  title?: string;
  company?: string;
  description: string;
}): Promise<JobDescriptionResponse> {
  const response = await fetch(`${API_BASE_URL}/jobs`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    await throwApiError(response, "岗位描述创建失败");
  }

  return response.json() as Promise<JobDescriptionResponse>;
}

export async function deleteJobDescription(jobId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/jobs/${jobId}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "岗位删除失败");
  }
}

export async function createAnalysis(input: {
  resumeId: string;
  jobId: string;
  useRag?: boolean;
}): Promise<AnalysisResponse> {
  const response = await fetch(`${API_BASE_URL}/analyses`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
        resumeId: input.resumeId,
        jobId: input.jobId,
        options: {
          useRag: input.useRag ?? true,
          includeAtsScore: true,
          language: "zh-CN",
        },
      }),
  });

  if (!response.ok) {
    await throwApiError(response, "匹配分析创建失败");
  }

  return response.json() as Promise<AnalysisResponse>;
}

export async function deleteAnalysis(analysisId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/analyses/${analysisId}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "分析报告删除失败");
  }
}

export async function structureResume(resumeId: string): Promise<ResumeResponse> {
  const response = await fetch(`${API_BASE_URL}/resumes/${resumeId}/structure`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "简历结构化失败");
  }

  return response.json() as Promise<ResumeResponse>;
}

export async function structureJob(jobId: string): Promise<JobDescriptionResponse> {
  const response = await fetch(`${API_BASE_URL}/jobs/${jobId}/structure`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "岗位结构化失败");
  }

  return response.json() as Promise<JobDescriptionResponse>;
}

export async function createRewrite(input: {
  analysisId: string;
  sectionText?: string;
  sectionId?: string;
  customPrompt?: string;
}): Promise<RewriteDraftResponse> {
  const response = await fetch(`${API_BASE_URL}/analyses/${input.analysisId}/rewrites`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({
      sectionText: input.sectionText,
      sectionId: input.sectionId,
      customPrompt: input.customPrompt,
    }),
  });

  if (!response.ok) {
    await throwApiError(response, "改写草稿创建失败");
  }

  return response.json() as Promise<RewriteDraftResponse>;
}

export async function deleteRewrite(rewriteId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "改写草稿删除失败");
  }
}

export async function updateRewrite(rewriteId: string, rewrittenText: string): Promise<RewriteDraftResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ rewrittenText }),
  });

  if (!response.ok) {
    await throwApiError(response, "改写草稿更新失败");
  }

  return response.json() as Promise<RewriteDraftResponse>;
}

export async function regenerateRewrite(rewriteId: string, userMessage: string): Promise<RewriteCandidateResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}/regenerate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ userMessage }),
  });

  if (!response.ok) {
    await throwApiError(response, "改写重生成失败");
  }

  return response.json() as Promise<RewriteCandidateResponse>;
}

export async function acceptRewriteCandidate(rewriteId: string, rewrittenText: string): Promise<RewriteDraftResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}/accept`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ rewrittenText }),
  });

  if (!response.ok) {
    await throwApiError(response, "候选改写确认失败");
  }

  return response.json() as Promise<RewriteDraftResponse>;
}

export async function rejectRewriteCandidate(rewriteId: string, reason?: string): Promise<RewriteDraftResponse> {
  const response = await fetch(`${API_BASE_URL}/rewrites/${rewriteId}/reject`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify({ reason }),
  });

  if (!response.ok) {
    await throwApiError(response, "候选改写拒绝失败");
  }

  return response.json() as Promise<RewriteDraftResponse>;
}

export async function listKnowledgeDocuments(): Promise<KnowledgeDocumentResponse[]> {
  return apiGet<KnowledgeDocumentResponse[]>("/knowledge/documents");
}

export async function createKnowledgeDocument(input: {
  documentType: string;
  title: string;
  sourceType?: string;
  content: string;
}): Promise<KnowledgeDocumentResponse> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    await throwApiError(response, "知识文档创建失败");
  }

  return response.json() as Promise<KnowledgeDocumentResponse>;
}

export async function updateKnowledgeDocument(
  documentId: string,
  input: {
    documentType: string;
    title: string;
    sourceType?: string;
    content: string;
  },
): Promise<KnowledgeDocumentResponse> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents/${documentId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    await throwApiError(response, "知识文档保存失败");
  }

  return response.json() as Promise<KnowledgeDocumentResponse>;
}

export async function deleteKnowledgeDocument(documentId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents/${documentId}`, {
    method: "DELETE",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "知识文档删除失败");
  }
}

export async function indexKnowledgeDocument(documentId: string): Promise<KnowledgeDocumentResponse> {
  const response = await fetch(`${API_BASE_URL}/knowledge/documents/${documentId}/index`, {
    method: "POST",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    await throwApiError(response, "知识文档索引失败");
  }

  return response.json() as Promise<KnowledgeDocumentResponse>;
}

export async function searchKnowledge(input: {
  query: string;
  topK?: number;
}): Promise<KnowledgeSearchResult[]> {
  const response = await fetch(`${API_BASE_URL}/knowledge/search`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(input),
  });

  if (!response.ok) {
    await throwApiError(response, "知识检索失败");
  }

  return response.json() as Promise<KnowledgeSearchResult[]>;
}
