package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;
import android.util.Log;
import org.json.JSONObject;

import com.stupidbeauty.sisterfuture.manager.MemoryManager;

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
            Log.e(TAG, "Failed to build definition", e);
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

        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("目标手机号不能为空");
        }
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("短信内容不能为空");
        }

        // 获取 SmsManager（Android 官方推荐使用 getDefault() 静态工厂方法，跨 Android 4.4+ 一致）
        SmsManager smsManager = SmsManager.getDefault();

        if (smsManager == null) {
            throw new Exception("无法获取 SmsManager，可能是因为不在手机上运行");
        }

        // 检查权限
        int permission = context.checkSelfPermission(Manifest.permission.SEND_SMS);
        if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 尝试动态申请（SEND_SMS 是危险权限，需要运行时申请）
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
                    // 非 Activity context，无法直接申请
                    throw new SecurityException("缺少 SEND_SMS 权限。请打开应用，进入设置授权 SEND_SMS 后重试。");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to request permission", e);
                throw new SecurityException("缺少 SEND_SMS 权限，且自动申请失败: " + e.getMessage());
            }
        }

        // 发送短信
        try {
            // 短信用 PendingIntent 标识（成功 / 失败）
            PendingIntent sentPI = PendingIntent.getBroadcast(
                context, 0, new Intent("SMS_SENT"), 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                context, 0, new Intent("SMS_DELIVERED"),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            // 拆分长短信
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            
            if (parts.size() == 1) {
                // 单条短信
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI);
            } else {
                // 多条短信
                java.util.ArrayList<PendingIntent> sentPIs = new java.util.ArrayList<>();
                java.util.ArrayList<PendingIntent> deliveredPIs = new java.util.ArrayList<>();
                for (int i = 0; i < parts.size(); i++) {
                    sentPIs.add(sentPI);
                    deliveredPIs.add(deliveredPI);
                }
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentPIs, deliveredPIs);
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "短信已成功发送");
            result.put("phoneNumber", phoneNumber);
            result.put("messageLength", message.length());
            result.put("partsCount", parts.size());

            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS", e);
            JSONObject errorResult = new JSONObject();
            errorResult.put("status", "error");
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户明确要求向某个手机号发送短信时调用此工具。需要提供目标手机号和短信内容。";
    }
}