package com.resumeai.application.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeai.application.ai.AiStructuringGateway;
import com.resumeai.domain.job.JobDescription;
import com.resumeai.infrastructure.persistence.JobDescriptionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDescriptionApplicationService {

    private final JobDescriptionRepository jobDescriptionRepository;
    private final AiStructuringGateway aiStructuringGateway;
    private final ObjectMapper objectMapper;

    public JobDescriptionApplicationService(
            JobDescriptionRepository jobDescriptionRepository,
            AiStructuringGateway aiStructuringGateway,
            ObjectMapper objectMapper
    ) {
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.aiStructuringGateway = aiStructuringGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JobDescriptionResponse create(CreateJobDescriptionRequest request) {
        var job = new JobDescription(
                blankToNull(request.title()),
                blankToNull(request.company()),
                request.description().trim()
        );
        return JobDescriptionResponse.from(jobDescriptionRepository.save(job));
    }

    @Transactional(readOnly = true)
    public List<JobDescriptionResponse> list() {
        return jobDescriptionRepository.findAll().stream()
                .map(JobDescriptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDescriptionResponse get(UUID jobId) {
        return jobDescriptionRepository.findById(jobId)
                .map(JobDescriptionResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Job description not found: " + jobId));
    }

    @Transactional
    public JobDescriptionResponse structure(UUID jobId) {
        var job = jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job description not found: " + jobId));
        var structured = aiStructuringGateway.structureJob(job.getDescription());
        job.markStructured(writeJson(structured));
        return JobDescriptionResponse.from(job);
    }

    @Transactional
    public void delete(UUID jobId) {
        if (jobDescriptionRepository.hasCompassReferences(jobId)) {
            throw new IllegalArgumentException("该岗位已有证据分析或申请记录，请保留历史资料。");
        }
        var job = jobDescriptionRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job description not found: " + jobId));
        jobDescriptionRepository.delete(job);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write structured job JSON.", exception);
        }
    }
}
