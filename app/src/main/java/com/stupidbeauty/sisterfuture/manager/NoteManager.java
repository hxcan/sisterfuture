package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;


/**
 * 记事本管理器
 * 负责记事的持久化存储和管理
 */
public class NoteManager {
    private static final String TAG = "NoteManager";
    private static final String PREFS_NAME = "notes_prefs";
    private static final String KEY_NOTES = "notes_list";
    private SharedPreferences sharedPreferences;



    public NoteManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }


    /**
     * 保存记事列表到SharedPreferences
     */
    private void saveNotes(List<Note> notes) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (Note note : notes) {
                JSONObject jsonNote = new JSONObject();
                jsonNote.put("id", note.getId());
                jsonNote.put("content", note.getContent());
                jsonNote.put("timestamp", note.getTimestamp());
                jsonArray.put(jsonNote);
            }
            sharedPreferences.edit().putString(KEY_NOTES, jsonArray.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    /**
     * 从SharedPreferences加载记事列表
     */
    private List<Note> loadNotes() {
        List<Note> notes = new ArrayList<>();
        try {
            String jsonStr = sharedPreferences.getString(KEY_NOTES, null);
            if (jsonStr != null) {
                JSONArray jsonArray = new JSONArray(jsonStr);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonNote = jsonArray.getJSONObject(i);
                    Note note = new Note();
                    note.setId(jsonNote.getString("id"));
                    note.setContent(jsonNote.getString("content"));
                    note.setTimestamp(jsonNote.getLong("timestamp"));
                    notes.add(note);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return notes;
    }



    /**
     * 添加新的记事
     */
    public Note addNote(String content) {
        List<Note> notes = loadNotes();
        Note newNote = new Note(UUID.randomUUID().toString(), content);
        notes.add(newNote);
        saveNotes(notes);
        return newNote;
    }


    /**
     * 根据ID删除记事
     */
    public boolean removeNote(String id) {
        List<Note> notes = loadNotes();
        Iterator<Note> iterator = notes.iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            Note note = iterator.next();
            if (note.getId().equals(id)) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed) {
            saveNotes(notes);
        }
        return removed;
    }


    /**
     * 获取所有记事
     */
    public List<Note> getAllNotes() {
        return loadNotes();
    }


    /**
     * 根据ID查找记事
     */
    public Note getNoteById(String id) {
        List<Note> notes = loadNotes();
        for (Note note : notes) {
            if (note.getId().equals(id)) {
                return note;
            }
        }
        return null;
    }
}
