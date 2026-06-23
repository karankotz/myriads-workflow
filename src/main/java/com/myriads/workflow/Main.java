package com.myriads.workflow;

import com.myriads.workflow.agent.Agent;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.Workflow;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowResult;
import com.myriads.workflow.pipeline.PipelineRegistry;
import com.myriads.workflow.pipeline.SequentialPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point demonstrating the starter engine with a tiny two-agent workflow.
 *
 * <p>This is a placeholder showing the intended shape: define agents, wire them
 * into a {@link Workflow} backed by a {@link com.myriads.workflow.pipeline.Pipeline}
 * from the {@link PipelineRegistry}, and run it. Real pipelines and agents will
 * be added on top of these abstractions.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        PipelineRegistry pipelines = PipelineRegistry.withDefaults();

        // An agent that seeds some work into the shared context.
        Agent planner = new Agent() {
            @Override
            public String name() {
                return "planner";
            }

            @Override
            public Object act(WorkflowContext context) {
                String goal = context.get("goal", String.class).orElse("say hello");
                log.info("Planning for goal: {}", goal);
                return "plan-for:" + goal;
            }
        };

        // An agent that consumes the previous agent's output.
        Agent executor = new Agent() {
            @Override
            public String name() {
                return "executor";
            }

            @Override
            public Object act(WorkflowContext context) {
                Object plan = context.get("planner").orElse("<no plan>");
                log.info("Executing: {}", plan);
                return "done:" + plan;
            }
        };

        Workflow workflow = Workflow.named("demo")
                .using(pipelines.get(SequentialPipeline.ID))
                .stage(planner.asStage())
                .stage(executor.asStage())
                .build();

        WorkflowContext context = new WorkflowContext("demo-run-1").put("goal", "ship the starter");
        WorkflowResult result = workflow.run(context);

        log.info("Workflow '{}' completed={}", workflow.name(), result.completed());
        StageResult last = result.lastStage();
        if (last != null) {
            log.info("Final output: {}", last.output());
        }
    }

    private Main() {
    }
}
