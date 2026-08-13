package com.deepseek.harness4j.client;

import com.deepseek.harness4j.error.HarnessException;
import com.deepseek.harness4j.error.HarnessTimeoutException;
import com.deepseek.harness4j.error.JsonRpcException;
import com.deepseek.harness4j.error.MissingRuntimeException;
import com.deepseek.harness4j.error.TransportClosedException;
import com.deepseek.harness4j.model.IncomingRequest;
import com.deepseek.harness4j.model.InitializeResponse;
import com.deepseek.harness4j.model.JsonValues;
import com.deepseek.harness4j.model.Notification;
import com.deepseek.harness4j.model.ServerInfo;
import com.deepseek.harness4j.runtime.RuntimeResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous JSON-RPC client for the DeepSeek Harness SDK runtime over stdio.
 *
 * <p>Line-by-line Java port of the Python {@code HarnessClient} in
 * {@code python/sdk/src/deepseek_harness/client.py}. The client spawns the
 * runtime subprocess, talks newline-delimited JSON-RPC 2.0 on stdin/stdout,
 * drains stderr for diagnostics, and routes responses, notifications, and
 * incoming requests to the right queues.
 */
public class HarnessClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HarnessConfig config;
    private Process proc;
    private BufferedWriter stdinWriter;
    private final Object lock = new Object();
    private final Object writeLock = new Object();
    private final Map<String, BlockingQueue<Object>> responses = new ConcurrentHashMap<>();
    private final BlockingQueue<Object> notifications = new LinkedBlockingQueue<>();
    private final Map<String, Subscriber> notificationSubscribers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionParents = new ConcurrentHashMap<>();
    private final BlockingQueue<Object> requests = new LinkedBlockingQueue<>();
    private final ArrayDeque<String> stderrLines = new ArrayDeque<>();
    private static final int STDERR_MAX_LINES = 400;
    private Thread readerThread;
    private Thread stderrThread;

    public HarnessClient() {
        this(new HarnessConfig());
    }

    public HarnessClient(HarnessConfig config) {
        this.config = config == null ? new HarnessConfig() : config;
    }

    /** @return the configuration this client was built with. */
    public HarnessConfig config() {
        return config;
    }

    /** @return the running subprocess, or {@code null} before {@link #start()}. */
    public Process process() {
        return proc;
    }

    /**
     * @return the global notification queue, for inspection by tests and advanced callers
     *         (unmatched notifications land here when no subscription matches).
     */
    public BlockingQueue<Object> notificationsQueue() {
        return notifications;
    }

    @Override
    public void close() {
        Process current = proc;
        if (current == null) {
            return;
        }
        try {
            request("shutdown", null, ShutdownResponse.class, config.shutdownTimeoutSeconds(),
                    null, null, null);
        } catch (Exception exc) {
            appendStderr("shutdown request failed: " + exc);
        }
        java.io.Writer stdin = stdinWriter;
        if (stdin != null) {
            try {
                stdin.close();
            } catch (Exception exc) {
                appendStderr("stdin close failed: " + exc);
            }
        }
        stdinWriter = null;
        if (current.isAlive()) {
            current.destroy();
        }
        try {
            Double timeout = config.shutdownTimeoutSeconds();
            boolean exited;
            if (timeout == null) {
                current.waitFor();
                exited = true;
            } else {
                exited = current.waitFor(timeout.longValue(), TimeUnit.SECONDS);
            }
            if (!exited) {
                current.destroyForcibly();
                current.waitFor();
            }
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
        this.proc = null;
        failWaiters(runtimeClosedError("DeepSeek Harness runtime closed"));
        joinThread(readerThread);
        joinThread(stderrThread);
    }

    public void start() {
        if (proc != null) {
            return;
        }
        synchronized (lock) {
            sessionParents.clear();
        }
        String[] chosen = config.launchArgsOverride() != null
                ? config.launchArgsOverride()
                : defaultLaunchArgs();
        List<String> args = new ArrayList<>(List.of(chosen));
        Map<String, String> env = new LinkedHashMap<>();
        if (config.env() != null) {
            env.putAll(config.env());
        }
        injectBundledDefaultConfig(env);
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(false);
        if (config.cwd() != null) {
            builder.directory(new File(Path.of(config.cwd()).toAbsolutePath().toString()));
        }
        builder.environment().putAll(env);
        try {
            this.proc = builder.start();
            this.stdinWriter = new BufferedWriter(
                    new OutputStreamWriter(this.proc.getOutputStream(), StandardCharsets.UTF_8));
        } catch (IOException exc) {
            throw new HarnessException("Failed to start DeepSeek Harness runtime", exc);
        }
        startReaderThread();
        startStderrThread();
    }

    public InitializeResponse initialize(String cwd, String provider, String model, Integer maxTokens) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cwd", Path.of(cwd).toAbsolutePath().toString());
        payload.put("provider", provider);
        payload.put("model", model);
        if (maxTokens != null) {
            payload.put("maxTokens", maxTokens);
        }
        try {
            return request("initialize", payload, InitializeResponse.class, null, null, null, null);
        } catch (RuntimeException | Error exc) {
            close();
            throw exc;
        }
    }

    public String sessionPrompt(String sessionId, List<Map<String, Object>> contentBlocks,
                                java.util.function.Consumer<Notification> onNotification,
                                NotificationSubscription notificationSubscription) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("contentBlocks", contentBlocks);
        SessionPromptResponse response = request(
                "session/prompt",
                payload,
                SessionPromptResponse.class,
                null,
                onNotification,
                notificationBelongsToSessionTree(sessionId),
                notificationSubscription);
        if (response.messageId() == null) {
            // Port of pydantic's required-field ValueError for _SessionPromptResponse.
            throw new IllegalArgumentException("session/prompt response requires a messageId");
        }
        return response.messageId();
    }

    public <T> T request(String method, Map<String, Object> params, Class<T> responseModel) {
        return request(method, params, responseModel, null, null, null, null);
    }

    public <T> T request(String method, Map<String, Object> params, Class<T> responseModel,
                         Double timeoutSeconds, java.util.function.Consumer<Notification> onNotification,
                         NotificationFilter notificationFilter,
                         NotificationSubscription notificationSubscription) {
        Object result = requestRaw(method, params, timeoutSeconds, onNotification, notificationFilter,
                notificationSubscription);
        if (!(result instanceof Map)) {
            // Port of Python's TypeError: "<method> response must be a JSON object".
            throw new IllegalArgumentException(method + " response must be a JSON object");
        }
        return MAPPER.convertValue(result, responseModel);
    }

    public void notify(String method, Map<String, Object> params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        if (params != null) {
            message.put("params", params);
        }
        writeMessage(message);
    }

    public Notification nextNotification() {
        return nextFromQueue(notifications, "notification");
    }

    public NotificationSubscription subscribeNotifications(NotificationFilter notificationFilter) {
        String subscriptionId = UUID.randomUUID().toString();
        BlockingQueue<Object> subscriptionQueue = new LinkedBlockingQueue<>();
        synchronized (lock) {
            notificationSubscribers.put(subscriptionId, new Subscriber(subscriptionQueue, notificationFilter));
        }
        return new NotificationSubscription(this, subscriptionId, subscriptionQueue);
    }

    public NotificationSubscription subscribeNotifications() {
        return subscribeNotifications(null);
    }

    /**
     * Subscribe to a session and descendants discovered from subagent lifecycle edges.
     */
    public NotificationSubscription subscribeSessionNotifications(String sessionId) {
        return subscribeNotifications(notificationBelongsToSessionTree(sessionId));
    }

    public IncomingRequest nextRequest() {
        return nextRequestFromQueue();
    }

    public void respond(Object requestId, Object result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", requestId);
        message.put("result", result);
        writeMessage(message);
    }

    public void respondError(Object requestId, int code, String message, Object data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("jsonrpc", "2.0");
        wire.put("id", requestId);
        wire.put("error", error);
        writeMessage(wire);
    }

    private Object requestRaw(String method, Map<String, Object> params, Double timeoutSeconds,
                              java.util.function.Consumer<Notification> onNotification,
                              NotificationFilter notificationFilter,
                              NotificationSubscription notificationSubscription) {
        String requestId = UUID.randomUUID().toString();
        BlockingQueue<Object> waiter = new LinkedBlockingQueue<>(1);
        NotificationSubscription tempSubscription = null;
        NotificationSubscription subscription = notificationSubscription;
        synchronized (lock) {
            responses.put(requestId, waiter);
        }
        if (onNotification != null && subscription == null) {
            tempSubscription = subscribeNotifications(notificationFilter);
            subscription = tempSubscription;
        }
        try {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("jsonrpc", "2.0");
            message.put("id", requestId);
            message.put("method", method);
            if (params != null) {
                message.put("params", params);
            }
            writeMessage(message);
        } catch (RuntimeException | Error exc) {
            synchronized (lock) {
                responses.remove(requestId);
            }
            if (tempSubscription != null) {
                tempSubscription.close();
            }
            throw exc;
        }
        Double timeout = timeoutSeconds != null ? timeoutSeconds : config.requestTimeoutSeconds();
        long deadlineNanos = timeout == null ? Long.MAX_VALUE : System.nanoTime() + toNanos(timeout);
        Object item = null;
        boolean done = false;
        try {
            while (!done) {
                if (onNotification != null && subscription != null) {
                    subscription.drain(onNotification);
                }
                Long waitNanos = null;
                if (onNotification != null) {
                    waitNanos = 50_000_000L; // 0.05 s
                }
                if (deadlineNanos != Long.MAX_VALUE) {
                    long remaining = deadlineNanos - System.nanoTime();
                    if (remaining <= 0) {
                        synchronized (lock) {
                            responses.remove(requestId);
                        }
                        String diagnostics = runtimeDiagnostics();
                        String suffix = diagnostics.isEmpty() ? "" : "\n" + diagnostics;
                        throw new HarnessTimeoutException(
                                method + " timed out waiting for DeepSeek Harness runtime" + suffix);
                    }
                    waitNanos = waitNanos == null ? remaining : Math.min(waitNanos, remaining);
                }
                try {
                    item = waitNanos == null
                            ? waiter.take()
                            : waiter.poll(waitNanos, TimeUnit.NANOSECONDS);
                    if (item != null) {
                        if (onNotification != null && subscription != null) {
                            subscription.drain(onNotification);
                        }
                        done = true;
                    }
                } catch (InterruptedException exc) {
                    Thread.currentThread().interrupt();
                    throw new HarnessException("Interrupted while waiting for runtime response", exc);
                }
            }
        } catch (RuntimeException | Error exc) {
            synchronized (lock) {
                responses.remove(requestId);
            }
            if (tempSubscription != null) {
                tempSubscription.close();
            }
            throw exc;
        } finally {
            if (tempSubscription != null) {
                tempSubscription.close();
            }
        }
        if (item instanceof Throwable throwable) {
            rethrow(throwable);
        }
        return item;
    }

    private void writeMessage(Map<String, Object> message) {
        Process current = proc;
        BufferedWriter writer = stdinWriter;
        if (current == null || writer == null) {
            throw new TransportClosedException("DeepSeek Harness runtime is not running");
        }
        try {
            String payload;
            try {
                payload = MAPPER.writeValueAsString(message) + "\n";
            } catch (IOException exc) {
                throw new HarnessException("Failed to serialize JSON-RPC message", exc);
            }
            synchronized (writeLock) {
                writer.write(payload);
                writer.flush();
            }
        } catch (Exception exc) {
            throw runtimeClosedError("Failed to write to DeepSeek Harness runtime", exc);
        }
    }

    private void startReaderThread() {
        readerThread = new Thread(this::readerLoop, "dsh-runtime-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void startStderrThread() {
        stderrThread = new Thread(this::stderrLoop, "dsh-runtime-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private void readerLoop() {
        Process current = proc;
        if (current == null || current.getInputStream() == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Object message;
                try {
                    message = MAPPER.readValue(line, new TypeReference<Map<String, Object>>() {
                    });
                } catch (IOException exc) {
                    continue;
                }
                handleMessage(message);
            }
        } catch (Throwable exc) {
            failWaiters(exc);
        } finally {
            failWaiters(runtimeClosedError("DeepSeek Harness runtime stdout closed"));
        }
    }

    private void stderrLoop() {
        Process current = proc;
        if (current == null || current.getErrorStream() == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(current.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendStderr(line.stripTrailing());
            }
        } catch (IOException ignored) {
            // stderr stream closed on shutdown; nothing else can reach this loop.
        }
    }

    void handleMessage(Object message) {
        Map<String, Object> msg = JsonValues.asObject(message);
        if (msg == null) {
            return;
        }
        Object msgId = msg.get("id");
        Object method = msg.get("method");
        if (isId(msgId) && method instanceof String methodName) {
            Object params = msg.get("params");
            Map<String, Object> payload = params instanceof Map
                    ? asStringKeyedMap(params)
                    : new LinkedHashMap<>();
            requests.add(new IncomingRequest(msgId, methodName, payload));
            return;
        }
        if (isId(msgId)) {
            BlockingQueue<Object> waiter;
            synchronized (lock) {
                waiter = responses.remove(String.valueOf(msgId));
            }
            if (waiter == null) {
                return;
            }
            Object error = msg.get("error");
            if (error instanceof Map errorMap) {
                Object errCode = errorMap.get("code");
                Object errMessage = errorMap.getOrDefault("message", "JSON-RPC error");
                waiter.add(new JsonRpcException(
                        JsonValues.asIntOrNull(errCode),
                        String.valueOf(errMessage),
                        errorMap.get("data")));
            } else {
                waiter.add(msg.get("result"));
            }
            return;
        }
        if (method instanceof String methodName) {
            Object params = msg.get("params");
            Map<String, Object> payload = params instanceof Map
                    ? asStringKeyedMap(params)
                    : new LinkedHashMap<>();
            Notification notification = new Notification(methodName, payload);
            List<Map.Entry<String, Subscriber>> subscribers;
            synchronized (lock) {
                recordSessionRelationshipLocked(notification);
                subscribers = new ArrayList<>(notificationSubscribers.entrySet());
            }
            boolean delivered = false;
            for (Map.Entry<String, Subscriber> entry : subscribers) {
                Subscriber subscriber = entry.getValue();
                boolean matches;
                try {
                    matches = subscriber.filter() == null || subscriber.filter().test(notification);
                } catch (Throwable exc) {
                    synchronized (lock) {
                        Subscriber current = notificationSubscribers.get(entry.getKey());
                        if (current != null && current == subscriber) {
                            notificationSubscribers.remove(entry.getKey());
                        }
                    }
                    subscriber.queue().add(exc);
                    continue;
                }
                if (matches) {
                    subscriber.queue().add(notification);
                    delivered = true;
                }
            }
            if (!delivered) {
                notifications.add(notification);
            }
        }
    }

    private void failWaiters(Throwable exc) {
        List<BlockingQueue<Object>> waiters;
        List<Subscriber> subscribers;
        synchronized (lock) {
            waiters = new ArrayList<>(responses.values());
            responses.clear();
            subscribers = new ArrayList<>(notificationSubscribers.values());
            notificationSubscribers.clear();
        }
        for (BlockingQueue<Object> waiter : waiters) {
            waiter.add(exc);
        }
        for (Subscriber subscriber : subscribers) {
            subscriber.queue().add(exc);
        }
        notifications.add(exc);
        requests.add(exc);
    }

    private TransportClosedException runtimeClosedError(String reason) {
        String diagnostics = runtimeDiagnostics();
        return new TransportClosedException(diagnostics.isEmpty() ? reason : reason + "\n" + diagnostics);
    }

    private TransportClosedException runtimeClosedError(String reason, Throwable cause) {
        String diagnostics = runtimeDiagnostics();
        return new TransportClosedException(
                diagnostics.isEmpty() ? reason : reason + "\n" + diagnostics, cause);
    }

    private String runtimeDiagnostics() {
        Process current = proc;
        if (current != null
                && !current.isAlive()
                && stderrThread != null
                && stderrThread.isAlive()
                && Thread.currentThread() != stderrThread) {
            joinThread(stderrThread, 100);
        }
        List<String> parts = new ArrayList<>();
        if (current != null && !current.isAlive()) {
            parts.add("exit code: " + current.exitValue());
        }
        synchronized (lock) {
            if (!stderrLines.isEmpty()) {
                parts.add("stderr tail:\n" + String.join("\n", stderrLines));
            }
        }
        return String.join("\n", parts);
    }

    private String[] defaultLaunchArgs() {
        if (config.runtimeBin() != null) {
            return new String[]{config.runtimeBin()};
        }
        if (config.bridgeBin() != null) {
            return new String[]{config.bridgeBin()};
        }
        String[] args;
        try {
            args = RuntimeResolver.resolveBundledLaunchArgs(null);
        } catch (MissingRuntimeException exc) {
            throw new MissingRuntimeException(
                    "Unable to locate the bundled DeepSeek Harness SDK runtime. "
                            + "Install deepseek-harness4j-runtime-bin or set HarnessConfig.runtimeBin.",
                    exc);
        }
        return args;
    }

    private void injectBundledDefaultConfig(Map<String, String> env) {
        boolean usesBundledRuntime = config.launchArgsOverride() == null
                && config.runtimeBin() == null
                && config.bridgeBin() == null;
        if (!usesBundledRuntime) {
            return;
        }
        String ambient = env.get("DSH_CORDIS_CONFIG");
        if (ambient != null && !ambient.isEmpty()) {
            return;
        }
        // defaultLaunchArgs already resolved the bundle or raised its install error.
        env.put("DSH_CORDIS_CONFIG", RuntimeResolver.bundledDefaultConfigPath().toString());
    }

    void unsubscribeNotifications(String subscriptionId) {
        synchronized (lock) {
            notificationSubscribers.remove(subscriptionId);
        }
    }

    private void recordSessionRelationshipLocked(Notification notification) {
        if (!"subagent.started".equals(notification.method())) {
            return;
        }
        Object parentId = notification.payload().get("parentSessionId");
        Object childId = notification.payload().get("childSessionId");
        if (parentId instanceof String parent
                && !parent.isEmpty()
                && childId instanceof String child
                && !child.isEmpty()
                && !parent.equals(child)) {
            sessionParents.put(child, parent);
        }
    }

    private NotificationFilter notificationBelongsToSessionTree(String sessionId) {
        return notification -> {
            Map<String, Object> payload = notification.payload();
            if ("subagent.started".equals(notification.method())
                    || "subagent.finished".equals(notification.method())) {
                Object parentId = payload.get("parentSessionId");
                if (parentId instanceof String parent
                        && sessionIsDescendantOf(parent, sessionId)) {
                    return true;
                }
                Object childId = payload.get("childSessionId");
                return sessionId.equals(childId);
            }
            Object relatedId = payload.get("sessionId");
            return relatedId instanceof String related
                    && sessionIsDescendantOf(related, sessionId);
        };
    }

    private boolean sessionIsDescendantOf(String sessionId, String rootSessionId) {
        String current = sessionId;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (!visited.contains(current)) {
            if (current.equals(rootSessionId)) {
                return true;
            }
            visited.add(current);
            String parent = sessionParents.get(current);
            if (parent == null) {
                return false;
            }
            current = parent;
        }
        return false;
    }

    private Notification nextFromQueue(BlockingQueue<Object> queue, String what) {
        Object item;
        try {
            item = queue.take();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new HarnessException("Interrupted while waiting for " + what, exc);
        }
        if (item instanceof Throwable throwable) {
            rethrow(throwable);
        }
        return (Notification) item;
    }

    private IncomingRequest nextRequestFromQueue() {
        Object item;
        try {
            item = requests.take();
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
            throw new HarnessException("Interrupted while waiting for incoming request", exc);
        }
        if (item instanceof Throwable throwable) {
            rethrow(throwable);
        }
        return (IncomingRequest) item;
    }

    private void appendStderr(String line) {
        synchronized (lock) {
            stderrLines.addLast(line);
            while (stderrLines.size() > STDERR_MAX_LINES) {
                stderrLines.removeFirst();
            }
        }
    }

    private static void joinThread(Thread thread) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(500);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
    }

    private static void joinThread(Thread thread, long millis) {
        if (thread == null || !thread.isAlive()) {
            return;
        }
        try {
            thread.join(millis);
        } catch (InterruptedException exc) {
            Thread.currentThread().interrupt();
        }
    }

    private static long toNanos(double seconds) {
        return (long) (seconds * 1_000_000_000.0);
    }

    private static boolean isId(Object value) {
        return value instanceof String || value instanceof Integer || value instanceof Long;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void rethrow(Throwable throwable) throws T {
        if (throwable instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw (T) new HarnessException(throwable);
    }

    /**
     * A registered notification subscription: its private queue plus the
     * optional filter that gates delivery.
     */
    private static final class Subscriber {
        private final BlockingQueue<Object> queue;
        private final NotificationFilter filter;

        private Subscriber(BlockingQueue<Object> queue, NotificationFilter filter) {
            this.queue = queue;
            this.filter = filter;
        }

        BlockingQueue<Object> queue() {
            return queue;
        }

        NotificationFilter filter() {
            return filter;
        }
    }

    /** Response model for {@code session/prompt} (Python {@code _SessionPromptResponse}). */
    static final class SessionPromptResponse {
        private String messageId;

        public String messageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }
    }

    /** Response model for {@code shutdown} (Python {@code _ShutdownResponse}). */
    static final class ShutdownResponse {
    }

    /**
     * Utility retained for parity with the Python module-level helpers and
     * tests: the {@code serverInfo} handshake payload helper.
     */
    public static ServerInfo serverInfo(String name, String version) {
        return new ServerInfo(name, version);
    }
}
