package com.resumeai.compass;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SnapshotService {
    private final JdbcTemplate db;
    private final CompassJson json;
    private final TransactionTemplate tx;
    public SnapshotService(JdbcTemplate db, CompassJson json, PlatformTransactionManager manager) {
        this.db=db; this.json=json; this.tx=new TransactionTemplate(manager);
    }
    public Snapshots.Version createText(String title, String input) {
        var name=Snapshots.field(title,"简历名称",200); var text=Snapshots.text(input);
        return Objects.requireNonNull(tx.execute(s -> {
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO resumes(id,title,raw_text,status) VALUES (?,?,?,'PARSED')",id,name,text);
            return saveVersion(id,text);
        }));
    }
    public Snapshots.Version createUploaded(String title,String input,String filename,String objectKey) {
        var name=Snapshots.field(title,"简历名称",200);var text=Snapshots.text(input);
        return Objects.requireNonNull(tx.execute(s->{
            UUID id=UUID.randomUUID();
            db.update("INSERT INTO resumes(id,title,raw_text,status,original_filename,content_type,object_key) VALUES (?,?,?,'PARSED',?,'application/pdf',?)",id,name,text,filename,objectKey);
            return saveVersion(id,text);
        }));
    }
    public Snapshots.Version createVersion(UUID resumeId, String input) {
        var text=Snapshots.text(input);
        return Objects.requireNonNull(tx.execute(s -> saveVersion(resumeId,text)));
    }
    public Snapshots.Version importCurrent(UUID resumeId) {
        return Objects.requireNonNull(tx.execute(s -> {
            var texts=db.query("SELECT raw_text FROM resumes WHERE id=? FOR UPDATE",(r,n)->r.getString(1),resumeId);
            if(texts.isEmpty()) throw CompassException.missing("简历不存在");
            var text=Snapshots.text(texts.getFirst());
            var existing=versions(resumeId);
            if(!existing.isEmpty() && existing.getFirst().hash().equals(Snapshots.hash(text))) return existing.getFirst();
            return saveVersion(resumeId,text);
        }));
    }
    private Snapshots.Version saveVersion(UUID resumeId,String text) {
        if(db.query("SELECT id FROM resumes WHERE id=? FOR UPDATE",(r,n)->r.getObject(1,UUID.class),resumeId).isEmpty())
            throw CompassException.missing("简历不存在");
        int number=db.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM resume_version WHERE resume_id=?",Integer.class,resumeId);
        UUID id=UUID.randomUUID(); var segments=Snapshots.segments(text,"r");
        db.update("INSERT INTO resume_version(id,resume_id,version_no,raw_text,content_hash,segments_json) VALUES (?,?,?,?,?,CAST(? AS jsonb))",
            id,resumeId,number,text,Snapshots.hash(text),json.write(segments));
        return version(id);
    }
    public Snapshots.Version version(UUID id) {
        var items=db.query("SELECT * FROM resume_version WHERE id=?",(r,n)->new Snapshots.Version(
            r.getObject("id",UUID.class),r.getObject("resume_id",UUID.class),r.getInt("version_no"),r.getString("raw_text"),
            r.getString("content_hash"),List.of(json.read(r.getString("segments_json"),Snapshots.Segment[].class)),r.getObject("created_at",OffsetDateTime.class)),id);
        if(items.isEmpty()) throw CompassException.missing("简历版本不存在"); return items.getFirst();
    }
    public List<Snapshots.Version> versions(UUID resumeId) {
        return db.query("SELECT id FROM resume_version WHERE resume_id=? ORDER BY version_no DESC",(r,n)->r.getObject(1,UUID.class),resumeId)
            .stream().map(this::version).toList();
    }
    public Snapshots.Job createJob(String title,String company,String input,String location,String sourceUrl,LocalDate deadline) {
        var name=Snapshots.field(title,"岗位名称",200); var employer=Snapshots.field(company,"公司",200); var text=Snapshots.text(input);
        if(location!=null && location.length()>200 || sourceUrl!=null && sourceUrl.length()>2000) throw CompassException.bad("地点或来源链接过长");
        var ids=db.query("SELECT id FROM job_descriptions WHERE title=? AND company=? AND compass_hash=? ORDER BY created_at LIMIT 1",
            (r,n)->r.getObject(1,UUID.class),name,employer,Snapshots.hash(text));
        if(!ids.isEmpty()) return job(ids.getFirst());
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO job_descriptions(id,title,company,description,status,compass_hash,compass_location,compass_source_url,compass_deadline) VALUES (?,?,?,?,'CREATED',?,?,?,?)",
            id,name,employer,text,Snapshots.hash(text),location,sourceUrl,deadline);
        return job(id);
    }
    public Snapshots.Job job(UUID id) {
        var jobs=db.query("SELECT * FROM job_descriptions WHERE id=?",(r,n)-> {
            String text=Snapshots.text(r.getString("description"));
            return new Snapshots.Job(id,r.getString("title"),r.getString("company"),text,Snapshots.hash(text),
                r.getString("compass_location"),r.getString("compass_source_url"),r.getObject("compass_deadline",LocalDate.class),Snapshots.segments(text,"j"));
        },id);
        if(jobs.isEmpty()) throw CompassException.missing("岗位不存在"); return jobs.getFirst();
    }
}
