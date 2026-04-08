package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import org.json.JSONObject;

/**
 * 启动应用工具
 * 用于通过包名启动手机上的指定应用程序
 */
public class LaunchAppTool implements Tool {
    private static final String TAG = "LaunchAppTool";
    private final Context context;

    public LaunchAppTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "launchApp";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "launchApp");
            functionDef.put("description", "通过包名启动手机上的指定应用程序。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("packageName", new JSONObject()
                    .put("type", "string")
                    .put("description", "要启动的应用的包名，例如：com.tencent.mm（微信）"))
            );
            parameters.put("required", new org.json.JSONArray(new String[]{"packageName"}));

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
        // 解析参数
        String packageName = arguments.optString("packageName", null);
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("包名不能为空");
        }

        // 获取 PackageManager
        PackageManager packageManager = context.getPackageManager();

        // 获取启动 Intent
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent == null) {
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("message", "未找到该包名的应用，请检查包名是否正确");
            result.put("packageName", packageName);
            return result;
        }

        // 添加 FLAG_ACTIVITY_NEW_TASK，因为这是从非 Activity 上下文启动
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动应用
        try {
            context.startActivity(launchIntent);
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "成功启动应用：" + packageName);
            result.put("packageName", packageName);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + packageName, e);
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("message", "启动应用失败：" + e.getMessage());
            result.put("packageName", packageName);
            result.put("error", e.toString());
            return result;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户需要启动手机上的某个应用时调用此工具。需要提供应用的包名（如 com.tencent.mm 表示微信）。如果不知道包名，可以先询问用户。";
    }
}
