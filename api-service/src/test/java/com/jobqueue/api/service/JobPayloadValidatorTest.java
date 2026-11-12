package com.jobqueue.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.exception.InvalidPayloadException;
import com.jobqueue.common.model.JobType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobPayloadValidatorTest {

    private final JobPayloadValidator validator = new JobPayloadValidator(new ObjectMapper(), 1024);

    @Test
    void acceptsValidPayloadForEveryType() {
        assertThatCode(() -> {
            validator.validate(JobType.EMAIL, Map.of("recipient", "a@b.com", "subject", "s", "message", "m"));
            validator.validate(JobType.DATA_PROCESSING, Map.of("numbers", List.of(1, 2.5, 3)));
            validator.validate(JobType.REPORT_GENERATION, Map.of("reportType", "sales"));
            validator.validate(JobType.WEBHOOK, Map.of("url", "https://example.com/hook"));
            validator.validate(JobType.COMPUTATION, Map.of("iterations", 1000));
        }).doesNotThrowAnyException();
    }

    @Test
    void emailRequiresAllFieldsAndAnAtSign() {
        assertThatThrownBy(() -> validator.validate(JobType.EMAIL, Map.of("recipient", "nope")))
                .isInstanceOfSatisfying(InvalidPayloadException.class, e ->
                        assertThat(e.getViolations()).hasSize(3)); // subject, message, recipient format
    }

    @Test
    void dataProcessingRejectsEmptyOrNonNumericArrays() {
        assertThatThrownBy(() -> validator.validate(JobType.DATA_PROCESSING, Map.of("numbers", List.of())))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> validator.validate(JobType.DATA_PROCESSING, Map.of("numbers", List.of(1, "x"))))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> validator.validate(JobType.DATA_PROCESSING, Map.of()))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void webhookRequiresHttpOrHttpsUrl() {
        assertThatThrownBy(() -> validator.validate(JobType.WEBHOOK, Map.of("url", "ftp://example.com")))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> validator.validate(JobType.WEBHOOK, Map.of("url", "not a url")))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void computationBoundsIterations() {
        assertThatThrownBy(() -> validator.validate(JobType.COMPUTATION, Map.of("iterations", 0)))
                .isInstanceOf(InvalidPayloadException.class);
        assertThatThrownBy(() -> validator.validate(JobType.COMPUTATION, Map.of("iterations", 5_000_000_000L)))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void rejectsOversizedPayload() {
        Map<String, Object> big = Map.of("reportType", "x".repeat(2000));
        assertThatThrownBy(() -> validator.validate(JobType.REPORT_GENERATION, big))
                .isInstanceOfSatisfying(InvalidPayloadException.class, e ->
                        assertThat(e.getViolations().get(0)).contains("exceeds"));
    }
}
