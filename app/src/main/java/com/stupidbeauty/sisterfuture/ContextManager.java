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
  }

  private boolean inDebugMessageIndexRange(int i)
  {
    int rangeMaximal = 1890;
    int rangeMinimal= 0;
    return true;
  }

  private void cleanupInvalidToolCallsOnStartup()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");
    
    if (historyStr.isEmpty())
    {
      return;
    }
    
    List<JSONObject> history = new ArrayList<>();
    int invalidCount = 0;
    int blankAssistantCount = 0;
    
    try
    {
      JSONArray array = new JSONArray(historyStr);
      
      for (int i = 0; i < array.length(); i++)
      {
        JSONObject currentObject = array.getJSONObject(i);
        
        String role = currentObject.optString("role", "");
        String content = currentObject.optString("content", "");
        boolean hasToolCalls = currentObject.has("tool_calls");
        
        if ("assistant".equals(role) && content.isEmpty() && !hasToolCalls)
        {
          blankAssistantCount++;
          continue;
        }

        if ((!(inDebugMessageIndexRange(i))) && (hasToolCalls))
        {
          continue;
        }
        
        if (isValidToolCallMessage(currentObject))
        {
          history.add(currentObject);
        }
        else
        {
          invalidCount++;
        }
      }
      
      history = normalizeToolCallMessages(history);
      
      if (invalidCount > 0 || blankAssistantCount > 0 || history.size() < array.length())
      {
        saveHistory(history);
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
    FileLogger.i(TAG, "#4935 [addToolMessage start] toolCallId=" + toolCallId + ", toolName=" + toolName);
    
    List<JSONObject> history = getHistory();
    FileLogger.i(TAG, "[addToolMessage] Current history count: " + history.size());
    
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String toolCallIds = msg.has("tool_calls") ? String.valueOf(msg.optJSONArray("tool_calls").length()) + " items" : "none";
      String toolId = msg.optString("tool_call_id", "none");
      String contentPreview = msg.optString("content", "").substring(0, Math.min(30, msg.optString("content").length()));
      FileLogger.i(TAG, "  Msg[" + i + "] role=" + role + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId + ", content=" + contentPreview + "...");
    }

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
    history = normalizeToolCallMessages(history);
    FileLogger.i(TAG, "[addToolMessage] Before normalize: " + history.size());
    history = normalizeToolCallMessages(history);
    FileLogger.i(TAG, "[addToolMessage] After normalize: " + history.size());
    
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String toolCallIds = msg.has("tool_calls") ? String.valueOf(msg.optJSONArray("tool_calls").length()) + " items" : "none";
      String toolId = msg.optString("tool_call_id", "none");
      FileLogger.i(TAG, "  Normalized[" + i + "] role=" + role + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);
    }
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

    List<JSONObject> historyBefore = getHistory();

    boolean hasToolCalls = message.has("tool_calls");
    int toolCallsCount = hasToolCalls ? message.optJSONArray("tool_calls").length() : 0;
    String contentPreview = message.optString("content", "").substring(0, Math.min(50, message.optString("content").length()));
    
    FileLogger.i(TAG, "#4935 [addRawMessage CALL] role=" + role + 
                  ", has_tool_calls=" + hasToolCalls + 
                  ", tool_calls_count=" + toolCallsCount +
                  ", content_preview=\"" + contentPreview + "...\"");
    
    // #4935 Log history state before add
    List<JSONObject> historyBefore = getHistory();
    FileLogger.i(TAG, "#4935 [addRawMessage BEFORE] History count: " + historyBefore.size());
    for (int i = 0; i < historyBefore.size(); i++)
    {
      JSONObject msg = historyBefore.get(i);
      String msgRole = msg.optString("role", "unknown");
      String msgToolCalls = msg.has("tool_calls") ? "has_tool_calls(" + msg.optJSONArray("tool_calls").length() + ")" : "none";
      String msgToolId = msg.optString("tool_call_id", "none");
      FileLogger.i(TAG, "  Msg[" + i + "] role=" + msgRole + ", tool_calls=" + msgToolCalls + ", tool_call_id=" + msgToolId);
    }

    try
    {
      if (message.has("tool_calls"))
      {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls.length() == 0)
        {
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
                return;
              }
            }
          }
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
            
            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();
              
              if (tokener.more())
              {
                return false;
              }
              }
              
              if (!(parsed instanceof JSONObject))
              {
                return false;
              }

              if (argumentsStr.length() > MAX_ARGUMENTS_STR_LENGTH)
              {
                return false;
              }
            }
            catch (JSONException e)
            {
              return false;
            }
          }
        }
      }
      return true;
    }
    catch (JSONException e)
    {
      return false;
    }
  }

  private List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  {
    List<JSONObject> history = oldHistory;
    List<JSONObject> list = new ArrayList<>();

    try
    {
      JSONObject pendingToolCallsObject = null;

      for (int i = 0; i < history.size(); i++)
      {
        JSONObject currentObject =  history.get(i);
        String roleString = currentObject.getString("role");
        String toolCallIds = currentObject.has("tool_calls") ? String.valueOf(currentObject.optJSONArray("tool_calls").length()) + " items" : "none";
        String toolId = currentObject.optString("tool_call_id", "none");
        FileLogger.d(TAG, "[normalize] Process[" + i + "] role=" + roleString + ", tool_calls=" + toolCallIds + ", tool_call_id=" + toolId);

        if (roleString.equals("assistant"))
        {
          if (currentObject.has("tool_calls"))
          {
            pendingToolCallsObject = currentObject;
            continue;
          }
        }
        else if (roleString.equals("tool"))
            {
          String answeringtoolCAllId = currentObject.optString("tool_call_id", "none");
          
              {
                JSONObject firstToolCall = toolCalls.getJSONObject(0);
                toolCallId = firstToolCall.optString("id", "unknown");
              }
            }
            catch (Exception e) {}
            
            FileLogger.d(TAG, "[normalize] Found assistant(tool_calls), id=" + toolCallId);
            pendingToolCallsObject = currentObject;
            continue;
          }
        }
        else if (roleString.equals("tool"))
        {
          String answeringtoolCAllId = currentObject.optString("tool_call_id", "none");
          FileLogger.d(TAG, "[normalize] Found tool reply, tool_call_id=" + answeringtoolCAllId);
          
          if (pendingToolCallsObject!=null)
          {
            JSONArray toolCALLSArray = pendingToolCallsObject.getJSONArray("tool_calls");
            JSONObject toolCallsFirst = toolCALLSArray.getJSONObject(0);
            String toolCAllsId = toolCallsFirst.getString("id");

            if (toolCAllsId.equals(answeringtoolCAllId))
            {
              FileLogger.d(TAG, "[normalize] Pair success, add assistant(tool_calls)");
              list.add(pendingToolCallsObject);
              pendingToolCallsObject = null;
            }
            else
            {
              FileLogger.w(TAG, "[normalize] Pair failed, clear pending");
              pendingToolCallsObject = null;
              continue;
            }

          }
          else
          {
            FileLogger.w(TAG, "[normalize] Orphan tool reply, skip");
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
    
    FileLogger.i(TAG, "[normalizeToolCallMessages done] Output count: " + list.size());
    return list;
  }

  public void replaceHistory(List<JSONObject> newHistory)
  {
    if (newHistory.size() > currentMaxRounds * 2)
    {
      newHistory = new ArrayList<>(newHistory.subList(newHistory.size() - (currentMaxRounds * 2), newHistory.size()));
    }
    
    saveHistory(newHistory);
  }

      FileLogger.i(TAG, "  NewMsg[" + i + "] role=" + role + ", content=" + content + "...");
    }
    
    if (newHistory.size() > currentMaxRounds * 2)
    {
      newHistory = new ArrayList<>(newHistory.subList(newHistory.size() - (currentMaxRounds * 2), newHistory.size()));
      FileLogger.w(TAG, "[replaceHistory] Exceeded limit, truncated to: " + newHistory.size());
    }
    
    saveHistory(newHistory);
    FileLogger.i(TAG, "[replaceHistory done] History saved");
  }

  private void saveHistory(List<JSONObject> history)
  {
    JSONArray historyArray = new JSONArray(history);
    sharedPreferences.edit()
        .putString(KEY_HISTORY, historyArray.toString())
        .putInt("current_max_rounds", currentMaxRounds)
        .apply();
    FileLogger.d(TAG, "#4935 [saveHistory] Saved, count: " + history.size());
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
  }

  public void decreaseMaxRounds()
  {
    List<JSONObject> history = getHistory();
    int idealMaxRounds = history.size() /2 -1 ;

    if (idealMaxRounds > INITIAL_MAX_ROUNDS)
    {
      currentMaxRounds = idealMaxRounds;
      history = removeOldHistoryEntries(history);
      saveHistory(history);
    }
  }
}