package com.myriads.workflow.web;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.Workflow;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.pipeline.SequentialPipeline;

import java.util.function.Function;

/**
 * Example workflows used to populate the portal out of the box.
 *
 * <p>Each stage sleeps briefly so the live, stage-by-stage animation in the UI
 * is easy to watch. The three workflows are chosen to exercise every outcome a
 * stage can produce — success, an early {@code HALT}, and a {@code FAILED}
 * stage that aborts the run — so the portal visibly renders all of them.
 */
public final class DemoWorkflows {

    /** Roughly how long each demo stage "works" for, to make the UI animate. */
    private static final long STEP_MILLIS = 600;

    private DemoWorkflows() {
    }

    /** Builds a catalog preloaded with the example workflows. */
    public static WorkflowCatalog catalog() {
        SequentialPipeline pipeline = new SequentialPipeline();

        Workflow happyPath = Workflow.named("research-and-ship")
                .using(pipeline)
                .stage(step("intake", ctx -> "goal=" + ctx.get("goal", String.class).orElse("(none)")))
                .stage(step("plan", ctx -> "plan for " + ctx.get("intake").orElse("?")))
                .stage(step("research", ctx -> "gathered 3 sources"))
                .stage(step("execute", ctx -> "applied " + ctx.get("plan").orElse("?")))
                .stage(step("summarize", ctx -> "done: " + ctx.get("execute").orElse("?")))
                .build();

        Workflow haltsEarly = Workflow.named("guarded-run")
                .using(pipeline)
                .stage(step("intake", ctx -> "received request"))
                .stage(haltStep("approval-gate", "halted: awaiting human approval"))
                .stage(step("execute", ctx -> "this stage is skipped after the halt"))
                .build();

        Workflow failsMidway = Workflow.named("flaky-run")
                .using(pipeline)
                .stage(step("intake", ctx -> "received request"))
                .stage(failStep("call-external-api", "upstream service returned 503"))
                .stage(step("persist", ctx -> "this stage is skipped after the failure"))
                .build();

        return new WorkflowCatalog()
                .register(happyPath)
                .register(haltsEarly)
                .register(failsMidway);
    }

    /** A stage that "works" for {@link #STEP_MILLIS} then succeeds with the given output. */
    private static Stage step(String name, Function<WorkflowContext, Object> body) {
        return named(name, ctx -> {
            pause();
            return StageResult.success(name, body.apply(ctx));
        });
    }

    /** A stage that halts the run cleanly. */
    private static Stage haltStep(String name, String message) {
        return named(name, ctx -> {
            pause();
            return StageResult.halt(name, message);
        });
    }

    /** A stage that fails, aborting the run. */
    private static Stage failStep(String name, String message) {
        return named(name, ctx -> {
            pause();
            throw new IllegalStateException(message);
        });
    }

    private static Stage named(String name, Stage delegate) {
        return new Stage() {
            @Override
            public StageResult execute(WorkflowContext context) throws Exception {
                return delegate.execute(context);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    private static void pause() {
        try {
            Thread.sleep(STEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
