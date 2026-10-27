package com.resumeai.application.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.application.knowledge.RagKnowledgeService;
import com.resumeai.domain.analysis.Analysis;
import com.resumeai.infrastructure.persistence.AnalysisRepository;
import com.resumeai.infrastructure.persistence.JobDescriptionRepository;
import com.resumeai.infrastructure.persistence.ResumeRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisApplicationService {

    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final AnalysisRepository analysisRepository;
    private final KeywordMatcher keywordMatcher;
    private final RagKnowledgeService ragKnowledgeService;
    private final ObjectMapper objectMapper;

    public AnalysisApplicationService(
            ResumeRepository resumeRepository,
            JobDescriptionRepository jobDescriptionRepository,
            AnalysisRepository analysisRepository,
            KeywordMatcher keywordMatcher,
            RagKnowledgeService ragKnowledgeService,
            ObjectMapper objectMapper
    ) {
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.analysisRepository = analysisRepository;
        this.keywordMatcher = keywordMatcher;
        this.ragKnowledgeService = ragKnowledgeService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalysisResponse create(CreateAnalysisRequest request) {
        var resume = resumeRepository.findById(request.resumeId())
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + request.resumeId()));
        var job = jobDescriptionRepository.findById(request.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Job description not found: " + request.jobId()));

        var match = keywordMatcher.match(resume.getRawText(), job.getDescription());
        var keywordScore = score(match.matched().size(), match.keywords().size());
        var useRag = request.options() == null || !Boolean.FALSE.equals(request.options().useRag());
        var guidance = useRag
                ? ragKnowledgeService.search(job.getDescription(), 4).stream()
                        .map(result -> result.title() + ": " + result.content())
                        .toList()
                : List.<String>of();
        // V2 has one transparent literal-coverage score. Deprecated API fields are aliases,
        // not additional measurements. Historical rows retain their original V1 formula values.
        var semanticScore = keywordScore;
        var atsScore = keywordScore;
        var overallScore = keywordScore;
        var report = new AnalysisReport(
                match.keywords(),
                match.matched(),
                match.missing(),
                suggestions(match.missing(), keywordScore, guidance),
                guidance,
                match.evidence(),
                match.details()
        );

        var analysis = new Analysis(
                resume,
                job,
                overallScore,
                keywordScore,
                semanticScore,
                atsScore,
                writeJson(report)
        );

        return AnalysisResponse.from(analysisRepository.save(analysis), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> list() {
        return analysisRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(analysis -> AnalysisResponse.from(analysis, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .map(analysis -> AnalysisResponse.from(analysis, objectMapper))
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }

    @Transactional
    public void delete(UUID analysisId) {
        var analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
        analysisRepository.delete(analysis);
    }

    private int score(int matched, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.round((matched * 100f) / total);
    }

    private List<String> suggestions(List<String> missingKeywords, int keywordScore, List<String> guidance) {
        var suggestions = new ArrayList<String>();
        if (keywordScore < 60) {
            suggestions.add("补充一段面向目标岗位的中文摘要，直接回应岗位核心要求。");
        }
        if (!missingKeywords.isEmpty()) {
            suggestions.add("检查这些 JD 关键词缺口，并在真实经历支持时补充证据："
                    + String.join(", ", missingKeywords.stream().limit(8).toList()));
        }
        suggestions.add("按“动作词 + 技术范围 + 业务场景 + 可验证结果”的结构改写项目经历。");
        suggestions.add("所有优化必须忠于已有经历，不要编造职责、指标或技术栈。");
        if (!guidance.isEmpty()) {
            suggestions.add("结合检索到的简历规则提升相关性：" + guidance.getFirst());
        }
        return suggestions;
    }

    private String writeJson(AnalysisReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize analysis report.", exception);
        }
    }
}
