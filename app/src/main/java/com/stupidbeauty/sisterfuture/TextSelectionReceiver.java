package com.stupidbeauty.sisterfuture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import androidx.core.app.RemoteInput;

public class TextSelectionReceiver extends BroadcastReceiver {
    private static final String EXTRA_SELECTED_TEXT = "android.intent.extra.PROCESS_TEXT";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle remoteInputs = RemoteInput.getResultsFromIntent(intent);
        String selectedText = null;

        if (remoteInputs != null) {
            selectedText = remoteInputs.getCharSequence(EXTRA_SELECTED_TEXT).toString();
        }

        // 兼容旧方式
        if (selectedText == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            selectedText = intent.getStringExtra(EXTRA_SELECTED_TEXT);
        }

        if (selectedText != null && !selectedText.trim().isEmpty()) {
            Intent serviceIntent = new Intent(context, SisterFutureService.class);
            serviceIntent.putExtra("question", selectedText);
            context.startService(serviceIntent);
        }
    }
}
