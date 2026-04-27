package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 搜索附近地址工具
 * 基于百度地图 SDK 的 POI 搜索功能
 */
public class SearchNearbyTool implements Tool {
    private static final String TAG = "SearchNearbyTool";
    private static volatile boolean sdkInitialized = false;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PoiSearch poiSearch;
    
    private OnResultCallback currentCallback;

    public SearchNearbyTool(Context context) {
        this.context = context;
        
        // 只初始化一次 SDK（参考 PlanRouteTool 的实现方式）
        if (!sdkInitialized) {
            synchronized (SearchNearbyTool.class) {
                if (!sdkInitialized) {
                    try {
                        // 参考 GetLocationTool 的实现初始化百度地图 SDK
                        SDKInitializer.setAgreePrivacy(context.getApplicationContext(), true);
                        SDKInitializer.setCoordType(CoordType.BD09LL);
                        SDKInitializer.initialize(context.getApplicationContext());
                        sdkInitialized = true;
                        Log.d(TAG, "百度地图 SDK 初始化成功");
                    } catch (Exception e) {
                        Log.e(TAG, "百度地图 SDK 初始化失败", e);
                    }
                }
            }
        }
        
        // 创建 PoiSearch 实例
        poiSearch = PoiSearch.newInstance();
        
        // 使用 OnGetPoiSearchResultListener 接口监听 POI 搜索结果
        poiSearch.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(PoiResult result) {
                handlePoiResult(result);
            }

            @Override
            public void onGetPoiDetailResult(PoiDetailResult result) {
                // POI 详情检索结果回调，此处不需要处理
                Log.d(TAG, "onGetPoiDetailResult called");
            }

            @Override
            public void onGetPoiIndoorResult(PoiIndoorResult result) {
                // 室内 POI 检索结果回调，此处不需要处理
                Log.d(TAG, "onGetPoiIndoorResult called");
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
            functionDef.put("description", "搜索附近的商家地点（银行、医院、超市等），返回商家列表。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("query"));

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "搜索关键词，如\"银行\"、\"医院\"、\"超市\"等"));
            properties.put("location", new JSONObject()
                .put("type", "string")
                .put("description", "搜索中心位置（可选，默认当前地址）"));
            properties.put("radius", new JSONObject()
                .put("type", "integer")
                .put("description", "搜索半径（米），默认1000米"));

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
                if (poiSearch == null) {
                    sendError(callback, "百度地图 SDK 未初始化成功，请检查权限和网络");
                    return;
                }

                String query = arguments.getString("query");
                int radius = arguments.optInt("radius", 1000);

                currentCallback = callback;

                Log.d(TAG, "开始搜索附近 - query=" + query + ", radius=" + radius);

                // 使用 PoiCitySearchOption 进行城市内 POI 搜索
                PoiCitySearchOption searchOption = new PoiCitySearchOption();
                searchOption.keyword(query);
                searchOption.city("深圳");
                searchOption.pageNum(0);
                searchOption.pageCapacity(20);

                boolean success = poiSearch.searchInCity(searchOption);

                if (!success) {
                    Log.e(TAG, "POI 搜索失败");
                    sendError(callback, "搜索失败，请稍后重试");
                    currentCallback = null;
                }

            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                sendError(callback, e.getMessage());
            }
        });
    }

    private void handlePoiResult(PoiResult result) {
        try {
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                sendError(currentCallback, "搜索失败：" + (result != null ? result.error : "未知错误"));
                currentCallback = null;
                return;
            }

            List<PoiInfo> poiList = result.getAllPoi();
            if (poiList == null || poiList.isEmpty()) {
                JSONObject response = new JSONObject();
                response.put("status", "success");
                response.put("message", "未找到附近的相关地点");
                response.put("count", 0);
                response.put("results", new JSONArray());
                if (currentCallback != null) {
                    currentCallback.onResult(response);
                }
                currentCallback = null;
                return;
            }

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
                
                results.put(item);
            }

            response.put("results", results);
            if (currentCallback != null) {
                currentCallback.onResult(response);
            }

        } catch (Exception e) {
            Log.e(TAG, "处理结果失败", e);
            sendError(currentCallback, "处理结果失败：" + e.getMessage());
        } finally {
            currentCallback = null;
        }
    }

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
        return "搜索附近的商家地点（银行、医院、超市等），返回商家列表。参数：query(关键词), location(位置), radius(搜索半径)。";
    }
}
