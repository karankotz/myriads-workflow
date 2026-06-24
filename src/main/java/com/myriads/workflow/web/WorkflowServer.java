package com.myriads.workflow.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myriads.workflow.core.StageResult;
import com.myriads.workflow.core.Workflow;
import com.myriads.workflow.core.WorkflowContext;
import com.myriads.workflow.core.WorkflowListener;
import com.myriads.workflow.core.WorkflowResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP portal that lists workflows and runs them, streaming each
 * stage's progress to the browser over Server-Sent Events.
 *
 * <p>Built on the JDK's {@link HttpServer} to keep the starter dependency-light.
 * Routes:
 * <ul>
 *   <li>{@code GET /} — the single-page UI (served from {@code resources/web/}).</li>
 *   <li>{@code GET /api/workflows} — list workflows and their stage names.</li>
 *   <li>{@code GET /api/workflows/{name}} — one workflow's definition.</li>
 *   <li>{@code POST /api/workflows/{name}/run} — run it, return the full result as JSON.</li>
 *   <li>{@code GET  /api/workflows/{name}/stream} — run it, streaming live SSE events.</li>
 * </ul>
 * The {@code stream} endpoint attaches a {@link WorkflowListener} that turns
 * engine callbacks into SSE events, which is what drives the live UI.
 */
public final class WorkflowServer {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServer.class);
    private static final String API_PREFIX = "/api/workflows";

    private final int port;
    private final WorkflowCatalog catalog;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    public WorkflowServer(int port, WorkflowCatalog catalog) {
        this.port = port;
        this.catalog = catalog;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(API_PREFIX, safely(this::handleApi));
        server.createContext("/", safely(this::handleStatic));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        log.info("Workflow portal running at http://localhost:{}", port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- routing -----------------------------------------------------------

    private void handleApi(HttpExchange exchange) throws IOException {
        String rest = exchange.getRequestURI().getPath().substring(API_PREFIX.length());
        if (rest.isEmpty() || rest.equals("/")) {
            listWorkflows(exchange);
            return;
        }

        String[] parts = rest.split("/"); // ["", name, action?]
        String name = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        String action = parts.length > 2 ? parts[2] : "";

        Optional<Workflow> workflow = catalog.get(name);
        if (workflow.isEmpty()) {
            sendJson(exchange, 404, Map.of("error", "unknown workflow: " + name));
            return;
        }

        switch (action) {
            case "" -> sendJson(exchange, 200, summary(workflow.get()));
            case "run" -> runWorkflow(exchange, workflow.get());
            case "stream" -> streamWorkflow(exchange, workflow.get());
            default -> sendJson(exchange, 404, Map.of("error", "unknown action: " + action));
        }
    }

    private void listWorkflows(HttpExchange exchange) throws IOException {
        List<Object> summaries = new ArrayList<>();
        for (Workflow workflow : catalog.all()) {
            summaries.add(summary(workflow));
        }
        sendJson(exchange, 200, summaries);
    }

    private void runWorkflow(HttpExchange exchange, Workflow workflow) throws IOException {
        WorkflowResult result = workflow.run(newContext(exchange));
        sendJson(exchange, 200, resultPayload(result));
    }

    private void streamWorkflow(HttpExchange exchange, Workflow workflow) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream out = exchange.getResponseBody()) {
            WorkflowListener listener = new WorkflowListener() {
                @Override
                public void onWorkflowStarted(String runId, String workflowName, List<String> stageNames) {
                    emit(out, "started", Map.of("runId", runId, "workflow", workflowName, "stages", stageNames));
                }

                @Override
                public void onStageStarted(String runId, String stageName) {
                    emit(out, "stage-started", Map.of("stage", stageName));
                }

                @Override
                public void onStageCompleted(String runId, StageResult result) {
                    emit(out, "stage-completed", stagePayload(result));
                }

                @Override
                public void onWorkflowCompleted(WorkflowResult result) {
                    emit(out, "completed", Map.of("runId", result.runId(), "completed", result.completed()));
                }
            };

            try {
                workflow.run(newContext(exchange), listener);
            } catch (UncheckedIOException disconnected) {
                log.debug("Client disconnected during stream of '{}'", workflow.name());
            } catch (RuntimeException e) {
                emit(out, "error", Map.of("message", String.valueOf(e.getMessage())));
            }
        }
    }

    // --- static files ------------------------------------------------------

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.equals("/favicon.ico")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try (InputStream in = getClass().getResourceAsStream("/web" + path)) {
            if (in == null) {
                sendText(exchange, 404, "Not found: " + path);
                return;
            }
            byte[] body = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(path));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    // --- payloads ----------------------------------------------------------

    private static Map<String, Object> summary(Workflow workflow) {
        return Map.of("name", workflow.name(), "stages", workflow.stageNames());
    }

    private static Map<String, Object> resultPayload(WorkflowResult result) {
        List<Object> stages = new ArrayList<>();
        for (StageResult stage : result.stageResults()) {
            stages.add(stagePayload(stage));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", result.runId());
        payload.put("completed", result.completed());
        payload.put("stages", stages);
        return payload;
    }

    private static Map<String, Object> stagePayload(StageResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", result.stageName());
        payload.put("status", result.status().name());
        payload.put("output", result.output() == null ? null : result.output().toString());
        payload.put("error", result.error() == null ? null : result.error().getMessage());
        return payload;
    }

    // --- helpers -----------------------------------------------------------

    private WorkflowContext newContext(HttpExchange exchange) {
        WorkflowContext context = new WorkflowContext(UUID.randomUUID().toString());
        String goal = queryParam(exchange, "goal");
        if (goal != null && !goal.isBlank()) {
            context.put("goal", goal);
        }
        return context;
    }

    private void emit(OutputStream out, String event, Object data) {
        try {
            String json = mapper.writeValueAsString(data);
            byte[] frame = ("event: " + event + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8);
            // A parallel pipeline emits from many threads onto this one stream;
            // lock so SSE frames are written whole and never interleave.
            synchronized (out) {
                out.write(frame);
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // aborts the run if the client has gone away
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).equals(key)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        return "text/plain; charset=utf-8";
    }

    /** Wraps a handler so any thrown exception becomes a 500 instead of killing the worker thread. */
    private com.sun.net.httpserver.HttpHandler safely(ThrowingHandler handler) {
        return exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                log.error("Error handling {}", exchange.getRequestURI(), e);
                try {
                    sendText(exchange, 500, "Internal error");
                } catch (IOException | IllegalStateException ignored) {
                    // response may already be committed (e.g. mid-stream); nothing more to do
                }
            } finally {
                exchange.close();
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
