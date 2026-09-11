package com.stupidbeauty.sisterfuture.utils;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class ToolArgumentsParserTest
{
  @Test
  public void keepsValidArgumentsAndNestedRequestBody()
  {
    JSONObject result = ToolArgumentsParser.parseOrEmpty(
      "{\"method\":\"POST\",\"url\":\"https://example.com\","
        + "\"headers\":{\"Content-Type\":\"application/json\"},"
        + "\"body\":\"{\\\"name\\\":\\\"future\\\"}\"}");

    assertEquals("POST", result.optString("method"));
    assertEquals("application/json",
      result.optJSONObject("headers").optString("Content-Type"));
    assertEquals("{\"name\":\"future\"}", result.optString("body"));
  }

  @Test
  public void recoversMarkdownFencedArguments()
  {
    JSONObject result = ToolArgumentsParser.parseOrEmpty(
      "```json\n{\"method\":\"GET\",\"url\":\"https://example.com\"}\n```");

    assertEquals("GET", result.optString("method"));
    assertEquals("https://example.com", result.optString("url"));
  }

  @Test
  public void recoversObjectSurroundedByModelExplanation()
  {
    JSONObject result = ToolArgumentsParser.parseOrEmpty(
      "调用参数如下： {\"method\":\"GET\",\"url\":\"https://example.com/{id}\"} 请执行");

    assertEquals("GET", result.optString("method"));
    assertEquals("https://example.com/{id}", result.optString("url"));
  }

  @Test
  public void recoversDoubleEncodedArguments()
  {
    JSONObject result = ToolArgumentsParser.parseOrEmpty(
      "\"{\\\"method\\\":\\\"DELETE\\\",\\\"url\\\":\\\"https://example.com/1\\\"}\"");

    assertEquals("DELETE", result.optString("method"));
    assertEquals("https://example.com/1", result.optString("url"));
  }

  @Test
  public void returnsEmptyObjectWhenArgumentsCannotBeRecovered()
  {
    JSONObject result = ToolArgumentsParser.parseOrEmpty("method=GET, url=https://example.com");

    assertNotNull(result);
    assertEquals(0, result.length());
    assertFalse(result.has("method"));
  }
}
