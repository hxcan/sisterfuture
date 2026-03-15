package com.stupidbeauty.sisterfuture.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * 获取当前无线网络信息工具
 * 实现 Tool 接口，可被 AI 助手调用
 */
public class NetworkInfoTool implements Tool
{
  private static final String TAG = "NetworkInfoTool";
  private Context context;

  public NetworkInfoTool(Context context)
  {
    this.context = context;
  }

  @Override
  public String getName()
  {
    return "get_network_info";
  }

  @Override
  public JSONObject getDefinition() throws Exception
  {
    JSONObject definition = new JSONObject();
    definition.put("name", getName());
    definition.put("description", "获取当前无线网络详细信息，包括 SSID、IP 地址、信号强度、网关等");
    
    JSONObject parameters = new JSONObject();
    parameters.put("type", "object");
    parameters.put("properties", new JSONObject());
    parameters.put("required", new JSONArray());
    
    definition.put("parameters", parameters);
    return definition;
  }

  @Override
  public boolean shouldInclude()
  {
    return true;
  }

  @Override
  public String getDefaultSystemPromptEnhancement()
  {
    return "使用 `get_network_info` 工具可以获取当前设备连接的 WiFi 网络详细信息，包括 SSID、IP 地址、信号强度、网关等。当用户询问网络状态、IP 地址或需要诊断网络问题时使用此工具。";
  }

  @Override
  public JSONObject execute(JSONObject arguments) throws Exception
  {
    return getNetworkInfo();
  }

  /**
   * 获取无线网络详细信息
   * @return JSON 格式的网络信息
   */
  public JSONObject getNetworkInfo()
  {
    JSONObject result = new JSONObject();
    try
    {
      WifiManager wifiManager = (WifiManager) context.getApplicationContext()
        .getSystemService(Context.WIFI_SERVICE);
      ConnectivityManager connectivityManager = (ConnectivityManager) context.getApplicationContext()
        .getSystemService(Context.CONNECTIVITY_SERVICE);

      NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
      result.put("isConnected", networkInfo != null && networkInfo.isConnected());
      result.put("networkType", networkInfo != null ? networkInfo.getTypeName() : "NONE");

      if (wifiManager != null && wifiManager.isWifiEnabled())
      {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        
        // SSID 处理 (Android 10+ 需要去除引号)
        String ssid = wifiInfo.getSSID();
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\""))
        {
          ssid = ssid.substring(1, ssid.length() - 1);
        }
        result.put("ssid", ssid != null ? ssid : "UNKNOWN");
        result.put("bssid", wifiInfo.getBSSID());
        result.put("signalStrength", wifiInfo.getRssi());
        result.put("linkSpeed", wifiInfo.getLinkSpeed());
        result.put("frequency", wifiInfo.getFrequency());
        
        // IP 地址转换
        int ipInt = wifiInfo.getIpAddress();
        String ip = String.format("%d.%d.%d.%d",
          (ipInt & 0xff), (ipInt >> 8 & 0xff),
          (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
        result.put("ipAddress", ip);
        result.put("isLocalNetwork", isLocalAddress(ip));
        
        // 子网掩码 (仅 Android 10+ API 30+ 支持)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        {
          try
          {
            // 使用反射调用 getSubnetMask() 避免编译错误
            java.lang.reflect.Method method = wifiInfo.getClass().getMethod("getSubnetMask");
            int subnetMask = (Integer) method.invoke(wifiInfo);
            result.put("subnetMask", intToIp(subnetMask));
          }
          catch (NoSuchMethodException e)
          {
            Log.w(TAG, "getSubnetMask() not available on this API level");
            result.put("subnetMask", "N/A");
          }
        }
        else
        {
          result.put("subnetMask", "N/A (API < 30)");
        }
        
        // 网关
        int gatewayInt = wifiManager.getDhcpInfo().gateway;
        String gateway = intToIp(gatewayInt);
        result.put("gateway", gateway);
      }
      else
      {
        result.put("ssid", "WIFI_DISABLED");
        result.put("bssid", null);
        result.put("signalStrength", -1);
        result.put("linkSpeed", -1);
        result.put("frequency", -1);
        result.put("ipAddress", null);
        result.put("isLocalNetwork", false);
        result.put("subnetMask", "N/A");
        result.put("gateway", null);
      }

      result.put("status", "success");
      result.put("message", "网络信息获取成功");

    }
    catch (Exception e)
    {
      Log.e(TAG, "获取网络信息失败", e);
      result.put("status", "error");
      result.put("message", "获取失败：" + e.getMessage());
    }
    return result;
  }

  /**
   * 判断 IP 是否为局域网地址
   */
  private boolean isLocalAddress(String ip)
  {
    if (ip == null) return false;
    return ip.startsWith("192.168.") || 
           ip.startsWith("10.") || 
           ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
           ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
           ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
           ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
           ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
           ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
           ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
           ip.startsWith("172.30.") || ip.startsWith("172.31.") ||
           "127.0.0.1".equals(ip);
  }

  /**
   * 整数转 IP 地址字符串
   */
  private String intToIp(int ipInt)
  {
    return String.format("%d.%d.%d.%d",
      (ipInt & 0xff), (ipInt >> 8 & 0xff),
      (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
  }

  /**
   * 获取所有网络接口详细信息
   */
  public JSONObject getAllNetworkInterfaces()
  {
    JSONObject result = new JSONObject();
    try
    {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      JSONObject interfacesJson = new JSONObject();
      
      for (NetworkInterface iface : interfaces)
      {
        JSONObject ifaceInfo = new JSONObject();
        ifaceInfo.put("displayName", iface.getDisplayName());
        ifaceInfo.put("name", iface.getName());
        ifaceInfo.put("isUp", iface.isUp());
        ifaceInfo.put("isLoopback", iface.isLoopback());
        ifaceInfo.put("isPointToPoint", iface.isPointToPoint());
        ifaceInfo.put("supportsMulticast", iface.supportsMulticast());
        
        List<InetAddress> addresses = Collections.list(iface.getInetAddresses());
        JSONArray ipArray = new JSONArray();
        for (InetAddress addr : addresses)
        {
          ipArray.put(addr.getHostAddress());
        }
        ifaceInfo.put("ipAddresses", ipArray);
        interfacesJson.put(iface.getName(), ifaceInfo);
      }
      
      result.put("status", "success");
      result.put("interfaces", interfacesJson);
      result.put("count", interfaces.size());
    }
    catch (Exception e)
    {
      Log.e(TAG, "获取网络接口失败", e);
      result.put("status", "error");
      result.put("message", e.getMessage());
    }
    return result;
  }
}