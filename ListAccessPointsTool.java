// ListAccessPointsTool.java
package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;

public class ListAccessPointsTool implements Tool {
    private static final String TAG = "ListAccessPointsTool";
    private final Context context;
    private final ModelAccessPointManager modelAccessPointManager;

    public ListAccessPointsTool(ModelAccessPointManager modelAccessPointManager, Context context) {
        this.context = context;
        this.modelAccessPointManager = modelAccessPointManager;
    }

    @Override
    public String getName() {
        return "list_access_points";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_access_points");
            functionDef.put("description", "列出当前所有模型接入点的详细信息，包括名称、URL、模型和状态等。用于查看系统中配置的所有可用接入点。");
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject());
            parameters.put("required", new JSONArray());
            functionDef.put("parameters", parameters);
            
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        try {
            // 获取所有接入点
            JSONArray accessPointsArray = new JSONArray();
            int total = modelAccessPointManager.getAccessPointCount();
            int currentIdx = modelAccessPointManager.getCurrentAccessPointIndex(); // 🔧 FIX: 获取真实当前索引
            
            for (int i = 0; i < total; i++) {
                JSONObject point = new JSONObject();
                point.put("index", i);
                point.put("name", modelAccessPointManager.getAllAccessPoints().get(i).getName());
                point.put("baseUrl", modelAccessPointManager.getAllAccessPoints().get(i).getBaseUrl());
                point.put("chatEndpoint", modelAccessPointManager.getAllAccessPoints().get(i).getChatEndpoint());
                point.put("modelName", modelAccessPointManager.getAllAccessPoints().get(i).getModelName());
                
                // 🔧 修复核心：基于 Manager 的真实状态标记
                if (i == currentIdx && currentIdx >= 0 && currentIdx < total) {
                    point.put("isCurrent", true);
                } else {
                    point.put("isCurrent", false);
                }
                
                accessPointsArray.put(point);
            }
            
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("total_count", total);
            result.put("access_points", accessPointsArray);
            result.put("message", "共找到 " + total + " 个接入点");
            Log.i(TAG, "Returning list with current index=" + currentIdx);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "执行出错", e);
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", e.getMessage());
            return error;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "列出所有模型接入点的工具。调用后会返回完整的接入点列表，包括名称、URL、端点和模型信息。" 
               + "可用于审计系统中的接入点配置，或作为删除操作的先决条件检查。";
    }
}