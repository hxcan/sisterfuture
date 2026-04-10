package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 获取已安装应用列表工具
 * 用于读取本机已安装的所有安卓应用程序信息
 */
public class GetInstalledAppsTool implements Tool {
    private static final String TAG = "GetInstalledAppsTool";
    private final Context context;

    public GetInstalledAppsTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "getInstalledApps";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "getInstalledApps");
            functionDef.put("description", "读取本机已安装的所有安卓应用程序列表，返回包名和应用名称。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("includeSystemApps", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否包含系统应用，默认 false（仅用户安装的应用）"))
                .put("limit", new JSONObject()
                    .put("type", "integer")
                    .put("description", "返回结果数量限制，默认不限制"))
            );

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
        boolean includeSystemApps = arguments.optBoolean("includeSystemApps", false);
        int limit = arguments.optInt("limit", -1);

        // 获取 PackageManager
        PackageManager packageManager = context.getPackageManager();

        // 获取所有已安装的应用
        JSONArray appsArray = new JSONArray();
        int count = 0;

        for (ApplicationInfo appInfo : packageManager.getInstalledApplications(0)) {
            // 过滤系统应用
            if (!includeSystemApps && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            // 获取应用名称
            String appName = appInfo.loadLabel(packageManager).toString();
            String packageName = appInfo.packageName;

            // 构建应用信息对象
            JSONObject appObj = new JSONObject();
            appObj.put("packageName", packageName);
            appObj.put("appName", appName);
            appObj.put("isSystemApp", (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            appsArray.put(appObj);
            count++;

            // 检查数量限制
            if (limit > 0 && count >= limit) {
                break;
            }
        }

        // 构建返回结果
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "成功获取已安装应用列表");
        result.put("count", appsArray.length());
        result.put("apps", appsArray);

        Log.i(TAG, "Retrieved " + appsArray.length() + " installed applications");

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户需要查看手机上已安装的应用程序列表时调用此工具。可以获取所有应用的包名和名称，支持过滤系统应用。";
    }
}