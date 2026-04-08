package com.stupidbeauty.sisterfuture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * 姐姐前台服务 - 显示持久化通知，防止应用被系统杀死
 * 支持实时更新通知内容，显示工作进展
 */
public class SisterFutureService extends Service {
    private static final String TAG = "SisterFutureService";
    private static final String CHANNEL_ID = "SisterFutureForegroundChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    // 通知内容状态
    private static String currentStatus = "姐姐正在运行中...";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // #4970 修复：添加 Intent null 检查，避免空指针异常
        if (intent != null) {
            // 处理状态更新
            if (intent.hasExtra("update_status")) {
                String newStatus = intent.getStringExtra("update_status");
                if (newStatus != null) {
                    currentStatus = newStatus;
                    Notification notification = createNotification(currentStatus);
                    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (manager != null) {
                        manager.notify(NOTIFICATION_ID, notification);
                    }
                    Log.d(TAG, "通知状态已更新：" + currentStatus);
                    return START_STICKY;
                }
            }
            
            // 处理可选的提问
            String question = intent.getStringExtra("question");
            if (question != null) {
                Log.d(TAG, "收到提问：" + question);
                SisterFutureApplication.handleQuestion(this, question);
            }
        } else {
            // Intent 为 null 时的安全处理（系统重启服务时可能发生）
            Log.w(TAG, "⚠️ onStartCommand 收到 null Intent，这是系统重启服务的正常行为");
        }
        
        // 启动前台服务
        Notification notification = createNotification(currentStatus);
        startForeground(NOTIFICATION_ID, notification);
        
        Log.d(TAG, "前台服务已启动");
        
        return START_STICKY; // 保持服务运行
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "前台服务已停止");
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "姐姐前台服务",
                NotificationManager.IMPORTANCE_LOW // 低重要性，不发出声音
            );
            channel.setDescription("显示姐姐应用的工作状态");
            channel.setShowBadge(false); // 不显示角标
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 创建通知
     * @param status 通知内容
     */
    private Notification createNotification(String status) {
        // 点击通知返回应用
        Intent intent = new Intent(this, SisterFutureActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("姐姐")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // 可替换为应用图标
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 不可滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    /**
     * 静态方法：更新通知内容（从 Activity 或其他组件调用）
     * @param context 上下文
     * @param status 新的状态文本
     */
    public static void updateNotificationStatus(Context context, String status) {
        Intent intent = new Intent(context, SisterFutureService.class);
        intent.putExtra("update_status", status);
        context.startService(intent);
        Log.d(TAG, "请求更新通知状态：" + status);
    }
    
    /**
     * 启动前台服务
     * @param context 上下文
     */
    public static void startForegroundService(Context context) {
        Intent intent = new Intent(context, SisterFutureService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    /**
     * 停止前台服务
     * @param context 上下文
     */
    public static void stopForegroundService(Context context) {
        Intent intent = new Intent(context, SisterFutureService.class);
        context.stopService(intent);
    }
}
