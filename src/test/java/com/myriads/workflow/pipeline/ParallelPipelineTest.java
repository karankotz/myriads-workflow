package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParallelPipelineTest {

    private final ParallelPipeline pipeline = new ParallelPipeline();

    @Test
    void runsEveryStageAndReturnsResultsInDeclarationOrder() {
        Stage a = namedStage("a", ctx -> StageResult.success("a", 1));
        Stage b = namedStage("b", ctx -> StageResult.success("b", 2));
        Stage c = namedStage("c", ctx -> StageResult.success("c", 3));

        WorkflowResult result = pipeline.run(List.of(a, b, c), new WorkflowContext("p1"));

        assertTrue(result.completed());
        assertEquals(List.of("a", "b", "c"),
                result.stageResults().stream().map(StageResult::stageName).toList());
        assertEquals(List.of(1, 2, 3),
                result.stageResults().stream().map(StageResult::output).toList());
    }

    @Test
    void aFailingStageDoesNotPreventOthersFromRunning() {
        AtomicInteger ran = new AtomicInteger();
        Stage ok1 = namedStage("ok1", ctx -> { ran.incrementAndGet(); return StageResult.success("ok1", "x"); });
        Stage boom = namedStage("boom", ctx -> { ran.incrementAndGet(); throw new IllegalStateException("kaboom"); });
        Stage ok2 = namedStage("ok2", ctx -> { ran.incrementAndGet(); return StageResult.success("ok2", "y"); });

        WorkflowResult result = pipeline.run(List.of(ok1, boom, ok2), new WorkflowContext("p2"));

        assertEquals(3, ran.get(), "all stages should run despite one failing");
        assertFalse(result.completed(), "a failed stage marks the run incomplete");
        assertEquals(StageResult.Status.FAILED, result.stageResults().get(1).status());
    }

    private static Stage namedStage(String name, Stage delegate) {
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
}
