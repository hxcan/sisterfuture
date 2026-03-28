package com.stupidbeauty.sisterfuture.listener;

import com.stupidbeauty.builtinftp.ErrorListener;
import android.util.Log;

/**
 * 未来姐姐内置 FTP 服务器错误监听器
 * 
 * @author 太极美术工程狮狮长
 * @version 1.0.0
 */
public class BuiltinFtpServerErrorListener implements ErrorListener {
    private static final String TAG = "BuiltinFtpServerErrorListener";

    /**
     * 处理 FTP 服务器错误
     * @param errorCode 错误代码
     */
    @Override
    public void onError(Integer errorCode) {
        Log.d(TAG, "onError, error from builtin ftp server: " + errorCode);
    }
}