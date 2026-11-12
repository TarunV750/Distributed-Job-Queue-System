package com.jobqueue.api.exception;

import java.util.List;

public class InvalidPayloadException extends RuntimeException {
    private final List<String> violations;

    public InvalidPayloadException(List<String> violations) {
        super("Invalid job payload");
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
