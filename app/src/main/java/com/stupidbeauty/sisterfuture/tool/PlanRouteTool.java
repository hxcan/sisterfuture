package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 百度地图路径规划工具
 * 支持步行、骑行、驾车、公交四种交通方式
 * 
 * 关联任务：#4779
 */
public class PlanRouteTool implements Tool {
    private static final String TAG = "PlanRouteTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final OkHttpClient httpClient = new OkHttpClient();

    // 百度地图路径规划 API 端点
    private static final String BASE_URL = "https://api.map.baidu.com/directionlite/v1";
    private static final String DRIVING_PATH = "/driving";
    private static final String WALKING_PATH = "/walking";
    private static final String RIDING_PATH = "/riding";
    private static final String TRANSIT_PATH = "/transit";

    public PlanRouteTool(Context context) {
        this.context = context;
    }

    @Override
    public String getName() {
        return "plan_route";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "plan_route");
            functionDef.put("description", "使用百度地图 API 进行路径规划，支持步行、骑行、驾车、公交四种交通方式。返回距离、时间、详细路线步骤。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("origin").put("destination").put("mode"));

            JSONObject properties = new JSONObject();
            
            // origin 参数
            JSONObject originProp = new JSONObject();
            originProp.put("type", "string");
            originProp.put("description", "起点经纬度，格式：'纬度,经度'（如：'39.908823,116.397470'），或使用 'location' 表示当前位置");
            properties.put("origin", originProp);

            // destination 参数
            JSONObject destProp = new JSONObject();
            destProp.put("type", "string");
            destProp.put("description", "终点经纬度，格式：'纬度,经度'（如：'39.908823,116.397470'）");
            properties.put("destination", destProp);

            // mode 参数
            JSONObject modeProp = new JSONObject();
            modeProp.put("type", "string");
            modeProp.put("description", "交通方式：'driving'（驾车）、'walking'（步行）、'riding'（骑行）、'transit'（公交）");
            modeProp.put("enum", new JSONArray().put("driving").put("walking").put("riding").put("transit"));
            properties.put("mode", modeProp);

            parameters.put("properties", properties);
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
    public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        executor.execute(() -> {
            try {
                String origin = arguments.getString("origin");
                String destination = arguments.getString("destination");
                String mode = arguments.getString("mode");

                // 如果起点是 "location"，获取当前位置
                if ("location".equals(origin)) {
                    GetLocationTool locationTool = new GetLocationTool(context);
                    final JSONObject[] locationResult = {null};
                    final Object lock = new Object();

                    locationTool.executeAsync(new JSONObject(), new OnResultCallback() {
                        @Override
                        public void onResult(JSONObject result) {
                            try {
                                if ("success".equals(result.getString("status"))) {
                                    JSONObject loc = result.getJSONObject("location");
                                    double lat = loc.getDouble("latitude");
                                    double lng = loc.getDouble("longitude");
                                    locationResult[0] = new JSONObject()
                                        .put("lat", lat)
                                        .put("lng", lng);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "解析位置失败", e);
                            }
                            synchronized (lock) {
                                lock.notify();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.e(TAG, "获取位置失败", e);
                            synchronized (lock) {
                                lock.notify();
                            }
                        }
                    });

                    synchronized (lock) {
                        lock.wait(10000); // 等待 10 秒
                    }

                    if (locationResult[0] != null) {
                        origin = locationResult[0].getDouble("lat") + "," + locationResult[0].getDouble("lng");
                    } else {
                        JSONObject error = new JSONObject();
                        error.put("status", "error");
                        error.put("message", "无法获取当前位置，请指定起点经纬度");
                        callback.onResult(error);
                        return;
                    }
                }

                // 获取 API Key
                String apiKey = getApiKey();
                if (apiKey == null || apiKey.isEmpty()) {
                    JSONObject error = new JSONObject();
                    error.put("status", "error");
                    error.put("message", "百度地图 API Key 未配置，请先在长期记忆中设置 baidu_map_api_key");
                    callback.onResult(error);
                    return;
                }

                // 构建 API URL
                String path = getApiPath(mode);
                String url = BASE_URL + path + "?origin=" + URLEncoder.encode(origin, "UTF-8")
                    + "&destination=" + URLEncoder.encode(destination, "UTF-8")
                    + "&ak=" + apiKey;

                // 驾车模式额外参数
                if ("driving".equals(mode)) {
                    url += "&coord_type=bd09ll&steps=1";
                } else {
                    url += "&coord_type=bd09ll";
                }

                Log.d(TAG, "请求 URL: " + url);

                // 发起 HTTP 请求
                Request request = new Request.Builder().url(url).build();
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "请求失败", e);
                        try {
                            JSONObject error = new JSONObject();
                            error.put("status", "error");
                            error.put("message", "网络请求失败：" + e.getMessage());
                            callback.onResult(error);
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try (ResponseBody body = response.body()) {
                            String responseJson = body.string();
                            Log.d(TAG, "API 响应：" + responseJson);

                            JSONObject result = parseResponse(responseJson, mode);
                            callback.onResult(result);
                        } catch (Exception e) {
                            Log.e(TAG, "解析响应失败", e);
                            try {
                                JSONObject error = new JSONObject();
                                error.put("status", "error");
                                error.put("message", "解析响应失败：" + e.getMessage());
                                callback.onResult(error);
                            } catch (Exception ignored) {}
                        }
                    }
                });

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

    private String getApiPath(String mode) {
        switch (mode) {
            case "driving": return DRIVING_PATH;
            case "walking": return WALKING_PATH;
            case "riding": return RIDING_PATH;
            case "transit": return TRANSIT_PATH;
            default: return DRIVING_PATH;
        }
    }

    private JSONObject parseResponse(String responseJson, String mode) {
        try {
            JSONObject json = new JSONObject(responseJson);

            if (json.has("error") && json.getInt("error") != 0) {
                JSONObject error = new JSONObject();
                error.put("status", "error");
                error.put("message", "百度地图 API 错误：" + json.getInt("error") + " - " + json.optString("message"));
                return error;
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");

            JSONArray routes = json.optJSONArray("routes");
            if (routes == null || routes.length() == 0) {
                result.put("message", "未找到可用路线");
                return result;
            }

            JSONObject bestRoute = routes.getJSONObject(0);

            // 基本信息
            result.put("distance", bestRoute.optString("distance", "0"));
            result.put("duration", bestRoute.optString("duration", "0"));

            // 格式化距离和时间
            int distanceMeters = bestRoute.optInt("distance", 0);
            int durationSeconds = bestRoute.optInt("duration", 0);

            String distanceText = distanceMeters >= 1000
                ? String.format("%.2fkm", distanceMeters / 1000.0)
                : distanceMeters + "m";

            String durationText;
            if (durationSeconds >= 3600) {
                durationText = String.format("%d小时%d分钟", durationSeconds / 3600, (durationSeconds % 3600) / 60);
            } else {
                durationText = String.format("%d分钟", (durationSeconds + 30) / 60);
            }

            result.put("formatted_distance", distanceText);
            result.put("formatted_duration", durationText);
            result.put("transport_mode", mode);

            // 路线步骤
            JSONArray steps = bestRoute.optJSONArray("steps");
            JSONArray stepsArray = new JSONArray();
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    JSONObject stepInfo = new JSONObject();
                    stepInfo.put("instruction", step.optString("instructions", ""));
                    stepInfo.put("distance", step.optString("distance", "0"));
                    stepInfo.put("duration", step.optString("duration", "0"));
                    stepsArray.put(stepInfo);
                }
            }
            result.put("steps", stepsArray);

            // 起点和终点
            JSONObject origin = bestRoute.optJSONObject("origin");
            JSONObject destination = bestRoute.optJSONObject("destination");
            if (origin != null) {
                result.put("origin_location", origin.optString("location", ""));
            }
            if (destination != null) {
                result.put("destination_location", destination.optString("location", ""));
            }

            result.put("message", "路线规划成功");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "解析响应失败", e);
            try {
                JSONObject error = new JSONObject();
                error.put("status", "error");
                error.put("message", "解析失败：" + e.getMessage());
                return error;
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
    }

    private String getApiKey() {
        try {
            // 从长期记忆读取
            SharedPreferences prefs = context.getSharedPreferences("memory", Context.MODE_PRIVATE);
            String memoryJson = prefs.getString("baidu_map_api_key", null);
            if (memoryJson != null) {
                JSONObject memory = new JSONObject(memoryJson);
                return memory.optString("content", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "读取 API Key 失败", e);
        }
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "使用百度地图 API 进行路径规划。参数：origin（起点，可用'location'表示当前位置）、destination（终点）、mode（交通方式：driving/walking/riding/transit）。返回距离、时间、详细步骤。API Key 从长期记忆自动读取。";
    }
}
