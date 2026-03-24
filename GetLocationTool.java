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
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;
import com.google.gson.Gson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GetLocationTool implements Tool {
    private static final String TAG = "GetLocationTool";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private GeoCoder geoCoder;
    
    // #4847 优化：增加超时时间到 30 秒
    private static final int LOCATION_TIMEOUT_MS = 30000;
    // #4847 优化：最大重试次数
    private static final int MAX_RETRY_COUNT = 2;

    public GetLocationTool(Context context) {
        this.context = context;
        
        // 初始化百度地图 SDK
        // AK 已从 AndroidManifest.xml 自动读取，不需要手动设置
        try {
            // 1. 同意隐私协议 - 修复 #4847：使用 ApplicationContext
            SDKInitializer.setAgreePrivacy(context.getApplicationContext(), true);
            
            // 2. 设置坐标系为 BD09LL（百度经纬度坐标）
            SDKInitializer.setCoordType(CoordType.BD09LL);
            
            // 3. 初始化 SDK（会自动从 Manifest 读取 AK）- 修复 #4847：使用 ApplicationContext
            SDKInitializer.initialize(context.getApplicationContext());
            
            // 4. 创建 GeoCoder 实例
            geoCoder = GeoCoder.newInstance();
            
            if (geoCoder != null) {
                Log.d(TAG, "百度地图 SDK 初始化成功");
            } else {
                Log.w(TAG, "GeoCoder 创建失败，将降级使用 Android Geocoder");
            }
        } catch (Exception e) {
            Log.e(TAG, "百度地图 SDK 初始化失败，将降级使用 Android Geocoder", e);
            geoCoder = null;
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
            functionDef.put("description", "查询当前地理位置，返回坐标和自然语言的地理位置信息。需要位置权限。优先使用百度地图 SDK 进行反向地理编码，失败时自动降级使用 Android 原生 Geocoder。");

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
                Log.d(TAG, "🔍 [DEBUG] 开始执行定位工具");
                
                // 检查权限
                if (!hasLocationPermission()) {
                    Log.w(TAG, "❌ [DEBUG] 位置权限未授权");
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

                Log.d(TAG, "✅ [DEBUG] 位置权限已授权");

                // 获取位置 - #4847 优化：带重试机制
                Log.d(TAG, "📍 [DEBUG] 开始获取位置...");
                LocationResult locationResult = getCurrentLocationWithRetry();
                
                if (locationResult == null || locationResult.location == null) {
                    Log.e(TAG, "❌ [DEBUG] 获取位置失败 - locationResult 为 null，已重试 " + MAX_RETRY_COUNT + " 次");
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "无法获取当前位置，请检查 GPS 是否开启或网络连接是否正常。如果在室内，请尝试到室外开阔地带重试。");
                    callback.onResult(result);
                    return;
                }

                double latitude = locationResult.location.getLatitude();
                double longitude = locationResult.location.getLongitude();
                String provider = locationResult.location.getProvider();
                float accuracy = locationResult.location.getAccuracy();

                Log.d(TAG, "✅ [DEBUG] 位置获取成功 - lat=" + latitude + ", lng=" + longitude + ", provider=" + provider + ", accuracy=" + accuracy);

                // 使用百度地图进行反向地理编码（如果 SDK 已初始化）
                String addressText = null;
                if (geoCoder != null) {
                    Log.d(TAG, "🗺️ [DEBUG] 开始百度反向地理编码...");
                    addressText = reverseGeocodeBaidu(latitude, longitude);
                    if (addressText != null && !addressText.isEmpty()) {
                        Log.d(TAG, "✅ [DEBUG] 百度反向地理编码成功：" + addressText);
                    } else {
                        Log.w(TAG, "⚠️ [DEBUG] 百度反向地理编码失败或返回空，将降级使用 Android Geocoder");
                    }
                } else {
                    Log.w(TAG, "⚠️ [DEBUG] 百度 GeoCoder 未初始化，直接使用 Android Geocoder");
                }
                
                // 备用方案：如果百度失败或 SDK 未初始化，使用 Android 原生 Geocoder
                if (addressText == null || addressText.isEmpty()) {
                    Log.d(TAG, "🗺️ [DEBUG] 开始 Android 反向地理编码...");
                    addressText = reverseGeocodeAndroid(latitude, longitude);
                    if (addressText != null && !addressText.isEmpty()) {
                        Log.d(TAG, "✅ [DEBUG] Android 反向地理编码成功：" + addressText);
                    } else {
                        Log.w(TAG, "⚠️ [DEBUG] Android 反向地理编码失败");
                    }
                }

                JSONObject result = new JSONObject();
                result.put("status", "success");
                
                JSONObject locationData = new JSONObject();
                locationData.put("latitude", latitude);
                locationData.put("longitude", longitude);
                locationData.put("accuracy", accuracy);
                locationData.put("provider", provider);
                locationData.put("address", addressText != null ? addressText : "无法解析地址");
                locationData.put("formatted_address", String.format(Locale.CHINA, "纬度：%.6f, 经度：%.6f", latitude, longitude));
                
                if (geoCoder != null && addressText != null && !addressText.isEmpty()) {
                    locationData.put("geocoder", "Baidu");
                } else {
                    locationData.put("geocoder", "Android");
                }
                
                result.put("location", locationData);
                result.put("message", "位置获取成功");

                Log.d(TAG, "✅ [DEBUG] 定位工具执行完成，返回结果");
                callback.onResult(result);

            } catch (Exception e) {
                Log.e(TAG, "❌ [DEBUG] 执行出错", e);
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
            boolean fineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean coarseLocation = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "🔐 [DEBUG] 权限检查 - FINE=" + fineLocation + ", COARSE=" + coarseLocation);
            return fineLocation || coarseLocation;
        }
        return true;
    }

    /**
     * #4847 优化：带重试机制的位置获取
     */
    private LocationResult getCurrentLocationWithRetry() {
        LocationResult result = null;
        
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            Log.d(TAG, "🔄 [DEBUG] 位置获取尝试 " + attempt + "/" + MAX_RETRY_COUNT);
            result = getCurrentLocation();
            
            if (result != null && result.location != null) {
                Log.d(TAG, "✅ [DEBUG] 位置获取成功（第 " + attempt + " 次尝试）");
                return result;
            }
            
            if (attempt < MAX_RETRY_COUNT) {
                Log.w(TAG, "⚠️ [DEBUG] 第 " + attempt + " 次尝试失败，准备重试...");
                try {
                    Thread.sleep(1000); // 重试前等待 1 秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return result;
    }

    private LocationResult getCurrentLocation() {
        Log.d(TAG, "📍 [DEBUG] getCurrentLocation() 开始执行");
        
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        
        if (locationManager == null) {
            Log.e(TAG, "❌ [DEBUG] LocationManager 为 null");
            return null;
        }
        
        // #4847 优化：优先使用 GPS 或 network，而不是 fused
        List<String> providers = locationManager.getProviders(true);
        Log.d(TAG, "📡 [DEBUG] 可用的位置提供者：" + (providers != null ? providers.size() : 0) + " 个 - " + (providers != null ? providers.toString() : "null"));
        
        // 优先选择 GPS（精度高），其次 network（室内可用），最后才是 fused
        String bestProvider = null;
        if (providers != null && providers.contains(LocationManager.GPS_PROVIDER)) {
            bestProvider = LocationManager.GPS_PROVIDER;
            Log.d(TAG, "🎯 [DEBUG] 优先选择 GPS_PROVIDER（精度高）");
        } else if (providers != null && providers.contains(LocationManager.NETWORK_PROVIDER)) {
            bestProvider = LocationManager.NETWORK_PROVIDER;
            Log.d(TAG, "🎯 [DEBUG] 选择 NETWORK_PROVIDER（室内可用）");
        } else {
            // 如果都没有，使用 getBestProvider
            bestProvider = locationManager.getBestProvider(new android.location.Criteria(), true);
            Log.d(TAG, "🎯 [DEBUG] 使用 getBestProvider: " + bestProvider);
        }

        if (bestProvider == null) {
            Log.e(TAG, "❌ [DEBUG] 没有可用的位置提供者");
            return null;
        }

        // 获取最后已知位置
        Location lastKnownLocation = null;
        try {
            lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
            if (lastKnownLocation != null) {
                Log.d(TAG, "📍 [DEBUG] 获取到最后已知位置 - lat=" + lastKnownLocation.getLatitude() + ", lng=" + lastKnownLocation.getLongitude() + ", time=" + lastKnownLocation.getTime());
            } else {
                Log.w(TAG, "⚠️ [DEBUG] 最后已知位置为 null");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ [DEBUG] 权限异常", e);
        }

        if (lastKnownLocation != null) {
            // 检查位置是否过时（超过 2 分钟）
            long currentTime = System.currentTimeMillis();
            long locationTime = lastKnownLocation.getTime();
            long age = currentTime - locationTime;
            Log.d(TAG, "⏱️ [DEBUG] 位置年龄：" + age + "ms (限制 120000ms)");
            
            if (age < 120000) { // 2 分钟内
                Log.d(TAG, "✅ [DEBUG] 使用最后已知位置（2 分钟内）");
                return new LocationResult(lastKnownLocation);
            } else {
                Log.w(TAG, "⚠️ [DEBUG] 位置过时，尝试请求新位置");
            }
        }

        // 请求更新位置（单次）
        Log.d(TAG, "📡 [DEBUG] 请求单次位置更新，提供者：" + bestProvider + ", 超时：" + LOCATION_TIMEOUT_MS + "ms");
        final LocationResult[] result = {null};
        final Object lock = new Object();
        
        try {
            locationManager.requestSingleUpdate(bestProvider, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    Log.d(TAG, "✅ [DEBUG] 位置更新回调 - lat=" + location.getLatitude() + ", lng=" + location.getLongitude());
                    result[0] = new LocationResult(location);
                    synchronized (lock) {
                        lock.notify();
                    }
                }

                @Override public void onProviderDisabled(@NonNull String provider) {
                    Log.w(TAG, "⚠️ [DEBUG] 位置提供者被禁用：" + provider);
                }
                @Override public void onProviderEnabled(@NonNull String provider) {
                    Log.d(TAG, "✅ [DEBUG] 位置提供者已启用：" + provider);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {
                    Log.d(TAG, "📡 [DEBUG] 位置提供者状态变化：" + provider + ", status=" + status);
                }
            }, Looper.getMainLooper());

            // #4847 优化：等待超时从 10 秒增加到 30 秒
            Log.d(TAG, "⏱️ [DEBUG] 等待位置更新，最多 " + (LOCATION_TIMEOUT_MS / 1000) + " 秒...");
            synchronized (lock) {
                lock.wait(LOCATION_TIMEOUT_MS);
            }
            
            if (result[0] != null) {
                Log.d(TAG, "✅ [DEBUG] 成功获取位置更新");
            } else {
                Log.w(TAG, "⚠️ [DEBUG] 位置更新超时（" + (LOCATION_TIMEOUT_MS / 1000) + " 秒）");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "❌ [DEBUG] 请求位置更新失败 - 安全异常", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "❌ [DEBUG] 请求位置更新失败 - 中断异常", e);
            Thread.currentThread().interrupt();
        }

        LocationResult finalResult = result[0] != null ? result[0] : (lastKnownLocation != null ? new LocationResult(lastKnownLocation) : null);
        if (finalResult != null) {
            Log.d(TAG, "✅ [DEBUG] getCurrentLocation() 成功返回");
        } else {
            Log.e(TAG, "❌ [DEBUG] getCurrentLocation() 失败返回 null");
        }
        return finalResult;
    }

    /**
     * 百度反向地理编码（同步等待模式）
     * 参考旅行盲盒 LauncherActivity.queryGeoCode() 方法
     */
    private String reverseGeocodeBaidu(double latitude, double longitude) {
        if (geoCoder == null) {
            Log.w(TAG, "⚠️ [DEBUG] 百度 GeoCoder 未初始化");
            return null;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultAddress = new AtomicReference<>(null);

        try {
            // 创建监听器（覆盖默认监听器）
            geoCoder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
                @Override
                public void onGetGeoCodeResult(com.baidu.mapapi.search.geocode.GeoCodeResult result) {
                    // 正向地理编码（地址→坐标），不使用
                    Log.d(TAG, "🗺️ [DEBUG] 百度正向地理编码回调（不使用）");
                    latch.countDown();
                }

                @Override
                public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {
                    Log.d(TAG, "🗺️ [DEBUG] 百度反向地理编码回调");
                    
                    if (reverseGeoCodeResult == null || 
                        reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                        Log.w(TAG, "❌ [DEBUG] 百度反向地理编码失败：" + 
                            (reverseGeoCodeResult != null ? reverseGeoCodeResult.error : "null"));
                        latch.countDown();
                        return;
                    }

                    // 提取地址信息
                    StringBuilder sb = new StringBuilder();
                    
                    // 详细地址
                    String address = reverseGeoCodeResult.getAddress();
                    if (address != null && !address.isEmpty()) {
                        sb.append(address);
                    }
                    
                    // 语义化描述
                    String sematicDescription = reverseGeoCodeResult.getSematicDescription();
                    if (sematicDescription != null && !sematicDescription.isEmpty()) {
                        if (sb.length() > 0) sb.append(" - ");
                        sb.append(sematicDescription);
                    }

                    resultAddress.set(sb.toString());
                    Log.d(TAG, "✅ [DEBUG] 百度反向地理编码成功：" + sb.toString());
                    latch.countDown();
                }
            });
            
            // 创建反向地理编码选项
            LatLng point = new LatLng(latitude, longitude);
            ReverseGeoCodeOption option = new ReverseGeoCodeOption()
                .location(point)
                // 设置是否返回新数据 默认值 0 不返回，1 返回
                .newVersion(1)
                // POI 召回半径，允许设置区间为 0-1000 米，超过 1000 米按 1000 米召回。默认值为 1000
                .radius(200);
            
            Log.d(TAG, "🗺️ [DEBUG] 发起百度反向地理编码请求：lat=" + latitude + ", lng=" + longitude);
            
            // 发起反向地理编码请求
            geoCoder.reverseGeoCode(option);
            
            // 等待回调（最多 5 秒）
            Log.d(TAG, "⏱️ [DEBUG] 等待百度反向地理编码回调，最多 5 秒...");
            if (latch.await(5, TimeUnit.SECONDS)) {
                String result = resultAddress.get();
                if (result != null) {
                    Log.d(TAG, "✅ [DEBUG] 百度反向地理编码完成：" + result);
                } else {
                    Log.w(TAG, "⚠️ [DEBUG] 百度反向地理编码返回 null");
                }
                return result;
            } else {
                Log.w(TAG, "⚠️ [DEBUG] 百度反向地理编码超时（5 秒）");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ [DEBUG] 百度反向地理编码失败", e);
            return null;
        }
    }

    private String reverseGeocodeAndroid(double latitude, double longitude) {
        Log.d(TAG, "🗺️ [DEBUG] 开始 Android Geocoder 反向地理编码");
        try {
            Geocoder geocoder = new Geocoder(context, Locale.CHINA);
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            
            if (addresses != null && !addresses.isEmpty()) {
                Log.d(TAG, "✅ [DEBUG] Android Geocoder 返回 " + addresses.size() + " 个结果");
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addr.getAddressLine(i));
                }
                
                String result = sb.toString();
                Log.d(TAG, "✅ [DEBUG] Android Geocoder 成功：" + result);
                return result;
            } else {
                Log.w(TAG, "⚠️ [DEBUG] Android Geocoder 返回空列表");
            }
        } catch (IOException e) {
            Log.e(TAG, "❌ [DEBUG] Android Geocoder 失败", e);
        } catch (Exception e) {
            Log.e(TAG, "❌ [DEBUG] Android Geocoder 异常", e);
        }
        
        Log.w(TAG, "⚠️ [DEBUG] Android Geocoder 返回 null");
        return null;
    }

    @Override
    public String getDefaultSystemPromptEnhancement() {
        return "用于查询当前地理位置，返回坐标和自然语言的地理位置信息。需要位置权限。当缺少权限时，会直接发起权限请求并提示用户授权后重试。优先使用百度地图 SDK 进行反向地理编码，失败时自动降级使用 Android 原生 Geocoder。";
    }

    // 内部类：位置结果
    private static class LocationResult {
        public final Location location;
        public LocationResult(Location location) {
            this.location = location;
        }
    }
}