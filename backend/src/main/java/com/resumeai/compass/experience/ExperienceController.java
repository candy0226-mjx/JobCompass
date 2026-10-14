package com.resumeai.compass.experience;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/evidence-analyses/{analysisId}/experience-inquiries")
public class ExperienceController {
    private final ExperienceService service;
    public ExperienceController(ExperienceService service) { this.service = service; }
    public record Start(UUID requirementId) {}
    public record Answers(long expectedVersion, List<Experience.Answer> answers) {}
    public record Version(long expectedVersion) {}
    public record Confirm(long expectedVersion, boolean confirmTruth) {}

    @PostMapping public ResponseEntity<Experience.Session> start(@PathVariable UUID analysisId, @RequestBody Start input) {
        return ResponseEntity.accepted().body(service.start(analysisId, input.requirementId()));
    }
    @GetMapping public List<Experience.Session> list(@PathVariable UUID analysisId) { return service.list(analysisId); }
    @GetMapping("/{id}") public Experience.Session get(@PathVariable UUID analysisId, @PathVariable UUID id) { return service.get(analysisId,id); }
    @PutMapping("/{id}/answers") public Experience.Session answers(@PathVariable UUID analysisId, @PathVariable UUID id, @RequestBody Answers input) {
        return service.saveAnswers(analysisId, id, input.expectedVersion(), input.answers());
    }
    @PostMapping("/{id}/draft") public Experience.Session draft(@PathVariable UUID analysisId, @PathVariable UUID id, @RequestBody Version input) {
        return service.draft(analysisId, id, input.expectedVersion());
    }
    @PostMapping("/{id}/confirm") public Experience.Session confirm(@PathVariable UUID analysisId, @PathVariable UUID id, @RequestBody Confirm input) {
        return service.confirm(analysisId, id, input.expectedVersion(), input.confirmTruth());
    }
    @PostMapping("/{id}/reopen") public Experience.Session reopen(@PathVariable UUID analysisId, @PathVariable UUID id, @RequestBody Version input) {
        return service.reopen(analysisId, id, input.expectedVersion());
    }
    @PostMapping("/{id}/retry") public ResponseEntity<Experience.Session> retry(@PathVariable UUID analysisId, @PathVariable UUID id, @RequestBody Version input) {
        return ResponseEntity.accepted().body(service.retry(analysisId, id, input.expectedVersion()));
    }
}
