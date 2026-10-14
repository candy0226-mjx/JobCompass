package com.resumeai.application.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.application.ai.AiStructuringGateway;
import com.resumeai.domain.resume.Resume;
import com.resumeai.infrastructure.document.DocumentTextExtractor;
import com.resumeai.infrastructure.persistence.ResumeRepository;
import com.resumeai.infrastructure.storage.ObjectStorageService;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ResumeApplicationService {

    private final ResumeRepository resumeRepository;
    private final ObjectStorageService objectStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final AiStructuringGateway aiStructuringGateway;
    private final ObjectMapper objectMapper;

    public ResumeApplicationService(
            ResumeRepository resumeRepository,
            ObjectStorageService objectStorageService,
            DocumentTextExtractor documentTextExtractor,
            AiStructuringGateway aiStructuringGateway,
            ObjectMapper objectMapper
    ) {
        this.resumeRepository = resumeRepository;
        this.objectStorageService = objectStorageService;
        this.documentTextExtractor = documentTextExtractor;
        this.aiStructuringGateway = aiStructuringGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResumeResponse upload(ResumeUploadCommand command) {
        var file = command.file();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is required.");
        }

        try {
            var bytes = file.getBytes();
            var originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename());
            var contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            var objectKey = "resumes/%s/%s".formatted(UUID.randomUUID(), originalFilename);
            var rawText = documentTextExtractor.extract(new ByteArrayInputStream(bytes), originalFilename, contentType);
            objectStorageService.put(objectKey, new ByteArrayInputStream(bytes), bytes.length, contentType);

            var title = command.title() == null || command.title().isBlank()
                    ? inferTitle(originalFilename)
                    : command.title().trim();

            var resume = new Resume(title, originalFilename, contentType, objectKey, rawText);
            return ResumeResponse.from(resumeRepository.save(resume));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to upload and parse resume.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> list() {
        return resumeRepository.findAll().stream()
                .map(ResumeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(UUID resumeId) {
        return resumeRepository.findById(resumeId)
                .map(ResumeResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));
    }

    @Transactional
    public ResumeResponse structure(UUID resumeId) {
        var resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));
        var structured = aiStructuringGateway.structureResume(resume.getRawText());
        resume.markStructured(writeJson(structured));
        return ResumeResponse.from(resume);
    }

    @Transactional
    public void delete(UUID resumeId) {
        // Guard before deleting the object: new immutable versions deliberately restrict parent deletion.
        if (resumeRepository.hasCompassVersions(resumeId)) {
            throw new IllegalArgumentException("该简历已有求职罗盘版本，请保留原文件和版本记录。");
        }
        var resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));
        if (resume.getObjectKey() != null && !resume.getObjectKey().isBlank()) {
            objectStorageService.delete(resume.getObjectKey());
        }
        resumeRepository.delete(resume);
    }

    private String inferTitle(String originalFilename) {
        var dot = originalFilename.lastIndexOf('.');
        return dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write structured resume JSON.", exception);
        }
    }
}
