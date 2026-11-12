package com.jobqueue.api.exception;

import com.jobqueue.common.model.JobStatus;
import java.util.UUID;

public class JobStateConflictException extends RuntimeException {
    public JobStateConflictException(UUID id, JobStatus current, String action) {
        super("Cannot " + action + " job " + id + " while it is " + current);
    }
}
