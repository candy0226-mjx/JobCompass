package com.resumeai.compass;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SnapshotTests {
    @Test void normalizedContentHasStableHashAndScopedSegments() {
        var a=Snapshots.text("  项目一\r\n\r\nRedis 缓存\n ");
        assertThat(Snapshots.hash(a)).isEqualTo(Snapshots.hash(Snapshots.text("项目一\n\nRedis 缓存")));
        assertThat(Snapshots.segments(a,"r")).containsExactly(new Snapshots.Segment("r1","项目一"),new Snapshots.Segment("r2","Redis 缓存"));
        assertThat(Snapshots.segments(a,"j").getFirst().id()).isEqualTo("j1");
    }
    @Test void oversizedAndBlankInputsAreRejectedWithoutSilentTruncation() {
        assertThatThrownBy(()->Snapshots.text(" ")).isInstanceOf(CompassException.class);
        assertThatThrownBy(()->Snapshots.text("a".repeat(20_001))).isInstanceOf(CompassException.class);
    }
}
