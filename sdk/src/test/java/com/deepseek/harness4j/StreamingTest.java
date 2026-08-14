package com.deepseek.harness4j;

import com.deepseek.harness4j.model.StreamChunk;
import com.deepseek.harness4j.test.TestRuntimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingTest {

    @TempDir
    Path tmp;

    @Test
    void test_stream_chunks_consumer() {
        List<StreamChunk> chunks = new ArrayList<>();
        RunResult result;

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "streaming-turn"))
                .build())) {
            result = harness.stream("stream this turn", "stream-sess", chunks::add);
        }

        assertEquals("stream-sess", result.sessionId());
        assertEquals("streaming hello", result.finalResponse());
        assertFalse(chunks.isEmpty());

        boolean hasReasoning = chunks.stream().anyMatch(StreamChunk::isReasoning);
        boolean hasContent = chunks.stream().anyMatch(StreamChunk::isContent);
        boolean hasToolCall = chunks.stream().anyMatch(StreamChunk::isToolCall);
        boolean hasToolResult = chunks.stream().anyMatch(StreamChunk::isToolResult);
        boolean hasTurnEnd = chunks.stream().anyMatch(StreamChunk::isTurnEnd);

        assertTrue(hasReasoning, "should receive reasoning chunk");
        assertTrue(hasContent, "should receive content chunk");
        assertTrue(hasToolCall, "should receive tool call chunk");
        assertTrue(hasToolResult, "should receive tool result chunk");
        assertTrue(hasTurnEnd, "should receive turn end chunk");
    }

    @Test
    void test_stream_reactive_publisher() throws Exception {
        List<StreamChunk> publishedChunks = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        try (DeepSeekHarness harness = new DeepSeekHarness(DeepSeekHarnessConfig.builder()
                .launchArgsOverride(TestRuntimes.fakeRuntimeCommand())
                .cwd(tmp.toString())
                .env(Map.of("FR_SCENARIO", "streaming-turn"))
                .build())) {
            Flow.Publisher<StreamChunk> publisher = harness.stream("reactive turn", "pub-sess");

            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamChunk item) {
                    publishedChunks.add(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS), "publisher should complete within timeout");
            assertFalse(publishedChunks.isEmpty());
            assertTrue(publishedChunks.stream().anyMatch(c -> c.text().contains("thinking")));
            assertTrue(publishedChunks.stream().anyMatch(c -> c.text().contains("streaming hello")));
        }
    }
}
