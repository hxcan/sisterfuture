// com.stupidbeauty.sisterfuture.tool.Tool.java
package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

// Tool.java
public interface Tool
{
  String getName();
  JSONObject getDefinition();
  boolean shouldInclude();

  // 在 Tool 接口中新增以下两个默认方法

  /**
  * 设置备注信息
  */
  default void setNote(String note, Context context)
  {
    SharedPreferences.Editor editor = getSharedPreferences(context).edit();
    editor.putString("note_" + getName(), note);
    editor.apply();
  }

  /**
  * 获取备注信息
  */
  default String getNote(Context context)
  {
    return getSharedPreferences(context).getString("note_" + getName(), "");
  }


  /**
   * 获取私有存储实例的默认实现
   */
  default SharedPreferences getSharedPreferences(Context context)
  {
    return context.getSharedPreferences("tool_enhancements", Context.MODE_PRIVATE);
  }

  // 🔥 默认系统增强提示词（各实现类可覆盖）
  default String getDefaultSystemPromptEnhancement()
  {
    return null; // 各工具可覆盖此方法提供默认值
  }

  /**
   * 设置系统增强提示词（融合后的新内容）
   */
  default void setSystemPromptEnhancement(String enhancement, Context context)
  {
    SharedPreferences.Editor editor = getSharedPreferences(context).edit();
    editor.putString("enhancement_" + getName(), enhancement);
    editor.apply();
  }

  /**
   * 获取最终的系统增强提示词
   * 优先级：私有存储 > 默认值 > null
   */
  default String getSystemPromptEnhancement(Context context)
  {
    // 首先检查私有存储
    String savedEnhancement = getSharedPreferences(context).getString("enhancement_" + getName(), "");
    if (savedEnhancement != null && !savedEnhancement.trim().isEmpty())
    {
      return savedEnhancement;
    }

    // 然后检查默认值
    String defaultEnhancement = getDefaultSystemPromptEnhancement();
    if (defaultEnhancement != null && !defaultEnhancement.trim().isEmpty())
    {
      return defaultEnhancement;
    }

    return null;
  }

  // 🔥 新增：是否为异步工具
  default boolean isAsync()
  {
    return false;
  }

  /**
   * 是否记录成功调用的参数历史。包含一次性签名地址等敏感参数的工具应覆盖为 false。
   */
  default boolean shouldRecordParameterHistory()
  {
    return true;
  }

  // 原始同步方法（保留）
  default JSONObject execute(JSONObject arguments) throws Exception
  {
    throw new UnsupportedOperationException("Synchronous execution not supported");
  }

  // 🔥 新增：异步执行入口（可选）
  default void executeAsync(JSONObject arguments, OnResultCallback callback)
  {
    try
    {
      JSONObject result = execute(arguments);
      callback.onResult(result);
    }
    catch (Exception e)
    {
      callback.onError(e);
    }
  }

  // 回调接口
  interface OnResultCallback
  {
    void onResult(JSONObject result);
    void onError(Exception e);
  }
}
