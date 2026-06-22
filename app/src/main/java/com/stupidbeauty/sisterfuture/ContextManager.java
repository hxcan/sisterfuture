package com.stupidbeauty.sisterfuture;

import com.stupidbeauty.codeposition.CodePosition;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import com.stupidbeauty.sisterfuture.manager.ToolAvoidanceDetectionManager;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ContextManager
{
  private static final String TAG = "ContextManager";
  private static final String CONTEXT_FILE_NAME = "conversation_context.json";
  private static final String PREF_NAME = "context_manager";
  private static final String KEY_HISTORY = "history";
  private static final String KEY_MAX_ROUNDS = "current_max_rounds";
  private static final int INITIAL_MAX_ROUNDS = 5;
  private static final int CONTEXT_ALERT_CLEANUP_THRESHOLD = 5;
  private Context context;
  private File contextFile;
  private SharedPreferences sharedPreferences;
  private int currentMaxRounds = INITIAL_MAX_ROUNDS;
  private int MAX_ARGUMENTS_STR_LENGTH = 226810;
  
  private List<JSONObject> memoryHistory;
  private Set<String> reservedMessageIds = new HashSet<>();

  private final ExecutorService writeExecutor = Executors.newSingleThreadExecutor();

  public ContextManager(Context context)
  {
    this.context = context;
    
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    currentMaxRounds = sharedPreferences.getInt(KEY_MAX_ROUNDS, INITIAL_MAX_ROUNDS);
    
    contextFile = new File(context.getFilesDir(), CONTEXT_FILE_NAME);
    
    loadHistoryFromFile();
    
    cleanupDuplicateContextAlertsOnStartup();
    cleanupInvalidToolCallsOnStartup();
    recoverFromToolAvoidanceOnStartup();
  }

  private void recoverFromToolAvoidanceOnStartup()
  {
    if (memoryHistory == null || memoryHistory.isEmpty()) return;
    ToolAvoidanceDetectionManager detector = new ToolAvoidanceDetectionManager();
    int removeCount = detector.performCleanup(memoryHistory);
    if (removeCount > 0)
    {
      FileLogger.w(TAG, "🧹 [TOOL_AVOIDANCE] 启动恢复 | 删除" + removeCount + "条污染消息 | 剩余" + memoryHistory.size() + "条");
      saveHistory(memoryHistory);
    }
  }

  private void loadHistoryFromFile()
  {
    boolean shouldFallbackToSP = false;
    
    if (!contextFile.exists())
    {
      shouldFallbackToSP = true;
    }
    else
    {
      try
      {
        BufferedReader reader = new BufferedReader(new FileReader(contextFile));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
        {
          sb.append(line);
        }
        reader.close();
        
        String fileContent = sb.toString();
        if (fileContent.isEmpty())
        {
          shouldFallbackToSP = true;
        }
        else
        {
          JSONObject rootObj = new JSONObject(fileContent);
          
          if (rootObj.has(KEY_HISTORY))
          {
            JSONArray array = rootObj.getJSONArray(KEY_HISTORY);
            memoryHistory = new ArrayList<>();
            
            for (int i = 0; i < array.length(); i++)
            {
              memoryHistory.add(array.getJSONObject(i));
            }
            return;
          }
          else
          {
            shouldFallbackToSP = true;
          }
        }
      }
      catch (Exception e)
      {
        shouldFallbackToSP = true;
      }
    }
    
    if (shouldFallbackToSP)
    {
      try
      {
        String spHistoryJson = sharedPreferences.getString(KEY_HISTORY, null);
        if (spHistoryJson != null && !spHistoryJson.isEmpty())
        {
          JSONArray array = new JSONArray(spHistoryJson);
          memoryHistory = new ArrayList<>();
          
          for (int i = 0; i < array.length(); i++)
          {
            memoryHistory.add(array.getJSONObject(i));
          }
          return;
        }
      }
      catch (Exception e)
      {
      }
      
      memoryHistory = new ArrayList<>();
    }
  }

  private boolean inDebugMessageIndexRange(int i)
  {
    return true;
  }

  private void cleanupInvalidToolCallsOnStartup()
  {
    if (memoryHistory == null || memoryHistory.isEmpty()) return;
    
    int invalidCount = 0;
    int blankAssistantCount = 0;
    List<JSONObject> validHistory = new ArrayList<>();
    
    try
    {
      for (int i = 0; i < memoryHistory.size(); i++)
      {
        JSONObject currentObject = memoryHistory.get(i);
        
        String role = currentObject.optString("role", "");
        String content = currentObject.optString("content", "");
        boolean hasToolCalls = currentObject.has("tool_calls");
        
        if ("assistant".equals(role) && content.isEmpty() && !hasToolCalls)
        {
          blankAssistantCount++;
          continue;
        }

        if (!isValidToolCallMessage(currentObject))
        {
          invalidCount++;
          continue;
        }
        
        validHistory.add(currentObject);
      }
      
      List<JSONObject> normalizedHistory = normalizeToolCallMessages(validHistory, false);
      
      if (invalidCount > 0 || blankAssistantCount > 0 || normalizedHistory.size() != memoryHistory.size())
      {
        saveHistory(normalizedHistory);
      }
    }
    catch (Exception e)
    {
    }
  }

  private void cleanupDuplicateContextAlertsOnStartup()
  {
    if (memoryHistory == null || memoryHistory.isEmpty()) return;

    String ALERT_MARKER = "⚠️ 上下文超长，已自动缩短";

    int duplicateCount = 0;
    for (int i = 0; i < memoryHistory.size(); i++)
    {
      JSONObject msg = memoryHistory.get(i);
      String role = msg.optString("role", "");
      String content = msg.optString("content", "");
      if ("assistant".equals(role) && content.contains(ALERT_MARKER))
      {
        duplicateCount++;
      }
    }

    if (duplicateCount <= CONTEXT_ALERT_CLEANUP_THRESHOLD) return;

    List<JSONObject> validHistory = new ArrayList<>();
    boolean foundLatest = false;
    for (int i = memoryHistory.size() - 1; i >= 0; i--)
    {
      JSONObject msg = memoryHistory.get(i);
      String role = msg.optString("role", "");
      String content = msg.optString("content", "");

      if ("assistant".equals(role) && content.contains(ALERT_MARKER))
      {
        if (foundLatest) continue;
        else foundLatest = true;
      }
      validHistory.add(0, msg);
    }

    saveHistory(validHistory);
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
  
  public void removeMessage(int index)
  {
    if (memoryHistory == null || index < 0 || index >= memoryHistory.size())
    {
      return;
    }
    
    JSONObject removedMessage = memoryHistory.remove(index);
    FileLogger.i(TAG, "🗑️ [REMOVE] 已删除消息 | index=" + index);
    
    List<JSONObject> normalizedHistory = normalizeToolCallMessages(memoryHistory, false);
    saveHistory(normalizedHistory);
  }

  public void addToolMessage(String toolCallId, String toolName, String content)
  {
    List<JSONObject> history = getHistory();
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
      return;
    }

    history.add(toolMessage);
    history = removeOldHistoryEntries(history);
    history = normalizeToolCallMessages(history, false);
    saveHistory(history);
  }

  public void addUserMessage(String message)
  {
    addMessage("user", message);
    
    List<JSONObject> history = getHistory();
    history = normalizeToolCallMessages(history, false);
    saveHistory(history);
  }

  public void addAssistantMessage(String message)
  {
    addMessage("assistant", message);
  }
  
  public void addAssistantMessage(String message, String messageId)
  {
    JSONObject msg = createMessage("assistant", message);
    if (messageId != null && !messageId.isEmpty())
    {
      try
      {
        msg.put("id", messageId);
        reservedMessageIds.remove(messageId);
      }
      catch (JSONException e)
      {
      }
    }
    addRawMessage(msg);
  }

  public void addRawMessage(JSONObject message)
  {
    if (message == null) return;

    if (message.has("tool_calls"))
    {
      try
      {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls.length() == 0) return;

        for (int i = 0; i < toolCalls.length(); i++)
        {
          JSONObject toolCall = toolCalls.getJSONObject(i);
          if (toolCall.has("function"))
          {
            JSONObject function = toolCall.getJSONObject("function");
            if (function.has("arguments"))
            {
              if (!isValidToolCallMessage(message)) return;
            }
          }
        }
      }
      catch (JSONException e)
      {
        return;
      }
    }

    List<JSONObject> history = getHistory();
    history.add(message);
    history = removeOldHistoryEntries(history);
    saveHistory(history);
  }
  
  private boolean isJsonSyntaxComplete(String jsonStr)
  {
    if (jsonStr == null || jsonStr.trim().isEmpty()) return false;
    
    int braceCount = 0;
    int bracketCount = 0;
    boolean inString = false;
    boolean escaped = false;
    
    for (int i = 0; i < jsonStr.length(); i++)
    {
      char c = jsonStr.charAt(i);
      
      if (escaped) { escaped = false; continue; }
      if (c == '\\' && inString) { escaped = true; continue; }
      if (c == '"' && !escaped) { inString = !inString; continue; }
      
      if (!inString)
      {
        if (c == '{') braceCount++;
        else if (c == '}') braceCount--;
        else if (c == '[') bracketCount++;
        else if (c == ']') bracketCount--;
        
        if (braceCount < 0 || bracketCount < 0) return false;
      }
    }
    
    if (braceCount != 0 || bracketCount != 0) return false;
    if (inString) return false;
    
    String trimmed = jsonStr.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false;
    
    return true;
  }

  private boolean hasUnquotedStringValues(String jsonStr)
  {
    String withoutQuotedStrings = jsonStr.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    Pattern pattern = Pattern.compile(":\\s*([a-zA-Z_][a-zA-Z0-9_]*)");
    Matcher matcher = pattern.matcher(withoutQuotedStrings);

    while (matcher.find())
    {
      String identifier = matcher.group(1);
      if (!identifier.equals("true") && !identifier.equals("false") && !identifier.equals("null"))
      {
        return true;
      }
    }
    return false;
  }

  private void addMessage(String role, String content)
  {
    JSONObject message = createMessage(role, content);
    addRawMessage(message);
  }

  public JSONArray getMessagesArray()
  {
    return new JSONArray(getHistory());
  }

  public List<JSONObject> getHistory()
  {
    if (memoryHistory == null) loadHistoryFromFile();
    return memoryHistory;
  }
  
  public String reserveMessageId()
  {
    String messageId = generateMessageId();
    reservedMessageIds.add(messageId);
    return messageId;
  }

  public void discardReservedMessageId(String messageId)
  {
    if (messageId != null) reservedMessageIds.remove(messageId);
  }
  
  public boolean isReservedMessageId(String messageId)
  {
    return reservedMessageIds.contains(messageId);
  }
  
  private static String generateMessageId()
  {
    return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
  }

  private boolean isValidToolCallMessage(JSONObject message)
  {
    try
    {
      if (!message.has("tool_calls")) return true;

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
            String trimmedArgs = argumentsStr.trim();
            
            if (trimmedArgs.startsWith("{"))
            {
              if (trimmedArgs.length() > 1)
              {
                char secondChar = trimmedArgs.charAt(1);
                if (secondChar != '"' && secondChar != '}') return false;
              }
            }

            if (hasUnquotedStringValues(argumentsStr)) return false;
            if (!isJsonSyntaxComplete(argumentsStr)) return false;

            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();
              if (tokener.more()) return false;
              if (!(parsed instanceof JSONObject)) return false;
              if (argumentsStr.length() > MAX_ARGUMENTS_STR_LENGTH) return false;
            }
            catch (JSONException e) { return false; }
          }
        }
      }
      return true;
    }
    catch (JSONException e) { return false; }
  }

  public List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  {
    return normalizeToolCallMessages(oldHistory, false);
  }

  public List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory, boolean strictMode)
  {
    List<JSONObject> history = oldHistory;
    List<JSONObject> list = new ArrayList<>();
    int cleanedCount = 0;

    try
    {
      JSONObject pendingToolCallsObject = null;
      List<String> matchedToolCallIds = new ArrayList<>();
      List<JSONObject> matchedToolMessages = new ArrayList<>();
      
      for (int i = 0; i < history.size(); i++)
      {
        JSONObject currentObject = history.get(i);
        String roleString = currentObject.getString("role");

        if (roleString.equals("assistant"))
        {
          if (currentObject.has("tool_calls"))
          {
            pendingToolCallsObject = currentObject;
            matchedToolCallIds.clear();
            continue;
          }
        }
        else if (roleString.equals("tool"))
        {
          String answeringtoolCAllId = currentObject.optString("tool_call_id", "none");
          
          if (pendingToolCallsObject != null)
          {
            JSONArray toolCallsArray = pendingToolCallsObject.getJSONArray("tool_calls");
            boolean matched = false;
            for (int tc = 0; tc < toolCallsArray.length(); tc++)
            {
              JSONObject toolCall = toolCallsArray.getJSONObject(tc);
              String toolCallId = toolCall.optString("id", "");
              if (toolCallId.equals(answeringtoolCAllId) && !matchedToolCallIds.contains(toolCallId))
              {
                matched = true;
                matchedToolCallIds.add(toolCallId);
                break;
              }
            }
            if (matched)
            {
              matchedToolMessages.add(currentObject);
              
              if (matchedToolCallIds.size() == pendingToolCallsObject.getJSONArray("tool_calls").length())
              {
                list.add(pendingToolCallsObject);
                list.addAll(matchedToolMessages);
                pendingToolCallsObject = null;
                matchedToolCallIds.clear();
                matchedToolMessages.clear();
              }
            }
            continue;
          }
          else
          {
            continue;
          }
        }

        list.add(currentObject);
      }
      
      if (strictMode && pendingToolCallsObject != null)
      {
        cleanedCount = removePendingAssistantMessages(list, pendingToolCallsObject);
        saveHistory(list);
      }
      else if (pendingToolCallsObject != null)
      {
        list.add(pendingToolCallsObject);
      }
    }
    catch (Exception e)
    {
    }
    
    return list;
  }

  private int removePendingAssistantMessages(List<JSONObject> list, JSONObject pendingObject)
  {
    int removedCount = 0;
    try
    {
      JSONArray toolCalls = pendingObject.optJSONArray("tool_calls");
      if (toolCalls != null)
      {
        for (int i = 0; i < toolCalls.length(); i++)
        {
          removedCount++;
        }
      }
    }
    catch (Exception e) {}
    return removedCount;
  }

  public void replaceHistory(List<JSONObject> newHistory)
  {
    if (newHistory.size() > currentMaxRounds * 2)
    {
      newHistory = new ArrayList<>(newHistory.subList(newHistory.size() - (currentMaxRounds * 2), newHistory.size()));
    }
    saveHistory(newHistory);
  }

  private void saveHistory(List<JSONObject> history)
  {
    memoryHistory = new ArrayList<>(history);
    final List<JSONObject> historyCopy = new ArrayList<>(history);
    
    writeExecutor.execute(() -> {
      try
      {
        JSONObject rootObj = new JSONObject();
        rootObj.put(KEY_HISTORY, new JSONArray(historyCopy));
        
        FileWriter writer = new FileWriter(contextFile);
        writer.write(rootObj.toString());
        writer.flush();
        writer.close();
      }
      catch (Exception e)
      {
      }
    });
  }

  private JSONObject createMessage(String role, String content)
  {
    JSONObject msg = new JSONObject();
    try
    {
      msg.put("role", role);
      msg.put("content", content);
    }
    catch (Exception e) {}
    return msg;
  }

  public void increaseMaxRounds()
  {
    if (currentMaxRounds < Integer.MAX_VALUE)
    {
      currentMaxRounds++;
      sharedPreferences.edit().putInt(KEY_MAX_ROUNDS, currentMaxRounds).apply();
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
      sharedPreferences.edit().putInt(KEY_MAX_ROUNDS, currentMaxRounds).apply();
      history = removeOldHistoryEntries(history);
      saveHistory(history);
    }
  }
}