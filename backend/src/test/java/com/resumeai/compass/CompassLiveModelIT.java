package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import com.resumeai.ResumeAiAgentApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

/** Explicit opt-in: makes real calls using the ignored local .env. Never sends personal documents. */
class CompassLiveModelIT {
    record Case(String name,String resume,String jd,String expected,boolean adversarial) {}
    @Test void labeledFixturesHaveValidCitationsAndNoUnsupportedMatches() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules();
        var cases=mapper.readValue(Files.readString(Path.of("../samples/jobcompass-evaluation.json")),Case[].class);
        var results=new ArrayList<Map<String,Object>>();int valid=0,correct=0,falseMatches=0;
        try(var context=SpringApplication.run(ResumeAiAgentApplication.class,"--server.port=0")) {
            var model=context.getBean(EvidenceModel.class);
            for(var c:cases) {
                UUID versionId=UUID.randomUUID();var version=new Snapshots.Version(versionId,UUID.randomUUID(),1,c.resume(),Snapshots.hash(c.resume()),Snapshots.segments(c.resume(),"r"),OffsetDateTime.now());
                var job=new Snapshots.Job(UUID.randomUUID(),"脱敏评估岗位","虚构评估公司",c.jd(),Snapshots.hash(c.jd()),null,null,null,Snapshots.segments(c.jd(),"j"));
                long start=System.nanoTime();var item=new LinkedHashMap<String,Object>();item.put("name",c.name());item.put("expected",c.expected());
                try {
                    var result=model.analyze(UUID.randomUUID(),version,job,"无申请记录");valid++;
                    var statuses=result.report().assessments().stream().map(Evidence.Assessment::status).toList();
                    boolean match=statuses.size()==1 && statuses.getFirst().equals(c.expected());if(match) correct++;
                    if(!c.expected().equals("MATCH") && statuses.contains("MATCH")) falseMatches++;
                    item.put("actual",statuses);item.put("matchesLabel",match);item.put("toolCalls",result.toolCalls());item.put("validCitations",true);
                } catch(CompassException e) { item.put("failureCode",e.code);item.put("matchesLabel",false); }
                item.put("durationMs",(System.nanoTime()-start)/1_000_000);results.add(item);
                System.out.println("COMPASS_EVAL "+c.name()+" -> "+item.getOrDefault("actual",item.get("failureCode")));
            }
            var report=new LinkedHashMap<String,Object>();report.put("date",java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());report.put("model",context.getEnvironment().getProperty("spring.ai.openai.chat.options.model"));
            report.put("cases",cases.length);report.put("validReports",valid);report.put("labelMatches",correct);report.put("unsupportedMatches",falseMatches);report.put("results",results);
            Files.writeString(Path.of("../docs/jobcompass-evaluation-results.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
        assertThat(valid).as("All fixture reports should pass structure and citation validation").isEqualTo(15);
        assertThat(falseMatches).as("Negative fixtures must not be asserted as matched").isZero();
        assertThat(correct).as("Small manually labeled sample, not a general accuracy guarantee").isGreaterThanOrEqualTo(13);
    }
}
