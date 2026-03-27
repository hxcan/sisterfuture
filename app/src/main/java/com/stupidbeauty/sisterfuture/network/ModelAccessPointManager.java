  /**
   * 🔥 #4657 报告当前接入点不可用，切换到下一个并增加计数器
   * @return 当前连续失败次数
   */
  public int reportCurrentAccessPointUnavailable()
  {
    // 缓存切换前的状态
    int oldIndex = currentAccessPointIndex;
    String oldApName = (oldIndex >= 0 && oldIndex < accessPoints.size()) 
        ? accessPoints.get(oldIndex).getName() : "N/A";
    
    // 递增计数器
    consecutiveFailures++;
    
    FileLogger.d(TAG, "📊 [FAILURE_COUNT] reportCurrentAccessPointUnavailable: " + consecutiveFailures);
    FileLogger.d(TAG, "🔄 [AP_SWITCH] 切换前：index=" + oldIndex + ", name=" + oldApName + ", totalSize=" + accessPoints.size());
    
    // 切换到下一个接入点
    if (currentAccessPointIndex < accessPoints.size() - 1)
    {
      currentAccessPointIndex++;
      FileLogger.d(TAG, "➡️ [AP_SWITCH] 正常切换到下一个：index=" + currentAccessPointIndex);
    }
    else
    {
      currentAccessPointIndex = 0; // 循环回到第一个访问点
      FileLogger.d(TAG, "🔁 [AP_SWITCH] 循环切换到第一个：index=0");
    }
    
    // 记录切换后的状态
    int newIndex = currentAccessPointIndex;
    String newApName = (newIndex >= 0 && newIndex < accessPoints.size()) 
        ? accessPoints.get(newIndex).getName() : "N/A";
    
    int threshold = Math.max(1, accessPoints.size() * FAILURE_THRESHOLD_MULTIPLIER);
    FileLogger.i(TAG, "🔥 [AP_SWITCH_COMPLETE] 切换完成：" + oldIndex + "(" + oldApName + ") → " + 
                  newIndex + "(" + newApName + ") | 连续失败次数：" + consecutiveFailures + "/" + threshold);
    
    return consecutiveFailures;
  }