package com.resumeai.compass;
import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
class EvidenceTests {
    final Snapshots.Version version=new Snapshots.Version(UUID.randomUUID(),UUID.randomUUID(),1,"使用 Redis",Snapshots.hash("使用 Redis"),Snapshots.segments("使用 Redis","r"),OffsetDateTime.now());
    final Snapshots.Job job=new Snapshots.Job(UUID.randomUUID(),"Java","测试","需要缓存一致性", "hash",null,null,null,Snapshots.segments("需要缓存一致性","j"));
    final EvidenceValidator validator=new EvidenceValidator();
    @Test void falseQuotesAndUnsupportedMatchesAreRejected() {
        var req=validator.requirements(new Evidence.Extraction(List.of(new Evidence.RequirementDraft("缓存一致性","REQUIRED","redis.cache_consistency",new Evidence.Reference("j1","缓存一致性")))),job);
        var a=new Evidence.Assessment(req.getFirst().id(),"MATCH",List.of(new Evidence.Reference("r1","实现双删")),"有实践","NONE",null,null,0);
        assertThatThrownBy(()->validator.report(req,new Evidence.Assessments(List.of(a)),version)).isInstanceOf(CompassException.class);
        var empty=new Evidence.Assessment(req.getFirst().id(),"MATCH",List.of(),"有实践","NONE",null,null,0);
        assertThatThrownBy(()->validator.report(req,new Evidence.Assessments(List.of(empty)),version)).isInstanceOf(CompassException.class);
        var uncertain=new Evidence.Assessment(req.getFirst().id(),"UNCERTAIN",List.of(),"未提供设计细节","CLARIFY","核实缓存策略","记录项目实际方案",30);
        assertThat(validator.report(req,new Evidence.Assessments(List.of(uncertain)),version).assessments()).hasSize(1);
        var falseGap=new Evidence.Assessment(req.getFirst().id(),"GAP",List.of(new Evidence.Reference("r1","使用 Redis")),"没有记录一致性实现","CLARIFY","核实缓存策略","记录项目实际方案",30);
        assertThatThrownBy(()->validator.report(req,new Evidence.Assessments(List.of(falseGap)),version)).isInstanceOf(CompassException.class);
    }
    @Test void toolsEnforceInputScopeAndInvocationBudget() {
        UUID run=UUID.randomUUID();var json=new CompassJson(new ObjectMapper().findAndRegisterModules());
        var tools=new JobCompassReadTools(run,version,job,"无",json);
        assertThatThrownBy(()->tools.getResumeVersion(UUID.randomUUID().toString())).isInstanceOf(CompassException.class);
        tools.getResumeVersion(version.id().toString());tools.getJobSnapshot(run.toString());tools.getApplicationHistory(job.id().toString());
        assertThatThrownBy(()->tools.getResumeVersion(version.id().toString())).isInstanceOf(CompassException.class);
    }
    @Test void advisoryEffortIsRoundedWithAuditButInvalidRangesStillFail() {
        var req=validator.requirements(new Evidence.Extraction(List.of(new Evidence.RequirementDraft("缓存一致性","REQUIRED","redis.cache_consistency",new Evidence.Reference("j1","缓存一致性")))),job);
        var a=new Evidence.Assessment(req.getFirst().id(),"UNCERTAIN",List.of(),"需要核实","CLARIFY","核实实际情况","记录实际方案",15);
        var report=validator.report(req,new Evidence.Assessments(List.of(a)),version);
        assertThat(report.assessments().getFirst().effortMinutes()).isEqualTo(30);
        assertThat(report.assessments().getFirst().status()).isEqualTo("UNCERTAIN");
        assertThat(report.taskAdjustments()).containsExactly(new Evidence.TaskAdjustment(req.getFirst().id(),15,30));
        for(int invalid:List.of(0,-1,961)) {
            var bad=new Evidence.Assessment(req.getFirst().id(),"UNCERTAIN",List.of(),"需要核实","CLARIFY","核实实际情况","记录实际方案",invalid);
            assertThatThrownBy(()->validator.report(req,new Evidence.Assessments(List.of(bad)),version)).isInstanceOf(CompassException.class);
        }
    }
    @Test void referenceAlignmentMustBeUniqueLiteralAndNeverSkipContent() {
        var resolver=new LiteralReferenceResolver();
        var segments=Snapshots.segments("使用 Spring   Boot\n处理订单状态","r");
        var resolved=resolver.resolve(new Evidence.Reference("r99","使用 Spring Boot处理订单状态"),segments);
        assertThat(resolved).containsExactly(new Evidence.Reference("r1","使用 Spring   Boot"),new Evidence.Reference("r2","处理订单状态"));
        assertThatThrownBy(()->resolver.resolve(new Evidence.Reference("r99","使用 Spring Boot处理库存状态"),segments)).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->resolver.resolve(new Evidence.Reference("r99","Redis"),Snapshots.segments("使用 Redis\n测试 Redis","r"))).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->resolver.resolve(new Evidence.Reference("r99","甲乙"),Snapshots.segments("甲\n中间正文\n乙","r"))).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->resolver.resolve(new Evidence.Reference("r99","notable"),Snapshots.segments("not able","r"))).isInstanceOf(CompassException.class);
    }
    @Test void modelStatusSchemaUsesEnumsAndAlignmentIsAuditable() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        assertThatThrownBy(()->mapper.readValue("{\"status\":\"PARTIAL\"}",Evidence.ModelAssessment.class)).isInstanceOf(Exception.class);
        var req=validator.requirements(new Evidence.Extraction(List.of(new Evidence.RequirementDraft("缓存一致性","REQUIRED","redis",new Evidence.Reference("j1","缓存一致性")))),job);
        var a=new Evidence.Assessment(req.getFirst().id(),"MATCH",List.of(new Evidence.Reference("r99","使用 Redis")),"有实际使用记录","NONE",null,null,0);
        var report=validator.report(req,new Evidence.Assessments(List.of(a)),version);
        assertThat(report.assessments().getFirst().resumeEvidence().getFirst().segmentId()).isEqualTo("r1");
        assertThat(report.referenceAdjustments()).hasSize(1);
        assertThat(report.referenceAdjustments().getFirst().reported().segmentId()).isEqualTo("r99");
    }
}
