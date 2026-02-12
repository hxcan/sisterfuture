package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import android.util.Log;
import com.stupidbeauty.sisterfuture.ContextManager;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;

public class ConversationResetTool implements Tool
{
  private static final String TAG = "ConversationResetTool";
  private ContextManager contextManager;

  // 🔒 防连续重置标记（防止模型在无历史时反复调用）
  private static volatile boolean JUST_RESET = false;

  // 🔥 单行、无换行、无英文双引号，JSON安全
  public static final String RESET_TOOL_DESCRIPTION =
    "仅当满足以下条件之一时调用：(1)用户明确表示“开始新话题”、“清空上下文”或“忘记之前内容”等类似语义；(2)当前消息与所有历史对话在语义上完全无关且无任何上下文依赖。禁止在首次对话（无历史）时调用；话题自然转换（如从天气聊到穿衣）不得视为新话题；正在聊软件开发相关的事情，接着贴代码，也不得视为新话题；存在模糊时请保留上下文。";

  public static String getFewShotExamples()
  {
    return "请参考以下调用示例：\n" +
          "用户：刚才聊的股票先放一放，现在我想问怎么做红烧肉。\n" +
          "→ 调用 reset_conversation_context\n" +
          "\n" +
          "用户：你好！\n" +
          "→ 不要调用 reset_conversation_context（这是第一条消息）\n" +
          "\n" +
          "用户：忘了之前说的，我们现在来聊聊量子计算。\n" +
          "→ 调用 reset_conversation_context\n" +
          "\n" +
          "用户：今天好冷啊。\n" +
          "→ 不要调用（属于自然话题延续）";
  }

  // 在 ConversationResetTool 类中添加实现
  @Override
  public boolean shouldInclude()
  {
    // ✅ 第一次请求：用户消息 ≤ 1 条 → 不应包含该工具
    List<JSONObject> history = contextManager.getHistory();
    int userMessageCount = 0;

    for (JSONObject msg : history)
    {
      if (msg!=null)
      {
        if ("user".equals(msg.optString("role")))
        {
          userMessageCount++;
        }
      } // if (msg!=null)
    }
    Log.d(TAG, CodePosition.newInstance().toString() + ", user message count: " + userMessageCount + ", history count: " + history.size()); // Debug.

    // 第一次请求：用户消息 ≤ 1 → 不包含 reset 工具
    return userMessageCount > 1;
  }

  public ConversationResetTool(ContextManager contextManager)
  {
    this.contextManager = contextManager;
  }

  @Override
  public String getName()
  {
    return "reset_conversation_context";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject function = new JSONObject();
      function.put("type", "function");

      JSONObject functionDef = new JSONObject();
      functionDef.put("name", getName());
      functionDef.put("description", RESET_TOOL_DESCRIPTION);
      functionDef.put("parameters", new JSONObject()); // 无参数

      function.put("function", functionDef);
      return function;
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to build tool definition", e);
      return new JSONObject();
    }
  }

  @Override
  public JSONObject execute(JSONObject arguments)
  {
    // 🛑 双重防护：如果刚刚重置过，直接返回忽略
    if (JUST_RESET)
    {
      Log.w(TAG, "⚠️ 连续 reset 调用被拦截（防死循环）");
      JSONObject ignoredResponse = new JSONObject();
      try
      {
        ignoredResponse.put("message", "上下文已在近期重置，请勿重复调用。");
      }
      catch (Exception ex)
      {
        Log.e(TAG, "Failed to build ignored response", ex);
      }
      return ignoredResponse;
    }

    try
    {
      List<JSONObject> history = contextManager.getHistory();

      // 仅当有足够历史时才执行重置（至少有一轮完整对话）
      if (history.size() >= 2)
      {
        JSONObject latestUser = null;
        JSONObject latestAssistant = null;

        // 从后往前找最近一轮 user
        for (int i = history.size() - 1; i >= 0; i--)
        {
          JSONObject msg = history.get(i);
          String role = msg.optString("role");
          if ("user".equals(role) && latestUser == null)
          {
            latestUser = msg;
          }
          if (latestUser != null )
          {
            break;
          }
        }

        // 构建新历史：只保留最新一轮
        java.util.List<JSONObject> newHistory = new java.util.ArrayList<>();
        if (latestUser != null) newHistory.add(latestUser);
        // if (latestAssistant != null) newHistory.add(latestAssistant);

        contextManager.replaceHistory(newHistory);
        JUST_RESET = true; // 🔒 标记已重置

        // 🕒 500ms 后自动解除保护
        new Handler
        (
          Looper.getMainLooper()
        ).postDelayed
        (
          () ->
            {
              JUST_RESET = false;
              Log.d(TAG, "🔓 连续重置保护已解除");
            }, 2500
        );

        Log.d(TAG, "🧹 对话上下文已由工具自身重置。");
      }

      // 🔥 关键：返回对模型有指导意义的 tool response
      JSONObject successResponse = new JSONObject();
      successResponse.put("message", "上下文已成功重置。接下来的回复将仅基于用户最新消息生成，请勿再次调用 reset_conversation_context。");
      return successResponse;
    }
    catch (Exception e)
    {
      // 出错时务必清除标记，避免永久锁死
      JUST_RESET = false;
      Log.e(TAG, "Error in tool execution", e);

      JSONObject errorResponse = new JSONObject();
      try
      {
        errorResponse.put("message", "上下文重置失败：" + e.getMessage());
      }
      catch (Exception ex)
      {
        Log.e(TAG, "Failed to build error response", ex);
      }
      return errorResponse;
    }
  }
}
