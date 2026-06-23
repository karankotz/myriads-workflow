package com.myriads.workflow.core;

import java.util.List;

/**
 * Aggregate outcome of a whole workflow run.
 *
 * @param runId        the run this result belongs to
 * @param completed    {@code true} if every stage finished without a failure
 * @param stageResults the per-stage results, in execution order
 */
public record WorkflowResult(String runId, boolean completed, List<StageResult> stageResults) {

    public WorkflowResult {
        stageResults = List.copyOf(stageResults);
    }

    /** The last stage that ran, or {@code null} if the workflow had no stages. */
    public StageResult lastStage() {
        return stageResults.isEmpty() ? null : stageResults.get(stageResults.size() - 1);
    }
}
