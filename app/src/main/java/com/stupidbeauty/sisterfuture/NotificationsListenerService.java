package com.stupidbeauty.sisterfuture;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
 * 通知正文提取策略（PR #446 综合修复）：
 * 1. EXTRA_BIG_TEXT / EXTRA_MESSAGES / EXTRA_TEXT_LINES 多字段读取
 * 2. **同时使用 getString() 和 getCharSequence() 两种方法读 EXTRA_TEXT**（修复短信应用正文）
 *    - getString() 对 String 类型有效
 *    - getCharSequence() 对 CharSequence 类型有效（短信应用用此类型）
 * 3. 延迟 300ms 重新读取，解决 onNotificationPosted 时 extras 未完整的问题
 * 4. 短信应用额外 dump 所有 extras keys（诊断日志）
 *
 * 日志约定：使用 FileLogger 而非 android.util.Log，日志输出到应用日志文件供后续回顾。
 */
public class NotificationsListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationsListenerService";

    /**
     * 延迟读取 extras 的时间（毫秒）
     * 短信应用等可能在 onNotificationPosted 第一次回调时 extras 还未完整
     * 延迟 300ms 后重新读取可以拿到完整内容
     */
    private static final long DELAY_READ_EXTRAS_MS = 300L;

    /**
     * 通知缓存：key = StatusBarNotification.getKey()（唯一标识）
     * 使用 ConcurrentHashMap 保证线程安全
     * Service 可能在任意线程收到回调
     */
    private static final ConcurrentHashMap<String, CachedNotification> notificationCache = new ConcurrentHashMap<>();

    /**
     * 主线程 Handler 用于延迟任务
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 缓存的通知信息（不依赖 StatusBarNotification 对象，因为原对象可能被回收）
     */
    public static class CachedNotification {
        public final String key;
        public final String packageName;
        public final String appName;
        public final String title;
        /** 主文本字段（向后兼容） */
        public final String text;
        /** 大文本字段（向后兼容） */
        public final String bigText;
        /** 详细文本字段 - 用 getCharSequence() 读取（PR #446 新增） */
        public final String textCharSequence;
        /** 文本来源记录（PR #446 新增，方便诊断） */
        public final String textSource;
        public final long postTime;
        public final int id;
        public final String tag;

        public CachedNotification(String key, String packageName, String appName,
                                 String title, String text, String bigText,
                                 String textCharSequence, String textSource,
                                 long postTime, int id, String tag) {
            this.key = key;
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.bigText = bigText;
            this.textCharSequence = textCharSequence;
            this.textSource = textSource;
            this.postTime = postTime;
            this.id = id;
            this.tag = tag;
        }
    }

    /**
     * 当通知被系统投递时调用
     * 第一次回调：立即缓存（可能 extras 不完整）
     * 第二次回调（延迟 300ms）：更新缓存（extras 已完整）
     */
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        try {
            CachedNotification cached = buildCachedNotification(sbn);
            notificationCache.put(cached.key, cached);
            FileLogger.i(TAG, "onNotificationPosted: cached notification | key=" + cached.key +
                " | package=" + cached.packageName + " | title=" + cached.title +
                " | text=" + cached.text + " | bigText=" + cached.bigText +
                " | textCharSeq=" + cached.textCharSequence + " | textSource=" + cached.textSource);

            // 修复：延迟再次读取，更新缓存
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        StatusBarNotification[] active = getActiveNotifications();
                        if (active != null) {
                            for (StatusBarNotification current : active) {
                                if (current.getKey().equals(sbn.getKey())) {
                                    CachedNotification updated = buildCachedNotification(current);
                                    notificationCache.put(updated.key, updated);
                                    FileLogger.i(TAG, "onNotificationPosted[delayed]: updated | key=" + updated.key +
                                        " | text=" + updated.text + " | bigText=" + updated.bigText +
                                        " | textCharSeq=" + updated.textCharSequence + " | textSource=" + updated.textSource);
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLogger.e(TAG, "onNotificationPosted[delayed]: failed to re-read", e);
                    }
                }
            }, DELAY_READ_EXTRAS_MS);
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
     *
     * PR #446 重要修复：
     * - 同时用 getString() 和 getCharSequence() 两种方法读 EXTRA_TEXT
     * - getString() 对 String 有效
     * - getCharSequence() 对 CharSequence 有效（短信应用用此类型）
     * - 日志输出两种方法的实际值
     * - 返回字段：text（兼容）、textCharSequence（CharSequence 值）、textSource（来源标记）
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
        String text = "";              // getString() 结果
        String bigText = "";           // EXTRA_BIG_TEXT
        String textCharSequence = "";  // getCharSequence() 结果
        String textSource = "";        // 标记 text 来自哪个字段
        Notification notification = sbn.getNotification();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                title = extras.getString(Notification.EXTRA_TITLE, "");

                // PR #446: 双轨读取 EXTRA_TEXT
                // getString() 适合 String 类型
                String textFromGetString = extras.getString(Notification.EXTRA_TEXT, "");
                // getCharSequence() 适合 CharSequence 类型（短信应用用此类型！）
                CharSequence textFromGetCharSeqCs = extras.getCharSequence(Notification.EXTRA_TEXT, "");
                String textFromGetCharSeq = (textFromGetCharSeqCs != null) ? textFromGetCharSeqCs.toString() : "";

                text = textFromGetString;  // 主字段保持向后兼容
                textCharSequence = textFromGetCharSeq;

                // 标记来源
                if (!textFromGetString.isEmpty() && !textFromGetCharSeq.isEmpty()) {
                    if (textFromGetString.equals(textFromGetCharSeq)) {
                        textSource = "BOTH_AGREE";
                    } else {
                        textSource = "BOTH_DIFFER";
                    }
                } else if (!textFromGetString.isEmpty()) {
                    textSource = "GET_STRING_ONLY";
                } else if (!textFromGetCharSeq.isEmpty()) {
                    textSource = "GET_CHARSEQUENCE_ONLY";
                } else {
                    textSource = "BOTH_EMPTY";
                }

                // 修复：短信应用把完整正文放在 EXTRA_BIG_TEXT
                CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
                if (bigTextCs != null) {
                    bigText = bigTextCs.toString();
                }

                // 修复：短信应用用 EXTRA_MESSAGES 存储 List<CharSequence>
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

                // 兼容性处理：text 为空时从 bigText 第一行填充
                if (text.isEmpty() && !bigText.isEmpty()) {
                    int newlineIdx = bigText.indexOf('\n');
                    text = (newlineIdx > 0) ? bigText.substring(0, newlineIdx) : bigText;
                    if (textSource.isEmpty() || textSource.equals("BOTH_EMPTY")) {
                        textSource = "FROM_BIGTEXT";
                    }
                    FileLogger.i(TAG, "buildCachedNotification: text was empty, populated from bigText first line");
                }

                // 诊断：短信应用额外 dump 所有 extras keys
                if ("com.android.messaging".equals(packageName)) {
                    StringBuilder keysDump = new StringBuilder();
                    keysDump.append("getString(EXTRA_TEXT)=\"").append(textFromGetString).append("\" | ");
                    keysDump.append("getCharSeq(EXTRA_TEXT)=\"").append(textFromGetCharSeq).append("\" | ");
                    for (String key2 : extras.keySet()) {
                        Object value = extras.get(key2);
                        String valueStr;
                        if (value instanceof CharSequence) {
                            valueStr = "\"" + value.toString() + "\"";
                        } else if (value instanceof CharSequence[]) {
                            StringBuilder arr = new StringBuilder("[");
                            CharSequence[] arrVal = (CharSequence[]) value;
                            for (int i = 0; i < arrVal.length; i++) {
                                if (i > 0) arr.append(", ");
                                arr.append("\"").append(arrVal[i].toString()).append("\"");
                            }
                            arr.append("]");
                            valueStr = arr.toString();
                        } else {
                            valueStr = String.valueOf(value);
                        }
                        if (valueStr.length() > 200) {
                            valueStr = valueStr.substring(0, 200) + "...";
                        }
                        keysDump.append(key2).append("=").append(valueStr).append(" | ");
                    }
                    FileLogger.i(TAG, "buildCachedNotification[SMS DEBUG]: " + keysDump.toString());
                }
            }
        }

        return new CachedNotification(key, packageName, appName, title, text, bigText,
                                     textCharSequence, textSource, postTime, id, tag);
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