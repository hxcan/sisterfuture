package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public class AddContactTool implements Tool {
    private static final String TAG = "AddContactTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AddContactTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "addContact";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "addContact");
            functionDef.put("description", "添加联系人到手机通讯录。需要在通讯录中创建新的联系人，包含姓名和电话号码。");
            
            JSONObject properties = new JSONObject();
            
            JSONObject name = new JSONObject();
            name.put("type", "string");
            name.put("description", "联系人的姓名");
            properties.put("name", name);
            
            JSONObject number = new JSONObject();
            number.put("type", "string");
            number.put("description", "联系人的电话号码");
            properties.put("number", number);
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", properties);
            parameters.put("required", new org.json.JSONArray().put("name").put("number"));
            
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
                String name = arguments.getString("name");
                String number = arguments.getString("number");
                
                if (!hasWriteContactsPermission()) {
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "当前不具有写入联系人的权限，需要 WRITE_CONTACTS 权限。请在设置中授权后重试。");
                    callback.onResult(result);
                    return;
                }
                
                boolean success = addContact(name, number);
                
                JSONObject result = new JSONObject();
                if (success) {
                    result.put("status", "success");
                    result.put("message", "联系人 " + name + " (" + number + ") 已成功添加到通讯录");
                } else {
                    result.put("status", "error");
                    result.put("message", "添加联系人失败");
                }
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

    private boolean hasWriteContactsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean addContact(String name, String number) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            
            ContentValues values = new ContentValues();
            values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.contacts");
            values.put(ContactsContract.RawContacts.ACCOUNT_NAME, "Phone");
            String rawContactIdStr = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, values).getLastPathSegment();
            long rawContactId = Long.parseLong(rawContactIdStr);
            
            ContentValues nameValues = new ContentValues();
            nameValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
            nameValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            nameValues.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues);
            
            ContentValues phoneValues = new ContentValues();
            phoneValues.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
            phoneValues.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
            phoneValues.put(ContactsContract.CommonDataKinds.Phone.NUMBER, number);
            phoneValues.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
            contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "添加联系人失败", e);
            return false;
        }
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "用于添加联系人到手机通讯录。需要提供联系人的姓名(name)和电话号码(number)。需要在 AndroidManifest 中声明 WRITE_CONTACTS 权限。";
    }
}
