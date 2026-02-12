package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import android.content.Intent;
import android.content.Context;
import android.util.Log; // 新增：用于调试
import java.util.Timer;
import java.util.TimerTask;

public class DelayedReplyTool implements Tool
{
  private static final String TAG = "DelayedReplyTool";
  private Context context;

  public DelayedReplyTool(Context context)
  {
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "delayed_reply";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "delayed_reply");
      functionDef.put("description", "用于测试异步工具调用。该工具会启动一个后台定时器，在指定延迟后自动返回结果，不阻塞主线程，无界面侵入。");

      functionDef.put("parameters", new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
          .put("message", new JSONObject()
            .put("type", "string")
            .put("description", "延迟返回的消息内容")
          )
          .put("delay_seconds", new JSONObject()
            .put("type", "integer")
            .put("description", "延迟秒数，默认3秒")
            .put("default", 3)
          )
        )
        .put("required", new JSONArray().put("message"))
      );

      return new JSONObject()
        .put("type", "function")
        .put("function", functionDef);
    }
    catch (Exception e)
    {
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public boolean isAsync()
  {
    return true;
  }

  @Override
  public void executeAsync(JSONObject arguments, OnResultCallback callback)
  {
    // 提取参数
    String message = arguments.optString("message", "主人等你好久了呢～");
    int delaySeconds = arguments.optInt("delay_seconds", 3);

    // 🔥 使用 Timer 实现真异步（不阻塞主线程）
    Timer timer = new Timer();
    timer.schedule(new TimerTask()
    {
      @Override
      public void run()
      {
        try
        {
          JSONObject result = new JSONObject();
          result.put("reply", message);
          result.put("delay_completed", true);
          result.put("actual_delay_seconds", delaySeconds);

          callback.onResult(result);
        }
        catch (Exception e)
        {
          callback.onError(e);
        }
        finally
        {
          timer.cancel(); // 任务完成后释放资源
        }
      }
    }, delaySeconds * 1000); // 转换为毫秒
  }
}
