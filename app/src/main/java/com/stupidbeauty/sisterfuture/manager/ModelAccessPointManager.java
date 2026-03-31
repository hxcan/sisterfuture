package com.stupidbeauty.sisterfuture.manager;

import android.content.Context;
import android.content.SharedPreferences;

public class ModelAccessPointManager {
    private static final String PREF_NAME = "model_access_point_store";
    private static final String KEY_CURRENT_INDEX = "current_index";
    private static final int DEFAULT_INDEX = 0;
    
    private static ModelAccessPointManager instance;
    private SharedPreferences prefs;

    private ModelAccessPointManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ModelAccessPointManager getInstance(Context context) {
        if (instance == null) {
            instance = new ModelAccessPointManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 获取当前接入点索引
     * @return 当前接入点索引，默认返回 0
     */
    public int getCurrentIndex() {
        return prefs.getInt(KEY_CURRENT_INDEX, DEFAULT_INDEX);
    }

    /**
     * 设置当前接入点索引
     * @param index 要设置的接入点索引
     */
    public void setCurrentIndex(int index) {
        prefs.edit().putInt(KEY_CURRENT_INDEX, index).apply();
    }
}
