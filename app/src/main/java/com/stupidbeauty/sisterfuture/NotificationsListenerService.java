package com.stupidbeauty.sisterfuture;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知监听服务 - 缓存所有可见通知供 ListNotificationsTool 读取
 *
 * 设计目的：
 * - 部分 ROM (如 Motorola Android 13) 上 NotificationManager.getActiveNotifications() 即使权限充足也返回空数组
 * - 本服务改为常驻缓存模式：通过 onNotificationPosted 实时缓存通知，工具读取缓存
 * - 应用启动时自动启动本服务（不依赖工具调用）
 * - 通知被划掉时自动从缓存移除（保持与 getActiveNotifications() 一致）
 *
 * 工作原理：
 * - 用户在 系统设置 → 通知使用权 → 找到"未来姐姐"并开启
 * - 系统将本服务注册为通知监听器
 * - 每次有通知到来时，onNotificationPosted 被调用，通知信息存入缓存
 * - 每次通知被划掉时，onNotificationRemoved 被调用，缓存中对应条目被移除
 * - ListNotificationsTool 通过 getCachedNotifications() 读取缓存
 *
 * 通知正文提取策略（修复短信应用等特殊通知读不到正文的问题）：
 * - EXTRA_BIG_TEXT：短信应用把完整正文放在这里
 * - EXTRA_TEXT：作为通用 fallback
 * - EXTRA_MESSAGES：短信应用把每条短信放在 List<CharSequence> 里
 * - EXTRA_TEXT_LINES：部分应用把多行文本放这里
 *
 * 日志约定：使用 FileLogger 而非 android.util.Log，日志输出到应用日志文件供后续回顾。
 */
public class NotificationsListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationsListenerService";

    /**
     * 通知缓存：key = StatusBarNotification.getKey()（唯一标识）
     * 使用 ConcurrentHashMap 保证线程安全
     * Service 可能在任意线程收到回调
     */
    private static final ConcurrentHashMap<String, CachedNotification> notificationCache = new ConcurrentHashMap<>();

    /**
     * 缓存的通知信息（不依赖 StatusBarNotification 对象，因为原对象可能被回收）
     */
    public static class CachedNotification {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final String bigText;
        public final long postTime;
        public final int id;
        public final String tag;

        public CachedNotification(String key, String packageName, String appName,
                                 String title, String text, String bigText,
                                 long postTime, int id, String tag) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.bigText = bigText;
            this.postTime = postTime;
            this.id = id;
            this.tag = tag;
        }
    }

    /**
     * 当通知被系统投递时调用
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        try {
            CachedNotification cached = buildCachedNotification(sbn);
            notificationCache.put(cached.key, cached);
            FileLogger.i(TAG, "onNotificationPosted: cached notification | key=" + cached.key +
                " | package=" + cached.packageName + " | title=" + cached.title +
                " | text=" + cached.text + " | bigText=" + cached.bigText);
        } catch (Exception e) {
            FileLogger.e(TAG, "onNotificationPosted: failed to cache notification", e);
        }
    }

    /**
     * 当通知被划掉或被系统移除时调用
     * 关键：跟踪移除，保持缓存与系统通知栏一致
     */
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
        try {
            String key = sbn.getKey();
            CachedNotification removed = notificationCache.remove(key);
            if (removed != null) {
                FileLogger.i(TAG, "onNotificationRemoved: removed from cache | key=" + key +
                    " | package=" + removed.packageName + " | title=" + removed.title);
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "onNotificationRemoved: failed to remove from cache", e);
        }
    }

    /**
     * 当用户授予/撤销通知使用权时调用
     */
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        FileLogger.i(TAG, "NotificationListener connected");
        FileLogger.i(TAG, "onListenerConnected: Service bound, will cache notifications from now on");

        // 启动时主动获取当前可见通知，填充缓存
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                FileLogger.i(TAG, "onListenerConnected: pre-loading " + active.length + " existing notifications");
                for (StatusBarNotification sbn : active) {
                    CachedNotification cached = buildCachedNotification(sbn);
                    notificationCache.put(cached.key, cached);
                }
            } else {
                FileLogger.w(TAG, "onListenerConnected: getActiveNotifications() returned null (ROM limitation), cache will fill as new notifications arrive");
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "onListenerConnected: failed to pre-load notifications", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        FileLogger.w(TAG, "onListenerDisconnected: Service unbound, cache preserved but not updated");
    }

    /**
     * 从 StatusBarNotification 构建 CachedNotification
     * 这里要把所有需要的信息都提取出来，避免持有 StatusBarNotification 引用导致内存泄漏
     */
    private CachedNotification buildCachedNotification(StatusBarNotification sbn) {
        String key = sbn.getKey();
        String packageName = sbn.getPackageName();
        long postTime = sbn.getPostTime();
        int id = sbn.getId();
        String tag = sbn.getTag();

        String appName = packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            appName = pm.getApplicationLabel(appInfo).toString();
        } catch (Exception e) {
            FileLogger.w(TAG, "buildCachedNotification: failed to get app name for " + packageName);
        }

        String title = "";
        String text = "";
        String bigText = "";
        Notification notification = sbn.getNotification();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                title = extras.getString(Notification.EXTRA_TITLE, "");
                text = extras.getString(Notification.EXTRA_TEXT, "");

                // 修复：短信应用等通知把正文放在 EXTRA_BIG_TEXT 而不是 EXTRA_TEXT
                CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if (bigTextCs != null) {
                    bigText = bigTextCs.toString();
                }

                // 修复：短信应用用 EXTRA_MESSAGES 存储 List<CharSequence>，每条一行
                // 如果 BIG_TEXT 为空，尝试从 MESSAGES 中拼接
                if (bigText.isEmpty()) {
                    CharSequence[] messages = extras.getCharSequenceArray(Notification.EXTRA_MESSAGES);
                    if (messages != null && messages.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < messages.length; i++) {
                            if (i > 0) sb.append("\n");
                            sb.append(messages[i].toString());
                        }
                        bigText = sb.toString();
                        FileLogger.i(TAG, "buildCachedNotification: extracted " + messages.length + " message lines from EXTRA_MESSAGES");
                    }
                }

                // 修复：部分应用用 EXTRA_TEXT_LINES 存储多行文本
                if (bigText.isEmpty()) {
                    CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                    if (textLines != null && textLines.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < textLines.length; i++) {
                            if (i > 0) sb.append("\n");
                            sb.append(textLines[i].toString());
                        }
                        bigText = sb.toString();
                        FileLogger.i(TAG, "buildCachedNotification: extracted " + textLines.length + " text lines from EXTRA_TEXT_LINES");
                    }
                }

                // 修复：如果 text 为空但 bigText 有内容，把 bigText 复制到 text（兼容字段）
                if (text.isEmpty() && !bigText.isEmpty()) {
                    // 截取 bigText 的第一行作为 text（保持向后兼容）
                    int newlineIdx = bigText.indexOf('\n');
                    text = (newlineIdx > 0) ? bigText.substring(0, newlineIdx) : bigText;
                    FileLogger.i(TAG, "buildCachedNotification: text was empty, populated from bigText first line");
                }
            }
        }

        return new CachedNotification(key, packageName, appName, title, text, bigText, postTime, id, tag);
    }

    /**
     * 获取当前缓存的所有通知
     * 由 ListNotificationsTool 调用
     */
    public static ArrayList<CachedNotification> getCachedNotifications() {
        Collection<CachedNotification> values = notificationCache.values();
        ArrayList<CachedNotification> result = new ArrayList<>(values.size());
        result.addAll(values);
        return result;
    }

    /**
     * 清空缓存（用于调试或用户手动重置）
     */
    public static void clearCache() {
        notificationCache.clear();
        FileLogger.i(TAG, "clearCache: notification cache cleared");
    }

    /**
     * 获取缓存大小（用于调试日志）
     */
    public static int getCacheSize() {
        return notificationCache.size();
    }

    /**
     * 保留原 rebind 方法作为备用
     * 仍可在需要时调用以尝试触发 onListenerConnected
     */
    public static void rebind(Context context) {
        try {
            FileLogger.i(TAG, "rebind: forcing rebind of notification listener service");
            NotificationListenerService.requestRebind(
                new ComponentName(context, NotificationsListenerService.class));
            FileLogger.i(TAG, "rebind: requestRebind() called successfully");
        } catch (Exception e) {
            FileLogger.e(TAG, "rebind: failed to request rebind", e);
        }
    }
}