// com.stupidbeauty.sisterfuture.tool.GetContactListTool.java
package com.stupidbeauty.sisterfuture.tool;

import com.google.gson.Gson;
import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import android.app.Activity;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetContactListTool implements Tool {
    private static final String TAG = "GetContactListTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public GetContactListTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "get_contact_list";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "get_contact_list");
            functionDef.put("description", "获取手机通讯录中的全部联系人列表。一次性返回所有联系人的姓名和号码，供大模型进行智能分析和匹配。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", new JSONObject());
            parameters.put("required", new JSONArray());

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
        return true;
    }

    @Override
    public void executeAsync(@NonNull JSONObject arguments, @NonNull OnResultCallback callback) {
        executor.execute(() -> {
            try {
                // 检查权限
                if (!hasReadContactsPermission()) {
                    // 返回提示信息并建议用户授权
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "当前不具有读取联系人的权限，需要您授权才能访问通讯录。请允许权限请求，之后再重试此操作。");
                    result.put("sister_future_note", "主人摸摸姐姐的后颈，下次授权会更顺利哦～");
                    callback.onResult(result);
                    
                    // 在主线程发起权限请求
                    ((Activity) context).runOnUiThread(() -> {
                        Log.d(TAG, "尝试发起权限请求"); // 添加日志
                        if (context instanceof Activity) {
                            Activity activity = (Activity) context;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Log.d(TAG, "准备调用requestPermissions"); // 确认执行到这里
                                activity.requestPermissions(
                                    new String[]{Manifest.permission.READ_CONTACTS}, 
                                    1001
                                );
                                Log.d(TAG, "已调用requestPermissions"); // 确认调用完成
                            }
                        } else {
                            Log.e(TAG, "Context is not an Activity, cannot request permissions");
                        }
                    });
                    return;
                }

                // 有权限时正常执行
                List<Contact> contacts = getAllContacts();
                
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("contacts", new JSONArray(new Gson().toJson(contacts)));
                result.put("total_count", contacts.size());
                result.put("sister_future_note", "主人摸摸姐姐的后颈，代码编译成功率+100%哦～");

                callback.onResult(result);
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                try {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    callback.onResult(error);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 检查是否具有读取联系人权限
     */
    private boolean hasReadContactsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android 6.0以下版本默认有权限
    }

    private List<Contact> getAllContacts() {
        List<Contact> contacts = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        
        Cursor cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            },
            null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                contacts.add(new Contact(name, number));
            }
            cursor.close();
        }

        return contacts;
    }

    // 内部类：联系人
    public static class Contact {
        public String name;
        public String number;

        public Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "用于获取手机通讯录中的全部联系人列表。工具本身不进行任何搜索或过滤，仅负责提供原始数据。由大模型负责后续的智能匹配和分析。当缺少权限时，会直接发起权限请求并提示用户授权后重试。";
    }
}
