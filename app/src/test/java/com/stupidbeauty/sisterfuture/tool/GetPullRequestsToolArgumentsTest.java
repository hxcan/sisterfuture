package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.sisterfuture.utils.ToolArgumentsParser;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GetPullRequestsToolArgumentsTest {
  private JSONObject valid() throws Exception {
    return new JSONObject("{\"owner\":\"hxcan\",\"repo\":\"sisterfuture\","
        + "\"start_time\":\"2026-09-16T00:00:00Z\",\"end_time\":\"2026-09-17T00:00:00Z\"}");
  }

  @Test public void defaultsAndInputArePreserved() throws Exception {
    JSONObject input = valid();
    String original = input.toString();
    JSONObject parsed = GetPullRequestsTool.parseArguments(input);
    assertEquals("hxcan", parsed.getString("owner"));
    assertEquals("all", parsed.getString("state"));
    assertEquals(30, parsed.getInt("per_page"));
    assertTrue(parsed.getBoolean("include_merged"));
    assertTrue(parsed.getBoolean("save_to_phone"));
    assertEquals("", parsed.getString("token"));
    assertEquals(original, input.toString());
  }

  @Test public void toleratesCaseWhitespaceAndScalarTypes() throws Exception {
    JSONObject input = valid();
    input.remove("owner");
    input.put("OWNER", " hxcan ").put("PER_PAGE", " 50 ")
        .put("INCLUDE_MERGED", "no").put("SAVE_TO_PHONE", 0)
        .put("STATE", " OPEN ").put("TOKEN", " test-token ");
    JSONObject parsed = GetPullRequestsTool.parseArguments(input);
    assertEquals("hxcan", parsed.getString("owner"));
    assertEquals(50, parsed.getInt("per_page"));
    assertFalse(parsed.getBoolean("include_merged"));
    assertFalse(parsed.getBoolean("save_to_phone"));
    assertEquals("open", parsed.getString("state"));
    assertEquals("test-token", parsed.getString("token"));
  }

  @Test public void optionalInvalidValuesFallBack() throws Exception {
    JSONObject parsed = GetPullRequestsTool.parseArguments(valid()
        .put("token", JSONObject.NULL).put("state", new JSONObject())
        .put("per_page", "oops").put("include_merged", new JSONObject()));
    assertEquals("", parsed.getString("token"));
    assertEquals("all", parsed.getString("state"));
    assertEquals(30, parsed.getInt("per_page"));
    assertTrue(parsed.getBoolean("include_merged"));
    for (int size : new int[]{0, -1, 101}) {
      assertEquals(30, GetPullRequestsTool.parseArguments(valid().put("per_page", size)).getInt("per_page"));
    }
  }

  @Test public void requiredInvalidValuesHaveActionableErrors() throws Exception {
    for (String key : new String[]{"owner", "repo", "start_time", "end_time"}) {
      for (Object value : new Object[]{JSONObject.NULL, " ", new JSONObject(), new org.json.JSONArray()}) {
        try {
          GetPullRequestsTool.parseArguments(valid().put(key, value));
          fail("Expected invalid " + key);
        } catch (IllegalArgumentException expected) {
          assertTrue(expected.getMessage().contains(key));
        }
      }
    }
  }

  @Test public void upstreamJsonRecoveryStillWorks() throws Exception {
    String json = valid().toString();
    for (String raw : new String[]{"```json\n" + json + "\n```", JSONObject.quote(json)}) {
      assertEquals("sisterfuture", GetPullRequestsTool.parseArguments(
          ToolArgumentsParser.parseOrEmpty(raw)).getString("repo"));
    }
  }
}
