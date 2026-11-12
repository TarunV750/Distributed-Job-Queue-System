package com.jobqueue.common.model;

/** Supported job kinds. Each gets its own handler in the worker (Phase 2). */
public enum JobType {
    EMAIL,
    DATA_PROCESSING,
    REPORT_GENERATION,
    WEBHOOK,
    COMPUTATION
}
