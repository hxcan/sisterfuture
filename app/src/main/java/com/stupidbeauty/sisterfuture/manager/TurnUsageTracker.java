package com.stupidbeauty.sisterfuture.manager;

import com.stupidbeauty.sisterfuture.bean.ModelUsage;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps model usage isolated per user turn, including turns whose streams overlap. */
public class TurnUsageTracker
{
  private final AtomicLong turnIdCounter = new AtomicLong(0L);
  private final LinkedHashMap<Long, MutableUsage> usages = new LinkedHashMap<>();

  public synchronized long startTurn()
  {
    long turnId = turnIdCounter.incrementAndGet();
    usages.put(turnId, new MutableUsage());
    return turnId;
  }

  public synchronized void beginRequest(long turnId)
  {
    if (turnId <= 0L) return;
    MutableUsage usage = usages.get(turnId);
    // A late tool callback must not resurrect a turn cleared by a context reset.
    if (usage == null) return;
    usage.outstandingRequests++;
  }

  public synchronized ModelUsage completeRequest(long turnId, ModelUsage requestUsage)
  {
    if (turnId <= 0L) return null;
    MutableUsage usage = usages.get(turnId);
    // A missing turn means the conversation was reset while this request was in flight.
    // Do not resurrect cleared usage state from a late callback.
    if (usage == null) return null;
    if (requestUsage != null) usage.add(requestUsage);
    if (usage.outstandingRequests > 0) usage.outstandingRequests--;
    return usage.snapshot();
  }

  public synchronized boolean hasOutstandingRequests(long turnId)
  {
    MutableUsage usage = usages.get(turnId);
    return usage != null && usage.outstandingRequests > 0;
  }

  public synchronized ModelUsage snapshot(long turnId)
  {
    MutableUsage usage = usages.get(turnId);
    return usage == null ? null : usage.snapshot();
  }

  public synchronized void finishTurn(long turnId)
  {
    usages.remove(turnId);
  }

  public synchronized void clear()
  {
    usages.clear();
  }

  private static class MutableUsage
  {
    long promptTokens;
    long completionTokens;
    long totalTokens;
    long peakPromptTokens;
    long requestBytes;
    long peakRequestBytes;
    int requestCount;
    int tokenRequestCount;
    String modelName;
    long contextWindowTokens;
    int outstandingRequests;

    void add(ModelUsage usage)
    {
      promptTokens += usage.getPromptTokens();
      completionTokens += usage.getCompletionTokens();
      totalTokens += usage.getTotalTokens();
      peakPromptTokens = Math.max(peakPromptTokens, usage.getPeakPromptTokens());
      requestBytes += usage.getRequestBytes();
      peakRequestBytes = Math.max(peakRequestBytes, usage.getPeakRequestBytes());
      requestCount += usage.getRequestCount();
      tokenRequestCount += usage.getTokenRequestCount();
      if (usage.getModelName() != null && !usage.getModelName().isEmpty())
      {
        modelName = usage.getModelName();
      }
      if (usage.getContextWindowTokens() > 0L)
      {
        contextWindowTokens = usage.getContextWindowTokens();
      }
    }

    ModelUsage snapshot()
    {
      ModelUsage result = new ModelUsage(
        promptTokens,
        completionTokens,
        totalTokens,
        peakPromptTokens,
        requestBytes,
        peakRequestBytes,
        requestCount,
        tokenRequestCount,
        modelName,
        contextWindowTokens
      );
      return result.hasAnyUsage() ? result : null;
    }
  }
}
