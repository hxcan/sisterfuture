package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import com.stupidbeauty.sisterfuture.network.ModelAccessPointManager;

public class RemoveAccessPointTool implements Tool {
    private static final String TAG = "RemoveAccessPointTool";
    private final Context context;
    private final ModelAccessPointManager modelAccessPointManager;

    public RemoveAccessPointTool(ModelAccessPointManager modelAccessPointManager, Context context) {
        this.context = context;
        this.modelAccessPointManager = modelAccessPointManager;
    }

    @Override
    public String getName() {
        return "remove_access_point";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "remove_access_point");
            functionDef.put("description", "从系统中删除指定索引的模型接入点。此操作将永久移除该接入点配置，需谨慎使用。");
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            properties.put("index", new JSONObject().put("type", "integer").put("description", "要删除的接入点在列表中的索引位置，从 0 开始计数。"));
            parameters.put("properties", properties);
            JSONArray required = new JSONArray();
            required.put("index");
            parameters.put("required", required);
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
            int index = arguments.getInt("index");
            if (index < 0 || index >= modelAccessPointManager.getAccessPointCount()) {
                JSONObject error = new JSONObject();
                error.put("status", "error");
                error.put("message", "无效的索引值：" + index + ". 可用范围是 0 到 " + (modelAccessPointManager.getAccessPointCount() - 1));
                return error;
            }

            // 🔧 FIX #4595: 通过比较当前激活的索引值，而非硬编码 index == 0
            int currentActiveIndex = modelAccessPointManager.getCurrentAccessPointIndex();
            if (index == currentActiveIndex) {
                ModelAccessPoint currentActive = modelAccessPointManager.getCurrentAccessPoint();
                JSONObject error = new JSONObject();
                error.put("status", "error");
                error.put("message", "无法删除当前激活的接入点 (" + currentActive.getName() + ", 索引 " + currentActiveIndex + ")。请先切换到其他接入点再尝试删除。");
                return error;
            }

            // 🔧 FIX: 删除前先缓存要删除的接入点名称
            ModelAccessPoint accessPointToDelete = modelAccessPointManager.getAllAccessPoints().get(index);
            String cachedName = accessPointToDelete.getName();

            // 执行删除操作
            modelAccessPointManager.removeAccessPoint(index);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            // 使用缓存的名称而不是重新获取
            result.put("message", "已成功删除索引为 " + index + " 的接入点：" + cachedName);
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
        return "删除指定索引的模型接入点。调用前必须通过 list_access_points 工具确认目标接入点的索引。" + 
               "删除后，系统将自动更新接入点列表，但不会影响当前正在使用的接入点。";
    }
}