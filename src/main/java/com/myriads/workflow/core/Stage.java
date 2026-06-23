package com.myriads.workflow.core;

/**
 * A single unit of work in a workflow.
 *
 * <p>A stage is the smallest schedulable piece of a pipeline. In an agentic
 * setting a stage typically delegates to an {@link com.myriads.workflow.agent.Agent},
 * but it can be any deterministic step (validation, routing, aggregation, ...).
 *
 * <p>Implementations must be safe to invoke from a pipeline that may run stages
 * concurrently or on remote workers once execution becomes distributed.
 */
@FunctionalInterface
public interface Stage {

    /**
     * Executes the stage against the shared run context.
     *
     * @param context shared state for this workflow run
     * @return the outcome of this stage
     * @throws Exception any failure; the pipeline decides how to handle it
     */
    StageResult execute(WorkflowContext context) throws Exception;

    /** Human-readable name, used in logs and tracing. Defaults to the class name. */
    default String name() {
        return getClass().getSimpleName();
    }
}
