package com.myriads.workflow.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;

/**
 * Thin wrapper over the Anthropic Java SDK used by {@link ClaudeAgent}.
 *
 * <p>Keeps all SDK calls in one place so the rest of the engine stays free of
 * Anthropic types. The underlying client is created lazily from the environment
 * ({@code ANTHROPIC_API_KEY}) and shared across agents.
 */
public final class ClaudeClient {

    private static volatile ClaudeClient instance;

    private final AnthropicClient client;

    private ClaudeClient(AnthropicClient client) {
        this.client = client;
    }

    /** The lazily-initialised shared client. Throws a clear error if no API key is set. */
    public static ClaudeClient shared() {
        ClaudeClient local = instance;
        if (local == null) {
            synchronized (ClaudeClient.class) {
                local = instance;
                if (local == null) {
                    String key = System.getenv("ANTHROPIC_API_KEY");
                    if (key == null || key.isBlank()) {
                        throw new IllegalStateException(
                                "ANTHROPIC_API_KEY is not set — export it to run Claude-backed agents");
                    }
                    local = new ClaudeClient(AnthropicOkHttpClient.fromEnv());
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Sends a single-turn request to Claude and returns the concatenated text response.
     *
     * @param model     model id, e.g. {@code claude-opus-4-8}
     * @param system    the system prompt defining the agent's role
     * @param user      the user prompt for this turn
     * @param maxTokens output cap for the response
     */
    public String complete(String model, String system, String user, long maxTokens) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .addUserMessage(user)
                .build();

        Message response = client.messages().create(params);

        StringBuilder text = new StringBuilder();
        response.content().stream()
                .flatMap(block -> block.text().stream())
                .forEach(block -> text.append(block.text()));
        return text.toString().trim();
    }
}
