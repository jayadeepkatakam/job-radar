package com.jayadeep.jobradar;

import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole app against in-memory H2 and checks that Flyway's schema matches the entity. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobradar;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "jobradar.schedule.enabled=false",
        "jobradar.claude.api-key="
})
class JobRadarApplicationTest {

    @Autowired
    private JobRepository repository;

    @Test
    void savesAndReadsBackAJob() {
        Job job = new Job("manual", "x-1", "Acme", "Software Engineer");
        job.setDescription("A".repeat(20_000));
        job.setDealBreakers(List.of("Requires a security clearance", "No visa sponsorship"));
        Job saved = repository.save(job);

        Job loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(JobStatus.NEW);
        assertThat(loaded.getDescription()).hasSize(20_000);
        assertThat(loaded.getDealBreakers()).containsExactly("Requires a security clearance", "No visa sponsorship");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(repository.existsBySourceAndExternalId("manual", "x-1")).isTrue();
    }
}
