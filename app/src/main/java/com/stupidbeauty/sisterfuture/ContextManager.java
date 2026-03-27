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
import com.stupidbeauty.sisterfuture.utils.FileLogger;

public class ContextManager
{
  private static final String TAG = "ContextManager";
  private static final String PREF_NAME = "context_manager";
  private static final String KEY_HISTORY = "history";
  private static final int INITIAL_MAX_ROUNDS = 5;
  private SharedPreferences sharedPreferences;
  private int currentMaxRounds = INITIAL_MAX_ROUNDS;
  private int MAX_ARGUMENTS_STR_LENGTH = 226810;

  public ContextManager(Context context)
  {
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    currentMaxRounds = sharedPreferences.getInt("current_max_rounds", INITIAL_MAX_ROUNDS);
    
    cleanupInvalidToolCallsOnStartup();
    FileLogger.d(TAG, "ContextManager init, currentMaxRounds=" + currentMaxRounds);
  }

  private boolean inDebugMessageIndexRange(int i)
  {
    int rangeMaximal = 1890;
    int rangeMinimal= 0;

    // return ((i>= rangeMinimal) && (i<=rangeMaximal)    );
    return true;
  } // private boolean inDebugMessageIndexRange(index i)

  private void cleanupInvalidToolCallsOnStartup()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");
    
    if (historyStr.isEmpty())
    {
      FileLogger.d(TAG, "[Startup cleanup] History empty, skip");
      return;
    }
    
    List<JSONObject> history = new ArrayList<>();
    int invalidCount = 0;
    int blankAssistantCount = 0;
    
    try
    {
      JSONArray array = new JSONArray(historyStr);
      FileLogger.i(TAG, "[Startup cleanup] Start, original count: " + array.length());
      
      for (int i = 0; i < array.length(); i++)
      {
        JSONObject currentObject = array.getJSONObject(i);
        
        // #4962 新增：过滤空白 assistant 消息
        String role = currentObject.optString("role", "");
        String content = currentObject.optString("content", "");
        boolean hasToolCalls = currentObject.has("tool_calls");
        
        if ("assistant".equals(role) && content.isEmpty() && !hasToolCalls)
        {
          blankAssistantCount++;
          continue;
        }

        if ((!(inDebugMessageIndexRange(i))) && (hasToolCalls)) // binary search to find the message that caused the problem. Only keep the messages with index in debug range
        {
          FileLogger.w(TAG, "Skipping message with index not in binary search range: " + i);

          continue;
        } // if !(inDebugMessageIndexRange(i)) // binary search to find the message that caused the problem. Only keep the messages with index in debug range
        
        // 原有逻辑：验证 tool_call 的 JSON 有效性
        if (isValidToolCallMessage(currentObject))
        {
          history.add(currentObject);
        }
        else
        {
          invalidCount++;
          FileLogger.w(TAG, "[Startup cleanup] Skip invalid JSON at index: " + i);
        }
      }
      
      history = normalizeToolCallMessages(history);
      
      if (invalidCount > 0 || blankAssistantCount > 0 || history.size() < array.length())
      {
        FileLogger.w(TAG, "[Startup cleanup] Removed " + invalidCount + " invalid messages, " + blankAssistantCount + " blank assistant messages");
        saveHistory(history);
      }
      else
      {
        FileLogger.d(TAG, "[Startup cleanup] History clean, no changes");
      }
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "[Startup cleanup] Error: " + e.getMessage(), e);
    }
  }

  private List<JSONObject> removeOldHistoryEntries(List<JSONObject> oldHistory)
  {
    List<JSONObject> history = oldHistory;

    if (oldHistory.size() > currentMaxRounds *2)
    {
      history = new ArrayList<>(history.subList(history.size() - (currentMaxRounds * 2), history.size()));
    }

    try
    {
      String firstRole = history.get(0).getString("role");
      if (firstRole.equals("tool"))
      {
        history = new ArrayList<>(history.subList(1, history.size()));
      }
    }
    catch(JSONException e)
    {
      e.printStackTrace();
    }

    return history;
  }
  
  public void addToolMessage(String toolCallId, String toolName, String content)
  {
    FileLogger.i(TAG, "#4935 [addToolMessage] toolCallId=" + toolCallId + ", toolName=" + toolName);
    
    List<JSONObject> history = getHistory();
    FileLogger.i(TAG, "[addToolMessage] Current history count: " + history.size());

    JSONObject toolMessage = new JSONObject();
    try
    {
      toolMessage.put("role", "tool");
      toolMessage.put("tool_call_id", toolCallId);
      toolMessage.put("name", toolName);
      toolMessage.put("content", content);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "Failed to create tool message", e);
      return;
    }

    history.add(toolMessage);
    FileLogger.i(TAG, "[addToolMessage] Added tool message, count: " + history.size());

    history = removeOldHistoryEntries(history);
    
    FileLogger.i(TAG, "[addToolMessage] Before normalize: " + history.size());
    history = normalizeToolCallMessages(history);
    FileLogger.i(TAG, "[addToolMessage] After normalize: " + history.size());

    saveHistory(history);
    FileLogger.i(TAG, "[addToolMessage done] History saved");
  }

  public void addUserMessage(String message)
  {
    addMessage("user", message);
    
    List<JSONObject> history = getHistory();
    history = normalizeToolCallMessages(history);
    saveHistory(history);
  }

  public void addAssistantMessage(String message)
  {
    addMessage("assistant", message);
  }

  public void addRawMessage(JSONObject message)
  {
    if (message == null)
    {
      FileLogger.w(TAG, "#4935 [addRawMessage] Input is null, skip");
      return;
    }

    String role = message.optString("role", "unknown");
    boolean hasToolCalls = message.has("tool_calls");
    int toolCallsCount = hasToolCalls ? message.optJSONArray("tool_calls").length() : 0;
    
    FileLogger.i(TAG, "#4935 [addRawMessage CALL] role=" + role + 
                  ", has_tool_calls=" + hasToolCalls + 
                  ", tool_calls_count=" + toolCallsCount);
    
    List<JSONObject> historyBefore = getHistory();
    FileLogger.i(TAG, "#4935 [addRawMessage BEFORE] History count: " + historyBefore.size());

    try
    {
      if (message.has("tool_calls"))
      {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls.length() == 0)
        {
          FileLogger.w(TAG, "#4935 [addRawMessage] Empty tool_calls detected, filtered");
          return;
        }

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
                new JSONObject(argumentsStr);
              }
              catch (JSONException e)
              {
                FileLogger.w(TAG, "Skip invalid JSON tool_call arguments: " + argumentsStr);
                FileLogger.w(TAG, "   tool_call name: " + function.optString("name", "unknown"));
                FileLogger.w(TAG, "#4935 [addRawMessage] Skipped due to invalid JSON, count remains: " + historyBefore.size());
                return;
              }
            }
          }
        }
      }
    }
    catch (JSONException e)
    {
      FileLogger.e(TAG, "Error checking tool_calls", e);
    }

    List<JSONObject> history = getHistory();
    history.add(message);
    
    FileLogger.i(TAG, "#4935 [addRawMessage] Message added, before: " + historyBefore.size() + " -> after: " + history.size());
    
    JSONObject lastMsg = history.get(history.size() - 1);
    String lastRole = lastMsg.optString("role", "unknown");
    boolean lastHasToolCalls = lastMsg.has("tool_calls");
    FileLogger.i(TAG, "#4935 [addRawMessage] Last msg verify: role=" + lastRole + ", has_tool_calls=" + lastHasToolCalls);

    history = removeOldHistoryEntries(history);
    
    FileLogger.i(TAG, "#4935 [removeOldHistoryEntries] After: " + history.size());
    
    saveHistory(history);
    
    FileLogger.i(TAG, "#4935 [addRawMessage DONE] Final count: " + history.size());
  }

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

  public void logFullHistory(String prefix)
  {
    List<JSONObject> history = getHistory();
    FileLogger.i(TAG, "[Full History] " + prefix + ", Total: " + history.size());
    
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String toolCalls = msg.has("tool_calls") ? " | tool_calls=" + msg.optJSONArray("tool_calls").length() : "";
      String toolId = msg.optString("tool_call_id", "");
      String toolIdLog = !toolId.isEmpty() ? " | tool_call_id=" + toolId : "";
      
      // ✅ #4997 简化所有消息日志，只输出关键信息
      if ("tool".equals(role)) {
        String toolName = msg.optString("name", "unknown");
        FileLogger.i(TAG, "  [" + i + "] role=" + role + toolCalls + toolIdLog + " | name=" + toolName);
      } else if ("user".equals(role)) {
        // user 消息只输出 role，不输出 content
        FileLogger.i(TAG, "  [" + i + "] role=" + role);
      } else if ("assistant".equals(role)) {
        // assistant 消息只输出 role 和 tool_calls 统计
        FileLogger.i(TAG, "  [" + i + "] role=" + role + toolCalls);
      } else {
        // 其他角色（如 system）只输出 role
        FileLogger.i(TAG, "  [" + i + "] role=" + role);
      }
    }
    
    FileLogger.i(TAG, "[Full History] End");
  }

  public List<JSONObject> getHistory()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");

    if (historyStr.isEmpty())
    {
      return new ArrayList<>();
    }

    List<JSONObject> list = new ArrayList<>();

    try
    {
      JSONArray array = new JSONArray(historyStr);
      FileLogger.d(TAG, "[getHistory] Load history, count: " + array.length());

      for (int i = 0; i < array.length(); i++)
      {
        list.add(array.getJSONObject(i));
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    return list;
  }

  private boolean isValidToolCallMessage(JSONObject message)
  {
    try
    {
      if (!message.has("tool_calls"))
      {
        return true;
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
            Log.i(TAG, "argumentsString length: " + argumentsStr.length() + ", content : " + argumentsStr );


            
            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();

              Log.i(TAG, "parsed length: " +  parsed.toString().length()   + ", content: " + parsed );

              if (tokener.more())
              {
                FileLogger.w(TAG, "Invalid JSON: extra data - \"" + argumentsStr + "\"");
                return false;
              }
              
              if (!(parsed instanceof JSONObject))
              {
                FileLogger.w(TAG, "Invalid JSON: not JSONObject, type=" + parsed.getClass().getName());
                return false;
              }

              if (argumentsStr.length() > MAX_ARGUMENTS_STR_LENGTH)
              {
                FileLogger.w(TAG, "arguments string too long: " + argumentsStr.length());
                return false;
              }
            }
            catch (JSONException e)
            {
              FileLogger.w(TAG, "Invalid JSON: " + e.getMessage());
              return false;
            }
          }
        }
      }
      return true;
    }
    catch (JSONException e)
    {
      FileLogger.w(TAG, "Error during validation: " + e.getMessage());
      return false;
    }
  }

  private List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  {
    FileLogger.i(TAG, "[normalize] Input: " + oldHistory.size());
    
    List<JSONObject> history = oldHistory;
    List<JSONObject> list = new ArrayList<>();

    try
    {
      JSONObject pendingToolCallsObject = null;

      for (int i = 0; i < history.size(); i++)
      {
        JSONObject currentObject =  history.get(i);
        String roleString = currentObject.getString("role");

        if (roleString.equals("assistant"))
        {
          if (currentObject.has("tool_calls"))
          {
            String toolCallId = "unknown";
            try
            {
              JSONArray toolCalls = currentObject.getJSONArray("tool_calls");
              if (toolCalls.length() > 0)
              {
                JSONObject firstToolCall = toolCalls.getJSONObject(0);
                toolCallId = firstToolCall.optString("id", "unknown");
              }
            }
            catch (Exception e) {}
            
            pendingToolCallsObject = currentObject;
            continue;
          }
        }
        else if (roleString.equals("tool"))
        {
          String answeringtoolCAllId = currentObject.optString("tool_call_id", "none");
          
          if (pendingToolCallsObject!=null)
          {
            JSONArray toolCALLSArray = pendingToolCallsObject.getJSONArray("tool_calls");
            JSONObject toolCallsFirst = toolCALLSArray.getJSONObject(0);
            String toolCAllsId = toolCallsFirst.getString("id");

            if (toolCAllsId.equals(answeringtoolCAllId))
            {
              list.add(pendingToolCallsObject);
              pendingToolCallsObject = null;
            }
            else
            {
              pendingToolCallsObject = null;
              continue;
            }

          }
          else
          {
            continue;
          }
        }

        list.add(currentObject);
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    
    FileLogger.i(TAG, "[normalize] Output: " + list.size());
    return list;
  }

  public void replaceHistory(List<JSONObject> newHistory)
  {
    FileLogger.i(TAG, "[replaceHistory] New count: " + newHistory.size());
    
    if (newHistory.size() > currentMaxRounds * 2)
    {
      newHistory = new ArrayList<>(newHistory.subList(newHistory.size() - (currentMaxRounds * 2), newHistory.size()));
      FileLogger.w(TAG, "[replaceHistory] Truncated to: " + newHistory.size());
    }
    
    saveHistory(newHistory);
    FileLogger.i(TAG, "[replaceHistory] Done");
  }

  private void saveHistory(List<JSONObject> history)
  {
    JSONArray historyArray = new JSONArray(history);
    sharedPreferences.edit()
        .putString(KEY_HISTORY, historyArray.toString())
        .putInt("current_max_rounds", currentMaxRounds)
        .apply();
    FileLogger.d(TAG, "[saveHistory] Saved: " + history.size());
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
    FileLogger.i(TAG, "Max rounds: " + currentMaxRounds);
  }

  public void decreaseMaxRounds()
  {
    FileLogger.i(TAG, "Max rounds before: " + currentMaxRounds);

    List<JSONObject> history = getHistory();
    int idealMaxRounds = history.size() /2 -1 ;

    if (idealMaxRounds > INITIAL_MAX_ROUNDS)
    {
      currentMaxRounds = idealMaxRounds;
      history = removeOldHistoryEntries(history);
      saveHistory(history);
    }
    FileLogger.i(TAG, "Max rounds after: " + currentMaxRounds);
  }
}