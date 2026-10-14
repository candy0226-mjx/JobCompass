package com.resumeai.compass;
import java.util.*;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class BudgetPlanner {
    private static final Map<String,String> SKILLS=Map.ofEntries(
        Map.entry("redis.cache_consistency","redis.cache_consistency"),Map.entry("redis_cache_consistency","redis.cache_consistency"),Map.entry("redis.cache_consistency_design","redis.cache_consistency"),
        Map.entry("redis.cache_penetration","redis.cache_penetration"),Map.entry("redis","redis.basics"),Map.entry("redis.basics","redis.basics"),
        Map.entry("spring_boot","springboot.api"),Map.entry("springboot","springboot.api"),Map.entry("spring_boot.api","springboot.api"),Map.entry("springboot.api","springboot.api"),
        Map.entry("java","java.core"),Map.entry("java.core","java.core"),Map.entry("javascript","javascript.core"),Map.entry("javascript.core","javascript.core"),
        Map.entry("database.unique_constraints","database.unique_constraints"),Map.entry("sql.unique_constraints","database.unique_constraints"),
        Map.entry("database.idempotency","database.idempotency"),Map.entry("postgresql","postgresql.basics"),Map.entry("postgresql.basics","postgresql.basics"));
    public String normalize(String key,UUID requirementId) {
        return key==null ? "unmapped:"+requirementId : SKILLS.getOrDefault(key.strip().toLowerCase(Locale.ROOT),"unmapped:"+requirementId);
    }
    public static boolean known(String key) { return SKILLS.containsKey(key); }
    public static void budget(int minutes) { if(minutes<30 || minutes>2400 || minutes%30!=0) throw CompassException.bad("每周预算须为 30–2400 分钟且为 30 的倍数"); }
    public static void effort(int minutes) { if(minutes<30 || minutes>960 || minutes%30!=0) throw CompassException.bad("任务耗时须为 30–960 分钟且为 30 的倍数"); }
    private static class Group {
        String skill,action,title,output;int effort;
        Set<UUID> requirements=new LinkedHashSet<>();Map<UUID,Integer> weights=new TreeMap<>();LocalDate deadline;
    }
    public List<Planning.Task> merge(List<EvidenceService.Run> runs,Map<UUID,Integer> priorities,int budget) {
        budget(budget);var groups=new TreeMap<String,Group>();
        for(var run:runs) for(var q:run.report().requirements()) {
            var a=run.report().assessments().stream().filter(x->x.requirementId().equals(q.id())).findFirst().orElseThrow();
            var review=run.reviews().stream().filter(x->x.requirementId().equals(q.id())).findFirst().orElse(null);
            String action=review==null?a.actionType():review.category();if(action.equals("NONE") || action.equals("CONFIRMED_MATCH")) continue;
            String key=normalize(review!=null && review.skillKey()!=null ? review.skillKey():q.skillKey(),q.id());var g=groups.computeIfAbsent(key+":"+action,k->new Group());
            if(g.title==null) {
                g.skill=key;g.action=action;
                boolean keep=action.equals(a.actionType()) && a.taskTitle()!=null && !a.taskTitle().isBlank();
                g.title=keep?a.taskTitle():switch(action){case "EVIDENCE_FIX"->"补充实际经历："+q.text();case "LEARN"->"完成实践："+q.text();default->"核实情况："+q.text();};
                g.output=keep?a.expectedOutput():switch(action){case "EVIDENCE_FIX"->"记录真实业务场景、技术选择和可复核的验证结果";case "LEARN"->"完成一项小型实践并记录过程和结果";default->"记录是否实际具备该能力及可提供的证据";};
            }
            if(g.title.length()>200) g.title=g.title.substring(0,199)+"…";
            int cost=action.equals(a.actionType()) && a.effortMinutes()>0?a.effortMinutes():action.equals("CLARIFY")?30:120;
            effort(cost);g.effort=Math.max(g.effort,cost);g.requirements.add(q.id());
            int weight=(q.kind().equals("REQUIRED")?3:1)*priorities.getOrDefault(run.job().id(),1);
            g.weights.merge(run.job().id(),weight,Math::max);
            if(run.job().deadline()!=null && (g.deadline==null || run.job().deadline().isBefore(g.deadline))) g.deadline=run.job().deadline();
        }
        if(groups.size()>20) throw CompassException.bad("准备事项超过 20 项，请先缩小岗位范围或复核要求");
        var tasks=new ArrayList<Planning.Task>();
        for(var g:groups.values()) {
            int benefit=g.weights.values().stream().mapToInt(Integer::intValue).sum();
            tasks.add(new Planning.Task(UUID.randomUUID(),g.skill,g.action,g.title,g.output,g.effort,false,List.copyOf(g.requirements),List.copyOf(g.weights.keySet()),
                benefit,benefit/(g.effort/60.0),g.deadline,"", "TODO",0));
        }
        return allocate(tasks,budget);
    }
    public List<Planning.Task> allocate(List<Planning.Task> tasks,int budget) {
        budget(budget);var sorted=new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingDouble(Planning.Task::priority).reversed()
            .thenComparing(Planning.Task::deadline,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Planning.Task::skillKey).thenComparing(Planning.Task::actionType));
        int remaining=budget;var result=new ArrayList<Planning.Task>();
        for(var task:sorted) {
            effort(task.effortMinutes());boolean selected=task.effortMinutes()<=remaining;if(selected) remaining-=task.effortMinutes();
            String reason="关联 "+task.jobIds().size()+" 个岗位；要求收益 "+task.benefit()+"；预计 "+task.effortMinutes()+" 分钟；优先值 "+String.format(Locale.ROOT,"%.2f",task.priority())+
                (selected?"；预算可容纳":"；剩余预算不足，待安排");
            if(task.deadline()!=null && task.deadline().isBefore(LocalDate.now())) reason+="；注意岗位截止日期已过";
            result.add(task.edit(task.title(),task.expectedOutput(),task.effortMinutes(),selected,reason,task.status(),task.version()));
        }
        return List.copyOf(result);
    }
}
