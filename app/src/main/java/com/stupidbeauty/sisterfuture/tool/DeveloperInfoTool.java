package com.stupidbeauty.sisterfuture.tool;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class DeveloperInfoTool implements Tool {
    private static final String TAG = "DeveloperInfoTool";
    private final Context context;

    public DeveloperInfoTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "get_developer_info";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "get_developer_info");
            functionDef.put("description", "调用该工具将输出开发者的联系方式，以及最新版下载地址。当用户要求获得开发者的信息时，触发调用此工具。");

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
    public JSONObject execute(JSONObject arguments) {
        try {
            JSONObject result = new JSONObject();
            result.put("developer", "太极美术工程狮狮长");
            result.put("contact", "todo");
            result.put("download_url", "todo");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "执行出错", e);
            JSONObject error = new JSONObject();
            try {
                error.put("error", e.getMessage());
            } catch (Exception ignored) {}
            return error;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "调用该工具将输出开发者的联系方式，以及最新版下载地址。当用户要求获得开发者的信息时，触发调用此工具。特别提醒：请勿自行猜测拼音所对应的名字。开发者代号为：太极美术工程狮狮长。";
    }
}
