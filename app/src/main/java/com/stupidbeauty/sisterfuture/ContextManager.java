package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.FileDescriptor;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
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
    
    // ✅ #4844 修复：在构造函数中执行一次初始清理（仅在启动时）
    cleanupInvalidToolCallsOnStartup();
  }

  // ✅ #4844 新增：只在启动时调用一次的清理方法
  private void cleanupInvalidToolCallsOnStartup()
  {
    // 直接从文件加载历史，不调用 getHistory() 避免递归
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");
    
    if (historyStr.isEmpty())
    {
      Log.d(TAG, "🧹 [启动清理] 历史为空，跳过清理");
      return;
    }
    
    List<JSONObject> history = new ArrayList<>();
    int invalidCount = 0;
    
    try
    {
      JSONArray array = new JSONArray(historyStr);
      Log.i(TAG, "🧹 [启动清理] 开始清理历史记录，原始消息数：" + array.length());
      
      // 第一步：过滤非法 JSON 的 tool_call
      for (int i = 0; i < array.length(); i++)
      {
        JSONObject currentObject = array.getJSONObject(i);
        if (isValidToolCallMessage(currentObject))
        {
          history.add(currentObject);
        }
        else
        {
          invalidCount++;
          Log.w(TAG, "⚠️ [启动清理] 跳过非法 JSON 的消息 (索引：" + i + ")");
        }
      }
      
      // 第二步：规范化配对关系（清理 orphan 的 tool 回复）← 这一步不能少！
      history = normalizeToolCallMessages(history);
      
      // 第三步：如果有清理，保存结果
      if (invalidCount > 0 || history.size() < array.length())
      {
        Log.w(TAG, "🧹 [启动清理] 共清理 " + invalidCount + " 条非法 JSON 的历史消息");
        saveHistory(history);
      }
      else
      {
        Log.d(TAG, "✅ [启动清理] 历史干净，无需清理");
      }
    }
    catch (Exception e)
    {
      Log.e(TAG, "[启动清理] 出错：" + e.getMessage(), e);
    }
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
    Log.i(TAG, "🔧 [addToolMessage] 开始添加工具回复 - toolCallId=" + toolCallId + ", toolName=" + toolName);
    
    List<JSONObject> history = getHistory();
    Log.i(TAG, "🔧 [addToolMessage] 当前历史消息数：" + history.size());
    
    // 🔍 #4855 调试：输出当前历史中的所有消息
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String toolCallIds = msg.has("tool_calls") ? String.valueOf(msg.optJSONArray("tool_calls").length()) + " 个" : "无";
      String toolId = msg.optString("tool_call_id", "无");
      Log.i(TAG, "📋 [addToolMessage] 历史[" + i + "] role=" + role + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);
    }

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
    Log.i(TAG, "🔧 [addToolMessage] 已添加 tool 消息，当前消息数：" + history.size());

    history = removeOldHistoryEntries(history);
    
    // 🔍 #4855 调试：normalize 前后对比
    Log.i(TAG, "🔧 [addToolMessage] normalize 前消息数：" + history.size());
    history = normalizeToolCallMessages(history); // NOrmalize tool calls messages
    Log.i(TAG, "🔧 [addToolMessage] normalize 后消息数：" + history.size());
    
    // 🔍 #4855 调试：输出 normalize 后的历史
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String toolCallIds = msg.has("tool_calls") ? String.valueOf(msg.optJSONArray("tool_calls").length()) + " 个" : "无";
      String toolId = msg.optString("tool_call_id", "无");
      Log.i(TAG, "📋 [addToolMessage] normalize 后历史[" + i + "] role=" + role + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);
    }

    saveHistory(history);
    Log.i(TAG, "🔧 [addToolMessage] 已保存历史");
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

  // ✅ #4844 修复：getHistory 只做加载，不做任何清理
  public List<JSONObject> getHistory()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");

    if (historyStr.isEmpty())
    {
      return new ArrayList<>();
    }

    Log.d(TAG, CodePosition.newInstance().toString() + ", history string: " + historyStr); // Debug.
    List<JSONObject> list = new ArrayList<>();

    try
    {
      JSONArray array = new JSONArray(historyStr);

      // 🔍 #4846 新增：入口日志
      Log.d(TAG, "📋 [getHistory] 加载历史记录，消息数：" + array.length());

      // ✅ #4844 修复：直接加载，不做任何清理或修改
      for (int i = 0; i < array.length(); i++)
      {
        list.add(array.getJSONObject(i));
      }
      
      // 🔍 #4855 调试：输出加载的历史消息
      for (int i = 0; i < list.size(); i++)
      {
        JSONObject msg = list.get(i);
        String role = msg.optString("role", "unknown");
        String toolCallIds = msg.has("tool_calls") ? String.valueOf(msg.optJSONArray("tool_calls").length()) + " 个" : "无";
        String toolId = msg.optString("tool_call_id", "无");
        Log.d(TAG, "📋 [getHistory] 历史[" + i + "] role=" + role + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);
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
            
            // 🔍 #4846 修复：使用 JSONTokener 进行严格验证
            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();
              
              // 检查是否还有剩余内容（如 "{}{}" 的情况）
              if (tokener.more())
              {
                Log.w(TAG, "❌ 非法 JSON: 存在额外数据 - \"" + argumentsStr + "\"");
                return false;
              }
              
              // 验证是否为 JSONObject 类型
              if (!(parsed instanceof JSONObject))
              {
                Log.w(TAG, "❌ 非法 JSON: 不是 JSON 对象，类型=" + parsed.getClass().getName());
                return false;
              }
            }
            catch (JSONException e)
            {
              Log.w(TAG, "❌ 非法 JSON: " + e.getMessage());
              return false; // 非法消息，过滤掉
            }
          }
        }
      }
      return true; // 所有 arguments 都是合法 JSON
    }
    catch (JSONException e)
    {
      Log.w(TAG, "❌ 检查过程出错：" + e.getMessage());
      return false; // 非法消息，过滤掉
    }
  }

  private List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  // private void normalizeToolCallMessages()
  {
    Log.i(TAG, "🔧 [normalizeToolCallMessages] 开始规范化，输入消息数：" + oldHistory.size());
    
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

        // 🔍 #4855 调试：输出当前处理的消息
        String toolCallIds = currentObject.has("tool_calls") ? String.valueOf(currentObject.optJSONArray("tool_calls").length()) + " 个" : "无";
        String toolId = currentObject.optString("tool_call_id", "无");
        Log.d(TAG, "🔧 [normalize] 处理[" + i + "] role=" + roleString + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);



        if (roleString.equals("assistant")) // Assistant message
        {
          if (currentObject.has("tool_calls")) // Has tool calls
          {
            // 🔍 #4855 调试：获取 tool_call id
            String toolCallId = "未知";
            try
            {
              JSONArray toolCalls = currentObject.getJSONArray("tool_calls");
              if (toolCalls.length() > 0)
              {
                JSONObject firstToolCall = toolCalls.getJSONObject(0);
                toolCallId = firstToolCall.optString("id", "未知");
              }
            }
            catch (Exception e) {}
            
            Log.d(TAG, "🔧 [normalize] 发现 assistant(tool_calls), id=" + toolCallId);
            pendingToolCallsObject = currentObject; // Remmber pending tool call object.
            continue; // Not adding this object. We has to wait for the next message.
          } // if (currentObject.has("tool_calls")) // Has tool calls
          // else // No tool calls.
        } // if (roleString.equals("assistant")) // Assistant message
        else if (roleString.equals("tool")) // tool message
        {
          // 🔍 #4855 调试：获取 tool_call_id
          String answeringtoolCAllId = currentObject.optString("tool_call_id", "无");
          Log.d(TAG, "🔧 [normalize] 发现 tool 回复，tool_call_id=" + answeringtoolCAllId);
          
          // Add the previous pending tool calls message.
          if (pendingToolCallsObject!=null)
          {
            JSONArray toolCALLSArray = pendingToolCallsObject.getJSONArray("tool_calls");
            JSONObject toolCallsFirst = toolCALLSArray.getJSONObject(0);
            String toolCAllsId = toolCallsFirst.getString("id"); // Ge the id.

            Log.d(TAG, "🔧 [normalize] 配对检查：pending id=" + toolCAllsId + ", tool id=" + answeringtoolCAllId);

            if (toolCAllsId.equals(answeringtoolCAllId)) // Matching messages.
            {
              Log.d(TAG, "🔧 [normalize] ✅ 配对成功，添加 assistant(tool_calls)");
              list.add(pendingToolCallsObject);
              pendingToolCallsObject = null;
            } // if (toolCAllsId.equals(answeringtoolCAllId)) // Matching messages.
            else // Not matching.
            {
              Log.w(TAG, "🔧 [normalize] ❌ 配对失败，清空 pending");
              pendingToolCallsObject = null;
              continue;
            } //else // Not matching.

          } // if (pendingToolCallsObject!=null)
          else // NO pending tool calls message
          {
            Log.w(TAG, "🔧 [normalize] ❌ orphan tool 回复，跳过");
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
    
    Log.i(TAG, "🔧 [normalizeToolCallMessages] 规范化完成，输出消息数：" + list.size());
    return list;
  }

  private void saveHistory(List<JSONObject> history)
  {
    JSONArray historyArray = new JSONArray(history);
    sharedPreferences.edit()
        .putString(KEY_HISTORY, historyArray.toString())
        .putInt("current_max_rounds", currentMaxRounds)
        .apply();
    Log.d(TAG, "💾 [saveHistory] 已保存历史，消息数：" + history.size());
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
