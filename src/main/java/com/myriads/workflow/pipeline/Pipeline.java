package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowListener;
import com.myriads.workflow.core.WorkflowResult;

import java.util.List;

/**
 * A strategy for executing a list of {@link Stage}s.
 *
 * <p>This is the main extension point of the engine. Each pipeline encodes a
 * different execution semantic — sequential today, and parallel, conditional /
 * branching, or distributed (dispatching stages to remote workers) later. New
 * "ways of adding pipelines" are introduced by implementing this interface and
 * registering them in a {@link PipelineRegistry}.
 */
public interface Pipeline {

    /**
     * Runs the given stages against the context, reporting progress to a listener.
     *
     * @param stages   the stages to execute
     * @param context  shared run state
     * @param listener observer notified as stages start and complete
     * @return the aggregate result of the run
     */
    WorkflowResult run(List<Stage> stages, WorkflowContext context, WorkflowListener listener);

    /** Convenience overload that runs without observing progress. */
    default WorkflowResult run(List<Stage> stages, WorkflowContext context) {
        return run(stages, context, WorkflowListener.NOOP);
    }

    /** Identifier used to look this pipeline up in a {@link PipelineRegistry}. */
    String id();
}
