package com.myriads.workflow.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, thread-safe state passed through every stage of a workflow run.
 *
 * <p>Stages read their inputs from the context and publish their outputs back
 * into it, so later stages (or other agents, once execution is distributed)
 * can consume them. Backed by a {@link ConcurrentHashMap} so parallel and
 * distributed pipelines can share it safely.
 */
public final class WorkflowContext {

    private final String runId;
    private final Map<String, Object> values = new ConcurrentHashMap<>();

    public WorkflowContext(String runId) {
        this.runId = runId;
    }

    /** Unique identifier for this workflow run. */
    public String runId() {
        return runId;
    }

    public WorkflowContext put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Optional<Object> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /** Typed accessor; throws if the stored value is not assignable to {@code type}. */
    public <T> Optional<T> get(String key, Class<T> type) {
        return get(key).map(type::cast);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Immutable snapshot of the current values, for logging or inspection. */
    public Map<String, Object> snapshot() {
        return Map.copyOf(values);
    }
}
