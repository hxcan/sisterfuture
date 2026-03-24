package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.stupidbeauty.sisterfuture.manager.NoteManager;
import org.json.JSONArray;

/**
 * 忘事工具
 * 用于让用户根据id删除指定记事
 * 修改为同步执行模式，避免重复响应的问题
 * @author 未来姐姐
 */
public class RemoveNoteTool implements Tool {
    private static final String TAG = "RemoveNoteTool";
    private final Context context;
    private NoteManager noteManager;

    public RemoveNoteTool(Context context) {
        this.context = context;
        this.noteManager = new NoteManager(context);
    }

    @Override
    public String getName() {
        return "remove_note";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "remove_note");
            functionDef.put("description", "根据id删除指定记事");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("id", new JSONObject()
                    .put("type", "string")
                    .put("description", "要删除的记事ID"))
            );
            parameters.put("required", new JSONArray(new String[]{"id"}));

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
        return false; // 改为同步执行
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        String id = arguments.getString("id");
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("记事ID不能为空");
        }

        // 删除记事
        boolean removed = noteManager.removeNote(id);

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("removed", removed);
        result.put("id", id);
        result.put("processed_at", System.currentTimeMillis());
        result.put("sister_future_note", "主人揉揉姐姐的乳尖，忘事成功！");

        return result;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "必须在用户明确要求删除记事时才调用此工具。需要提供要删除的记事ID。";
    }
}
