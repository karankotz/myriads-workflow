package com.myriads.workflow.core;

import java.util.List;

/**
 * Observer of a workflow run, invoked as stages execute.
 *
 * <p>This is what lets the engine be watched live — the web portal implements it
 * to stream per-stage progress to the browser, but it is equally useful for
 * metrics, tracing, or audit logs. All methods default to no-ops so observers
 * only override the events they care about; {@link #NOOP} is the do-nothing
 * instance used when no observer is supplied.
 *
 * <p>Callbacks are invoked synchronously on the thread running the workflow, in
 * execution order, so a slow listener slows the run.
 */
public interface WorkflowListener {

    /** Fired once before any stage runs. */
    default void onWorkflowStarted(String runId, String workflowName, List<String> stageNames) {
    }

    /** Fired immediately before a stage executes. */
    default void onStageStarted(String runId, String stageName) {
    }

    /** Fired immediately after a stage produces its result. */
    default void onStageCompleted(String runId, StageResult result) {
    }

    /** Fired once after the run finishes (whether it completed, halted, or failed). */
    default void onWorkflowCompleted(WorkflowResult result) {
    }

    /** A listener that ignores every event. */
    WorkflowListener NOOP = new WorkflowListener() {
    };
}
