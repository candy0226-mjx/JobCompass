package com.resumeai.compass;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence-analyses")
public class EvidenceController {
    private final EvidenceService evidence;
    public EvidenceController(EvidenceService evidence) { this.evidence=evidence; }
    public record Input(UUID resumeVersionId,UUID jobId) {}
    public record ReviewInput(List<Evidence.Review> reviews) {}
    @PostMapping public ResponseEntity<?> submit(@RequestBody Input input) {
        if(input.resumeVersionId()==null || input.jobId()==null) throw CompassException.bad("版本和岗位必填");
        return ResponseEntity.accepted().body(evidence.submit(input.resumeVersionId(),input.jobId(),null));
    }
    @GetMapping public List<EvidenceService.Run> list() { return evidence.list(); }
    @GetMapping("/{id}") public EvidenceService.Run get(@PathVariable UUID id) { return evidence.get(id); }
    @PostMapping("/{id}/retry") public ResponseEntity<?> retry(@PathVariable UUID id) { return ResponseEntity.accepted().body(evidence.retry(id)); }
    @PutMapping("/{id}/reviews") public EvidenceService.Run review(@PathVariable UUID id,@RequestBody ReviewInput input) { return evidence.review(id,input.reviews()); }
}
