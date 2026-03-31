package com.stupidbeauty.sisterfuture.network;

import java.util.ArrayList;
import java.util.List;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import com.stupidbeauty.sisterfuture.utils.FileLogger;



/**
 * 模型接入点管理器
 * 负责管理多个模型服务的接入点，支持动态添加和持久化存储
 */
public class ModelAccessPointManager
{
  private static final String TAG = "ModelAccessPointManager";
  private List<ModelAccessPoint> accessPoints;
  private int currentAccessPointIndex;
  private static final String PERSISTENT_FILE_NAME = "model_access_points.json"; // 持久化存储文件名
  private Context context; // 上下文用于访问应用私有目录
  
  // 🔥 #4657 接入点死循环修复 v2 - 连续失败计数器
  private int consecutiveFailures = 0;
  private static final int FAILURE_THRESHOLD_MULTIPLIER = 2; // 阈值 = 接入点数量 × 2
  
  /** 
   * 获取当前接入点数量 
   * @return 接入点数量 
   */ 
  public int getAccessPointCount() 
  {
    return accessPoints.size(); 
  } 

  /** 
   * 获取所有接入点列表 
   * @return 接入点列表（直接返回内部字段） 
   */ 
  public List<ModelAccessPoint> getAllAccessPoints() 
  { 
    return accessPoints; 
  } 

  /** 
   * 检查是否存在可用接入点 
   * @return 如果存在至少一个接入点则返回 true，否则返回 false 
   */ 
  public boolean hasAvailableAccessPoints() 
  { 
    return !accessPoints.isEmpty(); 
  }

  /**
   * 构造函数
   * @param context 应用上下文，用于文件操作
   */
  public ModelAccessPointManager(Context context) // 接收上下文参数
  {
    this.context = context;
    this.accessPoints = new ArrayList<>();
    loadFromPersistentStorage(); // 启动时先加载持久化数据
    
    // ✅ 已移除：不再添加任何默认访问点
    // 新用户首次启动时 accessPoints.isEmpty() == true，将触发 #4547 的空状态检测逻辑
    if (accessPoints.isEmpty()) {
      // ❌ 删除了以下代码：
      // addAccessPoint("phone Qwen3-30B", ...);
      // addAccessPoint("gx10 Qwen3-30B", ...);
      // ... (所有 6 个预置接入点)
      // addAccessPoint("Aliyun Qwen3.5-35b-a3b", ... aliyunKey ...);
    }

    this.currentAccessPointIndex = 0; // 默认指向第一个访问点
    this.consecutiveFailures = 0; // 初始化计数器
  }

  /**
   * 动态添加新的接入点，并立即持久化存储
   * @param name 接入点名称
   * @param baseUrl 基础 URL
   * @param chatEndpoint 聊天接口端点
   * @param modelName 模型名称
   */
  public void addAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName) {
      ModelAccessPoint newPoint = new ModelAccessPoint(name, baseUrl, chatEndpoint, modelName);
      accessPoints.add(newPoint);
      saveToPersistentStorage(); // 添加后立即保存
      FileLogger.i(TAG, "Added new access point: " + name + " and saved to storage");
  }

  /**
   * 动态添加新的接入点（带 apiKey），并立即持久化存储
   * @param name 接入点名称
   * @param baseUrl 基础 URL
   * @param chatEndpoint 聊天接口端点
   * @param modelName 模型名称
   * @param apiKey API 密钥
   */
  public void addAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName, String apiKey) {
      ModelAccessPoint newPoint = new ModelAccessPoint(name, baseUrl, chatEndpoint, modelName, apiKey);
      accessPoints.add(newPoint);
      saveToPersistentStorage(); // 添加后立即保存
      FileLogger.i(TAG, "Added new access point with apiKey: " + name + " and saved to storage");
  }

  /**
   * 内部方法：添加已创建的 AccessPoint 对象（带 apiKey 支持）
   * @param point 要添加的 AccessPoint 实例
   */
  public void addAccessPointInternal(ModelAccessPoint point) {
      accessPoints.add(point);
      saveToPersistentStorage();
      FileLogger.i(TAG, "Added access point with internal method: " + point.getName());
  }

  /**
   * 从持久化存储中加载接入点列表
   */
  private void loadFromPersistentStorage() {
    File file = new File(context.getFilesDir(), PERSISTENT_FILE_NAME);
    if (!file.exists()) {
      FileLogger.i(TAG, "Persistent file not found, using default access points");
      return;
    }
    
    try (FileInputStream fis = context.openFileInput(PERSISTENT_FILE_NAME)) {
      byte[] data = new byte[(int) file.length()];
      fis.read(data);
      String jsonStr = new String(data, StandardCharsets.UTF_8);
      
      JSONArray jsonArray = new JSONArray(jsonStr);
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject obj = jsonArray.getJSONObject(i);
        
        // ✅ 新增：尝试读取 apiKey 字段（如果存在）
        String apiKey = null;
        try {
          apiKey = obj.getString("apiKey");
          FileLogger.d(TAG, "Loaded apiKey for access point " + obj.getString("name"));
        } catch (JSONException e) {
          FileLogger.d(TAG, "No apiKey found for access point, setting to null");
        }
        
        accessPoints.add(new ModelAccessPoint(
          obj.getString("name"),
          obj.getString("baseUrl"),
          obj.getString("chatEndpoint"),
          obj.getString("modelName"),
          apiKey // 传入 apiKey 参数
        ));
      }
    } catch (Exception e) {
      FileLogger.e(TAG, "Failed to load from persistent storage", e);
    }
  }

  /**
   * 保存当前接入点列表到持久化存储
   */
  private void saveToPersistentStorage() {
    try (FileOutputStream fos = context.openFileOutput(PERSISTENT_FILE_NAME, Context.MODE_PRIVATE)) {
      JSONArray jsonArray = new JSONArray();
      for (ModelAccessPoint point : accessPoints) {
        JSONObject obj = new JSONObject();
        obj.put("name", point.getName());
        obj.put("baseUrl", point.getBaseUrl());
        obj.put("chatEndpoint", point.getChatEndpoint());
        obj.put("modelName", point.getModelName());
        
        // ✅ 新增：保存 apiKey 字段
        if (point.getApiKey() != null) {
          obj.put("apiKey", point.getApiKey());
        }
        
        jsonArray.put(obj);
      }
      fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
      FileLogger.i(TAG, "Saved " + accessPoints.size() + " access points to persistent storage");
    } catch (Exception e) {
      FileLogger.e(TAG, "Failed to save to persistent storage", e);
    }
  }

  /**
   * 获取当前接入点的基础 URL
   * @return 当前接入点的基础 URL，如果索引越界则返回 null
   */
  public String getCurrentBaseUrl()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      FileLogger.i(TAG, "getCurrentBaseUrl, access point index: " + currentAccessPointIndex + ", model name: " + accessPoints.get(currentAccessPointIndex).getBaseUrl());
      return accessPoints.get(currentAccessPointIndex).getBaseUrl();
    }
    return null;
  }

  /**
   * 获取当前接入点的模型名称
   * @return 当前接入点的模型名称，如果索引越界则返回 null
   */
  public String getCurrentModelName()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      FileLogger.i(TAG, "getCurrentModelName, access point index: " + currentAccessPointIndex + ", model name: " + accessPoints.get(currentAccessPointIndex).getModelName());
      return accessPoints.get(currentAccessPointIndex).getModelName();
    }
    return null;
  }

  /**
   * 获取当前接入点的聊天接口端点
   * @return 当前接入点的聊天接口端点，如果索引越界则返回 null
   */
  public String getCurrentChatEndpoint()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      return accessPoints.get(currentAccessPointIndex).getChatEndpoint();
    }
    return null;
  }

  /**
   * 获取当前接入点对象
   * @return 当前接入点对象，如果索引越界则返回 null
   */
  public ModelAccessPoint getCurrentAccessPoint()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      return accessPoints.get(currentAccessPointIndex);
    }
    return null;
  }

  /**
   * 获取当前激活的接入点索引
   * @return 当前激活的接入点索引（从 0 开始）
   */
  public int getCurrentAccessPointIndex()
  {
    return currentAccessPointIndex;
  }

  /**
   * 🔥 #4657 检查连续失败次数是否超过阈值
   * @return true 如果超过阈值，需要触发备用接入点向导
   */
  public boolean checkFailureThreshold() {
    int threshold = Math.max(1, accessPoints.size() * FAILURE_THRESHOLD_MULTIPLIER);
    boolean exceeded = consecutiveFailures >= threshold;
    
    FileLogger.d(TAG, "🎯 [THRESHOLD_CHECK] consecutiveFailures=" + consecutiveFailures + 
                  ", threshold=" + threshold + ", exceeded=" + exceeded);
    
    if (exceeded) {
      FileLogger.w(TAG, "🔥 连续失败次数超过阈值：" + consecutiveFailures + " >= " + threshold + " (接入点数量：" + accessPoints.size() + ")");
    }
    
    return exceeded;
  }

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

  /**
   * 🔥 #4657 重置连续失败计数器（请求成功时调用）
   */
  public void resetFailureCount() {
    if (consecutiveFailures > 0) {
      FileLogger.d(TAG, "🔄 [FAILURE_COUNT] resetFailureCount: " + consecutiveFailures + " -> 0");
      FileLogger.i(TAG, "✅ 请求成功，重置连续失败计数器：" + consecutiveFailures + " → 0");
      consecutiveFailures = 0;
    }
  }

  /**
   * 🔥 #4657 获取当前连续失败次数（用于调试）
   * @return 连续失败次数
   */
  public int getConsecutiveFailures() {
    return consecutiveFailures;
  }

  /**
   * 从列表中删除指定索引的接入点。
   * @param index 要删除的接入点的索引（从 0 开始）
   * @return 如果删除成功返回 true，否则返回 false（例如索引越界）
   */
  public boolean removeAccessPoint(int index) {
    if (index < 0 || index >= accessPoints.size()) {
      FileLogger.e(TAG, "Invalid index: " + index + ". Available range is 0 to " + (accessPoints.size() - 1));
      return false;
    }

    // 禁止删除当前激活的接入点
    if (accessPoints.get(index).equals(getCurrentAccessPoint())) {
      FileLogger.e(TAG, "Cannot delete the currently active access point. Please switch to another access point first.");
      return false;
    }

    // 🔧 FIX #4624: 执行删除操作前缓存当前索引值
    int oldIndex = currentAccessPointIndex;
    
    // 执行删除操作并更新当前索引（如果需要）
    accessPoints.remove(index);
    saveToPersistentStorage();
    
    // 🔧 修复核心：删除后立即验证索引有效性
    if (index <= currentAccessPointIndex && accessPoints.size() > 0) {
      // 如果删除的是当前索引或之前的节点，且列表不为空，则索引应减 1
      currentAccessPointIndex = Math.max(0, currentAccessPointIndex - 1);
      FileLogger.i(TAG, "Removed node at index=" + index + ", adjusted currentAccessPointIndex from " + oldIndex + " to " + currentAccessPointIndex);
    } else if (accessPoints.size() == 0) {
      // 如果删除后列表为空，重置为 0
      currentAccessPointIndex = 0;
      FileLogger.i(TAG, "Removed last node, reset currentAccessPointIndex to 0");
    }
    
    // 🔧 强制刷新：确保所有后续操作使用最新的列表和索引
    FileLogger.i(TAG, "After removal: size=" + accessPoints.size() + ", currentIndex=" + currentAccessPointIndex);
    if (accessPoints.size() > 0 && currentAccessPointIndex >= accessPoints.size()) {
      currentAccessPointIndex = accessPoints.size() - 1;
      FileLogger.w(TAG, "Index out of bounds after removal, corrected to: " + currentAccessPointIndex);
    }
    
    return true;
  }

  /**
   * 根据名称精准切换到指定的接入点
   * @param name 目标接入点的名称
   * @return 如果切换成功返回 true，如果未找到该名称的接入点返回 false
   */
  public boolean switchToAccessPointByName(String name) {
    for (int i = 0; i < accessPoints.size(); i++) {
      if (accessPoints.get(i).getName().equals(name)) {
        this.currentAccessPointIndex = i;
        FileLogger.i(TAG, "Switched to access point: " + name + ", index: " + i);
        return true;
      }
    }
    FileLogger.w(TAG, "Access point not found: " + name);
    return false;
  }
}