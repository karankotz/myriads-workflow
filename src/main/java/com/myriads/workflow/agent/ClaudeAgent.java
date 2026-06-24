package com.myriads.workflow.agent;

import com.myriads.workflow.core.WorkflowContext;

import java.util.function.Function;

/**
 * An {@link Agent} backed by Claude: it builds a prompt from the run context,
 * calls the Anthropic API, and returns the model's text as its output.
 *
 * <p>Because it implements {@link Agent}, a {@code ClaudeAgent} drops into any
 * pipeline and the web portal exactly like a hand-written stage — the engine
 * neither knows nor cares that the work is done by an LLM.
 *
 * <p>The model defaults to {@code claude-opus-4-8} and can be overridden with
 * the {@code MYRIADS_MODEL} environment variable. Requires {@code ANTHROPIC_API_KEY}
 * to be set at run time (see {@link ClaudeClient}).
 */
public final class ClaudeAgent implements Agent {

    private static final long DEFAULT_MAX_TOKENS = 512;

    private final String name;
    private final String systemPrompt;
    private final Function<WorkflowContext, String> promptBuilder;
    private final String model;
    private final long maxTokens;

    /**
     * @param name          the agent's stage name (also the context key for its output)
     * @param systemPrompt  defines the agent's role and behaviour
     * @param promptBuilder turns the shared run context into this agent's user prompt
     */
    public ClaudeAgent(String name, String systemPrompt, Function<WorkflowContext, String> promptBuilder) {
        this(name, systemPrompt, promptBuilder, defaultModel(), DEFAULT_MAX_TOKENS);
    }

    public ClaudeAgent(String name, String systemPrompt, Function<WorkflowContext, String> promptBuilder,
                       String model, long maxTokens) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.promptBuilder = promptBuilder;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object act(WorkflowContext context) {
        String prompt = promptBuilder.apply(context);
        return ClaudeClient.shared().complete(model, systemPrompt, prompt, maxTokens);
    }

    private static String defaultModel() {
        String override = System.getenv("MYRIADS_MODEL");
        return (override == null || override.isBlank()) ? "claude-opus-4-8" : override;
    }
}
