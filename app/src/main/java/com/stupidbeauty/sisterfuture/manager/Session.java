package com.stupidbeauty.sisterfuture.manager;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * Session - 会话数据类
 * 
 * 用于存储单个会话的完整信息，包括元数据和历史消息。
 * 
 * ## 设计目标
 * 1. 支持单会话模式（阶段 1）
 * 2. 与 ContextManager 无缝集成
 * 3. 支持数据持久化
 * 4. 架构预留多会话能力（阶段 2）
 * 
 * ## 核心字段
 * - sessionId: 会话唯一标识（默认："default_session"）
 * - title: 会话标题（可选）
 * - createdAt: 创建时间戳
 * - lastActiveAt: 最后活跃时间戳
 * - messageCount: 消息数量
 * - history: 历史消息列表
 * - repliedToolCallIds: 已回复的工具调用 ID 集合（ToolCallTracker）
 * 
 * ## 约束条件
 * - 阶段 1 仅使用单会话（default_session）
 * - 行为与原有 ContextManager 完全一致
 * - 支持回滚到旧版本
 * 
 * @author 未来姐姐
 * @since 2026-03-22
 */
public class Session
{
  private static final String TAG = "Session";
  
  /** 默认会话 ID（阶段 1 唯一会话） */
  public static final String DEFAULT_SESSION_ID = "default_session";
  
  /** 会话唯一标识 */
  private String sessionId;
  
  /** 会话标题（可选） */
  private String title;
  
  /** 创建时间戳（毫秒） */
  private long createdAt;
  
  /** 最后活跃时间戳（毫秒） */
  private long lastActiveAt;
  
  /** 消息数量 */
  private int messageCount;
  
  /** 历史消息列表 */
  private List<JSONObject> history;
  
  /** 已回复的工具调用 ID 集合（ToolCallTracker） */
  private Set<String> repliedToolCallIds;
  
  /**
   * 构造函数 - 创建新会话
   * 
   * @param sessionId 会话 ID
   * @param title 会话标题（可选，null 则使用默认标题）
   */
  public Session(String sessionId, String title)
  {
    this.sessionId = sessionId;
    this.title = title != null ? title : "会话 " + sessionId;
    this.createdAt = System.currentTimeMillis();
    this.lastActiveAt = this.createdAt;
    this.messageCount = 0;
    this.history = new ArrayList<>();
    this.repliedToolCallIds = new HashSet<>();
    
    FileLogger.d(TAG, "✅ 创建新会话：sessionId=" + sessionId + ", title=" + title);
  }
  
  /**
   * 构造函数 - 从 JSON 恢复会话
   * 
   * @param json 会话 JSON 数据
   */
  public Session(JSONObject json)
  {
    try
    {
      this.sessionId = json.optString("sessionId", DEFAULT_SESSION_ID);
      this.title = json.optString("title", "会话");
      this.createdAt = json.optLong("createdAt", System.currentTimeMillis());
      this.lastActiveAt = json.optLong("lastActiveAt", this.createdAt);
      this.messageCount = json.optInt("messageCount", 0);
      
      // 恢复历史消息
      this.history = new ArrayList<>();
      if (json.has("history"))
      {
        JSONArray historyArray = json.getJSONArray("history");
        for (int i = 0; i < historyArray.length(); i++)
        {
          this.history.add(historyArray.getJSONObject(i));
        }
      }
      
      // 恢复 ToolCallTracker
      this.repliedToolCallIds = new HashSet<>();
      if (json.has("repliedToolCallIds"))
      {
        JSONArray idsArray = json.getJSONArray("repliedToolCallIds");
        for (int i = 0; i < idsArray.length(); i++)
        {
          this.repliedToolCallIds.add(idsArray.getString(i));
        }
      }
      
      FileLogger.d(TAG, "✅ 从 JSON 恢复会话：sessionId=" + sessionId + ", messageCount=" + messageCount);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ 从 JSON 恢复会话失败：" + e.getMessage(), e);
      // 初始化默认值
      this.sessionId = DEFAULT_SESSION_ID;
      this.title = "会话";
      this.createdAt = System.currentTimeMillis();
      this.lastActiveAt = this.createdAt;
      this.messageCount = 0;
      this.history = new ArrayList<>();
      this.repliedToolCallIds = new HashSet<>();
    }
  }
  
  // ========== Getter/Setter 方法 ==========
  
  public String getSessionId()
  {
    return sessionId;
  }
  
  public String getTitle()
  {
    return title;
  }
  
  public void setTitle(String title)
  {
    this.title = title;
  }
  
  public long getCreatedAt()
  {
    return createdAt;
  }
  
  public long getLastActiveAt()
  {
    return lastActiveAt;
  }
  
  public void updateLastActiveAt()
  {
    this.lastActiveAt = System.currentTimeMillis();
  }
  
  public int getMessageCount()
  {
    return messageCount;
  }
  
  public List<JSONObject> getHistory()
  {
    return history;
  }
  
  public void setHistory(List<JSONObject> history)
  {
    this.history = history;
    this.messageCount = history != null ? history.size() : 0;
  }
  
  /**
   * 添加消息到历史
   * 
   * @param message 消息对象
   */
  public void addMessage(JSONObject message)
  {
    if (message == null)
    {
      return;
    }
    
    this.history.add(message);
    this.messageCount = this.history.size();
    this.updateLastActiveAt();
    
    FileLogger.d(TAG, "📝 添加消息到会话：sessionId=" + sessionId + ", messageCount=" + messageCount);
  }
  
  /**
   * 清空历史消息
   */
  public void clearHistory()
  {
    this.history.clear();
    this.messageCount = 0;
    this.repliedToolCallIds.clear();
    this.updateLastActiveAt();
    
    FileLogger.d(TAG, "🧹 清空会话历史：sessionId=" + sessionId);
  }
  
  // ========== ToolCallTracker 方法 ==========
  
  /**
   * 检查工具调用 ID 是否已回复
   * 
   * @param toolCallId 工具调用 ID
   * @return true=已回复，false=未回复
   */
  public boolean isToolCallReplied(String toolCallId)
  {
    return repliedToolCallIds.contains(toolCallId);
  }
  
  /**
   * 标记工具调用为已回复
   * 
   * @param toolCallId 工具调用 ID
   */
  public void markToolCallAsReplied(String toolCallId)
  {
    if (toolCallId != null && !toolCallId.isEmpty())
    {
      this.repliedToolCallIds.add(toolCallId);
      FileLogger.d(TAG, "✅ 标记工具调用为已回复：toolCallId=" + toolCallId);
    }
  }
  
  /**
   * 清空 ToolCallTracker
   */
  public void clearToolCallTracker()
  {
    int count = this.repliedToolCallIds.size();
    this.repliedToolCallIds.clear();
    FileLogger.d(TAG, "🧹 清空 ToolCallTracker：sessionId=" + sessionId + ", 清理数量=" + count);
  }
  
  // ========== JSON 序列化 ==========
  
  /**
   * 将会话转换为 JSON 对象（用于持久化）
   * 
   * @return JSON 对象
   */
  public JSONObject toJson()
  {
    JSONObject json = new JSONObject();
    try
    {
      json.put("sessionId", sessionId);
      json.put("title", title);
      json.put("createdAt", createdAt);
      json.put("lastActiveAt", lastActiveAt);
      json.put("messageCount", messageCount);
      
      // 序列化历史消息
      JSONArray historyArray = new JSONArray();
      for (JSONObject message : history)
      {
        historyArray.put(message);
      }
      json.put("history", historyArray);
      
      // 序列化 ToolCallTracker
      JSONArray idsArray = new JSONArray();
      for (String id : repliedToolCallIds)
      {
        idsArray.put(id);
      }
      json.put("repliedToolCallIds", idsArray);
    }
    catch (Exception e)
    {
      FileLogger.e(TAG, "❌ 序列化会话失败：" + e.getMessage(), e);
    }
    
    return json;
  }
  
  /**
   * 获取会话统计信息（用于日志）
   * 
   * @return 统计信息字符串
   */
  public String getStats()
  {
    return String.format("Session[sessionId=%s, title=%s, messageCount=%d, toolCallCount=%d, createdAt=%d]",
      sessionId, title, messageCount, repliedToolCallIds.size(), createdAt);
  }
}