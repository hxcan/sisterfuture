// SearchMemoryTool.java
package com.stupidbeauty.sisterfuture.tool;

import java.util.List;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.RadioGroup;
import net.tatans.tensorflowtts.utils.ThreadPoolManager;
import com.stupidbeauty.sisterfuture.bean.MemoryEntity;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.tool.GetCurrentTimeTool;
import com.stupidbeauty.sisterfuture.tool.SwitchAccessPointTool;
import com.stupidbeauty.sisterfuture.tool.GetCurrentAccessPointInfoTool;
import com.stupidbeauty.sisterfuture.tool.DeveloperInfoTool;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.util.Log;

public class SearchMemoryTool implements Tool {
    private static final String TAG = "SearchMemoryTool";
    private final Context context;
    private MemoryManager memoryManager;

    public SearchMemoryTool(MemoryManager memoryManager , Context context) {
        this.context = context;
        this.memoryManager = memoryManager;
    }

    @Override
    public String getName() {
        return "search_memory";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "search_memory");
            functionDef.put("description", "搜索长期记忆。用于让未来姐姐在长期记忆中搜索可能与当前所聊的上下文有关的东西，帮助补全一些缺失的上下文，避免用户需要在不同次的会话中一次次地重复说明。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "搜索关键词，可以是语义化的查询，例如\"我喜欢的音乐风格\"或\"系统登录密码\""));

            parameters.put("properties", properties);
            parameters.put("required", new JSONArray().put("query"));

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
            String query = arguments.getString("query");

            // 智能搜索：先搜索内容，再搜索标签
            List<MemoryEntity> results = memoryManager.searchMemory(query);

            // 构建返回结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("query", query);
            result.put("found_count", results.size());

            JSONArray matches = new JSONArray();
            for (MemoryEntity memory : results) {
                JSONObject match = new JSONObject();
                match.put("key", memory.getKey());
                match.put("content", memory.getContent());
                match.put("tags", memory.getTags());
                match.put("timestamp", memory.getTimestamp());
                matches.put(match);
            }
            result.put("matches", matches);

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
        return "用于搜索长期记忆。会智能匹配内容和标签，返回相关记忆条目。";
    }
}
