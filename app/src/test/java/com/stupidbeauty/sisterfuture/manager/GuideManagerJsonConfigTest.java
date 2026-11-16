package com.stupidbeauty.sisterfuture.manager;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 测试 #940963136747：输入 API 的向导支持 JSON 格式自定义第三方 API 配置
 *
 * 只测两个纯函数（isJsonConfig / parseAccessPointConfig）：
 * 不涉及网络、不涉及 UI、不依赖 Android 运行时。
 */
public class GuideManagerJsonConfigTest
{
  // ---------- isJsonConfig ----------

  @Test
  public void recognizesSingleObjectJsonConfig()
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://api.deepseek.com\","
      + "\"endpoint\":\"/chat/completions\",\"modelName\":\"deepseek-chat\"}";
    assertTrue(GuideManager.isJsonConfig(json));
  }

  @Test
  public void recognizesMultiAccessPointJsonConfig()
  {
    String json = "{\"accessPoints\":[{\"apiKey\":\"sk-aaaabbbbccccdddd\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m1\"}]}";
    assertTrue(GuideManager.isJsonConfig(json));
  }

  @Test
  public void rejectsPlainTextApiKey()
  {
    assertFalse(GuideManager.isJsonConfig("sk-abcdefghijklmnopqrstuvwxyz012345"));
    assertFalse(GuideManager.isJsonConfig("cp_abcdefghijklmnopqrstuvwxyz012345"));
  }

  @Test
  public void rejectsNullAndEmptyInput()
  {
    assertFalse(GuideManager.isJsonConfig(null));
    assertFalse(GuideManager.isJsonConfig(""));
    assertFalse(GuideManager.isJsonConfig("   "));
  }

  @Test
  public void rejectsJsonWithoutRequiredFields()
  {
    // 是合法 JSON，但既没有 apiKey 也没有 accessPoints —— 不能当配置处理
    assertFalse(GuideManager.isJsonConfig("{\"name\":\"some name\",\"modelName\":\"m\"}"));
  }

  @Test
  public void rejectsMalformedJson()
  {
    assertFalse(GuideManager.isJsonConfig("{\"apiKey\":\"sk-abcdefghijklmnop\","));
    assertFalse(GuideManager.isJsonConfig("not json at all"));
  }

  @Test
  public void rejectsArrayAtTopLevel()
  {
    assertFalse(GuideManager.isJsonConfig("[{\"apiKey\":\"sk-abcdefghijklmnop\"}]"));
  }

  @Test
  public void toleratesSurroundingWhitespace()
  {
    String json = "  \n {\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\"}  \n ";
    assertTrue(GuideManager.isJsonConfig(json));
  }

  // ---------- parseAccessPointConfig ----------

  @Test
  public void parsesSingleObjectFormat() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://api.deepseek.com\","
      + "\"endpoint\":\"/chat/completions\",\"modelName\":\"deepseek-chat\",\"name\":\"DeepSeek 官方\"}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);

    assertEquals(1, points.size());
    JSONObject point = points.get(0);
    assertEquals("sk-abcdefghijklmnop", point.getString("apiKey"));
    assertEquals("https://api.deepseek.com", point.getString("baseUrl"));
    assertEquals("/chat/completions", point.getString("endpoint"));
    assertEquals("deepseek-chat", point.getString("modelName"));
    assertEquals("DeepSeek 官方", point.getString("name"));
  }

  @Test
  public void parsesMultiAccessPointFormatInOrder() throws JSONException
  {
    String json = "{\"accessPoints\":["
      + "{\"name\":\"A\",\"apiKey\":\"sk-aaaabbbbccccdddd\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m1\"},"
      + "{\"name\":\"B\",\"apiKey\":\"sk-eeeeffffgggghhhh\",\"baseUrl\":\"https://b.example.com\","
      + "\"endpoint\":\"/chat/completions\",\"modelName\":\"m2\"}"
      + "]}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);

    assertEquals(2, points.size());
    assertEquals("A", points.get(0).getString("name"));
    assertEquals("B", points.get(1).getString("name"));
  }

  @Test
  public void defaultsNameToModelNameWhenAbsent() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"my-model\"}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);

    assertEquals("my-model", points.get(0).getString("name"));
  }

  @Test
  public void reportsMissingApiKeyField() throws JSONException
  {
    String json = "{\"baseUrl\":\"https://a.example.com\",\"endpoint\":\"/v1/chat/completions\","
      + "\"modelName\":\"m\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出缺少 apiKey 的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出缺失字段 apiKey，实际：" + e.getMessage(),
        e.getMessage().contains("apiKey"));
    }
  }

  @Test
  public void reportsMissingBaseUrlField() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"endpoint\":\"/v1/chat/completions\","
      + "\"modelName\":\"m\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出缺少 baseUrl 的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出缺失字段 baseUrl，实际：" + e.getMessage(),
        e.getMessage().contains("baseUrl"));
    }
  }

  @Test
  public void reportsMissingEndpointField() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://a.example.com\","
      + "\"modelName\":\"m\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出缺少 endpoint 的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出缺失字段 endpoint，实际：" + e.getMessage(),
        e.getMessage().contains("endpoint"));
    }
  }

  @Test
  public void reportsMissingModelNameField() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出缺少 modelName 的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出缺失字段 modelName，实际：" + e.getMessage(),
        e.getMessage().contains("modelName"));
    }
  }

  @Test
  public void reportsMissingFieldInsideArrayItem() throws JSONException
  {
    String json = "{\"accessPoints\":["
      + "{\"apiKey\":\"sk-aaaabbbbccccdddd\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m1\"},"
      + "{\"apiKey\":\"sk-eeeeffffgggghhhh\",\"baseUrl\":\"https://b.example.com\","
      + "\"modelName\":\"m2\"}"
      + "]}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出第 2 项缺少 endpoint 的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出缺失字段 endpoint，实际：" + e.getMessage(),
        e.getMessage().contains("endpoint"));
      assertTrue("异常信息应指出是第几项，实际：" + e.getMessage(),
        e.getMessage().contains("2"));
    }
  }

  @Test
  public void rejectsInvalidBaseUrl() throws JSONException
  {
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"not a url\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出 baseUrl 非法的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出 baseUrl，实际：" + e.getMessage(),
        e.getMessage().contains("baseUrl"));
    }
  }

  @Test
  public void rejectsTooShortApiKey() throws JSONException
  {
    // "sk-sh" 长度为 6，短于下限 8
    String json = "{\"apiKey\":\"sk-sh\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\"}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出 apiKey 过短的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出 apiKey，实际：" + e.getMessage(),
        e.getMessage().contains("apiKey"));
    }
  }

  @Test
  public void acceptsApiKeyAtExactMinimumLength() throws JSONException
  {
    // "sk-short" 长度恰好为 8，等于下限，应当通过校验
    String json = "{\"apiKey\":\"sk-short\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\"}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);

    assertEquals(1, points.size());
    assertEquals("sk-short", points.get(0).getString("apiKey"));
  }

  @Test
  public void acceptsThirdPartyApiKeyWithoutKnownPrefix() throws JSONException
  {
    // 第三方服务的 key 前缀五花八门，不能套用纯文本那套白名单
    String json = "{\"apiKey\":\"bare-hash-key-0123456789abcdef\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\"}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);

    assertEquals(1, points.size());
    assertEquals("bare-hash-key-0123456789abcdef", points.get(0).getString("apiKey"));
  }

  @Test
  public void rejectsEmptyAccessPointsArray() throws JSONException
  {
    String json = "{\"accessPoints\":[]}";

    try {
      GuideManager.parseAccessPointConfig(json);
      fail("应当抛出 accessPoints 为空的异常");
    } catch (JSONException e) {
      assertTrue("异常信息应指出 accessPoints，实际：" + e.getMessage(),
        e.getMessage().contains("accessPoints"));
    }
  }

  @Test
  public void parsedResultIsUsableForToolArguments() throws JSONException
  {
    // 解析结果应能直接作为 addModelAccessPoint 工具的参数使用
    String json = "{\"apiKey\":\"sk-abcdefghijklmnop\",\"baseUrl\":\"https://a.example.com\","
      + "\"endpoint\":\"/v1/chat/completions\",\"modelName\":\"m\",\"name\":\"N\"}";

    List<JSONObject> points = GuideManager.parseAccessPointConfig(json);
    JSONObject point = points.get(0);

    assertNotNull(point.getString("apiKey"));
    assertNotNull(point.getString("baseUrl"));
    assertNotNull(point.getString("endpoint"));
    assertNotNull(point.getString("modelName"));
    assertNotNull(point.getString("name"));
  }
}
