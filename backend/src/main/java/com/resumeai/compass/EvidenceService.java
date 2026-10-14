package com.resumeai.compass;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EvidenceService {
    private final JdbcTemplate db; private final CompassJson json; private final SnapshotService snapshots;
    private final EvidenceModel model; private final TransactionTemplate tx; private final String modelName;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),
        r->{var t=new Thread(r,"compass-evidence");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    public record Run(UUID id,String status,UUID resumeVersionId,Snapshots.Job job,Evidence.Report report,
                      List<Evidence.SavedReview> reviews,String errorCode,Long durationMs,int toolCalls,OffsetDateTime createdAt,AnalysisMetrics.Snapshot metrics) {
        public Run(UUID id,String status,UUID versionId,Snapshots.Job job,Evidence.Report report,List<Evidence.SavedReview> reviews,
                   String errorCode,Long durationMs,int toolCalls,OffsetDateTime createdAt) {
            this(id,status,versionId,job,report,reviews,errorCode,durationMs,toolCalls,createdAt,null);
        }
    }
    public EvidenceService(JdbcTemplate db,CompassJson json,SnapshotService snapshots,EvidenceModel model,PlatformTransactionManager manager,Environment env) {
        this.db=db;this.json=json;this.snapshots=snapshots;this.model=model;this.tx=new TransactionTemplate(manager);
        this.modelName=env.getProperty("spring.ai.openai.chat.options.model","configured-model");
    }
    @EventListener(ApplicationReadyEvent.class) public void recover() {
        db.update("UPDATE analysis_run SET status='FAILED',error_code='INTERRUPTED',finished_at=now() WHERE status IN ('PENDING','RUNNING')");
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
    public Run submit(UUID versionId,UUID jobId,UUID retryOf) {
        return enqueue(versionId,snapshots.job(jobId),retryOf);
    }
    private Run enqueue(UUID versionId,Snapshots.Job job,UUID retryOf) {
        var version=snapshots.version(versionId); UUID id=UUID.randomUUID();
        Snapshots.field(job.title(),"岗位名称",200);Snapshots.field(job.company(),"公司",200);
        tx.executeWithoutResult(s->db.update("INSERT INTO analysis_run(id,resume_version_id,job_id,job_snapshot_json,status,model_name,retry_of,prompt_version) VALUES (?,?,?,CAST(? AS jsonb),'PENDING',?,?,'evidence-v2')",
            id,versionId,job.id(),json.write(job),modelName,retryOf));
        try { workers.execute(()->execute(id,version,job)); }
        catch(RejectedExecutionException e) {
            fail(id,"QUEUE_FULL",0); throw new CompassException(429,"QUEUE_FULL","分析队列已满，请稍后重试；运行 ID："+id);
        }
        return get(id);
    }
    public Run retry(UUID id) { var old=get(id);if(!old.status().equals("FAILED")) throw CompassException.conflict("仅失败的分析可以重试");return enqueue(old.resumeVersionId(),old.job(),id); }
    void execute(UUID id,Snapshots.Version version,Snapshots.Job job) {
        long start=System.nanoTime();
        if(db.update("UPDATE analysis_run SET status='RUNNING' WHERE id=? AND status='PENDING'",id)==0) return;
        try {
            String history="尚无投递记录";
            if(db.queryForObject("SELECT to_regclass('job_application') IS NOT NULL",Boolean.class)) {
                var states=db.query("SELECT status FROM job_application WHERE job_id=?",(r,n)->r.getString(1),job.id());
                if(!states.isEmpty()) history=states.getFirst();
            }
            var result=model.analyze(id,version,job,history);
            tx.executeWithoutResult(s->{
                for(var requirement:result.report().requirements()) {
                    var assessment=result.report().assessments().stream().filter(a->a.requirementId().equals(requirement.id())).findFirst().orElseThrow();
                    db.update("INSERT INTO analysis_requirement(id,analysis_id,requirement_json,assessment_json) VALUES (?,?,CAST(? AS jsonb),CAST(? AS jsonb))",
                        requirement.id(),id,json.write(requirement),json.write(assessment));
                }
                db.update("UPDATE analysis_run SET status='SUCCEEDED',report_json=CAST(? AS jsonb),duration_ms=?,tool_calls=?,metrics_json=CAST(? AS jsonb),finished_at=now() WHERE id=?",
                    json.write(result.report()),elapsed(start),result.toolCalls(),result.metrics()==null?null:json.write(result.metrics()),id);
            });
        } catch(CompassException e) { fail(id,e.code,elapsed(start),e.metrics()); }
        catch(Exception e) { fail(id,"ANALYSIS_FAILED",elapsed(start)); }
    }
    private long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start); }
    private void fail(UUID id,String code,long duration) { fail(id,code,duration,null); }
    private void fail(UUID id,String code,long duration,AnalysisMetrics.Snapshot metrics) {
        db.update("UPDATE analysis_run SET status='FAILED',error_code=?,duration_ms=?,metrics_json=CAST(? AS jsonb),tool_calls=?,finished_at=now() WHERE id=?",
            code,duration,metrics==null?null:json.write(metrics),metrics==null?0:metrics.toolCalls(),id);
    }
    public Run get(UUID id) {
        var runs=db.query("SELECT * FROM analysis_run WHERE id=?",(r,n)->new Run(id,r.getString("status"),r.getObject("resume_version_id",UUID.class),
            json.read(r.getString("job_snapshot_json"),Snapshots.Job.class),r.getString("report_json")==null?null:json.read(r.getString("report_json"),Evidence.Report.class),
            reviews(id),r.getString("error_code"),r.getObject("duration_ms",Long.class),r.getInt("tool_calls"),r.getObject("created_at",OffsetDateTime.class),
            r.getString("metrics_json")==null?null:json.read(r.getString("metrics_json"),AnalysisMetrics.Snapshot.class)),id);
        if(runs.isEmpty()) throw CompassException.missing("分析不存在");return runs.getFirst();
    }
    public List<Run> list() { return db.query("SELECT id FROM analysis_run ORDER BY created_at DESC LIMIT 100",(r,n)->r.getObject(1,UUID.class)).stream().map(this::get).toList(); }
    public List<Evidence.SavedReview> reviews(UUID runId) {
        return db.query("SELECT v.* FROM requirement_review v JOIN analysis_requirement q ON q.id=v.requirement_id WHERE q.analysis_id=?",
            (r,n)->new Evidence.SavedReview(r.getObject("requirement_id",UUID.class),r.getString("category"),r.getString("note"),r.getLong("version"),r.getString("skill_key")),runId);
    }
    public Run review(UUID id,List<Evidence.Review> reviews) {
        var run=get(id);if(!run.status().equals("SUCCEEDED")) throw CompassException.conflict("只有成功报告可以复核");
        if(reviews==null || reviews.size()>20) throw CompassException.bad("复核列表无效");
        var ids=new HashSet<UUID>();run.report().requirements().forEach(q->ids.add(q.id()));var seen=new HashSet<UUID>();
        tx.executeWithoutResult(s->{ for(var review:reviews) {
            if(review==null || !ids.contains(review.requirementId()) || !seen.add(review.requirementId())) throw CompassException.bad("复核要求不属于当前报告或重复");
            if(review.category()==null || !Set.of("EVIDENCE_FIX","LEARN","CLARIFY","CONFIRMED_MATCH").contains(review.category()) || review.note()==null || review.note().length()>1000) throw CompassException.bad("复核类别或备注无效");
            String skill=review.skillKey()==null || review.skillKey().isBlank()?null:review.skillKey();
            if(skill!=null && !BudgetPlanner.known(skill)) throw CompassException.bad("请选择支持的技能键，未知要求保持独立");
            var old=db.query("SELECT category FROM requirement_review WHERE requirement_id=?",(r,n)->r.getString(1),review.requirementId());
            int changed;
            if(review.expectedVersion()==0) changed=db.update("INSERT INTO requirement_review(requirement_id,category,note,skill_key) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",review.requirementId(),review.category(),review.note(),skill);
            else changed=db.update("UPDATE requirement_review SET category=?,note=?,skill_key=?,version=version+1,updated_at=now() WHERE requirement_id=? AND version=?",review.category(),review.note(),skill,review.requirementId(),review.expectedVersion());
            if(changed!=1) throw CompassException.conflict("复核意见已更新，请刷新");
            db.update("INSERT INTO activity_event(id,resource_type,resource_id,old_value,new_value,version) VALUES (?,'REVIEW',?,?,?,?)",UUID.randomUUID(),review.requirementId(),old.isEmpty()?null:old.getFirst(),review.category(),review.expectedVersion()+1);
        }});
        return get(id);
    }
}
