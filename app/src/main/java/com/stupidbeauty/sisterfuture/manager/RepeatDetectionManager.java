package com.stupidbeauty.sisterfuture.manager;

import android.util.Log;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 重复回复检测管理器
 * 
 * 功能：检测 AI 是否连续返回完全相同的回复内容
 * 当连续 3 次回复相同时，触发接入点切换
 * 
 * @author sisterfuture
 * @since 2026-04-13
 */
public class RepeatDetectionManager
{
  private static final String TAG = "RepeatDetectionManager";
  private static final int REPEAT_THRESHOLD = 3; // 连续重复次数阈值
  
  private List<String> recentReplies;
  private int consecutiveRepeatCount;
  private String lastReply;

  public RepeatDetectionManager()
  {
    recentReplies = new ArrayList<>();
    consecutiveRepeatCount = 0;
    lastReply = null;
  }

  /**
   * 记录新的回复并检测是否连续重复
   * 
   * @param reply AI 的回复内容
   * @return 如果检测到连续重复返回 true，否则返回 false
   */
  public boolean recordAndCheck(String reply)
  {
    if (reply == null || reply.trim().isEmpty())
    {
      FileLogger.d(TAG, "⚠️ [REPEAT_CHECK] 回复为空，跳过检测");
      return false;
    }

    FileLogger.d(TAG, "🔍 [REPEAT_CHECK] 收到回复 | 长度=" + reply.length() + " | 前 50 字符=" + 
      (reply.length() > 50 ? reply.substring(0, 50) + "..." : reply));

    // 检查是否与上一次回复相同
    if (lastReply != null && lastReply.equals(reply))
    {
      consecutiveRepeatCount++;
      FileLogger.w(TAG, "⚠️ [REPEAT_DETECTED] 检测到重复回复 | 连续次数=" + consecutiveRepeatCount + " / " + REPEAT_THRESHOLD);
    }
    else
    {
      // 回复不同，重置计数器
      consecutiveRepeatCount = 1;
      FileLogger.d(TAG, "✅ [REPLY_CHANGED] 回复内容已变化，重置计数器");
    }

    // 更新历史记录
    lastReply = reply;
    recentReplies.add(reply);
    
    // 保持队列大小不超过阈值
    if (recentReplies.size() > REPEAT_THRESHOLD)
    {
      recentReplies.remove(0);
    }

    // 检查是否达到阈值
    boolean shouldSwitch = (consecutiveRepeatCount >= REPEAT_THRESHOLD);
    
    if (shouldSwitch)
    {
      FileLogger.e(TAG, "🚨 [REPEAT_THRESHOLD_REACHED] 连续重复次数达到阈值！建议切换接入点");
    }

    return shouldSwitch;
  }

  /**
   * 重置检测器（在切换接入点后调用）
   */
  public void reset()
  {
    FileLogger.i(TAG, "🔄 [RESET] 重置重复检测器");
    recentReplies.clear();
    consecutiveRepeatCount = 0;
    lastReply = null;
  }

  /**
   * 获取当前连续重复次数
   */
  public int getConsecutiveRepeatCount()
  {
    return consecutiveRepeatCount;
  }

  /**
   * 获取最近的回复历史
   */
  public List<String> getRecentReplies()
  {
    return new ArrayList<>(recentReplies);
  }
}