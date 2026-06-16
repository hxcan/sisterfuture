package com.stupidbeauty.sisterfuture;

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
 * - ListNotificationsTool 通过反射调用本服务的 getActiveNotifications()
 *
 * 为什么不用常驻监听 + 数据库缓存：
 * - 用户需求是"通知出现时能立即看到"
 * - 主任务 #785717931370 已决定采用"按需查询"方案
 * - 这种方案更省电、对新版本 Android 兼容性更好
 *
 * 如果未来需要"已划掉的通知也能查"，可在本服务里加 onNotificationRemoved 回调 + 数据库
 */
public class NotificationsListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationsListenerService";

    /**
     * 当通知被添加时调用（标准回调）
     *
     * 当前不处理，因为我们采用按需查询方案。
     * 如果未来要做"实时通知推送"功能，可在此发送广播给 UI。
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 故意留空
        // Log.d(TAG, "onNotificationPosted: " + sbn.getPackageName());
    }

    /**
     * 当通知被移除时调用（标准回调）
     *
     * 当前不处理。
     */
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // 故意留空
        // Log.d(TAG, "onNotificationRemoved: " + sbn.getKey());
    }

    /**
     * 当通知排序改变时调用（标准回调）
     */
    @Override
    public void onNotificationRankingUpdate(java.util.List<StatusBarNotification> rankingChanged) {
        // 故意留空
        // Log.d(TAG, "onNotificationRankingUpdate: " + rankingChanged.size());
    }

    /**
     * 当用户授予/撤销通知使用权时调用
     */
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "NotificationListener connected");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "NotificationListener disconnected");
    }
}