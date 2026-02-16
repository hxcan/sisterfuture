package com.stupidbeauty.sisterfuture;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class SisterFutureService extends Service {
    private static final String TAG = "SisterFutureService";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String question = intent.getStringExtra("question");
        if (question != null) {
            Log.d(TAG, "收到提问: " + question);
            // TODO: 将问题传递给主界面或启动对话Activity
            SisterFutureApplication.handleQuestion(this, question);
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
