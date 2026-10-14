package com.resumeai.compass;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;

/** A fresh instance per model attempt. Captured snapshots cannot be expanded by tool arguments. */
public class JobCompassReadTools {
    private final UUID runId;
    private final Snapshots.Version version;
    private final Snapshots.Job job;
    private final String history;
    private final CompassJson json;
    private final AtomicInteger calls=new AtomicInteger();
    public JobCompassReadTools(UUID runId,Snapshots.Version version,Snapshots.Job job,String history,CompassJson json) {
        this.runId=runId;this.version=version;this.job=job;this.history=history;this.json=json;
    }
    public int calls() { return calls.get(); }
    private void check(String actual,UUID expected) {
        if(calls.incrementAndGet()>4) throw new CompassException(422,"TOOL_LIMIT","工具调用超过四次");
        if(!expected.toString().equals(actual)) throw new CompassException(422,"TOOL_SCOPE","工具参数超出当前运行范围");
    }
    @Tool(description="读取本次分析的简历版本和原文分段，参数 versionId 必须为给定版本")
    public String getResumeVersion(String versionId) { check(versionId,version.id()); return json.write(version); }
    @Tool(description="读取本次运行的岗位 JD 快照和原文分段，参数 analysisId 必须为给定运行 ID")
    public String getJobSnapshot(String analysisId) { check(analysisId,runId); return json.write(job); }
    @Tool(description="读取给定版本中项目经历的原文候选段落，不推断不存在的经历")
    public String listCandidateProjects(String versionId) { check(versionId,version.id()); return json.write(version.segments()); }
    @Tool(description="读取本次岗位已有投递状态，只供提醒")
    public String getApplicationHistory(String jobId) { check(jobId,job.id()); return history; }
}
