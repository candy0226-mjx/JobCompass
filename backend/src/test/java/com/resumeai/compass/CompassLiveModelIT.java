package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import com.resumeai.ResumeAiAgentApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** Opt-in paired pilot. Stop the normal backend first; only fictional snapshots are sent. */
class CompassLiveModelIT {
    record Case(String name,String resume,String jd,String expected,boolean adversarial,String split) {}
    record Row(String name,String split,String mode,String expected,List<String> actual,boolean success,
               boolean matchesLabel,String failureCode,long durationMs,AnalysisMetrics.Snapshot metrics) {
        EvaluationSummary.Item item() { return new EvaluationSummary.Item(expected,actual,success,durationMs,
            metrics==null?null:metrics.reportedInputTokens(),metrics==null?null:metrics.reportedOutputTokens(),metrics!=null && metrics.usageComplete(),metrics==null?0:metrics.retryCount()); }
    }
    @Test void pairedSnapshotEvaluationKeepsAllOutcomesAndUsage() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();byte[] data=Files.readAllBytes(Path.of("../samples/jobcompass-evaluation.json"));
        var cases=mapper.readValue(data,Case[].class);var rows=new ArrayList<Row>();
        int limit=Integer.getInteger("compass.eval.limit",cases.length);assertThat(limit).isBetween(1,cases.length);
        Path output=Path.of(System.getProperty("compass.eval.output","../docs/jobcompass-evaluation-comparison.json"));
        var root=new LinkedHashMap<String,Object>();root.put("date",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());
        root.put("datasetSha256",sha(data));root.put("promptVersion","evidence-v2");root.put("pilotRepetitions",1);
        root.put("scope","Fictional single-requirement cases, paired alternating order; not general accuracy or load-test data.");
        root.put("usageBasis","Cumulative ChatClient response usage across tool rounds, summed across stages/retries. Missing usage is not zero; stage attempts are not HTTP calls.");
        root.put("changedVariable","Tool snapshot payload FULL vs COMPACT; every original segment is retained, prompts/validation unchanged.");
        root.put("rows",rows);root.put("completed",false);root.put("plannedTasks",limit*2);
        try(var context=SpringApplication.run(ResumeAiAgentApplication.class,"--server.port=0")) {
            var model=context.getBean(SpringAiEvidenceModel.class);
            root.put("requestedModel",context.getEnvironment().getProperty("spring.ai.openai.chat.options.model"));root.put("temperature",0.0);
            root.put("springAiVersion","2.0.0");
            for(int i=0;i<limit;i++) {
                var c=cases[i];var v=new Snapshots.Version(UUID.randomUUID(),UUID.randomUUID(),1,c.resume(),Snapshots.hash(c.resume()),Snapshots.segments(c.resume(),"r"),OffsetDateTime.now());
                var j=new Snapshots.Job(UUID.randomUUID(),"评估岗位","虚构公司",c.jd(),Snapshots.hash(c.jd()),null,null,null,Snapshots.segments(c.jd(),"j"));
                UUID runId=UUID.randomUUID();var modes=i%2==0?List.of(JobCompassReadTools.SnapshotMode.FULL,JobCompassReadTools.SnapshotMode.COMPACT):List.of(JobCompassReadTools.SnapshotMode.COMPACT,JobCompassReadTools.SnapshotMode.FULL);
                for(var mode:modes) {
                    long start=System.nanoTime();Row row;
                    try {
                        var result=model.analyze(runId,v,j,"无申请记录",mode);
                        var actual=result.report().assessments().stream().map(Evidence.Assessment::status).toList();
                        row=new Row(c.name(),c.split(),mode.name(),c.expected(),actual,true,actual.size()==1 && actual.getFirst().equals(c.expected()),null,elapsed(start),result.metrics());
                    } catch(CompassException e) { row=new Row(c.name(),c.split(),mode.name(),c.expected(),List.of(),false,false,e.code,elapsed(start),e.metrics()); }
                    rows.add(row);write(mapper,output,root);
                    System.out.println("COMPASS_PAIR "+c.split()+" "+mode+" "+c.name()+" -> "+(row.success()?row.actual():row.failureCode())+" tokens="+(row.metrics()==null?"missing":row.metrics().inputTokens())+" retries="+(row.metrics()==null?0:row.metrics().retryCount()));
                }
            }
        }
        var summaries=new LinkedHashMap<String,Object>();
        for(var split:List.of("development","holdout","all")) for(var mode:List.of("FULL","COMPACT")) {
            summaries.put(split+"_"+mode,EvaluationSummary.summarize(rows.stream().filter(r->r.mode().equals(mode) && (split.equals("all") || r.split().equals(split))).map(Row::item).toList()));
        }
        root.put("summaries",summaries);root.put("completed",true);
        var heldout=rows.stream().filter(r->r.split().equals("holdout")).toList();
        boolean pairedNoRegression=!heldout.isEmpty();
        for(var full:heldout.stream().filter(r->r.mode().equals("FULL")).toList()) {
            var compact=heldout.stream().filter(r->r.mode().equals("COMPACT") && r.name().equals(full.name())).findFirst().orElseThrow();
            if((full.success() && !compact.success()) || (full.matchesLabel() && !compact.matchesLabel()) || (!full.actual().contains("MATCH") && !full.expected().equals("MATCH") && compact.actual().contains("MATCH"))) pairedNoRegression=false;
        }
        var full=rows.stream().filter(r->r.mode().equals("FULL")).toList();var compact=rows.stream().filter(r->r.mode().equals("COMPACT")).toList();
        boolean usageComplete=rows.stream().allMatch(r->r.metrics()!=null && r.metrics().usageComplete());
        long fullInput=full.stream().mapToLong(r->r.metrics()==null?0:r.metrics().reportedInputTokens()).sum();
        long compactInput=compact.stream().mapToLong(r->r.metrics()==null?0:r.metrics().reportedInputTokens()).sum();
        root.put("holdoutPairedNoRegression",pairedNoRegression);root.put("usageComplete",usageComplete);
        root.put("inputTokenReduction",usageComplete && fullInput>0?1.0-(double)compactInput/fullInput:null);
        root.put("compactEligible",limit==cases.length && pairedNoRegression && usageComplete && compactInput<fullInput);
        write(mapper,output,root);
        assertThat(rows).hasSize(limit*2);assertThat(rows).allMatch(r->r.metrics()!=null);
        // Quality differences remain data; never tune prompts on the holdout to force a passing score.
    }
    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void write(ObjectMapper mapper,Path output,Object value) throws Exception {
        Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
