package com.resumeai.compass.experience;

import com.resumeai.compass.*;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ExperienceService {
    private final JdbcTemplate db;
    private final CompassJson json;
    private final EvidenceService evidence;
    private final SnapshotService snapshots;
    private final ExperienceModel model;
    private final ExperienceValidator validator;
    private final TransactionTemplate tx;
    private final String modelName;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4), r -> { var t = new Thread(r, "compass-experience"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());

    public ExperienceService(JdbcTemplate db, CompassJson json, EvidenceService evidence, SnapshotService snapshots,
                             ExperienceModel model, ExperienceValidator validator, PlatformTransactionManager manager,
                             Environment env) {
        this.db = db; this.json = json; this.evidence = evidence; this.snapshots = snapshots;
        this.model = model; this.validator = validator; this.tx = new TransactionTemplate(manager);
        this.modelName = env.getProperty("spring.ai.openai.chat.options.model", "configured-model");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        db.update("UPDATE experience_inquiry SET status='FAILED',error_code='INTERRUPTED',version=version+1,updated_at=now() WHERE status='QUESTIONS_PENDING'");
    }

    @PreDestroy public void close() { workers.shutdownNow(); }

    public Experience.Session start(UUID analysisId, UUID requirementId) {
        if (requirementId == null) throw CompassException.bad("请选择要追问的岗位要求");
        var run = successful(analysisId);
        var requirement = requirement(run, requirementId);
        UUID proposed = UUID.randomUUID();
        UUID id = tx.execute(s -> {
            // Serialize duplicate starts without changing the original report or review.
            db.queryForObject("SELECT id FROM analysis_requirement WHERE id=? FOR UPDATE", UUID.class, requirementId);
            var prior = db.query("SELECT id FROM experience_inquiry WHERE requirement_id=?", (r,n) -> r.getObject(1, UUID.class), requirementId);
            if (!prior.isEmpty()) return prior.getFirst();
            db.update("INSERT INTO experience_inquiry(id,analysis_id,requirement_id,status,model_name) VALUES (?,?,?,'QUESTIONS_PENDING',?)",
                    proposed, analysisId, requirementId, modelName);
            event(proposed, null, "QUESTIONS_PENDING", 0);
            return proposed;
        });
        if (proposed.equals(id)) enqueue(context(id, run, requirement), 0);
        return get(analysisId, id);
    }

    public List<Experience.Session> list(UUID analysisId) {
        successful(analysisId);
        return db.query("SELECT id FROM experience_inquiry WHERE analysis_id=? ORDER BY created_at", (r,n) -> r.getObject(1, UUID.class), analysisId)
                .stream().map(id -> get(analysisId, id)).toList();
    }

    public Experience.Session get(UUID analysisId, UUID id) {
        var items = db.query("SELECT i.*,a.resume_version_id FROM experience_inquiry i JOIN analysis_run a ON a.id=i.analysis_id WHERE i.id=? AND i.analysis_id=?",
                (r,n) -> new Experience.Session(id, analysisId, r.getObject("requirement_id", UUID.class), r.getString("status"), r.getLong("version"),
                        readList(r.getString("questions_json"), Experience.Question[].class), readList(r.getString("answers_json"), Experience.Answer[].class),
                        r.getString("draft_json") == null ? null : json.read(r.getString("draft_json"), Experience.Draft.class),
                        cards(id, analysisId, r.getObject("requirement_id", UUID.class), r.getObject("resume_version_id", UUID.class)),
                        r.getString("error_code"), r.getObject("duration_ms", Long.class), r.getObject("created_at", OffsetDateTime.class)), id, analysisId);
        if (items.isEmpty()) throw CompassException.missing("追问不属于当前报告或不存在");
        return items.getFirst();
    }

    private <T> List<T> readList(String value, Class<T[]> type) { return List.of(json.read(value, type)); }

    private List<Experience.Card> cards(UUID inquiryId, UUID analysisId, UUID requirementId, UUID resumeVersionId) {
        return db.query("SELECT * FROM experience_evidence_card WHERE inquiry_id=? ORDER BY confirmed_at DESC,id",
                (r,n) -> new Experience.Card(r.getObject("id", UUID.class), inquiryId, analysisId, requirementId, resumeVersionId,
                        r.getLong("answer_version"), r.getString("source_type"),
                        readList(r.getString("questions_json"), Experience.Question[].class), readList(r.getString("answers_json"), Experience.Answer[].class),
                        json.read(r.getString("card_json"), Experience.Draft.class), r.getObject("confirmed_at", OffsetDateTime.class)), inquiryId);
    }

    public Experience.Session saveAnswers(UUID analysisId, UUID id, long expectedVersion, List<Experience.Answer> answers) {
        tx.executeWithoutResult(s -> {
            var session = locked(analysisId, id); version(session, expectedVersion);
            if (!Set.of("ANSWERING", "CARD_DRAFT").contains(session.status())) throw CompassException.conflict("请等追问完成，或先重新编辑已确认卡");
            var normalized = validator.answers(session.questions(), answers);
            if (db.update("UPDATE experience_inquiry SET answers_json=CAST(? AS jsonb),draft_json=NULL,status='ANSWERING',error_code=NULL,version=version+1,updated_at=now() WHERE id=? AND version=?",
                    json.write(normalized), id, expectedVersion) != 1) throw CompassException.conflict("回答已修改，请刷新");
            event(id, session.status(), "ANSWERS_SAVED", expectedVersion + 1);
        });
        return get(analysisId, id);
    }

    public Experience.Session draft(UUID analysisId, UUID id, long expectedVersion) {
        tx.executeWithoutResult(s -> {
            var session = locked(analysisId, id); version(session, expectedVersion);
            if (!Set.of("ANSWERING", "CARD_DRAFT").contains(session.status())) throw CompassException.conflict("当前状态不能生成证据卡");
            var run = successful(analysisId);
            var draft = validator.draft(requirement(run, session.requirementId()).text(), session.questions(), session.answers());
            db.update("UPDATE experience_inquiry SET draft_json=CAST(? AS jsonb),status='CARD_DRAFT',version=version+1,updated_at=now() WHERE id=? AND version=?",
                    json.write(draft), id, expectedVersion);
            event(id, session.status(), "CARD_DRAFT", expectedVersion + 1);
        });
        return get(analysisId, id);
    }

    public Experience.Session confirm(UUID analysisId, UUID id, long expectedVersion, boolean confirmTruth) {
        if (!confirmTruth) throw CompassException.bad("请确认回答符合真实经历；未验证或不记得的情况应如实保留");
        tx.executeWithoutResult(s -> {
            var session = locked(analysisId, id);
            if (session.status().equals("CONFIRMED")) return; // Idempotent retry after a lost response.
            version(session, expectedVersion);
            if (!session.status().equals("CARD_DRAFT") || session.draft() == null) throw CompassException.conflict("请先生成并查看证据卡草稿");
            // Rebuild from persisted answers, never accept client-supplied card facts or source labels.
            var title = requirement(successful(analysisId), session.requirementId()).text();
            var canonical = validator.draft(title, session.questions(), session.answers());
            var confirmed = new Experience.Draft(canonical.title(), "USER_CONFIRMED", canonical.facts());
            db.update("INSERT INTO experience_evidence_card(id,inquiry_id,answer_version,source_type,questions_json,answers_json,card_json) VALUES (?,?,?,'USER_CONFIRMED',CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb))",
                    UUID.randomUUID(), id, expectedVersion, json.write(session.questions()), json.write(session.answers()), json.write(confirmed));
            db.update("UPDATE experience_inquiry SET status='CONFIRMED',version=version+1,updated_at=now() WHERE id=? AND version=?", id, expectedVersion);
            event(id, "CARD_DRAFT", "USER_CONFIRMED", expectedVersion + 1);
        });
        return get(analysisId, id);
    }

    public Experience.Session reopen(UUID analysisId, UUID id, long expectedVersion) {
        tx.executeWithoutResult(s -> {
            var session = locked(analysisId, id); version(session, expectedVersion);
            if (!session.status().equals("CONFIRMED")) throw CompassException.conflict("仅已确认卡需要重新编辑");
            db.update("UPDATE experience_inquiry SET status='ANSWERING',draft_json=NULL,version=version+1,updated_at=now() WHERE id=? AND version=?", id, expectedVersion);
            event(id, "CONFIRMED", "REOPENED", expectedVersion + 1);
        });
        return get(analysisId, id);
    }

    public Experience.Session retry(UUID analysisId, UUID id, long expectedVersion) {
        long pendingVersion = tx.execute(s -> {
            var session = locked(analysisId, id); version(session, expectedVersion);
            if (!session.status().equals("FAILED")) throw CompassException.conflict("只有失败追问可以重试");
            db.update("UPDATE experience_inquiry SET status='QUESTIONS_PENDING',error_code=NULL,version=version+1,updated_at=now() WHERE id=? AND version=?", id, expectedVersion);
            event(id, "FAILED", "QUESTIONS_PENDING", expectedVersion + 1);
            return expectedVersion + 1;
        });
        var session = get(analysisId, id); var run = successful(analysisId);
        enqueue(context(id, run, requirement(run, session.requirementId())), pendingVersion);
        return get(analysisId, id);
    }

    private void enqueue(Experience.Context context, long pendingVersion) {
        try { workers.execute(() -> generate(context, pendingVersion)); }
        catch (RejectedExecutionException e) {
            fail(context.inquiryId(), pendingVersion, "QUEUE_FULL", 0);
            throw new CompassException(429, "QUEUE_FULL", "经历追问队列已满，请稍后重试；追问 ID：" + context.inquiryId());
        }
    }

    private void generate(Experience.Context context, long pendingVersion) {
        long start = System.nanoTime(); String correction = "";
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                var questions = validator.questions(model.questions(context, correction));
                long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                tx.executeWithoutResult(s -> {
                    int changed = db.update("UPDATE experience_inquiry SET questions_json=CAST(? AS jsonb),status='ANSWERING',error_code=NULL,duration_ms=?,version=version+1,updated_at=now() WHERE id=? AND status='QUESTIONS_PENDING' AND version=?",
                            json.write(questions), duration, context.inquiryId(), pendingVersion);
                    if (changed == 1) event(context.inquiryId(), "QUESTIONS_PENDING", "ANSWERING", pendingVersion + 1);
                });
                return;
            } catch (Exception e) {
                if (attempt == 1) {
                    String code = e instanceof CompassException ce ? ce.code : "MODEL_FAILED";
                    fail(context.inquiryId(), pendingVersion, code, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
                    return;
                }
                correction = e instanceof CompassException ? e.getMessage() : "上次响应格式无效，请严格返回三个指定类别的问题。";
            }
        }
    }

    private void fail(UUID id, long pendingVersion, String code, long duration) {
        tx.executeWithoutResult(s -> {
            if (db.update("UPDATE experience_inquiry SET status='FAILED',error_code=?,duration_ms=?,version=version+1,updated_at=now() WHERE id=? AND status='QUESTIONS_PENDING' AND version=?",
                    code, duration, id, pendingVersion) == 1) event(id, "QUESTIONS_PENDING", "FAILED", pendingVersion + 1);
        });
    }

    private Experience.Session locked(UUID analysisId, UUID id) {
        var found = db.query("SELECT id FROM experience_inquiry WHERE id=? AND analysis_id=? FOR UPDATE", (r,n) -> r.getObject(1, UUID.class), id, analysisId);
        if (found.isEmpty()) throw CompassException.missing("追问不属于当前报告或不存在");
        return get(analysisId, id);
    }

    private void version(Experience.Session session, long expected) {
        if (session.version() != expected) throw CompassException.conflict("追问内容已修改，请刷新后重试");
    }

    private EvidenceService.Run successful(UUID id) {
        var run = evidence.get(id);
        if (!run.status().equals("SUCCEEDED")) throw CompassException.conflict("只有成功的证据报告可以追问");
        return run;
    }

    private Evidence.Requirement requirement(EvidenceService.Run run, UUID id) {
        return run.report().requirements().stream().filter(q -> q.id().equals(id)).findFirst()
                .orElseThrow(() -> CompassException.bad("岗位要求不属于当前报告"));
    }

    private Experience.Context context(UUID id, EvidenceService.Run run, Evidence.Requirement requirement) {
        var assessment = run.report().assessments().stream().filter(a -> a.requirementId().equals(requirement.id())).findFirst().orElseThrow();
        return new Experience.Context(id, run.id(), snapshots.version(run.resumeVersionId()), run.job(), requirement, assessment);
    }

    private void event(UUID id, String oldValue, String newValue, long version) {
        db.update("INSERT INTO activity_event(id,resource_type,resource_id,old_value,new_value,version) VALUES (?,'EXPERIENCE',?,?,?,?)",
                UUID.randomUUID(), id, oldValue, newValue, version);
    }
}
