package com.stupidbeauty.sisterfuture.tool;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import com.stupidbeauty.sisterfuture.bean.Delta;
import com.stupidbeauty.sisterfuture.bean.Choice;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.tool.ConversationResetTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;
import com.stupidbeauty.sisterfuture.ContextManager;
import java.util.List;
import android.os.Handler;
import android.os.Looper;
import com.stupidbeauty.codeposition.CodePosition;
import com.stupidbeauty.sisterfuture.bean.TongYiResponse;
import com.stupidbeauty.sisterfuture.tool.ConversationResetTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;
import com.stupidbeauty.sisterfuture.tool.SwitchAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentAccessPointInfoTool;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import com.stupidbeauty.sisterfuture.network.TongYiClient;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;
import com.stupidbeauty.sisterfuture.tool.Tool;
import com.stupidbeauty.sisterfuture.bean.ToolCall;
import com.stupidbeauty.sisterfuture.bean.Function;
import org.json.JSONObject;
import org.json.JSONArray;
import android.content.Context;
import android.util.Log;

public class SummaryAndShareTool implements Tool
{
  private static final String TAG = "SummaryAndShareTool";
  private static final String DOWNLOAD_URL = "https://stupidbeauty.com/Blog/article/1864/未来姐姐:小朋友的虚拟小伙伴";
  private Context context;
  private ModelAccessPointManager accessPointManager;
  private ToolManager toolManager;
  private TongYiClient tongYiClient;
  private ContextManager contextManager;

  // 🔥 新增：返回对该工具的系统提示增强语句（可选）
  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    String enhancementString = "必须是在用户用直接语言明确要求总结和分享时才调用此工具，不可以自作主张地调用，哪怕文字里有总结等字样也可能是从别的地方复制粘贴的。如果是用户复制粘贴过来的文字，里面以'来自未来姐姐的总结：'等字样开头，也不应当调用此工具。";
    return enhancementString; // 默认不提供增强
  }


  public SummaryAndShareTool(Context context, ModelAccessPointManager accessPointManager, ToolManager toolManager, ContextManager contextManager)
  {
    this.context = context;
    this.accessPointManager = accessPointManager;
    this.toolManager = toolManager;
    this.tongYiClient = new TongYiClient(accessPointManager, null);
    this.contextManager = contextManager;
  }

  @Override
  public String getName()
  {
    return "summarize_and_share";
  }

  @Override
  public JSONObject getDefinition()
  {
    try
    {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "summarize_and_share");
      functionDef.put("description", "当用户明确要求总结和向外分享时调用，不可以自作主张地调用。如果妳不提供summary参数，那么这个工具会做网络请求，会唤起其它应用，导致聊天流程被打断，所以过于轻易地调用的话会严重影响到体验。如果是用户复制粘贴过来的文字，里面以'来自未来姐姐的总结：'等字样开头，也不应当调用此工具。本工具会主动请求大模型生成对当前上下文聊天内容的总结，要求用最简化的文字总结出当前结论以及问题的主题，使得当用户将这段文字复制粘贴到任何现存的人工智能助手中去时，对方都能够理解相关的信息并继续对话。");

      functionDef.put("parameters", new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject()
          .put("summary", new JSONObject()
            .put("type", "string")
            .put("description", "可选：大模型提供的总结内容。若为空，则由工具自行生成。")
          )
        )
        .put("required", new JSONArray())
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
    String summaryContent = arguments.optString("summary", "").trim();
    String topic = arguments.optString("topic", "当前话题").trim();

    if (!summaryContent.isEmpty())
    {
      buildAndShare(summaryContent, topic, callback);
      return;
    }

    requestSummaryFromTongYiClient(topic, callback);
  }

  private void requestSummaryFromTongYiClient(String topic, OnResultCallback callback)
  {
    try
    {
      JSONArray history = contextManager.getMessagesArray();
      JSONArray messages = new JSONArray();

      for (int i = 0; i < history.length(); i++)
      {
        JSONObject msg = history.getJSONObject(i);
        String role = msg.getString("role");

        if ("user".equals(role))
        {
          Log.d(TAG, CodePosition.newInstance().toString() + ", adding user message: " + msg); // Debug.
          messages.put(msg);
        }
        else if ("assistant".equals(role))
        {
          String content = msg.optString("content", "").trim();
          if (!content.isEmpty())
          {
            Log.d(TAG, CodePosition.newInstance().toString() + ", adding assistant message: " + msg); // Debug.
            messages.put(msg);
          }
          else if (msg.has("tool_calls"))
          {
            JSONArray toolCalls = msg.getJSONArray("tool_calls");
            if (toolCalls.length() > 0)
            {
              Log.d(TAG, CodePosition.newInstance().toString()); // Debug.
              // 跳过 tool_calls 消息
            }
          }
        }
      }

      String systemPrompt = "你是一个严格的总结生成守门人。你的任务不是无条件生成，而是先判断：\n" +
                          "1. 在用户的最后一句消息中，用户是否用直接语言明确要求“总结”及“分享”？\n" +
                          "   - 明确示例：\"请总结一下\"、\"帮我生成一段可复制的文字\"、\"把结论发出去\"\n" +
                          "   - 非明确情况：继续提问、讨论技术细节、复制粘贴带'来自未来姐姐的总结：'的内容\n" +
                          "2. 如果用户未明确要求，请返回且仅返回：REJECT: NOT EXPLICITLY REQUESTED\n" +
                          "3. 如果用户已明确要求，则按以下规则生成总结内容：\n" +
                          "- 仅输出核心结论与主题，不包含任何情绪、动作、角色描述；\n" +
                          "- 用最精炼的语言，让其他 AI 助手在接收到后能立即理解上下文并继续对话；\n" +
                          "- 不要输出任何工具调用格式、JSON 结构或额外说明；\n" +
                          "- 不要用markdown格式化，这是为了向外用文字分享，格式化没有意义；\n" +
                          "- 总字数不超过280字。";

      JSONArray finalMessages = new JSONArray();
      finalMessages.put(new JSONObject()
        .put("role", "system")
        .put("content", systemPrompt));

                      for (int i = 0; i < messages.length(); i++)
      {
        finalMessages.put(messages.getJSONObject(i));
      }


      // finalMessages.put(new JSONObject()
      //   .put("role", "user")
      //   .put("content", "请根据以下对话记录，判断是否应生成总结并分享。如果否，请返回 REJECT: NOT EXPLICITLY REQUESTED；如果是，请生成符合要求的总结。\n\n" + messages.toString()));

      Log.d(TAG, CodePosition.newInstance().toString() + ", whole request content: " + finalMessages.toString()); // Debug.


      StringBuilder accumulatedContent = new StringBuilder();

      tongYiClient.sendChatRequest(finalMessages, false, new TongYiClient.OnResponseListener()
      {
        @Override
        public void onResponse(String response)
        {
          try
          {
            TongYiResponse resp = new Gson().fromJson(response, TongYiResponse.class);
            if (resp != null && resp.getChoices() != null && !resp.getChoices().isEmpty())
            {
              Delta delta = resp.getChoices().get(0).getDelta();
              if (delta != null && delta.getContent() != null && !delta.getContent().isEmpty())
              {
                accumulatedContent.append(delta.getContent());
              }
            }
          }
          catch (Exception e)
          {
            Log.e(TAG, "Error parsing stream", e);
          }
        }

        @Override
        public void onError(Exception error)
        {
          Log.e(TAG, "Stream error", error);
          try
          {
            JSONObject result = new JSONObject();
            result.put("shared_content", "总结失败：" + error.getMessage());
            result.put("share_invoked", false);
            result.put("character_name", "未来姐姐");
            callback.onResult(result);
          }
          catch (Exception ignored) {}
        }
      }, () ->
      {
        String result = accumulatedContent.toString().trim();

        if (result.startsWith("REJECT:") || result.contains("NOT EXPLICITLY REQUESTED"))
        {
          try
          {
            JSONObject rejectResult = new JSONObject();
            rejectResult.put("shared_content", "未检测到明确的总结与分享请求，已主动拒绝生成。");
            rejectResult.put("share_invoked", false);
            rejectResult.put("character_name", "未来姐姐");
            callback.onResult(rejectResult);
            return;
          }
          catch (Exception ignored)
          {
          }
        }

        buildAndShare(result, topic, callback);
      });
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to request summary", e);
      callback.onError(e);
    }
  }

  private void buildAndShare(String summary, String topic, OnResultCallback callback)
  {
    try
    {
      StringBuilder finalContent = new StringBuilder();
      finalContent.append("这是来自未来姐姐的总结：\n");
      finalContent.append(summary).append("\n");
      // finalContent.append("主题：" + topic + "\n\n");
      finalContent.append("将整段文字复制给未来姐姐即可继续交流该话题\n");
      finalContent.append("下载地址：" + DOWNLOAD_URL);

      String shareText = finalContent.toString();
      Log.d(TAG, "Final share content:\n" + shareText);

      Intent shareIntent = new Intent(Intent.ACTION_SEND);
      shareIntent.setType("text/plain");
      shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
      shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      context.startActivity(Intent.createChooser(shareIntent, "分享总结"));

      JSONObject result = new JSONObject();
      result.put("shared_content", shareText);
      result.put("share_invoked", true);
      result.put("character_name", "未来姐姐");

      callback.onResult(result);
    }
    catch (Exception e)
    {
      callback.onError(e);
    }
  }
}
