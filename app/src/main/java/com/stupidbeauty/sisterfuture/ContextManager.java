package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import butterknife.OnClick;
import com.iflytek.cloud.SpeechRecognizer;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

public class ContextManager
{
  private static final String TAG = "ContextManager";
  private static final String PREF_NAME = "context_manager";
  private static final String KEY_HISTORY = "history";
  private static final int INITIAL_MAX_ROUNDS = 5;
  private SharedPreferences sharedPreferences;
  private int currentMaxRounds = INITIAL_MAX_ROUNDS;

  public ContextManager(Context context)
  {
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    currentMaxRounds = sharedPreferences.getInt("current_max_rounds", INITIAL_MAX_ROUNDS);
  }

  private List<JSONObject> removeOldHistoryEntries(List<JSONObject> oldHistory)
  {
    List<JSONObject> history = oldHistory;

    // 保持历史长度限制（和 addUser/Assistant 一致）
    if (oldHistory.size() > currentMaxRounds *2)
    {
      history = new ArrayList<>(history.subList(history.size() - (currentMaxRounds * 2), history.size()));
    }

    try
    {
      String firstRole = history.get(0).getString("role"); // Get the first role.
      if (firstRole.equals("tool")) // It is a tool message
      {
        history = new ArrayList<>(history.subList(1, history.size()));
      } // if (firstRole.equals("tool")) // It is a tool message
    }
    catch(JSONException e)
    {
      e.printStackTrace();
    }

    return history;
  } // private List<JSONObject> removeOldHistoryEntries(List<JSONObject> oldHistory)
  // ContextManager.java —— 新增方法
  public void addToolMessage(String toolCallId, String toolName, String content)
  {
    List<JSONObject> history = getHistory();

    JSONObject toolMessage = new JSONObject();
    try
    {
      toolMessage.put("role", "tool");
      toolMessage.put("tool_call_id", toolCallId);
      toolMessage.put("name", toolName);
      toolMessage.put("content", content); // 必须是字符串！
    }
    catch (Exception e)
    {
      Log.e(TAG, "Failed to create tool message", e);
      return;
    }

    history.add(toolMessage);

    history = removeOldHistoryEntries(history);
    history = normalizeToolCallMessages(history); // NOrmalize tool calls messages

    saveHistory(history);
  }

  public void addUserMessage(String message)
  {
    addMessage("user", message);
    // history = normalizeToolCallMessages(history); // NOrmalize tool calls messages
    
    List<JSONObject> history = getHistory();

    history = normalizeToolCallMessages(history); // NOrmalize tool calls messages

    saveHistory(history);
  }

  public void addAssistantMessage(String message)
  {
    addMessage("assistant", message);
  }

  // ✅ 新增：直接将原始 JSONObject 追加到历史中，保持长度控制与持久化逻辑
  public void addRawMessage(JSONObject message)
  {
    if (message == null)
    {
      return;
    }

    // 在这里添加对空 tool_calls 的检查
    try
    {
      if (message.has("tool_calls"))
      {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls.length() == 0)
        {
          Log.w(TAG, "检测到空的 tool_calls 消息，已过滤");
          return; // 直接返回，不添加到历史中
        }

        // 🔍 #4841 新增：校验每个 tool_call 的 arguments 字段是否为合法 JSON
        for (int i = 0; i < toolCalls.length(); i++)
        {
          JSONObject toolCall = toolCalls.getJSONObject(i);
          if (toolCall.has("function"))
          {
            JSONObject function = toolCall.getJSONObject("function");
            if (function.has("arguments"))
            {
              String argumentsStr = function.getString("arguments");
              try
              {
                // 尝试解析为 JSON 对象，验证合法性
                new JSONObject(argumentsStr);
                // ✅ 合法 JSON，保留
              }
              catch (JSONException e)
              {
                // ❌ 非法 JSON，记录警告并跳过此条消息
                Log.w(TAG, "⚠️ 跳过非法 JSON 的 tool_call arguments: " + argumentsStr);
                Log.w(TAG, "   tool_call name: " + function.optString("name", "unknown"));
                return; // 直接返回，不将此 assistant 消息添加到历史
              }
            }
          }
        }
      }
    }
    catch (JSONException e)
    {
      Log.e(TAG, "检查 tool_calls 时出错", e);
    }

    List<JSONObject> history = getHistory();
    history.add(message);

    history = removeOldHistoryEntries(history);
    saveHistory(history);
  }

  // ✅ 重构 addMessage：使用 addRawMessage 实现核心逻辑
  private void addMessage(String role, String content)
  {
    JSONObject message = createMessage(role, content);
    addRawMessage(message);
  }

  public JSONArray getMessagesArray()
  {
    List<JSONObject> history = getHistory();
    return new JSONArray(history);
  }

  // ✅ 修复：改为 public，供 ConversationResetTool 调用
  public List<JSONObject> getHistory()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");

    if (historyStr.isEmpty())
    {
      return new ArrayList<>();
    }

    Log.d(TAG, CodePosition.newInstance().toString() + ", history string: " + historyStr); // Debug.
    List<JSONObject> list = new ArrayList<>();
    int invalidCount = 0; // 🔍 #4844 统计非法消息数量

    try
    {
      JSONArray array = new JSONArray(historyStr);

      // 🔍 #4844 第一步：过滤掉非法 JSON 的 tool_call 消息
      for (int i = 0; i < array.length(); i++)
      {
        JSONObject currentObject = array.getJSONObject(i);

        // 校验并过滤非法 JSON 的 tool_calls
        if (isValidToolCallMessage(currentObject))
        {
          list.add(currentObject);
        }
        else
        {
          Log.w(TAG, "⚠️ 跳过历史中非法 JSON 的消息 (索引：" + i + ")");
          invalidCount++;
        }
      }

      // 🔍 #4844 第二步：调用 normalizeToolCallMessages 清理 orphan 的 tool 回复消息
      list = normalizeToolCallMessages(list);

      // 🔍 #4844 如果有非法消息被过滤，保存清理后的历史
      if (invalidCount > 0)
      {
        Log.w(TAG, "🧹 共清理 " + invalidCount + " 条非法 JSON 的历史消息");
        saveHistory(list); // 持久化清理结果
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return list;
  }

  // 🔍 #4844 新增：校验 tool_call 消息的 arguments 是否为合法 JSON
  private boolean isValidToolCallMessage(JSONObject message)
  {
    try
    {
      if (!message.has("tool_calls"))
      {
        return true; // 非 tool_call 消息，直接通过
      }

      JSONArray toolCalls = message.getJSONArray("tool_calls");
      for (int i = 0; i < toolCalls.length(); i++)
      {
        JSONObject toolCall = toolCalls.getJSONObject(i);
        if (toolCall.has("function"))
        {
          JSONObject function = toolCall.getJSONObject("function");
          if (function.has("arguments"))
          {
            String argumentsStr = function.getString("arguments");
            // 尝试解析，失败则抛出异常
            new JSONObject(argumentsStr);
          }
        }
      }
      return true; // 所有 arguments 都是合法 JSON
    }
    catch (JSONException e)
    {
      Log.w(TAG, "检测到非法 JSON 的 tool_call: " + e.getMessage());
      return false; // 非法消息，过滤掉
    }
  }

  private List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  // private void normalizeToolCallMessages()
  {
    List<JSONObject> history = oldHistory;
    // history.add(message);


    // saveHistory(history);



    List<JSONObject> list = new ArrayList<>();

    try
    {
      // JSONArray array = new JSONArray(historyStr);
      JSONObject pendingToolCallsObject = null;

      for (int i = 0; i < history.size(); i++)
      {
        JSONObject currentObject =  history.get(i);
        String roleString = currentObject.getString("role"); // Get the role



        if (roleString.equals("assistant")) // Assistant message
        {
          if (currentObject.has("tool_calls")) // Has tool calls
          {
            pendingToolCallsObject = currentObject; // Remmber pending tool call object.
            continue; // Not adding this object. We has to wait for the next message.
          } // if (currentObject.has("tool_calls")) // Has tool calls
          // else // No tool calls.
        } // if (roleString.equals("assistant")) // Assistant message
        else if (roleString.equals("tool")) // tool message
        {
          // Add the previous pending tool calls message.
          if (pendingToolCallsObject!=null)
          {
            JSONArray toolCALLSArray = pendingToolCallsObject.getJSONArray("tool_calls");
            JSONObject toolCallsFirst = toolCALLSArray.getJSONObject(0);
            String toolCAllsId = toolCallsFirst.getString("id"); // Ge the id.

            String answeringtoolCAllId = currentObject.optString("tool_call_id");

            if (toolCAllsId.equals(answeringtoolCAllId)) // Matching messages.
            {
              list.add(pendingToolCallsObject);
              pendingToolCallsObject = null;
            } // if (toolCAllsId.equals(answeringtoolCAllId)) // Matching messages.
            else // Not matching.
            {
              pendingToolCallsObject = null;
              continue;
            } //else // Not matching.

          } // if (pendingToolCallsObject!=null)
          else // NO pending tool calls message
          {
            continue;
          }
        } // else if (roleString.equals("tool")) // tool message
        else // user mesage
        {
        } // else // user mesage

        list.add(currentObject);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return list;
  }

  private void saveHistory(List<JSONObject> history)
  {
    JSONArray historyArray = new JSONArray(history);
    sharedPreferences.edit()
        .putString(KEY_HISTORY, historyArray.toString())
        .putInt("current_max_rounds", currentMaxRounds)
        .apply();
  }

  private JSONObject createMessage(String role, String content)
  {
    JSONObject msg = new JSONObject();
    try
    {
      msg.put("role", role);
      msg.put("content", content);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return msg;
  }

  public void increaseMaxRounds()
  {
    if (currentMaxRounds < Integer.MAX_VALUE)
    {
      currentMaxRounds++;
      saveHistory(getHistory());
    }
    Log.i(TAG, "increase max rounds to: " + currentMaxRounds);
  }

  public void decreaseMaxRounds()
  {
    Log.i(TAG, "max rounds before decrease: " + currentMaxRounds);

    List<JSONObject> history = getHistory();
    int idealMaxRounds = history.size() /2 -1 ;

    if (idealMaxRounds > INITIAL_MAX_ROUNDS)
    {
      currentMaxRounds = idealMaxRounds;
      
      // ✅ #4829 新增：立即清理超出最新范围的历史旧消息
      history = removeOldHistoryEntries(history);
      
      saveHistory(history);
    }
    Log.i(TAG, "decrease max rounds to: " + currentMaxRounds);
  }

  // ✅ 新增：直接替换整个历史（用于重置上下文）
  public void replaceHistory(List<JSONObject> newHistory)
  {
    if (newHistory.size() > currentMaxRounds * 2)
    {
      newHistory = new ArrayList<>(newHistory.subList(newHistory.size() - (currentMaxRounds * 2), newHistory.size()));
    }
    saveHistory(newHistory);
  }
}