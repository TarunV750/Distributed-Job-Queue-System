package com.jobqueue.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jobqueue.common.model.JobType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Stable SHA-256 of the semantic request. Detects a client reusing an Idempotency-Key for a
 * different request, which must be rejected rather than silently returning an unrelated job.
 */
@Component
public class RequestFingerprint {

    private final ObjectMapper canonical;

    public RequestFingerprint(ObjectMapper mapper) {
        this.canonical = mapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String of(JobType type, Map<String, Object> payload, int maxRetries, int priority) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("jobType", type.name());
        doc.put("payload", payload);
        doc.put("maxRetries", maxRetries);
        doc.put("priority", priority);
        try {
            byte[] json = canonical.writeValueAsBytes(doc);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint request", e);
        }
    }
}
