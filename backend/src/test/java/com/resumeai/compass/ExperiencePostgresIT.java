package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import com.resumeai.compass.experience.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;

/** Explicit PostgreSQL gate; reuses the existing disposable-schema fixture, never user records. */
class ExperiencePostgresIT {
    @BeforeAll static void setup() {
        CompassPostgresIT.setup();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V10__compass_experience_inquiries.sql")).execute(CompassPostgresIT.ds);
    }
    @AfterAll static void cleanup() { CompassPostgresIT.cleanup(); }

    record Fixture(EvidenceService evidence, ExperienceService experience, EvidenceService.Run run) implements AutoCloseable {
        UUID requirementId() { return run.report().requirements().getFirst().id(); }
        @Override public void close() { experience.close(); evidence.close(); }
    }

    static Experience.QuestionOutput questions(Experience.Context context) {
        assertThat(context.resume().text()).contains("预约");
        return new Experience.QuestionOutput(List.of(
                new Experience.QuestionDraft("SCENARIO","预约功能遇到重复请求时，实际发生了什么？"),
                new Experience.QuestionDraft("IMPLEMENTATION","你本人如何实现预约防重复，负责什么部分？"),
                new Experience.QuestionDraft("VALIDATION","如何验证重复请求的结果？未验证可直接说明。")));
    }
    static Fixture fixture(ExperienceModel model) throws Exception {
        var db = CompassPostgresIT.db; var json = CompassPostgresIT.json; var snapshots = CompassPostgresIT.snapshots;
        var version = snapshots.createText("追问测试", "使用 Spring Boot 完成预约接口。");
        var job = snapshots.createJob("Java 实习", "测试公司" + UUID.randomUUID(), "实现预约接口并避免重复提交。", null,null,null);
        var evidence = CompassPostgresIT.service(CompassPostgresIT.model());
        var run = CompassPostgresIT.await(evidence,evidence.submit(version.id(),job.id(),null).id());
        var experience = new ExperienceService(db,json,evidence,snapshots,model,new ExperienceValidator(),CompassPostgresIT.manager,new MockEnvironment());
        return new Fixture(evidence,experience,run);
    }
    static Experience.Session await(Fixture fixture, UUID id) throws Exception {
        for (int i=0;i<200;i++) {
            var current=fixture.experience().get(fixture.run().id(),id);
            if (!current.status().equals("QUESTIONS_PENDING")) return current;
            Thread.sleep(20);
        }
        throw new AssertionError("Questions did not finish");
    }
    static List<Experience.Answer> answers(String implementation) {
        return List.of(new Experience.Answer("q1","连续点击产生重复预约请求。"),new Experience.Answer("q2",implementation),new Experience.Answer("q3","只做了手工验证，没有压测，不能声称提升性能。"));
    }
    static Experience.Session draft(Fixture f) throws Exception {
        var session=await(f,f.experience().start(f.run().id(),f.requirementId()).id());
        session=f.experience().saveAnswers(f.run().id(),session.id(),session.version(),answers("本人使用数据库唯一约束，不记得具体异常处理。"));
        return f.experience().draft(f.run().id(),session.id(),session.version());
    }

    @Test void completeFlowPreservesOriginalFactsAndKeepsConfirmedHistoryWhenEdited() throws Exception {
        try(var f=fixture((context,correction)->questions(context))) {
            var original=f.evidence().get(f.run().id()); var originalText=CompassPostgresIT.snapshots.version(original.resumeVersionId()).text();
            var draft=draft(f);
            assertThat(draft.draft().sourceType()).isEqualTo("USER_SUPPLIED");
            var confirmed=f.experience().confirm(f.run().id(),draft.id(),draft.version(),true);
            assertThat(confirmed.status()).isEqualTo("CONFIRMED"); assertThat(confirmed.cards()).hasSize(1);
            assertThat(confirmed.cards().getFirst().sourceType()).isEqualTo("USER_CONFIRMED");
            assertThat(confirmed.cards().getFirst().content().facts().get(2).quote()).contains("没有压测");
            var reopened=f.experience().reopen(f.run().id(),confirmed.id(),confirmed.version());
            var edited=f.experience().saveAnswers(f.run().id(),reopened.id(),reopened.version(),answers("补充异常捕获，向用户提示已预约。"));
            var next=f.experience().draft(f.run().id(),edited.id(),edited.version());
            next=f.experience().confirm(f.run().id(),next.id(),next.version(),true);
            assertThat(next.cards()).hasSize(2);
            assertThat(next.cards().stream().anyMatch(c->c.content().facts().get(1).quote().contains("不记得"))).isTrue();
            assertThat(f.evidence().get(f.run().id()).report()).isEqualTo(original.report());
            assertThat(f.evidence().get(f.run().id()).reviews()).isEqualTo(original.reviews());
            assertThat(CompassPostgresIT.snapshots.version(original.resumeVersionId()).text()).isEqualTo(originalText);
        }
    }

    @Test void foreignReportRequirementAndForeignSessionAreRejected() throws Exception {
        try(var a=fixture((c,x)->questions(c));var b=fixture((c,x)->questions(c))) {
            assertThatThrownBy(()->a.experience().start(a.run().id(),b.requirementId())).isInstanceOf(CompassException.class);
            var session=await(a,a.experience().start(a.run().id(),a.requirementId()).id());
            assertThatThrownBy(()->a.experience().get(b.run().id(),session.id())).isInstanceOf(CompassException.class);
            assertThatThrownBy(()->a.experience().saveAnswers(b.run().id(),session.id(),session.version(),answers("测试"))).isInstanceOf(CompassException.class);
            CompassPostgresIT.db.update("UPDATE analysis_run SET status='FAILED' WHERE id=?",b.run().id());
            assertThatThrownBy(()->b.experience().start(b.run().id(),b.requirementId())).isInstanceOf(CompassException.class);
        }
    }

    @Test void staleAnswersAndUnconfirmedTruthCannotOverwriteOrConfirmCard() throws Exception {
        try(var f=fixture((c,x)->questions(c))) {
            var first=await(f,f.experience().start(f.run().id(),f.requirementId()).id());
            var saved=f.experience().saveAnswers(f.run().id(),first.id(),first.version(),answers("数据库唯一约束"));
            assertThatThrownBy(()->f.experience().saveAnswers(f.run().id(),first.id(),first.version(),answers("覆盖"))).isInstanceOf(CompassException.class);
            var draft=f.experience().draft(f.run().id(),saved.id(),saved.version());
            assertThatThrownBy(()->f.experience().confirm(f.run().id(),draft.id(),draft.version(),false)).isInstanceOf(CompassException.class);
            assertThat(f.experience().get(f.run().id(),draft.id()).cards()).isEmpty();
            var confirmed=f.experience().confirm(f.run().id(),draft.id(),draft.version(),true);
            assertThatThrownBy(()->f.experience().saveAnswers(f.run().id(),confirmed.id(),confirmed.version(),answers("直接改确认卡"))).isInstanceOf(CompassException.class);
        }
    }

    @Test void duplicateConcurrentStartsAndConfirmationsOnlyCreateOneSessionAndOneCard() throws Exception {
        var calls=new AtomicInteger();
        try(var f=fixture((c,x)->{calls.incrementAndGet();return questions(c);});var pool=Executors.newFixedThreadPool(2)) {
            var starts=pool.invokeAll(List.<Callable<UUID>>of(()->f.experience().start(f.run().id(),f.requirementId()).id(),()->f.experience().start(f.run().id(),f.requirementId()).id()));
            UUID id=starts.getFirst().get(); assertThat(starts.get(1).get()).isEqualTo(id);
            var session=await(f,id);assertThat(calls.get()).isEqualTo(1);
            session=f.experience().saveAnswers(f.run().id(),id,session.version(),answers("使用唯一约束"));
            var draft=f.experience().draft(f.run().id(),id,session.version());
            var results=pool.invokeAll(List.<Callable<String>>of(()->f.experience().confirm(f.run().id(),id,draft.version(),true).status(),()->f.experience().confirm(f.run().id(),id,draft.version(),true).status()));
            assertThat(results.getFirst().get()).isEqualTo("CONFIRMED");assertThat(results.get(1).get()).isEqualTo("CONFIRMED");
            assertThat(f.experience().get(f.run().id(),id).cards()).hasSize(1);
            assertThat(CompassPostgresIT.db.queryForObject("SELECT count(*) FROM activity_event WHERE resource_id=? AND new_value='USER_CONFIRMED'",Integer.class,id)).isEqualTo(1);
        }
    }

    @Test void invalidModelOutputFailsThenRetryUsesSameFixedContext() throws Exception {
        var calls=new AtomicInteger();
        try(var f=fixture((c,x)-> calls.incrementAndGet()<=2 ? new Experience.QuestionOutput(List.of()) : questions(c))) {
            var failed=await(f,f.experience().start(f.run().id(),f.requirementId()).id());
            assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.errorCode()).isEqualTo("INVALID_QUESTIONS");assertThat(calls.get()).isEqualTo(2);
            var retry=f.experience().retry(f.run().id(),failed.id(),failed.version());
            assertThat(await(f,retry.id()).status()).isEqualTo("ANSWERING");
            assertThat(f.evidence().get(f.run().id()).status()).isEqualTo("SUCCEEDED");
        }
    }

    @Test void confirmationEventFailureRollsBackCardAndStateAtomically() throws Exception {
        try(var f=fixture((c,x)->questions(c))) {
            var draft=draft(f);var db=CompassPostgresIT.db;
            db.execute("CREATE FUNCTION fail_experience_confirmation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.new_value='USER_CONFIRMED' THEN RAISE EXCEPTION 'injected confirmation failure'; END IF; RETURN NEW; END $$");
            db.execute("CREATE TRIGGER fail_experience_confirmation BEFORE INSERT ON activity_event FOR EACH ROW EXECUTE FUNCTION fail_experience_confirmation()");
            try {
                assertThatThrownBy(()->f.experience().confirm(f.run().id(),draft.id(),draft.version(),true)).isInstanceOf(RuntimeException.class);
                var unchanged=f.experience().get(f.run().id(),draft.id());assertThat(unchanged.status()).isEqualTo("CARD_DRAFT");assertThat(unchanged.cards()).isEmpty();assertThat(unchanged.version()).isEqualTo(draft.version());
            } finally {db.execute("DROP TRIGGER fail_experience_confirmation ON activity_event");db.execute("DROP FUNCTION fail_experience_confirmation()");}
            assertThat(f.experience().confirm(f.run().id(),draft.id(),draft.version(),true).cards()).hasSize(1);
        }
    }

    @Test void emptyCardIsRejectedWhilePartialAnswersRemainExplicitlyPartial() throws Exception {
        try(var f=fixture((c,x)->questions(c))) {
            var session=await(f,f.experience().start(f.run().id(),f.requirementId()).id());
            var empty=f.experience().saveAnswers(f.run().id(),session.id(),session.version(),List.of(new Experience.Answer("q1",""),new Experience.Answer("q2",""),new Experience.Answer("q3","")));
            assertThatThrownBy(()->f.experience().draft(f.run().id(),empty.id(),empty.version())).isInstanceOf(CompassException.class);
            var saved=f.experience().saveAnswers(f.run().id(),empty.id(),empty.version(),List.of(new Experience.Answer("q1","没有做过，不清楚。"),new Experience.Answer("q2",""),new Experience.Answer("q3","")));
            var draft=f.experience().draft(f.run().id(),saved.id(),saved.version());
            assertThat(draft.draft().facts()).hasSize(1);assertThat(draft.draft().facts().getFirst().quote()).isEqualTo("没有做过，不清楚。");
        }
    }

    @Test void recoveryPreservesAnswersAndMarksOnlyPendingInquiriesInterrupted() throws Exception {
        try(var f=fixture((c,x)->questions(c))) {
            var draft=draft(f);
            f.experience().recover();
            assertThat(f.experience().get(f.run().id(),draft.id()).status()).isEqualTo("CARD_DRAFT");
            CompassPostgresIT.db.update("UPDATE experience_inquiry SET status='QUESTIONS_PENDING' WHERE id=?",draft.id());
            f.experience().recover();
            var interrupted=f.experience().get(f.run().id(),draft.id());
            assertThat(interrupted.status()).isEqualTo("FAILED");assertThat(interrupted.errorCode()).isEqualTo("INTERRUPTED");assertThat(interrupted.answers()).isEqualTo(draft.answers());
        }
    }

    @Test void boundedQueueRejectsExcessWorkAndRetainsFailureRecord() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var f=fixture((c,x)->{
            entered.countDown();
            try { if(!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test gate timeout"); }
            catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
            return questions(c);
        })) {
            var requirements=new ArrayList<>(f.run().report().requirements());
            var assessments=new ArrayList<>(f.run().report().assessments());
            var original=requirements.getFirst();var originalAssessment=assessments.getFirst();
            for(int i=0;i<5;i++) {
                UUID id=UUID.randomUUID();var requirement=new Evidence.Requirement(id,original.text()+i,original.kind(),original.skillKey(),original.jdEvidence());
                var assessment=new Evidence.Assessment(id,originalAssessment.status(),originalAssessment.resumeEvidence(),originalAssessment.reason(),originalAssessment.actionType(),originalAssessment.taskTitle(),originalAssessment.expectedOutput(),originalAssessment.effortMinutes());
                CompassPostgresIT.db.update("INSERT INTO analysis_requirement(id,analysis_id,requirement_json,assessment_json) VALUES (?,?,CAST(? AS jsonb),CAST(? AS jsonb))",id,f.run().id(),CompassPostgresIT.json.write(requirement),CompassPostgresIT.json.write(assessment));
                requirements.add(requirement);assessments.add(assessment);
            }
            CompassPostgresIT.db.update("UPDATE analysis_run SET report_json=CAST(? AS jsonb) WHERE id=?",CompassPostgresIT.json.write(new Evidence.Report(requirements,assessments)),f.run().id());
            var ids=new ArrayList<UUID>();
            try {
                ids.add(f.experience().start(f.run().id(),requirements.getFirst().id()).id());assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
                for(int i=1;i<5;i++) ids.add(f.experience().start(f.run().id(),requirements.get(i).id()).id());
                assertThatThrownBy(()->f.experience().start(f.run().id(),requirements.get(5).id())).isInstanceOfSatisfying(CompassException.class,e->assertThat(e.status).isEqualTo(429));
                assertThat(f.experience().list(f.run().id())).anyMatch(s->s.status().equals("FAILED")&&"QUEUE_FULL".equals(s.errorCode()));
            } finally {release.countDown();}
            for(UUID id:ids) assertThat(await(f,id).status()).isEqualTo("ANSWERING");
        }
    }
}
