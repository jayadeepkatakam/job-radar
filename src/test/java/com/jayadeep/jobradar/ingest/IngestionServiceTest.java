package com.jayadeep.jobradar.ingest;

import com.jayadeep.jobradar.config.AppProperties;
import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.Verdict;
import com.jayadeep.jobradar.scoring.FitResult;
import com.jayadeep.jobradar.scoring.ScoringService;
import com.jayadeep.jobradar.screening.DealBreakerScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    private JobSource greenhouse;
    private JobRepository repository;
    private ScoringService scoring;
    private IngestionService service;

    @BeforeEach
    void setUp() {
        greenhouse = mock(JobSource.class);
        when(greenhouse.name()).thenReturn("greenhouse");
        repository = mock(JobRepository.class);
        when(repository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        scoring = mock(ScoringService.class);

        AppProperties props = new AppProperties(
                List.of(new AppProperties.Board("greenhouse", "acme", "Acme"),
                        new AppProperties.Board("workday", "nope", "Nope")),
                List.of("engineer"),
                List.of("staff"),
                List.of("Java"),
                "profile",
                null);
        service = new IngestionService(props, List.of(greenhouse), repository, new DealBreakerScanner(), scoring);
    }

    private static RawJob raw(String id, String title, String description) {
        return new RawJob("greenhouse", id, "Acme", title, "Remote", "https://x/" + id, description, null);
    }

    @Test
    void savesNewMatchingJobsAndScoresThem() {
        when(greenhouse.fetch("acme", "Acme")).thenReturn(List.of(raw("1", "Software Engineer", "Java and Kafka")));
        when(scoring.score(anyString(), anyString())).thenReturn(new FitResult(80, "Good", List.of("Java"), "keyword"));

        IngestionService.IngestReport report = service.runAll();

        assertThat(report.fetched()).isEqualTo(1);
        assertThat(report.added()).isEqualTo(1);
        assertThat(report.errors()).singleElement().asString().contains("unknown source");

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFitScore()).isEqualTo(80);
        assertThat(saved.getValue().getVerdict()).isEqualTo(Verdict.APPLY);
    }

    @Test
    void skipsDuplicatesAndOffTargetTitles() {
        when(greenhouse.fetch("acme", "Acme")).thenReturn(List.of(
                raw("1", "Software Engineer", "Java"),
                raw("2", "Staff Software Engineer", "Java"),
                raw("3", "Account Executive", "Sales")));
        when(repository.existsBySourceAndExternalId("greenhouse", "1")).thenReturn(true);

        IngestionService.IngestReport report = service.runAll();

        assertThat(report.added()).isZero();
        assertThat(report.filteredByTitle()).isEqualTo(2);
        verify(repository, never()).save(any());
    }

    @Test
    void dealBreakersSkipScoring() {
        when(greenhouse.fetch("acme", "Acme")).thenReturn(List.of(
                raw("9", "Software Engineer", "Active TS/SCI clearance required.")));

        service.runAll();

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getVerdict()).isEqualTo(Verdict.SKIP);
        assertThat(saved.getValue().getDealBreakers()).contains("Requires a security clearance");
        verify(scoring, never()).score(anyString(), anyString());
    }

    @Test
    void oneFailingBoardDoesNotStopTheRun() {
        when(greenhouse.fetch("acme", "Acme")).thenThrow(new RuntimeException("503 Service Unavailable"));

        IngestionService.IngestReport report = service.runAll();

        assertThat(report.errors()).hasSize(2).anyMatch(e -> e.contains("503"));
    }
}
