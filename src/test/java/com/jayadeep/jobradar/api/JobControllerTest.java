package com.jayadeep.jobradar.api;

import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.JobStatus;
import com.jayadeep.jobradar.ingest.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private JobRepository repository;

    @MockBean
    private IngestionService ingestion;

    private static Job job() {
        Job j = new Job("greenhouse", "1", "Acme", "Software Engineer");
        j.setFitScore(77);
        return j;
    }

    @Test
    void listsJobsByStatus() throws Exception {
        when(repository.findByStatusOrderByFitScoreDescUpdatedAtDesc(JobStatus.NEW)).thenReturn(List.of(job()));

        mvc.perform(get("/api/jobs").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company").value("Acme"))
                .andExpect(jsonPath("$[0].fitScore").value(77))
                .andExpect(jsonPath("$[0].description").doesNotExist());
    }

    @Test
    void updatesStatusAndNotes() throws Exception {
        Job j = job();
        when(repository.findById(5L)).thenReturn(Optional.of(j));
        when(repository.saveAndFlush(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(patch("/api/jobs/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPLIED\",\"notes\":\"Referred by Sam\",\"followUp\":\"2026-11-03\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.notes").value("Referred by Sam"))
                .andExpect(jsonPath("$.followUp").value("2026-11-03"));
    }

    @Test
    void unknownJobIs404() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Job 99 not found"));
    }

    @Test
    void manualJobRequiresDescription() throws Exception {
        mvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Acme\",\"title\":\"SWE\"}"))
                .andExpect(status().isBadRequest());
    }
}
