package com.myriads.workflow.web;

import com.myriads.workflow.core.Workflow;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The set of named {@link Workflow}s the portal can list and run.
 *
 * <p>Insertion order is preserved so the UI lists workflows in the order they
 * were registered. Populate it at startup (see {@link DemoWorkflows}) and hand
 * it to the {@link WorkflowServer}.
 */
public final class WorkflowCatalog {

    private final Map<String, Workflow> workflows = new LinkedHashMap<>();

    public WorkflowCatalog register(Workflow workflow) {
        workflows.put(workflow.name(), workflow);
        return this;
    }

    public Optional<Workflow> get(String name) {
        return Optional.ofNullable(workflows.get(name));
    }

    public Collection<Workflow> all() {
        return workflows.values();
    }
}
