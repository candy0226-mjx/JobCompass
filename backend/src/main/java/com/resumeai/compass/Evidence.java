package com.resumeai.compass;
import java.util.List;
import java.util.UUID;

public final class Evidence {
    private Evidence() {}
    public record Reference(String segmentId,String quote) {}
    public record RequirementDraft(String text,String kind,String skillKey,Reference jdEvidence) {}
    public record Extraction(List<RequirementDraft> requirements) {}
    public record Requirement(UUID id,String text,String kind,String skillKey,Reference jdEvidence) {}
    public record Assessment(UUID requirementId,String status,List<Reference> resumeEvidence,String reason,
                             String actionType,String taskTitle,String expectedOutput,int effortMinutes) {}
    public record Assessments(List<Assessment> assessments) {}
    public record Report(List<Requirement> requirements,List<Assessment> assessments) {}
    public record Review(UUID requirementId,String category,String note,long expectedVersion,String skillKey) {
        public Review(UUID id,String category,String note,long version) { this(id,category,note,version,null); }
    }
    public record SavedReview(UUID requirementId,String category,String note,long version,String skillKey) {}
}
