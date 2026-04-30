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
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

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
  
  // 🔗 新增：预留的消息 ID 集合（用于追踪尚未确认的消息）
  private Set<String> reservedMessageIds = new HashSet<>();

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
              FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] ========== All Validations Passed (Load) ==========");
    return true;
  }

  private void cleanupInvalidToolCallsOnStartup()
  {
    if (memoryHistory == null || memoryHistory.isEmpty())
    {
      FileLogger.d(TAG, "🧹 [CLEANUP] 内存历史为空，跳过清理");
      return;
    }
    
    int invalidCount = 0;
    int blankAssistantCount = 0;
    
    try
    {
      // 🔍 遍历原始 memoryHistory，仅统计无效消息数量，不修改列表
      for (int i = 0; i < memoryHistory.size(); i++)
      {
        JSONObject currentObject = memoryHistory.get(i);
        
        String role = currentObject.optString("role", "");
        String content = currentObject.optString("content", "");
        boolean hasToolCalls = currentObject.has("tool_calls");
        
        FileLogger.d(TAG, "[CLEANUP_LOOP] Processing message #" + i + ", role=" + role + ", hasToolCalls=" + hasToolCalls);

        if ("assistant".equals(role) && content.isEmpty() && !hasToolCalls)
        {
          blankAssistantCount++;
          continue;
        }

        if ((!(inDebugMessageIndexRange(i))) && (hasToolCalls))
        {
          continue;
        }
        
        if (!isValidToolCallMessage(currentObject))
        {
          invalidCount++;
          FileLogger.w(TAG, "🗑️ [CLEANUP] 检测到无效消息 #" + i + "，将在 normalize 中处理");
          FileLogger.d(TAG, "[CLEANUP_LOOP] Message #" + i + " is invalid, invalidCount=" + invalidCount);
        }
      }
      
      // ✅ 直接对完整的 memoryHistory 进行 normalize 处理
      List<JSONObject> normalizedHistory = normalizeToolCallMessages(memoryHistory, false);
      
      // ✅ 只有当 normalize 改变了历史时才保存
      if (invalidCount > 0 || blankAssistantCount > 0 || normalizedHistory.size() != memoryHistory.size())
      {
        saveHistory(normalizedHistory);
        FileLogger.i(TAG, "🧹 [CLEANUP] 清理完成 | 无效消息：" + invalidCount + " | 空白助手消息：" + blankAssistantCount + " | 新历史：" + normalizedHistory.size() + " 条");
      }
      else
      {
        FileLogger.d(TAG, "🧹 [CLEANUP] 无需清理，历史保持原样");
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
  
  // 🔗 新增：带 messageId 的 addAssistantMessage 重载
  public void addAssistantMessage(String message, String messageId)
  {
    JSONObject msg = createMessage("assistant", message);
    if (messageId != null && !messageId.isEmpty())
    {
      try
      {
        msg.put("id", messageId);
        // 从预留集合中移除，标记为已确认
        reservedMessageIds.remove(messageId);
        FileLogger.d(TAG, "✅ [CONFIRM] 助手消息已确认 | id=" + messageId);
      }
      catch (JSONException e)
      {
        FileLogger.e(TAG, "❌ [CONFIRM] 添加 messageId 失败", e);
      }
    }
    addRawMessage(msg);
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
                            // 🔧 #774530570947 重构：委托给 isValidToolCallMessage 进行验证
              if (!isValidToolCallMessage(message))
              {
                FileLogger.w(TAG, "[addRawMessage] Skip: invalid tool call message");
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
   * 🔧 #763065048722 新增：检查 JSON 语法完整性
   * 检测括号匹配、引号闭合等基本语法结构
   */
  private boolean isJsonSyntaxComplete(String jsonStr)
  {
    if (jsonStr == null || jsonStr.trim().isEmpty())
    {
      return false;
    }
    
    // 1. 检查括号匹配
    int braceCount = 0;
    int bracketCount = 0;
    boolean inString = false;
    boolean escaped = false;
    
    for (int i = 0; i < jsonStr.length(); i++)
    {
      char c = jsonStr.charAt(i);
      
      if (escaped)
      {
        escaped = false;
        continue;
      }
      
      if (c == '\\' && inString)
      {
        escaped = true;
        continue;
      }
      
      if (c == '"' && !escaped)
      {
        inString = !inString;
        continue;
      }
      
      if (!inString)
      {
        if (c == '{') braceCount++;
        else if (c == '}') braceCount--;
        else if (c == '[') bracketCount++;
        else if (c == ']') bracketCount--;
        
        // 如果括号计数为负，说明闭合符号多于开启符号
        if (braceCount < 0 || bracketCount < 0)
        {
          FileLogger.d(TAG, "[isJsonSyntaxComplete] Mismatched brackets at position " + i);
          return false;
        }
      }
    }
    
    // 检查是否所有括号都闭合
    if (braceCount != 0 || bracketCount != 0)
    {
      FileLogger.d(TAG, "[isJsonSyntaxComplete] Unclosed brackets: brace=" + braceCount + ", bracket=" + bracketCount);
      return false;
    }
    
    // 检查是否在字符串中间结束
    if (inString)
    {
      FileLogger.d(TAG, "[isJsonSyntaxComplete] Unclosed string");
      return false;
    }
    
    // 2. 检查是否以合理的字符开始和结束
    String trimmed = jsonStr.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
    {
      FileLogger.d(TAG, "[isJsonSyntaxComplete] Does not start with { or end with }");
      return false;
    }
    
    return true;
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
  
  // 🔗 新增：生成并预留一个消息 ID
  public String reserveMessageId()
  {
    String messageId = generateMessageId();
    reservedMessageIds.add(messageId);
    FileLogger.d(TAG, "🔖 [RESERVE] 预留消息 ID | id=" + messageId + " | 当前预留数=" + reservedMessageIds.size());
    return messageId;
  }
  
  // 🔗 新增：丢弃未使用的预留 ID（当消息被丢弃时调用）
  public void discardReservedMessageId(String messageId)
  {
    if (messageId != null && reservedMessageIds.remove(messageId))
    {
      FileLogger.d(TAG, "🗑️ [DISCARD] 丢弃预留 ID | id=" + messageId);
    }
  }
  
  // 🔗 新增：检查某个 ID 是否是预留中的 ID
  public boolean isReservedMessageId(String messageId)
  {
    return reservedMessageIds.contains(messageId);
  }
  
  // 🔗 生成唯一消息 ID（时间戳 + UUID）
  private static String generateMessageId()
  {
    return "msg_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
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
            // 🔧 #774530570947 新增：严格检查 JSON 对象开头，拦截非法结构如 {5LiU..."path": ...}
            String trimmedArgs = argumentsStr.trim();
            if (trimmedArgs.startsWith("{"))
            {
              if (trimmedArgs.length() > 1)
              {
                char secondChar = trimmedArgs.charAt(1);
                if (secondChar != '"' && secondChar != '}')
                {
                  FileLogger.w(TAG, "[isValidToolCallMessage] Invalid: JSON object does not start with quoted key or empty object. Second char: " + secondChar);
                  return false;
                }
              }
            }

            FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] ========== Start Validation (Load) ==========");
            FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Raw arguments: " + argumentsStr);
            // 🔧 #774530570947 新增：使用 Gson 进行额外验证（仅用于调试，不作为判定依据）
            try
            {
              com.google.gson.Gson gson = new com.google.gson.Gson();
              java.util.Map<String, Object> gsonParsed = gson.fromJson(argumentsStr, java.util.Map.class);
              FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Gson parsed successfully. Keys: " + gsonParsed.keySet());
            }
            catch (Exception e)
            {
              FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Gson threw exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Raw arguments length: " + argumentsStr.length());

            
            FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Checking hasUnquotedStringValues...");
            // General validation: check for unquoted string values
            if (hasUnquotedStringValues(argumentsStr))
            {
              FileLogger.d(TAG, "[isValidToolCallMessage] Invalid: unquoted string values");
              return false;
            }
            
            FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] Checking isJsonSyntaxComplete...");
            // 🔧 #763065048722 新增：严格语法完整性检查
            if (!isJsonSyntaxComplete(argumentsStr))
            {
              FileLogger.d(TAG, "[isValidToolCallMessage] Invalid: syntax incomplete");
              return false;
            }
            
            try
            {
              JSONTokener tokener = new JSONTokener(argumentsStr);
              Object parsed = tokener.nextValue();
              
                FileLogger.d(TAG, "[DEBUG_JSON_VALIDATION_LOAD] JSONTokener parsed successfully. Type: " + parsed.getClass().getSimpleName());
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
      
      // 🔍 新增：记录输出历史的统计信息（精简版）
      int userMessageCount = 0;
      int preservedMultimodalCount = 0;
      for (int i = 0; i < list.size(); i++)
      {
        JSONObject msg = list.get(i);
        String role = msg.optString("role", "unknown");
        Object contentObj = msg.opt("content");
        
        if ("user".equals(role))
        {
          userMessageCount++;
          if (contentObj instanceof JSONArray)
          {
            preservedMultimodalCount++;
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
    
    // 2. 异步保存到 SP（持久化）
    try
    {
      JSONArray historyArray = new JSONArray(history);
      sharedPreferences.edit()
          .putString(KEY_HISTORY, historyArray.toString())
          .putInt("current_max_rounds", currentMaxRounds)
          .apply();  // apply() 异步没关系，因为读取的是内存
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