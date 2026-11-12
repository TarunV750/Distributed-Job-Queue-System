package com.jobqueue.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobqueue.api.exception.InvalidPayloadException;
import com.jobqueue.common.model.JobType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cheap, synchronous validation of the payload shape per job type, so obviously bad jobs are rejected
 * with a 400 instead of failing later in a worker. This is shape validation only: the webhook SSRF
 * check (private/internal addresses) must happen at execution time, after DNS resolution (Phase 2).
 */
@Component
public class JobPayloadValidator {

    private static final int MAX_NUMBERS = 100_000;
    private static final long MAX_ITERATIONS = 1_000_000_000L;

    private final ObjectMapper mapper;
    private final int maxPayloadBytes;

    public JobPayloadValidator(ObjectMapper mapper, @Value("${jobs.max-payload-bytes:262144}") int maxPayloadBytes) {
        this.mapper = mapper;
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public void validate(JobType type, Map<String, Object> payload) {
        List<String> violations = new ArrayList<>();

        try {
            if (mapper.writeValueAsBytes(payload).length > maxPayloadBytes) {
                throw new InvalidPayloadException(List.of("payload exceeds " + maxPayloadBytes + " bytes"));
            }
        } catch (JsonProcessingException e) {
            throw new InvalidPayloadException(List.of("payload is not valid JSON"));
        }

        switch (type) {
            case EMAIL -> {
                requireText(payload, "recipient", violations);
                requireText(payload, "subject", violations);
                requireText(payload, "message", violations);
                if (payload.get("recipient") instanceof String r && !r.isBlank() && !r.contains("@")) {
                    violations.add("recipient must be an email address");
                }
            }
            case DATA_PROCESSING -> validateNumbers(payload, violations);
            case REPORT_GENERATION -> requireText(payload, "reportType", violations);
            case WEBHOOK -> validateWebhook(payload, violations);
            case COMPUTATION -> validateIterations(payload, violations);
        }

        if (!violations.isEmpty()) {
            throw new InvalidPayloadException(violations);
        }
    }

    private void requireText(Map<String, Object> payload, String field, List<String> violations) {
        if (!(payload.get(field) instanceof String s) || s.isBlank()) {
            violations.add(field + " is required and must be a non-blank string");
        }
    }

    private void validateNumbers(Map<String, Object> payload, List<String> violations) {
        if (!(payload.get("numbers") instanceof List<?> numbers) || numbers.isEmpty()) {
            violations.add("numbers is required and must be a non-empty array");
            return;
        }
        if (numbers.size() > MAX_NUMBERS) {
            violations.add("numbers must contain at most " + MAX_NUMBERS + " elements");
        }
        if (!numbers.stream().allMatch(n -> n instanceof Number)) {
            violations.add("numbers must contain only numeric values");
        }
    }

    private void validateWebhook(Map<String, Object> payload, List<String> violations) {
        if (!(payload.get("url") instanceof String url) || url.isBlank()) {
            violations.add("url is required and must be a non-blank string");
            return;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                violations.add("url must use http or https");
            }
            if (uri.getHost() == null) {
                violations.add("url must include a host");
            }
        } catch (IllegalArgumentException e) {
            violations.add("url is not a valid URI");
        }
    }

    private void validateIterations(Map<String, Object> payload, List<String> violations) {
        if (!(payload.get("iterations") instanceof Number n) || n.longValue() < 1 || n.longValue() > MAX_ITERATIONS) {
            violations.add("iterations is required and must be between 1 and " + MAX_ITERATIONS);
        }
    }
}
