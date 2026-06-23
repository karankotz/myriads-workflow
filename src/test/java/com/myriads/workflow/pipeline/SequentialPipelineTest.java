package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequentialPipelineTest {

    private final SequentialPipeline pipeline = new SequentialPipeline();

    @Test
    void runsStagesInOrderAndChainsOutputThroughContext() {
        Stage first = namedStage("first", ctx -> StageResult.success("first", "a"));
        Stage second = namedStage("second", ctx ->
                StageResult.success("second", ctx.get("first").orElse("") + "b"));

        WorkflowResult result = pipeline.run(List.of(first, second), new WorkflowContext("t1"));

        assertTrue(result.completed());
        assertEquals(2, result.stageResults().size());
        assertEquals("ab", result.lastStage().output());
    }

    @Test
    void abortsRunWhenAStageFails() {
        Stage boom = namedStage("boom", ctx -> {
            throw new IllegalStateException("kaboom");
        });
        Stage never = namedStage("never", ctx -> StageResult.success("never", "should not run"));

        WorkflowResult result = pipeline.run(List.of(boom, never), new WorkflowContext("t2"));

        assertFalse(result.completed());
        assertEquals(1, result.stageResults().size());
        assertEquals(StageResult.Status.FAILED, result.lastStage().status());
    }

    @Test
    void haltStopsRunCleanly() {
        Stage stop = namedStage("stop", ctx -> StageResult.halt("stop", "enough"));
        Stage never = namedStage("never", ctx -> StageResult.success("never", "should not run"));

        WorkflowResult result = pipeline.run(List.of(stop, never), new WorkflowContext("t3"));

        assertTrue(result.completed());
        assertEquals(1, result.stageResults().size());
        assertEquals(StageResult.Status.HALT, result.lastStage().status());
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
