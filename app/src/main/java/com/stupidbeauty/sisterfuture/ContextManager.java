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
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ContextManager
{
  private static final String TAG = "ContextManager";
  private static final String PREF_NAME = "context_manager";
  private static final String KEY_HISTORY = "history";
  private static final int INITIAL_MAX_ROUNDS = 5;
  private SharedPreferences sharedPreferences;
  private int currentMaxRounds = INITIAL_MAX_ROUNDS;
  private int MAX_ARGUMENTS_STR_LENGTH = 226810;
  
  // ✅ 新增：内存中的历史列表（唯一真相源）
  private List<JSONObject> memoryHistory;

  public ContextManager(Context context)
  {
    sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    currentMaxRounds = sharedPreferences.getInt("current_max_rounds", INITIAL_MAX_ROUNDS);
    
    // ✅ 启动时从 SP 加载到内存
    loadHistoryFromSharedPreferences();
    
    cleanupInvalidToolCallsOnStartup();
  }

  // ✅ 新增：从 SP 加载历史到内存
  private void loadHistoryFromSharedPreferences()
  {
    String historyStr = sharedPreferences.getString(KEY_HISTORY, "");
    
    if (historyStr.isEmpty())
    {
      memoryHistory = new ArrayList<>();
      FileLogger.d(TAG, "📥 [LOAD] 从 SharedPreferences 加载历史：空");
      return;
    }
    
    try
    {
      JSONArray array = new JSONArray(historyStr);
      memoryHistory = new ArrayList<>();
      
      for (int i = 0; i < array.length(); i++)
      {
        memoryHistory.add(array.getJSONObject(i));
      }
    } // try
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [LOAD] 加载历史失败：" + e.getMessage(), e);
      memoryHistory = new ArrayList<>();
    }
  }

  private boolean inDebugMessageIndexRange(int i)
  {
    int rangeMaximal = 1890;
    int rangeMinimal= 0;
    return true;
  }

  private void cleanupInvalidToolCallsOnStartup()
  {
    if (memoryHistory == null || memoryHistory.isEmpty())
    {
      FileLogger.d(TAG, "🧹 [CLEANUP] 内存历史为空，跳过清理");
      return;
    }
    
    List<JSONObject> history = new ArrayList<>(memoryHistory);
    int invalidCount = 0;
    int blankAssistantCount = 0;
    
    try
    {
      JSONArray array = new JSONArray(history);
      
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
      
      // ✅ 启动清理使用标准模式（非严厉模式），保留悬而未决的工具调用
      history = normalizeToolCallMessages(history, false);
      
      if (invalidCount > 0 || blankAssistantCount > 0 || history.size() < array.length())
      {
        saveHistory(history);
        FileLogger.i(TAG, "🧹 [CLEANUP] 清理完成，新历史：" + memoryHistory.size() + " 条");
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
      FileLogger.e(TAG, "Failed to create tool message", e);
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

  public void addRawMessage(JSONObject message)
  {
    if (message == null)
    {
      return;
    }

    // Validate tool_calls arguments before adding to history
    if (message.has("tool_calls"))
    {
      try
      {
        JSONArray toolCalls = message.getJSONArray("tool_calls");
        if (toolCalls.length() == 0)
        {
          FileLogger.w(TAG, "[addRawMessage] Skip: empty tool_calls array");
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
              
              // Check length first
              if (argumentsStr.length() > MAX_ARGUMENTS_STR_LENGTH)
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: arguments too long (" + argumentsStr.length() + " > " + MAX_ARGUMENTS_STR_LENGTH + ")");
                return;
              }
              
              // General validation: detect any unquoted string identifiers in JSON
              if (hasUnquotedStringValues(argumentsStr))
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: arguments contains unquoted string values");
                return;
              }
              
              // Strict JSON validation
              try
              {
                JSONTokener tokener = new JSONTokener(argumentsStr);
                Object parsed = tokener.nextValue();
                
                if (tokener.more())
                {
                  FileLogger.w(TAG, "[addRawMessage] Skip: arguments has trailing content after JSON");
                  return;
                }
                
                if (!(parsed instanceof JSONObject))
                {
                  FileLogger.w(TAG, "[addRawMessage] Skip: arguments is not a JSONObject");
                  return;
                }
              }
              catch (JSONException e)
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: invalid JSON in arguments - " + e.getMessage());
                return;
              }
            }
          }
        }
      }
      catch (JSONException e)
      {
        FileLogger.e(TAG, "[addRawMessage] Error checking tool_calls: " + e.getMessage(), e);
        return;
      }
    }

    List<JSONObject> historyBefore = getHistory();

    List<JSONObject> history = getHistory();
    
    history.add(message);
    FileLogger.i(TAG, "[addRawMessage] Message added: " + historyBefore.size() + " -> " + history.size());
    
    history = removeOldHistoryEntries(history);
    saveHistory(history);
    FileLogger.i(TAG, "[addRawMessage DONE] Final count: " + history.size());
  }

  /**
   * General validation: check if JSON string contains unquoted string values.
   * Strategy: Remove all quoted strings, then look for alphabetic identifiers after colons.
   * Valid JSON values: "quoted", number, true, false, null, {, [, }
   * Invalid: unquoted identifiers like latest, abc, test_value
   */
  private boolean hasUnquotedStringValues(String jsonStr)
  {
    // Step 1: Remove all properly quoted strings (including escaped quotes)
    String withoutQuotedStrings = jsonStr.replaceAll("\"(?:[^\"\\\\]|\\\\.)*\"", "\"\"");
    
    // Step 2: Look for pattern: : followed by whitespace and an identifier
    // Identifiers start with letter/underscore, followed by alphanumeric/underscore
    Pattern pattern = Pattern.compile(":\\s*([a-zA-Z_][a-zA-Z0-9_]*)");
    Matcher matcher = pattern.matcher(withoutQuotedStrings);
    
    while (matcher.find())
    {
      String identifier = matcher.group(1);
      
      // Step 3: Check if it's NOT a valid JSON keyword
      if (!identifier.equals("true") && 
          !identifier.equals("false") && 
          !identifier.equals("null"))
      {
        FileLogger.d(TAG, "[hasUnquotedStringValues] Found unquoted identifier: " + identifier);
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
    List<JSONObject> history = getHistory();
    return new JSONArray(history);
  }

  public void logFullHistory(String prefix)
  {
    List<JSONObject> history = getHistory();
    FileLogger.i(TAG, "📋 [" + prefix + "] 历史消息列表（共 " + history.size() + " 条）:");
    for (int i = 0; i < history.size(); i++)
    {
      JSONObject msg = history.get(i);
      String role = msg.optString("role", "unknown");
      String content = msg.optString("content", "");
      boolean hasToolCalls = msg.has("tool_calls");
      FileLogger.i(TAG, "  [" + i + "] role=" + role + 
                    ", hasToolCalls=" + hasToolCalls + 
                    ", contentLength=" + content.length());
    }
  }

  // ✅ 修改：直接返回内存中的历史列表（唯一真相源）
  public List<JSONObject> getHistory()
  {
    if (memoryHistory == null)
    {
      FileLogger.w(TAG, "⚠️ [GET] 内存历史未初始化，重新加载");
      loadHistoryFromSharedPreferences();
    }

    return memoryHistory;
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
            
            // General validation: check for unquoted string values
            if (hasUnquotedStringValues(argumentsStr))
            {
              FileLogger.d(TAG, "[isValidToolCallMessage] Invalid: unquoted string values");
              return false;
            }
            
            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();
              
              if (tokener.more())
              {
                return false;
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

  /**
   * 标准化工具调用消息，配对 assistant+tool_calls 与对应的 tool 回复
   * 
   * @param oldHistory 原始历史记录
   * @return 标准化后的历史记录
   */
  public List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory)
  {
    return normalizeToolCallMessages(oldHistory, false);
  }

  /**
   * 标准化工具调用消息，支持严厉模式
   * 
   * @param oldHistory 原始历史记录
   * @param strictMode 严厉模式：true=移除所有未匹配的 assistant+tool_calls；false=保留等待后续回复
   * @return 标准化后的历史记录
   */
  public List<JSONObject> normalizeToolCallMessages(List<JSONObject> oldHistory, boolean strictMode)
  {
    List<JSONObject> history = oldHistory;
    List<JSONObject> list = new ArrayList<>();
    int cleanedCount = 0;

    try
    {
      // 🔍 新增：记录输入历史的消息类型
      FileLogger.i(TAG, "📝 [INPUT] 开始处理输入历史，共 " + oldHistory.size() + " 条消息");
      for (int i = 0; i < oldHistory.size(); i++)
      {
        JSONObject msg = oldHistory.get(i);
        String role = msg.optString("role", "unknown");
        Object contentObj = msg.opt("content");
        String contentType = (contentObj instanceof JSONArray) ? "JSONArray" : "String";
        int contentLength = (contentObj instanceof String) ? ((String) contentObj).length() : ((JSONArray) contentObj).length();
        
        FileLogger.i(TAG, "  [" + i + "] role=" + role + ", contentType=" + contentType + ", contentLength=" + contentLength);
        
        // 🖼️ 特别标记多模态消息
        if ("user".equals(role) && (contentObj instanceof JSONArray))
        {
          JSONArray contentArray = (JSONArray) contentObj;
          FileLogger.i(TAG, "🖼️ [MULTIMODAL] 检测到多模态用户消息，包含 " + contentArray.length() + " 个元素");
          for (int j = 0; j < contentArray.length(); j++)
          {
            JSONObject item = contentArray.optJSONObject(j);
            if (item != null)
            {
              String type = item.optString("type", "unknown");
              FileLogger.i(TAG, "    [" + j + "] type=" + type);
            }
          }
        }
      }

      JSONObject pendingToolCallsObject = null;
      List<String> matchedToolCallIds = new ArrayList<>();
      // ✅ 新增：暂存匹配的 tool 消息
      List<JSONObject> matchedToolMessages = new ArrayList<>();

      for (int i = 0; i < history.size(); i++)
      {
        JSONObject currentObject =  history.get(i);
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
          
          if (pendingToolCallsObject!=null)
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
              // ✅ 暂存匹配的 tool 消息
              matchedToolMessages.add(currentObject);
              
              if (matchedToolCallIds.size() == pendingToolCallsObject.getJSONArray("tool_calls").length())
              {
                // ✅ 所有 tool 都匹配完成，按顺序添加
                list.add(pendingToolCallsObject);  // 先添加 assistant
                list.addAll(matchedToolMessages);  // 再添加所有 tool 消息
                pendingToolCallsObject = null;
                matchedToolCallIds.clear();
                matchedToolMessages.clear();  // 清空暂存列表
              }
            }
            else
            {
              FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " did NOT match any pending tool_call!");
            }
            continue;
          }
          else
          {
            FileLogger.w(TAG, "[normalizeToolCallMessages] Tool message tool_call_id=" + answeringtoolCAllId + " found but pendingToolCallsObject is null, skipping!");
            continue;
          }
        }

        list.add(currentObject);
      }
      
      // 🔍 #759909257401 严厉模式：移除所有未匹配的 assistant+tool_calls 消息
      if (strictMode && pendingToolCallsObject != null)
      {
        cleanedCount = removePendingAssistantMessages(list, pendingToolCallsObject);
        FileLogger.i(TAG, "🔄 [TIMELINE_BRANCH] 创建新时间线，清理悬而未决的工具调用消息");
        FileLogger.i(TAG, "🗑️ [CLEANED] 共清理 " + cleanedCount + " 条未完成的工具调用消息");
        FileLogger.i(TAG, "📝 [INFO] 当前历史长度：" + list.size());
        
        // ✅ 严厉模式下需要显式保存清理后的历史
        saveHistory(list);
      }
      else if (pendingToolCallsObject != null)
      {
        list.add(pendingToolCallsObject);
        FileLogger.w(TAG, "[normalizeToolCallMessages] Pending assistant with tool_calls added at end, but some tool messages may be missing");
      }
      
      // 🔍 新增：记录输出历史的消息类型
      FileLogger.i(TAG, "📤 [OUTPUT] 处理完成，输出历史共 " + list.size() + " 条消息");
      int userMessageCount = 0;
      int preservedMultimodalCount = 0;
      for (int i = 0; i < list.size(); i++)
      {
        JSONObject msg = list.get(i);
        String role = msg.optString("role", "unknown");
        Object contentObj = msg.opt("content");
        String contentType = (contentObj instanceof JSONArray) ? "JSONArray" : "String";
        
        if ("user".equals(role))
        {
          userMessageCount++;
          if (contentObj instanceof JSONArray)
          {
            preservedMultimodalCount++;
            FileLogger.i(TAG, "  [" + i + "] ✅ PRESERVED: role=" + role + ", contentType=" + contentType + " (多模态消息)");
          }
          else
          {
            FileLogger.i(TAG, "  [" + i + "] role=" + role + ", contentType=" + contentType);
          }
        }
      }
      
      FileLogger.i(TAG, "📊 [SUMMARY] 输入 " + oldHistory.size() + " 条 -> 输出 " + list.size() + " 条，清理 " + cleanedCount + " 条");
      FileLogger.i(TAG, "📊 [SUMMARY] 用户消息：" + userMessageCount + " 条，其中多模态消息：" + preservedMultimodalCount + " 条");
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [ERROR] normalizeToolCallMessages 异常：" + e.getMessage(), e);
      e.printStackTrace();
    }
    
    return list;
  }

  /**
   * 移除未完成的 assistant+tool_calls 消息（严厉模式专用）
   * 
   * @param list 当前历史列表
   * @param pendingObject 待移除的未完成消息
   * @return 移除的消息数量
   */
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
          JSONObject toolCall = toolCalls.getJSONObject(i);
          String callId = toolCall.optString("id", "unknown");
          JSONObject func = toolCall.optJSONObject("function");
          String toolName = func != null ? func.optString("name", "unknown") : "unknown";
          
          FileLogger.w(TAG, "🗑️ [CLEANED] 移除未完成的 tool_call: " + callId + " (" + toolName + ")");
          removedCount++;
        }
      }
      
      // 不将 pendingObject 添加到 list 中，相当于移除了这条消息
      FileLogger.d(TAG, "[removePendingAssistantMessages] Removed " + removedCount + " pending tool_calls");
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "[removePendingAssistantMessages] Error: " + e.getMessage(), e);
    }
    
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

  // ✅ 修改：同时更新内存和 SP，内存是唯一真相源
  private void saveHistory(List<JSONObject> history)
  {
    // 1. 更新内存（唯一真相源）
    memoryHistory = new ArrayList<>(history);
    FileLogger.d(TAG, "💾 [SAVE] 更新内存历史：" + memoryHistory.size() + " 条");
    
    // 2. 异步保存到 SP（持久化）
    try
    {
      JSONArray historyArray = new JSONArray(history);
      sharedPreferences.edit()
          .putString(KEY_HISTORY, historyArray.toString())
          .putInt("current_max_rounds", currentMaxRounds)
          .apply();  // apply() 异步没关系，因为读取的是内存
      
      FileLogger.d(TAG, "💾 [SAVE] 保存历史到 SharedPreferences：" + history.size() + " 条");
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ [SAVE] 保存历史失败：" + e.getMessage(), e);
    }
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
