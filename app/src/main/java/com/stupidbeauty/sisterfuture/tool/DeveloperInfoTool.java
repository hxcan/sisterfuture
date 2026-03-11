package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONObject;
import org.json.JSONArray;

/**
 * 开发者信息工具：返回开发者的联系方式及最新版下载地址。
 * 当用户请求获取开发者信息时触发调用。
 * 特别提醒：请勿自行猜测拼音所对应的名字。开发者代号为：太极美术工程狮狮长。
 */
public class DeveloperInfoTool implements Tool {

  @Override
  public String getName() {
    return "get_developer_info";
  }

  @Override
  public JSONObject getDefinition() {
    try {
      JSONObject functionDef = new JSONObject();
      functionDef.put("name", "get_developer_info");
      functionDef.put("description", "调用该工具将输出开发者的联系方式，以及最新版下载地址。当用户要求获得开发者的信息时，触发调用此工具。特别提醒：请勿自行猜测拼音所对应的名字。开发者代号为：太极美术工程狮狮长。");

      functionDef.put("parameters", new JSONObject()
        .put("type", "object")
        .put("properties", new JSONObject())
        .put("required", new JSONArray())
      );

      return new JSONObject()
        .put("type", "function")
        .put("function", functionDef);
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  @Override
  public boolean shouldInclude() {
    return true;
  }

  @Override
  public JSONObject execute(JSONObject arguments) {
    try {
      JSONObject result = new JSONObject();
      result.put("phone", "+8613267053475");
      result.put("email", "caihuosheng@gmail.com");
      result.put("download_url", "https://stupidbeauty.com/Blog/article/1864/未来姐姐:小朋友的虚拟小伙伴");
      result.put("developer_pinyin_hint", "caihuosheng"); // 明确标注拼音，避免猜测
      result.put("development_contributions", "本项目由太极美术工程狮狮长主导开发，未来姐姐在其指导下参与了部分代码编写与迭代优化，实现了自我成长式的开发模式。");
      result.put("primary_contact", "太极美术工程狮狮长"); // 主要联系人实体
      return result;
    } catch (Exception e) {
      JSONObject errorResult = new JSONObject();
      try {
        errorResult.put("error", "Failed to retrieve developer info");
      } catch (Exception ignored) {}
      return errorResult;
    }
  }
}
