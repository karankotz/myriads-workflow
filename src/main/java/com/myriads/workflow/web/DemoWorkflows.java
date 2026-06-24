package com.myriads.workflow.web;

import com.myriads.workflow.agent.ClaudeAgent;
import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.Workflow;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.pipeline.ParallelPipeline;
import com.myriads.workflow.pipeline.SequentialPipeline;

import java.util.function.Function;

/**
 * Example workflows used to populate the portal out of the box.
 *
 * <p>Each stage sleeps briefly so the live, stage-by-stage animation in the UI
 * is easy to watch. Together the workflows map to real use cases and exercise
 * every outcome a stage can produce:
 * <ul>
 *   <li>{@code research-and-ship} — an agentic crew, all stages succeed.</li>
 *   <li>{@code kyc-onboarding} — customer onboarding that {@code HALT}s at a
 *       human approval gate.</li>
 *   <li>{@code ci-cd-deploy} — a release pipeline that {@code FAILED}s on a
 *       smoke test and skips promotion to prod.</li>
 *   <li>{@code security-scan} — independent scans fanned out concurrently with
 *       the {@link ParallelPipeline}.</li>
 * </ul>
 */
public final class DemoWorkflows {

    /** Default time each demo stage "works" for, to make the UI animate. */
    private static final long STEP_MILLIS = 600;

    private DemoWorkflows() {
    }

    /** Builds a catalog preloaded with the example workflows. */
    public static WorkflowCatalog catalog() {
        SequentialPipeline sequential = new SequentialPipeline();
        ParallelPipeline parallel = new ParallelPipeline();

        Workflow researchAndShip = Workflow.named("research-and-ship")
                .using(sequential)
                .stage(step("intake", ctx -> "goal=" + ctx.get("goal", String.class).orElse("(none)")))
                .stage(step("plan", ctx -> "plan for " + ctx.get("intake").orElse("?")))
                .stage(step("research", ctx -> "gathered 3 sources"))
                .stage(step("execute", ctx -> "applied " + ctx.get("plan").orElse("?")))
                .stage(step("summarize", ctx -> "done: " + ctx.get("execute").orElse("?")))
                .build();

        // Customer onboarding / KYC: stops for a human at the manual-review gate.
        Workflow kycOnboarding = Workflow.named("kyc-onboarding")
                .using(sequential)
                .stage(step("collect-documents", ctx -> "passport + proof-of-address received"))
                .stage(step("verify-identity", ctx -> "identity matched (98%)"))
                .stage(step("sanctions-screening", ctx -> "no sanctions hits"))
                .stage(step("risk-scoring", ctx -> "risk=MEDIUM"))
                .stage(haltStep("manual-review", "halted: MEDIUM risk requires a compliance officer"))
                .stage(step("provision-account", ctx -> "skipped until approved"))
                .stage(step("send-welcome-email", ctx -> "skipped until approved"))
                .build();

        // CI/CD release: smoke test fails on staging, so prod promotion is skipped.
        Workflow ciCdDeploy = Workflow.named("ci-cd-deploy")
                .using(sequential)
                .stage(step("checkout", ctx -> "main @ a1b2c3d"))
                .stage(step("build", ctx -> "artifact built"))
                .stage(step("unit-tests", ctx -> "412 passed"))
                .stage(step("package", ctx -> "image myriads:a1b2c3d"))
                .stage(step("deploy-staging", ctx -> "rolled out to staging"))
                .stage(failStep("smoke-test", "staging health check failed: /readyz 503"))
                .stage(step("promote-prod", ctx -> "skipped after smoke-test failure"))
                .build();

        // Independent scans that run at the same time — staggered to show concurrency.
        Workflow securityScan = Workflow.named("security-scan")
                .using(parallel)
                .stage(step("dependency-audit", 700, ctx -> "0 critical CVEs"))
                .stage(step("secret-detection", 1100, ctx -> "no secrets found"))
                .stage(step("license-check", 500, ctx -> "all licenses compatible"))
                .stage(step("sast-analysis", 1400, ctx -> "2 low-severity findings"))
                .build();

        return new WorkflowCatalog()
                .register(researchAndShip)
                .register(kycOnboarding)
                .register(ciCdDeploy)
                .register(securityScan)
                .register(aiResearchCrew(sequential));
    }

    /**
     * A crew of real Claude-backed agents chained together: each agent reads the
     * previous one's output from the context and feeds the next. Requires
     * {@code ANTHROPIC_API_KEY}; without it the first stage fails with a clear
     * message (visible in the portal).
     */
    private static Workflow aiResearchCrew(SequentialPipeline pipeline) {
        return Workflow.named("ai-research-crew")
                .using(pipeline)
                .stage(new ClaudeAgent("planner",
                        "You are a planning agent. Given a goal, outline a focused 3-step plan. "
                                + "Respond in under 70 words, as a numbered list.",
                        ctx -> "Goal: " + ctx.get("goal", String.class)
                                .orElse("introduce the Myriads distributed workflow engine")).asStage())
                .stage(new ClaudeAgent("researcher",
                        "You are a research agent. Given a plan, list the key facts, risks, or "
                                + "considerations for each step. Respond in under 90 words.",
                        ctx -> "Plan:\n" + ctx.get("planner").orElse("")).asStage())
                .stage(new ClaudeAgent("writer",
                        "You are a writing agent. Given research notes, write a concise, polished "
                                + "summary a stakeholder could read. Respond in under 100 words.",
                        ctx -> "Research notes:\n" + ctx.get("researcher").orElse("")).asStage())
                .build();
    }

    /** A stage that "works" for {@link #STEP_MILLIS} then succeeds with the given output. */
    private static Stage step(String name, Function<WorkflowContext, Object> body) {
        return step(name, STEP_MILLIS, body);
    }

    /** A stage that "works" for {@code delayMillis} then succeeds with the given output. */
    private static Stage step(String name, long delayMillis, Function<WorkflowContext, Object> body) {
        return named(name, ctx -> {
            pause(delayMillis);
            return StageResult.success(name, body.apply(ctx));
        });
    }

    /** A stage that halts the run cleanly. */
    private static Stage haltStep(String name, String message) {
        return named(name, ctx -> {
            pause(STEP_MILLIS);
            return StageResult.halt(name, message);
        });
    }

    /** A stage that fails, aborting the run. */
    private static Stage failStep(String name, String message) {
        return named(name, ctx -> {
            pause(STEP_MILLIS);
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

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
