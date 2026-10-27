package com.resumeai.application.analysis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.domain.resume.Resume;
import com.resumeai.domain.job.JobDescription;
import com.resumeai.infrastructure.persistence.*;
import com.resumeai.application.knowledge.RagKnowledgeService;

class RuleScoringTests {
    @Test void noLengthRewardAndNoIndependentSemanticOrAtsMeasurement() {
        var resumes = mock(ResumeRepository.class); var jobs = mock(JobDescriptionRepository.class);
        var analyses = mock(AnalysisRepository.class); var rag = mock(RagKnowledgeService.class);
        UUID resumeId=UUID.randomUUID(), jobId=UUID.randomUUID();
        when(jobs.findById(jobId)).thenReturn(Optional.of(new JobDescription("test", "test", "Java Redis")));
        when(analyses.save(any())).thenAnswer(i -> i.getArgument(0));
        var service = new AnalysisApplicationService(resumes,jobs,analyses,new KeywordMatcher(),rag,new ObjectMapper());
        var request = new CreateAnalysisRequest(resumeId,jobId,new CreateAnalysisRequest.AnalysisOptions(false,true,"zh"));
        when(resumes.findById(resumeId)).thenReturn(Optional.of(new Resume("test",null,null,null,"Java 项目")));
        var a=service.create(request);
        when(resumes.findById(resumeId)).thenReturn(Optional.of(new Resume("test",null,null,null,"Java 项目\n".repeat(1000))));
        var b=service.create(request);
        assertThat(a.overallScore()).isEqualTo(50);
        assertThat(b.overallScore()).isEqualTo(a.overallScore());
        assertThat(b.keywordScore()).isEqualTo(b.overallScore());
        assertThat(b.semanticScore()).isEqualTo(b.keywordScore());
        assertThat(b.atsScore()).isEqualTo(b.keywordScore());
        verifyNoInteractions(rag);
    }
}
