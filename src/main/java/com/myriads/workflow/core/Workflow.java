package com.myriads.workflow.core;

import com.myriads.workflow.pipeline.Pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named sequence of {@link Stage}s executed by a chosen {@link Pipeline}.
 *
 * <p>Build one with the fluent {@link Builder}, then {@link #run()} it. The
 * workflow itself is execution-agnostic — swapping the pipeline (sequential,
 * parallel, distributed) changes how the same stages are run.
 */
public final class Workflow {

    private final String name;
    private final List<Stage> stages;
    private final Pipeline pipeline;

    private Workflow(String name, List<Stage> stages, Pipeline pipeline) {
        this.name = name;
        this.stages = List.copyOf(stages);
        this.pipeline = pipeline;
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    /** The ordered names of this workflow's stages, for display before a run. */
    public List<String> stageNames() {
        return stages.stream().map(Stage::name).toList();
    }

    /** Runs the workflow with a freshly generated run id. */
    public WorkflowResult run() {
        return run(new WorkflowContext(UUID.randomUUID().toString()));
    }

    /** Runs the workflow against a caller-supplied context. */
    public WorkflowResult run(WorkflowContext context) {
        return run(context, WorkflowListener.NOOP);
    }

    /** Runs the workflow against a context while reporting progress to a listener. */
    public WorkflowResult run(WorkflowContext context, WorkflowListener listener) {
        listener.onWorkflowStarted(context.runId(), name, stageNames());
        WorkflowResult result = pipeline.run(stages, context, listener);
        listener.onWorkflowCompleted(result);
        return result;
    }

    public static final class Builder {
        private final String name;
        private final List<Stage> stages = new ArrayList<>();
        private Pipeline pipeline;

        private Builder(String name) {
            this.name = name;
        }

        public Builder stage(Stage stage) {
            stages.add(stage);
            return this;
        }

        public Builder using(Pipeline pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Workflow build() {
            if (pipeline == null) {
                throw new IllegalStateException("A pipeline must be set via using(...) before build()");
            }
            return new Workflow(name, stages, pipeline);
        }
    }
}
