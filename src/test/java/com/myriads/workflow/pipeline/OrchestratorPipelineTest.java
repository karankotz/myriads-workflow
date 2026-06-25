package com.myriads.workflow.pipeline;

import com.myriads.workflow.core.Stage;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrchestratorPipelineTest {

    private final OrchestratorPipeline pipeline = new OrchestratorPipeline();

    @Test
    void runsOnlyThePoolAgentsThePlannerNamesInPlanOrder() {
        // Planner picks coder then reviewer; researcher is left out.
        Stage planner = namedStage("planner", ctx -> StageResult.success("planner", "run coder then reviewer"));
        Stage researcher = namedStage("researcher", ctx -> StageResult.success("researcher", "r"));
        Stage coder = namedStage("coder", ctx -> StageResult.success("coder", "c"));
        Stage reviewer = namedStage("reviewer", ctx -> StageResult.success("reviewer", "v"));

        WorkflowResult result = pipeline.run(
                List.of(planner, researcher, coder, reviewer), new WorkflowContext("o1"));

        assertTrue(result.completed());
        assertEquals(List.of("planner", "coder", "reviewer"),
                result.stageResults().stream().map(StageResult::stageName).toList());
    }

    @Test
    void fallsBackToRunningTheWholePoolWhenPlanIsUnrecognized() {
        Stage planner = namedStage("planner", ctx -> StageResult.success("planner", "(no idea)"));
        Stage a = namedStage("alpha", ctx -> StageResult.success("alpha", "a"));
        Stage b = namedStage("bravo", ctx -> StageResult.success("bravo", "b"));

        WorkflowResult result = pipeline.run(List.of(planner, a, b), new WorkflowContext("o2"));

        assertTrue(result.completed());
        assertEquals(List.of("planner", "alpha", "bravo"),
                result.stageResults().stream().map(StageResult::stageName).toList());
    }

    @Test
    void aFailingPlannerAbortsBeforeAnyPoolAgentRuns() {
        Stage planner = namedStage("planner", ctx -> { throw new IllegalStateException("boom"); });
        Stage worker = namedStage("worker", ctx -> StageResult.success("worker", "w"));

        WorkflowResult result = pipeline.run(List.of(planner, worker), new WorkflowContext("o3"));

        assertEquals(1, result.stageResults().size());
        assertEquals(StageResult.Status.FAILED, result.stageResults().get(0).status());
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
