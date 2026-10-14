package com.resumeai.compass;
import static org.assertj.core.api.Assertions.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
class BudgetPlannerTests {
    final BudgetPlanner planner=new BudgetPlanner();
    EvidenceService.Run run(UUID version,String key,String kind,String action,int cost) {
        UUID req=UUID.randomUUID();var q=new Evidence.Requirement(req,"测试要求",kind,key,new Evidence.Reference("j1","要求"));
        var a=new Evidence.Assessment(req,"UNCERTAIN",List.of(),"待复核",action,"准备任务","实验记录",cost);
        return new EvidenceService.Run(UUID.randomUUID(),"SUCCEEDED",version,new Snapshots.Job(UUID.randomUUID(),"Java","公司","要求","h",null,null,null,List.of()),new Evidence.Report(List.of(q),List.of(a)),List.of(),null,1L,2,OffsetDateTime.now());
    }
    @Test void sharedTasksKeepAllSourcesAndUseMaximumCost() {
        UUID v=UUID.randomUUID();var a=run(v,"redis.cache_consistency","REQUIRED","LEARN",120);var b=run(v,"redis.cache_consistency_design","PREFERRED","LEARN",180);
        var merged=planner.merge(List.of(a,b),Map.of(a.job().id(),2),480);
        assertThat(merged).hasSize(1);assertThat(merged.getFirst().jobIds()).hasSize(2);assertThat(merged.getFirst().requirementIds()).hasSize(2);
        assertThat(merged.getFirst().effortMinutes()).isEqualTo(180);assertThat(merged.getFirst().benefit()).isEqualTo(7);
    }
    @Test void differentLanguagesActionsAndUnknownSkillsAreNotMerged() {
        UUID v=UUID.randomUUID();var runs=List.of(run(v,"java","REQUIRED","LEARN",60),run(v,"javascript","REQUIRED","LEARN",60),
            run(v,"java","REQUIRED","EVIDENCE_FIX",60),run(v,"unknown","REQUIRED","LEARN",60),run(v,"unknown","REQUIRED","LEARN",60));
        assertThat(planner.merge(runs,Map.of(),480)).hasSize(5);
    }
    @Test void skipsExpensiveTasksAndContinuesWithinBudget() {
        UUID v=UUID.randomUUID();var expensive=run(v,"redis.cache_consistency","REQUIRED","LEARN",120);var cheap=run(v,"java","PREFERRED","LEARN",30);
        var tasks=planner.merge(List.of(expensive,cheap),Map.of(expensive.job().id(),3),60);
        assertThat(tasks.getFirst().skillKey()).isEqualTo("redis.cache_consistency");
        assertThat(tasks.getFirst().selected()).isFalse();
        assertThat(tasks.stream().filter(Planning.Task::selected).mapToInt(Planning.Task::effortMinutes).sum()).isEqualTo(30);
        assertThat(tasks.stream().filter(t->!t.selected()).findFirst().orElseThrow().explanation()).contains("预算不足");
        assertThatThrownBy(()->BudgetPlanner.budget(-30)).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->BudgetPlanner.effort(0)).isInstanceOf(CompassException.class);
    }
}
