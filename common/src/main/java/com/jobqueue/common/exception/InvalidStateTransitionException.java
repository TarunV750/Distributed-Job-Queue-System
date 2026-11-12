package com.jobqueue.common.exception;

import com.jobqueue.common.model.JobStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(JobStatus from, JobStatus to) {
        super("Invalid job state transition: " + from + " -> " + to);
    }
}
