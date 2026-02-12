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
    if (oldHistory.size() > currentMaxRounds * 2)
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

    try
    {
      JSONArray array = new JSONArray(historyStr);

      for (int i = 0; i < array.length(); i++)
      {
        JSONObject currentObject =  array.getJSONObject(i);

        list.add(currentObject);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return list;
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
