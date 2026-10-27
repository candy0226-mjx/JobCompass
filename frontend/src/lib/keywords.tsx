import type { ReactNode } from "react";

export function normalizeKeyword(keyword: string) {
  return keyword.trim().toLowerCase();
}

export function uniqueKeywords(keywords: string[]) {
  return Array.from(new Set(keywords.map((keyword) => keyword.trim()).filter(Boolean)));
}

export function keywordSet(keywords: string[]) {
  return new Set(uniqueKeywords(keywords).map(normalizeKeyword));
}

export function highlightText(text: string, keywords: string[]): ReactNode[] {
  const normalized = uniqueKeywords(keywords)
    .filter((keyword) => keyword.length >= 2)
    .sort((a, b) => b.length - a.length);

  if (normalized.length === 0 || !text) {
    return [text];
  }

  const escaped = normalized.map((keyword) => keyword.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
  const pattern = new RegExp(`((?<![A-Za-z0-9_+#])(?:${escaped.join("|")})(?![A-Za-z0-9_+#]))`, "gi");
  const parts = text.split(pattern);
  const lookup = new Set(normalized.map(normalizeKeyword));

  return parts.map((part, index) => {
    if (lookup.has(normalizeKeyword(part))) {
      return (
        <mark className="border border-[#171713] bg-[#f3cf5c] px-1 text-[#171713]" key={`${part}-${index}`}>
          {part}
        </mark>
      );
    }
    return part;
  });
}

export function deriveJobKeywords(description: string) {
  const tokens = description.match(/[A-Za-z][A-Za-z0-9+#.-]{1,}|[\u4e00-\u9fa5]{2,}/g) ?? [];
  const stopwords = new Set([
    "负责",
    "熟悉",
    "经验",
    "能力",
    "优先",
    "岗位",
    "要求",
    "相关",
    "进行",
    "以及",
    "具有",
    "and",
    "the",
    "with",
    "for",
    "job",
    "work",
  ]);
  const counts = new Map<string, number>();
  tokens.forEach((token) => {
    const normalized = normalizeKeyword(token);
    if (stopwords.has(normalized) || normalized.length < 2) {
      return;
    }
    counts.set(token, (counts.get(token) ?? 0) + 1);
  });
  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 24)
    .map(([keyword]) => keyword);
}
