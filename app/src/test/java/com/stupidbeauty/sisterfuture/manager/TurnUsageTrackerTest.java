package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.bean.ModelUsage;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TurnUsageTrackerTest
{
  @Test
  public void aggregatesRequestsAndKeepsPeakContextPerTurn()
  {
    TurnUsageTracker tracker = new TurnUsageTracker();
    long firstTurn = tracker.startTurn();
    long secondTurn = tracker.startTurn();

    tracker.beginRequest(firstTurn);
    tracker.beginRequest(firstTurn);
    tracker.beginRequest(firstTurn);
    assertTrue(tracker.hasOutstandingRequests(firstTurn));

    tracker.completeRequest(firstTurn,
      ModelUsage.forRequest("model-b", 5_000L, 1_200L, 50L, 1_250L));
    tracker.completeRequest(firstTurn,
      ModelUsage.forRequest("model-a", 2_000L, -1L, -1L, -1L));
    assertTrue(tracker.hasOutstandingRequests(firstTurn));
    tracker.completeRequest(firstTurn,
      ModelUsage.forRequest("model-a", 10_000L, 800L, 200L, 1_000L));
    assertFalse(tracker.hasOutstandingRequests(firstTurn));

    ModelUsage result = tracker.snapshot(firstTurn);
    assertEquals(3, result.getRequestCount());
    assertEquals(2, result.getTokenRequestCount());
    assertEquals(2_250L, result.getTotalTokens());
    assertEquals(2_000L, result.getPromptTokens());
    assertEquals(1_200L, result.getPeakPromptTokens());
    assertEquals(17_000L, result.getRequestBytes());
    assertEquals(10_000L, result.getPeakRequestBytes());
    assertEquals("model-a", result.getModelName());
    assertTrue(result.buildCompactSummary().contains("已知值，2/3 次返回"));

    assertNull(tracker.snapshot(secondTurn));
    tracker.finishTurn(firstTurn);
    assertNull(tracker.snapshot(firstTurn));

    // Late callbacks after a reset must not recreate an already-cleared turn.
    tracker.completeRequest(firstTurn,
      ModelUsage.forRequest("model-a", 1_000L, 10L, 5L, 15L));
    assertNull(tracker.snapshot(firstTurn));
    tracker.beginRequest(firstTurn);
    assertNull(tracker.snapshot(firstTurn));
  }

  @Test
  public void neverEvictsAnOutstandingTurn()
  {
    TurnUsageTracker tracker = new TurnUsageTracker();
    long[] turnIds = new long[9];
    for (int i = 0; i < turnIds.length; i++)
    {
      turnIds[i] = tracker.startTurn();
      tracker.beginRequest(turnIds[i]);
    }

    for (long turnId : turnIds)
    {
      assertTrue(tracker.hasOutstandingRequests(turnId));
    }
  }

  @Test
  public void keepsUnfinishedTurnsWhileTheyWaitBetweenToolHops()
  {
    TurnUsageTracker tracker = new TurnUsageTracker();
    long[] turnIds = new long[9];
    for (int i = 0; i < turnIds.length; i++)
    {
      turnIds[i] = tracker.startTurn();
      tracker.beginRequest(turnIds[i]);
      tracker.completeRequest(turnIds[i],
        ModelUsage.forRequest("model-a", 1_000L + i, 100L, 10L, 110L));
      assertFalse(tracker.hasOutstandingRequests(turnIds[i]));
    }

    // Zero outstanding requests does not mean a turn is finished: an async
    // tool can still be running before it starts the next model request.
    for (long turnId : turnIds)
    {
      assertEquals(110L, tracker.snapshot(turnId).getTotalTokens());
    }
  }
}
