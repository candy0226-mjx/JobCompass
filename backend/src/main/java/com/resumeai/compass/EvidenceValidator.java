package com.resumeai.compass;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EvidenceValidator {
    public List<Evidence.Requirement> requirements(Evidence.Extraction extraction,Snapshots.Job job) {
        if(extraction==null || extraction.requirements()==null || extraction.requirements().isEmpty() || extraction.requirements().size()>(extraction.complete()==null?20:60))
            throw invalid("JD 原子要求须为 1–60 条；旧版要求最多 20 条");
        boolean structured=extraction.complete()!=null;
        if(structured && (extraction.warnings()==null || extraction.warnings().size()>20 || extraction.warnings().stream().anyMatch(w->w==null || w.isBlank() || w.length()>500)))
            throw invalid("提取警告字段无效");
        var result=new ArrayList<Evidence.Requirement>();
        for(var r:extraction.requirements()) {
            if(r==null) throw invalid("要求不能为空");
            bounded(r.text(),500); if(!Set.of("REQUIRED","PREFERRED").contains(r.kind())) throw invalid("要求类型无效");
            if(r.skillKey()!=null && r.skillKey().length()>120) throw invalid("技能键过长");
            reference(r.jdEvidence(),job.segments());
            if(structured) {
                key(r.groupKey());key(r.criterionKey());
                if(!Set.of("SKILL","EXPERIENCE","DUTY","QUALIFICATION").contains(Objects.toString(r.category(),""))
                    || !Set.of("ALL","ANY").contains(Objects.toString(r.logic(),""))) throw invalid("要求类别或组逻辑无效");
                if(r.additionalJdEvidence()!=null && r.additionalJdEvidence().size()>20) throw invalid("附加来源过多");
                if(r.additionalJdEvidence()!=null) r.additionalJdEvidence().forEach(ref->reference(ref,job.segments()));
            }
            result.add(new Evidence.Requirement(UUID.randomUUID(),r.text(),r.kind(),r.skillKey(),r.jdEvidence(),
                structured && Boolean.TRUE.equals(extraction.complete())?sourceGroup(r,job):r.groupKey(),r.criterionKey(),r.category(),r.logic(),r.additionalJdEvidence()));
        }
        return structured?normalize(result):List.copyOf(result);
    }
    public Evidence.Report report(List<Evidence.Requirement> requirements,Evidence.Assessments output,Snapshots.Version version) {
        if(output==null || output.assessments()==null || output.assessments().size()!=requirements.size()) throw invalid("每条要求必须恰有一个判断");
        var ids=new HashSet<UUID>(); requirements.forEach(r->ids.add(r.id())); var seen=new HashSet<UUID>();
        var adjusted=new ArrayList<Evidence.Assessment>();var changes=new ArrayList<Evidence.TaskAdjustment>();
        var referenceChanges=new ArrayList<Evidence.ReferenceAdjustment>();var resolver=new LiteralReferenceResolver();
        for(var a:output.assessments()) {
            if(a==null || !ids.contains(a.requirementId()) || !seen.add(a.requirementId())) throw invalid("要求 ID 无效或重复");
            if(a.status()==null || !Set.of("MATCH","GAP","UNCERTAIN").contains(a.status())) throw invalid("判断状态无效");
            bounded("判断理由（要求 "+a.requirementId()+"）",a.reason(),1000);
            if(a.resumeEvidence()==null || a.resumeEvidence().size()>5) throw invalid("证据列表无效");
            var resolved=new LinkedHashSet<Evidence.Reference>();
            for(var ref:a.resumeEvidence()) {
                try {
                    var aligned=resolver.resolve(ref,version.segments());aligned.forEach(r->reference(r,version.segments()));resolved.addAll(aligned);
                    if(!aligned.equals(List.of(ref))) referenceChanges.add(new Evidence.ReferenceAdjustment(a.requirementId(),ref,aligned));
                }
                catch(CompassException e) { throw invalid("要求 "+a.requirementId()+" 的简历引用不匹配；须从对应段落复制短子串，不得跨段或改写"); }
            }
            if(resolved.size()>5) throw invalid("对齐后引用超过五条，请选择较短引用");
            if(!a.status().equals("UNCERTAIN") && a.resumeEvidence().isEmpty()) throw invalid("MATCH/GAP 必须有原文证据，未提及用 UNCERTAIN");
            if(a.status().equals("GAP") && !explicitGap(List.copyOf(resolved)))
                throw invalid("缺少记录或只出现另一项技术不能证明不会；GAP 须引用明确否认或明确无法满足条件的原文，否则用 UNCERTAIN");
            if(a.actionType()==null || !Set.of("NONE","EVIDENCE_FIX","LEARN","CLARIFY").contains(a.actionType())) throw invalid("任务类型无效");
            if(!a.actionType().equals("NONE")) {
                bounded("任务标题（要求 "+a.requirementId()+"）",a.taskTitle(),200); bounded("预期产物（要求 "+a.requirementId()+"）",a.expectedOutput(),500);
                if(a.effortMinutes()<=0 || a.effortMinutes()>960) throw invalid("要求 "+a.requirementId()+" 的任务估计须为 1–960 分钟正数，当前值="+a.effortMinutes());
                int minutes=((a.effortMinutes()+29)/30)*30;
                if(minutes!=a.effortMinutes()) changes.add(new Evidence.TaskAdjustment(a.requirementId(),a.effortMinutes(),minutes));
                adjusted.add(new Evidence.Assessment(a.requirementId(),a.status(),List.copyOf(resolved),a.reason(),a.actionType(),a.taskTitle(),a.expectedOutput(),minutes));
            } else {
                adjusted.add(new Evidence.Assessment(a.requirementId(),a.status(),List.copyOf(resolved),a.reason(),a.actionType(),a.taskTitle(),a.expectedOutput(),a.effortMinutes()));
            }
        }
        return new Evidence.Report(requirements,List.copyOf(adjusted),null,null,null,List.copyOf(changes),List.copyOf(referenceChanges));
    }
    public Evidence.Report structuredReport(Evidence.Extraction extraction,List<Evidence.Requirement> requirements,
                                             Evidence.Assessments output,Snapshots.Version version,Snapshots.Job job) {
        var checked=report(requirements,output,version);
        var refs=new HashSet<String>();
        for(var r:requirements) {refs.add(r.jdEvidence().segmentId());if(r.additionalJdEvidence()!=null) r.additionalJdEvidence().forEach(q->refs.add(q.segmentId()));}
        var warnings=new ArrayList<>(extraction.warnings()==null?List.of():extraction.warnings());
        var missing=job.segments().stream().filter(s->!s.text().strip().matches("(岗位职责|任职要求|职位要求|职位描述|工作职责|任职资格|岗位要求|加分项|基本要求|优先条件)[:：]?"))
            .filter(s->!refs.contains(s.id())).map(Snapshots.Segment::id).toList();
        if(!missing.isEmpty()) warnings.add("以下 JD 分段未被引用，提取完整性待核实："+String.join(",",missing));
        return new Evidence.Report(checked.requirements(),checked.assessments(),"evidence-v3",Boolean.TRUE.equals(extraction.complete()) && missing.isEmpty(),List.copyOf(warnings),checked.taskAdjustments(),checked.referenceAdjustments());
    }
    private void key(String value) { if(value==null || !value.matches("[a-z0-9][a-z0-9_.-]{0,119}")) throw invalid("组及子项须为稳定英文键"); }
    private String sourceGroup(Evidence.RequirementDraft r,Snapshots.Job job) {
        String segment=job.segments().stream().filter(s->s.id().equals(r.jdEvidence().segmentId())).findFirst().orElseThrow().text();
        String quote=r.jdEvidence().quote();int begin=segment.indexOf(quote),end=begin+quote.length();
        var clauses=java.util.regex.Pattern.compile("[^；;。！？!?]+[；;。！？!?]?").matcher(segment);
        int index=0,found=-1;
        while(clauses.find()) {
            if(clauses.start()<end && clauses.end()>begin && !segment.substring(Math.max(begin,clauses.start()),Math.min(end,clauses.end())).replaceAll("[\\s；;。！？!?]","").isEmpty()) {
                if(found>=0) throw invalid("每条 jdEvidence 须位于一个独立分句，不能跨越分号或句号；请为子项引用对应分句");
                found=index;
            }
            index++;
        }
        if(found<0) throw invalid("岗位引用不能只有标点");
        return r.category().toLowerCase(Locale.ROOT)+"-"+r.jdEvidence().segmentId()+"-"+found;
    }
    private List<Evidence.Requirement> normalize(List<Evidence.Requirement> input) {
        // Equal text (including controlled aliases) is never counted twice under invented keys.
        var aliases=new HashMap<String,String>(); var groups=new TreeMap<String,List<Evidence.Requirement>>();
        var lexicon=new com.resumeai.application.analysis.TechnologyLexicon();
        for(var r:input) {
            var text=new StringBuilder(r.text()); var occurrences=new ArrayList<>(lexicon.scan(r.text()).occurrences());
            occurrences.sort(Comparator.comparingInt(com.resumeai.application.analysis.TechnologyLexicon.Occurrence::start).reversed());
            for(var o:occurrences) text.replace(o.start(),o.end(),o.canonical());
            String identity=r.category()+":"+text.toString().toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}]+","");
            String criterion=aliases.computeIfAbsent(identity,k->r.criterionKey());
            groups.computeIfAbsent(r.groupKey(),k->new ArrayList<>()).add(new Evidence.Requirement(r.id(),r.text(),r.kind(),r.skillKey(),r.jdEvidence(),r.groupKey(),criterion,r.category(),r.logic(),r.additionalJdEvidence()));
        }
        var signatures=new HashMap<String,String>();var result=new LinkedHashMap<String,Evidence.Requirement>();
        for(var entry:groups.entrySet()) {
            var first=entry.getValue().getFirst();
            if(entry.getValue().stream().anyMatch(r->!r.category().equals(first.category()) || !r.logic().equals(first.logic())))
                throw invalid("同组要求的类别与 ALL/ANY 逻辑必须一致");
            String signature=first.category()+":"+first.logic()+":"+entry.getValue().stream().map(Evidence.Requirement::criterionKey).distinct().sorted().reduce((a,b)->a+","+b).orElseThrow();
            String group=signatures.computeIfAbsent(signature,k->entry.getKey());
            for(var r:entry.getValue()) {
                String identity=group+":"+r.criterionKey();var old=result.get(identity);
                var refs=new LinkedHashSet<Evidence.Reference>();refs.add(r.jdEvidence());if(r.additionalJdEvidence()!=null) refs.addAll(r.additionalJdEvidence());
                if(old!=null) { refs.add(old.jdEvidence());if(old.additionalJdEvidence()!=null) refs.addAll(old.additionalJdEvidence()); }
                String kind=old!=null && old.kind().equals("REQUIRED")?"REQUIRED":r.kind();
                var primary=old==null?r.jdEvidence():old.jdEvidence();refs.remove(primary);
                result.put(identity,new Evidence.Requirement(old==null?r.id():old.id(),old==null?r.text():old.text(),kind,r.skillKey(),primary,
                    group,r.criterionKey(),r.category(),r.logic(),List.copyOf(refs)));
            }
        }
        var ownership=new HashMap<String,String>();
        if(result.values().stream().map(Evidence.Requirement::groupKey).distinct().count()>20) throw invalid("去重后独立要求组最多 20 个，不得静默截断");
        for(var r:result.values()) {
            String previous=ownership.putIfAbsent(r.category()+":"+r.criterionKey(),r.groupKey());
            if(previous!=null && !previous.equals(r.groupKey())) throw invalid("同一能力子项跨组重复，请重新分组以免重复计分");
        }
        // A mandatory occurrence dominates a preferred repetition of the same requirement group.
        var required=new HashSet<String>();result.values().stream().filter(r->r.kind().equals("REQUIRED")).forEach(r->required.add(r.groupKey()));
        return result.values().stream().map(r->new Evidence.Requirement(r.id(),r.text(),required.contains(r.groupKey())?"REQUIRED":"PREFERRED",r.skillKey(),r.jdEvidence(),r.groupKey(),r.criterionKey(),r.category(),r.logic(),r.additionalJdEvidence())).toList();
    }
    private void reference(Evidence.Reference ref,List<Snapshots.Segment> segments) {
        if(ref==null || ref.quote()==null || ref.quote().isBlank()) throw invalid("引用不能为空");
        if(segments.stream().noneMatch(s->s.id().equals(ref.segmentId()) && s.text().contains(ref.quote()))) throw invalid("引用 ID 或原文不匹配");
    }
    private boolean explicitGap(List<Evidence.Reference> refs) {
        // Deliberately conservative: arbitrary missing information must not become a negative ability claim.
        return refs.stream().anyMatch(ref->List.of("不会","不能","无法","从未","未取得","未获得","不具备","未达到","没有相关经验","没有实践经验")
            .stream().anyMatch(ref.quote()::contains));
    }
    private void bounded(String value,int max) { if(value==null || value.isBlank() || value.length()>max) throw invalid("模型字段为空或过长"); }
    private void bounded(String label,String value,int max) { if(value==null || value.isBlank() || value.length()>max) throw invalid(label+"为空或超过 "+max+" 字符"); }
    private CompassException invalid(String message) { return new CompassException(422,"INVALID_EVIDENCE",message); }
}
