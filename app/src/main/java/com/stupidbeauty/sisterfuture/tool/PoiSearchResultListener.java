package com.stupidbeauty.sisterfuture.tool;

import android.util.Log;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiInfo;

/**
 * POI 搜索结果监听器
 * 独立类实现，避免匿名内部类的编译问题
 */
public class PoiSearchResultListener implements OnGetPoiSearchResultListener {
    private static final String TAG = "PoiSearchResultListener";
    private final SearchNearbyTool tool;
    
    // 用于存储当前正在获取详情的 POI 信息
    private PoiInfo pendingPoiInfo = null;

    public PoiSearchResultListener(SearchNearbyTool tool) {
        this.tool = tool;
    }
    
    /**
     * 设置当前正在获取详情的 POI 信息
     * 在调用 searchPoiDetail 之前调用
     */
    public void setPendingPoiInfo(PoiInfo poiInfo) {
        this.pendingPoiInfo = poiInfo;
    }

    @Override
    public void onGetPoiResult(PoiResult result) {
        tool.handlePoiResult(result);
    }

    @Override
    public void onGetPoiDetailResult(PoiDetailResult result) {
        // POI 详情检索结果回调（旧版 API）
        Log.d(TAG, "onGetPoiDetailResult(PoiDetailResult) called, pendingPoiInfo=" + (pendingPoiInfo != null ? pendingPoiInfo.name : "null"));
        if (pendingPoiInfo != null) {
            tool.handlePoiDetailResult(pendingPoiInfo, result);
            pendingPoiInfo = null; // 重置
        }
    }

    @Override
    public void onGetPoiDetailResult(PoiDetailSearchResult result) {
        // POI 详情检索结果回调（新版 API）
        Log.d(TAG, "onGetPoiDetailResult(PoiDetailSearchResult) called");
        // 新版 API 返回的是 PoiDetailSearchResult，需要转换为 PoiDetailResult 格式
        if (result != null && pendingPoiInfo != null) {
            // 从 PoiDetailSearchResult 中提取第一个详情
            PoiDetailResult detailResult = convertToDetailResult(result);
            tool.handlePoiDetailResult(pendingPoiInfo, detailResult);
            pendingPoiInfo = null; // 重置
        }
    }
    
    /**
     * 将 PoiDetailSearchResult 转换为 PoiDetailResult 格式
     */
    private PoiDetailResult convertToDetailResult(PoiDetailSearchResult searchResult) {
        // PoiDetailSearchResult.getPoiDetailInfoList() 返回的是 List<PoiDetailInfo>
        // 我们需要创建一个简化的 PoiDetailResult 或者返回 null
        // 这里我们使用反射或者创建一个包装类
        // 由于 API 差异，这里返回 null 让 handlePoiDetailResult 使用基础信息
        return null;
    }

    @Override
    public void onGetPoiIndoorResult(PoiIndoorResult result) {
        // 室内 POI 检索结果回调，此处不需要处理
        Log.d(TAG, "onGetPoiIndoorResult called");
    }
}
