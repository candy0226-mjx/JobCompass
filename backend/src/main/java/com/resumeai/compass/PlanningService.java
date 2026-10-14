package com.resumeai.compass;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PlanningService {
    private final JdbcTemplate db;private final CompassJson json;private final EvidenceService evidence;
    private final BudgetPlanner planner;private final TransactionTemplate tx;
    public PlanningService(JdbcTemplate db,CompassJson json,EvidenceService evidence,BudgetPlanner planner,PlatformTransactionManager manager) {
        this.db=db;this.json=json;this.evidence=evidence;this.planner=planner;this.tx=new TransactionTemplate(manager);
    }
    public Planning.Plan create(List<UUID> analysisIds,int budget,Map<UUID,Integer> priorities) {
        BudgetPlanner.budget(budget);
        if(analysisIds==null || analysisIds.isEmpty() || analysisIds.size()>3 || new HashSet<>(analysisIds).size()!=analysisIds.size()) throw CompassException.bad("请选择 1–3 份不同报告");
        var runs=analysisIds.stream().map(evidence::get).toList();var jobs=new HashSet<UUID>();
        UUID version=runs.getFirst().resumeVersionId();
        for(var run:runs) if(!run.status().equals("SUCCEEDED") || !version.equals(run.resumeVersionId()) || !jobs.add(run.job().id())) throw CompassException.bad("须使用同一简历版本、不同岗位的成功报告");
        var requested=priorities==null?Map.<UUID,Integer>of():priorities;
        if(requested.keySet().stream().anyMatch(id->!jobs.contains(id)) || requested.values().stream().anyMatch(p->p==null || p<1 || p>3)) throw CompassException.bad("岗位重要程度须为 1–3，且属于所选报告");
        var tasks=planner.merge(runs,requested,budget);UUID id=UUID.randomUUID();
        tx.executeWithoutResult(s->{
            db.update("INSERT INTO preparation_plan(id,status,budget_minutes,allocated_minutes) VALUES (?,'DRAFT',?,?)",id,budget,allocated(tasks));
            for(var run:runs) db.update("INSERT INTO plan_analysis(plan_id,analysis_id,priority,review_snapshot_json) VALUES (?,?,?,CAST(? AS jsonb))",id,run.id(),requested.getOrDefault(run.job().id(),1),json.write(run.reviews()));
            for(var task:tasks) {
                db.update("INSERT INTO plan_task(id,plan_id,task_json,selected,effort_minutes) VALUES (?,?,CAST(? AS jsonb),?,?)",task.id(),id,json.write(task),task.selected(),task.effortMinutes());
                for(var req:task.requirementIds()) db.update("INSERT INTO task_requirement(task_id,requirement_id) VALUES (?,?)",task.id(),req);
            }
        });
        return get(id);
    }
    public Planning.Plan get(UUID id) {
        var plans=db.query("SELECT * FROM preparation_plan WHERE id=?",(r,n)->new Planning.Plan(id,r.getString("status"),r.getLong("version"),r.getInt("budget_minutes"),r.getInt("allocated_minutes"),sources(id),tasks(id),r.getObject("created_at",OffsetDateTime.class)),id);
        if(plans.isEmpty()) throw CompassException.missing("准备计划不存在");return plans.getFirst();
    }
    public List<Planning.Plan> list() { return db.query("SELECT id FROM preparation_plan ORDER BY created_at DESC LIMIT 100",(r,n)->r.getObject(1,UUID.class)).stream().map(this::get).toList(); }
    private List<Planning.Source> sources(UUID id) {
        return db.query("SELECT p.analysis_id,p.priority,a.job_snapshot_json FROM plan_analysis p JOIN analysis_run a ON a.id=p.analysis_id WHERE p.plan_id=? ORDER BY p.analysis_id",
            (r,n)->{var j=json.read(r.getString(3),Snapshots.Job.class);return new Planning.Source(r.getObject(1,UUID.class),j.id(),j.company(),j.title(),r.getInt(2),j.deadline());},id);
    }
    private List<Planning.Task> tasks(UUID id) {
        var tasks=db.query("SELECT * FROM plan_task WHERE plan_id=?",(r,n)->{
            var t=json.read(r.getString("task_json"),Planning.Task.class);
            return t.edit(t.title(),t.expectedOutput(),r.getInt("effort_minutes"),r.getBoolean("selected"),t.explanation(),r.getString("status"),r.getLong("version"));
        },id);
        return tasks.stream().sorted(Comparator.comparingDouble(Planning.Task::priority).reversed().thenComparing(Planning.Task::skillKey).thenComparing(Planning.Task::id)).toList();
    }
    private int allocated(List<Planning.Task> tasks) { return tasks.stream().filter(Planning.Task::selected).mapToInt(Planning.Task::effortMinutes).sum(); }
    private void lock(UUID id) { if(db.query("SELECT id FROM preparation_plan WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),id).isEmpty()) throw CompassException.missing("准备计划不存在"); }
    private void version(long actual,long expected) { if(actual!=expected) throw CompassException.conflict("内容已被修改，请刷新后重试"); }
    public Planning.Plan patch(UUID id,Planning.Patch request) {
        BudgetPlanner.budget(request.budgetMinutes());
        if(request.tasks()==null || request.tasks().size()>20) throw CompassException.bad("任务列表无效");
        tx.executeWithoutResult(s->{
            lock(id);var plan=get(id);if(!plan.status().equals("DRAFT")) throw CompassException.conflict("已确认计划内容冻结，请另建草稿");version(plan.version(),request.expectedVersion());
            var edits=new HashMap<UUID,Planning.TaskEdit>();
            for(var edit:request.tasks()) {
                if(edit==null || !plan.tasks().stream().anyMatch(t->t.id().equals(edit.id())) || edits.put(edit.id(),edit)!=null) throw CompassException.bad("任务 ID 无效或重复");
                Snapshots.field(edit.title(),"任务标题",200);Snapshots.field(edit.expectedOutput(),"可检查产物",500);BudgetPlanner.effort(edit.effortMinutes());
            }
            var updated=plan.tasks().stream().map(t->{var e=edits.get(t.id());return e==null?t:t.edit(e.title().strip(),e.expectedOutput().strip(),e.effortMinutes(),e.selected(),
                "用户调整；关联 "+t.jobIds().size()+" 个岗位；要求收益 "+t.benefit()+"；预计 "+e.effortMinutes()+" 分钟",t.status(),t.version()+1);}).toList();
            int minutes=allocated(updated);if(minutes>request.budgetMinutes()) throw CompassException.bad("选中任务总耗时超过预算，请取消部分任务或增加预算");
            int changed=db.update("UPDATE preparation_plan SET budget_minutes=?,allocated_minutes=?,version=version+1 WHERE id=? AND version=?",request.budgetMinutes(),minutes,id,request.expectedVersion());
            if(changed!=1) throw CompassException.conflict("计划版本冲突");
            for(var task:updated) if(edits.containsKey(task.id())) db.update("UPDATE plan_task SET task_json=CAST(? AS jsonb),selected=?,effort_minutes=?,version=version+1 WHERE id=?",json.write(task),task.selected(),task.effortMinutes(),task.id());
            event("PLAN",id,"DRAFT","DRAFT_EDITED",plan.version()+1);
        });return get(id);
    }
    private String confirmationHash(Planning.Plan plan) {
        var content=plan.tasks().stream().filter(Planning.Task::selected).sorted(Comparator.comparing(Planning.Task::id))
            .map(t->List.of(t.id(),t.title(),t.expectedOutput(),t.effortMinutes())).toList();
        return Snapshots.hash(json.write(List.of(plan.id(),plan.budgetMinutes(),content)));
    }
    public Planning.Plan confirm(UUID id,String key,long expectedVersion) {
        Snapshots.field(key,"幂等键",120);
        try {
            tx.executeWithoutResult(s->{
                lock(id);var plan=get(id);String hash=confirmationHash(plan);
                var prior=db.query("SELECT id,confirmation_hash FROM preparation_plan WHERE confirmation_key=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),key);
                if(!prior.isEmpty()) { if(!prior.getFirst().getKey().equals(id) || !prior.getFirst().getValue().equals(hash)) throw CompassException.conflict("幂等键已用于不同内容或计划");return; }
                if(plan.status().equals("CONFIRMED")) return;
                if(!plan.status().equals("DRAFT")) throw CompassException.conflict("计划状态不允许确认");version(plan.version(),expectedVersion);
                if(plan.allocatedMinutes()>plan.budgetMinutes()) throw CompassException.bad("计划超出预算");
                db.update("UPDATE preparation_plan SET status='CONFIRMED',confirmation_key=?,confirmation_hash=?,confirmed_at=now(),version=version+1 WHERE id=? AND version=?",key,hash,id,expectedVersion);
                for(var source:plan.sources()) {
                    UUID applicationId=UUID.randomUUID();int inserted=db.update("INSERT INTO job_application(id,job_id,status) VALUES (?,?,'PLANNED') ON CONFLICT(job_id) DO NOTHING",applicationId,source.jobId());
                    if(inserted==1) event("APPLICATION",applicationId,null,"PLANNED",0);
                }
                event("PLAN",id,"DRAFT","CONFIRMED",plan.version()+1);
            });
        } catch(DataIntegrityViolationException e) {
            var prior=db.query("SELECT id,confirmation_hash FROM preparation_plan WHERE confirmation_key=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),key);
            if(prior.isEmpty() || !prior.getFirst().getKey().equals(id) || !prior.getFirst().getValue().equals(confirmationHash(get(id)))) throw CompassException.conflict("确认冲突，请刷新");
        }
        return get(id);
    }
    public Planning.Plan taskStatus(UUID planId,UUID taskId,String status,long expectedVersion) {
        if(status==null || !Set.of("TODO","DOING","DONE","SKIPPED").contains(status)) throw CompassException.bad("任务状态无效");
        tx.executeWithoutResult(s->{
            lock(planId);var plan=get(planId);if(!plan.status().equals("CONFIRMED")) throw CompassException.conflict("只有已确认计划才能更新任务");
            var task=plan.tasks().stream().filter(t->t.id().equals(taskId)).findFirst().orElseThrow(()->CompassException.missing("任务不属于该计划"));
            if(!task.selected()) throw CompassException.conflict("待安排任务不能更新进度");version(task.version(),expectedVersion);
            if(task.status().equals(status)) return;
            var transitions=Map.of("TODO",Set.of("DOING","SKIPPED"),"DOING",Set.of("TODO","DONE","SKIPPED"),"DONE",Set.of("TODO","DOING"),"SKIPPED",Set.of("TODO"));
            if(!transitions.get(task.status()).contains(status)) throw CompassException.conflict("不允许该状态转换，请按准备流程操作");
            if(db.update("UPDATE plan_task SET status=?,version=version+1 WHERE id=? AND version=?",status,taskId,expectedVersion)!=1) throw CompassException.conflict("任务版本冲突");
            event("TASK",taskId,task.status(),status,expectedVersion+1);
        });return get(planId);
    }
    public List<Planning.Application> applications() {
        return db.query("SELECT a.*,j.title,j.company FROM job_application a JOIN job_descriptions j ON j.id=a.job_id ORDER BY a.updated_at DESC",
            (r,n)->new Planning.Application(r.getObject("id",UUID.class),r.getObject("job_id",UUID.class),r.getString("title"),r.getString("company"),r.getString("status"),r.getString("note"),r.getLong("version")));
    }
    public Planning.Application applicationStatus(UUID id,String status,String note,long expectedVersion) {
        if(status==null || !Set.of("PLANNED","APPLIED","INTERVIEW","OFFER","REJECTED","WITHDRAWN").contains(status) || note==null || note.length()>1000) throw CompassException.bad("申请状态或备注无效");
        tx.executeWithoutResult(s->{
            var items=db.query("SELECT status,version FROM job_application WHERE id=?",(r,n)->Map.entry(r.getString(1),r.getLong(2)),id);
            if(items.isEmpty()) throw CompassException.missing("申请不存在");version(items.getFirst().getValue(),expectedVersion);
            if(db.update("UPDATE job_application SET status=?,note=?,version=version+1,updated_at=now() WHERE id=? AND version=?",status,note,id,expectedVersion)!=1) throw CompassException.conflict("申请版本冲突");
            event("APPLICATION",id,items.getFirst().getKey(),status,expectedVersion+1);
        });return applications().stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
    }
    public List<Planning.Event> events(UUID resourceId) {
        String where=resourceId==null?"":" WHERE resource_id=?";
        return db.query("SELECT * FROM activity_event"+where+" ORDER BY occurred_at DESC LIMIT 100",
            (r,n)->new Planning.Event(r.getObject("id",UUID.class),r.getString("resource_type"),r.getObject("resource_id",UUID.class),r.getString("old_value"),r.getString("new_value"),r.getLong("version"),r.getObject("occurred_at",OffsetDateTime.class)),resourceId==null?new Object[0]:new Object[]{resourceId});
    }
    public List<Planning.Event> planEvents(UUID planId) {
        get(planId);
        return db.query("SELECT * FROM activity_event WHERE resource_id=? OR resource_id IN (SELECT id FROM plan_task WHERE plan_id=?) ORDER BY occurred_at DESC LIMIT 100",
            (r,n)->new Planning.Event(r.getObject("id",UUID.class),r.getString("resource_type"),r.getObject("resource_id",UUID.class),r.getString("old_value"),r.getString("new_value"),r.getLong("version"),r.getObject("occurred_at",OffsetDateTime.class)),planId,planId);
    }
    private void event(String type,UUID id,String oldValue,String newValue,long version) {
        db.update("INSERT INTO activity_event(id,resource_type,resource_id,old_value,new_value,version) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),type,id,oldValue,newValue,version);
    }
}
