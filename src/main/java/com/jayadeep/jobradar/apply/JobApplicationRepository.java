package com.jayadeep.jobradar.apply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findAllByOrderByUpdatedAtDesc();

    List<JobApplication> findByJobIdOrderByCreatedAtDesc(Long jobId);
}
