package com.resumeai.compass.experience;

import com.resumeai.compass.Evidence;
import com.resumeai.compass.Snapshots;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Experience {
    private Experience() {}

    public record Context(UUID inquiryId, UUID analysisId, Snapshots.Version resume,
                          Snapshots.Job job, Evidence.Requirement requirement, Evidence.Assessment assessment) {}
    public record QuestionDraft(String focus, String question) {}
    public record QuestionOutput(List<QuestionDraft> questions) {}
    public record Question(String id, String focus, String question) {}
    public record Answer(String questionId, String text) {}
    public record Fact(String questionId, String focus, String quote, String sourceType) {}
    public record Draft(String title, String sourceType, List<Fact> facts) {}
    public record Card(UUID id, UUID inquiryId, UUID analysisId, UUID requirementId, UUID resumeVersionId,
                       long answerVersion, String sourceType, List<Question> questions, List<Answer> answers,
                       Draft content, OffsetDateTime confirmedAt) {}
    public record Session(UUID id, UUID analysisId, UUID requirementId, String status, long version,
                          List<Question> questions, List<Answer> answers, Draft draft, List<Card> cards,
                          String errorCode, Long durationMs, OffsetDateTime createdAt) {}
}
