package com.stupidbeauty.sisterfuture.bean;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Local-only model usage metadata. A value may describe one request or the
 * aggregate of all model requests made for one user turn.
 */
public class ModelUsage
{
  public static final String LOCAL_METADATA_KEY = "_local_usage";

  private final long promptTokens;
  private final long completionTokens;
  private final long totalTokens;
  private final long peakPromptTokens;
  private final long requestBytes;
  private final long peakRequestBytes;
  private final int requestCount;
  private final int tokenRequestCount;
  private final String modelName;
  private final long contextWindowTokens;

  public ModelUsage(long promptTokens, long completionTokens, long totalTokens,
                    long peakPromptTokens, long requestBytes, long peakRequestBytes,
                    int requestCount, int tokenRequestCount, String modelName,
                    long contextWindowTokens)
  {
    this.promptTokens = Math.max(0L, promptTokens);
    this.completionTokens = Math.max(0L, completionTokens);
    this.totalTokens = Math.max(0L, totalTokens);
    this.peakPromptTokens = Math.max(0L, peakPromptTokens);
    this.requestBytes = Math.max(0L, requestBytes);
    this.peakRequestBytes = Math.max(0L, peakRequestBytes);
    this.requestCount = Math.max(0, requestCount);
    this.tokenRequestCount = Math.max(0, tokenRequestCount);
    this.modelName = modelName;
    this.contextWindowTokens = Math.max(0L, contextWindowTokens);
  }

  public static ModelUsage forRequest(String modelName, long requestBytes,
                                      long promptTokens, long completionTokens,
                                      long totalTokens)
  {
    boolean hasTokenUsage = promptTokens >= 0L || completionTokens >= 0L || totalTokens >= 0L;
    long normalizedPrompt = Math.max(0L, promptTokens);
    long normalizedCompletion = Math.max(0L, completionTokens);
    long normalizedTotal = totalTokens >= 0L
      ? totalTokens
      : normalizedPrompt + normalizedCompletion;

    return new ModelUsage(
      normalizedPrompt,
      normalizedCompletion,
      normalizedTotal,
      normalizedPrompt,
      requestBytes,
      requestBytes,
      1,
      hasTokenUsage ? 1 : 0,
      modelName,
      0L
    );
  }

  public static ModelUsage fromJson(JSONObject json)
  {
    if (json == null) return null;

    ModelUsage usage = new ModelUsage(
      json.optLong("prompt_tokens", 0L),
      json.optLong("completion_tokens", 0L),
      json.optLong("total_tokens", 0L),
      json.optLong("peak_prompt_tokens", 0L),
      json.optLong("request_bytes", 0L),
      json.optLong("peak_request_bytes", 0L),
      json.optInt("request_count", 0),
      json.optInt("token_request_count", 0),
      json.optString("model", null),
      json.optLong("context_window_tokens", 0L)
    );

    return usage.hasAnyUsage() ? usage : null;
  }

  public JSONObject toJson()
  {
    JSONObject json = new JSONObject();
    try
    {
      json.put("prompt_tokens", promptTokens);
      json.put("completion_tokens", completionTokens);
      json.put("total_tokens", totalTokens);
      json.put("peak_prompt_tokens", peakPromptTokens);
      json.put("request_bytes", requestBytes);
      json.put("peak_request_bytes", peakRequestBytes);
      json.put("request_count", requestCount);
      json.put("token_request_count", tokenRequestCount);
      if (modelName != null && !modelName.isEmpty()) json.put("model", modelName);
      if (contextWindowTokens > 0L) json.put("context_window_tokens", contextWindowTokens);
    }
    catch (JSONException ignored)
    {
    }
    return json;
  }

  public boolean hasAnyUsage()
  {
    return requestCount > 0 || requestBytes > 0L || tokenRequestCount > 0;
  }

  public boolean hasTokenUsage()
  {
    return tokenRequestCount > 0;
  }

  public long getPromptTokens()
  {
    return promptTokens;
  }

  public long getCompletionTokens()
  {
    return completionTokens;
  }

  public long getTotalTokens()
  {
    return totalTokens;
  }

  public long getPeakPromptTokens()
  {
    return peakPromptTokens;
  }

  public long getRequestBytes()
  {
    return requestBytes;
  }

  public long getPeakRequestBytes()
  {
    return peakRequestBytes;
  }

  public int getRequestCount()
  {
    return requestCount;
  }

  public int getTokenRequestCount()
  {
    return tokenRequestCount;
  }

  public String getModelName()
  {
    return modelName;
  }

  public long getContextWindowTokens()
  {
    return contextWindowTokens;
  }

  public String buildCompactSummary()
  {
    StringBuilder result = new StringBuilder("模型用量 ");
    if (hasTokenUsage())
    {
      result.append(formatCount(totalTokens)).append(" tokens");
      if (tokenRequestCount < requestCount)
      {
        result.append("（已知值，").append(tokenRequestCount).append("/")
          .append(requestCount).append(" 次返回）");
      }
      if (peakPromptTokens > 0L)
      {
        result.append(" · 上下文 ").append(formatCount(peakPromptTokens));
      }
      if (peakRequestBytes > 0L)
      {
        result.append(" · 请求体峰值 ").append(formatBytes(peakRequestBytes));
      }
    }
    else
    {
      result.append("请求体峰值 ").append(formatBytes(peakRequestBytes));
      result.append(" · token 未返回");
    }

    if (requestCount > 1)
    {
      result.append(" · ").append(requestCount).append(" 次请求");
    }
    return result.toString();
  }

  private static String formatCount(long value)
  {
    if (value >= 1_000_000L)
    {
      return String.format(Locale.US, "%.1fM", value / 1_000_000.0);
    }
    if (value >= 1_000L)
    {
      return String.format(Locale.US, "%.1fk", value / 1_000.0);
    }
    return Long.toString(value);
  }

  private static String formatBytes(long value)
  {
    if (value >= 1024L * 1024L)
    {
      return String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024.0));
    }
    if (value >= 1024L)
    {
      return String.format(Locale.US, "%.1f KB", value / 1024.0);
    }
    return value + " B";
  }
}
