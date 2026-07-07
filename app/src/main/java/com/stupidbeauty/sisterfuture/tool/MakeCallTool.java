package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

/**
 * 拨打电话工具 - 直接进入拨打状态
 * 关键：使用 ACTION_CALL 而不是 ACTION_DIAL
 * ACTION_CALL: 直接拨出（需要 CALL_PHONE 权限）
 * ACTION_DIAL: 打开拨号界面（需要用户再点拨打）
 */
public class MakeCallTool implements Tool {
    private static final String TAG = "MakeCallTool";
    private final Context context;

    public MakeCallTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "make_call";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "make_call");
            functionDef.put("description", "直接拨打电话（不是打开拨号界面），一键进入通话中状态。");

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
            JSONObject result = new JSONObject();
            result.put("status", "permission_required");
            result.put("message", "需要 CALL_PHONE 权限才能直接拨打电话。请先授权后重试。");
            result.put("permission", Manifest.permission.CALL_PHONE);
            result.put("phone_number", phoneNumber);
            return result;
        }

        // 构造 ACTION_CALL Intent
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
        return "当用户需要打电话时调用此工具。直接拨打电话（不是打开拨号界面），用户一点击就能直接通话。注意：需要 CALL_PHONE 权限。如果没有权限，会提示用户授权。\n\n典型使用场景：\n- '打 12333 咨询社保'\n- '打给警长李宇坤'\n- '打给配偶'\n- '打 120 急救'\n\n使用 get_contact_list 工具可以查询联系人信息。";
    }
}
