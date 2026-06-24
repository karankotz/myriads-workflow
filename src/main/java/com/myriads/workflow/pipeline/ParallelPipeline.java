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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fan-out pipeline: runs every stage concurrently and waits for all of them.
 *
 * <p>Each stage gets its own virtual thread, so this suits independent work —
 * parallel scans, multi-region deploys, or a crew of agents that don't depend
 * on each other's output. Because stages run at the same time, ordering between
 * them is not defined: a stage should not rely on another stage's value in the
 * shared {@link WorkflowContext} (use a {@link SequentialPipeline} when it must).
 *
 * <p>The run is reported {@code completed = false} if any stage failed; unlike
 * the sequential pipeline a {@code HALT} cannot cancel siblings that are already
 * running, so it simply ends that one stage. Results are returned in stage
 * declaration order regardless of which finished first.
 */
public final class ParallelPipeline implements Pipeline {

    public static final String ID = "parallel";

    private static final Logger log = LoggerFactory.getLogger(ParallelPipeline.class);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public WorkflowResult run(List<Stage> stages, WorkflowContext context, WorkflowListener listener) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<StageResult>> futures = new ArrayList<>(stages.size());
            for (Stage stage : stages) {
                futures.add(pool.submit(() -> runStage(stage, context, listener)));
            }

            List<StageResult> results = new ArrayList<>(stages.size());
            boolean completed = true;
            for (Future<StageResult> future : futures) {
                StageResult result = future.get();
                results.add(result);
                if (result.status() == StageResult.Status.FAILED) {
                    completed = false;
                }
            }
            return new WorkflowResult(context.runId(), completed, results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Parallel run interrupted for " + context.runId(), e);
        } catch (ExecutionException e) {
            // runStage already converts stage failures to results, so this is unexpected.
            throw new IllegalStateException("Unexpected parallel run failure for " + context.runId(), e.getCause());
        }
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
