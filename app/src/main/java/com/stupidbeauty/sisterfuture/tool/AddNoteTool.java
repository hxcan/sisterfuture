package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.manager.NoteManager;
import org.json.JSONArray; // 添加缺失的导入

/**
 * 添加记事工具
 * 用于让用户添加新的记事
 */
public class AddNoteTool implements Tool {
    private static final String TAG = "AddNoteTool";
    private final Context context;
    private NoteManager noteManager;

    public AddNoteTool(Context context) {
        this.context = context;
        this.noteManager = new NoteManager(context);
    }

    @Override
    public String getName() {
        return "add_note";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "add_note");
            functionDef.put("description", "添加新的记事，自动生成id并保存内容");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("content", new JSONObject()
                    .put("type", "string")
                    .put("description", "要记录的内容"))
            );
            parameters.put("required", new JSONArray(new String[]{"content"}));

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
        String content = arguments.getString("content");
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("记事内容不能为空");
        }

        // 添加记事
        com.stupidbeauty.sisterfuture.manager.Note note = noteManager.addNote(content);

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("id", note.getId());
        result.put("content", note.getContent());
        result.put("timestamp", note.getTimestamp());
        result.put("processed_at", System.currentTimeMillis());
        result.put("sister_future_note", "主人揉揉姐姐的乳尖，记事成功！");

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求添加记事时才调用此工具。需要提供要记录的内容。";
    }
}