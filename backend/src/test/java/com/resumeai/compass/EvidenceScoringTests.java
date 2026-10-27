package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.time.OffsetDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class EvidenceScoringTests {
    final EvidenceScoring scoring=new EvidenceScoring();
    final EvidenceValidator validator=new EvidenceValidator();
    Evidence.Requirement req(String group,String criterion,String kind,String category,String logic) {
        return new Evidence.Requirement(UUID.randomUUID(),criterion,kind,criterion,new Evidence.Reference("j1",criterion),group,criterion,category,logic,List.of());
    }
    Evidence.Assessment assessment(Evidence.Requirement r,String status) {
        return new Evidence.Assessment(r.id(),status,List.of(new Evidence.Reference("r1","项目实践")),"核对依据","NONE",null,null,0);
    }
    Evidence.Report report(List<Evidence.Requirement> reqs,String... statuses) {
        var list=new ArrayList<Evidence.Assessment>();for(int i=0;i<reqs.size();i++) list.add(assessment(reqs.get(i),statuses[i]));
        return new Evidence.Report(reqs,List.copyOf(list),"evidence-v3",true,List.of());
    }
    @Test void weightsHaveTransparentContributions() {
        var reqs=new ArrayList<Evidence.Requirement>();
        for(int i=0;i<7;i++) reqs.add(req("g"+i,"c"+i,i<5?"REQUIRED":"PREFERRED","SKILL","ALL"));
        var s=scoring.calculate(report(reqs,"MATCH","MATCH","MATCH","UNCERTAIN","UNCERTAIN","MATCH","GAP"));
        assertThat(s.coverageScore()).isEqualTo(58.82);assertThat(s.uncertainPercent()).isEqualTo(35.29);assertThat(s.gapPercent()).isEqualTo(5.88);
        assertThat(s.totalWeight()).isEqualTo(17);assertThat(s.groups()).hasSize(7);
    }
    @Test void allGroupSharesOneBudgetAndOrderDoesNotChangeScore() {
        var a=req("java","io","REQUIRED","SKILL","ALL");var b=req("java","jvm","REQUIRED","SKILL","ALL");
        var r=report(List.of(a,b),"MATCH","UNCERTAIN");var s=scoring.calculate(r);
        assertThat(s.totalWeight()).isEqualTo(3);assertThat(s.coverageScore()).isEqualTo(50);
        var reversed=new Evidence.Report(List.of(b,a),r.assessments(),"evidence-v3",true,List.of());
        assertThat(scoring.calculate(reversed).groups()).isEqualTo(s.groups());
        assertThat(scoring.calculate(report(List.of(a,b),"MATCH","MATCH")).coverageScore()).isEqualTo(100);
    }
    @Test void anyRequiresOneSupportedAlternativeOrAllExplicitGaps() {
        var a=req("mq","rabbitmq","REQUIRED","SKILL","ANY");var b=req("mq","kafka","REQUIRED","SKILL","ANY");
        assertThat(scoring.calculate(report(List.of(a,b),"MATCH","UNCERTAIN")).coverageScore()).isEqualTo(100);
        var unknown=scoring.calculate(report(List.of(a,b),"GAP","UNCERTAIN"));
        assertThat(unknown.uncertainPercent()).isEqualTo(100);assertThat(unknown.gapPercent()).isZero();
        assertThat(scoring.calculate(report(List.of(a,b),"GAP","GAP")).gapPercent()).isEqualTo(100);
    }
    @Test void dutyAndQualificationRemainVisibleWithoutInventingScores() {
        var a=req("education","degree","REQUIRED","QUALIFICATION","ALL");var b=req("duty","coding","REQUIRED","DUTY","ALL");
        var s=scoring.calculate(report(List.of(a,b),"UNCERTAIN","MATCH"));
        assertThat(s.available()).isFalse();assertThat(s.coverageScore()).isNull();assertThat(s.excludedGroups()).hasSize(2);
    }
    @Test void uncertaintyBecomingGapDoesNotBecomeAdditionalCoverage() {
        var a=req("cache","redis","REQUIRED","SKILL","ALL");
        var u=scoring.calculate(report(List.of(a),"UNCERTAIN"));var g=scoring.calculate(report(List.of(a),"GAP"));
        assertThat(u.coverageScore()).isEqualTo(g.coverageScore());assertThat(u.uncertainPercent()).isEqualTo(100);assertThat(g.gapPercent()).isEqualTo(100);
        assertThat(scoring.calculate(report(List.of(a),"MATCH")).coverageScore()).isEqualTo(100);
    }
    @Test void incompleteLegacyAndDuplicateInputsCannotMasqueradeAsPerfectCoverage() throws Exception {
        var a=req("g","java","REQUIRED","SKILL","ALL");var r=report(List.of(a),"MATCH");
        assertThat(scoring.calculate(new Evidence.Report(r.requirements(),r.assessments(),"evidence-v3",false,List.of())).coverageScore()).isNull();
        assertThat(scoring.calculate(new Evidence.Report(r.requirements(),r.assessments()))).isNull();
        assertThat(scoring.calculate(new Evidence.Report(List.of(a,a),r.assessments(),"evidence-v3",true,List.of())).available()).isFalse();
        var mapper=new ObjectMapper().findAndRegisterModules();
        var historical=mapper.readValue("{\"requirements\":[],\"assessments\":[]}",Evidence.Report.class);
        assertThat(historical.schemaVersion()).isNull();
        var score=scoring.calculate(r);assertThat(mapper.readValue(mapper.writeValueAsString(score),EvidenceScoring.Snapshot.class)).isEqualTo(score);
    }
    @Test void repeatedSourcesMergeOnceAndMandatoryOccurrenceWins() {
        String text="熟悉 Spring Boot\n熟悉 SpringBoot";
        var job=new Snapshots.Job(UUID.randomUUID(),"test","test",text,"hash",null,null,null,Snapshots.segments(text,"j"));
        var drafts=List.of(
            new Evidence.RequirementDraft("熟悉 Spring Boot","PREFERRED","springboot",new Evidence.Reference("j1","熟悉 Spring Boot"),"a","boot","SKILL","ALL",List.of()),
            new Evidence.RequirementDraft("熟悉 SpringBoot","REQUIRED","springboot",new Evidence.Reference("j2","熟悉 SpringBoot"),"b","other","SKILL","ALL",List.of()));
        var requirements=validator.requirements(new Evidence.Extraction(drafts,true,List.of()),job);
        assertThat(requirements).hasSize(1);assertThat(requirements.getFirst().kind()).isEqualTo("REQUIRED");
        assertThat(requirements.getFirst().additionalJdEvidence()).hasSize(1);
    }
    @Test void differentRedisCapabilitiesAreNotMergedBySkillKey() {
        String text="Redis 基础\nRedis 缓存一致性";
        var job=new Snapshots.Job(UUID.randomUUID(),"test","test",text,"hash",null,null,null,Snapshots.segments(text,"j"));
        var drafts=List.of(
            new Evidence.RequirementDraft("Redis 基础","REQUIRED","redis",new Evidence.Reference("j1","Redis 基础"),"a","redis-basics","SKILL","ALL",List.of()),
            new Evidence.RequirementDraft("Redis 缓存一致性","REQUIRED","redis",new Evidence.Reference("j2","Redis 缓存一致性"),"b","redis-consistency","SKILL","ALL",List.of()));
        assertThat(validator.requirements(new Evidence.Extraction(drafts,true,List.of()),job)).hasSize(2);
    }
    @Test void uncoveredJobSegmentsDisableScoreEvenIfModelClaimsComplete() {
        String text="使用 Redis\n每周四天";
        var job=new Snapshots.Job(UUID.randomUUID(),"test","test",text,"hash",null,null,null,Snapshots.segments(text,"j"));
        var draft=new Evidence.RequirementDraft("使用 Redis","REQUIRED","redis",new Evidence.Reference("j1","使用 Redis"),"redis","redis","SKILL","ALL",List.of());
        var extraction=new Evidence.Extraction(List.of(draft),true,List.of());var reqs=validator.requirements(extraction,job);
        var version=new Snapshots.Version(UUID.randomUUID(),UUID.randomUUID(),1,"项目实践", "hash",Snapshots.segments("项目实践","r"),OffsetDateTime.now());
        var r=validator.structuredReport(extraction,reqs,new Evidence.Assessments(List.of(assessment(reqs.getFirst(),"MATCH"))),version,job);
        assertThat(r.extractionComplete()).isFalse();assertThat(scoring.calculate(r).coverageScore()).isNull();
        assertThat(r.warnings()).anyMatch(w->w.contains("j2"));
    }
    @Test void sourceClausePreventsModelFromMultiplyingWeights() {
        String text="熟悉 Redis 缓存和 Kafka 消费机制。";
        var job=new Snapshots.Job(UUID.randomUUID(),"test","test",text,"hash",null,null,null,Snapshots.segments(text,"j"));
        var drafts=List.of(
            new Evidence.RequirementDraft("Redis 缓存","REQUIRED","redis",new Evidence.Reference("j1","Redis 缓存"),"invented-a","redis-cache","SKILL","ALL",List.of()),
            new Evidence.RequirementDraft("Kafka 消费机制","REQUIRED","kafka",new Evidence.Reference("j1","Kafka 消费机制"),"invented-b","kafka-consumption","SKILL","ALL",List.of()));
        var reqs=validator.requirements(new Evidence.Extraction(drafts,true,List.of()),job);
        assertThat(reqs.stream().map(Evidence.Requirement::groupKey).distinct()).hasSize(1);
        var s=scoring.calculate(report(reqs,"MATCH","GAP"));
        assertThat(s.totalWeight()).isEqualTo(3);assertThat(s.coverageScore()).isEqualTo(50);
    }
    @Test void batchingBoundsPromptSizeWithoutDroppingOrRepeatingRequirements() {
        var reqs=new ArrayList<Evidence.Requirement>();
        for(int i=0;i<23;i++) reqs.add(req("g"+i,"c"+i,"REQUIRED","SKILL","ALL"));
        var batches=SpringAiEvidenceModel.batches(reqs);
        assertThat(batches).hasSize(5);assertThat(batches).allSatisfy(b->assertThat(b.size()).isBetween(1,5));
        assertThat(batches.stream().flatMap(List::stream).toList()).containsExactlyElementsOf(reqs);
    }
}
