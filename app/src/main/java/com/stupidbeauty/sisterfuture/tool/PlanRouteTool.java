package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.util.Log;
import com.baidu.mapapi.search.route.*;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.SearchResult;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanRouteTool implements Tool {
    private static final String TAG = "PlanRouteTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RoutePlanSearch mRouteSearch = RoutePlanSearch.newInstance();
    
    private OnResultCallback currentCallback;
    private String currentMode;

    public PlanRouteTool(Context context) {
        this.context = context;
        
        mRouteSearch.setOnGetRoutePlanResultListener(new OnGetRoutePlanResultListener() {
            @Override
            public void onGetDrivingRouteResult(DrivingRouteResult result) {
                handleRouteResult(result, "driving");
            }
            
            @Override
            public void onGetWalkingRouteResult(WalkingRouteResult result) {
                handleRouteResult(result, "walking");
            }
            
            @Override
            public void onGetBikingRouteResult(BikingRouteResult result) {
                handleRouteResult(result, "riding");
            }
            
            @Override
            public void onGetTransitRouteResult(TransitRouteResult result) {
                handleRouteResult(result, "transit");
            }
            
            @Override
            public void onGetIndoorRouteResult(IndoorRouteResult result) {
                sendError(currentCallback, "不支持室内路线规划");
            }
            
            @Override
            public void onGetMassTransitRouteResult(MassTransitRouteResult result) {
                sendError(currentCallback, "不支持大交通路线规划");
            }
        });
    }

    @Override
    public String getName() { return "plan_route"; }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "plan_route");
            functionDef.put("description", "使用百度地图 SDK 进行路径规划");
            
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("required", new JSONArray().put("origin").put("destination").put("mode"));
            
            JSONObject properties = new JSONObject();
            properties.put("origin", new JSONObject()
                .put("type", "string")
                .put("description", "起点经纬度"));
            properties.put("destination", new JSONObject()
                .put("type", "string")
                .put("description", "终点经纬度"));
            properties.put("mode", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("driving").put("walking").put("riding").put("transit")));
            
            parameters.put("properties", properties);
            functionDef.put("parameters", parameters);
            return new JSONObject().put("type", "function").put("function", functionDef);
        } catch (Exception e) {
            Log.e(TAG, "getDefinition failed", e);
            return new JSONObject();
        }
    }

    @Override
    public boolean shouldInclude() { return true; }
    @Override
    public boolean isAsync() { return true; }

    @Override
    public void executeAsync(JSONObject arguments, OnResultCallback callback) {
        executor.execute(() -> {
            try {
                String origin = arguments.getString("origin");
                String destination = arguments.getString("destination");
                String mode = arguments.getString("mode");

                LatLng originLatLng = parseLocation(origin, callback);
                if (originLatLng == null) return;
                
                LatLng destLatLng = parseLocation(destination, callback);
                if (destLatLng == null) return;

                currentCallback = callback;
                currentMode = mode;

                PlanNode from = PlanNode.withLocation(originLatLng);
                PlanNode to = PlanNode.withLocation(destLatLng);
                
                switch (mode) {
                    case "driving":
                        mRouteSearch.drivingSearch(new DrivingRoutePlanOption().from(from).to(to));
                        break;
                    case "walking":
                        mRouteSearch.walkingSearch(new WalkingRoutePlanOption().from(from).to(to));
                        break;
                    case "riding":
                        mRouteSearch.bikingSearch(new BikingRoutePlanOption().from(from).to(to));
                        break;
                    case "transit":
                        mRouteSearch.transitSearch(new TransitRoutePlanOption().from(from).to(to).city("深圳市"));
                        break;
                    default:
                        sendError(callback, "不支持的交通方式：" + mode);
                        currentCallback = null;
                        currentMode = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "执行出错", e);
                sendError(callback, e.getMessage());
            }
        });
    }

    private LatLng parseLocation(String location, OnResultCallback callback) {
        try {
            if ("location".equals(location)) {
                GetLocationTool locationTool = new GetLocationTool(context);
                final LatLng[] result = {null};
                final Object lock = new Object();

                locationTool.executeAsync(new JSONObject(), new OnResultCallback() {
                    public void onResult(JSONObject r) {
                        try {
                            if ("success".equals(r.getString("status"))) {
                                JSONObject loc = r.getJSONObject("location");
                                result[0] = new LatLng(loc.getDouble("latitude"), loc.getDouble("longitude"));
                            }
                        } catch (Exception e) {}
                        synchronized (lock) { lock.notify(); }
                    }
                    public void onError(Exception e) {
                        synchronized (lock) { lock.notify(); }
                    }
                });

                synchronized (lock) { lock.wait(10000); }
                
                if (result[0] == null) {
                    sendError(callback, "无法获取当前位置");
                    return null;
                }
                return result[0];
            } else {
                String[] parts = location.split(",");
                return new LatLng(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()));
            }
        } catch (Exception e) {
            sendError(callback, "解析位置失败：" + e.getMessage());
            return null;
        }
    }

    private void handleRouteResult(Object result, String mode) {
        try {
            JSONObject response = parseSDKResult(result, mode);
            if (currentCallback != null) currentCallback.onResult(response);
        } catch (Exception e) {
            Log.e(TAG, "处理结果失败", e);
        } finally {
            currentCallback = null;
            currentMode = null;
        }
    }

    private JSONObject parseSDKResult(Object result, String mode) {
        JSONObject response = new JSONObject();
        try {
            SearchResult.ERRORNO errorCode = null;
            if (result instanceof DrivingRouteResult) {
                errorCode = ((DrivingRouteResult) result).error;
            } else if (result instanceof WalkingRouteResult) {
                errorCode = ((WalkingRouteResult) result).error;
            } else if (result instanceof BikingRouteResult) {
                errorCode = ((BikingRouteResult) result).error;
            } else if (result instanceof TransitRouteResult) {
                errorCode = ((TransitRouteResult) result).error;
            }
            
            if (errorCode != null && errorCode != SearchResult.ERRORNO.NO_ERROR) {
                response.put("status", "error");
                response.put("message", "路线规划失败：" + errorCode);
                return response;
            }
            
            List<?> routes = null;
            if (result instanceof DrivingRouteResult) {
                routes = ((DrivingRouteResult) result).getRouteLines();
            } else if (result instanceof WalkingRouteResult) {
                routes = ((WalkingRouteResult) result).getRouteLines();
            } else if (result instanceof BikingRouteResult) {
                routes = ((BikingRouteResult) result).getRouteLines();
            } else if (result instanceof TransitRouteResult) {
                routes = ((TransitRouteResult) result).getRouteLines();
            }
            
            if (routes == null || routes.isEmpty()) {
                response.put("status", "success");
                response.put("message", "未找到可用路线");
                return response;
            }
            
            Object bestRoute = routes.get(0);
            int distance = 0, duration = 0;
            
            if (bestRoute instanceof DrivingRouteLine) {
                distance = ((DrivingRouteLine) bestRoute).getDistance();
                duration = ((DrivingRouteLine) bestRoute).getDuration();
            } else if (bestRoute instanceof WalkingRouteLine) {
                distance = ((WalkingRouteLine) bestRoute).getDistance();
                duration = ((WalkingRouteLine) bestRoute).getDuration();
            } else if (bestRoute instanceof BikingRouteLine) {
                distance = ((BikingRouteLine) bestRoute).getDistance();
                duration = ((BikingRouteLine) bestRoute).getDuration();
            } else if (bestRoute instanceof TransitRouteLine) {
                distance = ((TransitRouteLine) bestRoute).getDistance();
                duration = ((TransitRouteLine) bestRoute).getDuration();
            }
            
            response.put("status", "success");
            response.put("distance", String.valueOf(distance));
            response.put("duration", String.valueOf(duration));
            response.put("formatted_distance", distance >= 1000 ? 
                String.format("%.2fkm", distance/1000.0) : distance + "m");
            response.put("formatted_duration", duration >= 3600 ?
                String.format("%d小时%d分钟", duration/3600, (duration%3600)/60) :
                String.format("%d分钟", (duration+30)/60));
            response.put("transport_mode", mode);
            response.put("message", "路线规划成功");
            response.put("steps", new JSONArray());
            
        } catch (Exception e) {
            Log.e(TAG, "parseSDKResult failed", e);
            try {
                response.put("status", "error");
                response.put("message", "解析失败：" + e.getMessage());
            } catch (Exception ignored) {}
        }
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
        return "使用百度地图 SDK 进行路径规划。参数：origin, destination, mode(driving/walking/riding/transit)。API Key 从清单文件自动读取。";
    }
}
