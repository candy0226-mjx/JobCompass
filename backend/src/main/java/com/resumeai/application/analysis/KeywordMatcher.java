package com.resumeai.application.analysis;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class KeywordMatcher {
    public static final String VERSION = "keyword-v2";
    private final TechnologyLexicon lexicon = new TechnologyLexicon();
    public record TermEvidence(String keyword, List<TechnologyLexicon.Occurrence> jobOccurrences,
                               List<TechnologyLexicon.Occurrence> resumeOccurrences) {}
    public record Details(String algorithmVersion, String dictionaryVersion, boolean available, Integer coverageScore,
                          List<String> unknownCandidates, List<TechnologyLexicon.Occurrence> corrections,
                          List<TermEvidence> terms, List<String> relatedCandidates) {}

    public List<String> extractKeywords(String jobDescription) {
        return lexicon.scan(jobDescription).occurrences().stream().map(TechnologyLexicon.Occurrence::canonical).distinct().sorted().toList();
    }
    public MatchResult match(String resumeText, String jobDescription) {
        var job = lexicon.scan(jobDescription); var resume = lexicon.scan(resumeText);
        var keywords = job.occurrences().stream().map(TechnologyLexicon.Occurrence::canonical).distinct().sorted().toList();
        var matched = new ArrayList<String>(); var missing = new ArrayList<String>();
        var evidence = new ArrayList<AnalysisReport.EvidenceItem>(); var terms = new ArrayList<TermEvidence>();
        for (String key : keywords) {
            var refs = resume.occurrences().stream().filter(o -> o.canonical().equals(key)).toList();
            boolean found = !refs.isEmpty();
            (found ? matched : missing).add(key);
            String excerpts = refs.stream().map(o -> line(resumeText, o.start(), o.end())).distinct().limit(5)
                .reduce((a,b) -> a + "\n" + b).orElse("");
            evidence.add(new AnalysisReport.EvidenceItem(key, excerpts, found));
            terms.add(new TermEvidence(key, job.occurrences().stream().filter(o -> o.canonical().equals(key)).toList(), refs));
        }
        var corrections = job.occurrences().stream().filter(o -> o.source().equals("CORRECTION")).toList();
        var related = resume.occurrences().stream().anyMatch(o -> o.canonical().equals("spring ai"))
            ? List.of("Spring AI 可能关联大模型应用开发，需核对模型调用、提示词或工具调用等实际经历；不自动计作 llm 命中。") : List.<String>of();
        var details = new Details(VERSION, TechnologyLexicon.VERSION, !keywords.isEmpty(),
            keywords.isEmpty() ? null : Math.round(matched.size()*100f/keywords.size()), job.unknownCandidates(),
            corrections, List.copyOf(terms), related);
        return new MatchResult(keywords, List.copyOf(matched), List.copyOf(missing), List.copyOf(evidence), details);
    }
    private String line(String text, int start, int end) {
        int left = text.lastIndexOf('\n', Math.max(0, start-1)) + 1;
        int right = text.indexOf('\n', end); if (right < 0) right = text.length();
        return text.substring(left, right).strip();
    }
    public record MatchResult(List<String> keywords, List<String> matched, List<String> missing,
                              List<AnalysisReport.EvidenceItem> evidence, Details details) {}
}
