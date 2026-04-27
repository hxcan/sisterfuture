package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailSearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
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
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PoiSearch poiSearch;
    
    private OnResultCallback currentCallback;
    private boolean includeDetails = false;
    private int detailSuccessCount = 0;
    private int detailFailCount = 0;

    public SearchNearbyTool(Context context) {
        this.context = context;
        
        // 创建 PoiSearch 实例
        poiSearch = PoiSearch.newInstance();
        
        // 使用独立的监听器类
        poiSearch.setOnGetPoiSearchResultListener(new PoiSearchResultListener(this));
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
            functionDef.put("description", "搜索附近的商家地点（银行、医院、超市等），返回商家列表。如果启用 include_details，会获取每个地点的详细信息（如营业时间），但会增加耗时，建议同时减小 result_count。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("query"));

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "搜索关键词，如\"银行\"、\"医院\"、\"超市\"等"));
            properties.put("city", new JSONObject()
                .put("type", "string")
                .put("description", "搜索城市（可选，默认深圳市）"));
            properties.put("result_count", new JSONObject()
                .put("type", "integer")
                .put("description", "返回结果数量（可选，默认20，最大20）"));
            properties.put("include_details", new JSONObject()
                .put("type", "boolean")
                .put("description", "是否获取详细信息（如营业时间），默认false。如果启用，建议将 result_count 设置得小一些以免耗时太长"));

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
                String query = arguments.getString("query");
                String city = arguments.optString("city", "深圳市");
                int resultCount = arguments.optInt("result_count", 20);
                includeDetails = arguments.optBoolean("include_details", false);
                
                // 限制 result_count 最大为 20
                if (resultCount > 20) resultCount = 20;
                if (resultCount < 1) resultCount = 1;

                currentCallback = callback;
                detailSuccessCount = 0;
                detailFailCount = 0;

                Log.d(TAG, "开始搜索附近 - query=" + query + ", city=" + city + ", resultCount=" + resultCount + ", includeDetails=" + includeDetails);

                // 使用 PoiCitySearchOption 进行城市内 POI 搜索
                PoiCitySearchOption searchOption = new PoiCitySearchOption();
                searchOption.keyword(query);
                searchOption.city(city);
                searchOption.pageNum(0);
                searchOption.pageCapacity(resultCount);

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

    /**
     * 处理 POI 搜索结果
     */
    void handlePoiResult(PoiResult result) {
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

            // 如果不获取详情，直接返回基础信息
            if (!includeDetails) {
                JSONObject response = buildResponse(poiList, null);
                if (currentCallback != null) {
                    currentCallback.onResult(response);
                }
                currentCallback = null;
                return;
            }

            // 需要获取详情，异步请求每个 POI 的详细信息
            Log.d(TAG, "开始获取 POI 详情，数量: " + poiList.size());
            fetchPoiDetails(poiList);

        } catch (Exception e) {
            Log.e(TAG, "处理结果失败", e);
            sendError(currentCallback, "处理结果失败：" + e.getMessage());
        }
    }

    /**
     * 异步获取 POI 详情
     */
    private void fetchPoiDetails(List<PoiInfo> poiList) {
        // 用于存储详情结果的数组
        JSONArray detailedResults = new JSONArray();
        
        // 创建一个计数器来跟踪完成的请求
        final int[] completedCount = {0};
        final int totalCount = poiList.size();
        
        for (int i = 0; i < totalCount; i++) {
            final PoiInfo poi = poiList.get(i);
            
            // 为每个 POI 创建详情搜索选项
            PoiDetailSearchOption detailOption = new PoiDetailSearchOption();
            detailOption.poiUid(poi.uid);
            
            // 异步获取详情
            poiSearch.searchPoiDetail(detailOption, new com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener() {
                @Override
                public void onGetPoiResult(PoiResult result) {
                    // 这个回调不会被调用，因为我们使用的是 searchPoiDetail 的重载方法
                }

                @Override
                public void onGetPoiDetailResult(PoiDetailResult result) {
                    try {
                        JSONObject item = new JSONObject();
                        item.put("name", poi.name != null ? poi.name : "");
                        item.put("address", poi.address != null ? poi.address : "");
                        item.put("uid", poi.uid != null ? poi.uid : "");
                        
                        if (poi.location != null) {
                            item.put("latitude", poi.location.latitude);
                            item.put("longitude", poi.location.longitude);
                        }
                        
                        // 添加详情信息
                        if (result != null) {
                            if (result.businessHours != null && !result.businessHours.isEmpty()) {
                                item.put("business_hours", result.businessHours);
                            }
                            if (result.telephone != null && !result.telephone.isEmpty()) {
                                item.put("telephone", result.telephone);
                            }
                            if (result.rating > 0) {
                                item.put("rating", result.rating);
                            }
                            if (result.commentNum > 0) {
                                item.put("comment_count", result.commentNum);
                            }
                        }
                        
                        detailedResults.put(item);
                        detailSuccessCount++;
                        
                    } catch (Exception e) {
                        Log.e(TAG, "处理详情失败 for POI: " + poi.name, e);
                        // 即使详情获取失败，也添加基础信息
                        try {
                            JSONObject item = new JSONObject();
                            item.put("name", poi.name != null ? poi.name : "");
                            item.put("address", poi.address != null ? poi.address : "");
                            item.put("uid", poi.uid != null ? poi.uid : "");
                            if (poi.location != null) {
                                item.put("latitude", poi.location.latitude);
                                item.put("longitude", poi.location.longitude);
                            }
                            detailedResults.put(item);
                        } catch (Exception ex) {
                            Log.e(TAG, "添加基础信息失败", ex);
                        }
                        detailFailCount++;
                    } finally {
                        completedCount[0]++;
                        // 所有请求完成后，返回结果
                        if (completedCount[0] == totalCount) {
                            finishWithDetails(detailedResults);
                        }
                    }
                }

                @Override
                public void onGetPoiDetailResult(PoiDetailSearchResult result) {
                    // 这个重载版本也不会被调用
                }

                @Override
                public void onGetPoiIndoorResult(PoiIndoorResult result) {
                    // 室内 POI 结果，忽略
                }
            });
        }
    }

    /**
     * 完成详情获取，返回结果
     */
    private void finishWithDetails(JSONArray detailedResults) {
        try {
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "找到 " + detailedResults.length() + " 个附近地点" + 
                (includeDetails ? " (已获取详情: 成功" + detailSuccessCount + ", 失败" + detailFailCount + ")" : ""));
            response.put("count", detailedResults.length());
            response.put("results", detailedResults);
            
            if (currentCallback != null) {
                currentCallback.onResult(response);
            }
        } catch (Exception e) {
            Log.e(TAG, "构建响应失败", e);
            sendError(currentCallback, "构建响应失败：" + e.getMessage());
        } finally {
            currentCallback = null;
        }
    }

    /**
     * 构建基础响应（不获取详情时）
     */
    private JSONObject buildResponse(List<PoiInfo> poiList, JSONArray detailedResults) throws Exception {
        JSONObject response = new JSONObject();
        response.put("status", "success");
        response.put("message", "找到 " + poiList.size() + " 个附近地点");
        response.put("count", poiList.size());

        JSONArray results = detailedResults != null ? detailedResults : new JSONArray();
        
        if (detailedResults == null) {
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
        }

        response.put("results", results);
        return response;
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
        return "搜索附近的商家地点（银行、医院、超市等），返回商家列表。参数：query(关键词), city(城市), result_count(结果数量，默认20), include_details(是否获取详情如营业时间，默认false)。如果启用 include_details，建议将 result_count 设置得小一些以免耗时太长。";
    }
}
