package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

/** Explicit database gate: mvn -Dtest=CompassPostgresIT test. Uses a disposable schema, never application rows. */
class CompassPostgresIT {
    static JdbcTemplate db, admin;
    static DriverManagerDataSource ds;
    static DataSourceTransactionManager manager;
    static CompassJson json;
    static SnapshotService snapshots;
    static String schema;
    @BeforeAll static void setup() {
        String url=System.getenv().getOrDefault("COMPASS_TEST_DB_URL","jdbc:postgresql://localhost:5432/resume_ai");
        String user=System.getenv().getOrDefault("COMPASS_TEST_DB_USER","resume_ai");
        String password=System.getenv().getOrDefault("COMPASS_TEST_DB_PASSWORD","resume_ai");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        schema="compass_it_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        ds=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema+",public",user,password);
        db=new JdbcTemplate(ds); manager=new DataSourceTransactionManager(ds);
        db.execute("CREATE TABLE resumes(id uuid PRIMARY KEY,title varchar(200),raw_text text,status varchar(40),original_filename varchar(500),content_type varchar(120),object_key varchar(700),created_at timestamptz DEFAULT now())");
        db.execute("CREATE TABLE job_descriptions(id uuid PRIMARY KEY,title varchar(200),company varchar(200),description text,status varchar(40),created_at timestamptz DEFAULT now())");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__compass_input_versions.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__compass_evidence_runs.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V7__compass_preparation_plans.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V8__compass_review_skill_override.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__compass_confirmed_review.sql")).execute(ds);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__analysis_metrics.sql")).execute(ds);
        json=new CompassJson(new ObjectMapper().findAndRegisterModules()); snapshots=new SnapshotService(db,json,manager);
    }
    @AfterAll static void cleanup() {
        if(admin!=null && schema!=null && schema.matches("compass_it_[a-f0-9]{32}")) admin.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    @Test void snapshotsRemainImmutableAndReferencedParentsCannotBeDeleted() {
        var v1=snapshots.createText("版本测试","使用 Redis 缓存");
        var v2=snapshots.createVersion(v1.resumeId(),"实现缓存更新与失效处理");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(snapshots.version(v1.id()).text()).isEqualTo("使用 Redis 缓存");
        assertThat(v2.hash()).isNotEqualTo(v1.hash());
        assertThatThrownBy(()->db.update("DELETE FROM resumes WHERE id=?",v1.resumeId())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentVersionCreationIsSerializedByParentLock() throws Exception {
        var v1=snapshots.createText("并发测试","原始正文");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var results=pool.invokeAll(List.<Callable<Integer>>of(
                ()->snapshots.createVersion(v1.resumeId(),"新正文 A").versionNo(),
                ()->snapshots.createVersion(v1.resumeId(),"新正文 B").versionNo()));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(2,3);
        }
    }
    @Test void duplicateJobsReuseIdentityWithoutChangingContent() {
        var a=snapshots.createJob("Java 实习","测试公司","熟悉 Redis",null,null,null);
        var b=snapshots.createJob("Java 实习","测试公司","熟悉 Redis",null,null,null);
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(snapshots.job(a.id()).segments().getFirst().id()).isEqualTo("j1");
    }
    static EvidenceService service(EvidenceModel model) {
        return new EvidenceService(db,json,snapshots,model,manager,new org.springframework.mock.env.MockEnvironment());
    }
    static EvidenceService.Run await(EvidenceService service,UUID id) throws Exception {
        for(int i=0;i<200;i++) { var run=service.get(id); if(run.status().equals("SUCCEEDED")||run.status().equals("FAILED")) return run; Thread.sleep(20); }
        throw new AssertionError("Run did not finish");
    }
    static EvidenceModel model() {
        return (id,v,j,h)-> {
            var q=new Evidence.Requirement(UUID.randomUUID(),"缓存一致性","REQUIRED","redis.cache_consistency",new Evidence.Reference("j1",j.segments().getFirst().text()));
            var a=new Evidence.Assessment(q.id(),"UNCERTAIN",List.of(),"需要复核真实项目细节","CLARIFY","核实缓存更新策略","记录项目实际策略与验证结果",30);
            return new EvidenceModel.Result(new Evidence.Report(List.of(q),List.of(a)),2);
        };
    }
    @Test void telemetryPersistsForSuccessAndFailureAndLegacyRunsHaveNoFakeMetrics() throws Exception {
        var v=snapshots.createText("统计测试","项目使用 Redis");var j=snapshots.createJob("Java","统计虚构公司","需要缓存一致性",null,null,null);
        var metrics=new AnalysisMetrics("FULL");var a=metrics.begin("EXTRACT",1);a.finish(1,null);var b=metrics.begin("ASSESS",1);b.finish(1,null);
        var service=service((id,version,job,h)->new EvidenceModel.Result(model().analyze(id,version,job,h).report(),2,metrics.snapshot()));
        try { var run=await(service,service.submit(v.id(),j.id(),null).id());assertThat(run.status()).isEqualTo("SUCCEEDED");
            assertThat(service.get(run.id()).metrics().attempts()).hasSize(2);assertThat(run.metrics().usageComplete()).isFalse(); }
        finally { service.close(); }
        var failedMetrics=new AnalysisMetrics("FULL");var failed=failedMetrics.begin("EXTRACT",1);failed.finish(1,"MODEL_FAILED");
        var failing=service((id,version,job,h)->{throw new CompassException(502,"MODEL_FAILED","测试失败").withMetrics(failedMetrics.snapshot());});
        try { var run=await(failing,failing.submit(v.id(),j.id(),null).id());assertThat(run.status()).isEqualTo("FAILED");assertThat(run.metrics().modelFailures()).isEqualTo(1);assertThat(run.toolCalls()).isEqualTo(1); }
        finally { failing.close(); }
        var legacy=service(model());try { assertThat(await(legacy,legacy.submit(v.id(),j.id(),null).id()).metrics()).isNull(); } finally { legacy.close(); }
    }
    @Test void modelFailureAndUserReviewNeverRewriteEvidence() throws Exception {
        var v=snapshots.createText("证据测试","项目使用 Redis"); var j=snapshots.createJob("Java","证据公司","需要缓存一致性",null,null,null);
        var service=service(model());
        try {
            var run=await(service,service.submit(v.id(),j.id(),null).id());
            assertThat(run.status()).isEqualTo("SUCCEEDED");
            var req=run.report().requirements().getFirst().id();
            var reviewed=service.review(run.id(),List.of(new Evidence.Review(req,"EVIDENCE_FIX","实际实现过，需要补充表述",0)));
            assertThat(reviewed.report()).isEqualTo(run.report()); assertThat(reviewed.reviews().getFirst().version()).isEqualTo(1);
            assertThatThrownBy(()->service.review(run.id(),List.of(new Evidence.Review(req,"LEARN","过期编辑",0)))).isInstanceOf(CompassException.class);
        } finally { service.close(); }
        var failing=service((id,version,job,history)->{throw new CompassException(502,"MODEL_FAILED","模拟服务失败");});
        try {
            var failed=await(failing,failing.submit(v.id(),j.id(),null).id());
            assertThat(failed.status()).isEqualTo("FAILED"); assertThat(failed.report()).isNull();
            assertThat(db.queryForObject("SELECT count(*) FROM analysis_requirement WHERE analysis_id=?",Integer.class,failed.id())).isZero();
            db.update("UPDATE job_descriptions SET description='已改动的岗位文本' WHERE id=?",j.id());
            var retry=await(failing,failing.retry(failed.id()).id());
            assertThat(retry.job().text()).isEqualTo("需要缓存一致性");
        } finally { failing.close(); }
    }
    @Test void multiJobDraftMergesSourcesAndRejectsInvalidBudgetOrMixedVersions() throws Exception {
        var v=snapshots.createText("计划测试","项目使用 Redis");var service=service(model());
        try {
            var j1=snapshots.createJob("Java A","规划公司","需要缓存一致性",null,null,null);
            var j2=snapshots.createJob("Java B","规划公司","需要缓存一致性",null,null,null);
            var a=await(service,service.submit(v.id(),j1.id(),null).id());var b=await(service,service.submit(v.id(),j2.id(),null).id());
            var plans=new PlanningService(db,json,service,new BudgetPlanner(),manager);
            var plan=plans.create(List.of(a.id(),b.id()),480,java.util.Map.of());
            assertThat(plan.tasks()).hasSize(1);assertThat(plan.tasks().getFirst().jobIds()).hasSize(2);assertThat(plan.sources()).hasSize(2);
            assertThat(db.queryForObject("SELECT count(*) FROM job_application WHERE job_id IN (?,?)",Integer.class,j1.id(),j2.id())).isZero();
            var task=plan.tasks().getFirst();
            assertThatThrownBy(()->plans.patch(plan.id(),new Planning.Patch(0,30,List.of(new Planning.TaskEdit(task.id(),task.title(),task.expectedOutput(),120,true))))).isInstanceOf(CompassException.class);
            assertThat(plans.get(plan.id()).version()).isZero();assertThat(plans.get(plan.id()).allocatedMinutes()).isEqualTo(30);
            var edited=plans.patch(plan.id(),new Planning.Patch(0,480,List.of(new Planning.TaskEdit(task.id(),task.title(),task.expectedOutput(),120,true))));
            assertThat(edited.version()).isEqualTo(1);assertThat(edited.allocatedMinutes()).isEqualTo(120);
            var v2=snapshots.createVersion(v.resumeId(),"新的简历版本");var other=await(service,service.submit(v2.id(),j2.id(),null).id());
            assertThatThrownBy(()->plans.create(List.of(a.id(),other.id()),480,java.util.Map.of())).isInstanceOf(CompassException.class);
        } finally { service.close(); }
    }
    @Test void concurrentConfirmationIsIdempotentAndApplicationsAreReused() throws Exception {
        var v=snapshots.createText("确认测试","项目使用 Redis");var j=snapshots.createJob("Java 确认","幂等公司","需要缓存一致性",null,null,null);var service=service(model());
        try {
            var run=await(service,service.submit(v.id(),j.id(),null).id());var plans=new PlanningService(db,json,service,new BudgetPlanner(),manager);
            var plan=plans.create(List.of(run.id()),480,java.util.Map.of());String key=UUID.randomUUID().toString();
            try(var pool=Executors.newFixedThreadPool(2)) {
                var futures=pool.invokeAll(List.<Callable<Planning.Plan>>of(()->plans.confirm(plan.id(),key,0),()->plans.confirm(plan.id(),key,0)));
                assertThat(futures.get(0).get().version()).isEqualTo(1);assertThat(futures.get(1).get().id()).isEqualTo(plan.id());
            }
            assertThat(db.queryForObject("SELECT count(*) FROM activity_event WHERE resource_id=? AND new_value='CONFIRMED'",Integer.class,plan.id())).isEqualTo(1);
            var app=plans.applications().stream().filter(a->a.jobId().equals(j.id())).findFirst().orElseThrow();
            plans.applicationStatus(app.id(),"APPLIED","已投递",app.version());
            assertThatThrownBy(()->plans.applicationStatus(app.id(),"INTERVIEW","过期编辑",app.version())).isInstanceOf(CompassException.class);
            var second=plans.create(List.of(run.id()),480,java.util.Map.of());
            assertThatThrownBy(()->plans.confirm(second.id(),key,0)).isInstanceOf(CompassException.class);
            assertThat(plans.get(second.id()).status()).isEqualTo("DRAFT");
            plans.confirm(second.id(),UUID.randomUUID().toString(),0);
            assertThat(plans.applications().stream().filter(a->a.jobId().equals(j.id())).findFirst().orElseThrow().status()).isEqualTo("APPLIED");
            var task=plans.get(plan.id()).tasks().getFirst();
            plans.taskStatus(plan.id(),task.id(),"DOING",0);
            assertThatThrownBy(()->plans.taskStatus(plan.id(),task.id(),"DONE",0)).isInstanceOf(CompassException.class);
            assertThat(plans.taskStatus(plan.id(),task.id(),"DONE",1).tasks().getFirst().status()).isEqualTo("DONE");
        } finally { service.close(); }
    }
    @Test void confirmationFailureRollsBackPlanApplicationsAndEvents() throws Exception {
        var v=snapshots.createText("事务测试","项目使用 Redis");var j=snapshots.createJob("Java 事务","回滚公司","需要缓存一致性",null,null,null);var service=service(model());
        try {
            var run=await(service,service.submit(v.id(),j.id(),null).id());var plans=new PlanningService(db,json,service,new BudgetPlanner(),manager);
            var plan=plans.create(List.of(run.id()),480,java.util.Map.of());
            db.execute("CREATE FUNCTION reject_test_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.resource_id='"+plan.id()+"'::uuid AND NEW.new_value='CONFIRMED' THEN RAISE EXCEPTION 'simulated event write failure'; END IF; RETURN NEW; END; $$");
            db.execute("CREATE TRIGGER reject_confirmation BEFORE INSERT ON activity_event FOR EACH ROW EXECUTE FUNCTION reject_test_confirmation()");
            try { assertThatThrownBy(()->plans.confirm(plan.id(),UUID.randomUUID().toString(),0)).isInstanceOf(RuntimeException.class); }
            finally { db.execute("DROP TRIGGER reject_confirmation ON activity_event");db.execute("DROP FUNCTION reject_test_confirmation()"); }
            assertThat(plans.get(plan.id()).status()).isEqualTo("DRAFT");assertThat(plans.get(plan.id()).version()).isZero();
            assertThat(db.queryForObject("SELECT count(*) FROM job_application WHERE job_id=?",Integer.class,j.id())).isZero();
            assertThat(plans.events(plan.id())).isEmpty();
        } finally { service.close(); }
    }
    @Test void interruptedRunsAreRecoveredWithoutSilentRerun() {
        var v=snapshots.createText("重启测试","项目使用 Redis");var j=snapshots.createJob("Java 重启","恢复公司","需要缓存一致性",null,null,null);UUID id=UUID.randomUUID();
        db.update("INSERT INTO analysis_run(id,resume_version_id,job_id,job_snapshot_json,status) VALUES (?,?,?,CAST(? AS jsonb),'RUNNING')",id,v.id(),j.id(),json.write(j));
        var service=service(model());try { service.recover();assertThat(service.get(id).errorCode()).isEqualTo("INTERRUPTED");assertThat(service.get(id).report()).isNull(); } finally { service.close(); }
    }
    @Test void executorRejectsOverflowAndRetainsFailureRecord() throws Exception {
        var v=snapshots.createText("队列测试","项目使用 Redis");var j=snapshots.createJob("Java 队列","队列公司","需要缓存一致性",null,null,null);
        var release=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.CountDownLatch(2);var ids=new java.util.ArrayList<UUID>();
        var service=service((id,version,job,history)->{entered.countDown();try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new CompassException(500,"INTERRUPTED","中断"); } return model().analyze(id,version,job,history);});
        try {
            for(int i=0;i<10;i++) ids.add(service.submit(v.id(),j.id(),null).id());
            assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->service.submit(v.id(),j.id(),null)).isInstanceOf(CompassException.class).hasFieldOrPropertyWithValue("status",429);
            assertThat(db.queryForObject("SELECT count(*) FROM analysis_run WHERE resume_version_id=? AND error_code='QUEUE_FULL'",Integer.class,v.id())).isEqualTo(1);
            release.countDown();for(var id:ids) assertThat(await(service,id).status()).isEqualTo("SUCCEEDED");
        } finally { release.countDown();service.close(); }
    }
}
