package com.resumeai.compass;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {
    private final PlanningService plans;
    public PlanningController(PlanningService plans) { this.plans=plans; }
    public record Input(List<UUID> analysisIds,int weeklyBudgetMinutes,Map<UUID,Integer> jobPriorities) {}
    public record Confirm(String idempotencyKey,long expectedVersion) {}
    public record State(String status,String note,long expectedVersion) {}
    @PostMapping("/preparation-plans") public ResponseEntity<?> create(@RequestBody Input input) { return ResponseEntity.status(201).body(plans.create(input.analysisIds(),input.weeklyBudgetMinutes(),input.jobPriorities())); }
    @GetMapping("/preparation-plans") public List<Planning.Plan> list() { return plans.list(); }
    @GetMapping("/preparation-plans/{id}") public Planning.Plan get(@PathVariable UUID id) { return plans.get(id); }
    @GetMapping("/preparation-plans/{id}/events") public List<Planning.Event> history(@PathVariable UUID id) { return plans.planEvents(id); }
    @PatchMapping("/preparation-plans/{id}") public Planning.Plan patch(@PathVariable UUID id,@RequestBody Planning.Patch input) { return plans.patch(id,input); }
    @PostMapping("/preparation-plans/{id}/confirm") public Planning.Plan confirm(@PathVariable UUID id,@RequestBody Confirm input) { return plans.confirm(id,input.idempotencyKey(),input.expectedVersion()); }
    @PatchMapping("/preparation-plans/{id}/tasks/{taskId}") public Planning.Plan task(@PathVariable UUID id,@PathVariable UUID taskId,@RequestBody State input) { return plans.taskStatus(id,taskId,input.status(),input.expectedVersion()); }
    @GetMapping("/applications") public List<Planning.Application> applications() { return plans.applications(); }
    @PatchMapping("/applications/{id}") public Planning.Application application(@PathVariable UUID id,@RequestBody State input) { return plans.applicationStatus(id,input.status(),input.note(),input.expectedVersion()); }
    @GetMapping("/activity-events") public List<Planning.Event> events(@RequestParam(required=false) UUID resourceId) { return plans.events(resourceId); }
}
