package com.stupidbeauty.sisterfuture.tool;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.manager.MemoryManager;

/**
 * 写剪贴板工具
 * 用于将文本内容写入系统剪贴板
 */
public class WriteClipboardTool implements Tool {
    private static final String TAG = "WriteClipboardTool";
    private final Context context;

    public WriteClipboardTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "writeClipboard";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "writeClipboard");
            functionDef.put("description", "写入文本内容到系统剪贴板。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("text", new JSONObject()
                    .put("type", "string")
                    .put("description", "要写入剪贴板的文本内容"))
            );
            parameters.put("required", new org.json.JSONArray(new String[]{"text"}));

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
        String text = arguments.optString("text", null);
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("剪贴板内容不能为空");
        }

        // 写入剪贴板
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("FutureSister", text);
        clipboardManager.setPrimaryClip(clip);

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("message", "已成功写入剪贴板");
        result.put("textLength", text.length());

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户明确要求将文本写入剪贴板时调用此工具。需要提供要复制的文本内容。";
    }
}
