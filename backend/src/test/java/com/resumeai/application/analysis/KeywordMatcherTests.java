package com.resumeai.application.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

class KeywordMatcherTests {

    private final KeywordMatcher keywordMatcher = new KeywordMatcher();

    @Test
    void matchesResumeEvidenceAgainstJobDescriptionKeywords() {
        var resume = """
                Built Java Spring Boot services with PostgreSQL and Redis.
                Designed RAG workflow with PGvector and Ollama.
                """;
        var job = "We need Java, Spring Boot, PostgreSQL, PGvector, Docker, and RAG experience.";

        var result = keywordMatcher.match(resume, job);

        assertThat(result.keywords()).contains("java", "spring boot", "postgresql", "pgvector", "rag");
        assertThat(result.matched()).contains("java", "spring boot", "postgresql", "pgvector", "rag");
        assertThat(result.missing()).contains("docker");
        assertThat(result.evidence()).anyMatch(item -> item.keyword().equals("pgvector") && item.matched());
    }

    @Test void longestNamesAliasesAndIndependentRequirements() {
        var result = keywordMatcher.match("用 SpringBoot 开发", "熟悉 Spring Boot；熟悉 SpringBoot");
        assertThat(result.keywords()).containsExactly("spring boot");
        assertThat(result.details().coverageScore()).isEqualTo(100);
        assertThat(result.details().terms().getFirst().jobOccurrences()).hasSize(2);
        assertThat(keywordMatcher.match("Spring Boot", "Spring 框架及 Spring Boot").missing()).containsExactly("spring");
    }
    @Test void boundariesProtectDifferentTechnologiesAndSymbols() {
        var result = keywordMatcher.match("JavaScript MySQL C++ C# Node.js", "Java SQL C++ C# NodeJS");
        assertThat(result.missing()).containsExactly("java", "sql");
        assertThat(result.matched()).containsExactly("c#", "c++", "node.js");
    }
    @Test void repetitionAndOrdinaryWordsCannotIncreaseCoverage() {
        var a = keywordMatcher.match("Java 项目", "Java Redis");
        var b = keywordMatcher.match("Java 项目\nJava 项目\nJava Java", "Java Redis Java 岗位 负责 熟悉 优化");
        assertThat(b.keywords()).isEqualTo(a.keywords());
        assertThat(b.details().coverageScore()).isEqualTo(a.details().coverageScore());
    }
    @Test void correctionsPreserveSourceAndUnknownCandidatesRemainVisible() {
        var result = keywordMatcher.match("MySQL", "MySQLl Rust");
        assertThat(result.keywords()).containsExactly("mysql");
        assertThat(result.details().corrections().getFirst().original()).isEqualTo("MySQLl");
        assertThat(result.details().unknownCandidates()).containsExactly("rust");
    }
    @Test void absentKnownTermsAreUnavailableAndSpringAiIsNotAutomaticLlmEvidence() {
        assertThat(keywordMatcher.match("Java", "岗位负责业务维护").details().coverageScore()).isNull();
        var result = keywordMatcher.match("Spring AI", "大模型应用开发");
        assertThat(result.missing()).containsExactly("llm");
        assertThat(result.details().relatedCandidates()).hasSize(1);
    }
    @Test void aliasesReturnLiteralExcerptsAndVersionedJsonRoundTrips() throws Exception {
        var result = keywordMatcher.match("用 K8s 部署\n用 Kubernetes 部署", "Kubernetes");
        assertThat(result.evidence().getFirst().evidence()).contains("K8s", "Kubernetes");
        var report = new AnalysisReport(result.keywords(), result.matched(), result.missing(), java.util.List.of(), java.util.List.of(), result.evidence(), result.details());
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue(mapper.writeValueAsString(report), AnalysisReport.class)).isEqualTo(report);
        var historical = mapper.readValue("{\"extractedKeywords\":[\"boot\"],\"matchedKeywords\":[],\"missingKeywords\":[\"boot\"],\"suggestions\":[],\"retrievedGuidance\":[],\"evidenceMap\":[]}", AnalysisReport.class);
        assertThat(historical.keywordEvaluation()).isNull();
        assertThat(historical.extractedKeywords()).containsExactly("boot");
    }
    @Test void optimizationToolUsesSameVersionAndUnavailablePolicy() {
        var tools = new com.resumeai.agent.ResumeOptimizationTools(null, keywordMatcher);
        assertThat(tools.calculateMatchScore("SpringBoot", "Spring Boot SpringBoot"))
            .contains("algorithm=keyword-v2", "keywordScore=100");
        assertThat(tools.calculateMatchScore("Java", "岗位负责业务")).contains("keywordScore=无法评估");
    }
}
