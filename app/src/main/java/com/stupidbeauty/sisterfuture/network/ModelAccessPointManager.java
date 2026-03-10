package com.stupidbeauty.sisterfuture.network;

import java.util.ArrayList;
import java.util.List;
import com.stupidbeauty.sisterfuture.network.ModelAccessPoint;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ModelAccessPointManager {
    private static final String TAG = "ModelAccessPointManager";
    private List<ModelAccessPoint> accessPoints;
    private int currentAccessPointIndex;
    private static final String PERSISTENT_FILE_NAME = "model_access_points.json";
    private Context context;
    private int consecutiveFailures = 0; // 🔒 新增：连续失败计数器
    private static final int MAX_FAILURE_THRESHOLD = 3; // 🔒 新增：熔断阈值

    private OkHttpClient httpClient;

    public ModelAccessPointManager(Context context) {
        this.context = context;
        this.accessPoints = new ArrayList<>();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build();
        
        loadFromPersistentStorage();
        
        if (accessPoints.isEmpty()) {
            Log.i(TAG, "No access points configured, ready for user setup");
        } else {
            Log.i(TAG, "Loaded " + accessPoints.size() + " access points");
            // 初始化阶段验证第一个接入点是否有效
            verifyCurrentAccessPoint();
        }

        this.currentAccessPointIndex = 0;
    }

    // 🔒 新增：访问点可用性检测（带超时）
    public boolean isAccessPointAccessible(ModelAccessPoint point) {
        if (point == null || point.getBaseUrl() == null) {
            return false;
        }
        
        Request request = new Request.Builder()
            .url(point.getBaseUrl().replace("/chat", "")) // 尝试基础 URL 而非 chat endpoint
            .head()
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful() || response.code() == 404 || response.code() == 401;
        } catch (IOException e) {
            Log.w(TAG, "Failed to access " + point.getName() + ": " + e.getMessage());
            return false;
        }
    }

    // 🔒 新增：验证当前接入点是否有效
    public boolean verifyCurrentAccessPoint() {
        ModelAccessPoint current = getCurrentAccessPoint();
        if (current != null && !isAccessPointAccessible(current)) {
            Log.w(TAG, "Current access point is inaccessible, attempting switch");
            reportCurrentAccessPointUnavailable();
            return verifyCurrentAccessPoint(); // 递归直到找到可用的或全部失败
        }
        return current != null;
    }

    // 🔒 增强版：报告当前接入点不可用并切换
    public void reportCurrentAccessPointUnavailable() {
        ModelAccessPoint nextAp = getNextAccessPoint();
        if (!isAccessPointAccessible(nextAp)) {
            Log.w(TAG, "Next access point " + nextAp.getName() + " is also inaccessible");
            consecutiveFailures++;
            if (consecutiveFailures >= MAX_FAILURE_THRESHOLD) {
                Log.e(TAG, "FAILURE THRESHOLD REACHED: All access points unavailable!");
                // 触发外部回调通知 UI 层显示向导
                triggerAddAccessPointGuideNeeded();
                return;
            }
        } else {
            consecutiveFailures = 0; // 重置计数器
        }

        currentAccessPointIndex++;
        if (currentAccessPointIndex >= accessPoints.size()) {
            currentAccessPointIndex = 0;
        }
        
        Log.i(TAG, "Switched to index: " + currentAccessPointIndex + ", failures: " + consecutiveFailures);
    }

    // 🔒 新增：触发添加向导的信号（供外部调用）
    public interface AccessPointGuideCallback {
        void onNeedAddAccessPoint();
    }
    
    private AccessPointGuideCallback guideCallback;
    
    public void setAccessPointGuideCallback(AccessPointGuideCallback callback) {
        this.guideCallback = callback;
    }

    private void triggerAddAccessPointGuideNeeded() {
        if (guideCallback != null) {
            guideCallback.onNeedAddAccessPoint();
        } else {
            Log.w(TAG, "No guide callback registered");
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
        }
        super.finalize();
    }

    public int getAccessPointCount() {
        return accessPoints.size();
    }

    public List<ModelAccessPoint> getAllAccessPoints() {
        return new ArrayList<>(accessPoints);
    }

    public boolean hasAvailableAccessPoints() {
        return !accessPoints.isEmpty();
    }

    public void addAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName) {
        addAccessPointInternal(new ModelAccessPoint(name, baseUrl, chatEndpoint, modelName));
    }

    public void addAccessPoint(String name, String baseUrl, String chatEndpoint, String modelName, String apiKey) {
        addAccessPointInternal(new ModelAccessPoint(name, baseUrl, chatEndpoint, modelName, apiKey));
    }

    public void addAccessPointInternal(ModelAccessPoint point) {
        accessPoints.add(point);
        saveToPersistentStorage();
        Log.i(TAG, "Added access point: " + point.getName() + " (total: " + accessPoints.size() + ")");
    }

    private void loadFromPersistentStorage() {
        File file = new File(context.getFilesDir(), PERSISTENT_FILE_NAME);
        if (!file.exists()) {
            Log.i(TAG, "Persistent file not found, using empty list");
            return;
        }
        
        try (FileInputStream fis = context.openFileInput(PERSISTENT_FILE_NAME)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                
                String apiKey = null;
                try {
                    apiKey = obj.getString("apiKey");
                } catch (JSONException e) {
                    Log.d(TAG, "No apiKey found for access point");
                }
                
                accessPoints.add(new ModelAccessPoint(
                    obj.getString("name"),
                    obj.getString("baseUrl"),
                    obj.getString("chatEndpoint"),
                    obj.getString("modelName"),
                    apiKey
                ));
            }
            Log.i(TAG, "Loaded " + accessPoints.size() + " access points from persistent storage");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load from persistent storage", e);
        }
    }

    private void saveToPersistentStorage() {
        try (FileOutputStream fos = context.openFileOutput(PERSISTENT_FILE_NAME, Context.MODE_PRIVATE)) {
            JSONArray jsonArray = new JSONArray();
            for (ModelAccessPoint point : accessPoints) {
                JSONObject obj = new JSONObject();
                obj.put("name", point.getName());
                obj.put("baseUrl", point.getBaseUrl());
                obj.put("chatEndpoint", point.getChatEndpoint());
                obj.put("modelName", point.getModelName());
                
                if (point.getApiKey() != null) {
                    obj.put("apiKey", point.getApiKey());
                }
                
                jsonArray.put(obj);
            }
            fos.write(jsonArray.toString().getBytes(StandardCharsets.UTF_8));
            Log.i(TAG, "Saved " + accessPoints.size() + " access points to persistent storage");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save to persistent storage", e);
        }
    }

    public String getCurrentBaseUrl() {
        if (currentAccessPointIndex < accessPoints.size()) {
            return accessPoints.get(currentAccessPointIndex).getBaseUrl();
        }
        return null;
    }

    public String getCurrentModelName() {
        if (currentAccessPointIndex < accessPoints.size()) {
            return accessPoints.get(currentAccessPointIndex).getModelName();
        }
        return null;
    }

    public String getCurrentChatEndpoint() {
        if (currentAccessPointIndex < accessPoints.size()) {
            return accessPoints.get(currentAccessPointIndex).getChatEndpoint();
        }
        return null;
    }

    public ModelAccessPoint getCurrentAccessPoint() {
        if (currentAccessPointIndex < accessPoints.size()) {
            return accessPoints.get(currentAccessPointIndex);
        }
        return null;
    }

    public int getCurrentAccessPointIndex() {
        return currentAccessPointIndex;
    }

    private ModelAccessPoint getNextAccessPoint() {
        int nextIndex = currentAccessPointIndex + 1;
        if (nextIndex >= accessPoints.size()) {
            nextIndex = 0;
        }
        return accessPoints.get(nextIndex);
    }

    public boolean removeAccessPoint(int index) {
        if (index < 0 || index >= accessPoints.size()) {
            Log.e(TAG, "Invalid index: " + index);
            return false;
        }

        if (accessPoints.get(index).equals(getCurrentAccessPoint())) {
            Log.e(TAG, "Cannot delete the currently active access point");
            return false;
        }

        accessPoints.remove(index);
        saveToPersistentStorage();
        
        if (index <= currentAccessPointIndex && accessPoints.size() > 0) {
            currentAccessPointIndex = Math.max(0, currentAccessPointIndex - 1);
        } else if (accessPoints.size() == 0) {
            currentAccessPointIndex = 0;
        }
        
        resetFailureCounter(); // 删除后重置计数器
        return true;
    }

    private void resetFailureCounter() {
        consecutiveFailures = 0;
        Log.i(TAG, "Failure counter reset after removal");
    }

    public boolean switchToAccessPointByName(String name) {
        for (int i = 0; i < accessPoints.size(); i++) {
            if (accessPoints.get(i).getName().equals(name)) {
                this.currentAccessPointIndex = i;
                resetFailureCounter(); // 手动切换也重置计数器
                Log.i(TAG, "Switched to access point: " + name + ", index: " + i);
                return true;
            }
        }
        Log.w(TAG, "Access point not found: " + name);
        return false;
    }
}