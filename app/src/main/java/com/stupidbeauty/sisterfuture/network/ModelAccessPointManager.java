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
  * @return 如果存在至少一个接入点则返回true，否则返回false 
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
    
    // 只有当持久化存储中没有数据时才添加默认访问点
    if (accessPoints.isEmpty()) {
      // 添加候选访问点并命名
      addAccessPoint("phone Qwen3-30B", "http://127.0.0.1:1447", "/v1/chat/completions", "/root/.cache/huggingface/hub/models--Qwen--Qwen3-30B-A3B-Instruct-2507/snapshots");
      addAccessPoint("gx10 Qwen3-30B", "http://192.168.150.58:8000", "/v1/chat/completions", "/root/.cache/huggingface/hub/models--Qwen--Qwen3-30B-A3B-Instruct-2507/snapshots");
      addAccessPoint("gx10 Qwen3-235B", "http://192.168.150.227:8080", "/v1/chat/completions", "Qwen3 235B Gptq Int4 Fp16 New");
      addAccessPoint("Dev Machine Qwen3-30B", "http://192.168.45.211:1447", "/v1/chat/completions", "/root/.cache/huggingface/hub/models--Qwen--Qwen3-30B-A3B-Instruct-2507/snapshots");
      addAccessPoint("Amd Computer Qwen3-30B", "http://192.168.26.104:1447", "/v1/chat/completions", "/root/.cache/huggingface/hub/models--Qwen--Qwen3-30B-A3B-Instruct-2507/snapshots");
      addAccessPoint("Aliyun Qwen3-235B", "https://dashscope.aliyuncs.com", "/compatible-mode/v1/chat/completions", "qwen3-235b-a22b-instruct-2507");
    }

    this.currentAccessPointIndex = 0; // 默认指向第一个访问点
  }

  /**
   * 动态添加新的接入点，并立即持久化存储
   * @param name 接入点名称
   * @param baseUrl 基础URL
   * @param chatEndpoint 聊天接口端点
   * @param modelName 模型名称
   */
  public void addAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName) {
      ModelAccessPoint newPoint = new ModelAccessPoint(name, baseUrl, chatEndpoint, modelName);
      accessPoints.add(newPoint);
      saveToPersistentStorage(); // 添加后立即保存
      Log.i(TAG, "Added new access point: " + name + " and saved to storage");
  }

  /**
   * 从持久化存储中加载接入点列表
   */
  private void loadFromPersistentStorage() {
    File file = new File(context.getFilesDir(), PERSISTENT_FILE_NAME);
    if (!file.exists()) {
      Log.i(TAG, "Persistent file not found, using default access points");
      return;
    }
    
    try (FileInputStream fis = context.openFileInput(PERSISTENT_FILE_NAME)) {
      byte[] data = new byte[(int) file.length()];
      fis.read(data);
      String jsonStr = new String(data, StandardCharsets.UTF_8);
      
      JSONArray jsonArray = new JSONArray(jsonStr);
      for (int i = 0; i < jsonArray.length(); i++) {
        JSONObject obj = jsonArray.getJSONObject(i);
        accessPoints.add(new ModelAccessPoint(
          obj.getString("name"),
          obj.getString("baseUrl"),
          obj.getString("chatEndpoint"),
          obj.getString("modelName")
        ));
      }
      Log.i(TAG, "Loaded " + accessPoints.size() + " access points from persistent storage");
    } catch (Exception e) {
      Log.e(TAG, "Failed to load from persistent storage", e);
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
        jsonArray.put(obj);
      }
      fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
      Log.i(TAG, "Saved " + accessPoints.size() + " access points to persistent storage");
    } catch (Exception e) {
      Log.e(TAG, "Failed to save to persistent storage", e);
    }
  }

  /**
   * 获取当前接入点的基础URL
   * @return 当前接入点的基础URL，如果索引越界则返回null
   */
  public String getCurrentBaseUrl()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      Log.i(TAG, "getCurrentBaseUrl, access point index: " + currentAccessPointIndex + ", model name: " + accessPoints.get(currentAccessPointIndex).getBaseUrl());
      return accessPoints.get(currentAccessPointIndex).getBaseUrl();
    }
    return null;
  }

  /**
   * 获取当前接入点的模型名称
   * @return 当前接入点的模型名称，如果索引越界则返回null
   */
  public String getCurrentModelName()
  {
    if (currentAccessPointIndex < accessPoints.size())
    {
      Log.i(TAG, "getCurrentModelName, access point index: " + currentAccessPointIndex + ", model name: " + accessPoints.get(currentAccessPointIndex).getModelName());
      return accessPoints.get(currentAccessPointIndex).getModelName();
    }
    return null;
  }

  /**
   * 获取当前接入点的聊天接口端点
   * @return 当前接入点的聊天接口端点，如果索引越界则返回null
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
   * @return 当前接入点对象，如果索引越界则返回null
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
   * 报告当前接入点不可用，切换到下一个
   * 当到达末尾时循环回到第一个
   */
  public void reportCurrentAccessPointUnavailable()
  {
    if (currentAccessPointIndex < accessPoints.size() - 1)
    {
      currentAccessPointIndex++;
    }
    else
    {
      currentAccessPointIndex = 0; // 循环回到第一个访问点
    }
    Log.i(TAG, "reportCurrentAccessPointUnavailable, access point index: " + currentAccessPointIndex);
  }
}
