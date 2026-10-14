package com.resumeai.application.rewrite;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.agent.ResumeOptimizationTools;
import com.resumeai.domain.analysis.Analysis;
import com.resumeai.domain.rewrite.RewriteDraft;
import com.resumeai.domain.rewrite.RewriteDraftStatus;
import com.resumeai.infrastructure.persistence.AnalysisRepository;
import com.resumeai.infrastructure.persistence.RewriteDraftRepository;
import com.resumeai.infrastructure.storage.ObjectStorageService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class RewriteCandidateTests {
    private final RewriteDraftRepository repository = mock(RewriteDraftRepository.class);
    private final ResumeRewriteService service = new ResumeRewriteService(mock(AnalysisRepository.class), repository,
            mock(ResumeOptimizationTools.class), mock(ChatClient.Builder.class), mock(ObjectStorageService.class), new ObjectMapper());

    private RewriteDraft draft() {
        var analysis = mock(Analysis.class); when(analysis.getId()).thenReturn(UUID.randomUUID());
        var draft = new RewriteDraft(analysis,"test","真实原文","原稿","旧理由","{\"结论\":\"旧校验\"}");
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(repository.save(any(RewriteDraft.class))).thenAnswer(i -> i.getArgument(0));
        return draft;
    }

    @Test void generationDoesNotApplyTextAndAcceptancePersistsMatchingMetadataAndHistory() {
        var draft=draft();
        service.rememberCandidate(draft,"用户新要求",new RewriteGeneration("新稿","新理由","{\"结论\":\"新校验\"}"));
        assertThat(draft.getRewrittenText()).isEqualTo("原稿");assertThat(draft.getRationale()).isEqualTo("旧理由");
        var accepted=service.accept(draft.getId(),new ApplyRewriteCandidateRequest("新稿"));
        assertThat(accepted.rewrittenText()).isEqualTo("新稿");assertThat(accepted.rationale()).isEqualTo("新理由");
        assertThat(accepted.verificationJson()).contains("新校验");assertThat(accepted.conversationHistory()).contains("ACCEPTED","用户新要求");
        assertThat(accepted.regeneratedCount()).isEqualTo(1);
    }

    @Test void staleOrTamperedCandidateTextCannotApplyNewMetadata() {
        var draft=draft();service.rememberCandidate(draft,"第一轮",new RewriteGeneration("第一稿","第一理由","{}"));
        service.rememberCandidate(draft,"第二轮",new RewriteGeneration("第二稿","第二理由","{}"));
        assertThatThrownBy(()->service.accept(draft.getId(),new ApplyRewriteCandidateRequest("第一稿"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.accept(draft.getId(),new ApplyRewriteCandidateRequest("客户端伪造"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getRewrittenText()).isEqualTo("原稿");assertThat(draft.getConversationHistory()).contains("SUPERSEDED");
    }

    @Test void repeatedAcceptDoesNotIncrementGenerationCount() {
        var draft=draft();service.rememberCandidate(draft,"要求",new RewriteGeneration("候选","理由","{}"));
        service.accept(draft.getId(),new ApplyRewriteCandidateRequest("候选"));
        assertThat(service.accept(draft.getId(),new ApplyRewriteCandidateRequest("候选")).regeneratedCount()).isEqualTo(1);
    }

    @Test void rejectionKeepsAppliedContentAndMakesRejectedCandidateUnacceptable() {
        var draft=draft();service.rememberCandidate(draft,"要求",new RewriteGeneration("候选","理由","{}"));
        service.reject(draft.getId(),new RejectRewriteCandidateRequest("不适合"));
        assertThat(draft.getRewrittenText()).isEqualTo("原稿");assertThat(draft.getRationale()).isEqualTo("旧理由");
        assertThat(draft.getStatus()).isEqualTo(RewriteDraftStatus.REJECTED);
        assertThatThrownBy(()->service.accept(draft.getId(),new ApplyRewriteCandidateRequest("候选"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void emptyCandidateCannotCreateFakeSuccessfulGeneration() {
        var draft=draft();
        assertThatThrownBy(()->service.rememberCandidate(draft,"要求",new RewriteGeneration(" ","理由","{}"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(draft.getRegeneratedCount()).isZero();assertThat(draft.getConversationHistory()).isEqualTo("[]");
    }
}
