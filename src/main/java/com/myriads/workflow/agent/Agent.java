package com.myriads.workflow.agent;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;

/**
 * An autonomous worker that performs one task within a workflow.
 *
 * <p>An {@code Agent} is the agentic unit of the system: given the shared run
 * context it decides what to do and returns a payload. It adapts to the
 * pipeline via {@link #asStage()}, so any agent can be dropped into a workflow
 * as a {@link Stage}. Concrete agents (LLM-backed, tool-using, remote) will
 * implement {@link #act(WorkflowContext)}.
 */
public interface Agent {

    /** Stable name for this agent, used in logs and stage output keys. */
    String name();

    /**
     * Performs this agent's task.
     *
     * @param context shared run state to read inputs from and write outputs to
     * @return this agent's output payload (may be {@code null})
     * @throws Exception any failure; surfaced to the pipeline as a failed stage
     */
    Object act(WorkflowContext context) throws Exception;

    /** Adapts this agent into a {@link Stage} so it can be placed in a workflow. */
    default Stage asStage() {
        return new Stage() {
            @Override
            public StageResult execute(WorkflowContext context) throws Exception {
                return StageResult.success(Agent.this.name(), Agent.this.act(context));
            }

            @Override
            public String name() {
                return Agent.this.name();
            }
        };
    }
}
