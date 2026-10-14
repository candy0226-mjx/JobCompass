package com.resumeai.compass;
import java.util.List;
import java.util.UUID;
public interface EvidenceModel {
    record Result(Evidence.Report report,int toolCalls,AnalysisMetrics.Snapshot metrics) {
        public Result(Evidence.Report report,int toolCalls) { this(report,toolCalls,null); }
    }
    Result analyze(UUID runId,Snapshots.Version version,Snapshots.Job job,String history);
}
