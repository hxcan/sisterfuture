package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * SessionManager - 会话管理器（阶段 1：单会话模式）
 * 
 * 负责管理会话的生命周期，包括创建、获取、清空和持久化。
 * 
 * ## 设计目标
 * 1. 实现单会话模式（阶段 1）
 * 2. 与 ContextManager 无缝集成
 * 3. 支持数据持久化
 * 4. 架构预留多会话能力（阶段 2）
 * 
 * ## 核心功能
 * - createSession(): 创建新会话
 * - getCurrentSession(): 获取当前会话
 * - clearCurrentSession(): 清空当前会话（集成 ToolCallTracker 清理）
 * - saveSession(): 保存会话到本地存储
 * - loadDefaultSession(): 加载默认会话
 * 
 * ## 与 ContextManager 集成
 * - 写入时同步：ContextManager.addMessage() 时同步写入 SessionManager
 * - 读取时兜底：ContextManager.getHistory() 优先从 SessionManager 读取，无数据则从原有历史列表读取
 * - 重置时清理：reset_conversation_context 工具调用 SessionManager.clearCurrentSession()
 * 
 * ## 约束条件
 * - 阶段 1 仅使用单会话（default_session）
 * - 行为与原有 ContextManager 完全一致
 * - 支持回滚到旧版本
 * 
 * @author 未来姐姐
 * @since 2026-03-22
 */
public class SessionManager
{
  private static final String TAG = "SessionManager";
  private static final String PREF_NAME = "session_manager";
  private static final String KEY_CURRENT_SESSION_ID = "current_session_id";
  private static final String KEY_SESSION_PREFIX = "session_";
  
  private static SessionManager instance;
  private Context context;
  private SharedPreferences sharedPreferences;
  private Session currentSession;
  
  /**
   * 私有构造函数（单例模式）
   */
  private SessionManager(Context context)
  {
    this.context = context.getApplicationContext();
    this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    
    // 加载默认会话
    loadDefaultSession();
    
    FileLogger.d(TAG, "✅ SessionManager 初始化完成");
  }
  
  /**
   * 获取单例实例
   * 
   * @param context 上下文
   * @return SessionManager 实例
   */
  public static synchronized SessionManager getInstance(Context context)
  {
    if (instance == null)
    {
      instance = new SessionManager(context);
    }
    return instance;
  }
  
  /**
   * 创建新会话
   * 
   * @param title 会话标题（可选）
   * @return 创建的会话对象
   */
  public Session createSession(String title)
  {
    // 阶段 1：仅支持单会话，如果已存在则返回现有会话
    if (currentSession != null)
    {
      FileLogger.w(TAG, "⚠️ 阶段 1 仅支持单会话，返回现有会话：sessionId=" + currentSession.getSessionId());
      return currentSession;
    }
    
    // 创建默认会话
    String sessionId = Session.DEFAULT_SESSION_ID;
    Session session = new Session(sessionId, title);
    
    // 设置为当前会话
    this.currentSession = session;
    
    // 保存会话 ID
    sharedPreferences.edit()
      .putString(KEY_CURRENT_SESSION_ID, sessionId)
      .apply();
    
    // 持久化会话
    saveSession(session);
    
    FileLogger.i(TAG, "✅ 创建会话：sessionId=" + sessionId + ", title=" + title);
    return session;
  }
  
  /**
   * 获取当前会话
   * 
   * @return 当前会话对象
   */
  public Session getCurrentSession()
  {
    if (currentSession == null)
    {
      // 如果当前会话为空，尝试加载默认会话
      loadDefaultSession();
    }
    
    if (currentSession == null)
    {
      // 如果仍然为空，创建默认会话
      FileLogger.w(TAG, "⚠️ 当前会话为空，创建默认会话");
      return createSession("默认会话");
    }
    
    return currentSession;
  }
  
  /**
   * 清空当前会话（集成 ToolCallTracker 清理）
   * 
   * 此方法在 reset_conversation_context 工具调用时使用。
   */
  public void clearCurrentSession()
  {
    Session session = getCurrentSession();
    if (session != null)
    {
      // 清空历史消息
      session.clearHistory();
      
      // 清空 ToolCallTracker
      session.clearToolCallTracker();
      
      // 持久化
      saveSession(session);
      
      FileLogger.i(TAG, "🧹 清空当前会话：sessionId=" + session.getSessionId());
    }
  }
  
  /**
   * 保存会话到本地存储
   * 
   * @param session 要保存的会话对象
   */
  public void saveSession(Session session)
  {
    if (session == null)
    {
      FileLogger.w(TAG, "⚠️ 保存会话失败：会话对象为空");
      return;
    }
    
    String key = KEY_SESSION_PREFIX + session.getSessionId();
    String sessionJson = session.toJson().toString();
    
    sharedPreferences.edit()
      .putString(key, sessionJson)
      .apply();
    
    FileLogger.d(TAG, "💾 保存会话：sessionId=" + session.getSessionId() + ", messageCount=" + session.getMessageCount());
  }
  
  /**
   * 加载默认会话
   * 
   * 从本地存储加载默认会话，如果不存在则返回 null。
   */
  private void loadDefaultSession()
  {
    String sessionId = sharedPreferences.getString(KEY_CURRENT_SESSION_ID, Session.DEFAULT_SESSION_ID);
    String key = KEY_SESSION_PREFIX + sessionId;
    String sessionJson = sharedPreferences.getString(key, null);
    
    if (sessionJson != null)
    {
      try
      {
        JSONObject json = new JSONObject(sessionJson);
        this.currentSession = new Session(json);
        FileLogger.i(TAG, "✅ 加载默认会话：sessionId=" + sessionId + ", messageCount=" + currentSession.getMessageCount());
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "❌ 加载默认会话失败：" + e.getMessage(), e);
        this.currentSession = null;
      }
    }
    else
    {
      FileLogger.d(TAG, "📋 默认会话不存在，首次启动");
      this.currentSession = null;
    }
  }
  
  /**
   * 从本地存储加载会话
   * 
   * @param sessionId 会话 ID
   * @return 会话对象，如果不存在则返回 null
   */
  public Session loadSession(String sessionId)
  {
    String key = KEY_SESSION_PREFIX + sessionId;
    String sessionJson = sharedPreferences.getString(key, null);
    
    if (sessionJson != null)
    {
      try
      {
        JSONObject json = new JSONObject(sessionJson);
        Session session = new Session(json);
        FileLogger.d(TAG, "✅ 加载会话：sessionId=" + sessionId + ", messageCount=" + session.getMessageCount());
        return session;
      }
      catch (Exception e)
      {
        FileLogger.e(TAG, "❌ 加载会话失败：" + e.getMessage(), e);
        return null;
      }
    }
    
    FileLogger.d(TAG, "📋 会话不存在：sessionId=" + sessionId);
    return null;
  }
  
  /**
   * 切换到指定会话（阶段 2 扩展）
   * 
   * @param sessionId 目标会话 ID
   * @return true=切换成功，false=切换失败
   */
  public boolean switchToSession(String sessionId)
  {
    // 阶段 1：不支持切换，仅支持默认会话
    if (!sessionId.equals(Session.DEFAULT_SESSION_ID))
    {
      FileLogger.w(TAG, "⚠️ 阶段 1 仅支持单会话，无法切换到：sessionId=" + sessionId);
      return false;
    }
    
    // 加载目标会话
    Session session = loadSession(sessionId);
    if (session == null)
    {
      FileLogger.w(TAG, "⚠️ 切换会话失败：会话不存在，sessionId=" + sessionId);
      return false;
    }
    
    this.currentSession = session;
    
    // 保存当前会话 ID
    sharedPreferences.edit()
      .putString(KEY_CURRENT_SESSION_ID, sessionId)
      .apply();
    
    FileLogger.i(TAG, "✅ 切换会话：sessionId=" + sessionId);
    return true;
  }
  
  /**
   * 获取当前会话 ID
   * 
   * @return 当前会话 ID
   */
  public String getCurrentSessionId()
  {
    return getCurrentSession().getSessionId();
  }
  
  /**
   * 重置 SessionManager（用于测试或特殊场景）
   */
  public void reset()
  {
    this.currentSession = null;
    sharedPreferences.edit()
      .clear()
      .apply();
    
    FileLogger.i(TAG, "🧹 SessionManager 已重置");
  }
  
  /**
   * 获取会话统计信息
   * 
   * @return 统计信息字符串
   */
  public String getStats()
  {
    Session session = getCurrentSession();
    if (session == null)
    {
      return "SessionManager[无当前会话]";
    }
    
    return String.format("SessionManager[currentSession=%s, messageCount=%d, toolCallCount=%d]",
      session.getSessionId(), session.getMessageCount(), session.getHistory().size());
  }
}