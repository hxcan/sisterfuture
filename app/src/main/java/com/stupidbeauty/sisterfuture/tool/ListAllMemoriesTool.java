// ListAllMemoriesTool.java
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

public class ListAllMemoriesTool implements Tool {
    private static final String TAG = "ListAllMemoriesTool";
    private final Context context;
    private MemoryManager memoryManager;

    public ListAllMemoriesTool(MemoryManager memoryManager, Context context) {
        this.context = context;
        this.memoryManager = memoryManager;
    }

    @Override
    public String getName() {
        return "list_all_memories";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_all_memories");
            functionDef.put("description", "列出当前已有的全部长期记忆，用于确认写入长期记忆工具是否正常工作。");

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
            // 获取所有记忆
            List<MemoryEntity> allMemories = memoryManager.getAllMemories();

            // 构建返回结果
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("total_count", allMemories.size());

            JSONArray memories = new JSONArray();
            for (MemoryEntity memory : allMemories) {
                JSONObject memoryObj = new JSONObject();
                memoryObj.put("key", memory.getKey());
                memoryObj.put("content", memory.getContent());
                memoryObj.put("tags", memory.getTags());
                memoryObj.put("timestamp", memory.getTimestamp());
                memories.put(memoryObj);
            }
            result.put("memories", memories);

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
        return "用于列出当前已有的全部长期记忆，用于确认写入长期记忆工具是否正常工作。";
    }
}
