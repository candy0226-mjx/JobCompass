package com.resumeai.compass;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

public final class Planning {
    private Planning() {}
    public record Task(UUID id,String skillKey,String actionType,String title,String expectedOutput,int effortMinutes,
        boolean selected,List<UUID> requirementIds,List<UUID> jobIds,int benefit,double priority,LocalDate deadline,
        String explanation,String status,long version) {
        public Task edit(String title,String output,int effort,boolean selected,String explanation,String status,long version) {
            return new Task(id,skillKey,actionType,title,output,effort,selected,requirementIds,jobIds,benefit,benefit/(effort/60.0),deadline,explanation,status,version);
        }
    }
    public record Source(UUID analysisId,UUID jobId,String company,String title,int priority,LocalDate deadline) {}
    public record Plan(UUID id,String status,long version,int budgetMinutes,int allocatedMinutes,
                       List<Source> sources,List<Task> tasks,OffsetDateTime createdAt) {}
    public record TaskEdit(UUID id,String title,String expectedOutput,int effortMinutes,boolean selected) {}
    public record Patch(long expectedVersion,int budgetMinutes,List<TaskEdit> tasks) {}
    public record Application(UUID id,UUID jobId,String title,String company,String status,String note,long version) {}
    public record Event(UUID id,String resourceType,UUID resourceId,String oldValue,String newValue,long version,OffsetDateTime occurredAt) {}
}
