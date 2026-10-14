package com.resumeai.infrastructure.persistence;

import com.resumeai.domain.resume.Resume;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    @Query(value="SELECT EXISTS(SELECT 1 FROM resume_version WHERE resume_id=:id)",nativeQuery=true)
    boolean hasCompassVersions(@Param("id") UUID id);
}
