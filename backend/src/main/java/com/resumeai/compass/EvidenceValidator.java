package com.resumeai.compass;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EvidenceValidator {
    public List<Evidence.Requirement> requirements(Evidence.Extraction extraction,Snapshots.Job job) {
        if(extraction==null || extraction.requirements()==null || extraction.requirements().isEmpty() || extraction.requirements().size()>20)
            throw invalid("JD 要求须为 1–20 条");
        var result=new ArrayList<Evidence.Requirement>();
        for(var r:extraction.requirements()) {
            if(r==null) throw invalid("要求不能为空");
            bounded(r.text(),500); if(!Set.of("REQUIRED","PREFERRED").contains(r.kind())) throw invalid("要求类型无效");
            if(r.skillKey()!=null && r.skillKey().length()>120) throw invalid("技能键过长");
            reference(r.jdEvidence(),job.segments());
            result.add(new Evidence.Requirement(UUID.randomUUID(),r.text(),r.kind(),r.skillKey(),r.jdEvidence()));
        }
        return List.copyOf(result);
    }
    public Evidence.Report report(List<Evidence.Requirement> requirements,Evidence.Assessments output,Snapshots.Version version) {
        if(output==null || output.assessments()==null || output.assessments().size()!=requirements.size()) throw invalid("每条要求必须恰有一个判断");
        var ids=new HashSet<UUID>(); requirements.forEach(r->ids.add(r.id())); var seen=new HashSet<UUID>();
        for(var a:output.assessments()) {
            if(a==null || !ids.contains(a.requirementId()) || !seen.add(a.requirementId())) throw invalid("要求 ID 无效或重复");
            if(a.status()==null || !Set.of("MATCH","GAP","UNCERTAIN").contains(a.status())) throw invalid("判断状态无效");
            bounded(a.reason(),1000);
            if(a.resumeEvidence()==null || a.resumeEvidence().size()>5) throw invalid("证据列表无效");
            a.resumeEvidence().forEach(ref->reference(ref,version.segments()));
            if(!a.status().equals("UNCERTAIN") && a.resumeEvidence().isEmpty()) throw invalid("MATCH/GAP 必须有原文证据，未提及用 UNCERTAIN");
            if(a.status().equals("GAP") && !explicitGap(a.resumeEvidence()))
                throw invalid("缺少记录或只出现另一项技术不能证明不会；GAP 须引用明确否认或明确无法满足条件的原文，否则用 UNCERTAIN");
            if(a.actionType()==null || !Set.of("NONE","EVIDENCE_FIX","LEARN","CLARIFY").contains(a.actionType())) throw invalid("任务类型无效");
            if(!a.actionType().equals("NONE")) {
                bounded(a.taskTitle(),200); bounded(a.expectedOutput(),500);
                if(a.effortMinutes()<30 || a.effortMinutes()>960 || a.effortMinutes()%30!=0) throw invalid("任务耗时须为 30–960 分钟且为 30 的倍数");
            }
        }
        return new Evidence.Report(requirements,List.copyOf(output.assessments()));
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
    private CompassException invalid(String message) { return new CompassException(422,"INVALID_EVIDENCE",message); }
}
