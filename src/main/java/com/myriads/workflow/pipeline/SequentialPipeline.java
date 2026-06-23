package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowListener;
import com.myriads.workflow.core.WorkflowResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Default pipeline: runs stages one after another in declaration order.
 *
 * <p>A stage's {@link StageResult.Status#FAILED} aborts the run; a
 * {@link StageResult.Status#HALT} stops it cleanly. Each stage's output is also
 * published into the context under its stage name so downstream stages can read
 * it. This is the simplest of the pluggable execution strategies — parallel and
 * distributed pipelines will implement {@link Pipeline} the same way.
 */
public final class SequentialPipeline implements Pipeline {

    public static final String ID = "sequential";

    private static final Logger log = LoggerFactory.getLogger(SequentialPipeline.class);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public WorkflowResult run(List<Stage> stages, WorkflowContext context, WorkflowListener listener) {
        List<StageResult> results = new ArrayList<>(stages.size());

        for (Stage stage : stages) {
            listener.onStageStarted(context.runId(), stage.name());
            StageResult result = runStage(stage, context);
            results.add(result);
            listener.onStageCompleted(context.runId(), result);

            if (result.output() != null) {
                context.put(stage.name(), result.output());
            }

            switch (result.status()) {
                case FAILED -> {
                    log.error("Stage '{}' failed; aborting run {}", stage.name(), context.runId(),
                            result.error());
                    return new WorkflowResult(context.runId(), false, results);
                }
                case HALT -> {
                    log.info("Stage '{}' halted run {}", stage.name(), context.runId());
                    return new WorkflowResult(context.runId(), true, results);
                }
                case SUCCESS -> log.debug("Stage '{}' completed", stage.name());
            }
        }

        return new WorkflowResult(context.runId(), true, results);
    }

    private StageResult runStage(Stage stage, WorkflowContext context) {
        try {
            log.info("Running stage '{}' (run {})", stage.name(), context.runId());
            return stage.execute(context);
        } catch (Exception e) {
            return StageResult.failed(stage.name(), e);
        }
    }
}
