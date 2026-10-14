package com.resumeai.infrastructure.persistence;

import com.resumeai.domain.job.JobDescription;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {
    @Query(value="SELECT EXISTS(SELECT 1 FROM analysis_run WHERE job_id=:id) OR EXISTS(SELECT 1 FROM job_application WHERE job_id=:id)",nativeQuery=true)
    boolean hasCompassReferences(@Param("id") UUID id);
}
