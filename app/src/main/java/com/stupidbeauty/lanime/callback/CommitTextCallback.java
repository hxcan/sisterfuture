package com.stupidbeauty.lanime.callback;

import org.json.JSONException;
import org.json.JSONObject;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
// import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.koushikdutta.async.http.Multimap;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.stupidbeauty.sisterfuture.SisterFutureApplication;
import com.stupidbeauty.lanime.Constants;

/**
 * @author root 蔡火胜。
 * 收到对于通知的回复内容之后，对之进行处理的回调对象。
 */
public class CommitTextCallback implements HttpServerRequestCallback 
{
	private static final String TAG = "CommitTextCallback"; //!<The tag used to output debug info.

	@Override
	/*
	  收到请求。
	 */
	public void onRequest(AsyncHttpServerRequest arg0,AsyncHttpServerResponse arg1) 
	{
		Multimap qryMap = arg0.getQuery(); //获取表示查询参数的映射对象。
		String phoneNumber=qryMap.getString("text"); //获取要提交的文本内容。
		
		Log.d(TAG,"Got command:"+phoneNumber); //Debug.
		
		String callbackIp=qryMap.getString("callbackIp"); //获取回调IP。
		
		reportCallbackIp(callbackIp); //报告回调IP。
		
        //发送广播，让界面更新：
		Intent intent4Wrk = new Intent(); //创建意图对象。晚安诸位。
		Bundle extras=new Bundle(); //创建数据包。
		extras.putString("text", phoneNumber); //加入文本内容。
		
		if (qryMap.containsKey("transactionId")) //传入了事务编号参数。
		{
			String transactionIdString=qryMap.getString("transactionId"); //获取事务编号字符串。
			long transactionId=Long.parseLong(transactionIdString); //解析成事务编号。
			extras.putLong("transactionId", transactionId); //加入事务编号。
			
		} //if (qryMap.containsKey("transactionId")) //传入了事务编号参数。
		
		intent4Wrk.putExtras(extras); //设置附加数据。
												
		intent4Wrk.setAction(Constants.Operation.CommitText);// action与接收器相同
		
		Context appContext= SisterFutureApplication.getAppContext(); //获取应用程序上下文。
		LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(appContext); //Get the local broadcast manger instance.
		
		localBroadcastManager.sendBroadcast(intent4Wrk); //发送广播。
					

		//回复：
		JSONObject replyObject=new JSONObject(); //创建回复对象。
		try //填充JSON内容，并且捕获可能的异常。 
		{
			replyObject.put("success", true); //请求成功。
		} //try //填充JSON内容，并且捕获可能的异常。
		catch (JSONException e)  //捕获异常。
		{
			e.printStackTrace(); //报告错误信息。
		} //请求成功。
		
		
		arg1.send(replyObject); //发送回复。

	} //public void onRequest(AsyncHttpServerRequest arg0,AsyncHttpServerResponse arg1)

	/**
	 * 报告回调IP。
	 * @param callbackIp 要报告的回调IP。
	 */
	private void reportCallbackIp(String callbackIp) 
	{
		Context appContext= SisterFutureApplication.getAppContext(); //获取应用程序上下文。
		LocalBroadcastManager localBroadcastManager=LocalBroadcastManager.getInstance(appContext); //Get the local broadcast manger instance.
		Intent intent=new Intent(); //创建意图。
		
		intent.setAction(Constants.NativeMessage.NOTIFY_CALLBACK_IP); //设置动作。
		
		intent.putExtra("callbackIp", callbackIp); //加入额外参数。回调IP。
		
		
		localBroadcastManager.sendBroadcast(intent); //发送广播。
		
	} //private void reportCallbackIp(String callbackIp)
} //public class NoticeCallback
