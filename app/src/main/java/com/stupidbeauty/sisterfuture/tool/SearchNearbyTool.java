package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.baidu.mapapi.search.poi.PoiSearch;
import com.baidu.mapapi.search.poi.PoiNearbySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailSearchOption;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.model.LatLng;
import com.baidu.location.BDLocation;
import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 搜索附近地址工具
 * 基于百度地图 SDK 的 POI 搜索功能
 * 支持基于 GPS 位置的附近搜索
 */
public class SearchNearbyTool implements Tool {
    private static final String TAG = "SearchNearbyTool";
    private static final long DETAIL_TIMEOUT_MS = 5000; // 详情获取超时 5 秒
    private static final int DEFAULT_RADIUS_METERS = 1000; // 默认搜索半径 1000 米
    
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final PoiSearch poiSearch;
    private final PoiSearchResultListener listener;
    
    private OnResultCallback currentCallback;
    private boolean includeDetails = false;
    private int detailSuccessCount = 0;
    private int detailFailCount = 0;
    private JSONArray detailedResults;
    private List<PoiInfo> currentPoiList;
    private int completedCount = 0;
    private CountDownLatch detailLatch;
    private AtomicBoolean isTimedOut = new AtomicBoolean(false);
    private int totalDetailRequests = 0;
    
    // 位置相关
    private LocationClient locationClient;
    private BDLocation currentLocation;
    private CountDownLatch locationLatch;

    public SearchNearbyTool(Context context) {
        this.context = context;
        
        // 创建 PoiSearch 实例
        poiSearch = PoiSearch.newInstance();
        
        // 使用独立的监听器类
        listener = new PoiSearchResultListener(this);
        poiSearch.setOnGetPoiSearchResultListener(listener);
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
            functionDef.put("description", "搜索附近的商家地点（银行、医院、超市等），返回商家列表。基于 GPS 位置搜索，支持自定义中心点和搜索半径。");

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("query"));

            JSONObject properties = new JSONObject();
            properties.put("query", new JSONObject()
                .put("type", "string")
                .put("description", "搜索关键词，如\"银行\"、\"医院\"、\"超市\"等"));
            properties.put("latitude", new JSONObject()
                .put("type", "number")
                .put("description", "搜索中心点纬度（可选，不填则自动获取 GPS 位置）"));
            properties.put("longitude", new JSONObject()
                .put("type", "number")
                .put("description", "搜索中心点经度（可选，不填则自动获取 GPS 位置）"));
            properties.put("radius", new JSONObject()
                .put("type", "integer")
                .put("description", "搜索半径，单位米（可选，默认 1000，最大 5000）"));
            properties.put("result_count", new JSONObject()
                .put("type", "integer")
                .put("description", "返回结果数量（可选，默认 20，最大 20）"));
            properties.put("include_details", new JSONObject()
                .put("type", "boolean")
                .put("description", "是否获取详细信息（如营业时间），默认 false。如果启用，建议将 result_count 设置得小一些以免耗时太长"));

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
                Double latitude = arguments.has("latitude") && !arguments.isNull("latitude") ? arguments.getDouble("latitude") : null;
                Double longitude = arguments.has("longitude") && !arguments.isNull("longitude") ? arguments.getDouble("longitude") : null;
                int radius = arguments.optInt("radius", DEFAULT_RADIUS_METERS);
                int resultCount = arguments.optInt("result_count", 20);
                includeDetails = arguments.optBoolean("include_details", false);
                
                // 限制参数范围
                if (radius > 5000) radius = 5000;
                if (radius < 100) radius = 100;
                if (resultCount > 20) resultCount = 20;
                if (resultCount < 1) resultCount = 1;

                currentCallback = callback;
                detailSuccessCount = 0;
                detailFailCount = 0;
                completedCount = 0;
                isTimedOut.set(false);

                Log.d(TAG, "开始搜索附近 - query=" + query + ", lat=" + latitude + ", lng=" + longitude + ", radius=" + radius + ", resultCount=" + resultCount + ", includeDetails=" + includeDetails);

                // 如果没有提供位置，先获取 GPS 位置
                if (latitude == null || longitude == null) {
                    BDLocation location = getCurrentLocation();
                    if (location == null) {
                        sendError(callback, "无法获取当前位置，请检查定位权限");
                        currentCallback = null;
                        return;
                    }
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    currentLocation = location;
                    Log.d(TAG, "自动获取 GPS 位置: " + latitude + ", " + longitude);
                }

                // 使用 PoiNearbySearchOption 进行附近搜索
                LatLng center = new LatLng(latitude, longitude);
                PoiNearbySearchOption searchOption = new PoiNearbySearchOption();
                searchOption.keyword(query);
                searchOption.location(center);
                searchOption.radius(radius);
                searchOption.pageNum(0);
                searchOption.pageCapacity(resultCount);

                boolean success = poiSearch.searchNearby(searchOption);

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
     * 获取当前 GPS 位置
     */
    private BDLocation getCurrentLocation() {
        try {
            locationLatch = new CountDownLatch(1);
            
            locationClient = new LocationClient(context);
            LocationClientOption option = new LocationClientOption();
            option.setIsNeedAddress(true);
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("bd09ll"); // 百度坐标
            option.setScanSpan(1000); // 1秒定位一次
            locationClient.setLocOption(option);
            
            locationClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation location) {
                    if (location != null && location.getLocType() == BDLocation.TypeGpsLocation) {
                        currentLocation = location;
                    }
                    locationLatch.countDown();
                }
            });
            
            locationClient.start();
            
            // 等待最多 10 秒获取位置
            boolean gotLocation = locationLatch.await(10, TimeUnit.SECONDS);
            locationClient.stop();
            
            if (gotLocation && currentLocation != null) {
                return currentLocation;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "获取位置失败", e);
            return null;
        }
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
            Log.d(TAG, "开始获取 POI 详情，数量：" + poiList.size());
            currentPoiList = poiList;
            detailedResults = new JSONArray();
            totalDetailRequests = poiList.size();
            completedCount = 0;
            
            // 创建倒计时闩锁，用于等待所有详情请求完成或超时
            detailLatch = new CountDownLatch(poiList.size());
            
            // 启动超时检测线程
            startDetailTimeoutWatcher();
            
            fetchPoiDetails(poiList);

        } catch (Exception e) {
            Log.e(TAG, "处理结果失败", e);
            sendError(currentCallback, "处理结果失败：" + e.getMessage());
        }
    }

    /**
     * 启动详情获取超时检测
     */
    private void startDetailTimeoutWatcher() {
        Thread timeoutThread = new Thread(() -> {
            try {
                // 等待所有详情请求完成，或超时
                boolean completed = detailLatch.await(DETAIL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (!completed && !isTimedOut.get()) {
                    // 超时了，但还没有全部完成
                    isTimedOut.set(true);
                    Log.w(TAG, "详情获取超时，未完成的请求数：" + (totalDetailRequests - completedCount));
                    
                    // 在主线程中完成
                    new Handler(Looper.getMainLooper()).post(() -> {
                        finishWithDetails(detailedResults);
                    });
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "超时检测线程被中断", e);
                Thread.currentThread().interrupt();
            }
        });
        timeoutThread.setDaemon(true);
        timeoutThread.start();
    }

    /**
     * 异步获取 POI 详情
     */
    private void fetchPoiDetails(List<PoiInfo> poiList) {
        for (int i = 0; i < poiList.size(); i++) {
            if (isTimedOut.get()) {
                Log.w(TAG, "已超时，跳过剩余详情请求");
                break;
            }
            
            PoiInfo poi = poiList.get(i);
            
            // 为每个 POI 创建详情搜索选项
            PoiDetailSearchOption detailOption = new PoiDetailSearchOption();
            detailOption.poiUid(poi.uid);
            
            // 在调用 searchPoiDetail 之前，设置当前正在获取详情的 POI 信息
            listener.setPendingPoiInfo(poi);
            
            // 异步获取详情（单参数版本）
            poiSearch.searchPoiDetail(detailOption);
        }
    }

    /**
     * 处理 POI 详情结果（由 PoiSearchResultListener 调用）
     */
    void handlePoiDetailResult(PoiInfo poi, PoiDetailResult result) {
        // 如果已经超时，跳过处理
        if (isTimedOut.get()) {
            Log.d(TAG, "已超时，跳过详情处理: " + poi.name);
            return;
        }
        
        try {
            JSONObject item = new JSONObject();
            item.put("name", poi.name != null ? poi.name : "");
            item.put("address", poi.address != null ? poi.address : "");
            item.put("uid", poi.uid != null ? poi.uid : "");
            
            if (poi.location != null) {
                item.put("latitude", poi.location.latitude);
                item.put("longitude", poi.location.longitude);
            }
            
            // 添加详情信息（只保留可用的字段）
            if (result != null) {
                if (result.telephone != null && !result.telephone.isEmpty()) {
                    item.put("telephone", result.telephone);
                }
                if (result.commentNum > 0) {
                    item.put("comment_count", result.commentNum);
                }
            }
            
            synchronized (detailedResults) {
                detailedResults.put(item);
            }
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
                synchronized (detailedResults) {
                    detailedResults.put(item);
                }
            } catch (Exception ex) {
                Log.e(TAG, "添加基础信息失败", ex);
            }
            detailFailCount++;
        } finally {
            completedCount++;
            // 标记一个详情请求完成
            if (detailLatch != null) {
                detailLatch.countDown();
            }
            
            // 所有请求完成后，返回结果
            if (completedCount >= totalDetailRequests) {
                finishWithDetails(detailedResults);
            }
        }
    }

    /**
     * 完成详情获取，返回结果
     */
    private void finishWithDetails(JSONArray detailedResults) {
        try {
            JSONObject response = new JSONObject();
            response.put("status", "success");
            
            String timeoutNote = isTimedOut.get() ? " (部分详情获取超时)" : "";
            response.put("message", "找到 " + detailedResults.length() + " 个附近地点" + 
                (includeDetails ? " (已获取详情：成功" + detailSuccessCount + ", 失败" + detailFailCount + ")" + timeoutNote : ""));
            response.put("count", detailedResults.length());
            response.put("results", detailedResults);
            response.put("detail_success_count", detailSuccessCount);
            response.put("detail_fail_count", detailFailCount);
            response.put("is_timed_out", isTimedOut.get());
            
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
        return "搜索附近的商家地点（银行、医院、超市等），返回商家列表。基于 GPS 位置搜索。参数：query(关键词), latitude(纬度，可选), longitude(经度，可选), radius(搜索半径，默认1000米), result_count(结果数量，默认 20), include_details(是否获取详情，默认 false)。如果不提供 latitude 和 longitude，将自动获取 GPS 位置。";
    }
}
