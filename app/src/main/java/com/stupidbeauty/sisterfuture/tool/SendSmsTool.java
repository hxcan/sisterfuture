package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.manager.MemoryManager;
import com.stupidbeauty.sisterfuture.utils.FileLogger;

/**
 * 发送短信工具
 * 用于主动向指定手机号发送短信
 *
 * 使用场景：
 * - 手机丢失时，主人发送短信指令，未来姐姐自动回复位置信息
 * - 远程控制：收到短信指令后执行相应操作并回复结果
 * - 主动通知：如给联系人发提醒
 */
public class SendSmsTool implements Tool {
    private static final String TAG = "SendSmsTool";
    private final Context context;

    public SendSmsTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "sendSms";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "sendSms");
            functionDef.put("description", "主动向指定手机号发送短信。需要 android.permission.SEND_SMS 权限。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("phoneNumber", new JSONObject()
                    .put("type", "string")
                    .put("description", "目标手机号，例如 18138292381"))
                .put("message", new JSONObject()
                    .put("type", "string")
                    .put("description", "要发送的短信内容"))
            );
            parameters.put("required", new org.json.JSONArray(new String[]{"phoneNumber", "message"}));

            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            FileLogger.e(TAG, "Failed to build definition", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() {
        return true;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public JSONObject execute(JSONObject arguments) throws Exception {
        // 解析参数
        String phoneNumber = arguments.optString("phoneNumber", null);
        String message = arguments.optString("message", null);

        FileLogger.i(TAG, "==== sendSms 工具开始执行 ====");
        FileLogger.i(TAG, "目标号码: " + phoneNumber);
        FileLogger.i(TAG, "短信长度: " + (message != null ? message.length() : 0));

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            FileLogger.e(TAG, "目标手机号为空");
            throw new IllegalArgumentException("目标手机号不能为空");
        }
        if (message == null || message.trim().isEmpty()) {
            FileLogger.e(TAG, "短信内容为空");
            throw new IllegalArgumentException("短信内容不能为空");
        }

        // 检查权限
        int permission = context.checkSelfPermission(Manifest.permission.SEND_SMS);
        FileLogger.i(TAG, "SEND_SMS 权限状态: " + (permission == android.content.pm.PackageManager.PERMISSION_GRANTED ? "已授权" : "未授权"));
        if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            FileLogger.w(TAG, "缺少 SEND_SMS 权限，尝试动态申请");
            try {
                if (context instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) context;
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.SEND_SMS},
                        1001
                    );
                    JSONObject pendingResult = new JSONObject();
                    pendingResult.put("status", "permission_required");
                    pendingResult.put("error", "需要 SEND_SMS 权限，已发起申请。请在弹窗中允许后重试。");
                    pendingResult.put("permission", "android.permission.SEND_SMS");
                    pendingResult.put("next_step", "用户允许权限后，再次调用 sendSms 即可发送");
                    return pendingResult;
                } else {
                    throw new SecurityException("缺少 SEND_SMS 权限。请打开应用，进入设置授权 SEND_SMS 后重试。");
                }
            } catch (Exception e) {
                FileLogger.e(TAG, "申请权限失败", e);
                throw new SecurityException("缺少 SEND_SMS 权限，且自动申请失败: " + e.getMessage());
            }
        }

        // 详细日志：检查 TelephonyManager 状态
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                FileLogger.i(TAG, "TelephonyManager 状态:");
                FileLogger.i(TAG, "  - SIM 状态: " + tm.getSimState());
                FileLogger.i(TAG, "  - 网络运营商: " + tm.getNetworkOperatorName());
                FileLogger.i(TAG, "  - 数据状态: " + tm.getDataState());
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        FileLogger.i(TAG, "  - 网络类型: " + tm.getNetworkType());
                    }
                } catch (Exception e) {
                    FileLogger.w(TAG, "获取网络类型失败", e);
                }
            } else {
                FileLogger.e(TAG, "TelephonyManager 为 null");
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "检查 TelephonyManager 状态失败", e);
        }

        // 详细日志：检查 SubscriptionManager 状态
        SmsManager smsManager = null;
        try {
            FileLogger.i(TAG, "==== 开始获取 SmsManager ====");
            SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            FileLogger.i(TAG, "SubscriptionManager 实例: " + (subscriptionManager != null ? "非空" : "null"));

            if (subscriptionManager != null) {
                int defaultSmsSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                try {
                    defaultSmsSubId = subscriptionManager.getDefaultSmsSubscriptionId();
                    FileLogger.i(TAG, "默认 SMS 订阅 ID: " + defaultSmsSubId);
                } catch (Exception e) {
                    FileLogger.e(TAG, "获取默认 SMS 订阅 ID 失败", e);
                }

                // 列出所有订阅
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        java.util.List<SubscriptionInfo> subs = subscriptionManager.getActiveSubscriptionInfoList();
                        if (subs != null) {
                            FileLogger.i(TAG, "活跃订阅数量: " + subs.size());
                            for (SubscriptionInfo sub : subs) {
                                FileLogger.i(TAG, "  - 订阅 #" + sub.getSubscriptionId() + " | 卡槽 " + sub.getSimSlotIndex() + " | 运营商 " + sub.getCarrierName());
                            }
                        } else {
                            FileLogger.w(TAG, "活跃订阅列表为 null");
                        }
                    }
                } catch (Exception e) {
                    FileLogger.e(TAG, "列出订阅失败", e);
                }

                if (defaultSmsSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    try {
                        FileLogger.i(TAG, "尝试使用 SubscriptionManager.getSmsManagerForSubscriptionId(" + defaultSmsSubId + ")");
                        smsManager = subscriptionManager.getSmsManagerForSubscriptionId(defaultSmsSubId);
                        FileLogger.i(TAG, "getSmsManagerForSubscriptionId 成功");
                    } catch (Exception e) {
                        FileLogger.e(TAG, "getSmsManagerForSubscriptionId 失败", e);
                    }
                } else {
                    FileLogger.w(TAG, "默认 SMS 订阅 ID 无效，尝试 getDefault()");
                    smsManager = SmsManager.getDefault();
                    FileLogger.i(TAG, "SmsManager.getDefault() 成功: " + (smsManager != null));
                }
            } else {
                FileLogger.w(TAG, "SubscriptionManager 为 null，使用 SmsManager.getDefault()");
                smsManager = SmsManager.getDefault();
                FileLogger.i(TAG, "SmsManager.getDefault() 成功: " + (smsManager != null));
            }
        } catch (Exception e) {
            FileLogger.e(TAG, "获取 SmsManager 过程中发生异常", e);
            if (smsManager == null) {
                smsManager = SmsManager.getDefault();
                FileLogger.i(TAG, "fallback getDefault() 成功: " + (smsManager != null));
            }
        }

        if (smsManager == null) {
            FileLogger.e(TAG, "无法获取 SmsManager，放弃");
            throw new Exception("无法获取 SmsManager");
        }

        // 发送短信
        try {
            FileLogger.i(TAG, "==== 开始发送短信 ====");
            FileLogger.i(TAG, "目标: " + phoneNumber);
            FileLogger.i(TAG, "内容: " + message);

            // 短信用 PendingIntent 标识（成功 / 失败）
            PendingIntent sentPI = PendingIntent.getBroadcast(
                context, 0, new Intent("SMS_SENT_" + System.currentTimeMillis()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                context, 0, new Intent("SMS_DELIVERED_" + System.currentTimeMillis()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            // 拆分长短信
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            FileLogger.i(TAG, "短信分段数: " + parts.size());

            if (parts.size() == 1) {
                FileLogger.i(TAG, "发送单条短信...");
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
            } else {
                FileLogger.i(TAG, "发送多条短信...");
                java.util.ArrayList<PendingIntent> sentPIs = new java.util.ArrayList<>();
                java.util.ArrayList<PendingIntent> deliveredPIs = new java.util.ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    sentPIs.add(sentPI);
                    deliveredPIs.add(deliveredPI);
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentPIs, deliveredPIs);
            }

            FileLogger.i(TAG, "✅ 短信已成功发送");

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "短信已成功发送");
            result.put("phoneNumber", phoneNumber);
            result.put("messageLength", message.length());
            result.put("partsCount", parts.size());

            return result;
        } catch (Throwable e) {
            // 用 Throwable 捕获 Error 和 Exception
            FileLogger.e(TAG, "❌ 发送短信失败: " + e.getClass().getName() + " - " + e.getMessage(), e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", e.getMessage());
            errorResult.put("error_type", e.getClass().getSimpleName());
            return errorResult;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户明确要求向某个手机号发送短信时调用此工具。需要提供目标手机号和短信内容。";
    }
}