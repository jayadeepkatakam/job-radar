package com.jayadeep.jobradar.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findBySourceAndExternalId(String source, String externalId);

    boolean existsBySourceAndExternalId(String source, String externalId);

    List<Job> findByStatusOrderByFitScoreDescUpdatedAtDesc(JobStatus status);

    List<Job> findAllByOrderByUpdatedAtDesc();
}
