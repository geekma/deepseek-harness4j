package com.deepseek.harness4j.examples;

import com.deepseek.harness4j.DeepSeekHarness;
import com.deepseek.harness4j.DeepSeekHarnessConfig;
import com.deepseek.harness4j.RunResult;
import com.deepseek.harness4j.runtime.RuntimeResolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Drive the repo-source JSON-RPC bin through the SDK and a keyless mock SSE server.
 *
 * <p>Line-by-line Java port of {@code python/sdk/tests/manual_sdk_agent_smoke.py}. Requires the
 * upstream deepseek-harness checkout (for {@code packages/examples/jsonrpc-demo/src/bin.ts}) and
 * a system {@code node} with {@code tsx}; no API key. This manual smoke is not collected by the
 * test suite — run {@code main} directly.
 *
 * <pre>{@code
 * java -cp ... com.deepseek.harness4j.examples.ManualSdkAgentSmoke \
 *   --repo-root /path/to/deepseek-harness [--keep-sessions]
 * }</pre>
 */
public final class ManualSdkAgentSmoke {

    private static final byte[] ZSTD_MAGIC = {(byte) 0x28, (byte) 0xb5, (byte) 0x2f, (byte) 0xfd};

    private static final List<Map<String, Object>> REQUESTS = new ArrayList<>();

    private ManualSdkAgentSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath();
        boolean keepSessions = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--repo-root" -> repoRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--keep-sessions" -> keepSessions = true;
                default -> throw new IllegalArgumentException("unknown option: " + args[i]);
            }
        }
        runSmoke(repoRoot, keepSessions);
    }

    private static void runSmoke(Path repoRoot, boolean keepSessions) throws Exception {
        Path sessionRoot = Files.createTempDirectory("dsh-sdk-smoke-sessions-");
        Path runtimeEntry = repoRoot.resolve("packages/examples/jsonrpc-demo/src/bin.ts");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ManualSdkAgentSmoke::handleCompletion);
        server.setExecutor(executor);
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        System.out.println("repo_root=" + repoRoot);
        System.out.println("session_root=" + sessionRoot);
        System.out.println("mock_base_url=" + baseUrl);

        try {
            RunResult result;
            try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                    .model("sdk-smoke-model")
                    .cwd(repoRoot.resolve("python/sdk").toString())
                    .runtimeCwd(repoRoot.toString())
                    .sessionRoot(sessionRoot.toString())
                    .cordis(RuntimeResolver.bundledDefaultConfigPath().toString())
                    .launchArgsOverride("node", "--import", "tsx", runtimeEntry.toString())
                    .env(Map.of(
                            "DEEPSEEK_BASE_URL", baseUrl,
                            "DEEPSEEK_API_KEY", "sdk-smoke-key"))
                    .requestTimeoutSeconds(20.0)
                    .shutdownTimeoutSeconds(2.0)
                    .build())) {
                result = harness.run(
                        "Please reply with a short confirmation and do not call tools.",
                        "sdk-smoke-main",
                        null);
            }
            System.out.println("final_response=" + result.finalResponse());
            if (!result.finalResponse().contains("configured HTTP model endpoint")) {
                throw new AssertionError("final response did not reach the mock endpoint");
            }
            if (REQUESTS.size() != 1) {
                throw new AssertionError("expected exactly one model request, got " + REQUESTS.size());
            }
            Map<String, Object> request = REQUESTS.get(0);
            System.out.println(request);
            if (!"Bearer sdk-smoke-key".equals(request.get("authorization"))) {
                throw new AssertionError("unexpected authorization header: " + request.get("authorization"));
            }
            Map<String, Object> body = (Map<String, Object>) request.get("body");
            if (!"sdk-smoke-model".equals(body.get("model"))) {
                throw new AssertionError("unexpected model: " + body.get("model"));
            }

            List<Path> jsonlFiles = new ArrayList<>();
            try (var stream = Files.walk(sessionRoot)) {
                stream.filter(path -> path.toString().endsWith(".jsonl.zstd"))
                        .forEach(jsonlFiles::add);
            }
            if (jsonlFiles.isEmpty()) {
                throw new AssertionError("no Zstandard JSONL sessions were written under " + sessionRoot);
            }
            System.out.println("session_jsonl_zstd_files:");
            for (Path path : jsonlFiles) {
                System.out.println("  " + path + " bytes=" + Files.size(path));
                byte[] head = Files.readAllBytes(path);
                boolean magic = head.length >= 4
                        && head[0] == ZSTD_MAGIC[0] && head[1] == ZSTD_MAGIC[1]
                        && head[2] == ZSTD_MAGIC[2] && head[3] == ZSTD_MAGIC[3];
                if (!magic) {
                    throw new AssertionError("session file is not Zstandard: " + path);
                }
            }
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }

        if (keepSessions) {
            System.out.println("kept_session_root=" + sessionRoot);
        } else {
            deleteRecursively(sessionRoot);
            System.out.println("removed temporary session root");
        }
    }

    private static void handleCompletion(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        REQUESTS.add(Map.of(
                "path", exchange.getRequestURI().getPath(),
                "authorization", exchange.getRequestHeaders().getFirst("authorization"),
                "body", parseJson(body)));

        String sse = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":null,"
                + "\"reasoning_content\":\"\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":"
                + "\"SDK runtime reached the configured HTTP model endpoint.\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":9}}\n\n"
                + "data: [DONE]\n\n";
        byte[] payload = sse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "text/event-stream");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static Map<String, Object> parseJson(String text) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    text, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception error) {
            throw new IllegalStateException("could not parse mock request body", error);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
            });
        }
    }
}
