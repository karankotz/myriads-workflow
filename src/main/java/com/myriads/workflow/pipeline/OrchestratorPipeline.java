package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowListener;
import com.myriads.workflow.core.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planner-driven pipeline: an agent decides at run time which agents to run.
 *
 * <p>By convention the <b>first stage is the planner</b> and the remaining
 * stages form a named <b>agent pool</b>. The planner runs first; its text output
 * is read as a plan — the pool agents whose names appear in it are then executed
 * in the order they appear. Pool agents the plan doesn't mention never run (the
 * portal renders them as skipped), so the planner shapes the trajectory.
 *
 * <p>This is the agentic counterpart to {@link SequentialPipeline}: the control
 * flow is decided by the model, not fixed in code. If the planner names nothing
 * recognizable, the pipeline falls back to running the whole pool in declaration
 * order. As with the sequential pipeline, a {@code FAILED} stage aborts the run
 * and a {@code HALT} stops it cleanly; each stage's output is published to the
 * context under its name.
 *
 * <p>Pool agent names should be distinct and not substrings of one another
 * (e.g. avoid {@code research} alongside {@code researcher}), since the plan is
 * matched by name occurrence.
 */
public final class OrchestratorPipeline implements Pipeline {

    public static final String ID = "orchestrator";

    private static final Logger log = LoggerFactory.getLogger(OrchestratorPipeline.class);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public WorkflowResult run(List<Stage> stages, WorkflowContext context, WorkflowListener listener) {
        List<StageResult> results = new ArrayList<>();
        if (stages.isEmpty()) {
            return new WorkflowResult(context.runId(), true, results);
        }

        // First stage is the planner; the rest are the selectable agent pool.
        Stage planner = stages.get(0);
        Map<String, Stage> pool = new LinkedHashMap<>();
        for (int i = 1; i < stages.size(); i++) {
            pool.put(stages.get(i).name(), stages.get(i));
        }

        StageResult plan = runStage(planner, context, listener);
        results.add(plan);
        if (plan.status() == StageResult.Status.FAILED) {
            log.error("Planner '{}' failed; aborting run {}", planner.name(), context.runId(), plan.error());
            return new WorkflowResult(context.runId(), false, results);
        }

        List<String> chosen = parsePlan(plan.output(), pool.keySet());
        if (chosen.isEmpty()) {
            chosen = new ArrayList<>(pool.keySet());
            log.info("Planner produced no recognizable plan; running the whole pool {}", chosen);
        }
        log.info("Orchestrator plan for run {}: {}", context.runId(), chosen);

        for (String name : chosen) {
            Stage stage = pool.get(name);
            StageResult result = runStage(stage, context, listener);
            results.add(result);
            switch (result.status()) {
                case FAILED -> {
                    log.error("Stage '{}' failed; aborting run {}", name, context.runId(), result.error());
                    return new WorkflowResult(context.runId(), false, results);
                }
                case HALT -> {
                    log.info("Stage '{}' halted run {}", name, context.runId());
                    return new WorkflowResult(context.runId(), true, results);
                }
                case SUCCESS -> log.debug("Stage '{}' completed", name);
            }
        }

        return new WorkflowResult(context.runId(), true, results);
    }

    /** Returns the pool names that appear in the plan text, ordered by first occurrence. */
    private static List<String> parsePlan(Object planOutput, Collection<String> available) {
        String text = planOutput == null ? "" : planOutput.toString().toLowerCase();
        return available.stream()
                .map(name -> Map.entry(name, text.indexOf(name.toLowerCase())))
                .filter(entry -> entry.getValue() >= 0)
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .toList();
    }

    private StageResult runStage(Stage stage, WorkflowContext context, WorkflowListener listener) {
        listener.onStageStarted(context.runId(), stage.name());
        StageResult result;
        try {
            log.info("Running stage '{}' (run {})", stage.name(), context.runId());
            result = stage.execute(context);
        } catch (Exception e) {
            result = StageResult.failed(stage.name(), e);
        }
        if (result.output() != null) {
            context.put(stage.name(), result.output());
        }
        listener.onStageCompleted(context.runId(), result);
        return result;
    }
}
