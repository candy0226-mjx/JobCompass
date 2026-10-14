package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class EvaluationSummaryTests {
    @Test void failuresStayInDenominatorAndIncompleteUsageIsNotZeroCost() {
        var items=List.of(new EvaluationSummary.Item("MATCH",List.of("MATCH"),true,100,10L,5L,true,0),
            new EvaluationSummary.Item("UNCERTAIN",List.of("MATCH"),true,200,12L,8L,true,1),
            new EvaluationSummary.Item("GAP",List.of(),false,1000,4L,2L,false,1));
        var s=EvaluationSummary.summarize(items);
        assertThat(s.get("labelAgreement")).isEqualTo(1.0/3);assertThat(s.get("successRate")).isEqualTo(2.0/3);
        assertThat(s.get("falseMatchRate")).isEqualTo(0.5);assertThat(s.get("failedNegativeTasks")).isEqualTo(1);
        assertThat(s.get("meanTotalTokens")).isNull();assertThat(s.get("retryCount")).isEqualTo(2);
        assertThat(s.get("reportedInputTokens")).isEqualTo(26L);assertThat(s.get("reportedOutputTokens")).isEqualTo(15L);
        assertThat(s.get("p50DurationMs")).isEqualTo(200L);assertThat(s.get("p95DurationMs")).isEqualTo(1000L);
    }
    @Test void emptyOrPositiveOnlySamplesDoNotInventRates() {
        assertThat(EvaluationSummary.summarize(List.of()).get("labelAgreement")).isNull();
        assertThat(EvaluationSummary.summarize(List.of(new EvaluationSummary.Item("MATCH",List.of("MATCH"),true,1,2L,3L,true,0))).get("falseMatchRate")).isNull();
    }
    @Test void fixtureSplitsAreDisjointAndCoverAllThreeLabels() throws Exception {
        var path=Path.of("../samples/jobcompass-evaluation.json");if(!Files.exists(path)) path=Path.of("samples/jobcompass-evaluation.json");
        var cases=new ObjectMapper().readTree(Files.readString(path));assertThat(cases.size()).isEqualTo(40);
        var ids=new HashSet<String>(); var splitLabels=new HashMap<String,Set<String>>();var counts=new HashMap<String,Integer>();
        for(var c:cases) { assertThat(ids.add(c.path("name").asText())).isTrue();String split=c.path("split").asText();assertThat(split).isIn("development","holdout");
            splitLabels.computeIfAbsent(split,k->new HashSet<>()).add(c.path("expected").asText());counts.merge(split,1,Integer::sum);
            assertThat(c.path("resume").asText()).isNotBlank();assertThat(c.path("jd").asText()).isNotBlank(); }
        assertThat(counts).containsEntry("development",25).containsEntry("holdout",15);
        splitLabels.values().forEach(labels->assertThat(labels).containsExactlyInAnyOrder("MATCH","GAP","UNCERTAIN"));
    }
}
