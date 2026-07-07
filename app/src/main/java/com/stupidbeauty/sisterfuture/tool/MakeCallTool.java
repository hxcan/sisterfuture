package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/**
 * 拨打电话工具 - 直接进入拨打状态
 * 工具名：makeCall（驼峰风格，首字母小写）
 * 关键：使用 ACTION_CALL 而不是 ACTION_DIAL
 * ACTION_CALL: 直接拨出（需要 CALL_PHONE 权限）
 * ACTION_DIAL: 打开拨号界面（需要用户再点拨打）
 */
public class MakeCallTool implements Tool {
    private static final String TAG = "MakeCallTool";
    private static final int REQUEST_CODE_CALL_PHONE = 9001;
    private final Context context;

    public MakeCallTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "makeCall";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "makeCall");
            functionDef.put("description", "直接拨打电话（不是打开拨号界面），一键进入通话中状态。需要 CALL_PHONE 权限。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject()
                .put("phone_number", new JSONObject()
                    .put("type", "string")
                    .put("description", "要拨打的电话号码，例如：12333、13800138000"))
                .put("confirm_first", new JSONObject()
                    .put("type", "boolean")
                    .put("description", "是否需要先弹出确认对话框（默认 true，防止误拨）")
                    .put("default", true))
            );
            parameters.put("required", new org.json.JSONArray(new String[]{"phone_number"}));

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
        String phoneNumber = arguments.optString("phone_number", null);
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("电话号码不能为空");
        }
        boolean confirmFirst = arguments.optBoolean("confirm_first", true);

        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            // 权限未授予，尝试申请
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                // 必须在 UI 线程调用 requestPermissions
                new Handler(Looper.getMainLooper()).post(() -> {
                    Log.d(TAG, "准备申请 CALL_PHONE 权限");
                    try {
                        activity.requestPermissions(
                            new String[]{Manifest.permission.CALL_PHONE},
                            REQUEST_CODE_CALL_PHONE
                        );
                        Log.d(TAG, "已发起 CALL_PHONE 权限申请");
                    } catch (Exception e) {
                        Log.e(TAG, "申请权限失败", e);
                    }
                });
                JSONObject result = new JSONObject();
                result.put("status", "permission_requested");
                result.put("message", "已发起 CALL_PHONE 权限申请，请授权后重试。");
                result.put("permission", Manifest.permission.CALL_PHONE);
                result.put("phone_number", phoneNumber);
                return result;
            } else {
                // Context 不是 Activity，无法申请权限
                JSONObject result = new JSONObject();
                result.put("status", "permission_required");
                result.put("message", "需要 CALL_PHONE 权限才能直接拨打电话，但当前 Context 不是 Activity，无法自动申请。请手动在系统设置中授权后重试。");
                result.put("permission", Manifest.permission.CALL_PHONE);
                result.put("phone_number", phoneNumber);
                return result;
            }
        }

        // 有权限，直接拨号
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + phoneNumber));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(callIntent);
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "正在拨打：" + phoneNumber);
            result.put("phone_number", phoneNumber);
            result.put("mode", confirmFirst ? "with_confirm" : "direct");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to make call: " + phoneNumber, e);
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("message", "拨打失败：" + e.getMessage());
            result.put("phone_number", phoneNumber);
            result.put("error", e.toString());
            return result;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "当用户需要打电话时调用此工具。直接拨打电话（不是打开拨号界面），用户一点击就能直接通话。\n\n" +
               "权限处理：需要 CALL_PHONE 权限（运行时危险权限）。如果没有权限，工具会自动调用 Android 系统权限申请对话框，提示用户授权。授权后用户再次说\"打电话\"即可正常拨打。\n\n" +
               "典型使用场景：\n" +
               "- '打 12333 咨询社保'\n" +
               "- '打给警长李宇坤'\n" +
               "- '打给配偶'\n" +
               "- '打 120 急救'\n\n" +
               "使用 get_contact_list 工具可以查询联系人信息，然后 makeCall 拨打电话。";
    }
}
