package com.resumeai.compass;

import java.time.OffsetDateTime;
import java.util.*;

/** Deterministic score over validated requirements. No model-supplied numerical scores. */
public final class EvidenceScoring {
    public static final String VERSION="evidence-coverage-v1";
    public record Member(UUID requirementId,String criterionKey,String text,String status,Double weightShare) {}
    public record Group(String groupKey,String kind,String category,String logic,String status,double weight,
                        double matchedWeight,double uncertainWeight,double gapWeight,List<Member> members) {}
    public record Snapshot(String algorithmVersion,String weightVersion,String source,boolean available,
                           Double coverageScore,Double uncertainPercent,Double gapPercent,double totalWeight,
                           List<Group> groups,List<Group> excludedGroups,List<String> warnings,OffsetDateTime calculatedAt) {}

    public Snapshot calculate(Evidence.Report report) {
        if(report==null || !"evidence-v3".equals(report.schemaVersion())) return null;
        var warnings=new ArrayList<>(report.warnings()==null?List.of():report.warnings());
        if(!Boolean.TRUE.equals(report.extractionComplete())) {
            warnings.add("提取未确认完整，暂不生成分数。仍可查看逐项证据。");return unavailable(warnings);
        }
        if(report.requirements()==null || report.requirements().isEmpty() || report.assessments()==null) return unavailable(List.of("要求或判断为空"));
        var assessments=new HashMap<UUID,Evidence.Assessment>();
        for(var a:report.assessments()) {
            if(a==null || a.requirementId()==null || !Set.of("MATCH","GAP","UNCERTAIN").contains(Objects.toString(a.status(),""))
                || assessments.put(a.requirementId(),a)!=null) return unavailable(List.of("判断状态或 ID 无效"));
        }
        var grouped=new TreeMap<String,List<Evidence.Requirement>>();var ids=new HashSet<UUID>();var criteria=new HashSet<String>();
        for(var r:report.requirements()) {
            if(r==null || r.id()==null || !ids.add(r.id()) || !assessments.containsKey(r.id()) || r.groupKey()==null || r.criterionKey()==null
                || !Set.of("ALL","ANY").contains(Objects.toString(r.logic(),""))
                || !Set.of("REQUIRED","PREFERRED").contains(Objects.toString(r.kind(),""))
                || !Set.of("SKILL","EXPERIENCE","DUTY","QUALIFICATION").contains(Objects.toString(r.category(),""))
                || !criteria.add(r.category()+":"+r.criterionKey())) return unavailable(List.of("分组缺失或存在重复计分子项"));
            grouped.computeIfAbsent(r.groupKey(),k->new ArrayList<>()).add(r);
        }
        if(assessments.size()!=ids.size()) return unavailable(List.of("要求判断覆盖不完整"));
        var groups=new ArrayList<Group>();var excluded=new ArrayList<Group>();
        double total=0,matched=0,uncertain=0,gap=0;
        for(var entry:grouped.entrySet()) {
            var reqs=entry.getValue();reqs.sort(Comparator.comparing(Evidence.Requirement::criterionKey));var first=reqs.getFirst();
            if(reqs.stream().anyMatch(r->!r.kind().equals(first.kind()) || !r.category().equals(first.category()) || !r.logic().equals(first.logic())))
                return unavailable(List.of("同组元数据冲突"));
            boolean counted=Set.of("SKILL","EXPERIENCE").contains(first.category());
            double weight=counted?(first.kind().equals("REQUIRED")?3.0:1.0):0;
            boolean any=first.logic().equals("ANY");double m=0,u=0,g=0;
            var members=new ArrayList<Member>();
            for(var r:reqs) {
                String status=assessments.get(r.id()).status();
                members.add(new Member(r.id(),r.criterionKey(),r.text(),status,any?null:weight/reqs.size()));
                if(!any) { switch(status) { case "MATCH" -> m+=weight/reqs.size();case "GAP" -> g+=weight/reqs.size();default -> u+=weight/reqs.size(); } }
            }
            boolean hasMatch=members.stream().anyMatch(a->a.status().equals("MATCH"));
            boolean allMatch=members.stream().allMatch(a->a.status().equals("MATCH"));
            boolean allGap=members.stream().allMatch(a->a.status().equals("GAP"));
            boolean hasGap=members.stream().anyMatch(a->a.status().equals("GAP"));
            String status=any?(hasMatch?"MATCH":allGap?"GAP":"UNCERTAIN"):(allMatch?"MATCH":hasGap?"GAP":"UNCERTAIN");
            if(any) { switch(status) {case "MATCH" -> m=weight;case "GAP" -> g=weight;default -> u=weight;} }
            var group=new Group(entry.getKey(),first.kind(),first.category(),first.logic(),status,weight,m,u,g,List.copyOf(members));
            if(counted) {groups.add(group);total+=weight;matched+=m;uncertain+=u;gap+=g;} else excluded.add(group);
        }
        if(total==0) { warnings.add("没有可计分的技术或经验要求；资格和职责单独展示。");
            return new Snapshot(VERSION,"required3-preferred1-v1","MODEL",false,null,null,null,0,List.of(),List.copyOf(excluded),List.copyOf(warnings),OffsetDateTime.now(java.time.ZoneOffset.UTC)); }
        warnings.add("仅对已提取且校验通过的技术/经验要求计分；模型的完整性声明不等于独立证明没有遗漏。");
        return new Snapshot(VERSION,"required3-preferred1-v1","MODEL",true,percent(matched,total),percent(uncertain,total),percent(gap,total),
            total,List.copyOf(groups),List.copyOf(excluded),List.copyOf(warnings),OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }
    private double percent(double value,double total) { return Math.round(value/total*10000.0)/100.0; }
    private Snapshot unavailable(List<String> warnings) {
        return new Snapshot(VERSION,"required3-preferred1-v1","MODEL",false,null,null,null,0,List.of(),List.of(),List.copyOf(warnings),OffsetDateTime.now(java.time.ZoneOffset.UTC));
    }
}
