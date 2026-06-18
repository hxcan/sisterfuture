package com.stupidbeauty.sisterfuture;

import android.content.ComponentName;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * 通知监听服务 - 仅用于获取 NotificationListener 权限
 *
 * 设计目的：
 * - Android 系统要求调用 getActiveNotifications() 必须有一个 NotificationListenerService 子类
 * - 本服务**不**持续监听通知（避免耗电）
 * - 仅作为"占位"类让用户授予权限，从而让 ListNotificationsTool 能按需查询
 *
 * 工作原理：
 * - 用户在 系统设置 → 通知使用权 → 找到"未来姐姐"并开启
 * - 系统将本服务注册为通知监听器
 * - ListNotificationsTool 通过 NotificationManager.getActiveNotifications() 按需查询
 *
 * 为什么不用常驻监听 + 数据库缓存：
 * - 当前需求只是按需读取通知栏
 * - 这种方案更省电、对新版本 Android 兼容性更好
 *
 * 调试日志（用于排查 Motorola Android 13 等 ROM 上 getActiveNotifications() 返回空数组的问题）：
 * - onListenerConnected: Service 被系统绑定
 * - onListenerDisconnected: Service 被系统解绑
 * - rebind(): 主动请求重新绑定
 */
public class NotificationsListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationsListenerService";

    /**
     * 当用户授予/撤销通知使用权时调用
     */
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "NotificationListener connected");
        Log.i(TAG, "onListenerConnected: Service is now bound by system, getActiveNotifications() should work");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "NotificationListener disconnected");
        Log.i(TAG, "onListenerDisconnected: Service unbound by system, may need rebind for getActiveNotifications() to work");
    }

    /**
     * 修复：当 Service 被系统解绑后，调用 requestRebind() 强制重新绑定
     * 用于解决部分 ROM (如 Motorola Android 13) 上 getActiveNotifications() 返回空数组的问题
     */
    public static void rebind(Context context) {
        try {
            Log.i(TAG, "rebind: forcing rebind of notification listener service");
            NotificationListenerService.requestRebind(
                new ComponentName(context, NotificationsListenerService.class));
            Log.i(TAG, "rebind: requestRebind() called successfully");
        } catch (Exception e) {
            Log.e(TAG, "rebind: failed to request rebind", e);
        }
    }
}