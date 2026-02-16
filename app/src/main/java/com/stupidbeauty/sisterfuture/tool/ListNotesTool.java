package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.stupidbeauty.sisterfuture.manager.NoteManager;
import com.stupidbeauty.sisterfuture.manager.Note;
import java.util.List;
import org.json.JSONObject;

/**
 * 列出所有记事的工具
 */
public class ListNotesTool implements Tool {
    private static final String TAG = "ListNotesTool"; // 日志标签
    private final Context context;
    private NoteManager noteManager;

    public ListNotesTool(Context context) {
        this.context = context;
        this.noteManager = new NoteManager(context);
    }

    /**
     * 执行列出记事的操作
     * @param arguments 参数，此处不需要任何参数
     * @return 包含所有记事信息的字符串
     */
    @Override
    public JSONObject execute(JSONObject arguments) {
        Log.d(TAG, "开始执行列出记事操作");
        
        try {
            List<Note> notes = noteManager.getAllNotes();
            
            if (notes == null || notes.isEmpty()) {
                JSONObject result = new JSONObject();
                result.put("message", "当前没有任何记事");
                return result;
            }
            
            StringBuilder resultText = new StringBuilder();
            resultText.append("共找到 ").append(notes.size()).append(" 条记事：\n\n");
            
            for (Note note : notes) {
                resultText.append("ID: ").append(note.getId()).append("\n");
                resultText.append("内容: ").append(note.getContent()).append("\n");
                resultText.append("时间: ").append(note.getTimestamp()).append("\n");
                resultText.append("-------------------\n");
            }
            
            JSONObject result = new JSONObject();
            result.put("message", resultText.toString());
            Log.d(TAG, "成功列出 " + notes.size() + " 条记事");
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "列出记事时发生异常", e);
            JSONObject errorResult = new JSONObject();
            try {
                errorResult.put("error", "列出记事时发生错误: " + e.getMessage());
            } catch (Exception ex) {
                // ignore
            }
            return errorResult;
        }
    }

    /**
     * 获取工具的描述
     * @return 工具描述
     */
    public String getDescription() {
        return "列出当前已有的全部记事，返回包含所有记事id、内容和时间戳的列表。";
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "list_notes");
            functionDef.put("description", "列出当前已有的全部记事，返回包含所有记事id、内容和时间戳的列表。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject());
            parameters.put("required", new JSONObject().put("array", new JSONObject()));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public String getName() {
        return "list_notes";
    }
}