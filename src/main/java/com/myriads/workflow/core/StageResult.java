package com.myriads.workflow.core;

/**
 * Outcome of a single {@link Stage} execution.
 *
 * @param stageName the stage that produced this result
 * @param status    whether the stage succeeded, and how the pipeline should proceed
 * @param output    optional payload produced by the stage (may be {@code null})
 * @param error     populated when {@code status} is {@link Status#FAILED}
 */
public record StageResult(String stageName, Status status, Object output, Throwable error) {

    public enum Status {
        /** Stage completed; continue with the next stage. */
        SUCCESS,
        /** Stage failed; the pipeline decides whether to abort the run. */
        FAILED,
        /** Stage asks the pipeline to stop the run early without error. */
        HALT
    }

    public static StageResult success(String stageName, Object output) {
        return new StageResult(stageName, Status.SUCCESS, output, null);
    }

    public static StageResult failed(String stageName, Throwable error) {
        return new StageResult(stageName, Status.FAILED, null, error);
    }

    public static StageResult halt(String stageName, Object output) {
        return new StageResult(stageName, Status.HALT, output, null);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
