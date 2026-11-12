package com.jobqueue.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Needs Docker (Testcontainers). Runs under `mvn verify` via Failsafe. */
@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class JobApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private static final String BODY = """
            {"jobType":"DATA_PROCESSING","payload":{"numbers":[10,20,30,40]},"maxRetries":3,"priority":5}
            """;

    private String submit(String body, String key) throws Exception {
        var req = post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            req.header("Idempotency-Key", key);
        }
        return mvc.perform(req).andReturn().getResponse().getContentAsString();
    }

    @Test
    void submitPersistsJobAndOutboxEventAtomically() throws Exception {
        var res = mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(res).get("jobId").asText();

        mvc.perform(get("/api/v1/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobType").value("DATA_PROCESSING"))
                .andExpect(jsonPath("$.retryCount").value(0));

        Integer outboxRows = jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?::uuid and published_at is null",
                Integer.class, id);
        assertThat(outboxRows).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyReturnsSameJobAndCreatesOnlyOne() throws Exception {
        String first = submit(BODY, "it-key-1");
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Idempotency-Key", "it-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(mapper.readTree(first).get("jobId").asText()));

        Integer rows = jdbc.queryForObject("select count(*) from jobs where idempotency_key = 'it-key-1'", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentRequestIsConflict() throws Exception {
        submit(BODY, "it-key-2");
        String other = BODY.replace("[10,20,30,40]", "[1,2]");
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content(other)
                        .header("Idempotency-Key", "it-key-2"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadAndUnknownTypeAreBadRequests() throws Exception {
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobType\":\"DATA_PROCESSING\",\"payload\":{}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobType\":\"NOPE\",\"payload\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelPendingJobThenSecondCancelConflicts() throws Exception {
        JsonNode created = mapper.readTree(submit(BODY, null));
        String id = created.get("jobId").asText();

        mvc.perform(post("/api/v1/jobs/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/jobs/" + id + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void listSupportsFilterAndPagination() throws Exception {
        submit(BODY, null);
        mvc.perform(get("/api/v1/jobs").param("status", "PENDING").param("jobType", "DATA_PROCESSING")
                        .param("size", "1").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void unknownJobIs404AndAttemptsListIsEmptyForNewJob() throws Exception {
        mvc.perform(get("/api/v1/jobs/00000000-0000-0000-0000-000000000000")).andExpect(status().isNotFound());
        String id = mapper.readTree(submit(BODY, null)).get("jobId").asText();
        mvc.perform(get("/api/v1/jobs/" + id + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
