package com.stupidbeauty.lanime.network.volley;

import java.io.UnsupportedEncodingException;
import java.util.Map;

import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;
import com.google.gson.Gson; //找不到gson库？请阅读项目根目录下的 "externalLibraries.txt".

public class GsonRequest<T> extends Request<T> {
	
	private static final String TAG = "GsonRequest"; //!<输出调试信息时使用的标记。
	private final Listener<T> mListener;
	private Gson mGson;
	private Class<T> mClass;
	private Map<String, String> mParams; //!<参数列表组成的映射。
	

	@SuppressWarnings("SameParameterValue")
	public GsonRequest(int method, String url, Class<T> clazz, Listener<T> listener, ErrorListener errorListener)
	{
		
		super(method, url, errorListener);

		Log.d(TAG,"GsonRequest,url:"+url); //Debug.

		mGson=new Gson();
		mClass=clazz;
		mListener=listener;
	}
	
	public GsonRequest(String url,Class<T> clazz,Listener<T> listener,ErrorListener errorListener)
	{
		this(Method.GET,url,clazz,listener,errorListener);
	} //public GsonRequest(String url,Class<T> clazz,Listener<T> listener,ErrorListener errorListener)
	

	@Override
	protected Response<T> parseNetworkResponse(NetworkResponse response) {

		try
		{
			String jsonString=new String(response.data,HttpHeaderParser.parseCharset(response.headers));
			
			Log.d(TAG, "parseNetworkResponse,jsonString:"+jsonString); //Debug.
			
			
			return Response.success(mGson.fromJson(jsonString, mClass),HttpHeaderParser.parseCacheHeaders(response));
		}
		catch(UnsupportedEncodingException e)
		{
			return Response.error(new ParseError(e));
		} //catch(UnsupportedEncodingException e)
		
		
	}

	@Override
	protected void deliverResponse(T response) {


		mListener.onResponse(response);
		
	}
	
	@Override
	protected Map<String,String> getParams() throws AuthFailureError
	{
		return mParams;
	} //protected Map<String,String> getParams() throws AuthFailureError
	

	/**
	 * 设置参数映射。
	 * @param urlParams 要设置的参数映射。
	 */
	public void setParams(Map<String, String> urlParams) 
	{
		mParams=urlParams; //记录参数映射。
	} //public void setParams(Map<String, String> urlParams)

}
