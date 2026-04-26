package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.baidu.mapapi.search.poi.*;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 搜索附近地址工具
 * 基于百度地图 SDK 的 POI 搜索功能
 * 
 * 功能：
 * - 根据关键词搜索附近的地点（银行、医院、超市等）
 * - 返回商家列表（含名称、地址、距离）
 * - 可选：营业时间、导航路线
 */
public class SearchNearbyTool implements Tool {
    private static final String TAG = "SearchNearbyTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PoiSearch poiSearch;
    private final GeoCoder geoCoder;
    
    private OnResultCallback currentCallback;
    private String currentQuery;

    public SearchNearbyTool(Context context) {
        this.context = context;
        // 初始化 POI 搜索
        poiSearch = PoiSearch.newInstance();
        // 初始化地理编码（用于获取当前位置）
        geoCoder = GeoCoder.newInstance();
        
        // 设置 POI 搜索监听器
        poiSearch.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(PoiResult result) {
                handlePoiResult(result);
            }
            
            @Override
            public void onGetSuggestionSearchResult(SuggestionResult result) {
                // 建议搜索结果，暂时不使用
                Log.d(TAG, "建议搜索结果：" + (result != null ? result.getSuggestions() : "null"));
            }
            
            @Override
            public void onGetPoiDetailResult(PoiDetailResult result) {
                // POI 详情结果
                Log.d(TAG, "POI 详情结果：" + (result != null ? result.getName() : "null"));
            }
            
            @Override
            public void onGetPoiDetailSearchResult(PoiDetailSearchResult result) {
                // POI 详情搜索结果
                Log.d(TAG, "POI 详情搜索结果：" + (result != null ? result.getName() : "null"));
            }
        });
    }

    @Override
    public String getName() {
        return "searchNearby";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "searchNearby");
            functionDef.put("description", "搜索附近的商家地点（银行、医院、超市等），返回商家列表。可选返回营业时间和导航路线。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("query"));

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "搜索关键词，如\"银行\"、\"医院\"、\"超市\"等"));
            properties.put("location", new JSONObject()
                .put("type", "string")
                .put("description", "搜索中心位置（可选，默认当前地址），格式：\"纬度,经度\" 或 \"location\"（使用当前位置）"));
            properties.put("radius", new JSONObject()
                .put("type", "integer")
                .put("description", "搜索半径（米），默认1000米"));
            properties.put("includeOpeningHours", new JSONObject()
                .put("type", "boolean")
                .put("description", "是否包含营业时间，默认false"));
            properties.put("includeRoute", new JSONObject()
                .put("type", "boolean")
                .put("description", "是否包含导航路线，默认false"));

            parameters.put("properties", properties);
            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "getDefinition failed", e);
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
                // 获取参数
                String query = arguments.getString("query");
                String location = arguments.optString("location", "location"); // 默认使用当前位置
                int radius = arguments.optInt("radius", 1000); // 默认1000米
                // 注意：includeOpeningHours 和 includeRoute 参数暂时未使用，预留给扩展功能
                // boolean includeOpeningHours = arguments.optBoolean("includeOpeningHours", false);
                // boolean includeRoute = arguments.optBoolean("includeRoute", false);

                currentCallback = callback;
                currentQuery = query;

                Log.d(TAG, "🔍 [DEBUG] 开始搜索附近 - query=" + query + ", location=" + location + ", radius=" + radius);

                // 解析位置
                LatLng centerLatLng = parseLocation(location, callback);
                if (centerLatLng == null) {
                    sendError(callback, "无法获取搜索中心位置");
                    return;
                }

                Log.d(TAG, "📍 [DEBUG] 搜索中心位置：" + centerLatLng.latitude + "," + centerLatLng.longitude);

                // 创建搜索选项
                PoiSearchOption searchOption = new PoiSearchOption()
                    .keyword(query)
                    .location(centerLatLng)
                    .radius(radius)
                    .pageNum(0)
                    .pageCapacity(20);

                // 执行搜索
                boolean success = poiSearch.searchInCity(new PoiCitySearchOption()
                    .city("深圳") // 默认城市
                    .searchOption(searchOption));

                if (!success) {
                    Log.e(TAG, "❌ [DEBUG] POI 搜索失败");
                    sendError(callback, "搜索失败，请稍后重试");
                    currentCallback = null;
                    currentQuery = null;
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ [DEBUG] 执行出错", e);
                sendError(callback, e.getMessage());
            }
        });
    }

    /**
     * 解析位置字符串
     */
    private LatLng parseLocation(String location, OnResultCallback callback) {
        try {
            if (location == null || location.isEmpty() || "location".equals(location)) {
                // 使用当前位置
                return getCurrentLocation(callback);
            } else if ("current".equals(location)) {
                return getCurrentLocation(callback);
            } else {
                // 解析经纬度字符串
                String[] parts = location.split(",");
                if (parts.length >= 2) {
                    return new LatLng(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim())
                    );
                } else {
                    sendError(callback, "位置格式错误，应为\"纬度,经度\"");
                    return null;
                }
            }
        } catch (Exception e) {
            sendError(callback, "解析位置失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 获取当前位置
     */
    private LatLng getCurrentLocation(OnResultCallback callback) {
        final LatLng[] result = {null};
        final CountDownLatch latch = new CountDownLatch(1);
        
        // 使用 GetLocationTool 获取当前位置
        GetLocationTool locationTool = new GetLocationTool(context);
        locationTool.executeAsync(new JSONObject(), new OnResultCallback() {
            @Override
            public void onResult(JSONObject r) {
                try {
                    if ("success".equals(r.getString("status"))) {
                        JSONObject loc = r.getJSONObject("location");
                        result[0] = new LatLng(
                            loc.getDouble("latitude"),
                            loc.getDouble("longitude")
                        );
                        Log.d(TAG, "✅ [DEBUG] 获取当前位置成功：" + result[0].latitude + "," + result[0].longitude);
                    } else {
                        Log.w(TAG, "⚠️ [DEBUG] 获取当前位置失败：" + r.optString("message"));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ [DEBUG] 解析位置失败", e);
                } finally {
                    latch.countDown();
                }
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "❌ [DEBUG] 获取位置出错", e);
                latch.countDown();
            }
        });

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "❌ [DEBUG] 等待位置超时", e);
            Thread.currentThread().interrupt();
        }

        if (result[0] == null) {
            sendError(callback, "无法获取当前位置，请确认位置权限已开启");
        }
        return result[0];
    }

    /**
     * 处理 POI 搜索结果
     */
    private void handlePoiResult(PoiResult result) {
        try {
            Log.d(TAG, "🗺️ [DEBUG] POI 搜索回调");
            
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                Log.e(TAG, "❌ [DEBUG] POI 搜索失败：" + (result != null ? result.error : "null"));
                sendError(currentCallback, "搜索失败：" + (result != null ? result.error : "未知错误"));
                currentCallback = null;
                currentQuery = null;
                return;
            }

            List<PoiInfo> poiList = result.getAllPoi();
            if (poiList == null || poiList.isEmpty()) {
                Log.w(TAG, "⚠️ [DEBUG] 未找到相关地点");
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "未找到附近的相关地点");
                response.put("count", 0);
                response.put("results", new JSONArray());
                
                if (currentCallback != null) {
                    currentCallback.onResult(response);
                }
                currentCallback = null;
                currentQuery = null;
                return;
            }

            Log.d(TAG, "✅ [DEBUG] 找到 " + poiList.size() + " 个结果");

            // 构建返回结果
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "找到 " + poiList.size() + " 个附近地点");
            response.put("count", poiList.size());

            JSONArray results = new JSONArray();
            for (int i = 0; i < Math.min(poiList.size(), 20); i++) {
                PoiInfo poi = poiList.get(i);
                JSONObject item = new JSONObject();
                item.put("name", poi.name != null ? poi.name : "");
                item.put("address", poi.address != null ? poi.address : "");
                item.put("uid", poi.uid != null ? poi.uid : "");
                
                if (poi.location != null) {
                    item.put("latitude", poi.location.latitude);
                    item.put("longitude", poi.location.longitude);
                }
                
                // 距离（如有）
                if (poi.distance > 0) {
                    item.put("distance", poi.distance);
                    item.put("formattedDistance", formatDistance(poi.distance));
                }
                
                // 营业时间（如有）
                if (poi.poiDetailInfo != null && poi.poiDetailInfo.getOpeningHours() != null) {
                    item.put("openingHours", poi.poiDetailInfo.getOpeningHours());
                }
                
                results.put(item);
            }

            response.put("results", results);

            if (currentCallback != null) {
                currentCallback.onResult(response);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ [DEBUG] 处理结果失败", e);
            sendError(currentCallback, "处理结果失败：" + e.getMessage());
        } finally {
            currentCallback = null;
            currentQuery = null;
        }
    }

    /**
     * 格式化距离
     */
    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format("%.2fkm", meters / 1000.0);
        } else {
            return meters + "m";
        }
    }

    /**
     * 发送错误
     */
    private void sendError(OnResultCallback callback, String message) {
        if (callback == null) return;
        try {
            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", message);
            callback.onResult(error);
        } catch (Exception ignored) {}
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "搜索附近的商家地点（银行、医院、超市等），返回商家列表。可选返回营业时间和导航路线。参数：query(关键词), location(位置，默认当前位置), radius(搜索半径，默认1000米), includeOpeningHours, includeRoute。";
    }
}