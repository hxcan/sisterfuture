package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.stupidbeauty.sisterfuture.SfBaseDef;

public class SystemPromptManager {
    private static final String PREF_NAME = "system_prompt_store";
    private static final String KEY_PROMPT = "current_prompt";
    private static SystemPromptManager instance;
    private SharedPreferences prefs;

    private SystemPromptManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SystemPromptManager getInstance(Context context) {
        if (instance == null) {
            instance = new SystemPromptManager(context.getApplicationContext());
        }
        return instance;
    }

    public String getCurrentPrompt() {
        String saved = prefs.getString(KEY_PROMPT, "");
        // 如果没有保存过，则返回默认值
        return saved.isEmpty() ? SfBaseDef.DEFAULT_SYSTEM_PROMPT : saved;
    }

    public void updatePrompt(String newPrompt) {
        prefs.edit().putString(KEY_PROMPT, newPrompt).apply();
    }
}