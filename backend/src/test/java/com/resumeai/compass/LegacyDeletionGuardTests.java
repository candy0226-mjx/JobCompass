package com.resumeai.compass;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.application.resume.ResumeApplicationService;
import com.resumeai.application.ai.AiStructuringGateway;
import com.resumeai.infrastructure.persistence.ResumeRepository;
import com.resumeai.infrastructure.storage.ObjectStorageService;
import com.resumeai.infrastructure.document.DocumentTextExtractor;
import org.junit.jupiter.api.Test;
class LegacyDeletionGuardTests {
    @Test void referencedResumeCannotLoseItsOriginalObjectBeforeForeignKeyFailure() {
        var repo=mock(ResumeRepository.class);var storage=mock(ObjectStorageService.class);UUID id=UUID.randomUUID();
        when(repo.hasCompassVersions(id)).thenReturn(true);
        var legacy=new ResumeApplicationService(repo,storage,mock(DocumentTextExtractor.class),mock(AiStructuringGateway.class),new ObjectMapper());
        assertThatThrownBy(()->legacy.delete(id)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);verify(repo,never()).delete(any());
    }
}
