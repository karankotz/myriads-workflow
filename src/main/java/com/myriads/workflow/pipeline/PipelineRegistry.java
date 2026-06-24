package com.myriads.workflow.pipeline;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of available {@link Pipeline} strategies, keyed by {@link Pipeline#id()}.
 *
 * <p>This is how new execution strategies are wired into the engine: implement
 * {@link Pipeline}, register an instance here, then select it by id when
 * building a workflow. A fresh registry comes preloaded with the built-in
 * {@link SequentialPipeline}.
 */
public final class PipelineRegistry {

    private final Map<String, Pipeline> pipelines = new ConcurrentHashMap<>();

    private PipelineRegistry() {
    }

    /** Creates a registry preloaded with the built-in pipelines. */
    public static PipelineRegistry withDefaults() {
        PipelineRegistry registry = new PipelineRegistry();
        registry.register(new SequentialPipeline());
        registry.register(new ParallelPipeline());
        return registry;
    }

    public PipelineRegistry register(Pipeline pipeline) {
        pipelines.put(pipeline.id(), pipeline);
        return this;
    }

    public Pipeline get(String id) {
        Pipeline pipeline = pipelines.get(id);
        if (pipeline == null) {
            throw new NoSuchElementException("No pipeline registered with id '" + id + "'");
        }
        return pipeline;
    }

    public boolean contains(String id) {
        return pipelines.containsKey(id);
    }
}
