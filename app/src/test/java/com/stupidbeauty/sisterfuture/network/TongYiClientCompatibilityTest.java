package com.stupidbeauty.sisterfuture.network;

import com.stupidbeauty.sisterfuture.bean.ModelUsage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TongYiClientCompatibilityTest
{
  @Test
  public void recognizesOnlyExplicitOptionalUsageFieldRejections()
  {
    assertTrue(TongYiClient.isUnsupportedStreamOptionsResponse(
      400, "Unrecognized request argument supplied: stream_options"));
    assertTrue(TongYiClient.isUnsupportedStreamOptionsResponse(
      422, "{\"loc\":[\"body\",\"stream_options\"],\"msg\":\"Extra inputs are not permitted\"}"));
    assertTrue(TongYiClient.isUnsupportedStreamOptionsResponse(
      400, "stream_options is not a valid argument"));
    assertTrue(TongYiClient.isUnsupportedStreamOptionsResponse(
      400, "不支持 include_usage 参数"));

    assertFalse(TongYiClient.isUnsupportedStreamOptionsResponse(
      400, "maximum context length exceeded"));
    assertFalse(TongYiClient.isUnsupportedStreamOptionsResponse(
      500, "stream_options is unsupported"));
    assertFalse(TongYiClient.isUnsupportedStreamOptionsResponse(
      400, "stream_options was accepted but another parameter failed"));
  }

  @Test
  public void completionGateAllowsExactlyOneConcurrentTerminalPath() throws Exception
  {
    TongYiClient.CompletionGate gate = new TongYiClient.CompletionGate();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Boolean>> attempts = new ArrayList<>();
    for (int i = 0; i < 32; i++)
    {
      attempts.add(() ->
      {
        start.await();
        return gate.tryComplete();
      });
    }

    try
    {
      List<Future<Boolean>> results = new ArrayList<>();
      for (Callable<Boolean> attempt : attempts)
      {
        results.add(executor.submit(attempt));
      }
      start.countDown();

      int winners = 0;
      for (Future<Boolean> result : results)
      {
        if (result.get()) winners++;
      }

      assertEquals(1, winners);
      assertTrue(gate.isCompleted());
      assertFalse(gate.tryComplete());
    }
    finally
    {
      executor.shutdownNow();
    }
  }

  @Test
  public void finishReasonMarksEofAsCompleteOnlyWhenNonEmpty()
  {
    assertTrue(TongYiClient.isTerminalFinishReason("stop"));
    assertTrue(TongYiClient.isTerminalFinishReason("tool_calls"));
    assertTrue(TongYiClient.isTerminalFinishReason("length"));
    assertFalse(TongYiClient.isTerminalFinishReason(null));
    assertFalse(TongYiClient.isTerminalFinishReason(""));
    assertFalse(TongYiClient.isTerminalFinishReason("null"));
  }

  @Test
  public void requestMetricsIncludeCompatibilityFallbackAttempt()
  {
    TongYiClient.RequestMetricsAccumulator metrics =
      new TongYiClient.RequestMetricsAccumulator();
    metrics.recordAttempt(12_000L, "model-a");
    metrics.recordAttempt(11_900L, "model-a");
    metrics.recordProviderUsage(2_000L, 300L, 2_300L);

    ModelUsage usage = metrics.snapshot();

    assertEquals(2, usage.getRequestCount());
    assertEquals(1, usage.getTokenRequestCount());
    assertEquals(23_900L, usage.getRequestBytes());
    assertEquals(12_000L, usage.getPeakRequestBytes());
    assertEquals(2_300L, usage.getTotalTokens());
    assertEquals("model-a", usage.getModelName());
  }

  @Test
  public void failedAttemptStillContributesRequestBodyBudget()
  {
    TongYiClient.RequestMetricsAccumulator metrics =
      new TongYiClient.RequestMetricsAccumulator();
    metrics.recordAttempt(8_192L, "model-b");

    ModelUsage usage = metrics.snapshot();

    assertEquals(1, usage.getRequestCount());
    assertEquals(0, usage.getTokenRequestCount());
    assertEquals(8_192L, usage.getRequestBytes());
    assertEquals(8_192L, usage.getPeakRequestBytes());
  }

  @Test
  public void laterPartialUsageChunkKeepsAlreadyKnownTokenFields()
  {
    TongYiClient.RequestMetricsAccumulator metrics =
      new TongYiClient.RequestMetricsAccumulator();
    metrics.recordAttempt(1_024L, "model-c");
    metrics.recordProviderUsage(1_000L, -1L, -1L);
    metrics.recordProviderUsage(-1L, 250L, -1L);

    ModelUsage usage = metrics.snapshot();

    assertEquals(1_000L, usage.getPromptTokens());
    assertEquals(250L, usage.getCompletionTokens());
    assertEquals(1_250L, usage.getTotalTokens());
    assertEquals(1, usage.getTokenRequestCount());
  }

  @Test
  public void partialChunkDoesNotOverwriteExplicitTotalTokens()
  {
    TongYiClient.RequestMetricsAccumulator metrics =
      new TongYiClient.RequestMetricsAccumulator();
    metrics.recordAttempt(1_024L, "model-c");
    metrics.recordProviderUsage(-1L, -1L, 1_250L);
    metrics.recordProviderUsage(1_000L, -1L, -1L);

    ModelUsage usage = metrics.snapshot();

    assertEquals(1_000L, usage.getPromptTokens());
    assertEquals(1_250L, usage.getTotalTokens());
  }
}
