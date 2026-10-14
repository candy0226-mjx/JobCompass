package com.resumeai.compass;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.*;
import org.springframework.ai.chat.model.ChatResponse;

class AnalysisMetricsTests {
    private ChatResponse response(int in,int out) { return ChatResponse.builder().generations(List.of()).metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(in,out)).build()).build(); }
    @Test void sumsEveryStageAndValidationRetryIncludingRejectedResponses() {
        var m=new AnalysisMetrics("FULL");var first=m.begin("EXTRACT",1);first.recordUsage(response(100,20));first.finish(1,"INVALID_EVIDENCE");
        var retry=m.begin("EXTRACT",2);retry.recordUsage(response(120,30));retry.finish(1,null);
        var assess=m.begin("ASSESS",1);assess.recordUsage(response(200,40));assess.finish(2,null);
        var s=m.snapshot();assertThat(s.inputTokens()).isEqualTo(420L);assertThat(s.outputTokens()).isEqualTo(90L);
        assertThat(s.retryCount()).isEqualTo(1);assertThat(s.validationFailures()).isEqualTo(1);assertThat(s.toolCalls()).isEqualTo(4);
        assertThat(s.usageComplete()).isTrue();assertThat(s.attempts()).hasSize(3);
    }
    @Test void networkFailureMakesTotalsUnknownButRetainsReportedUsage() {
        var m=new AnalysisMetrics("COMPACT");var a=m.begin("EXTRACT",1);a.recordUsage(response(40,10));a.finish(1,null);
        var b=m.begin("ASSESS",1);b.finish(0,"MODEL_FAILED");var s=m.snapshot();
        assertThat(s.inputTokens()).isNull();assertThat(s.usageComplete()).isFalse();assertThat(s.reportedInputTokens()).isEqualTo(40);
        assertThat(s.modelFailures()).isEqualTo(1);assertThatThrownBy(()->b.finish(0,null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void emptyUsageDoesNotPretendTaskIsFreeAndScopesAreIndependent() {
        var m=new AnalysisMetrics("FULL");var a=m.begin("EXTRACT",1);a.recordUsage(response(0,0));a.finish(0,null);
        assertThat(m.snapshot().usageComplete()).isFalse();assertThat(new AnalysisMetrics("FULL").snapshot().attempts()).isEmpty();
    }
    @Test void compactSnapshotPreservesEverySegmentAndToolScope() throws Exception {
        var json=new CompassJson(new ObjectMapper().findAndRegisterModules());var id=UUID.randomUUID();String text="使用 Redis 缓存。\n尚未使用分布式锁。";
        var v=new Snapshots.Version(UUID.randomUUID(),UUID.randomUUID(),1,text,Snapshots.hash(text),Snapshots.segments(text,"r"),OffsetDateTime.now());
        var j=new Snapshots.Job(UUID.randomUUID(),"Java","虚构",text,Snapshots.hash(text),null,null,null,Snapshots.segments(text,"j"));
        var compact=new JobCompassReadTools(id,v,j,"无",json,JobCompassReadTools.SnapshotMode.COMPACT);
        var full=new JobCompassReadTools(id,v,j,"无",json,JobCompassReadTools.SnapshotMode.FULL);
        var c=compact.getResumeVersion(v.id().toString());assertThat(c.length()).isLessThan(full.getResumeVersion(v.id().toString()).length());
        assertThat(json.read(c,com.fasterxml.jackson.databind.JsonNode.class).get("segments")).isEqualTo(new ObjectMapper().valueToTree(v.segments()));
        assertThat(c).contains("尚未使用分布式锁");assertThatThrownBy(()->compact.getJobSnapshot(UUID.randomUUID().toString())).isInstanceOf(CompassException.class);
    }
}
