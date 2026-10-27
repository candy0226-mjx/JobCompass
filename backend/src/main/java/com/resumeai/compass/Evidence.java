package com.resumeai.compass;
import java.util.List;
import java.util.UUID;

public final class Evidence {
    private Evidence() {}
    public record Reference(String segmentId,String quote) {}
    public record RequirementDraft(String text,String kind,String skillKey,Reference jdEvidence,
                                   String groupKey,String criterionKey,String category,String logic,List<Reference> additionalJdEvidence) {
        public RequirementDraft(String text,String kind,String skillKey,Reference ref) {
            this(text,kind,skillKey,ref,null,null,null,null,null);
        }
    }
    public record Extraction(List<RequirementDraft> requirements,Boolean complete,List<String> warnings) {
        public Extraction(List<RequirementDraft> requirements) { this(requirements,null,null); }
    }
    public record Requirement(UUID id,String text,String kind,String skillKey,Reference jdEvidence,
                              String groupKey,String criterionKey,String category,String logic,List<Reference> additionalJdEvidence) {
        public Requirement(UUID id,String text,String kind,String skillKey,Reference ref) {
            this(id,text,kind,skillKey,ref,null,null,null,null,null);
        }
    }
    public record Assessment(UUID requirementId,String status,List<Reference> resumeEvidence,String reason,
                             String actionType,String taskTitle,String expectedOutput,int effortMinutes) {}
    public record Assessments(List<Assessment> assessments) {}
    public enum Status { MATCH,GAP,UNCERTAIN }
    public enum Action { NONE,EVIDENCE_FIX,LEARN,CLARIFY }
    public record ModelAssessment(UUID requirementId,Status status,List<Reference> resumeEvidence,String reason,
                                  Action actionType,String taskTitle,String expectedOutput,int effortMinutes) {
        public Assessment toAssessment() { return new Assessment(requirementId,status==null?null:status.name(),resumeEvidence,reason,
            actionType==null?null:actionType.name(),taskTitle,expectedOutput,effortMinutes); }
    }
    public record ModelAssessments(List<ModelAssessment> assessments) {}
    public record TaskAdjustment(UUID requirementId,int reportedMinutes,int allocatedMinutes) {}
    public record ReferenceAdjustment(UUID requirementId,Reference reported,List<Reference> resolved) {}
    public record Report(List<Requirement> requirements,List<Assessment> assessments,String schemaVersion,Boolean extractionComplete,List<String> warnings,List<TaskAdjustment> taskAdjustments,List<ReferenceAdjustment> referenceAdjustments) {
        public Report(List<Requirement> requirements,List<Assessment> assessments) { this(requirements,assessments,null,null,null,null,null); }
        public Report(List<Requirement> requirements,List<Assessment> assessments,String schemaVersion,Boolean complete,List<String> warnings) {
            this(requirements,assessments,schemaVersion,complete,warnings,null,null);
        }
        public Report(List<Requirement> requirements,List<Assessment> assessments,String schemaVersion,Boolean complete,List<String> warnings,List<TaskAdjustment> adjustments) {
            this(requirements,assessments,schemaVersion,complete,warnings,adjustments,null);
        }
    }
    public record Review(UUID requirementId,String category,String note,long expectedVersion,String skillKey) {
        public Review(UUID id,String category,String note,long version) { this(id,category,note,version,null); }
    }
    public record SavedReview(UUID requirementId,String category,String note,long version,String skillKey) {}
}
