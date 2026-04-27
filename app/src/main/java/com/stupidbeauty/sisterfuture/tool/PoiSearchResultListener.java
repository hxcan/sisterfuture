package com.stupidbeauty.sisterfuture.tool;

import android.util.Log;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;

/**
 * POI 搜索结果监听器
 * 独立类实现，避免匿名内部类的编译问题
 */
public class PoiSearchResultListener implements OnGetPoiSearchResultListener {
    private static final String TAG = "PoiSearchResultListener";
    private final SearchNearbyTool tool;

    public PoiSearchResultListener(SearchNearbyTool tool) {
        this.tool = tool;
    }

    @Override
    public void onGetPoiResult(PoiResult result) {
        tool.handlePoiResult(result);
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
}
