package com.resumeai.compass;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import com.resumeai.infrastructure.document.DocumentTextExtractor;
import com.resumeai.infrastructure.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
public class SnapshotController {
    private final SnapshotService snapshots;
    private final DocumentTextExtractor extractor;
    private final ObjectStorageService storage;
    public SnapshotController(SnapshotService snapshots,DocumentTextExtractor extractor,ObjectStorageService storage) { this.snapshots=snapshots; this.extractor=extractor; this.storage=storage; }
    public record TextInput(String title,String text) {}
    public record JobInput(String title,String company,String text,String location,String sourceUrl,LocalDate deadline) {}
    @PostMapping("/resumes/text") public ResponseEntity<?> text(@RequestBody TextInput input) {
        return ResponseEntity.status(201).body(snapshots.createText(input.title(),input.text()));
    }
    @PostMapping("/resumes/{id}/versions") public ResponseEntity<?> version(@PathVariable UUID id,@RequestBody TextInput input) {
        return ResponseEntity.status(201).body(snapshots.createVersion(id,input.text()));
    }
    @PostMapping("/resumes/{id}/versions/import") public Snapshots.Version importCurrent(@PathVariable UUID id) { return snapshots.importCurrent(id); }
    @GetMapping("/resumes/{id}/versions") public List<Snapshots.Version> versions(@PathVariable UUID id) { return snapshots.versions(id); }
    @PostMapping(value="/compass/resumes/upload",consumes="multipart/form-data")
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file,@RequestPart("title") String title) {
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw CompassException.bad("PDF 不能为空且不能超过 5MB");
        Snapshots.field(title,"简历名称",200);
        String filename=Snapshots.field(file.getOriginalFilename(),"文件名",500);
        if(!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new CompassException(422,"INVALID_PDF","文件扩展名须为 .pdf");
        try(var pdf=Loader.loadPDF(file.getBytes())) {
            if(pdf.getNumberOfPages()>10) throw CompassException.bad("PDF 不能超过 10 页");
            if(pdf.isEncrypted()) throw CompassException.bad("请上传未加密的 PDF");
        } catch(CompassException e) { throw e; }
        catch(Exception e) { throw new CompassException(422,"INVALID_PDF","文件不是可读取的 PDF"); }
        byte[] bytes;String raw;
        try {
            bytes=file.getBytes();raw=extractor.extract(new ByteArrayInputStream(bytes),filename,"application/pdf");
        } catch(Exception e) { throw new CompassException(422,"PDF_PARSE_FAILED","PDF 正文提取失败，请粘贴正文"); }
        if(raw==null || raw.isBlank()) throw new CompassException(422,"PDF_NO_TEXT","没有可提取的正文，请粘贴简历文本");
        Snapshots.text(raw);
        String key="resumes/"+UUID.randomUUID()+"/resume.pdf";
        storage.put(key,new ByteArrayInputStream(bytes),bytes.length,"application/pdf");
        try { return ResponseEntity.status(201).body(snapshots.createUploaded(title,raw,filename,key)); }
        catch(RuntimeException e) { try { storage.delete(key); } catch(Exception cleanup) { e.addSuppressed(cleanup); } throw e; }
    }
    @PostMapping("/compass/jobs") public ResponseEntity<?> job(@RequestBody JobInput input) {
        return ResponseEntity.status(201).body(snapshots.createJob(input.title(),input.company(),input.text(),input.location(),input.sourceUrl(),input.deadline()));
    }
}
