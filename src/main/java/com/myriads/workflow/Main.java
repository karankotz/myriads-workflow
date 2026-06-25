package com.myriads.workflow;

import com.myriads.workflow.agent.Agent;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.Workflow;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowListener;
import com.myriads.workflow.core.WorkflowResult;
import com.myriads.workflow.pipeline.PipelineRegistry;
import com.myriads.workflow.pipeline.SequentialPipeline;
import com.myriads.workflow.web.DemoWorkflows;
import com.myriads.workflow.web.WorkflowServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

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

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equalsIgnoreCase("serve")) {
            serve(args);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("ai")) {
            runAi(args);
            return;
        }
        runDemo();
    }

    /**
     * Runs a Claude-backed workflow from the command line:
     * {@code ai [goal...]} runs the research crew, {@code ai orchestrate [goal...]}
     * runs the planner-driven orchestrator.
     */
    private static void runAi(String[] args) {
        boolean orchestrate = args.length > 1 && args[1].equalsIgnoreCase("orchestrate");
        String workflowName = orchestrate ? "ai-orchestrator" : "ai-research-crew";
        int goalStart = orchestrate ? 2 : 1;

        String goal = args.length > goalStart
                ? String.join(" ", Arrays.copyOfRange(args, goalStart, args.length))
                : "introduce the Myriads distributed workflow engine";

        Workflow workflow = DemoWorkflows.catalog().get(workflowName).orElseThrow();
        WorkflowContext context = new WorkflowContext("ai-cli").put("goal", goal);

        WorkflowResult result = workflow.run(context, new WorkflowListener() {
            @Override
            public void onStageStarted(String runId, String stageName) {
                log.info("→ {} thinking…", stageName);
            }

            @Override
            public void onStageCompleted(String runId, StageResult stage) {
                if (stage.isSuccess()) {
                    log.info("[{}]\n{}", stage.stageName(), stage.output());
                } else {
                    log.error("[{}] failed: {}", stage.stageName(),
                            stage.error() == null ? "?" : stage.error().getMessage());
                }
            }
        });

        log.info("AI workflow completed={}", result.completed());
    }

    /** Starts the web portal and blocks until the JVM is shut down. */
    private static void serve(String[] args) throws Exception {
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        WorkflowServer server = new WorkflowServer(port, DemoWorkflows.catalog());
        server.start();
        log.info("Open the portal at http://localhost:{}  (Ctrl+C to stop)", port);

        CountDownLatch shutdown = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            shutdown.countDown();
        }));
        shutdown.await();
    }

    /** Runs the inline demo workflow from the command line. */
    private static void runDemo() {
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
