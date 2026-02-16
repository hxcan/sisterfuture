/*

 */
package com.stupidbeauty.lanime.callback;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.os.Environment;

import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;
import com.stupidbeauty.lanime.Constants;

/**
 * @author root 蔡火胜。
 * 收到对于通知的回复内容之后，对之进行处理的回调对象。
 */
public class PhoneInformationCallback implements HttpServerRequestCallback 
{
	@Override
	/*
	  收到请求。
	 */
	public void onRequest(AsyncHttpServerRequest arg0,AsyncHttpServerResponse arg1) 
	{
		//回复：
		JSONObject replyObject=new JSONObject(); //创建回复对象。
		try //填充JSON内容，并且捕获可能的异常。 
		{
			replyObject.put("success", true); //请求成功。
			replyObject.put("phoneModel", Build.MODEL); //手机型号。
			Boolean whetherHasPhoneAvatar=checkPhoneAvatar(); //检查是否拥有手机头像。
			replyObject.put("hasPhoneAvatar", whetherHasPhoneAvatar); //是否拥有手机头像。
		} //try //填充JSON内容，并且捕获可能的异常。
		catch (JSONException e)  //捕获异常。
		{
			e.printStackTrace(); //报告错误信息。
		} //请求成功。
		
		
		arg1.send(replyObject); //发送回复。

	} //public void onRequest(AsyncHttpServerRequest arg0,AsyncHttpServerResponse arg1)

	/**
	 * 检查是否有手机头像。
	 * @return 是否有手机头像。
	 */
	private Boolean checkPhoneAvatar() 
	{
		Boolean result=false; //结果。
		
		File avatarFile=new File(Environment.getExternalStorageDirectory().getAbsolutePath()+ "/"+ Constants.Path.SdCardDirectoryName+ "/" + Constants.Path.PhoneAvatarFileName  ); //头像文件对象。
		
		if (avatarFile.exists()) //文件存在。
		{
			result=true; //有头像存在。
		} //if (avatarFile.exists()) //文件存在。
		
	
		return result;
	} //private Boolean checkPhoneAvatar()
} //public class NoticeCallback
