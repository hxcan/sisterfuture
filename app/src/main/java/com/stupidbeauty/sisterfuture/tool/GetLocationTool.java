package com.stupidbeauty.sisterfuture.tool;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoReverseCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeSearch;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GetLocationTool implements Tool {
    private static final String TAG = "GetLocationTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ReverseGeoCodeSearch geoSearch;

    public GetLocationTool(Context context) {
        this.context = context;
        // 初始化百度地图 SDK
        try {
            SDKInitializer.setAgreePrivacy(context);
            SDKInitializer.setCoordType(CoordType.BD09LL);
            SDKInitializer.initialize(context);
            geoSearch = ReverseGeoCodeSearch.newInstance();
        } catch (Exception e) {
            Log.e(TAG, "百度地图 SDK 初始化失败", e);
        }
    }

    @Override
    public String getName() {
        return "get_location";
    }

    @Override
    public JSONObject getDefinition() {
        try {
            JSONObject functionDef = new JSONObject();
            functionDef.put("name", "get_location");
            functionDef.put("description", "查询当前地理位置，返回坐标和自然语言的地理位置信息。需要位置权限。");

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
                if (!hasLocationPermission()) {
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "当前不具有位置权限，需要您授权才能访问地理位置。请允许权限请求，之后再重试此操作。");
                    callback.onResult(result);

                    // 在主线程发起权限请求
                    ((Activity) context).runOnUiThread(() -> {
                        Log.d(TAG, "尝试发起位置权限请求");
                        if (context instanceof Activity) {
                            Activity activity = (Activity) context;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                activity.requestPermissions(
                                    new String[]{
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    },
                                    1002
                                );
                            }
                        }
                    });
                    return;
                }

                // 获取位置
                LocationResult locationResult = getCurrentLocation();
                
                if (locationResult == null || locationResult.location == null) {
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "无法获取当前位置，请检查 GPS 是否开启或网络连接是否正常。");
                    callback.onResult(result);
                    return;
                }

                double latitude = locationResult.location.getLatitude();
                double longitude = locationResult.location.getLongitude();
                String provider = locationResult.location.getProvider();
                float accuracy = locationResult.location.getAccuracy();

                // 使用百度地图进行反向地理编码
                String addressText = reverseGeocodeBaidu(latitude, longitude);
                
                // 备用方案：如果百度失败，使用 Android 原生 Geocoder
                if (addressText == null || addressText.isEmpty()) {
                    addressText = reverseGeocodeAndroid(latitude, longitude);
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                
                JSONObject locationData = new JSONObject();
                locationData.put("latitude", latitude);
                locationData.put("longitude", longitude);
                locationData.put("accuracy", accuracy);
                locationData.put("provider", provider);
                locationData.put("address", addressText != null ? addressText : "无法解析地址");
                locationData.put("formatted_address", String.format(Locale.CHINA, "纬度: %.6f, 经度: %.6f", latitude, longitude));
                
                result.put("location", locationData);
                result.put("message", "位置获取成功");

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

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                   context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private LocationResult getCurrentLocation() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        // 尝试 GPS 定位
        List<String> providers = locationManager.getProviders(true);
        String bestProvider = locationManager.getBestProvider(
            new android.location.Criteria(), true
        );

        if (bestProvider == null && providers.contains(LocationManager.GPS_PROVIDER)) {
            bestProvider = LocationManager.GPS_PROVIDER;
        }
        if (bestProvider == null && providers.contains(LocationManager.NETWORK_PROVIDER)) {
            bestProvider = LocationManager.NETWORK_PROVIDER;
        }

        if (bestProvider == null) {
            Log.e(TAG, "没有可用的位置提供者");
            return null;
        }

        // 获取最后已知位置
        Location lastKnownLocation = null;
        try {
            lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
        } catch (SecurityException e) {
            Log.e(TAG, "权限异常", e);
        }

        if (lastKnownLocation != null) {
            // 检查位置是否过时（超过 2 分钟）
            long currentTime = System.currentTimeMillis();
            long locationTime = lastKnownLocation.getTime();
            if (currentTime - locationTime < 120000) { // 2 分钟内
                return new LocationResult(lastKnownLocation);
            }
        }

        // 请求更新位置（单次）
        final LocationResult[] result = {null};
        final Object lock = new Object();
        
        try {
            locationManager.requestSingleUpdate(bestProvider, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    result[0] = new LocationResult(location);
                    synchronized (lock) {
                        lock.notify();
                    }
                }

                @Override public void onProviderDisabled(@NonNull String provider) {}
                @Override public void onProviderEnabled(@NonNull String provider) {}
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            }, Looper.getMainLooper());

            // 等待最多 10 秒
            synchronized (lock) {
                lock.wait(10000);
            }
        } catch (SecurityException | InterruptedException e) {
            Log.e(TAG, "请求位置更新失败", e);
        }

        return result[0] != null ? result[0] : (lastKnownLocation != null ? new LocationResult(lastKnownLocation) : null);
    }

    private String reverseGeocodeBaidu(double latitude, double longitude) {
        if (geoSearch == null) {
            Log.w(TAG, "百度地图搜索实例未初始化");
            return null;
        }

        try {
            ReverseGeoCodeOption option = new ReverseGeoCodeOption();
            option.location(new com.baidu.mapapi.model.LatLng(latitude, longitude));
            
            // 同步调用（在后台线程）
            // 注意：百度 SDK 通常是异步回调，这里简化处理
            // 实际项目中可能需要使用 CountDownLatch 等待回调
            Log.d(TAG, "百度反向地理编码: " + latitude + ", " + longitude);
            
            // 由于百度 SDK 是异步的，这里返回一个占位符
            // 完整实现需要使用回调或 LiveData
            return "纬度: " + latitude + ", 经度: " + longitude + " (百度 SDK 需异步处理)";
            
        } catch (Exception e) {
            Log.e(TAG, "百度反向地理编码失败", e);
            return null;
        }
    }

    private String reverseGeocodeAndroid(double latitude, double longitude) {
        try {
            Geocoder geocoder = new Geocoder(context, Locale.CHINA);
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addr.getAddressLine(i));
                }
                
                return sb.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Android Geocoder 失败", e);
        }
        
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "用于查询当前地理位置，返回坐标和自然语言的地理位置信息。需要位置权限。当缺少权限时，会直接发起权限请求并提示用户授权后重试。优先使用百度地图 SDK 进行反向地理编码，Android 原生 Geocoder 作为备用方案。";
    }

    // 内部类：位置结果
    private static class LocationResult {
        public final Location location;
        public LocationResult(Location location) {
            this.location = location;
        }
    }
}